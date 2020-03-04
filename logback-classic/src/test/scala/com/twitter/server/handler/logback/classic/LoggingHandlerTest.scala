package com.twitter.server.handler.logback.classic

import ch.qos.logback.classic.{Level, Logger}
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.util.Await
import com.twitter.util.logging.{Logger => UtilLogger}
import org.scalatest.FunSuite
import org.slf4j.LoggerFactory

class LoggingHandlerTest extends FunSuite {

  private val handler = new LoggingHandler()

  test("sees logger created by implicit ClassTag") {
    class ClassTagLogger()

    val log = UtilLogger[ClassTagLogger]

    assert(handler.loggers.map(_.getName).contains(classOf[ClassTagLogger].getName))
  }

  test("sees logger created for the given name") {
    val log = UtilLogger("name")

    assert(handler.loggers.map(_.getName).contains("name"))
  }

  test("sees logger created for given class") {
    class SomeClass()

    val log = UtilLogger(classOf[SomeClass])

    assert(handler.loggers.map(_.getName).contains(classOf[SomeClass].getName))
  }

  test("sees logger created to wrap an underlying org.slf4j.Logger") {
    val underlying = LoggerFactory.getLogger("name")
    val log = UtilLogger(underlying)

    assert(handler.loggers.map(_.getName).contains(underlying.getName))
  }

  test("can set logging levels for logger created by implicit ClassTag") {
    class ClassTagLogger()

    val log = UtilLogger[ClassTagLogger]

    val loggerName = classOf[ClassTagLogger].getName
    val logger = LoggerFactory.getLogger(loggerName).asInstanceOf[Logger]

    logger.setLevel(Level.WARN)
    assert(handler.loggers.filter(_.getName == loggerName).head.getLevel() == Level.WARN)

    logger.setLevel(Level.DEBUG)
    assert(handler.loggers.filter(_.getName == loggerName).head.getLevel() == Level.DEBUG)
  }

  test("logger can be reset") {
    val logger = LoggerFactory.getLogger("foo").asInstanceOf[Logger]
    assert(logger.getLevel == null)
    logger.setLevel(Level.DEBUG)

    logger.setLevel(null)
    assert(logger.getLevel == null)
  }

  test("reset button works") {
    // mimics the request when reset button is pressed
    val req = Request(("logger", "bar"), ("level", "null"))
    val logger = LoggerFactory.getLogger("bar").asInstanceOf[Logger]

    assert(logger.getLevel == null)

    logger.setLevel(Level.DEBUG)
    Await.result(handler(req), 5.seconds)

    assert(logger.getLevel == null)
  }

  test("can set jul level") {
    val logger = java.util.logging.Logger.getLogger("snap")
    logger.setLevel(null)
    val req = Request(("logger", "snap"), ("level", "FINER"), ("isJul", "true"))

    assert(logger.getLevel == null)

    Await.result(handler(req), 5.seconds)

    assert(logger.getLevel == java.util.logging.Level.FINER)
  }

  test("loggers with overridden levels will display as overridden") {
    val log = LoggerFactory.getLogger("baz").asInstanceOf[Logger]

    val req = Request(("overridden", "true"))
    val overriddenHtml1 = Await.result(handler(req), 5.seconds).contentString

    assert(!overriddenHtml1.contains("baz"))

    log.setLevel(Level.DEBUG) // log level is now overridden to DEBUG
    val overriddenHtml2 = Await.result(handler(req), 5.seconds).contentString

    assert(overriddenHtml2.contains("baz"))

    log.setLevel(null) // log level is now inherited, no longer overridden
    val overriddenHtml3 = Await.result(handler(req), 5.seconds).contentString

    assert(!overriddenHtml3.contains("baz"))
  }

  test("displays all loggers when in display all mode") {
    val req = Request(("overridden", "false"))
    val html = Await.result(handler(req), 5.seconds).contentString
    val loggerNames: Seq[String] = handler.loggers.map(_.getName).toSeq

    for (name <- loggerNames) assert(html.contains(name))
  }
}
