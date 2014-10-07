package com.twitter.server

import com.twitter.app.{GlobalFlag, App}
import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.handler._
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._

trait Lifecycle { self: App =>
  // Mesos/Aurora lifecycle endpoints
  HttpMuxer.addHandler("/abortabortabort", new AbortHandler)
  HttpMuxer.addHandler("/quitquitquit", new ShutdownHandler(this))
  HttpMuxer.addHandler("/health", new ReplyHandler("OK\n"))
}

object promoteBeforeServing extends GlobalFlag[Boolean](false,
  "Promote objects in young generation to old generation before serving requests. " +
    "May shorten the following gc pauses by avoiding the copying back and forth between survivor " +
    "spaces of a service's long lived objects.")

object Lifecycle {

  private[server] class PromoteToOldGen(
      runtimeArgs: Seq[String] = ManagementFactory.getRuntimeMXBean.getInputArguments.asScala)
  {
    private[this] val hasPromoted = new AtomicBoolean(false)

    private[server] def promoted: Boolean = hasPromoted.get()

    /**
     * Depending on the VM flags used, `System.gc` will not have the effect we want.
     */
    private[this] def shouldExplicitGc: Boolean = {
      !runtimeArgs.contains("-XX:+ExplicitGCInvokesConcurrent")
    }

    /**
     * Helps make early gc pauses more consistent.
     *
     * Ideally this runs in the moments right before your service starts accepting traffic.
     *
     * Note: while we gather more data points, you must explicitly set the
     * `promoteBeforeServing` flag to true, but we expect to default that to true in the future.
     * (the application flags must have already been parsed).
     */
    def beforeServing(): Unit = {
      if (promoteBeforeServing() && shouldExplicitGc && hasPromoted.compareAndSet(false, true)) {
        // This relies on the side-effect of a full gc that all objects in the
        // young generation are promoted to the old generation.
        // If this side-effect were to disappear in a future version
        // of the jdk, it would not be disastrous. However, then we may choose
        // to add a `System.promoteAllLiveObjects()` hook.
        System.gc()
      }
    }
  }

  /**
   * Give the application control over when to present to Mesos as being ready
   * for traffic. When the method `warmupComplete()` is invoked, the application
   * is considered ready.
   */
  trait Warmup {
    HttpMuxer.addHandler("/health", new ReplyHandler(""))

    def warmupComplete(): Unit = {
      new PromoteToOldGen().beforeServing()
      HttpMuxer.addHandler("/health", new ReplyHandler("OK\n"))
    }
  }
}
