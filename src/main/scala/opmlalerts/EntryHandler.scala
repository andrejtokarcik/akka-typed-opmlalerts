package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import scala.io.Source
import scala.util.matching.Regex

object EntryHandler {
  // TODO move all messages to a separate object/module?
  // (a package object perhaps?)
  sealed trait Command
  final case class AddPattern(re: Regex) extends Command
  final case class ScanEntry(url: URL, replyTo: ActorRef[MatchFound]) extends Command
  final case class MatchFound(url: URL, matchedSection: String)

  val context = raw"(?:.){0,30}".r

  val scanEntry: Behavior[Command] = Actor.deferred { ctx ⇒
    ctx.system.log.info("Subscribing to Regex messages")
    val adapter = ctx.spawnAdapter(AddPattern)  // XXX should be ctx.watch'd?
    ctx.system.eventStream.subscribe(adapter, classOf[Regex])
    scanEntryBeh("".r)
  }

  private def scanEntryBeh(pattern: Regex): Behavior[Command] =
    Actor.immutable {
      case (ctx, AddPattern(re)) => {
        ctx.system.log.info("Adding pattern: {}", re)
        val extended = (pattern + "|" + context + re + context).r.unanchored
        scanEntryBeh(extended)
      }

      case (ctx, ScanEntry(url, replyTo)) ⇒ {
        ctx.system.log.info("Scanning entry '{}' for pattern: {}", url, pattern)
        val contents = Source.fromURL(url)
        val matchingLines = contents.getLines flatMap (pattern.findFirstIn _)
        matchingLines foreach { replyTo ! MatchFound(url, _) }
      }

      Actor.same
    }
}
