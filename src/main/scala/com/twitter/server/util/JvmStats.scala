package com.twitter.server.util

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.jvm

@deprecated("Use JvmStats from util-jvm directly", "2017-08-11")
object JvmStats {

  def register(statsReceiver: StatsReceiver): Unit = {
    jvm.JvmStats.register(statsReceiver)
  }

}
