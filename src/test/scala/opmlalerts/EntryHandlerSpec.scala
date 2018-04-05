package opmlalerts

import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import java.net.URL
import java.time
import scala.util.matching.Regex

import opmlalerts.EntryHandler._

sealed trait EntryHandlerSpec

object EntryHandlerSyncSpec {
  implicit class EntryDSL(page: URL) {
    def scannedFor(pattern: String) = {
      val testKit = BehaviorTestKit(EntryHandler.scanEntry)
      val inbox = TestInbox[MatchFound]()
      testKit.run(ScanEntry(page, pattern.r, inbox.ref))
      inbox.receiveAll
    }
  }
}

class EntryHandlerSyncSpec extends CommonSyncSpec with EntryHandlerSpec {
  import EntryHandlerSyncSpec._
  import TestPages._

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

object EntryHandlerAsyncSpec {
  val irretrievable = (entry: URL) ⇒ s"Entry $entry could not be retrieved"
}

class EntryHandlerAsyncSpec extends CommonTestKit with EntryHandlerSpec {
  import EntryHandlerAsyncSpec._
  import TestPages._

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
