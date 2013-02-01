package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.Logging
import com.twitter.finagle.http.HttpMuxer

trait TwitterServer extends App
  with Admin
  with Logging
{
  HttpMuxer.addHandler("/", new IndexHandler)
  HttpMuxer.addHandler("/abortabortabort", new MesosAbortHandler)
  HttpMuxer.addHandler("/contention", new ContentionHandler)
  HttpMuxer.addHandler("/health", new ReplyHandler("OK\n"))
  HttpMuxer.addHandler("/ping", new ReplyHandler("pong"))
  HttpMuxer.addHandler("/pprof/contention", new ProfileResourceHandler(Thread.State.BLOCKED))
  HttpMuxer.addHandler("/pprof/heap", new HeapResourceHandler)
  HttpMuxer.addHandler("/pprof/profile", new ProfileResourceHandler(Thread.State.RUNNABLE))
  HttpMuxer.addHandler("/quitquitquit", new ShutdownHandler)
  HttpMuxer.addHandler("/server_info", new ServerInfoHandler(this))
  HttpMuxer.addHandler("/shutdown", new ShutdownHandler)
  HttpMuxer.addHandler("/threads", new ThreadsHandler)
  HttpMuxer.addHandler("/tracing", new TracingHandler)
}
