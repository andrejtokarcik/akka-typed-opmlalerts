package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import scala.concurrent.Future

import opmlalerts.ImmutableRoundRobin._

object Manager {
  final case class Register(opml: URL)
  final case object PollAll  // TODO regularly via timer/scheduler after Register

  def poolManager(entryHandlerPoolSize: Int) =
    Actor.deferred[WorkerResponse] { ctx ⇒
      val roundRobin = roundRobinBehavior(entryHandlerPoolSize, EntryHandler.scanEntry)
      val entryHandlerPool = ctx.spawn(roundRobin, "entryHandlerPool")
      poolManagerBehavior(entryHandlerPool)
    }

  private def poolManagerBehavior(entryHandlerPool: ActorRef[EntryCommand]) =
    Actor.immutable[WorkerResponse] {
      case (ctx, NewEntry(url)) ⇒ {
        //ctx.system.log.debug("Received ...")  -- root debug, worker info
        entryHandlerPool ! ScanEntry(url, ctx.self)
        Actor.same
      }

      // TODO fetch title via FeedHandler for pretty printing
      case (ctx, m @ MatchFound(_, _)) ⇒ {
        implicit val ec = ctx.executionContext
        Future { prettyPrint(m) }
        Actor.same
      }
    }

  val prettyPrint: MatchFound ⇒ Unit = println
}
