package com.twitter.server.handler

import com.twitter.finagle.server.ServerRegistry
import com.twitter.finagle.Service
import com.twitter.finagle.util.StackRegistry
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.MetricSource
import com.twitter.server.view.StackRegistryView
import com.twitter.util.Future

private object ServerRegistryHandler {
  def render(servers: Seq[(String, StackRegistry.Entry)]): String =
    s"""<link type="text/css" href="/admin/files/css/server-registry.css" rel="stylesheet"/>
        <script type="application/javascript" src="/admin/files/js/server-registry.js"></script>
        <script type="application/javascript" src="/admin/files/js/chart-renderer.js"></script>
        <ul id="server-tabs" class="nav nav-tabs" data-refresh-uri="/admin/metrics">
          ${
             (for ((scope, StackRegistry.Entry(name, addr, _, _)) <- servers) yield {
                s"""<li><a href="#${name}-entry" data-toggle="tab">$scope</a></li>"""
             }).mkString("\n")
           }
        </ul>
        <!-- Tab panes -->
        <div id="servers" class="tab-content">
          ${
             (for ((scope, StackRegistry.Entry(name, addr, _, _)) <- servers) yield {
                val scopeDash = scope.replace("/", "-")
                s"""<div class="tab-pane borders" id="${name}-entry">
                      <div class="row">
                        <!-- server stats -->
                        <div class="server-info col-md-3">
                          <dl class="server-stats dl-horizontal">
                            <dt><a href="/admin/metrics#$scope/load">Load:</a></dt>
                            <dd id="${scopeDash}-load" data-key="$scope/load">...</dd>

                            <dt><a href="/admin/metrics#$scope/failures">Failures:</a></dt>
                            <dd id="${scopeDash}-failures" data-key="$scope/failures">...</dd>

                            <dt><a href="/admin/metrics#$scope/success">Success:</a></dt>
                            <dd id="${scopeDash}-success" data-key="$scope/success">...</dd>

                            <dt><a href="/admin/metrics#$scope/requests">Requests:</a></dt>
                            <dd id="${scopeDash}-requests"data-key="$scope/requests">...</dd>
                          </dl>
                        </div>
                        <!-- graph -->
                        <div id="server-graph" class="col-md-9"></div>
                      </div>

                    </div>"""
             }).mkString("\n")
           }
        </div>"""
}

/**
 * Renders information about servers registered to Finagle's ServerRegistry
 * in an html fragment. Server's can be queried by passing in the server name
 * as part of the uri (ex. "/admin/servers/myserver").
 */
class ServerRegistryHandler(
  source: MetricSource = new MetricSource,
  registry: StackRegistry = ServerRegistry
) extends Service[Request, Response] {
  // Search the metrics source for the stat scope that includes `serverName`.
  // The search namespace includes both "$serverName/" and "srv/$serverName"
  // to take into account finagle's ServerStatsReceiver. Note, unnamed servers are
  // ignored as we can't dissambiguate their stats.
  private[this] def findScope(serverName: String): Option[String] = {
    val k0 = s"$serverName"
    val k1 = s"srv/$serverName"
    if (source.contains(s"$k0/load")) Some(k0)
    else if (source.contains(s"$k1/load")) Some(k1)
    else None
  }

  def apply(req: Request): Future[Response] = {
    val (path, _) = parse(req.getUri)
    path.split('/').last match {
      case idx@("index.html" | "index.htm" | "index.txt" | "servers") =>
        val servers = (registry.registrants flatMap {
          case e@StackRegistry.Entry(name, _, _, _) if name.nonEmpty =>
            for (scope <- findScope(name)) yield (scope, e)

          case _ => Nil
        }).toSeq
        val html = ServerRegistryHandler.render(servers)
        // This is useful to avoid the returned fragment being wrapped
        // with an index in the context of an ajax call.
        val typ = if (idx.endsWith(".txt")) "text/plain" else "text/html"
        newResponse(
          contentType = s"$typ;charset=UTF-8",
          content = Buf.Utf8(html)
        )

      case name =>
        val entries = registry.registrants filter { _.name == name }
        if (entries.isEmpty) new404(s"$name could not be found.") else {
          val server = entries.head
          val scope = findScope(server.name)
          val html = StackRegistryView.render(server, scope)
          newResponse(
            contentType = "text/html;charset=UTF-8",
            content = Buf.Utf8(html)
          )
        }
    }
  }

}