package opmlalerts

import akka.actor.typed.ActorRef
import java.net.URL
import scala.util.matching.Regex

sealed trait WorkMessage

sealed trait FeedCommand extends WorkMessage
final case class PollFeed(replyTo: ActorRef[NewEntry]) extends FeedCommand

sealed trait EntryCommand extends WorkMessage
final case class AddPattern(re: Regex) extends EntryCommand
final case class ScanEntry(url: URL, replyTo: ActorRef[MatchFound]) extends EntryCommand

sealed trait WorkerResponse extends WorkMessage
final case class NewEntry(url: URL) extends WorkerResponse
final case class MatchFound(url: URL, matchedSection: String) extends WorkerResponse
