package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.stats.{StatsReceiver, LoadedStatsReceiver}
import com.twitter.server.util._

trait Stats { app: App =>
  val statsReceiver: StatsReceiver = LoadedStatsReceiver

  premain {
    JvmStats.register(statsReceiver)
    TwitterStats.register(statsReceiver)
  }
}
