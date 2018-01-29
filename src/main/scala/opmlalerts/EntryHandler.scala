package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import scala.util.matching.Regex
import scala.util.{ Try, Success, Failure }

object EntryHandler {
  sealed trait Command
  final case class ScanEntry(entryURL: URL,
                             pattern: Regex,
                             replyTo: ActorRef[MatchFound])
      extends Command

  final case class MatchFound(matchedSection: String, numMatches: Int)

  def scanEntry: Behavior[Command] =
    Actor.immutable {
      case (ctx, ScanEntry(entryURL, pattern, replyTo)) ⇒ {
        ctx.system.log.debug("Scanning entry {} for pattern '{}'", entryURL, pattern)
        scanWithBrowser(entryURL, pattern.withContext) match {
          case Success(sections) ⇒
            if (sections.nonEmpty)
              { replyTo ! MatchFound(sections.matched, sections.size) }
          case Failure(fail) ⇒
            ctx.system.log.warning("Entry {} could not be retrieved: {}", entryURL, fail)
        }
        Actor.same
      }
    }

  private implicit class RegexWithContext(re: Regex) {
    val withContext = {
      val context = raw"(?:.{0,30})"
      (raw"(?<=\s)" + context + re + context + raw"(?=\s)").r.unanchored
    }
  }

  lazy val browser = JsoupBrowser()
  def scanWithBrowser(url: URL, pattern: Regex) = Try {
    val stream = url.openStream()
    val text = browser.parseInputStream(stream).body.text
    pattern findAllIn text
  }
}
