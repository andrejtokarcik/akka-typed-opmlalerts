package opmlalerts

import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import java.net.URL
import java.time
import scala.concurrent.duration._
import scala.util.matching.Regex

import opmlalerts.EntryHandler._

trait EntryHandlerSpec {
  val basicPage       = getClass.getResource("/moby-dick.html")

  val nonExistentPage = getClass.getResource("/doesNotExist")
}

object EntryHandlerSyncSpec extends EntryHandlerSpec {
  implicit class EntryDSL(page: URL) {
    def scannedFor(pattern: String) = {
      val testkit = BehaviorTestkit(EntryHandler.scanEntry)
      val inbox = TestInbox[MatchFound]()
      testkit.run(ScanEntry(page, pattern.r, inbox.ref))
      inbox.receiveAll
    }
  }
}

class EntryHandlerSyncSpec extends CustomSyncSpec {
  import EntryHandlerSyncSpec._

  "scanEntry (qua behavior)" should {
    "emit a MatchFound when entry matched by pattern" in {
      val received = basicPage scannedFor "[a-z]+ed in"
      val matched = MatchFound("summer-cool weather that now reigned in these latitudes, and in", 3)
      received shouldEqual Seq(matched)
    }

    "emit no MatchFound when entry not matched by pattern" in {
      val received = basicPage scannedFor "haskell"
      received shouldBe empty
    }
  }
}

object EntryHandlerAsyncSpec extends EntryHandlerSpec {
  val irretrievable = (entry: URL) ⇒ s"Entry $entry could not be retrieved"
}

class EntryHandlerAsyncSpec extends CustomAsyncSpec {
  import EntryHandlerAsyncSpec._

  implicit class PageDSL(page: URL) {
    val somePattern = ".*".r

    def shouldLogWarning(msg: URL ⇒ String) = {
      val entryHandler = spawn(EntryHandler.scanEntry)
      val probe = TestProbe[MatchFound]()
      expectWarning(msg(page)) {
        entryHandler ! ScanEntry(page, somePattern, probe.ref)
      }
    }
  }

  "scanEntry (qua actor)" should {
    "log a warning when entry page cannot be retrieved" in {
      nonExistentPage shouldLogWarning irretrievable
    }
  }
}