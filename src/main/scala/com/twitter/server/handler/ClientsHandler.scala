package com.twitter.server.handler

import com.twitter.finagle.client.ClientRegistry
import com.twitter.server.controller.TemplateViewController.render
import com.twitter.finagle.Service
import com.twitter.server.view.{ClientInfoView, ClientListView}
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.util.CharsetUtil.UTF_8

/**
 * Gets a list of clients from the [[com.twitter.finagle.client.ClientRegistry ClientRegistry]] and
 * displays individual client information at <baseUrl>?name=<client name>
 */
class ClientsHandler(baseUrl: String) extends WebHandler {

  def apply(req: HttpRequest) = {
    val response = new DefaultHttpResponse(req.getProtocolVersion, HttpResponseStatus.OK)
    val responseString = {
      val query = req.getUri().stripPrefix(baseUrl)
      if (query.isEmpty) 
        render(new ClientListView(ClientRegistry.clientList(), baseUrl), "ClientList")
      else {
        val clientName = extractQueryValue("name", query)
        ClientRegistry.clientInfo(clientName) match {
          case Some(client) => render(new ClientInfoView(client), "ClientInfo")
          case None => "Client '" + clientName + "' cound not be found."
        } 
      } 
    }
    response.setContent(copiedBuffer(responseString, UTF_8))
    Future.value(response)
  }
}