package com.twitter.server.responder

import com.twitter.server.controller.TemplateViewController
import com.twitter.server.controller.TemplateViewController.Renderable
import org.jboss.netty.handler.codec.http._

private[server] class TemplateResponder(templateName: String) extends Responder {
  class View extends Renderable
  
  override def respond(req: HttpRequest): Response = {
    val htmlResponse = TemplateViewController.renderInRoot(
      new View(), templateName)
    new Response(htmlResponse)
  }
}