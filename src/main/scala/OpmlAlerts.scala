package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import java.net.URL
import com.rometools.rome.io.SyndFeedInput
//import java.util.Calendar
//import java.util.Date
//import scala.util.{Try, Success, Failure}

import scala.concurrent.Future
import scala.concurrent.duration._


object FeedHandler {
  final case class Fetch(replyTo: ActorRef[Download])
  final case class Download(urls: List[URL])

  val sfi = new SyndFeedInput

  //override def preStart(): Unit = log.info("Feed handler of {} started", feed)
  //override def postStop(): Unit = log.info("Feed handler of {} stopped", feed)

  def fetcher(feed: URL): Behavior[Fetch] =
    Actor.immutable[Fetch] { (ctx, msg) ⇒
      ctx.system.log.info("Fetching {}...", feed)
      msg.replyTo ! Download(List())
      Actor.same
    }
}

object Main extends App {
  import FeedHandler._
  import scala.concurrent.ExecutionContext.Implicits.global

  val system: ActorSystem[Fetch] = ActorSystem(fetcher(new URL("http://localhost")), "fetcher")

  implicit val timeout = Timeout(5.seconds)
  implicit val sched = system.scheduler
  val future: Future[Download] = system ? (Fetch(_))

  for {
    urls ← future recover { case _ ⇒ List() }
    done ← {
      println(s"result: $urls")
      system.terminate()
    }
  } println("system terminated")
}
