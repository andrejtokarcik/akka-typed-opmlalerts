package opmlalerts

import akka.actor.typed.ActorRef
import java.net.URL
import scala.util.matching.Regex

object Messages {

  sealed trait FeedCommand
  final case class GetNewEntries(replyTo: ActorRef[NewEntry])
      extends FeedCommand

  sealed trait EntryCommand
  final case class AddPattern(pattern: Regex) extends EntryCommand
  final case class ScanEntry(entry: NewEntry,
                             replyTo: ActorRef[MatchFound])
      extends EntryCommand

  sealed trait PrintCommand
  final case class PrintResult(feedTitle: Option[String],
                               entryURL: URL,
                               matchedSection: String)
      extends PrintCommand
  // XXX replace matchedSection with desc of the entry?

  sealed trait ManagerMessage
  final case class RegisterPrinters(newPrinters: Set[ActorRef[PrintCommand]])
      extends ManagerMessage
  final case class PollFeed(feedURL: URL) extends ManagerMessage

  sealed trait WorkerResponse extends ManagerMessage
  final case class NewEntry(feedURL: URL, url: URL)
      extends WorkerResponse  // TODO add date & other info
  final case class MatchFound(entry: NewEntry,
                              matchedSection: String)
      extends WorkerResponse
}
