package opmlalerts

import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import akka.testkit.{ EventFilter, TestEvent }
import com.typesafe.config.ConfigFactory
import java.net.URL
import java.time
import org.scalatest._
import scala.concurrent.duration._

import opmlalerts.FeedHandler._

trait FeedHandlerSpec {

  val basicRSSFeed = getClass.getResource("/lorem-ipsum.rss")
  val corruptedRSSFeed = getClass.getResource("/pubDate-corrupted.rss")
  val nonExistentFeed = new URL("file:///doesNotExist")

  def currentTime = time.Instant.now

}

object FeedHandlerBehaviorSpec extends FeedHandlerSpec {

  def parseTime(text: String) = {
    val formatter = time.format.DateTimeFormatter.RFC_1123_DATE_TIME
    time.ZonedDateTime.parse(text, formatter).toInstant
  }

  implicit def feedDSL(feed: URL) = new {
    def fetchSince(sinceStr: String) = {
      val since = parseTime(sinceStr)
      val testkit = BehaviorTestkit(fetcher(feed, since))
      val inbox = TestInbox[Download]()
      testkit.run(Fetch(inbox.ref))
      inbox.receiveAll
    }
  }

  implicit def id2Download[T](url: T): Download =
    new Download(new URL(s"http://example.com/test/$url"))

  implicit def ids2Downloads[T](urls: Seq[T]): Seq[Download] =
    urls map id2Download

}

class FeedHandlerBehaviorSpec extends WordSpec with Matchers {
  import FeedHandlerBehaviorSpec._

  "fetcher (qua behavior)" should {

    "emit a Download per new item" in {
      val received = basicRSSFeed fetchSince "Tue, 16 Jan 2018 02:45:50 GMT"
      val expected: Seq[Download] = Vector(1516070880, 1516070820, 1516070760)
      received shouldEqual expected
    }

    "emit no Download if no new items" in {
      val received = basicRSSFeed fetchSince "Tue, 16 Jan 2018 02:48:16 GMT"
      received shouldBe empty
    }

    "emit Downloads only for entries with incorrupted dates" in {
      val received = corruptedRSSFeed fetchSince "Tue, 16 Jan 2018 02:43:30 GMT"
      val expected: Seq[Download] = Vector(1516070760)
      received shouldEqual expected
    }

  }
}

object FeedHandlerAsyncSpec extends FeedHandlerSpec {

  // NOTE: Although the `ActorContextSpec` suite does use `typed.loggers`,
  // the option does not seem to be taken into account and the old `loggers`
  // must be specified.
  val config = ConfigFactory.parseString(
    """|akka {
       |  loglevel = WARNING
       |  loggers = ["akka.testkit.TestEventListener"]
       |}""".stripMargin)

  val expectTimeout = 500.millis

}

class FeedHandlerAsyncSpec extends TestKit(FeedHandlerAsyncSpec.config)
  with WordSpecLike with BeforeAndAfterAll {

  import FeedHandlerAsyncSpec._

  "fetcher (qua actor)" should {

    "log a warning on parse failure" in {
      val probe = TestProbe[Download]()
      val fetcherActor = spawn(fetcher(nonExistentFeed, currentTime))

      val filter = EventFilter.warning(start = "An exception occurred while processing " +
        s"feed '$nonExistentFeed'", occurrences = 1)
      system.eventStream publish TestEvent.Mute(filter)

      fetcherActor ! Fetch(probe.ref)
      probe.expectNoMsg(expectTimeout)
      filter.assertDone(expectTimeout)
    }

  }
}
