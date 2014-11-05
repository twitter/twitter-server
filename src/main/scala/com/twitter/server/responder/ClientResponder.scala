package com.twitter.server.responder

import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.util.StackRegistry
import com.twitter.server.controller.TemplateViewController
import com.twitter.server.controller.TemplateViewController.Renderable
import org.jboss.netty.handler.codec.http._

private[server] class ClientResponder(baseUrl: String) extends Responder {

  private[this] class ClientInfoView(client: StackRegistry.Entry) extends Renderable {
    val name = client.name
    val dest = client.addr
    val modules = client.stack.tails map { n =>
        Map("role" -> n.head.role, "description" -> n.head.description, "perModuleParams" -> Nil)
    }
  }

  override def respond(req: HttpRequest): Response = {
    val clientName = req.getUri().stripPrefix(baseUrl)
    val clients = ClientRegistry.registrants filter { _.name == clientName }
    if (clients.nonEmpty) {
      val htmlResponse = TemplateViewController.renderInRoot(
        new ClientInfoView(clients.head), "ClientInfo")
      Response(htmlResponse)
    } else {
      val response = "Client '" + clientName + "' cound not be found."
      Response(response, response)
    }
  }
}
