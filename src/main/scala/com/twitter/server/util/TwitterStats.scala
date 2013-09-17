package com.twitter.server.util

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.concurrent.Scheduler

object TwitterStats {

  def register(statsReceiver: StatsReceiver) = {
    val sched = statsReceiver.scope("scheduler")

    sched.addGauge("productivity") {
      val cpu = Scheduler.cpuTime
      val usr = Scheduler.usrTime
      if (cpu.toFloat == 0F) 0F
      else usr.toFloat / cpu.toFloat
    }
    
    sched.addGauge("dispatches") {
      Scheduler.numDispatches.toFloat
    }
  }
}
