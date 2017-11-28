package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.stats.{StatsReceiver, LoadedStatsReceiver}
import com.twitter.server.util._

trait Stats { app: App =>
  /**
   * By default this returns the global [[LoadedStatsReceiver]] instance. Take note when
   * overriding this method to return the *same instance* on multiple calls otherwise you
   * will get surprising stating behavior.
   *
   * @return a [[StatsReceiver]] instance.
   * @see [[com.twitter.finagle.stats.LoadedStatsReceiver]]
   */
  def statsReceiver: StatsReceiver = LoadedStatsReceiver

  premain {
    TwitterStats.register(statsReceiver)
  }
}
