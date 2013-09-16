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
  /**
   * Give the application control over when to present to Mesos as being ready
   * for traffic. When the method `warmupComplete()` is invoked, the application
   * is considered ready.
   */
  trait Warmup {
    HttpMuxer.addHandler("/health", new ReplyHandler(""))
    def warmupComplete() = HttpMuxer.addHandler("/health", new ReplyHandler("OK\n"))
  }
}
