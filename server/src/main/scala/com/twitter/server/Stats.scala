package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.stats.{StatsReceiver, LoadedStatsReceiver}

trait Stats { app: App =>

  /**
   * This returns the global [[LoadedStatsReceiver]] instance.
   *
   * @return a [[StatsReceiver]] instance.
   * @see [[com.twitter.finagle.stats.LoadedStatsReceiver]]
   */
  def statsReceiver: StatsReceiver = LoadedStatsReceiver
}
