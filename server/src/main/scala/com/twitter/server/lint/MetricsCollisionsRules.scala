package com.twitter.server.lint

import com.twitter.finagle.stats.{
  CollisionTrackingStatsReceiver,
  DelegatingStatsReceiver,
  LoadedStatsReceiver,
  StatsReceiver
}
import com.twitter.util.lint.Rule

object MetricsCollisionsRules {
  def apply(): Seq[Rule] = {
    apply(DelegatingStatsReceiver.all(LoadedStatsReceiver))
  }

  private def apply(statsReceivers: Seq[StatsReceiver]): Seq[Rule] =
    statsReceivers.collect {
      case ctsr: CollisionTrackingStatsReceiver => ctsr.metricsCollisionsLinterRule
    }
}
