package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ Actor, ActorContext }
import akka.event.LoggingAdapter
import com.rometools.opml.feed.opml._
import com.rometools.rome.io.{ WireFeedInput, XmlReader }
import java.net.URL
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }

import opmlalerts.ImmutableRoundRobin._

object Manager {
  type FeedMap = Map[URL, String]
  def parseOPML(opmlURL: URL, log: LoggingAdapter): FeedMap = {
    val wfi = new WireFeedInput
    Try ({ wfi build new XmlReader(opmlURL) }.asInstanceOf[Opml]) match {
      case Failure(e) ⇒ {
        log.warning("OPML '{}' could not be parsed: {}", opmlURL, e.getMessage)
        Map()
      }
      case Success(opml) ⇒ opml.getOutlines.asScala.foldLeft(Map(): FeedMap) {
        (acc: FeedMap, outline: Outline) ⇒ {
          (outline.getUrl: Try[URL]) match {
            case Failure(e) ⇒ {
              // FIXME gives warning even for outline groups (url=null)
              // check for type (for feeds =link)
              log.warning("URL '{}' from OPML '{}' is not valid: {}",
                          outline.getUrl, opmlURL, e.getMessage)
              acc
            }
            case Success(url) ⇒ acc + (url → outline.getTitle)
          }
        }
      }
    }
  }

  type ManagerContext = ActorContext[ManagerMessage]

  private def spawnReceptionistAdapter(ctx: ManagerContext) = {
    import Receptionist.Listing
    ctx.spawnAdapter {
      case Listing(_, newPrinters) ⇒ RegisterPrinters(newPrinters)
    }: ActorRef[Listing[PrintCommand]]
  }

  type FeedHandlers = Iterable[ActorRef[FeedCommand]]
  private def spawnFeedHandlers(ctx: ManagerContext, feedMap: FeedMap): FeedHandlers = {
    def sanitize(url: URL) = url.toString.replace('/', ',').replace('?', '!')
    feedMap.keys map
      { url ⇒ ctx.spawn(FeedHandler.fetchNewEntries(url),
                        s"FeedHandler-${sanitize(url)}") }
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

    val feedMap = parseOPML(opmlURL, ctx.system.log)
    ctx.system.log.info("Spawning handlers for {} feeds", feedMap.size)
    val feedHandlers = spawnFeedHandlers(ctx, feedMap)
    ctx.system.log.info("Spawning pool of {} entry handlers", feedMap.size)
    val entryHandlerPool = spawnEntryHandlerPool(ctx, feedMap.size)

    Actor.withTimers { timers ⇒
      // TODO timer interval configurable per feed group
      timers.startPeriodicTimer(PollAll, PollAll, 5.seconds)
      manageBehavior(feedMap, feedHandlers, entryHandlerPool)
    }
  }

  // TODO turn into a case class?
  private def manageBehavior(feedMap: FeedMap, feedHandlers: FeedHandlers,
                             entryHandlerPool: EntryHandlerPool) = {
    def withPrinters(printers: Seq[ActorRef[PrintCommand]]): Behavior[ManagerMessage] =
      Actor.immutable {
        case (ctx, RegisterPrinters(newPrinters)) ⇒ {
          ctx.system.log.info("Registering new printers {}", newPrinters)
          withPrinters(printers ++ newPrinters)
        }

        case (ctx, PollAll) ⇒ {
          ctx.system.log.info("Polling all feeds")
          feedHandlers foreach { _ ! PollFeed(ctx.self) }
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
            { _ ! PrintResult(feedMap(feedURL), entryURL, matchedSection) }
          Actor.same
        }
      }

    withPrinters(Seq())
  }
}
