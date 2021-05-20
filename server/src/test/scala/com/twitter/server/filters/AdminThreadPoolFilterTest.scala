package com.twitter.server.filters

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Await, Future}
import org.scalatest.funsuite.AnyFunSuite

class AdminThreadPoolFilterTest extends AnyFunSuite {
  test("work done in the dedicated admin thread pool") {
    @volatile var workingThreadId: Long = -1

    val svc = new Service[Request, Response] {
      def apply(request: Request): Future[Response] = {
        workingThreadId = Thread.currentThread().getId

        Future.value(Response())
      }
    }
    val s = AdminThreadPoolFilter.isolateService(svc)

    val caller = Thread.currentThread()
    // will also set `workingThreadId`
    Await.result(s(Request()), 5.seconds)

    assert(workingThreadId != -1)
    assert(workingThreadId != caller.getId)
  }
}
