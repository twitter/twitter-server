package com.twitter.server

import com.twitter.finagle.tracing.{Trace, SpanId, TraceId}
import com.twitter.logging.StringHandler
import java.util.logging.Logger
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LogFormatTest extends FunSuite {
  def testStringHandler(f: (Logger, StringHandler) => Unit) {
    val handler = new StringHandler
    handler.setFormatter(new LogFormatter)

    val logger = Logger.getLogger(this.getClass.getSimpleName)
    logger.setUseParentHandlers(false)
    logger.addHandler(handler)

    f(logger, handler)
  }

  test("LogFormat contains trace ID if set") {
    testStringHandler { (log, handler) =>
      Trace.unwind {
        Trace.setId(TraceId(None, None, SpanId(1), None))
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
}
