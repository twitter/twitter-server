package com.twitter.server

import com.twitter.app.{App, Flag}
import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.filter.ServerAdmissionControl
import com.twitter.finagle.http.{HttpMuxer, Method, Request, Response, Route => HttpRoute}
import com.twitter.finagle.server.ServerRegistry
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.util.LoadService
import com.twitter.finagle.{Http, ListeningServer, NullServer, Service}
import com.twitter.server.Admin.Grouping
import com.twitter.server.filters.AdminThreadPoolFilter
import com.twitter.server.handler.{AdminHttpMuxHandler, LoggingHandler, NoLoggingHandler}
import com.twitter.server.lint.LoggingRules
import com.twitter.server.util.HttpUtils
import com.twitter.server.view.{IndexView, NotFoundView}
import com.twitter.util.lint.GlobalRules
import com.twitter.util.registry.Library
import com.twitter.util.{Future, Monitor, Time}
import java.net.{InetSocketAddress, URLEncoder}
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import scala.language.reflectiveCalls

object AdminHttpServer {

  /**
   * The name used for the finagle server.
   */
  val ServerName = "adminhttp"

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
    method: Method = Method.Get)

  object Route {
    // backwards compatibility
    def isolate(r: Route): Route = AdminThreadPoolFilter.isolateRoute(r)
    def isolate(s: Service[Request, Response]): Service[Request, Response] =
      AdminThreadPoolFilter.isolateService(s)

    def from(route: HttpRoute): Route = route.index match {
      case Some(index) =>
        Route(
          path = index.path.getOrElse(route.pattern),
          handler = route.handler,
          alias = index.alias,
          group = Some(index.group),
          includeInIndex = true,
          method = index.method
        )
      case None =>
        Route(
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
    method: Method = Method.Get
  ): Route = {
    Route(path, handler, alias, group, includeInIndex, method)
  }

  /** Convert an AdminHttpMuxHandler to a AdminHttpServer.Route */
  private def muxHandlerToRoute(handler: AdminHttpMuxHandler): Route = {
    AdminThreadPoolFilter.isolateRoute(Route.from(handler.route))
  }

  private val defaultLoggingHandlerRoute: Route =
    AdminThreadPoolFilter.isolateRoute(
      Route(
        path = "/admin/logging",
        handler = new NoLoggingHandler,
        alias = "Logging",
        group = Some(Grouping.Utilities),
        includeInIndex = true
      )
    )
}

trait AdminHttpServer { self: App with Stats =>
  import AdminHttpServer._

  // We use slf4-api directly b/c we're in a trait and want the trait class to be the Logger name
  private[this] val log = LoggerFactory.getLogger(getClass)

  /**
   * If true, the Twitter-Server admin server will be disabled.
   * Note: Disabling the admin server allows services to be deployed into environments where only a single port is allowed
   */
  protected def disableAdminHttpServer: Boolean = false

  def defaultAdminPort: Int = 9990
  val adminPort: Flag[InetSocketAddress] =
    flag("admin.port", new InetSocketAddress(defaultAdminPort), "Admin http server port")

  private[this] val adminHttpMuxer = new Service[Request, Response] {
    override def apply(request: Request): Future[Response] = underlying(request)

    @volatile var underlying: Service[Request, Response] =
      new Service[Request, Response] {
        def apply(request: Request): Future[Response] =
          HttpUtils.new404("no admin server initialized")
      }

    override def close(deadline: Time): Future[Unit] = underlying.close(deadline)
  }

  @volatile protected var adminHttpServer: ListeningServer = NullServer

  // Look up a logging handler, will only be added if a single one is found.
  private[this] val loggingHandlerRoute: Seq[Route] = {
    val handlers = LoadService[LoggingHandler]()
    if (handlers.length > 1) {
      // add linting issue for multiple logging handlers
      GlobalRules.get.add(LoggingRules.multipleLoggingHandlers(handlers.map(_.name)))
      Seq(defaultLoggingHandlerRoute)
    } else if (handlers.length == 1) {
      // add the logging handler
      handlers.map(muxHandlerToRoute)
    } else {
      // add linting issue for missing logging handler
      GlobalRules.get.add(LoggingRules.NoLoggingHandler)
      Seq(defaultLoggingHandlerRoute)
    }
  }

  // We start with routes added via load service, note that these will be overridden
  // by any routes added in any call to updateMuxer().
  private[this] val loadServiceRoutes: Seq[Route] =
    LoadService[AdminHttpMuxHandler]().map(muxHandlerToRoute) ++ loggingHandlerRoute

  private[this] var allRoutes: Seq[Route] = loadServiceRoutes

  /**
   * The address to which the Admin HTTP server is bound.
   */
  def adminBoundAddress: InetSocketAddress = {
    // this should never be a NullServer unless
    // [[com.twitter.server.AdminHttpServer#premain]] is skipped/broken
    adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress]
  }

  /**
   * Add a collection of [[Route]]s into admin http server.
   */
  def addAdminRoutes(newRoutes: Seq[Route]): Unit = synchronized {
    allRoutes = allRoutes ++ newRoutes
    updateMuxer()
  }

  /**
   * Add a [[Route]] into admin http server.
   */
  def addAdminRoute(route: Route): Unit = {
    addAdminRoutes(Seq(route))
  }

  /**
   * Get all [[Route]]s of admin http server.
   */
  def routes: Seq[Route] = allRoutes

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

  private[this] def updateMuxer(): Unit = {
    // create a service which multiplexes across all endpoints.
    val localMuxer = allRoutes.foldLeft(new HttpMuxer) {
      case (muxer, route) =>
        val service =
          new IndexView(route.alias, route.path, () => indexEntries).andThen(route.handler)
        muxer.withHandler(route.path, service)
    }

    val endpoints = allRoutes.map { route => s"\t${route.path} => ${route.handler.toString}" }

    log.debug(s"AdminHttpServer Muxer endpoints:\n" + endpoints.mkString("\n"))
    adminHttpMuxer.underlying = HttpUtils.combine(Seq(localMuxer, HttpMuxer))
  }

  /** create index with both the local and global muxer namespace and server/client registries. */
  private[this] def indexEntries: Seq[IndexView.Entry] =
    downstreamClients +: listeningServers +: localRoutes

  /** group listening servers for display */
  private[this] def listeningServers: IndexView.Group = {
    val serverLinks: Seq[IndexView.Entry] = ServerRegistry.registrants.collect {
      case server if server.name.nonEmpty =>
        val encodedName = URLEncoder.encode(server.name, StandardCharsets.UTF_8.name)
        IndexView.Link(server.name, "/admin/servers/" + encodedName)
    }.toSeq

    IndexView.Group("Listening Servers", serverLinks.sorted(IndexView.EntryOrdering))
  }

  /** group downstream clients for display */
  private[this] def downstreamClients: IndexView.Group = {
    val clientLinks: Seq[IndexView.Entry] = ClientRegistry.registrants.collect {
      case client if client.name.nonEmpty =>
        val encodedName = URLEncoder.encode(client.name, StandardCharsets.UTF_8.name)
        IndexView.Link(client.name, "/admin/clients/" + encodedName)
    }.toSeq

    IndexView.Group("Downstream Clients", clientLinks.sorted(IndexView.EntryOrdering))
  }

  /** convert local routes into the IndexView data model */
  private[this] def localRoutes: Seq[IndexView.Entry] = {
    val routes = allRoutes ++
      HttpMuxer.routes.map(Route.from)

    routes
      .filter(_.includeInIndex)
      .groupBy(_.group)
      .flatMap {
        case (groupOpt, rts) =>
          val links = rts.map(routeToIndexLink)
          groupOpt match {
            case Some(group) => Seq(IndexView.Group(group, links))
            case None => links
          }
      }
      .toSeq
  }

  private[this] def routeToIndexLink(route: Route): IndexView.Link =
    IndexView.Link(route.alias, route.path, route.method)

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
    // For consistency, we will add the routes regardless of whether the `adminHttpServer` gets
    // started. This may not always be true and we may change this behavior in the future.
    addAdminRoutes(Admin.adminRoutes(statsReceiver, self))

    // we delay this check until we call the premain to ensure the `disableAdminHttpServer` value
    // has the correct initialization order
    if (disableAdminHttpServer) {
      log.info("admin http is disabled and will not be started.")
    } else {
      startServer()
    }
  }
}
