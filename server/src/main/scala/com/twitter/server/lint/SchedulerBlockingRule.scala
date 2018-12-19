package com.twitter.server.lint

import com.twitter.concurrent.Scheduler
import com.twitter.conversions.DurationOps._
import com.twitter.util.Duration
import com.twitter.util.lint.{Category, Issue, Rule}

/**
 * Lint rule for code that is blocking the `Scheduler`.
 */
object SchedulerBlockingRule {

  /**
   * The specific amount for the minumum is semi-arbitrary, but this allows
   * for minimal blocking in services, such as during startup, without
   * triggering this lint rule.
   */
  def apply(): Rule =
    apply(Scheduler, 5.seconds)

  /** exposed for testing */
  private[lint] def apply(scheduler: Scheduler, minimumBlocking: Duration): Rule = {
    Rule(
      Category.Performance,
      "Blocking the Scheduler",
      """
        |Blocking the Scheduler via usage of `com.twitter.util.Await` can
        |cause unexpected slowness, a decrease in throughput, and potentially
        |deadlocks for your application. Developers should instead write
        |code in terms of the `Future` combinators or do the blocking in
        |a `com.twitter.util.FuturePool`. To help track down the code
        |doing the blocking, you can set the System property
        |`-Dcom.twitter.concurrent.schedulerSampleBlockingFraction=$fraction`
        |which is defined in `com.twitter.concurrent.LocalScheduler` and it will
        |log that fraction of blocking stacktraces.
        |
        |The metric for this lint rule is exported at "scheduler/blocking_ms"
        |and more details can be found at:
        |https://twitter.github.io/finagle/guide/Metrics.html#scheduler
      """.stripMargin
    ) {
      val blocking = Duration.fromNanoseconds(scheduler.blockingTimeNanos)
      if (blocking <= minimumBlocking)
        Nil
      else
        Seq(Issue(s"The Scheduler has been blocked for ${blocking.inMillis} milliseconds"))
    }
  }

}
