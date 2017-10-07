package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Status}
import com.twitter.finagle.tracing.{Record, Trace, TraceId, Tracer}
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}

@RunWith(classOf[JUnitRunner])
class TracingHandlerTest extends FunSuite with MockitoSugar with BeforeAndAfter {
  val service = new TracingHandler

  test("enable tracing") {
    val tracer = mock[Tracer]
    when(tracer.isActivelyTracing(any[TraceId])).thenReturn(true)

    try {
      // FIXME: This relies on a global.
      Trace.disable()
      Trace.letTracer(tracer) {
        Trace.record("msg")
        verify(tracer, never()).record(any(classOf[Record]))

        val request = Request("/", ("enable", "true"))
        assert(Await.result(service(request)).status == Status.Ok)

        Trace.record("msg")
        verify(tracer).record(any(classOf[Record]))
      }
    } finally {
      Trace.enable()
    }
  }

  test("disable tracing") {
    val tracer = mock[Tracer]
    when(tracer.isActivelyTracing(any[TraceId])).thenReturn(true)

    try {
      Trace.enable()
      Trace.letTracer(tracer) {
        Trace.record("msg")
        verify(tracer).record(any(classOf[Record]))

        val tracer2 = mock[Tracer]
        Trace.letTracer(tracer2) {
          val request = Request("/", ("disable", "true"))
          assert(Await.result(service(request)).status == Status.Ok)

          Trace.record("msg")
          verify(tracer2, never()).record(any(classOf[Record]))
        }
      }
    } finally {
      Trace.enable()
    }
  }
}
