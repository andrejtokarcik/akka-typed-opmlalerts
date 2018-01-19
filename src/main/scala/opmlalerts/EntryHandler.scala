package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import scala.io.Source
import scala.util.matching.Regex
import scala.util.{ Try, Success, Failure }

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
        val access = Try { Source.fromURL(entry.url) }
        access match {
          case Success(contents) ⇒ {
            ctx.system.log.info("Scanning {} for pattern: {}", entry, pattern)
            val matchingLines = contents.getLines flatMap (pattern.findFirstIn _)
            matchingLines foreach { replyTo ! MatchFound(entry, _) }
          }
          case Failure(fail) ⇒
            ctx.system.log.warning("{} could not be retrieved: {}", entry, fail)
        }
        Actor.same
      }
    }
}
