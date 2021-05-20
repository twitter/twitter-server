package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Status}
import com.twitter.finagle.tracing.{Trace, Tracer}
import com.twitter.util.{Await, Awaitable}
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.funsuite.AnyFunSuite

class TracingHandlerTest extends AnyFunSuite with MockitoSugar with BeforeAndAfter {
  val service = new TracingHandler

  private[this] def await[T](a: Awaitable[T]): T = Await.result(a, 2.seconds)

  test("enable tracing") {
    val tracer = mock[Tracer]
    try {
      Trace.disable()
      Trace.letTracer(tracer) {
        assert(!Trace.enabled)
        val request = Request("/", ("enable", "true"))
        assert(await(service(request)).status == Status.Ok)
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
        assert(await(service(request)).status == Status.Ok)
        assert(!Trace.enabled)
      }
    } finally {
      Trace.enable()
    }
  }
}
