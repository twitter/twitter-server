package com.twitter.server.filters

import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finagle.Service
import com.twitter.finagle.filter.OffloadFilter
import com.twitter.finagle.http.{Request, Response}
import com.twitter.server.AdminHttpServer.Route
import com.twitter.util.{ExecutorServiceFuturePool, FuturePool}
import java.util.concurrent.Executors

object AdminThreadPoolFilter {
  private lazy val Pool: FuturePool =
    new ExecutorServiceFuturePool(
      Executors
        .newCachedThreadPool(new NamedPoolThreadFactory("AdminFuturePool", makeDaemons = true))
    ) {
      override def toString: String = "Admin.FuturePool"
    }

  /**
   * Force the [[Route]] `r` to be handled by a dedicated admin thread pool.
   */
  def isolateRoute(r: Route): Route =
    r.copy(handler = isolateService(r.handler))

  /**
   * Force the [[Service[Request, Response]] `s` to be handled by a dedicated admin thread pool.
   */
  def isolateService(s: Service[Request, Response]): Service[Request, Response] =
    (new OffloadFilter.Server(Pool)).andThen(s)
}
