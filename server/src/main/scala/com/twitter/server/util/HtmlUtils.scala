package com.twitter.server.util

private[server] object HtmlUtils {

  /**
   * Escapes `s` for output into HTML.
   */
  def escapeHtml(s: String): String = {
    val out = new StringBuilder(s.length)
    var pos = 0
    while (pos < s.length) {
      s.charAt(pos) match {
        case '<' => out.append("&lt;")
        case '>' => out.append("&gt;")
        case '&' => out.append("&amp;")
        case '"' => out.append("&quot;")
        case '\'' => out.append("&#39;")
        case c => out.append(c)
      }
      pos += 1
    }
    out.toString
  }

}
