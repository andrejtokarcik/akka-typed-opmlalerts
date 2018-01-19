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
  val basicRSSFeed      = getClass.getResource("/lorem-ipsum.rss")
  val corruptedRSSFeed  = getClass.getResource("/date-corrupted.rss")
  val unparsableRSSFeed = getClass.getResource("/unparsable.rss")

  //val basicAtomFeed     = getClass.getResource("/sample.atom")
  //val corruptedAtomFeed = getClass.getResource("/date-corrupted.rss")
  //val pubDateAtomFeed   = getClass.getResource("/published-updated.atom")

  val nonExistentFeed   = new URL("file:///doesNotExist")
}

object FeedHandlerBehaviorSpec extends FeedHandlerSpec {
  def parseTime(text: String) = {
    val formatter = time.format.DateTimeFormatter.RFC_1123_DATE_TIME
    time.ZonedDateTime.parse(text, formatter).toInstant
  }

  implicit def feedHandlerDSL(feed: URL) = new {
    def fetchNewSince(sinceStr: String) = {
      val since = parseTime(sinceStr)
      val testkit = BehaviorTestkit(fetchNewEntries(feed, since))
      val inbox = TestInbox[NewEntry]()
      testkit.run(PollFeed(inbox.ref))
      inbox.receiveAll
    }

    def havingFetched[T](ids: Iterable[T]) =
      ids map { id ⇒ NewEntry(feed, new URL(s"http://example.com/test/$id")) }
  }
}

class FeedHandlerBehaviorSpec extends WordSpec with Matchers {
  import FeedHandlerBehaviorSpec._

  "fetchNewEntries (qua behavior)" should {

    "emit a NewEntry per new item (RSS)" in {
      val received = basicRSSFeed fetchNewSince "Tue, 16 Jan 2018 02:45:50 GMT"
      val expected = basicRSSFeed havingFetched Vector(1516070880, 1516070820, 1516070760)
      received shouldEqual expected
    }

    "emit no NewEntry if no new items" in {
      val received = basicRSSFeed fetchNewSince "Tue, 16 Jan 2018 02:48:16 GMT"
      received shouldBe empty
    }

    "emit NewEntry's only for entries with incorrupted dates (RSS)" in {
      val received = corruptedRSSFeed fetchNewSince "Tue, 16 Jan 2018 02:43:30 GMT"
      val expected = corruptedRSSFeed havingFetched Vector(1516070760)
      received shouldEqual expected
    }
  }
}

object FeedHandlerAsyncSpec extends FeedHandlerSpec {
  // NOTE: Although the `ActorContextSpec` suite does use `typed.loggers`,
  // the option does not seem to be taken into account and the old `loggers`
  // must be specified instead.
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

  "fetchNewEntries (qua actor)" should {

    "log a warning on parse failure" in {
      val probe = TestProbe[NewEntry]()
      val fetcher = spawn(fetchNewEntries(nonExistentFeed))

      val logMsg = s"Feed '$nonExistentFeed' could not be parsed"
      val filter = EventFilter.warning(start = logMsg, occurrences = 1)
      system.eventStream publish TestEvent.Mute(filter)

      fetcher ! PollFeed(probe.ref)
      probe.expectNoMsg(expectTimeout)
      filter.assertDone(expectTimeout)
    }
  }
}
