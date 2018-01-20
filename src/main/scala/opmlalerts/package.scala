import java.net.URL
import java.nio.file.{ Path, Paths }
import scala.util.{ Try, Failure }

package object opmlalerts {

  private[opmlalerts] implicit def tryString2URL(str: String): Try[URL] =
    Try { new URL(str) }

  private[opmlalerts] implicit def tryString2Path(str: String): Try[Path] =
    Try { Paths get str }

  private[opmlalerts] implicit class EnrichedTry[T](t: Try[T]) {
    def getOrEffect(f: Throwable ⇒ Unit): T = {
      t.recoverWith { case e ⇒ f(e) ; Failure(e) }
      t.get
    }
  }
}
