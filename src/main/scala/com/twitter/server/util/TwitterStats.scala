package com.twitter.server.util

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.concurrent.Scheduler

object TwitterStats {

  def register(statsReceiver: StatsReceiver): Unit = {
    val scheduler = statsReceiver.scope("scheduler")

    scheduler.provideGauge("dispatches") {
      Scheduler.numDispatches.toFloat
    }
  }
}
