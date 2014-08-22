package com.twitter.server.handler

import com.twitter.server.responder.{Response, Responder}
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._

/**
 * A generic handler that returns the html and curl responses from
 * a [[com.twitter.server.handler.responder]] Responder
 */
private[server] class RequestHandler(responder: Responder) extends WebHandler {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    val response: Response = responder.respond(req)
    makeHttpFriendlyResponse(req, response.curlResponse, response.htmlResponse)
  }
}
