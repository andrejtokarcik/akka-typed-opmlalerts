package opmlalerts

import akka.event.LoggingAdapter
import com.rometools.opml.feed.opml.{ Opml ⇒ RomeOPML, Outline ⇒ RomeOutline }
import com.rometools.rome.feed.synd.{ SyndEntry ⇒ RomeEntry }
import com.rometools.rome.io.{ SyndFeedInput, WireFeedInput, XmlReader }
import java.io.File
import java.net.URL
import java.time.Instant
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.matching.Regex
import scala.util.{ Try, Success, Failure }

object Parser {
  final case class FeedInfo(title: Option[String],
                            pattern: Option[Regex],
                            interval: FiniteDuration)

  final case class FeedEntry(date: Instant, url: URL)
}

case class Parser(log: LoggingAdapter) {
  import Parser._

  def parseOPML(opmlURL: File): Map[URL, FeedInfo] = this.OPML(opmlURL).parse()
  def parseFeed(feedURL: URL): Vector[FeedEntry] = this.Feed(feedURL).parse()

  case class OPML(opmlURL: File) {

    lazy val wfi = new WireFeedInput

    def parse(): Map[URL, FeedInfo] = {
      Try ({ wfi build new XmlReader(opmlURL) }.asInstanceOf[RomeOPML]) match {
        case Failure(e) ⇒ {
          log.error("OPML '{}' could not be parsed: {}", opmlURL, e)
          Map()
        }
        case Success(opml) ⇒ opml.getOutlines.asScala.foldLeft(Map(): Map[URL, FeedInfo]) {
          (acc: Map[URL, FeedInfo], outline: RomeOutline) ⇒
            this.Outline(outline).parseWith(acc)
        }
      }
    }

    case class Outline(outline: RomeOutline) {

      lazy val titleAttr = Option(outline.getTitle) orElse Option(outline.getText)
      lazy val urlAttr: Try[URL] = outline.getUrl
      lazy val intervalAttr = Option(outline.getAttributeValue("interval"))

      lazy val defaultInterval = 1.minute

      lazy val logDesc = s"associated with title ${titleAttr} and URL ${urlAttr} in OPML ${opmlURL}"

      def parseWith(partiallyConstructed: Map[URL, FeedInfo]) = {
         urlAttr match {
          case Failure(e) ⇒ {
            if (outline.getChildren.asScala.nonEmpty)  // TODO need to step in recursively
              log.info("Skipping an outline group {}", logDesc)
            else
              log.warning("URL {} is not valid: {}", logDesc, e)

            partiallyConstructed
          }
          case Success(feedURL) ⇒ {
            partiallyConstructed +
              (feedURL → FeedInfo(titleAttr, parsePattern(), parseInterval()))
          }
        }
      }

      def parsePattern() = {
        val pattern = Option(outline.getAttributeValue("pattern")) map (_.r)
        if (pattern.isEmpty)
          log.warning("No pattern {}", logDesc)   // XXX should be totally skippped, not even downloaded?
        pattern
      }

      def parseInterval() = {
        val asDuration = Try { intervalAttr map (_.toInt) map (_.seconds) }
        if (asDuration.isFailure) {
          log.warning("Interval '{}' {} is not valid: {}",
                      intervalAttr.get, logDesc, asDuration.failed.get)
        }
        asDuration.toOption.flatten getOrElse defaultInterval
      }
    }
  }

  case class Feed(feedURL: URL) {

    lazy val sfi = new SyndFeedInput

    def parse(): Vector[FeedEntry] = {
      Try { sfi build new XmlReader(feedURL) } match {
        case Failure(e) ⇒ {
          log.warning("Feed '{}' could not be parsed: {}", feedURL, e)
          Vector()
        }
        case Success(feed) ⇒ feed.getEntries.asScala.foldLeft(Vector(): Vector[FeedEntry]) {
          (acc: Vector[FeedEntry], entry: RomeEntry) ⇒
            this.Entry(entry).parseWith(acc)
        }
      }
    }
    
    case class Entry(entry: RomeEntry) {

      lazy val dateAttr = {
        (Option(entry.getUpdatedDate) orElse
          Option(entry.getPublishedDate)) map (_.toInstant)
      }
      lazy val urlAttr: Try[URL] = entry.getLink

      def parseWith(partiallyConstructed: Vector[FeedEntry]) = {
        dateAttr match {
          case None ⇒ {
            log.warning("Feed '{}' contains entry with missing/corrupted date: {}",
                        feedURL, entry.getLink)
            partiallyConstructed
          }
          case Some(date) ⇒ {
            urlAttr match {
              case Failure(e) ⇒ {
                log.warning("URL from feed '{}' is not valid: {}", feedURL, e)
                partiallyConstructed
              }
              case Success(url) ⇒ partiallyConstructed :+ FeedEntry(date, url)
            }
          }
        }
      }
    }
  }

}
