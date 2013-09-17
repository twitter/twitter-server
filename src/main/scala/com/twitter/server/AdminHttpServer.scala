package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.{ListeningServer, Http, NullServer}
import java.net.InetSocketAddress

trait AdminHttpServer { self: App =>
  def defaultHttpPort = 9990
  val adminPort = flag("admin.port", new InetSocketAddress(defaultHttpPort), "Admin http server port")

  @volatile protected var adminHttpServer: ListeningServer = NullServer

  premain {
    adminHttpServer = Http.serve(adminPort(), HttpMuxer)
  }

  onExit {
    adminHttpServer.close()
  }
}
