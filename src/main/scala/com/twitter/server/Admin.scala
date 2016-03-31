package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.stats.LoadedStatsReceiver
import com.twitter.finagle.stats.WithHistogramDetails
import com.twitter.server.handler._
import com.twitter.server.view._

object Admin {

  /**
   * Common constants for [[AdminHttpServer.Route]]'s `group`.
   */
  object Grouping {
    val ProcessInfo = "Process Info"
    val PerfProfile = "Performance Profile"
    val Utilities = "Utilities"
    val Metrics = "Metrics"
  }

  /**
   * Constants for Admin endpoints.
   */
  object Path {
    val Root = ""
    val Admin = "/admin"
    val Clients = Admin + "/clients/"
    val Servers = Admin + "/servers/"
    val Files = Admin + "/files/"
  }
}

/**
 * Defines many of the default `/admin/` HTTP routes.
 */
trait Admin { self: App with AdminHttpServer with Stats =>
  import Admin._
  import AdminHttpServer.Route
  import Admin.Grouping

  override protected def routes: Seq[Route] = { 
    val standardRoutes = Seq(
      Route(
        path = Path.Root, handler = new AdminRedirectHandler,
        alias = "Admin Redirect", group = None, includeInIndex = false),
      Route(
        path = Path.Admin, handler = new SummaryHandler,
        alias = "Summary", group = None, includeInIndex = true),
      Route(
        path = "/admin/server_info", handler = new TextBlockView andThen new ServerInfoHandler(self),
        alias = "Build Properties", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = "/admin/contention", handler = new TextBlockView andThen new ContentionHandler,
        alias = "Contention", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = "/admin/lint", handler = new LintHandler(),
        alias = "Lint", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = "/admin/lint.json", handler = new LintHandler(),
        alias = "Lint", group = Some(Grouping.ProcessInfo), includeInIndex = false),
      Route(
        path = "/admin/threads", handler = new ThreadsHandler,
        alias = "Threads", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = "/admin/threads.json", handler = new ThreadsHandler,
        alias = "Threads", group = Some(Grouping.ProcessInfo), includeInIndex = false),
      Route(
        path = "/admin/announcer", handler = new TextBlockView andThen new AnnouncerHandler,
        alias = "Announcer", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = "/admin/dtab", handler = new TextBlockView andThen new DtabHandler,
        alias = "Dtab", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = "/admin/pprof/heap", handler = new HeapResourceHandler,
        alias = "Heap", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = "/admin/pprof/profile", handler = new ProfileResourceHandler(Thread.State.RUNNABLE),
        alias = "Profile", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = "/admin/pprof/contention", handler = new ProfileResourceHandler(Thread.State.BLOCKED),
        alias = "Blocked Profile", group = Some(Grouping.PerfProfile), includeInIndex = true),
      Route(
        path = "/admin/ping", handler = new ReplyHandler("pong"),
        alias = "Ping", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = "/admin/shutdown", handler = new ShutdownHandler(this),
        alias = "Shutdown", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = "/admin/tracing", handler = new TracingHandler,
        alias = "Tracing", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = "/admin/events", handler = new EventsHandler,
        alias = "Events", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = "/admin/events/record/", handler = new EventRecordingHandler(),
        alias = "EventRecording", group = None, includeInIndex = false),
      Route(
        path = "/admin/logging", handler = new LoggingHandler,
        alias = "Logging", group = Some(Grouping.Utilities), includeInIndex = true),
      Route(
        path = "/admin/metrics", handler = new MetricQueryHandler,
        alias = "Watch", group = Some(Grouping.Metrics), includeInIndex = true),
      Route(
        path = Path.Clients, handler = new ClientRegistryHandler(Path.Clients),
        alias = "Clients", group = None, includeInIndex = false),
      Route(
        path = Path.Servers, handler = new ServerRegistryHandler(Path.Servers),
        alias = "Servers", group = None, includeInIndex = false),
      Route(
        path = Path.Files,
        handler = ResourceHandler.fromJar(
          baseRequestPath = Path.Files,
          baseResourcePath = "twitter-server"),
        alias = "Files", group = None, includeInIndex = false),
      Route(
        path = "/admin/registry.json", handler = new RegistryHandler,
        alias = "Registry", group = Some(Grouping.ProcessInfo), includeInIndex = true),
      Route(
        path = "/favicon.ico", ResourceHandler.fromJar(
          baseRequestPath = "/",
          baseResourcePath = "twitter-server/img"),
        alias = "Favicon", group = None, includeInIndex = false)
    )
 
    // If histograms are available, add an additional endpoint
    statsReceiver match {
      // TODO: should be changed to annotate StatsReceivers
      // that can delegate to other StatsReceivers
      case lsr: LoadedStatsReceiver.type => 
        lsr.self match {
          case details: WithHistogramDetails => 
            val histogramHandler = new HistogramQueryHandler(details)
            standardRoutes ++ Seq(
              Route(
                path = "/admin/histograms", handler = histogramHandler,
                alias = "Histograms", group = Some(Grouping.Metrics), includeInIndex = true),
              Route(
                path = "/admin/histograms.json", handler = histogramHandler,
                alias = "/admin/histograms.json", group = Some(Grouping.Metrics), includeInIndex = true)
            )
          case _ => standardRoutes
        }
      case _ => standardRoutes
    }
  }
}