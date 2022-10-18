package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.http.Method
import com.twitter.finagle.stats.AggregateWithHistogramDetails
import com.twitter.finagle.stats.DelegatingStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.stats.WithHistogramDetails
import com.twitter.server.AdminHttpServer.Route
import com.twitter.server.filters.AdminThreadPoolFilter
import com.twitter.server.handler._
import com.twitter.server.view._

object Admin {

  /**
   * Defines many of the default `/admin/` HTTP routes.
   */
  def adminRoutes(statsReceiver: StatsReceiver, app: App): Seq[Route] = {
    // we handle ping in-band with the global default worker pool
    val colocatedRoutes = Seq(
      Route(
        path = "/admin/ping",
        handler = new ReplyHandler("pong"),
        alias = "Ping",
        group = Some(Grouping.Utilities),
        includeInIndex = true
      )
    )

    // everything else is isolated
    val standardRoutes = Seq(
      Route(
        path = Path.Root,
        handler = new AdminRedirectHandler,
        alias = "Admin Redirect",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = Path.Admin,
        handler = new SummaryHandler,
        alias = "Summary",
        group = None,
        includeInIndex = true
      ),
      Route(
        path = Path.Admin + "/",
        // this redirects to admin if the path is exactly Path.Admin + "/"
        // and shows a 404 otherwise.
        handler = new NotFoundView().andThen(new AdminRedirectHandler(_ == Path.Admin + "/")),
        alias = "Admin Redirect",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = "/admin/server_info",
        handler = new TextBlockView().andThen(new ServerInfoHandler()),
        alias = "Build Properties",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = "/admin/contention",
        handler = new TextBlockView().andThen(new ContentionHandler),
        alias = "Contention",
        group = Some(Grouping.PerfProfile),
        includeInIndex = true
      ),
      Route(
        path = "/admin/lint",
        handler = new LintHandler(),
        alias = "Lint",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = "/admin/lint.json",
        handler = new LintHandler(),
        alias = "Lint",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = false
      ),
      Route(
        path = "/admin/failedlint",
        handler = new FailedLintRuleHandler,
        alias = "Failed Lint Rules",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = "/admin/threads",
        handler = new ThreadsHandler,
        alias = "Threads",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = "/admin/threads.json",
        handler = new ThreadsHandler,
        alias = "Threads",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = false
      ),
      Route(
        path = "/admin/announcer",
        handler = new TextBlockView().andThen(new AnnouncerHandler),
        alias = "Announcer",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = "/admin/dtab",
        handler = new DtabHandler,
        alias = "Dtab",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = "/admin/pprof/heap",
        handler = new HeapResourceHandler,
        alias = "Heap",
        group = Some(Grouping.PerfProfile),
        includeInIndex = true
      ),
      Route(
        path = "/admin/pprof/profile",
        handler = new ProfileResourceHandler(Thread.State.RUNNABLE),
        alias = "Profile",
        group = Some(Grouping.PerfProfile),
        includeInIndex = true
      ),
      Route(
        path = "/admin/pprof/contention",
        handler = new ProfileResourceHandler(Thread.State.BLOCKED),
        alias = "Blocked Profile",
        group = Some(Grouping.PerfProfile),
        includeInIndex = true
      ),
      Route(
        path = "/admin/shutdown",
        handler = new ShutdownHandler(app),
        alias = "Shutdown",
        group = Some(Grouping.Utilities),
        includeInIndex = true,
        method = Method.Post
      ),
      Route(
        path = "/admin/tracing",
        handler = new TracingHandler,
        alias = "Tracing",
        group = Some(Grouping.Utilities),
        includeInIndex = true
      ),
      Route(
        path = "/admin/metrics",
        handler = new MetricQueryHandler,
        alias = "Watch",
        group = Some(Grouping.Metrics),
        includeInIndex = true
      ),
      Route(
        path = "/admin/metric_metadata.json",
        handler = new MetricMetadataQueryHandler(),
        alias = "Metric Metadata",
        group = Some(Grouping.Metrics),
        includeInIndex = true
      ),
      Route(
        path = "/admin/metric/expressions.json",
        handler = new MetricExpressionHandler(),
        alias = "Metric Expressions",
        group = Some(Grouping.Metrics),
        includeInIndex = true
      ),
      Route(
        path = Path.Clients,
        handler = new ClientRegistryHandler(Path.Clients),
        alias = "Clients",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = LoadBalancersHandler.RoutePath,
        handler = new LoadBalancersHandler,
        alias = "Load Balancers",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = Path.Servers,
        handler = new ServerRegistryHandler(Path.Servers),
        alias = "Servers",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = Path.Files,
        handler = ResourceHandler
          .fromJar(baseRequestPath = Path.Files, baseResourcePath = "twitter-server"),
        alias = "Files",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = "/admin/registry.json",
        handler = new RegistryHandler,
        alias = "Registry",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = "/admin/toggles",
        handler = new ToggleHandler(),
        alias = "Toggles",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = "/admin/toggles/",
        handler = new ToggleHandler(),
        alias = "Toggles",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = TunableHandler.Path,
        handler = new TunableHandler(),
        alias = "Tunables",
        group = Some(Grouping.ProcessInfo),
        includeInIndex = true
      ),
      Route(
        path = TunableHandler.PathForId,
        handler = new TunableHandler(),
        alias = "Tunables",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = "/favicon.ico",
        ResourceHandler.fromJar(baseRequestPath = "/", baseResourcePath = "twitter-server/img"),
        alias = "Favicon",
        group = None,
        includeInIndex = false
      ),
      Route(
        path = Path.Servers + "connections/",
        handler = new AttachedClientsHandler(),
        alias = "Incoming Connections",
        group = Some(Grouping.Utilities),
        includeInIndex = true
      ),
      Route(
        path = "admin/namespaces",
        handler = new NamespaceHandler(),
        alias = "Namespaces in chronological order",
        group = None,
        includeInIndex = false
      )
    ).map(AdminThreadPoolFilter.isolateRoute)

    // If histograms are available, add an additional endpoint
    val histos = DelegatingStatsReceiver
      .all(statsReceiver)
      .collect { case histo: WithHistogramDetails => histo }
    val aggregate = if (histos.nonEmpty) Some(AggregateWithHistogramDetails(histos)) else None
    colocatedRoutes ++ standardRoutes ++ {
      aggregate match {
        case Some(details) => {
          val histogramHandler = new HistogramQueryHandler(details)
          Seq(
            Route(
              path = "/admin/histograms",
              handler = histogramHandler,
              alias = "Histograms",
              group = Some(Grouping.Metrics),
              includeInIndex = true
            ),
            Route(
              path = "/admin/histograms.json",
              handler = histogramHandler,
              alias = "/admin/histograms.json",
              group = Some(Grouping.Metrics),
              includeInIndex = false
            )
          )
        }
        case None => Nil
      }
    }
  }

  /**
   * Common constants for [[AdminHttpServer.Route]]'s `group`.
   */
  object Grouping {
    val ProcessInfo: String = "Process Info"
    val PerfProfile: String = "Performance Profile"
    val Utilities: String = "Utilities"
    val Metrics: String = "Metrics"
  }

  /**
   * Constants for Admin endpoints.
   */
  object Path {
    val Root: String = ""
    val Admin: String = "/admin"
    val Clients: String = Admin + "/clients/"
    val Servers: String = Admin + "/servers/"
    val Files: String = Admin + "/files/"
  }
}
