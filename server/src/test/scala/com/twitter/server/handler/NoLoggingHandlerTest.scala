package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Status}
import com.twitter.util.Await
import org.scalatest.funsuite.AnyFunSuite

class NoLoggingHandlerTest extends AnyFunSuite {

  test("NoLoggingHandler responds with correct message") {
    val handler = new NoLoggingHandler

    val res = Await.result(handler(Request("/")), 5.seconds)
    assert(res.status == Status.Ok)
    val message = NoLoggingHandler.MissingLoggingImplMessageHeader +
      " " + NoLoggingHandler.MissingLoggingImplMessageBody
    assert(res.contentString.contains(message))
  }
}
