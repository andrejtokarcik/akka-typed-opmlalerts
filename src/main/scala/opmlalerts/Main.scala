package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._

import opmlalerts.FeedHandler._

object Main extends App {
  import scala.concurrent.ExecutionContext.Implicits.global

  val poller = pollForNewEntries("http://lorem-rss.herokuapp.com/feed")
  val system: ActorSystem[Poll] = ActorSystem(poller, "single-poller")

  implicit val timeout = Timeout(500.millis)
  implicit val sched = system.scheduler
  val future: Future[FeedEntry] = system ? (Poll(_))

  for {
    urls ← future recover { case _ ⇒ List() }
    done ← {
      println(s"result: $urls")
      system.terminate()
    }
  } println("system terminated")
}
