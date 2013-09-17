package com.twitter.server

import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.handler._

trait Lifecycle {
  // Mesos/Aurora lifecycle endpoints
  HttpMuxer.addHandler("/abortabortabort", new AbortHandler)
  HttpMuxer.addHandler("/quitquitquit", new ShutdownHandler)
  HttpMuxer.addHandler("/health", new ReplyHandler("OK\n"))
}

object Lifecycle {
  trait Warmup {
    HttpMuxer.addHandler("/health", new ReplyHandler(""))
    def warmupComplete() = HttpMuxer.addHandler("/health", new ReplyHandler("OK\n"))
  }
}