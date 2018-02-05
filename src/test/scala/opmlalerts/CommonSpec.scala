package opmlalerts

import akka.actor.typed.scaladsl.adapter._
import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import akka.testkit.{ EventFilter, filterEvents }
import com.typesafe.config.ConfigFactory
import java.time.Instant
import org.scalatest._
import scala.concurrent.duration._

sealed trait CommonSpec {
  val someTime = Instant.now
  val someInterval = 1.minute

  object TestFeeds {
    val basicFeed         = getClass.getResource("/lorem-ipsum.rss")
    val corruptedDateFeed = getClass.getResource("/corrupted-date.rss")
    val corruptedURLFeed  = getClass.getResource("/corrupted-url.rss")
    val unparsableFeed    = getClass.getResource("/unparsable.rss")

    val nonExistentFeed   = getClass.getResource("/doesNotExist")
  }

  object TestPages {
    val basicPage         = getClass.getResource("/moby-dick.html")

    val nonExistentPage   = getClass.getResource("/doesNotExist")
  }
}

trait CommonSyncSpec extends CommonSpec with WordSpecLike with Matchers

object CommonAsyncSpec {
  // NOTE: Although the `ActorContextSpec` suite does use `typed.loggers`,
  // the option does not seem to be taken into account and the old-style
  // `loggers` must be specified instead.
  val config = ConfigFactory.parseString(
    """|akka {
       |  loglevel = WARNING
       |  loggers = ["akka.testkit.TestEventListener"]
       |}""".stripMargin)
}

abstract class CommonAsyncSpec extends TestKit(CommonAsyncSpec.config) with CommonSpec with WordSpecLike {
  def expectWarning[T](msg: String, num: Int = 1)(block: ⇒ T) = {
    val filter = EventFilter.warning(start = msg, occurrences = num)
    filterEvents(filter)(block)(system.toUntyped)
  }
}
