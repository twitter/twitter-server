package com.twitter.server

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.tracing.{Trace, Tracer}
import com.twitter.util.Await
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito

class TracingHandlerSpec extends SpecificationWithJUnit with Mockito {
  val service = new TracingHandler
  val tracer = mock[Tracer]

  "Tracing handler" should {
    doBefore { Trace.clear() }

    "enable tracing" in {
      try {
        Trace.disable
        Trace.pushTracer(tracer)
        Trace.record("msg")
        there was no(tracer).record(any)

        val request = Request("/", ("enable", "true"))
        Response(Await.result(service(request))).status must be_==(HttpResponseStatus.OK)

        Trace.record("msg")
        there was one(tracer).record(any)
      } finally {
        Trace.enable
      }
    }

    "disable tracing" in {
      try {
        Trace.enable
        Trace.pushTracer(tracer)
        Trace.record("msg")
        there was one(tracer).record(any)

        val tracer2 = mock[Tracer]
        Trace.pushTracer(tracer2)

        val request = Request("/", ("disable", "true"))
        Response(Await.result(service(request))).status must be_==(HttpResponseStatus.OK)

        Trace.record("msg")
        there was no(tracer2).record(any)
      } finally {
        Trace.enable
      }
    }
  }
}
