package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.{ Actor, ActorContext }
import com.rometools.opml.feed.opml._
import com.rometools.rome.io.{ WireFeedInput, XmlReader }
import java.net.URL
import scala.collection.JavaConverters._

import opmlalerts.ImmutableRoundRobin._

object Manager {
  lazy val wfi = new WireFeedInput
  def parseOPML(opmlURL: URL) = { wfi build new XmlReader(opmlURL) }.asInstanceOf[Opml]

  type FeedMap = Map[URL, String]
  def buildFeedMap(opml: Opml): FeedMap =
    Map (opml.getOutlines.asScala map { x ⇒ ((x.getUrl: URL, x.getTitle)) }: _*)

  private def spawnReceptionistAdapter()(implicit ctx: ActorContext[ManagerMessage]) = {
    import Receptionist.Listing
    ctx.spawnAdapter {
      case Listing(_, newPrinters) ⇒ RegisterPrinters(newPrinters)
    }: ActorRef[Listing[PrintCommand]]
  }

  type FeedHandlers = Iterable[ActorRef[FeedCommand]]
  private def spawnFeedHandlers(feedMap: FeedMap)
                               (implicit ctx: ActorContext[ManagerMessage]): FeedHandlers =
    feedMap.keys map
      { url ⇒ ctx.spawn(FeedHandler.fetchNewEntries(url), s"FeedHandler-$url") }

  type EntryHandlerPool = ActorRef[EntryCommand]
  private def spawnEntryHandlerPool(poolSize: Int)
                                   (implicit ctx: ActorContext[ManagerMessage]): EntryHandlerPool = {
    val roundRobin = roundRobinBehavior(poolSize, EntryHandler.scanEntry)
    ctx.spawn(roundRobin, "EntryHandler-pool")
  }

  def poolManager(opmlURL: URL): Behavior[ManagerMessage] = Actor.deferred { ctx ⇒
    implicit val context = ctx

    ctx.system.log.info("Subscribing to Printer instantiations")
    val adapter = spawnReceptionistAdapter()
    ctx.system.receptionist ! Receptionist.Subscribe(Printer.ServiceKey, adapter)

    val feedMap = buildFeedMap(parseOPML(opmlURL))
    val feedHandlers = spawnFeedHandlers(feedMap)
    val entryHandlerPool = spawnEntryHandlerPool(feedMap.size)
    poolManagerBehavior(feedMap, feedHandlers, entryHandlerPool)
  }

  private def poolManagerBehavior(feedMap: FeedMap, feedHandlers: FeedHandlers,
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
