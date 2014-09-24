package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.netty3.Netty3Listener
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.{Http, ListeningServer, NullServer, param}
import com.twitter.util.Monitor
import java.net.{SocketAddress, InetSocketAddress}
import java.util.logging.Logger

trait AdminHttpServer { self: App =>
  def defaultHttpPort = 9990
  val adminPort = flag("admin.port", new InetSocketAddress(defaultHttpPort), "Admin http server port")

  @volatile protected var adminHttpServer: ListeningServer = NullServer

  premain {
    val log = Logger.getLogger(getClass.getName)
    val loggingMonitor = new Monitor {
      def handle(exc: Throwable): Boolean = {
        log.severe(exc.toString)
        false
      }
    }

    adminHttpServer = Http.server
      .configured(param.Stats(NullStatsReceiver))
      .configured(param.Tracer(NullTracer))
      .configured(param.Monitor(loggingMonitor))
      .serve(adminPort(), HttpMuxer)
  }

  onExit {
    adminHttpServer.close()
  }
}
