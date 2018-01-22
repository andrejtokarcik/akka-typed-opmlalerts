package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import java.time.Instant

object FeedHandler {
  sealed trait Command
  final case class GetNewEntries(replyTo: ActorRef[NewEntry])
      extends Command

  final case class NewEntry(url: URL)
}

case class FeedHandler(feedURL: URL) {
  import FeedHandler._

  def getNewEntriesSince(lastPolled: Instant): Behavior[Command] =
    Actor.immutable { case (ctx, GetNewEntries(replyTo)) ⇒
      ctx.system.log.info("Fetching and parsing feed '{}'", feedURL)
      val pollTime = Instant.now

      val entries = Parser(ctx.system.log).parseFeed(feedURL)
      val newEntries = entries filter { _.date isAfter lastPolled }
      newEntries foreach { entry ⇒ replyTo ! NewEntry(entry.url) }
      ctx.system.log.info("Feed '{}' had {} new entries", feedURL, newEntries.length)

      getNewEntriesSince(pollTime)
    }
}
