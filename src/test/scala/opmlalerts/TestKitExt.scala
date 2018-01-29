package opmlalerts

import akka.actor.typed.scaladsl.adapter._
import akka.testkit.typed._
import akka.testkit.typed.scaladsl._
import akka.testkit.{ EventFilter, filterEvents }
import com.typesafe.config.ConfigFactory

object TestKitExt {
  // NOTE: Although the `ActorContextSpec` suite does use `typed.loggers`,
  // the option does not seem to be taken into account and the old `loggers`
  // must be specified instead.
  val config = ConfigFactory.parseString(
    """|akka {
       |  loglevel = WARNING
       |  loggers = ["akka.testkit.TestEventListener"]
       |}""".stripMargin)
}

abstract class TestKitExt extends TestKit(TestKitExt.config) {
  def expectWarning[T](msg: String, num: Int = 1)(block: ⇒ T) = {
    val filter = EventFilter.warning(start = msg, occurrences = num)
    filterEvents(filter)(block)(system.toUntyped)
  }
}
