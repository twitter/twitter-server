package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.handler._

trait Admin { self: App =>
  val url = "\"http://twitter.github.io/twitter-server/Features.html#http-admin-interface\""
  val failString = "Your requested endpoint was not found." +
    "  Please see the <a href=%s>docs</a>".format(url) +
    " or the <a href=\"../admin\">index</a> for the endpoints."
  HttpMuxer.addHandler("/admin", new IndexHandler("/admin/"))
  HttpMuxer.addHandler("/admin/", new FailureHandler(failString))
  HttpMuxer.addHandler("/admin/announcer", new AnnouncerHandler)
  HttpMuxer.addHandler("/admin/clients", new ClientsHandler("/admin/clients"))
  HttpMuxer.addHandler("/admin/contention", new ContentionHandler)
  HttpMuxer.addHandler("/admin/dtab", new DtabHandler)
  HttpMuxer.addHandler("/admin/ping", new ReplyHandler("pong"))
  HttpMuxer.addHandler("/admin/pprof/contention", new ProfileResourceHandler(Thread.State.BLOCKED))
  HttpMuxer.addHandler("/admin/pprof/heap", new HeapResourceHandler)
  HttpMuxer.addHandler("/admin/pprof/profile", new ProfileResourceHandler(Thread.State.RUNNABLE))
  HttpMuxer.addHandler("/admin/server_info", new ServerInfoHandler(this))
  HttpMuxer.addHandler("/admin/shutdown", new ShutdownHandler(this))
  HttpMuxer.addHandler("/admin/threads", new ThreadsHandler)
  HttpMuxer.addHandler("/admin/tracing", new TracingHandler)
}
