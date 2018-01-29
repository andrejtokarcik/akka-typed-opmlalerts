package opmlalerts

import akka.event.LoggingAdapter
import com.rometools.opml.feed.opml.{ Opml ⇒ RomeOPML, Outline ⇒ RomeOutline }
import com.rometools.rome.feed.synd.{ SyndEntry ⇒ RomeEntry }
import com.rometools.rome.io.{ SyndFeedInput, WireFeedInput, XmlReader }
import java.nio.file.Path
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

  final case class FeedEntry(title: Option[String],
                             date: Instant,
                             url: URL)

  lazy val wfi = new WireFeedInput
  lazy val sfi = new SyndFeedInput

  def quote[T](o: Option[T]) = o map (x ⇒ s"'$x'") getOrElse "<unspecified>"
}

case class Parser(log: LoggingAdapter) {
  import Parser._

  def parseOPML(opmlPath: Path): Map[URL, FeedInfo] = this.OPML(opmlPath).parse()
  def parseFeed(feedURL: URL): Vector[FeedEntry] = this.Feed(feedURL).parse()

  case class OPML(opmlPath: Path) {

    def parse(): Map[URL, FeedInfo] = {
      Try ({ wfi build new XmlReader(opmlPath.toFile) }.asInstanceOf[RomeOPML]) match {
        case Failure(e) ⇒ {
          log.error("OPML {} could not be parsed: {}", opmlPath, e)
          Map()
        }
        case Success(opml) ⇒ opml.getOutlines.asScala.foldLeft(Map(): Map[URL, FeedInfo]) {
          (acc: Map[URL, FeedInfo], outline: RomeOutline) ⇒
            this.Outline(outline).parseWith(acc)
        }
      }
    }

    case class Outline(outline: RomeOutline) {

      lazy val titleAttr    = Option(outline.getTitle) orElse Option(outline.getText)
      lazy val urlAttr      = Option(outline.getUrl) orElse Option(outline.getXmlUrl) orElse
                              Option(outline.getHtmlUrl)
      lazy val patternAttr  = Option(outline.getAttributeValue("pattern"))
      lazy val intervalAttr = Option(outline.getAttributeValue("interval"))

      lazy val defaultInterval = 60.seconds

      lazy val logDesc = {
        val titleDesc = s"title ${quote(titleAttr)}"
        val urlDesc = s"URL ${quote(urlAttr)}"
        s"associated with ${titleDesc} and ${urlDesc} in OPML ${opmlPath}"
      }

      def parseWith(partiallyConstructed: Map[URL, FeedInfo]) = {
         Try { new URL(urlAttr.get) } match {
          case Failure(e) ⇒ {
            if (outline.getChildren.asScala.nonEmpty) {  // TODO need to step in recursively
              log.warning("Skipping outline group {}", logDesc)
            } else {
              log.warning("URL '{}' {} is not valid: {}", quote(urlAttr), logDesc, e)
            }
            partiallyConstructed
          }
          case Success(feedURL) ⇒ {
            partiallyConstructed +
              (feedURL → FeedInfo(titleAttr, parsePattern(), parseInterval()))
          }
        }
      }

      def parsePattern() = {
        val asRegex = Try { patternAttr map (_.r) }
        if (asRegex.isFailure) {
          log.warning("Pattern '{}' {} is not valid: {}",
                      patternAttr.get, logDesc, asRegex.failed.get)
        }
        val pattern = asRegex.toOption.flatten
        if (pattern.isEmpty)
          log.warning("No pattern {}", logDesc)  // TODO should not be added to feedMap?
        pattern
      }

      def parseInterval() = {
        val asDuration = Try { intervalAttr map (_.toInt.seconds) }
        if (asDuration.isFailure) {
          log.warning("Interval '{}' {} is not valid: {}",
                      intervalAttr.get, logDesc, asDuration.failed.get)
        }
        asDuration.toOption.flatten getOrElse defaultInterval
      }
    }
  }

  case class Feed(feedURL: URL) {

    def parse(): Vector[FeedEntry] = {
      Try { sfi build new XmlReader(feedURL) } match {
        case Failure(e) ⇒ {
          log.warning("Feed {} could not be parsed: {}", feedURL, e)
          Vector()
        }
        case Success(feed) ⇒ feed.getEntries.asScala.foldLeft(Vector(): Vector[FeedEntry]) {
          (acc: Vector[FeedEntry], entry: RomeEntry) ⇒
            this.Entry(entry).parseWith(acc)
        }
      }
    }
    
    case class Entry(entry: RomeEntry) {

      lazy val titleAttr = Option(entry.getTitle)
      lazy val dateAttr  = Option(entry.getUpdatedDate) orElse Option(entry.getPublishedDate)
      lazy val urlAttr   = Option(entry.getLink)

      def parseWith(partiallyConstructed: Vector[FeedEntry]) = {
        dateAttr map (_.toInstant) match {
          case None ⇒ {
            log.warning("Feed {} contains entry with missing/corrupted date: {}",
                        feedURL, quote(dateAttr))
            partiallyConstructed
          }
          case Some(date) ⇒ {
            Try { new URL(urlAttr.get) } match {
              case Failure(e) ⇒ {
                log.warning("Feed {} contains entry with missing/corrupted URL: {} -- {}",
                            feedURL, quote(urlAttr), e)
                partiallyConstructed
              }
              case Success(url) ⇒ partiallyConstructed :+ FeedEntry(titleAttr, date, url)
            }
          }
        }
      }
    }
  }

}
