package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.{Logger, Logging, NullLogger}
import com.twitter.util.{Duration, Await, Closable}
import com.twitter.util.TimeConversions._

/**
 * Supports the specification of a default close grace period from a flag.
 *
 * See [[com.twitter.app.App.defaultCloseGracePeriod]] for details.
 */
@deprecated("Closer behavior has been moved to com.twitter.app.App", "1.5.3")
trait Closer { self: App =>
  private[this] val closeGracePeriod =
    flag("close.gracePeriod", Duration.Top, "Default closable grace period (deprecated)")

  override def defaultCloseGracePeriod = closeGracePeriod()
}
