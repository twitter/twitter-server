package com.twitter.server

import com.twitter.app.GlobalFlag
import com.twitter.app.lifecycle.Event
import com.twitter.app.lifecycle.Event.PrebindWarmup
import com.twitter.app.lifecycle.Event.WarmupComplete
import com.twitter.finagle.http.Method.Post
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.http.Route
import com.twitter.finagle.http.RouteIndex
import com.twitter.server.handler._
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._

trait Lifecycle { self: TwitterServer =>
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
    runtimeArgs: Seq[String] =
      ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList) {
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

  private[Lifecycle] object Warmup {

    /**
     * Initialize warmup code. Ensures that the /health endpoint will not return on "OK" response.
     */
    def initializeWarmup(): Unit =
      HttpMuxer.addHandler(Route("/health", new ReplyHandler("warming up\n")))

    /**
     * Prebind warmup code. Used for warmup tasks that we want to run before we
     * accept traffic.
     */
    def prebindWarmup(): Unit = new PromoteToOldGen().beforeServing()

    /**
     * The service is bound to a port and warmed up, announce health.
     */
    def warmupComplete(): Unit =
      HttpMuxer.addHandler(Route("/health", new ReplyHandler("OK\n")))

  }

  /**
   * A [[Warmup]] that is detached from a [[TwitterServer]] lifecycle. As there is no guarantee that
   * this trait is used within the context of a [[TwitterServer]], the behavior and expectations of
   * this warmup are determined by the implementor.
   */
  @deprecated(
    "Warmup behavior is a TwitterServer lifecycle concern. Please mixin Warmup to your TwitterServer.",
    "2020-06-25")
  trait DetatchedWarmup {
    Warmup.initializeWarmup()

    /**
     * Prebind warmup code. Used for warmup tasks that we want to run before we
     * accept traffic.
     */
    def prebindWarmup(): Unit = Warmup.prebindWarmup()

    /**
     * The service is bound to a port and warmed up, announce health.
     */
    def warmupComplete(): Unit = Warmup.warmupComplete()
  }

  /**
   * Give the application control over when to present to Mesos as being ready
   * for traffic. When the method `warmupComplete()` is invoked, the application
   * is considered ready.
   * @note Mesos doesn't gate traffic on /health so all pre-bind warmup needs to
   *       happen in `prebindWarmup()`
   */
  trait Warmup { self: TwitterServer =>
    Warmup.initializeWarmup()

    override protected[twitter] def startupCompletionEvent: Event = WarmupComplete

    /**
     * Prebind warmup code. Used for warmup tasks that we want to run before we
     * accept traffic.
     */
    def prebindWarmup(): Unit = observe(PrebindWarmup) {
      Warmup.prebindWarmup()
    }

    /**
     * The service is bound to a port and warmed up, announce health.
     */
    def warmupComplete(): Unit = observe(WarmupComplete) {
      Warmup.warmupComplete()
    }
  }
}
