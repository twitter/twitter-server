package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Route}

/**
 * Trait AdminHttpMuxHandler is used for service-loading HTTP handlers specifically for the AdminHttpServer
 */
private[twitter] trait AdminHttpMuxHandler extends Service[Request, Response] {

  def route: Route
}
