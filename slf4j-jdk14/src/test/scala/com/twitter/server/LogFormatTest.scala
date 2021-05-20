package com.twitter.server

import com.twitter.finagle.tracing.{SpanId, Trace, TraceId}
import com.twitter.logging.{StringHandler, Level => TwLevel}
import java.util.logging.Logger
import org.scalatest.funsuite.AnyFunSuite

class LogFormatTest extends AnyFunSuite {
  def testStringHandler(f: (Logger, StringHandler) => Unit): Unit = {
    val handler = new StringHandler
    handler.setFormatter(new com.twitter.server.logging.LogFormatter)

    val logger = Logger.getLogger(this.getClass.getSimpleName)
    logger.setUseParentHandlers(false)
    logger.addHandler(handler)

    f(logger, handler)
  }

  test("LogFormat contains trace ID if set") {
    testStringHandler { (log, handler) =>
      Trace.letId(TraceId(None, None, SpanId(1), None)) {
        log.info("test")
        assert(handler.get.contains("TraceId:0000000000000001"))
      }
    }
  }

  test("LogFormat omits trace ID if not set") {
    testStringHandler { (log, handler) =>
      log.info("test")
      assert(!handler.get.contains("TraceId"))
    }
  }

  test("com.twitter.logging.Levels are known") {
    testStringHandler { (log, handler) =>
      log.log(TwLevel.ERROR, "anError")
      assert(handler.get.startsWith("E"), handler.get)
    }
    testStringHandler { (log, handler) =>
      log.log(TwLevel.CRITICAL, "anCritical")
      assert(handler.get.startsWith("E"), handler.get)
    }
  }
}
