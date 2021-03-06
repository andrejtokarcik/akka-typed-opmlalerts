package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.scaladsl.adapter._
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.MonitorActor
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._
import scala.io.StdIn
import scala.util.Try
import sys.process._

object Main extends App {

  def exitWithError(error: String) = {
    println(fansi.Bold.On(fansi.Color.Red(error)))
    sys.exit(1)
  }

  val opmlPathStr =
    args.headOption getOrElse exitWithError("Specify path to an OPML file")

  val opmlPath = {
    val t = Try { Paths.get(opmlPathStr) }
    if (t.isFailure)
      exitWithError(s"'$opmlPathStr' is not a valid path: ${t.failed.get}")
    t.get
  }

  def spawnManager(ctx: ActorContext[EventAtPath]) =
    ctx.spawnAnonymous(Manager.manage(opmlPath))

  val root: Behavior[EventAtPath] = Behaviors.setup { ctx ⇒
    val screenWidth = Try { "tput cols".!!.trim.toInt }.toOption
    ctx.system.log.debug("Spawning printer with screen width = {}", screenWidth)
    ctx.spawn(Printer.printOnConsole(screenWidth), "printer")

    ctx.system.log.debug("Spawning file monitor for {}", opmlPath)
    val fileMonitor = ctx.actorOf(MonitorActor(concurrency = 1), "fileMonitor")
    fileMonitor ! RegisterSubscriber(event = ENTRY_MODIFY, path = opmlPath,
                                     subscriber = ctx.self.toUntyped)

    ctx.system.log.debug("Spawning manager with OPML file {}", opmlPath)
    restarter(spawnManager(ctx))
  }

  def restarter(manager: ActorRef[Manager.Message]): Behavior[EventAtPath] =
    Behaviors.immutable {
      case (ctx, _: EventAtPath) ⇒ {
        ctx.system.log.info("OPML {} updated, restarting", opmlPath)
        ctx.stop(manager)
        restarter(spawnManager(ctx))
      }
    }

  val system = ActorSystem(root, "opmlalerts")

  try {
    println(fansi.Bold.On("***** Press ENTER to exit the system"))
    StdIn.readLine()
  } finally {
    val _ = system.terminate()
  }
  println("Bye!")
}
