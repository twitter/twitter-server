package com.twitter.server

import java.net.InetSocketAddress

import com.twitter.app.App
import com.twitter.finagle._
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.param.{Label, Stats}
import com.twitter.finagle.server.StackServer
import com.twitter.finagle.stats.NullStatsReceiver

trait AdminHttpServer { self: App =>
  def defaultHttpPort = 9990
  val adminPort = flag("admin.port", new InetSocketAddress(defaultHttpPort), "Admin http server port")

  @volatile protected var adminHttpServer: ListeningServer = NullServer

  premain {
    // Use NullStatsReceiver to keep admin endpoints from getting their stats mixed in
    // with the service's stats
    // TODO: use StackServer when available (Verify proper usage of StackServer!)

    val serverParams = StackServer.defaultParams +
          Label("httpserver") +
          Stats(NullStatsReceiver)

    adminHttpServer = Http.Server(params = serverParams).serve(adminPort(), HttpMuxer)
  }

  onExit {
    adminHttpServer.close()
  }
}
