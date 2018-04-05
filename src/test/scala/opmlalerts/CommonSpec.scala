package opmlalerts

import akka.actor.typed.scaladsl.adapter._
import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import akka.testkit.{ EventFilter, filterEvents }
import com.typesafe.config.ConfigFactory
import java.time.Instant
import org.scalatest._
import scala.concurrent.duration._

// TODO mix in TypeCheckedTripleEquals
sealed trait CommonSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
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

trait CommonSyncSpec extends CommonSpec

trait CommonAsyncSpec extends CommonSpec {
  self: ActorTestKit ⇒
  override protected def afterAll(): Unit = shutdownTestKit()
}

abstract class CommonTestKit extends ActorTestKit with CommonAsyncSpec {

  // NOTE: Although the `ActorContextSpec` suite does use `typed.loggers`,
  // the option does not seem to be taken into account and the old-style
  // `loggers` must be specified instead.
  override def config = ConfigFactory.parseString(
    """|akka {
       |  loglevel = WARNING
       |  loggers = ["akka.testkit.TestEventListener"]
       |}""".stripMargin)

  implicit lazy val untypedSystem = system.toUntyped

  def expectWarning[T](msg: String, num: Int = 1)(block: ⇒ T) = {
    val filter = EventFilter.warning(start = msg, occurrences = num)
    filterEvents(filter)(block)
  }

  def expectWarningOfPattern[T](pattern: String, num: Int = 1)(block: ⇒ T) = {
    val filter = EventFilter.warning(pattern = pattern, occurrences = num)
    filterEvents(filter)(block)
  }
}
