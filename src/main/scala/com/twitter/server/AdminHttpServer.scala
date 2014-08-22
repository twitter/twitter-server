package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.{ServerCodecConfig, NullServer, ListeningServer, HttpServer}
import com.twitter.finagle.http.{Http, HttpMuxer}
import com.twitter.finagle.netty3.Netty3Listener
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import java.net.{SocketAddress, InetSocketAddress}

trait AdminHttpServer { self: App =>
  def defaultHttpPort = 9990
  val adminPort = flag("admin.port", new InetSocketAddress(defaultHttpPort), "Admin http server port")
  
  @volatile protected var adminHttpServer: ListeningServer = NullServer

  premain {
    // Use NullStatsReceiver to keep admin endpoints from getting their stats mixed in
    // with the service's stats
    // TODO: use StackServer when available
    val httpListener = Netty3Listener[Any, Any]("http",
      Http().server(ServerCodecConfig("httpserver", new SocketAddress{})).pipelineFactory,
      statsReceiver = NullStatsReceiver
    )
    adminHttpServer = HttpServer
      .copy(listener = httpListener)
      .copy(statsReceiver = NullStatsReceiver)
      .copy(tracer = NullTracer)
      .serve(adminPort(), HttpMuxer)
  }

  onExit {
    adminHttpServer.close()
  }
}
