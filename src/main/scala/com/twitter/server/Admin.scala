package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.{ListeningServer, Http}
import com.twitter.util.{Future, Time}
import java.net.{SocketAddress, InetSocketAddress}

trait Admin { self: App =>
  def defaultAdminPort = 9900
  val adminPort = flag("admin.port", new InetSocketAddress(defaultAdminPort), "Service admin port")

  @volatile private[this] var adminServer: ListeningServer = _

  premain {
    adminServer = Http.serve(adminPort(), HttpMuxer)
  }

  onExit {
    assert(adminServer != null)
    adminServer.close()
  }
}
