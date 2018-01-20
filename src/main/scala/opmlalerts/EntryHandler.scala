package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import scala.io.Source
import scala.util.matching.Regex
import scala.util.{ Try, Success, Failure }
import scalaj.http.{ Http, HttpOptions }

import opmlalerts.Messages.{ AddPattern, EntryCommand, MatchFound, ScanEntry }

object EntryHandler {
  val context = raw"(?:.){0,30}".r

  val scanEntry: Behavior[EntryCommand] = Actor.deferred { ctx ⇒
    ctx.system.log.info("Subscribing to Regex messages")
    val adapter = ctx.spawnAdapter(AddPattern)  // XXX should be ctx.watch'd?
    ctx.system.eventStream.subscribe(adapter, classOf[Regex])
    scanEntryBehavior("".r)
  }

  private def scanEntryBehavior(pattern: Regex): Behavior[EntryCommand] =
    Actor.immutable {
      case (ctx, AddPattern(re)) => {
        ctx.system.log.info("Adding pattern: {}", re)
        val extended = (pattern + "|" + context + re + context).r.unanchored
        scanEntryBehavior(extended)
      }

      case (ctx, ScanEntry(entry, replyTo)) ⇒ {
        ctx.system.log.info("Scanning {} for pattern '{}'", entry, pattern)
        scanStream(entry.url, pattern) match {
          case Success(matchingLines) ⇒
            matchingLines foreach { replyTo ! MatchFound(entry, _) }
          case Failure(fail) ⇒
            ctx.system.log.warning("{} could not be retrieved: {}", entry, fail)
        }
        Actor.same
      }
    }

  def scanStream(url: URL, pattern: Regex) = Try {
    val response = Http(url.toString).option(HttpOptions.followRedirects(true)) execute {
      is ⇒ (Source.fromInputStream(is).getLines flatMap pattern.findFirstIn).toList
    }
    response.body
  }
}
