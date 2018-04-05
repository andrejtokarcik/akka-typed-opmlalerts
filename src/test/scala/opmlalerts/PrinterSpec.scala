package opmlalerts

import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import java.io.ByteArrayOutputStream
import scala.util.matching.Regex

import opmlalerts.EntryHandler.MatchFound
import opmlalerts.Parser.{ FeedEntry, FeedInfo }
import opmlalerts.Printer._

sealed trait PrinterSpec

object PrinterSyncSpec {
  val screenWidth = Some(5)
  val testKit = BehaviorTestKit(printOnConsole(screenWidth, register = false))

  def gatherOutput(msg: PrintMatch) = {
    val stream = new ByteArrayOutputStream()
    Console.withOut(stream) {
      testKit.run(msg)
    }
    fansi.Str(stream.toString).plainText
  }
}

class PrinterSyncSpec extends CommonSyncSpec with PrinterSpec {
  import PrinterSyncSpec._
  import TestFeeds.basicFeed
  import TestPages.basicPage

  def testWith(feedTitle: Option[String] = None, feedPattern: Option[Regex] = None,
               entryTitle: Option[String] = None, matchedSection: String, numMatches: Int) = {

    val feedInfo = FeedInfo(feedTitle, feedPattern, someInterval)
    val feedEntry = FeedEntry(entryTitle, someTime, basicPage)
    val matched = MatchFound(matchedSection, numMatches)

    def optionalFieldDesc[T](desc: String, o: Option[T]) =
      o.fold("")(x ⇒ s"$desc: $x\n")
    val feedTitleField = optionalFieldDesc("Feed title", feedTitle)
    val feedPatternField = optionalFieldDesc("Pattern", feedPattern)
    val entryTitleField = optionalFieldDesc("Entry title", entryTitle)

    val expected =
      s"""|=====
          |${feedTitleField}Feed URL: ${basicFeed}
          |${entryTitleField}Entry URL: ${basicPage}
          |Entry updated: $someTime
          |${feedPatternField}Number of matches: $numMatches
          |Sample match: $matchedSection
          |=====
          |""".stripMargin

    val output = gatherOutput(PrintMatch(basicFeed, feedInfo, feedEntry, matched))
    output shouldEqual expected
  }

  "printOnConsole (qua behavior)" should {
    "format and print a minimal match" in {
      testWith(matchedSection = "matched", numMatches = 100)
    }

    "format and print a maximal match" in {
      testWith(feedTitle = Some("Feed F"), feedPattern = Some(".*".r),
               entryTitle = Some("Entry E"), matchedSection = "matched", numMatches = 100)
    }
  }
}

class PrinterAsyncSpec extends CommonTestKit with PrinterSpec {
  "printOnConsole (qua actor)" should {
    "register with the receptionist when spawned" in {
      expectWarningOfPattern(raw"Registered\(ServiceKey\[opmlalerts\.Printer\$$Command\]\(Printer\)") {
        spawn(printOnConsole())
      }
    }
  }
}
