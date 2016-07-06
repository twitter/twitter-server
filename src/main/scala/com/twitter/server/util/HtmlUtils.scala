package com.twitter.server.util

private[server] object HtmlUtils {

  private[server] def escapeHtml(s: String) : String =
    scala.xml.Utility.escape(s)

}
