package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.{Http, HttpMuxer}
import java.net.InetSocketAddress

trait Admin { self: App =>
  def defaultAdminPort = 9900
  val adminPort = flag("admin.port", new InetSocketAddress(defaultAdminPort), "Service admin port")

  private[this] var adminServer: Option[Server] = None

  premain {
    adminServer = Some(ServerBuilder()
      .name("admin")
      .bindTo(adminPort())
      .codec(Http())
      .build(HttpMuxer))
  }

  onExit {
    adminServer.foreach(_.close())
  }
}
