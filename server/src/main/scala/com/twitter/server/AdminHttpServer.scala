package com.twitter.server

import com.twitter.app.App
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.filter.ServerAdmissionControl
import com.twitter.finagle.http.Method.Get
import com.twitter.finagle.http.{HttpMuxer, Method, Request, Response}
import com.twitter.finagle.server.ServerRegistry
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.util.LoadService
import com.twitter.finagle.{Http, ListeningServer, NullServer, Service, SimpleFilter, http}
import com.twitter.server.Admin.Grouping
import com.twitter.server.handler.{AdminHttpMuxHandler, NoLoggingHandler}
import com.twitter.server.lint.LoggingRules
import com.twitter.server.util.HttpUtils
import com.twitter.server.view.{IndexView, NotFoundView}
import com.twitter.util.lint.GlobalRules
import com.twitter.util.registry.Library
import com.twitter.util.{ExecutorServiceFuturePool, Future, FuturePool, Monitor}
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.slf4j.LoggerFactory
import scala.collection.mutable
import scala.language.reflectiveCalls

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
   *
   * @param method Specifies which HTTP Method to use from
   * [[com.twitter.finagle.http.Method]]. The default is [[Method.Get]].
   */
  case class Route(
    path: String,
    handler: Service[Request, Response],
    alias: String,
    group: Option[String],
    includeInIndex: Boolean,
    method: Method = Get
  )

  object Route {

    /**
     * A filter which forces an [[Http]] [[Service]] to be handled outside the
     * global default worker pool.
     */
    private[this] val IsolateFilter: SimpleFilter[Request, Response] =
      new SimpleFilter[Request, Response] {
        def apply(
          request: Request,
          service: Service[Request, Response]
        ): Future[Response] = Pool(service(request)).flatten
      }

    private[this] lazy val Pool: FuturePool =
      new ExecutorServiceFuturePool(
        Executors
          .newCachedThreadPool(new NamedPoolThreadFactory("AdminFuturePool", makeDaemons = true))
      ) { override def toString: String = "Route.Pool" }

    /**
     * Force the [[Route]] `r` to be handled outside the global default worker pool.
     */
    def isolate(r: Route): Route =
      r.copy(handler = IsolateFilter.andThen(r.handler))

    def from(route: http.Route): Route = route.index match {
      case Some(index) =>
        mkRoute(
          path = index.path.getOrElse(route.pattern),
          handler = route.handler,
          alias = index.alias,
          group = Some(index.group),
          includeInIndex = true,
          method = index.method
        )
      case None =>
        mkRoute(
          path = route.pattern,
          handler = route.handler,
          alias = route.pattern,
          group = None,
          includeInIndex = false
        )
    }
  }

  /**
   * Create a Route using a Finagle service interface
   */
  def mkRoute(
    path: String,
    handler: Service[Request, Response],
    alias: String,
    group: Option[String],
    includeInIndex: Boolean,
    method: Method = Get
  ): Route = {
    Route(path, handler, alias, group, includeInIndex, method)
  }

  /**
   * The name used for the finagle server.
   */
  val ServerName = "adminhttp"

}

trait AdminHttpServer { self: App =>
  import AdminHttpServer._

  // We use slf4-api directly b/c we're in a trait and want the trait class to be the Logger name
  private[this] val log = LoggerFactory.getLogger(getClass)

  def defaultAdminPort: Int = 9990
  val adminPort =
    flag("admin.port", new InetSocketAddress(defaultAdminPort), "Admin http server port")

  private[this] val adminHttpMuxer = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = underlying(request)

    @volatile var underlying: Service[Request, Response] =
      new Service[Request, Response] {
        def apply(request: Request): Future[Response] =
          HttpUtils.new404("no admin server initialized")
      }
  }

  @volatile protected var adminHttpServer: ListeningServer = NullServer

  // We start with routes added via load service, note that these will be overridden
  // by any routes added in any call to updateMuxer().
  private[this] val loadServiceRoutes: Seq[Route] =
    LoadService[AdminHttpMuxHandler]()
      .map(handler => Route.from(handler.route))
      .map(Route.isolate)
  private var allRoutes: Seq[Route] = loadServiceRoutes

  /**
   * The address to which the Admin HTTP server is bound.
   */
  def adminBoundAddress: InetSocketAddress = {
    // this should never be a NullServer unless
    // [[com.twitter.server.AdminHttpServer#premain]] is skipped/broken
    adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress]
  }

  def addAdminRoutes(newRoutes: Seq[Route]): Unit = synchronized {
    allRoutes = allRoutes ++ newRoutes
    updateMuxer()
  }

  def addAdminRoute(route: Route) {
    addAdminRoutes(Seq(route))
  }

  // TODO: remove routes, have all routes be added via addAdminRoutes
  // For now these routes will be added to allRoutes in premain
  protected def routes: Seq[Route] = Nil

  /**
   * Name used for registration in the [[com.twitter.util.registry.Library]]
   * @return library name to register in the Library registry.
   */
  protected def libraryName: String = "twitter-server"

  /**
   * This method allows for further configuration of the http server for parameters not exposed by
   * this trait or for overriding defaults provided herein, e.g.,
   *
   * {{{
   * override def configureAdminHttpServer(server: Http.Server): Http.Server =
   *  server.withMonitor(myMonitor)
   * }}}
   *
   * @param server - the [[com.twitter.finagle.Http.Server]] to configure.
   * @return a configured Http.Server.
   */
  protected def configureAdminHttpServer(server: Http.Server): Http.Server = server

  /** Provide a "catch-all" LoggingHandler that displays a message about configuring a logging implementation */
  private[this] def addLoggingHandler(): Unit = {
    if (!allRoutes.exists(_.path == "/admin/logging")) {
      // add linting issue for un-configured logging handler
      GlobalRules.get.add(LoggingRules.NoLoggingHandler)

      allRoutes = allRoutes ++
        Seq(
          mkRoute(
            path = "/admin/logging",
            handler = new NoLoggingHandler,
            alias = "Logging",
            group = Some(Grouping.Utilities),
            includeInIndex = true
          )
        ).map(Route.isolate)
    }
  }

  private[this] def updateMuxer() = {
    addLoggingHandler() // ensure there is an /admin/logging handler

    val rts = allRoutes ++
      HttpMuxer.routes.map(Route.from)

    // convert local routes into the IndexView data model
    val localRoutes =
      rts.filter(_.includeInIndex).groupBy(_.group).flatMap {
        case (group, rs) =>
          val links = rs.map { r =>
            IndexView.Link(r.alias, r.path, r.method)
          }
          group match {
            case Some(g) => Seq(IndexView.Group(g, links))
            case None => links
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

      clientGroup +: serverGroup +: localRoutes.toSeq
    }

    // create a service which multiplexes across all endpoints.
    val endpoints = new mutable.ArrayBuffer[String]()
    val localMuxer = allRoutes.foldLeft(new HttpMuxer) {
      case (muxer, route) =>
        endpoints += s"\t${route.path} => ${route.handler.getClass.getName}"
        val service = new IndexView(route.alias, route.path, index) andThen route.handler
        muxer.withHandler(route.path, service)
    }
    log.debug(s"AdminHttpServer Muxer endpoints:\n" + endpoints.mkString("\n"))
    adminHttpMuxer.underlying = HttpUtils.combine(Seq(localMuxer, HttpMuxer))
  }

  private[this] def startServer(): Unit = {
    val loggingMonitor = new Monitor {
      def handle(exc: Throwable): Boolean = {
        log.error(s"Caught exception in AdminHttpServer: $exc", exc)
        false
      }
    }

    log.info(s"Serving admin http on ${adminPort()}")
    adminHttpServer = configureAdminHttpServer(
      Http.server
        .configured(Http.Netty4Impl)
        .withStatsReceiver(NullStatsReceiver)
        .withTracer(NullTracer)
        .withMonitor(loggingMonitor)
        .withLabel(ServerName)
        // disable admission control, since we want the server to report stats
        // especially when it's in a bad state.
        .configured(ServerAdmissionControl.Param(false))
    ).serve(adminPort(), new NotFoundView andThen adminHttpMuxer)

    closeOnExitLast(adminHttpServer)
    Library.register(libraryName, Map.empty)
  }

  premain {
    addAdminRoutes(routes)
    startServer()
  }
}
