package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{expectsHtml, newResponse}
import com.twitter.util.Future
import com.twitter.util.logging.Logger

private[server] object NoLoggingHandler {
  val MissingLoggingImplMessageHeader: String =
    "You have not configured a logging handler implementation for TwitterServer."
  val MissingLoggingImplMessageBody: String =
    "Please add a dependency on one of: twitter-server-logback-classic, " +
      "twitter-server-slf4j-jdk14, or twitter-server-slf4j-log4j12."
}

class NoLoggingHandler extends Service[Request, Response] {
  import NoLoggingHandler._

  private[this] val log = Logger[NoLoggingHandler]

  def apply(request: Request): Future[Response] = {
    log.warn(MissingLoggingImplMessageHeader + " " + MissingLoggingImplMessageBody)

    if (!expectsHtml(request)) {
      newResponse(
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8(MissingLoggingImplMessageHeader + " " + MissingLoggingImplMessageBody)
      )
    } else {
      newResponse(
        contentType = "text/html;charset=UTF-8",
        content =
          Buf.Utf8(s"<h2>$MissingLoggingImplMessageHeader</h2><br/>$MissingLoggingImplMessageBody")
      )
    }
  }
}
