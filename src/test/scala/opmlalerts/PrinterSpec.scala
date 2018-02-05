package opmlalerts

import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import java.io.ByteArrayOutputStream

import opmlalerts.EntryHandler.MatchFound
import opmlalerts.Parser.{ FeedEntry, FeedInfo }
import opmlalerts.Printer._

trait PrinterSpec {
  val screenWidth = Some(5)

  // FIXME copied from {Feed,Entry}HandlerSpec's
  val basicFeed = getClass.getResource("/lorem-ipsum.rss")
  val basicPage = getClass.getResource("/moby-dick.html")
}

object PrinterSyncSpec extends PrinterSpec {
  val testkit = BehaviorTestkit(printOnConsole(screenWidth, register = false))
}

class PrinterSyncSpec extends CommonSyncSpec {
  import PrinterSyncSpec._

  "printOnConsole (qua behavior)" should {
    "format and print a minimal match" in {
      val feedInfo = FeedInfo(None, None, someInterval)
      val feedEntry = FeedEntry(None, someTime, basicPage)
      val matched = MatchFound("matched", 100)

      val stream = new ByteArrayOutputStream()
      Console.withOut(stream) {
        testkit.run(PrintMatch(basicFeed, feedInfo, feedEntry, matched))
      }

      val expected =
        s"""|=====
            |Feed URL: $basicFeed
            |Entry URL: $basicPage
            |Entry updated: $someTime
            |Number of matches: 100
            |Sample match: matched
            |=====
            |""".stripMargin
      fansi.Str(stream.toString).plainText shouldEqual expected
    }
  }
}
