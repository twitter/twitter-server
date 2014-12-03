package com.twitter.server.handler

import com.twitter.finagle.http
import com.twitter.io.Charsets
import com.twitter.logging.{Level, Logger}
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LoggingHandlerTest extends FunSuite {
  test("query all loggers") {
    val handler = new LoggingHandler
    val loggers = Logger.iterator

    val res = Await.result(handler(http.Request("/")))
    assert(res.getStatus === http.Status.Ok)
    val text = res.getContent.toString(Charsets.Utf8)
    for (logger <- loggers) {
      assert(text.contains(logger.name))
    }

    val browserReq = http.Request("/")
    browserReq.headers().set("User-Agent", "Mozilla")
    val browserRes = Await.result(handler(http.Request("/")))
    assert(browserRes.getStatus === http.Status.Ok)
    val html = browserRes.getContent.toString(Charsets.Utf8)
    for (logger <- loggers) {
      assert(html.contains(logger.name))
    }
  }

  test("change log level") {
    val handler = new LoggingHandler

    assert(Logger("").getLevel === Level.INFO)
    Await.result(handler(http.Request("/?logger=root&level=DEBUG")))
    assert(Logger("").getLevel === Level.DEBUG)
  }

  test("text output") {
    val handler = new LoggingHandler

    val l0 = () => {
      val logger = Logger("l0")
      logger.setLevel(Level.ALL)
      logger
    }

    val l1 = () => {
      val logger = Logger("l1")
      logger.setLevel(Level.DEBUG)
      logger
    }

    Logger.withLoggers(List(l0, l1)) {
      val req = http.Request("/")
      val res = Await.result(handler(req))
      assert(res.getStatus === http.Status.Ok)
      val text = res.getContent.toString(Charsets.Utf8)
      assert(text === "root OFF\nl0 ALL\nl1 DEBUG")
    }
  }
}