import java.net.URL
import scala.util.Try

package object opmlalerts {

  private[opmlalerts] implicit def tryString2URL(str: String): Try[URL] =
    Try { new URL(str) }

}
