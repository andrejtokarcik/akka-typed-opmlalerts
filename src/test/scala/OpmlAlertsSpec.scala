package opmlalerts

//import akka.actor.typed._
//import akka.actor.typed.scaladsl._
import akka.testkit.typed._
//import akka.testkit.typed.Effect._
import org.scalatest.{ Matchers, WordSpec }
import java.net.URL

class FetcherBehaviorSpec extends WordSpec with Matchers {
  import FeedHandler._

  "fetcher replies with download message" in {
    // TODO use a local file as URL
    val testKit = BehaviorTestkit(fetcher(new URL("http://localhost")))
    val inbox = TestInbox[Download]()
    testKit.run(Fetch(inbox.ref))
    inbox.expectMsg(Download(List()))
  }
}
