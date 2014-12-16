package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.http.{Response, Request, HttpMuxer}
import com.twitter.finagle.server.ServerRegistry
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.{Filter, Http, ListeningServer, NullServer, param, Service}
import com.twitter.server.util.HttpUtils
import com.twitter.server.view.{IndexView, NotFoundView}
import com.twitter.util.Monitor
import java.net.InetSocketAddress
import java.util.logging.Logger
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpRequest}

object AdminHttpServer {
  /**
   * Represents an element which can be routed to via the admin http server.
   *
   * @param path The path used to access the route. A request
   * is routed to the path as per the [[com.twitter.finagle.http.HttpMuxer]]
   * spec.
   *
   * @param handler The service which requests are routed to.
   *
   * @param alias A short name used to identify the route when listed in
   * index.
   *
   * @param group A grouping used to organize the route in the
   * admin server pages. Routes with the same grouping are displayed
   * together in the admin server pages.
   *
   * @param includeInIndex Indicates whether the route is included
   * as part of the admin server index.
   */
  case class Route(
    path: String,
    handler: Service[HttpUtils.Request, HttpUtils.Response],
    alias: String,
    group: Option[String],
    includeInIndex: Boolean)

  /**
   * Create a Route using a Finagle service interface
   */
  def mkRoute(
    path: String,
    handler: Service[Request, Response],
    alias: String,
    group: Option[String],
    includeInIndex: Boolean
  ): Route = {
    val nettyToFinagle = Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
      service(Request(req)) map { _.httpResponse }
    }

    Route(path, nettyToFinagle andThen handler, alias, group, includeInIndex)
  }

}

trait AdminHttpServer { self: App =>
  import AdminHttpServer._

  def defaultHttpPort: Int = 9990
  val adminPort = flag("admin.port", new InetSocketAddress(defaultHttpPort), "Admin http server port")

  @volatile protected var adminHttpServer: ListeningServer = NullServer

  protected def routes: Seq[Route] = Nil

  premain {
    // a logger used to log all sync and async exceptions
    // that occur in the admin server.
    val log = Logger.getLogger(getClass.getName)
    val loggingMonitor = new Monitor {
      def handle(exc: Throwable): Boolean = {
        log.severe(exc.toString)
        false
      }
    }

    // Stat libraries join the global muxer namespace.
    // Special case and group them here.
    val (metricLinks, otherLinks) = {
      val links = HttpMuxer.patterns map {
        case path@"/admin/metrics.json" => IndexView.Link(path, s"$path?pretty=true")
        case path => IndexView.Link(path, path)
      }
      links partition {
        case IndexView.Link("/admin/metrics.json", _) => true
        case IndexView.Link("/stats.json", _) => true
        case _ => false
      }
    }

    // convert local routes into the IndexView data model
    val localRoutes =
      routes.filter(_.includeInIndex).groupBy(_.group) flatMap {
        case (group, rs) =>
          val links = rs map { r => IndexView.Link(r.alias, r.path) }
          if (!group.isDefined) links else {
            val linx = if (group != Some("Metrics")) links else links++metricLinks
            Seq(IndexView.Group(group.get, linx))
          }
      }

    // create index with both the local and global muxer namespaces
    // and server/client registries.
    val index = { () =>
      val serverGroup = {
        val serverLinks: Seq[IndexView.Entry] = (ServerRegistry.registrants collect {
          case server if server.name.nonEmpty =>
            IndexView.Link(server.name, "/admin/servers/" + server.name)
        }).toSeq
        IndexView.Group("Listening Servers", serverLinks.sorted(IndexView.EntryOrdering))
      }

      val clientGroup = {
        val clientLinks: Seq[IndexView.Entry] = (ClientRegistry.registrants collect {
          case client if client.name.nonEmpty =>
            IndexView.Link(client.name, "/admin/clients/" + client.name)
        }).toSeq
        IndexView.Group("Downstream Clients", clientLinks.sorted(IndexView.EntryOrdering))
      }

      val miscGroup = IndexView.Group("Misc", otherLinks)
      miscGroup +: clientGroup +: serverGroup +: localRoutes.toSeq
    }

    // create a service which multiplexes across all endpoints.
    val adminHttpMuxer = {
      val localMuxer = routes.foldLeft(new HttpMuxer) {
        case (muxer, route) =>
          log.info(s"${route.path} => ${route.handler.getClass.getName}")
          val service = new IndexView(route.alias, route.path, index) andThen route.handler
          muxer.withHandler(route.path, service)
      }
      HttpUtils.combine(localMuxer, HttpMuxer)
    }

    adminHttpServer = Http.server
      .configured(param.Stats(NullStatsReceiver))
      .configured(param.Tracer(NullTracer))
      .configured(param.Monitor(loggingMonitor))
      .serve(adminPort(), new NotFoundView andThen adminHttpMuxer)
  }

  onExit {
    adminHttpServer.close()
  }
}
