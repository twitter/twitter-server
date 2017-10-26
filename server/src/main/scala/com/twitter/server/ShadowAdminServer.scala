package com.twitter.server

import com.twitter.app.App
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finagle.http.{HttpMuxHandler, HttpMuxer}
import com.twitter.finagle.netty4
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.util.LoadService
import com.twitter.finagle.{Http, ListeningServer, NullServer, param}
import com.twitter.util.logging.Logger
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * An admin http server which serves requests outside the default
 * finagle worker pool. This server shadows vital endpoints (ex. stats)
 * that are useful for diagnostics and should be available even if the
 * server becomes overwhelmed.
 *
 * Note, we don't serve all of /admin on a separate worker pool because
 * it's important to serve certain admin endpoints in-band with the server.
 * In particular, /health and /ping should be served in-band so that they
 * are an accurate proxy of server health.
 */
@deprecated(
  "deprecated since TwitterServer now serves everything but ping" +
    " + health check outside of the global default worker pool",
  since = "2017-10-04"
)
trait ShadowAdminServer { self: App with AdminHttpServer =>

  @volatile protected var shadowHttpServer: ListeningServer = NullServer
  val shadowAdminPort = flag(
    "shadow.admin.port",
    new InetSocketAddress(defaultAdminPort + 1),
    "Shadow admin http server port"
  )

  premain {
    val log = Logger[ShadowAdminServer]
    log.info(s"Serving BlackBox http server on port ${shadowAdminPort().getPort}")

    // Ostrich, commons stats, and metrics export a `HttpMuxHandler`
    val handlers = LoadService[HttpMuxHandler]() filter { handler =>
      handler.route.pattern == "/vars.json" ||
      handler.route.pattern == "/stats.json" ||
      handler.route.pattern == "/admin/metrics.json" ||
      handler.route.pattern == "/admin/per_host_metrics.json"
    }

    val muxer = handlers.foldLeft(new HttpMuxer) {
      case (httpMuxer, h) => httpMuxer.withHandler(h.route.pattern, h)
    }

    shadowHttpServer = Http.server
      .configured(param.Stats(NullStatsReceiver))
      .configured(Http.Netty4Impl)
      .configured(param.Tracer(NullTracer))
      .configured(
        new netty4.param.WorkerPool(
          executor = Executors.newCachedThreadPool(
            new NamedPoolThreadFactory("twitter-server/netty", makeDaemons = true)
          ),
          numWorkers = 1
        )
      )
      .serve(shadowAdminPort(), muxer)
    closeOnExit(shadowHttpServer)
  }
}