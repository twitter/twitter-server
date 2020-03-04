package com.twitter.server

import com.twitter.app.{App, GlobalFlag}
import com.twitter.finagle.http.Method.Post
import com.twitter.finagle.http.{HttpMuxer, Route, RouteIndex}
import com.twitter.server.handler._
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._

trait Lifecycle { self: App =>
  // Mesos/Aurora lifecycle endpoints
  val group = "Misc"
  HttpMuxer.addHandler(
    Route(
      pattern = "/abortabortabort",
      handler = new AbortHandler,
      index = Some(RouteIndex(alias = "Abort Server", group = group, method = Post))
    )
  )
  HttpMuxer.addHandler(
    Route(
      pattern = "/quitquitquit",
      handler = new ShutdownHandler(this),
      index = Some(RouteIndex(alias = "Quit Server", group = group, method = Post))
    )
  )
  HttpMuxer.addHandler(
    Route(
      pattern = "/health",
      handler = new ReplyHandler("OK\n"),
      index = Some(RouteIndex(alias = "Health", group = group))
    )
  )

}

object promoteBeforeServing
    extends GlobalFlag[Boolean](
      true,
      "Promote objects in young generation to old generation before serving requests. " +
        "May shorten the following gc pauses by avoiding the copying back and forth between survivor " +
        "spaces of a service's long lived objects."
    )

object Lifecycle {

  private[server] class PromoteToOldGen(
    runtimeArgs: Seq[String] = ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList) {
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
   * @note Mesos doesn't gate traffic on /health so all pre-bind warmup needs to
   *       happen in `prebindWarmup()`
   */
  trait Warmup {
    HttpMuxer.addHandler(Route("/health", new ReplyHandler("")))

    /**
     * Prebind warmup code. Used for warmup tasks that we want to run before we
     * accept traffic.
     */
    def prebindWarmup(): Unit = {
      new PromoteToOldGen().beforeServing()
    }

    /**
     * The service is bound to a port and warmed up, announce health.
     */
    def warmupComplete(): Unit = {
      HttpMuxer.addHandler(Route("/health", new ReplyHandler("OK\n")))
    }
  }
}
