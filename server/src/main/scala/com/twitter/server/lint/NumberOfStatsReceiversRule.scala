package com.twitter.server.lint

import com.twitter.finagle.stats.{DelegatingStatsReceiver, LoadedStatsReceiver, StatsReceiver}
import com.twitter.util.lint.{Category, Issue, Rule}

object NumberOfStatsReceiversRule {

  def apply(): Rule =
    apply(DelegatingStatsReceiver.all(LoadedStatsReceiver))

  /** Exposed for testing */
  private[lint] def apply(statsReceivers: Seq[StatsReceiver]): Rule = {
    Rule(
      Category.Performance,
      "Number of StatsReceivers",
      "More than one StatsReceiver loaded causes a larger than necessary " +
        "memory footprint and slower runtime usage. Examine your (transitive) " +
        "dependencies and remove unwanted StatsReceivers either via dependency " +
        "management or the com.twitter.finagle.util.loadServiceDenied flag. " +
        "Alternatively, having none loaded indicates that the service will not " +
        "have any telemetry reported which is dangerous way to operate a service."
    ) {
      if (statsReceivers.size == 1) {
        Nil
      } else if (statsReceivers.isEmpty) {
        Seq(Issue("No StatsReceivers registered"))
      } else {
        Seq(Issue(s"Multiple StatsReceivers registered: ${statsReceivers.mkString(", ")}"))
      }
    }
  }

}
