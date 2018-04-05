package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import java.net.URL
import java.time.Instant

object FeedHandler {
  sealed trait Command
  final case class GetNewEntries(replyTo: ActorRef[NewEntry])
      extends Command

  final case class NewEntry(entry: Parser.FeedEntry)
}

case class FeedHandler(feedURL: URL) {
  import FeedHandler._

  def getNewEntriesSince(lastPoll: Instant): Behavior[Command] =
    Behaviors.immutable { case (ctx, GetNewEntries(replyTo)) ⇒
      ctx.system.log.debug("Fetching and parsing feed {}", feedURL)
      val pollTime = Instant.now

      val entries = Parser(ctx.system.log).parseFeed(feedURL)
      val newEntries = entries filter { _.date isAfter lastPoll }
      newEntries foreach { replyTo ! NewEntry(_) }
      ctx.system.log.info("Feed {} has {} new entries", feedURL, newEntries.length)

      getNewEntriesSince(pollTime)
    }
}
