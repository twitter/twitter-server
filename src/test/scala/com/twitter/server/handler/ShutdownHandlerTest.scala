package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.http.{Status, Request, Method}
import com.twitter.server.TwitterServer
import com.twitter.util.{Closable, Time, Await, Future}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ShutdownHandlerTest extends FunSuite {

  class Closer(testDeadline: Time => Unit) extends TwitterServer {
    @volatile var closed = false
    closeOnExit {
      Closable.make { deadline =>
        testDeadline(deadline)
        closed = true
        Future.Unit
      }
    }
  }

  object Closer {
    def mk(testDeadline: Time => Unit): Closer = new Closer(testDeadline)
  }

  test("close without a grace period")(Time.withCurrentTimeFrozen { tc =>
    val now = Time.now
    val closer = Closer.mk { deadline =>
      // MinGrace is 1 second
      assert(deadline == now + 1.second)
    }
    val handler = new ShutdownHandler(closer)
    val rsp = Await.result(handler(Request(Method.Post, "/foo")))
    assert(rsp.status == Status.Ok)
    assert(closer.closed)
  })

  test("close with a grace period") {
    val grace = 10.seconds
    val expectedDeadline = Time.now + grace
    val closer = Closer.mk { deadline =>
      assert(deadline > expectedDeadline - 1.second)
      assert(deadline < expectedDeadline + 1.second)
    }
    val handler = new ShutdownHandler(closer)
    val rsp = Await.result(handler(Request(Method.Post, "/foo?grace=" + grace.toString)))
    assert(rsp.status == Status.Ok)
    assert(closer.closed)
  }

  test("fail when an invalid grace parameter is specified") {
    val closer = Closer.mk { _ =>
      fail()
    }
    val handler = new ShutdownHandler(closer)
    val rsp = Await.result(handler(Request(Method.Post, "/foo?grace=5")))
    assert(rsp.status == Status.BadRequest)
    assert(!closer.closed)
  }

  test("do not close when given a GET request") {
    val now = Time.now
    val closer = Closer.mk { deadline =>
      assert(deadline == now + 1.second)
    }
    val handler = new ShutdownHandler(closer)
    val rsp = Await.result(handler(Request(Method.Get, "/foo")))
    assert(rsp.status == Status.MethodNotAllowed)
    assert(!closer.closed)
  }
}
