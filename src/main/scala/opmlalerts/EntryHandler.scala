package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import scala.io.Source
import scala.util.matching.Regex
import scala.util.{ Try, Success, Failure }
import scalaj.http.{ Http, HttpOptions }

object EntryHandler {
  sealed trait Command
  final case class ScanEntry(entryURL: URL,
                             pattern: Regex,
                             replyTo: ActorRef[MatchFound])
      extends Command

  final case class MatchFound(firstWithContext: String, numMatches: Int)

  def scanEntry: Behavior[Command] =
    Actor.immutable {
      case (ctx, ScanEntry(entryURL, pattern, replyTo)) ⇒ {
        ctx.system.log.info("Scanning {} for pattern '{}'", entryURL, pattern)
        scanStream(entryURL, pattern.withContext) match {
          case Success(matchedSections) ⇒
            matchedSections.headOption foreach
              { replyTo ! MatchFound(_, matchedSections.length) }
          case Failure(fail) ⇒
            ctx.system.log.warning("{} could not be retrieved: {}", entryURL, fail)
        }
        Actor.same
      }
    }

  private implicit class RegexWithContext(re: Regex) {
    val withContext = {
      val context = raw"(?:.){0,30}"
      (context + re + context).r.unanchored
    }
  }

  def scanStream(url: URL, pattern: Regex) = Try {
    val response = Http(url.toString).option(HttpOptions.followRedirects(true)) execute {
      is ⇒ Source.fromInputStream(is).getLines.flatMap(pattern.findFirstIn).toList
    }
    response.body
  }
}
