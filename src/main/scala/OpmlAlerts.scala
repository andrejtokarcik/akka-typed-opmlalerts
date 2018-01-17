package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.{ SyndFeedInput, XmlReader }
import scala.collection.JavaConverters._
import scala.util.{ Try, Success, Failure }
import java.time.Instant

object FeedHandler {
  final case class Fetch(replyTo: ActorRef[Download])
  final case class Download(url: URL)

  lazy val sfi = new SyndFeedInput
  def parseFeed(feed: URL) = Try { sfi build new XmlReader(feed) }
  def extractDate(x: SyndEntry): Option[Instant] = {
    val date = Option(x.getUpdatedDate) orElse Option(x.getPublishedDate)
    date map { _.toInstant }
  }

  //override def preStart(): Unit = log.info("Feed handler of {} started", feed)
  //override def postStop(): Unit = log.info("Feed handler of {} stopped", feed)

  def fetcher(feed: URL, lastFetched: Instant): Behavior[Fetch] =
    Actor.immutable[Fetch] { (ctx, msg) ⇒
      println(ctx.system.log.getClass)
      ctx.system.log.info("Fetching and parsing feed '{}' for latest updates...", feed)
      val maybeParsed = parseFeed(feed)
      maybeParsed match {
        case Success(v) ⇒
          val newEntries = v.getEntries.asScala withFilter (extractDate(_) exists { _ isAfter lastFetched })
          for (entry ← newEntries)
            msg.replyTo ! Download(new URL(entry.getLink))

        case Failure(e) ⇒
          ctx.system.log.info("An exception occurred while processing of feed '{}': {}",
                              feed, e.getMessage)
      }
      Actor.same  // TODO refresh lastFetched with Instant.now  + test
    }
}


//object Main extends App

import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._

object Main extends App {
  import FeedHandler._
  import scala.concurrent.ExecutionContext.Implicits.global

  val fetcherBeh = fetcher(new URL("http://lorem-rss.herokuapp.com/feed"), Instant.now)
  val system: ActorSystem[Fetch] = ActorSystem(fetcherBeh, "fetcher")

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
