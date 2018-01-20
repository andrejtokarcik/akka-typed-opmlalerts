package opmlalerts

import akka.event.LoggingAdapter
import com.rometools.opml.feed.opml._
import com.rometools.rome.io.{ WireFeedInput, XmlReader }
import java.net.URL
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.matching.Regex
import scala.util.{ Try, Success, Failure }

object Parser {
  final case class FeedInfo(title: Option[String], pattern: Option[Regex], interval: FiniteDuration)
  type FeedInfoMap = Map[URL, FeedInfo]

  lazy val defaultInterval = 1.minute

  def parseOPML(opmlURL: URL, log: LoggingAdapter): FeedInfoMap = {
    val wfi = new WireFeedInput
    Try ({ wfi build new XmlReader(opmlURL) }.asInstanceOf[Opml]) match {
      case Failure(e) ⇒ {
        log.error("OPML '{}' could not be parsed: {}", opmlURL, e.getMessage)
        Map()
      }
      case Success(opml) ⇒ opml.getOutlines.asScala.foldLeft(Map(): FeedInfoMap) {
        (acc: FeedInfoMap, outline: Outline) ⇒ {
          val title = Option(outline.getTitle) orElse Option(outline.getText)
          (outline.getUrl: Try[URL]) match {
            case Failure(e) ⇒ {
              // TODO need to step in recursively
              if (outline.getChildren.asScala.nonEmpty)
                log.info("Skipping an OPML outline group with title {} in OPML '{}'",
                         title, opmlURL)
              else
                log.warning("URL '{}' with title {} from OPML '{}' is not valid: {}",
                            outline.getUrl, title, opmlURL, e)
              acc
            }
            case Success(feedURL) ⇒ {
              val pattern = Option(outline.getAttributeValue("pattern")) map (_.r)
              val interval = {
                val attr = Option(outline.getAttributeValue("interval"))
                val asDuration = Try { attr map (_.toInt) map (_.seconds) }
                if (asDuration.isFailure) {
                  log.warning("Interval '{}' associated with feed '{}' is not valid: {}",
                              attr.get, feedURL, asDuration.failed.get)
                }
                asDuration.toOption.flatten getOrElse defaultInterval
              }
              acc + (feedURL → FeedInfo(title, pattern, interval))
            }
          }
        }
      }
    }
  }

}
