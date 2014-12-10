package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.tracing.Record
import com.twitter.finagle.tracing.{Trace, Tracer}
import com.twitter.util.Await
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.junit.runner.RunWith
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, times, verify}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}

@RunWith(classOf[JUnitRunner])
class TracingHandlerSpec extends FunSuite with MockitoSugar with BeforeAndAfter {
  val service = new TracingHandler

  test("enable tracing") {
    val tracer = mock[Tracer]
    try {
      // FIXME: This relies on a global.
      Trace.disable()
      Trace.letTracer(tracer) {
        Trace.record("msg")
        verify(tracer, never()).record(any(classOf[Record]))
  
        val request = Request("/", ("enable", "true"))
        assert(Response(Await.result(service(request))).status == HttpResponseStatus.OK)
  
        Trace.record("msg")
        verify(tracer).record(any(classOf[Record]))
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
        Trace.record("msg")
        verify(tracer).record(any(classOf[Record]))
  
        val tracer2 = mock[Tracer]
        Trace.letTracer(tracer2) {
          val request = Request("/", ("disable", "true"))
          assert(Response(Await.result(service(request))).status ==HttpResponseStatus.OK)
    
          Trace.record("msg")
          verify(tracer2, never()).record(any(classOf[Record]))
        }
      }
    } finally {
      Trace.enable()
    }
  }
}
