package com.twitter.server.lint

import com.twitter.finagle.stats.{
  DelegatingStatsReceiver,
  LoadedStatsReceiver,
  StatsReceiver,
  StatsReceiverWithCumulativeGauges
}
import com.twitter.util.lint.Rule

object TooManyCumulativeGaugesRules {

  def apply(): Seq[Rule] = {
    apply(DelegatingStatsReceiver.all(LoadedStatsReceiver))
  }

  /** Exposed for testing */
  private[lint] def apply(statsReceivers: Seq[StatsReceiver]): Seq[Rule] =
    statsReceivers.collect {
      case srwg: StatsReceiverWithCumulativeGauges =>
        srwg.largeGaugeLinterRule
    }

}
