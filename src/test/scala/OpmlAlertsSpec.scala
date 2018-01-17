package opmlalerts

import akka.testkit.typed._
import java.net.URL
import org.scalatest.{ Matchers, WordSpec }
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import java.time
import org.scalactic.TypeCheckedTripleEquals._

import opmlalerts.FeedHandler._

object FeedHandlerBehaviorSpec {

  lazy val basicRSSFeed = getClass.getResource("/lorem-ipsum.rss")
  lazy val corruptedRSSFeed = getClass.getResource("/pubDate-corrupted.rss")
  lazy val nonExistentFeed = new URL("file:///doesNotExist")

  lazy val currentTime = time.Instant.now

  def parseTime(text: String) = {
    val formatter = time.format.DateTimeFormatter.RFC_1123_DATE_TIME
    time.ZonedDateTime.parse(text, formatter).toInstant
  }

  def getReceivedSince(sinceStr: String)(implicit feed: URL) = {
    val since = parseTime(sinceStr)
    val testkit = BehaviorTestkit(fetcher(feed, since))
    val inbox = TestInbox[Download]()
    testkit.run(Fetch(inbox.ref))
    inbox.receiveAll
  }

  implicit def url2download[T](url: T): Download =
    new Download(new URL(s"http://example.com/test/$url"))

  implicit def urls2downloads[T](urls: Seq[T]): Seq[Download] =
    urls map url2download
}

class FeedHandlerBehaviorSpec extends WordSpec with Matchers {
  import FeedHandlerBehaviorSpec._

  "fetcher" should {

    "emit Download for new items" in {
      implicit val feed = basicRSSFeed
      val received  = getReceivedSince("Tue, 16 Jan 2018 02:45:50 GMT")
      val downloads: Seq[Download] = Vector(1516070880, 1516070820, 1516070760)
      received shouldEqual downloads
    }

    "emit no Download if no new items" in {
      implicit val feed = basicRSSFeed
      val received = getReceivedSince("Tue, 16 Jan 2018 02:48:16 GMT")
      received shouldBe empty
    }

    "emit Download only for entries with incorrupted dates" in {
      implicit val feed = corruptedRSSFeed
      val received = getReceivedSince("Tue, 16 Jan 2018 02:43:30 GMT")
      val downloads: Seq[Download] = Vector(1516070760)
      received shouldEqual downloads
    }

    // TODO test cases
    // - check log on parse failure

  }
}
