package opmlalerts

import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import java.net.URL
import java.time
import org.scalatest._
import scala.concurrent.duration._

import opmlalerts.FeedHandler._

trait FeedHandlerSpec {
  val basicFeed         = getClass.getResource("/lorem-ipsum.rss")
  val corruptedDateFeed = getClass.getResource("/corrupted-date.rss")
  val corruptedURLFeed  = getClass.getResource("/corrupted-url.rss")
  val unparsableFeed    = getClass.getResource("/unparsable.rss")

  val nonExistentFeed   = new URL("file:///doesNotExist")

  val now = time.Instant.now
}

object FeedHandlerSyncSpec extends FeedHandlerSpec {

  def parseTime(text: String) = {
    val formatter = time.format.DateTimeFormatter.RFC_1123_DATE_TIME
    time.ZonedDateTime.parse(text, formatter).toInstant
  }

  implicit class feedHandlerDSL(feed: URL) {
    def getNewSince(timeStr: String) = {
      val time = parseTime(timeStr)
      val testkit = BehaviorTestkit(FeedHandler(feed) getNewEntriesSince time)
      val inbox = TestInbox[NewEntry]()
      testkit.run(GetNewEntries(inbox.ref))
      inbox.receiveAll map { _.entry.url }
    }
  }

  def exampleEntries[T](ids: T*) =
    ids map { id ⇒ new URL(s"http://example.com/test/$id") }
}

class FeedHandlerSyncSpec extends WordSpecLike with Matchers {
  import FeedHandlerSyncSpec._

  "getNewEntriesSince (qua behavior)" should {

    "emit a NewEntry per new item" in {
      val received = basicFeed getNewSince "Tue, 16 Jan 2018 02:45:50 GMT"
      val expected = exampleEntries(1516070880, 1516070820, 1516070760)
      received shouldEqual expected
    }

    "emit no NewEntry if no new items" in {
      val received = basicFeed getNewSince "Tue, 16 Jan 2018 02:48:16 GMT"
      received shouldBe empty
    }

    "emit NewEntry's only for entries with incorrupted dates" in {
      val received = corruptedDateFeed getNewSince "Tue, 16 Jan 2018 02:43:30 GMT"
      val expected = exampleEntries(1516070760)
      received shouldEqual expected
    }

    "emit NewEntry's only for entries with incorrupted URLs" in {
      val received = corruptedURLFeed getNewSince "Tue, 16 Jan 2018 02:44:30 GMT"
      val expected = exampleEntries(1516070820)
      received shouldEqual expected
    }
  }
}

object FeedHandlerAsyncSpec extends FeedHandlerSpec {
  val expectTimeout = 500.millis

  val unparsable = (feed: URL) ⇒ s"Feed $feed could not be parsed"
  val corruptedDate = (feed: URL) ⇒ s"Feed $feed contains entry with missing/corrupted date"
  val corruptedURL = (feed: URL) ⇒ s"Feed $feed contains entry with missing/corrupted URL"
}

class FeedHandlerAsyncSpec extends TestKitExt with WordSpecLike {
  import FeedHandlerAsyncSpec._

  implicit class FeedDSL(feed: URL) {
    def shouldLogWarning(msg: URL ⇒ String) =
      shouldLogWarnings(1, msg)

    def shouldLogWarnings(num: Int, msg: URL ⇒ String) = {
      val probe = TestProbe[NewEntry]()
      val feedHandler = spawn(FeedHandler(feed) getNewEntriesSince now)
      expectWarning(msg(feed), num) {
        feedHandler ! GetNewEntries(probe.ref)
      }
    }
  }

  "getNewEntriesSince (qua actor)" should {
    "log a warning when feed does not exist" in {
      nonExistentFeed shouldLogWarning unparsable
    }

    "log a warning when feed cannot be parsed" in {
      unparsableFeed shouldLogWarning unparsable
    }

    "log a warning when feed contains corrupted date fields" in {
      corruptedDateFeed shouldLogWarnings (3, corruptedDate)
    }

    "log a warning when feed contains corrupted URL fields" in {
      corruptedURLFeed shouldLogWarnings (3, corruptedURL)
    }
  }
}
