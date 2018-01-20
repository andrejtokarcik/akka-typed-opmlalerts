package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ Actor, ActorContext }
import java.net.URL

import opmlalerts.ImmutableRoundRobin._
import opmlalerts.Messages._
import opmlalerts.Parser.FeedInfo

object Manager {
  type ManagerContext = ActorContext[ManagerMessage]

  private def spawnReceptionistAdapter(ctx: ManagerContext) = {
    import Receptionist.Listing
    ctx.spawnAdapter {
      case Listing(_, newPrinters) ⇒ RegisterPrinters(newPrinters)
    }: ActorRef[Listing[PrintCommand]]
  }

  type FeedHandlerMap = Map[URL, ActorRef[FeedCommand]]
  private def spawnFeedHandlers(ctx: ManagerContext, feedMap: Map[URL, FeedInfo]): FeedHandlerMap = {
    def sanitize(url: URL) = url.toString.replace('/', ',').replace('?', '!')
    feedMap.keysIterator.map(url ⇒
      url → ctx.spawn(FeedHandler.getNewEntries(url), s"FeedHandler-${sanitize(url)}")
    ).toMap
  }

  type EntryHandlerPool = ActorRef[EntryCommand]
  private def spawnEntryHandlerPool(ctx: ManagerContext, poolSize: Int): EntryHandlerPool = {
    val roundRobin = roundRobinBehavior(poolSize, EntryHandler.scanEntry)
    ctx.spawn(roundRobin, "EntryHandler-pool")
  }

  def manageActors(opmlURL: URL): Behavior[ManagerMessage] = Actor.deferred { ctx ⇒
    ctx.system.log.info("Subscribing to Printer instantiations")
    val adapter = spawnReceptionistAdapter(ctx)
    ctx.system.receptionist ! Receptionist.Subscribe(Printer.ServiceKey, adapter)

    val feedMap = Parser(ctx.system.log).parseOPML(opmlURL)
    ctx.system.log.info("Spawning {} feed handlers (one per feed)", feedMap.size)
    val feedHandlers = spawnFeedHandlers(ctx, feedMap)
    ctx.system.log.info("Spawning pool of {} entry handlers", feedMap.size)
    val entryHandlerPool = spawnEntryHandlerPool(ctx, feedMap.size)

    Actor.withTimers { timers ⇒
      // TODO timer interval configurable per feed group
      for ((feedURL, feedInfo) ← feedMap)
        timers.startPeriodicTimer(PollFeed, PollFeed(feedURL), feedInfo.interval)

      manageBehavior(feedMap, feedHandlers, entryHandlerPool)
    }
  }

  // TODO turn into a case class?
  private def manageBehavior(feedMap: Map[URL, FeedInfo], feedHandlers: FeedHandlerMap,
                             entryHandlerPool: EntryHandlerPool) = {
    def withPrinters(printers: Seq[ActorRef[PrintCommand]]): Behavior[ManagerMessage] =
      Actor.immutable {
        case (ctx, RegisterPrinters(newPrinters)) ⇒ {
          ctx.system.log.info("Registering new printers {}", newPrinters)
          withPrinters(printers ++ newPrinters)
        }

        case (ctx, PollFeed(feedURL)) ⇒ {
          ctx.system.log.info("Polling feed {}", feedURL)
          feedHandlers(feedURL) ! GetNewEntries(ctx.self)
          Actor.same
        }

        case (ctx, entry @ NewEntry(_, _)) ⇒ {
          entryHandlerPool ! ScanEntry(entry, ctx.self)
          Actor.same
        }

        case (ctx, MatchFound(NewEntry(feedURL, entryURL), matchedSection)) ⇒ {
          if (printers.isEmpty)
            ctx.system.log.warning("Attempting to PrintResult but no printers registered")

          printers foreach
            { _ ! PrintResult(feedMap(feedURL).title, entryURL, matchedSection) }
          Actor.same
        }
      }

    withPrinters(Seq())
  }
}
