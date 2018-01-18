package opmlalerts

import akka.actor.typed._
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
  
  type FeedHandlers = Iterable[ActorRef[FeedCommand]]
  private def spawnFeedHandlers[T](feedMap: FeedMap)(implicit ctx: ActorContext[T]): FeedHandlers =
    feedMap.keys map
      { url ⇒ ctx.spawn(FeedHandler.fetchNewEntries(url), s"FeedHandler-$url") }

  type EntryHandlerPool = ActorRef[EntryCommand]
  private def spawnEntryHandlerPool[T](poolSize: Int)(implicit ctx: ActorContext[T]): EntryHandlerPool = {
    val roundRobin = roundRobinBehavior(poolSize, EntryHandler.scanEntry)
    ctx.spawn(roundRobin, "EntryHandler-pool")
  }

  def poolManager(opmlURL: URL): Behavior[ManagerMessage] = Actor.deferred { ctx ⇒
    val feedMap = buildFeedMap(parseOPML(opmlURL))
    val feedHandlers = spawnFeedHandlers(feedMap)(ctx)
    val entryHandlerPool = spawnEntryHandlerPool(feedMap.size)(ctx)
    poolManagerBehavior(feedMap, feedHandlers, entryHandlerPool)
  }

  private def poolManagerBehavior(feedMap: FeedMap, feedHandlers: FeedHandlers,
                                  entryHandlerPool: EntryHandlerPool): Behavior[ManagerMessage] = {
    def withPrinters(printers: Seq[ActorRef[PrintCommand]]): Behavior[ManagerMessage] =
      Actor.immutable {
        case (ctx, RegisterPrinter(printer: ActorRef[PrintCommand])) ⇒ {
          ctx.system.log.info("Registering new printer {}", printer)
          withPrinters(printer +: printers)
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

        case (_, MatchFound(NewEntry(feedURL, entryURL), matchedSection)) ⇒ {
          val feedTitle = feedMap(feedURL)
          printers foreach
            { _ ! PrintResult(feedURL, feedTitle, entryURL, matchedSection) }
          Actor.same
        }
      }

    withPrinters(Seq())
  }
}
