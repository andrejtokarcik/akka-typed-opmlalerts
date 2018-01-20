package opmlalerts

import akka.actor.typed._
import java.io.File
//import java.nio.file.Path
import scala.Console._
import scala.io.StdIn
import scala.util.Try

import opmlalerts.Manager._

object Main extends App {
  def exitWithError(error: String) = {
    println(s"${RESET}${BOLD}${RED}$error${RESET}")
    sys.exit(1)
  }

  // TODO watch
  val opmlPathStr: String = args.headOption getOrElse
    exitWithError("Specify path to an OPML file")
  //val opmlPath: Path = (opmlPathStr: Try[Path]) getOrEffect
  //  { case e ⇒ exitWithError(s"'$opmlPathStr' is not a path: $e") }
  val opmlFile: File = Try { new File(opmlPathStr) } getOrEffect
    { e ⇒ exitWithError(s"'$opmlPathStr' is not a file: $e") }

  val manager = manageActors(opmlFile)
  val system = ActorSystem(manager, "opml-alerts")

  // TODO create printer

  try {
    println(s"${RESET}${BOLD}*** Press ENTER to exit the system${RESET}")
    StdIn.readLine()
  } finally {
    val _ = system.terminate()
  }
  println("End of demo, bye!")
}
