package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import scala.Console._

object Printer {
  sealed trait Command
  final case class PrintMatch(feedURL: URL,
                              feed: Parser.FeedInfo,
                              entry: Parser.FeedEntry,
                              matchFound: EntryHandler.MatchFound)
      extends Command

  val ServiceKey = receptionist.ServiceKey[Command]("Printer")

  def printOnConsole(maybeWidth: Option[Int]): Behavior[Command] =
    Actor.deferred { ctx ⇒
      import receptionist.Receptionist.Register
      ctx.system.receptionist ! Register(ServiceKey, ctx.self, ctx.system.deadLetters)

      val screenWidth = maybeWidth getOrElse 80
      Actor.immutable {
        (_, msg) ⇒ {
          doPrintOnConsole(msg, screenWidth)
          Thread.sleep(1000)  // avoid flooding
          Actor.same
        }
      }
    }

  def doPrintOnConsole(msg: Command, screenWidth: Int) = {
    msg match {
      case PrintMatch(feedURL, feed, entry, matched) ⇒ {
        println("=" * screenWidth)
        if (feed.title.isDefined)
          printBit("Feed title", feed.title.get)
        printBit("Feed URL", feedURL)
        if (entry.title.isDefined)
          printBit("Entry title", entry.title.get)
        printBit("Entry URL", entry.url)
        printBit("Entry updated", entry.date)
        if (feed.pattern.isDefined)
          printBit("Pattern", feed.pattern.get)
        printBit("Number of matches", matched.numMatches)
        printBit("Sample match", matched.matchedSection)
        println("=" * screenWidth)
        flush()
      }
    }
  }

  private def printBit(desc: String, bit: Any) = {
    println(s"${RESET}${BOLD}${desc}:${RESET} ${bit}")
  }

}
