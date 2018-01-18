package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist
import akka.actor.typed.scaladsl.Actor

object Printer {
  val ServiceKey = receptionist.ServiceKey[PrintCommand]("Printer")

  def printOnConsole(screenWidth: Int): Behavior[PrintCommand] = Actor.deferred { ctx ⇒
    import receptionist.Receptionist.Register
    ctx.system.receptionist ! Register(ServiceKey, ctx.self, ctx.system.deadLetters)

    Actor.immutable {
      (_, msg) ⇒ {
        doPrintOnConsole(screenWidth, msg)
        Actor.same
      }
    }
  }

  private def doPrintOnConsole(screenWidth: Int, msg: PrintCommand) = {
    msg match {
      case PrintResult(feedTitle, entryURL, matchedSection) ⇒ {
        import scala.Console._
        println(s"${RESET}${BOLD}Feed title:${RESET} $feedTitle")
        println(s"${RESET}${BOLD}Entry URL:${RESET} $entryURL")
        println(s"${RESET}${BOLD}Matched section:${RESET}")
        println(matchedSection)
        print("=" * screenWidth)
        flush()
      }
    }
  }
}
