package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.{Logger, Logging, NullLogger}
import com.twitter.util.{Await, Closable}
import com.twitter.util.TimeConversions._

/**
 * An App mixin providing a `closeOnExit` function for graceful shutdown of
 * Closable interfaces. This method adds shutdown hooks that enforces a grace
 * period between closing `Closable`s and exiting the VM.
 *
 * An example use case is allowing Finagle servers to drain on shutdown, giving
 * outstanding requests time to finish.
 */
trait Closer { self: App =>
  private[this] val closeGracePeriod = flag("close.gracePeriod", 5.seconds, "Closable grace period")

  private[this] lazy val log: Logger = self match {
    case logging: Logging => logging.log
    case _ => NullLogger
  }

  /**
   * Register a Closable to be closed on application exit. Messages relevant
   * to `close` invocation will be logged on shutdown.
   */
  def closeOnExit(closable: Closable) {
    onExit {
      log.info("Closing %s with grace period of %s", closable, closeGracePeriod())

      val closeFuture =
        closable.close(closeGracePeriod().fromNow) onFailure { t =>
          log.warning(t, "%s failed to close within %s", closable, closeGracePeriod())
        }

      Await.ready(closeFuture)
    }
  }
}
