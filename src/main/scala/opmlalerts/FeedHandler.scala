package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import akka.event.LoggingAdapter
import com.rometools.rome.feed.synd._
import com.rometools.rome.io.{ SyndFeedInput, XmlReader }
import java.net.URL
import java.time.Instant
import scala.collection.JavaConverters._
import scala.util.{ Try, Success, Failure }

object FeedHandler {
  final case class FeedEntry(date: Instant, url: URL)
  def parseFeed(feedURL: URL, log: LoggingAdapter): Vector[FeedEntry] = {
    val sfi = new SyndFeedInput
    Try { sfi build new XmlReader(feedURL) } match {
      case Failure(e) ⇒ {
        log.warning("Feed '{}' could not be parsed: {}", feedURL, e.getMessage)
        Vector()
      }
      case Success(feed) ⇒ feed.getEntries.asScala.foldLeft(Vector(): Vector[FeedEntry]) {
        (acc: Vector[FeedEntry], entry: SyndEntry) ⇒ {
          extractDate(entry) match {
            case None ⇒ {
              log.warning("Feed '{}' contains an entry with a missing/corrupted date: {}",
                          feedURL, entry.getLink)
              acc
            }
            case Some(date) ⇒ {
              (entry.getLink: Try[URL]) match {
                case Failure(e) ⇒ {
                  log.warning("URL '{}' from feed '{}' is not valid: {}",
                              feed.getLink, feedURL, e.getMessage)
                  acc
                }
                case Success(url) ⇒ acc :+ FeedEntry(date, url)
              }
            }
          }
        }
      }
    }
  }

  def extractDate(entry: SyndEntry) = {
    val date = Option(entry.getUpdatedDate) orElse Option(entry.getPublishedDate)
    date map (_.toInstant)
  }

  def filterNewEntries(entries: Vector[FeedEntry], lastPolled: Instant) =
    entries filter { _.date isAfter lastPolled }

  def fetchNewEntries(feedURL: URL, lastPolled: Instant = Instant.now.minusSeconds(120)): Behavior[FeedCommand] =
    Actor.immutable { case (ctx, PollFeed(replyTo)) ⇒
      ctx.system.log.info("Fetching and parsing feed '{}'", feedURL)
      val pollTime = Instant.now

      val feed = parseFeed(feedURL, ctx.system.log)
      val newEntries = filterNewEntries(feed, lastPolled)
      for (FeedEntry(_, url) ← newEntries)
        replyTo ! NewEntry(feedURL, url)
      ctx.system.log.info("Feed '{}' had {} new entries", feedURL, newEntries.length)

      fetchNewEntries(feedURL, pollTime)
    }
}
