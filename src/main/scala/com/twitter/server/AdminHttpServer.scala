package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.{HttpServer, ListeningServer, NullServer}
import java.net.InetSocketAddress

trait AdminHttpServer { self: App =>
  def defaultHttpPort = 9990
  val adminPort = flag("admin.port", new InetSocketAddress(defaultHttpPort), "Admin http server port")

  @volatile protected var adminHttpServer: ListeningServer = NullServer

  premain {
    // Use NullStatsReceiver to keep admin endpoints from getting their stats mixed in
    // with the service's stats
    adminHttpServer = HttpServer.copy(statsReceiver = NullStatsReceiver).serve(adminPort(), HttpMuxer)
  }

  onExit {
    adminHttpServer.close()
  }
}
