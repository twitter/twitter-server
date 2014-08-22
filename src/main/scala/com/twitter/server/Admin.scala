package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.handler._
import com.twitter.server.responder._

trait Admin { self: App =>
  val url = "\"http://twitter.github.io/twitter-server/Features.html#http-admin-interface\""
  val failString = "Your requested endpoint was not found." +
    "  Please see the <a href=%s>docs</a>".format(url) +
    " or the <a href=\"../admin\">index</a> for the endpoints."
  HttpMuxer.addHandler("/admin", new RequestHandler(new IndexResponder(new ConfigurationFlags(self))))
  HttpMuxer.addHandler("/admin/metrics_graphs", new RequestHandler(new TemplateResponder("MetricsList")))
  HttpMuxer.addHandler("/admin/metrics", new MetricsHandler("/admin/metrics"))
  HttpMuxer.addHandler("/admin/files", new FileHandler("/admin/files", "twitter-server/src/main/resources"))
  HttpMuxer.addHandler("/admin/", new FailureHandler(failString))
  HttpMuxer.addHandler("/admin/announcer", new RequestHandlerJson(new AnnouncerHandler))
  HttpMuxer.addHandler("/admin/clients/", new RequestHandler(new ClientResponder("/admin/clients/")))
  HttpMuxer.addHandler("/admin/contention", new RequestHandlerJson(new ContentionHandler))
  HttpMuxer.addHandler("/admin/dtab", new DtabHandler)
  HttpMuxer.addHandler("/admin/ping", new RequestHandlerJson(new ReplyHandler("pong")))
  HttpMuxer.addHandler("/admin/pprof/contention", new ProfileResourceHandler(Thread.State.BLOCKED))
  HttpMuxer.addHandler("/admin/pprof/heap", new HeapResourceHandler)
  HttpMuxer.addHandler("/admin/pprof/profile", new ProfileResourceHandler(Thread.State.RUNNABLE))
  HttpMuxer.addHandler("/admin/server_info", new RequestHandlerJson(new ServerInfoHandler(this)))
  HttpMuxer.addHandler("/admin/shutdown", new ShutdownHandler(this))
  HttpMuxer.addHandler("/admin/threads", new RequestHandlerJson(new ThreadsHandler))
  HttpMuxer.addHandler("/admin/tracing", new TracingHandler)
  HttpMuxer.addHandler("/admin/logging", new LoggingHandler)
}
