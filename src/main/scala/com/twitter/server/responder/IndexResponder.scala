package com.twitter.server.responder

import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.controller.TemplateViewController
import com.twitter.server.controller.TemplateViewController.Renderable
import com.twitter.server.controller.Navigation
import com.twitter.server.{ConfigurationFlags, Flag}
import org.jboss.netty.handler.codec.http._

private[server] case class Link(name: String, path: String, subLinks: List[Link] = List.empty)

private[server] class IndexResponder(val flags: ConfigurationFlags) extends Responder {

  private[this] class IndexView(
    val setFlags: List[Flag],
    val unsetFlags: List[Flag],
    paths: List[String]
  ) extends Renderable

  override def respond(req: HttpRequest): Response = {
    val paths = HttpMuxer.patterns.filter(_.startsWith("/")).toList
    val htmlResponse = TemplateViewController.renderInRoot(
      new IndexView(flags.setFlags, flags.unsetFlags, paths), "Index")
    val curlResponse = paths.mkString("\n")
    new Response(htmlResponse, curlResponse)
  }
}