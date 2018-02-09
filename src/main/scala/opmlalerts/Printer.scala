package opmlalerts

import akka.actor.typed._
import akka.actor.typed.receptionist
import akka.actor.typed.scaladsl.Actor
import java.net.URL

object Printer {
  sealed trait Command
  final case class PrintMatch(feedURL: URL,
                              feed: Parser.FeedInfo,
                              entry: Parser.FeedEntry,
                              matchFound: EntryHandler.MatchFound)
      extends Command

  val ServiceKey = receptionist.ServiceKey[Command]("Printer")

  def printOnConsole(maybeWidth: Option[Int] = None, register: Boolean = true): Behavior[Command] =
    Actor.deferred[Any] { ctx ⇒
      import receptionist.Receptionist.{ Register, Registered }
      if (register)
        ctx.system.receptionist ! Register(ServiceKey, ctx.self, ctx.self)

      val screenWidth = maybeWidth getOrElse 80
      Actor.immutable { (ctx, msg) ⇒
        msg match {
          case Registered(key, instance) if key == ServiceKey && instance == ctx.self ⇒
            Actor.unhandled

          case cmd: Command ⇒ {
            doPrinterCommand(cmd, screenWidth)(println)
            Thread.sleep(1000)  // avoid flooding
            Actor.same
          }
        }
      }
    }.narrow[Command]

  def doPrinterCommand(cmd: Command, screenWidth: Int)(implicit printFun: String ⇒ Unit) = {
    def printField(desc: String, field: Any) =
      printFun(s"${fansi.Bold.On(desc)}: $field")

    cmd match {
      case PrintMatch(feedURL, feed, entry, matched) ⇒ {
        printFun("=" * screenWidth)
        if (feed.title.isDefined)
          printField("Feed title", feed.title.get)
        printField("Feed URL", feedURL)
        if (entry.title.isDefined)
          printField("Entry title", entry.title.get)
        printField("Entry URL", entry.url)
        printField("Entry updated", entry.date)
        if (feed.pattern.isDefined)
          printField("Pattern", feed.pattern.get)
        printField("Number of matches", matched.numMatches)
        printField("Sample match", matched.matchedSection)
        printFun("=" * screenWidth)
      }
    }
  }
}
