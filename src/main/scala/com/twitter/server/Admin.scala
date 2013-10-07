package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.handler._

trait Admin { self: App =>
  HttpMuxer.addHandler("/admin", new IndexHandler("/admin/"))
  HttpMuxer.addHandler("/admin/announcer", new AnnouncerHandler)
  HttpMuxer.addHandler("/admin/contention", new ContentionHandler)
  HttpMuxer.addHandler("/admin/ping", new ReplyHandler("pong"))
  HttpMuxer.addHandler("/admin/pprof/contention", new ProfileResourceHandler(Thread.State.BLOCKED))
  HttpMuxer.addHandler("/admin/pprof/heap", new HeapResourceHandler)
  HttpMuxer.addHandler("/admin/pprof/profile", new ProfileResourceHandler(Thread.State.RUNNABLE))
  HttpMuxer.addHandler("/admin/server_info", new ServerInfoHandler(this))
  HttpMuxer.addHandler("/admin/shutdown", new ShutdownHandler)
  HttpMuxer.addHandler("/admin/threads", new ThreadsHandler)
  HttpMuxer.addHandler("/admin/tracing", new TracingHandler)
}
