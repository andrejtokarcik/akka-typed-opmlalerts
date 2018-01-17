package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.{ SyndFeedInput, XmlReader }
import java.net.URL
import java.time.Instant
import scala.collection.JavaConverters._
import scala.util.{ Try, Success, Failure }

object FeedHandler {
  final case class Fetch(replyTo: ActorRef[Download])
  final case class Download(url: URL)

  lazy val sfi = new SyndFeedInput
  def parseFeed(feed: URL) = Try { sfi build new XmlReader(feed) }
  def extractDate(x: SyndEntry): Option[Instant] = {
    val date = Option(x.getUpdatedDate) orElse Option(x.getPublishedDate)
    date map { _.toInstant }
  }

  //override def preStart(): Unit = log.info("Feed handler of {} started", feed)
  //override def postStop(): Unit = log.info("Feed handler of {} stopped", feed)

  def fetcher(feed: URL, lastFetched: Instant): Behavior[Fetch] =
    Actor.immutable[Fetch] { (ctx, msg) ⇒
      ctx.system.log.info("Fetching and parsing feed '{}' for latest updates", feed)
      val maybeParsed = parseFeed(feed)
      maybeParsed match {
        case Success(v) ⇒
          ctx.system.log.info("Filtering out the newly added entries")
          val newEntries = v.getEntries.asScala withFilter (extractDate(_) exists { _ isAfter lastFetched })
          for (entry ← newEntries)
            msg.replyTo ! Download(new URL(entry.getLink))

        case Failure(e) ⇒
          ctx.system.log.warning("An exception occurred while processing feed '{}': {}",
                                 feed, e.getMessage)
      }
      Actor.same  // TODO refresh lastFetched with Instant.now  + test
    }
}
