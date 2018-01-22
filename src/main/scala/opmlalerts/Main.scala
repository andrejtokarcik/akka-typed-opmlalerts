package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.io.File
//import java.nio.file.Path
import scala.Console._
import scala.io.StdIn
import scala.util.Try
import sys.process._

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

  val screenWidth = Try { "tput cols".!!.trim.toInt }.toOption

  val root: Behavior[akka.NotUsed] =
    Actor.deferred { ctx ⇒
      ctx.spawn(Manager.manage(opmlFile), "manager")
      ctx.spawn(Printer.printOnConsole(screenWidth), "printer")

      Actor.empty
    }

  val system = ActorSystem(root, "opmlalerts")

  try {
    println(s"${RESET}${BOLD}*** Press ENTER to exit the system${RESET}")
    StdIn.readLine()
  } finally {
    val _ = system.terminate()
  }
  println("End of demo, bye!")
}
