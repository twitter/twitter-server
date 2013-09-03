package com.twitter.server.util

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.concurrent.Scheduler

object TwitterStats {

  def register(statsReceiver: StatsReceiver) = {
    val sched = statsReceiver.scope("scheduler")

    sched.addGauge("productivity") {
      var cpu, usr = 0L
      for (s <- Scheduler.schedulers) {
        cpu += s.cpuTime
        usr += s.usrTime
      }
      if (cpu.toFloat == 0F) 0F
      else usr.toFloat / cpu.toFloat
    }
    
    sched.addGauge("dispatches") {
      var n = 0L
      for (s <- Scheduler.schedulers)
        n += s.numDispatches
      n.toFloat
    }
  }
}
