package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import scala.Console._

object Printer {
  sealed trait Command
  final case class PrintMatch(feedTitle: Option[String],
                               feedURL: URL,
                               entryURL: URL,
                               matchFound: EntryHandler.MatchFound)
      extends Command

  val ServiceKey = receptionist.ServiceKey[Command]("Printer")

  def printOnConsole(maybeWidth: Option[Int]): Behavior[Command] =
    Actor.deferred { ctx ⇒
      import receptionist.Receptionist.Register
      ctx.system.receptionist ! Register(ServiceKey, ctx.self, ctx.system.deadLetters)

      val screenWidth = maybeWidth getOrElse 80
      ctx.system.log.info("Spawned printer with screen width = {}", screenWidth)

      Actor.immutable {
        (_, msg) ⇒ {
          doPrintOnConsole(msg, screenWidth)
          Actor.same
        }
      }
    }

  def doPrintOnConsole(msg: Command, screenWidth: Int) = {
    msg match {
      case PrintMatch(feedTitle, feedURL, entryURL, matched) ⇒ {
        if (feedTitle.isDefined)
          printBit("Feed title", feedTitle.get)
        printBit("Feed URL", feedURL)
        printBit("Entry URL", entryURL)
        printBit("Number of matches", matched.numMatches)
        printBit("One of the matches", matched.matchedSection)
        println("=" * screenWidth)
        flush()
      }
    }
  }

  private def printBit(desc: String, bit: Any) = {
    println(s"${RESET}${BOLD}${desc}:${RESET} ${bit}")
  }

}
