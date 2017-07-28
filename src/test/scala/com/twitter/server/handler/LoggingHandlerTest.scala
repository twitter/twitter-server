package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Status}
import com.twitter.logging.{Level, Logger}
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.{Matchers, FunSuite}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LoggingHandlerTest extends FunSuite with Matchers {
  test("query all loggers") {
    val handler = new LoggingHandler
    val loggers = Logger.iterator

    val res = Await.result(handler(Request("/")))
    assert(res.status == Status.Ok)
    val text = res.contentString
    for (logger <- loggers) {
      assert(text.contains(logger.name))
    }

    val browserReq = Request("/")
    browserReq.headerMap.set("User-Agent", "Mozilla")
    val browserRes = Await.result(handler(Request("/")))
    assert(browserRes.status == Status.Ok)
    val html = browserRes.contentString
    for (logger <- loggers) {
      assert(html.contains(logger.name))
    }
  }

  test("change log level") {
    val handler = new LoggingHandler

    Logger("").setLevel(Level.INFO)
    assert(Logger("").getLevel == Level.INFO)
    Await.result(handler(Request("/?logger=root&level=DEBUG")))
    assert(Logger("").getLevel == Level.DEBUG)
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
      val req = Request("/")
      val res = Await.result(handler(req))
      assert(res.status == Status.Ok)
      val text = res.contentString
      text should include("root OFF")
      text should include("l0 ALL")
      text should include("l1 DEBUG")
    }
  }
}
