package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Status}
import com.twitter.finagle.tracing.{Trace, Tracer}
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}

@RunWith(classOf[JUnitRunner])
class TracingHandlerTest extends FunSuite with MockitoSugar with BeforeAndAfter {
  val service = new TracingHandler

  test("enable tracing") {
    val tracer = mock[Tracer]
    try {
      Trace.disable()
      Trace.letTracer(tracer) {
        assert(!Trace.enabled)
        val request = Request("/", ("enable", "true"))
        assert(Await.result(service(request)).status == Status.Ok)
        assert(Trace.enabled)
      }
    } finally {
      Trace.enable()
    }
  }

  test("disable tracing") {
    val tracer = mock[Tracer]
    try {
      Trace.enable()
      Trace.letTracer(tracer) {
        assert(Trace.enabled)
        val request = Request("/", ("disable", "true"))
        assert(Await.result(service(request)).status == Status.Ok)
        assert(!Trace.enabled)
      }
    } finally {
      Trace.enable()
    }
  }
}
