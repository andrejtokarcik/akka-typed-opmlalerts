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
final case class PrintResult(feedURL: URL, feedTitle: String,
                             entryURL: URL, matchedSection: String) extends PrintCommand

sealed trait ManagerMessage extends WorkMessage
final case class RegisterPrinter(printer: ActorRef[PrintCommand]) extends ManagerMessage
final case object PollAll extends ManagerMessage  // TODO regularly via timer/scheduler after Register

sealed trait WorkerResponse extends ManagerMessage
final case class NewEntry(feedURL: URL, url: URL) extends WorkerResponse
final case class MatchFound(entry: NewEntry, matchedSection: String) extends WorkerResponse
