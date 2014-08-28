package com.twitter.server.responder

import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.controller.TemplateViewController
import com.twitter.server.controller.TemplateViewController.Renderable
import com.twitter.server.{ConfigurationFlags, Flag}
import org.jboss.netty.handler.codec.http._

private[server] class IndexResponder(flags: ConfigurationFlags) extends Responder {
  private[this] class IndexView(
    val setFlags: List[Flag],
    val unsetFlags: List[Flag],
    paths: List[String]
  ) extends Renderable

  override def respond(req: HttpRequest): Response = {
    val paths = HttpMuxer.patterns.filter(_.startsWith("/")).toList
    // TODO: global flags are expensive to lookup, they require walking the classpath.
    // This should probably happen in a separate threadpool. This might require changing
    // the responder interface. For now, this feature is disabled and flags aren't
    // displayed.
    val indexView = new IndexView(Nil, Nil, paths)
    val htmlResponse = TemplateViewController.renderInRoot(indexView, "Index")
    val curlResponse = paths.mkString("\n")
    Response(htmlResponse, curlResponse)
  }
}