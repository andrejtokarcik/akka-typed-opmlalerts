package opmlalerts

import akka.actor.typed.ActorRef
import java.net.URL
import scala.util.matching.Regex

sealed trait WorkMessage

sealed trait FeedCommand extends WorkMessage
final case class PollFeed(replyTo: ActorRef[NewEntry]) extends FeedCommand

sealed trait EntryCommand extends WorkMessage
final case class AddPattern(re: Regex) extends EntryCommand
final case class ScanEntry(entry: NewEntry, replyTo: ActorRef[MatchFound]) extends EntryCommand

sealed trait PrintCommand extends WorkMessage
final case class PrintResult(feedTitle: String,
                             entryURL: URL, matchedSection: String) extends PrintCommand
// XXX replace matchedSection with desc of the entry?

sealed trait ManagerMessage extends WorkMessage
final case class RegisterPrinters(newPrinters: Set[ActorRef[PrintCommand]]) extends ManagerMessage
final case object PollAll extends ManagerMessage

sealed trait WorkerResponse extends ManagerMessage
final case class NewEntry(feedURL: URL, url: URL) extends WorkerResponse  // TODO add date & other info
final case class MatchFound(entry: NewEntry, matchedSection: String) extends WorkerResponse
