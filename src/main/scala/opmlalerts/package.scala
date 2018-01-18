import java.net.URL

package object opmlalerts {
  // TODO handle java.net.MalformedURLException
  private[opmlalerts] implicit def str2URL(str: String) = new URL(str)
}
