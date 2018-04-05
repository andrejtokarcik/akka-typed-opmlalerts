package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import java.nio.file.Path
import java.net.URL
import java.time.{ Duration, Instant }
import scala.concurrent.duration._
import scalaz.Memo

import opmlalerts.ImmutableRoundRobin._

object Manager {
  sealed trait Message

  sealed trait Command extends Message
  final case class RegisterPrinters(newPrinters: Set[ActorRef[Printer.Command]])
      extends Command
  final case class UnregisterPrinter(printer: ActorRef[Printer.Command])
      extends Command
  final case class PollFeed(feedURL: URL) extends Command
  private final case object ReportTimeSinceLastPoll extends Command

  private sealed trait Response extends Message
  private final case class NewEntryOfFeed(feedURL: URL,
                                          entry: Parser.FeedEntry)
      extends Response
  private final case class MatchFoundInEntry(entry: NewEntryOfFeed,
                                             matched: EntryHandler.MatchFound)
      extends Response

  def manage(opmlPath: Path): Behavior[Message] =
    Behaviors.setup { ctx ⇒ new Manager(ctx)(opmlPath).manage() }
}

class Manager(ctx: ActorContext[Manager.Message])(opmlPath: Path) {
  import Manager._

  val firstPoll = Instant.now

  val feedMap = Parser(ctx.system.log).parseOPML(opmlPath)

  type FeedHandlerMap = Map[URL, ActorRef[FeedHandler.Command]]
  lazy val feedHandlers = {
    ctx.system.log.debug("Spawning {} feed handlers (one per feed)", feedMap.size)
    def sanitize(url: URL) = url.toString.replace('/', '-').replace('?', '!')
    feedMap.keysIterator.map(url ⇒
      url → ctx.spawn(FeedHandler(url) getNewEntriesSince firstPoll,
                      s"FeedHandler-${sanitize(url)}")
    ).toMap
  }

  type EntryHandlerPool = ActorRef[EntryHandler.Command]
  lazy val entryHandlerPool = {
    val poolSize = feedMap.size * 3
    ctx.system.log.debug("Spawning pool of {} entry handlers", poolSize)
    val roundRobin = roundRobinBehavior(poolSize, EntryHandler.scanEntry)
    ctx.spawn(roundRobin, "EntryHandler-pool")
  }

  def subscribeToPrinters() = {
    ctx.system.log.debug("Subscribing to Printer instantiations")
    val adapter: ActorRef[Receptionist.Listing] = ctx.messageAdapter {
      case Printer.ServiceKey.Listing(newPrinters) ⇒ RegisterPrinters(newPrinters)
    }
    ctx.system.receptionist !
      Receptionist.Subscribe(Printer.ServiceKey, adapter)
  }

  def manage(): Behavior[Message] = {
    if (feedMap.isEmpty) {
      ctx.system.log.error("No valid feeds -- no action until the OPML is updated")
      return Behaviors.empty
    }

    subscribeToPrinters()
    Behaviors.withTimers { timers ⇒
      timers.startPeriodicTimer(ReportTimeSinceLastPoll, ReportTimeSinceLastPoll, 10.seconds)
      // TODO timer interval configurable per feed group
      for ((feedURL, feedInfo) ← feedMap)
        timers.startPeriodicTimer(PollFeed(feedURL), PollFeed(feedURL), feedInfo.interval)

      behavior(printers = Seq(), lastPoll = firstPoll, lastFeed = None)
    }
  }

  // FIXME This might not work as expected after upgrade to Akka 2.5.11 because
  // `messageAdapter` can now spawn only one adapter per message class whereas
  // the original idea here was to provide a standalone adapter facing each of
  // the FeedHandler actors (all of which reply with a NewEntry message).
  private lazy val newEntryAdapter = {
    import FeedHandler.NewEntry
    Memo.immutableHashMapMemo[URL, ActorRef[NewEntry]] { feedURL ⇒
      val adapter: ActorRef[NewEntry] = ctx.messageAdapter {
        case NewEntry(entry) ⇒ NewEntryOfFeed(feedURL, entry)
      }
      adapter
    }
  }

  private lazy val matchFoundAdapter =
    Memo.immutableHashMapMemo[NewEntryOfFeed, ActorRef[EntryHandler.MatchFound]] { feedEntry ⇒
      val adapter = ctx.messageAdapter {
        matched: EntryHandler.MatchFound ⇒ MatchFoundInEntry(feedEntry, matched)
      }
      adapter
    }

  private def behavior(printers: Seq[ActorRef[Printer.Command]],
                       lastPoll: Instant, lastFeed: Option[URL]): Behavior[Message] =
    Behaviors.immutable { (ctx, msg) ⇒
      msg match {
        case RegisterPrinters(newPrinters) ⇒ {
          ctx.system.log.debug("Registering new printers {}", newPrinters)
          newPrinters foreach
            { printer ⇒ ctx.watchWith(printer, UnregisterPrinter(printer)) }
          behavior(printers ++ newPrinters, lastPoll, lastFeed)
        }

        case UnregisterPrinter(printer) ⇒ {
          ctx.system.log.debug("Unregistering printer {}", printer)
          behavior(printers filter (_ != printer), lastPoll, lastFeed)
        }

        case PollFeed(feedURL) ⇒ {
          ctx.system.log.info("Polling feed {} (after {})", feedURL, feedMap(feedURL).interval)
          val adapter = newEntryAdapter(feedURL)
          feedHandlers(feedURL) ! FeedHandler.GetNewEntries(adapter)
          behavior(printers, Instant.now, Some(feedURL))
        }

        case feedEntry @ NewEntryOfFeed(feedURL, entry) ⇒ {
          val adapter = matchFoundAdapter(feedEntry)
          feedMap(feedURL).pattern foreach
            { entryHandlerPool ! EntryHandler.ScanEntry(entry.url, _, adapter) }
          Behaviors.same
        }

        case MatchFoundInEntry(NewEntryOfFeed(feedURL, entry), matched) ⇒ {
          if (printers.isEmpty)
            ctx.system.log.warning("Attempting to PrintMatch but no printers registered")

          printers foreach
            { _ ! Printer.PrintMatch(feedURL, feedMap(feedURL), entry, matched) }
          Behaviors.same
        }

        case ReportTimeSinceLastPoll ⇒ {
          // Converting java.time.Duration to Scala representation for better output format
          val duration = Duration.between(lastPoll, Instant.now).getSeconds.seconds
          val feedInfo = lastFeed map (feed ⇒ s" (of feed $feed)")
          ctx.system.log.info("Last poll{} {} ago", feedInfo getOrElse "", duration)
          Behaviors.same
        }
      }
    }
}
