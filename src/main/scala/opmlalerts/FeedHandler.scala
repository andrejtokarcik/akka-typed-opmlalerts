package opmlalerts

import akka.actor.typed._
import akka.actor.typed.scaladsl.Actor
import java.net.URL
import java.time.Instant

import opmlalerts.Messages.{ FeedCommand, GetNewEntries, NewEntry }
import opmlalerts.Parser.FeedEntry

object FeedHandler {

  def getNewEntries(feedURL: URL, lastPolled: Instant = Instant.now): Behavior[FeedCommand] =
    Actor.immutable { case (ctx, GetNewEntries(replyTo)) ⇒
      ctx.system.log.info("Fetching and parsing feed '{}'", feedURL)
      val pollTime = Instant.now

      val entries = Parser(ctx.system.log).parseFeed(feedURL)
      val newEntries = entries filter { _.date isAfter lastPolled }
      for (FeedEntry(_, url) ← newEntries)
        replyTo ! NewEntry(feedURL, url)
      ctx.system.log.info("Feed '{}' had {} new entries", feedURL, newEntries.length)

      getNewEntries(feedURL, pollTime)
    }
}
