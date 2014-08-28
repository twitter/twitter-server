package com.twitter.server.responder

import com.twitter.finagle.client.{ClientInfo, ClientModuleInfo, ClientRegistry}
import com.twitter.server.controller.TemplateViewController
import com.twitter.server.controller.TemplateViewController.Renderable
import org.jboss.netty.handler.codec.http._

private[server] class ClientResponder(baseUrl: String) extends Responder {

  private[this] class ClientInfoView(client: ClientInfo) extends Renderable {
    val name = client.name
    val dest = client.dest
    val modules = client.modules.toList map {
      case ClientModuleInfo(role, description, perModuleParams) =>
        Map("role" -> role, "description" -> description, "perModuleParams" ->
          ResponderUtils.mapParams(perModuleParams))
    }
  }

  override def respond(req: HttpRequest): Response = {
    val clientName = req.getUri().stripPrefix(baseUrl)
    ClientRegistry.clientInfo(clientName) match {
      case Some(client) =>
        val htmlResponse = TemplateViewController.renderInRoot(
          new ClientInfoView(client), "ClientInfo")
        Response(htmlResponse)
      case None =>
        val response = "Client '" + clientName + "' cound not be found."
        Response(response, response)
    }
  }
}