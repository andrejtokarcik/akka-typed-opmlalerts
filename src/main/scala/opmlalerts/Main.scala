package opmlalerts

import akka.actor.typed._
import java.net.URL
import scala.Console._
import scala.io.StdIn
import scala.util.Try

import opmlalerts.Manager._

object Main extends App {
  def exitWithError(error: String) = {
    println(s"${RESET}${BOLD}${RED}$error${RESET}")
    sys.exit(1)
  }

  val opmlStr: String = args.headOption getOrElse
    exitWithError("Argument required: URL pointing to an OPML file")
  val maybeURL: Try[URL] = opmlStr
  if (maybeURL.isFailure)
    exitWithError(s"'$opmlStr' is not a valid URL: ${maybeURL.failed.get.getMessage}")
  val opmlURL = maybeURL.get

  val manager = manageActors(opmlURL)
  val system: ActorSystem[ManagerMessage] = ActorSystem(manager, "opml-alerts")

  // TODO create printer

  try {
    println(s"${RESET}${BOLD}*** Press ENTER to exit the system${RESET}")
    StdIn.readLine()
  } finally {
    val _ = system.terminate()
  }
  println("End of demo, bye!")
}
