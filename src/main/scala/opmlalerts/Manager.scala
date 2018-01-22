package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ Actor, ActorContext }
import java.io.File
import java.net.URL
import java.time.Instant
import scalaz.Memo

import opmlalerts.ImmutableRoundRobin._
import opmlalerts.Parser.FeedInfo

object Manager {
  sealed trait Message

  sealed trait Command extends Message
  final case class RegisterPrinters(newPrinters: Set[ActorRef[Printer.Command]])
      extends Command
  final case class UnregisterPrinter(printer: ActorRef[Printer.Command])
      extends Command
  final case class PollFeed(feedURL: URL) extends Command

  private sealed trait Response extends Message
  private final case class NewEntryOfFeed(feedURL: URL,
                                          entry: FeedHandler.NewEntry)
      extends Response
  private final case class MatchFoundInEntry(entry: NewEntryOfFeed,
                                             matched: EntryHandler.MatchFound)
      extends Response

  def manage(opmlURL: File): Behavior[Message] =
    Actor.deferred { ctx ⇒ new Manager(opmlURL)(ctx).behavior }
}

class Manager(opmlURL: File)(implicit val ctx: ActorContext[Manager.Message]) {
  import Manager._

  val feedMap = Parser(ctx.system.log).parseOPML(opmlURL)

  type FeedHandlerMap = Map[URL, ActorRef[FeedHandler.Command]]
  val feedHandlers = {
    ctx.system.log.info("Spawning {} feed handlers (one per feed)", feedMap.size)
    def sanitize(url: URL) = url.toString.replace('/', ',').replace('?', '!')
    feedMap.keysIterator.map(url ⇒
      url → ctx.spawn(FeedHandler(url) getNewEntriesSince Instant.now,
                      s"FeedHandler-${sanitize(url)}")
    ).toMap
  }

  type EntryHandlerPool = ActorRef[EntryHandler.Command]
  val entryHandlerPool = {
    val poolSize = feedMap.size * 3
    ctx.system.log.info("Spawning pool of {} entry handlers", poolSize)
    val roundRobin = roundRobinBehavior(poolSize, EntryHandler.scanEntry)
    ctx.spawn(roundRobin, "EntryHandler-pool")
  }

  def behavior: Behavior[Message] = {
    Actor.withTimers { timers ⇒
      // TODO timer interval configurable per feed group
      for ((feedURL, feedInfo) ← feedMap)
        timers.startPeriodicTimer(PollFeed, PollFeed(feedURL), feedInfo.interval)

      val withSpawnedHandlers = this.WithHandlers(feedMap, feedHandlers, entryHandlerPool)
      withSpawnedHandlers.withPrinters(Seq())
    }
  }

  // TODO display info about remaining time till next poll every ~10 seconds

  case class WithHandlers(feedMap: Map[URL, FeedInfo],
                     feedHandlers: FeedHandlerMap,
                     entryHandlerPool: EntryHandlerPool) {

    {
      import Receptionist.Listing
      ctx.system.log.info("Subscribing to Printer instantiations")
      val adapter = ctx.spawnAdapter {
        case Listing(_, newPrinters) ⇒ RegisterPrinters(newPrinters)
      }: ActorRef[Listing[Printer.Command]]
      ctx.system.receptionist !
        Receptionist.Subscribe(Printer.ServiceKey, adapter)
    }

    private lazy val newEntryAdapter =
      Memo.immutableHashMapMemo[URL, ActorRef[FeedHandler.NewEntry]] { feedURL ⇒
        val adapter = ctx.spawnAdapter {
          entry: FeedHandler.NewEntry ⇒ NewEntryOfFeed(feedURL, entry)
        }
        adapter
      }

    private lazy val matchFoundAdapter =
      Memo.immutableHashMapMemo[NewEntryOfFeed, ActorRef[EntryHandler.MatchFound]] { feedEntry ⇒
        val adapter = ctx.spawnAdapter {
          matched: EntryHandler.MatchFound ⇒ MatchFoundInEntry(feedEntry, matched)
        }
        adapter
      }

    def withPrinters(printers: Seq[ActorRef[Printer.Command]]): Behavior[Message] =
      Actor.immutable {
        case (ctx, RegisterPrinters(newPrinters)) ⇒ {
          ctx.system.log.info("Registering new printers {}", newPrinters)
          newPrinters foreach
            { printer ⇒ ctx.watchWith(printer, UnregisterPrinter(printer)) }
          withPrinters(printers ++ newPrinters)
        }

        case (_, UnregisterPrinter(printer)) ⇒ {
          ctx.system.log.info("Unregistering printer {}", printer)
          withPrinters(printers filter (_ != printer))
        }

        case (ctx, PollFeed(feedURL)) ⇒ {
          ctx.system.log.info("Polling feed {} (after {})", feedURL, feedMap(feedURL).interval)
          val adapter = newEntryAdapter(feedURL)
          feedHandlers(feedURL) ! FeedHandler.GetNewEntries(adapter)
          Actor.same
        }

        case (_, feedEntry @ NewEntryOfFeed(feedURL, entry)) ⇒ {
          val adapter = matchFoundAdapter(feedEntry)
          feedMap(feedURL).pattern foreach
            { entryHandlerPool ! EntryHandler.ScanEntry(entry.url, _, adapter) }
          Actor.same
        }

        case (ctx, MatchFoundInEntry(NewEntryOfFeed(feedURL, entry), matched)) ⇒ {
          if (printers.isEmpty)
            ctx.system.log.warning("Attempting to PrintMatch but no printers registered")

          printers foreach
            { _ ! Printer.PrintMatch(feedMap(feedURL).title, feedURL, entry.url, matched) }
          Actor.same
        }
      }
  }
}
