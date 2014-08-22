package com.twitter.server.handler

import com.twitter.server.responder.Responder
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._
import com.twitter.server.responder.Response
import com.twitter.finagle.Service
import com.twitter.server.controller.TemplateViewController
import com.twitter.server.controller.TemplateViewController.Renderable

/**
 * A handler that extracts the JSON response from another handler and renders it within
 * a template, then returns the new html response and the original response as
 * a curl response.
 */
private[server] class RequestHandlerJson(handler: Service[HttpRequest, HttpResponse]) extends WebHandler {
  private[this] class JsonView(
    val json: String
  ) extends Renderable

  def apply(req: HttpRequest): Future[HttpResponse] = {
    handler(req) flatMap { jsonResponse =>
      val json = jsonResponse.getContent.toString("utf8")
      val htmlResponse = TemplateViewController.renderInRoot(
        new JsonView(json), "Json")
      makeHttpFriendlyResponse(req, json, htmlResponse)
    }
  }
}
