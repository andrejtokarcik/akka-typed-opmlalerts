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
  lazy val sfi = new SyndFeedInput
  def parseFeed(feedURL: URL) = Try { sfi build new XmlReader(feedURL) }

  def filterNewEntries(feed: SyndFeed, lastPolled: Instant) =
    feed.getEntries.asScala withFilter
      (extractDate(_) exists { _ isAfter lastPolled })

  def extractDate(entry: SyndEntry) = {
    val date = Option(entry.getUpdatedDate) orElse Option(entry.getPublishedDate)
    date map (_.toInstant)
  }

  def fetchNewEntries(feedURL: URL, lastPolled: Instant = Instant.now): Behavior[FeedCommand] =
    Actor.immutable { case (ctx, PollFeed(replyTo)) ⇒
      ctx.system.log.info("Fetching and parsing feed '{}'", feedURL)
      val pollTime = Instant.now
      val maybeParsed = parseFeed(feedURL)

      maybeParsed match {
        case Success(feed) ⇒
          ctx.system.log.info("Filtering newly added entries")
          for (entry ← filterNewEntries(feed, lastPolled))
            replyTo ! NewEntry(feedURL, entry.getLink)

        case Failure(e) ⇒
          ctx.system.log.warning("An exception occurred while processing feed '{}': {}",
                                 feedURL, e.getMessage)
      }

      fetchNewEntries(feedURL, pollTime)
    }
}
