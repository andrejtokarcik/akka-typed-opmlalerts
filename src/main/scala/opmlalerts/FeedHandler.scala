package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import com.rometools.rome.feed.synd._
import com.rometools.rome.io.{ SyndFeedInput, XmlReader }
import java.net.URL
import java.time.Instant
import scala.collection.JavaConverters._
import scala.util.{ Try, Success, Failure }

object FeedHandler {
  final case class Poll(replyTo: ActorRef[FeedEntry])
  final case class FeedEntry(url: URL)

  // TODO would be useful for EntryHandler too
  // (with java.net.MalformedURLException handled)
  implicit def str2URL(str: String) = new URL(str)

  def pollForNewEntries(feedURL: URL, since: Instant = Instant.now) =
    new FeedHandler(feedURL) pollForNewEntries since

  lazy val sfi = new SyndFeedInput
  def parseFeed(feed: URL) = Try { sfi build new XmlReader(feed) }

  def filterNewEntries(feed: SyndFeed, lastPolled: Instant) =
    feed.getEntries.asScala withFilter
      (extractDate(_) exists { _ isAfter lastPolled })

  def extractDate(entry: SyndEntry) = {
    val date = Option(entry.getUpdatedDate) orElse Option(entry.getPublishedDate)
    date map (_.toInstant)
  }
}

class FeedHandler(feedURL: URL) {
  import FeedHandler._

  def pollForNewEntries(lastPolled: Instant): Behavior[Poll] =
    Actor.immutable[Poll] { (ctx, msg) ⇒
      ctx.system.log.info("Fetching and parsing feed '{}'", feedURL)
      val polled = Instant.now
      val maybeParsed = parseFeed(feedURL)

      maybeParsed match {
        case Success(feed) ⇒
          ctx.system.log.info("Filtering newly added entries")
          for (entry ← filterNewEntries(feed, lastPolled))
            msg.replyTo ! FeedEntry(entry.getLink)

        case Failure(e) ⇒
          ctx.system.log.warning("An exception occurred while processing feed '{}': {}",
                                 feedURL, e.getMessage)
      }
      this.pollForNewEntries(polled)
    }
}
