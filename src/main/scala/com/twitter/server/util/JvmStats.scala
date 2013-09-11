package com.twitter.server.util

import com.twitter.finagle.stats.{BroadcastStatsReceiver, StatsReceiver}
import java.lang.management.ManagementFactory
import scala.collection.mutable

object JvmStats {
  import com.twitter.conversions.string._
  import scala.collection.JavaConverters._

  def register(statsReceiver: StatsReceiver) = {
    val stats = statsReceiver.scope("jvm")

    val mem = ManagementFactory.getMemoryMXBean()

    val heap = mem.getHeapMemoryUsage()
    val heapStats = stats.scope("heap")
    heapStats.addGauge("committed") { heap.getCommitted() }
    heapStats.addGauge("max") { heap.getMax() }
    heapStats.addGauge("used") { heap.getUsed() }

    val nonHeap = mem.getNonHeapMemoryUsage()
    val nonHeapStats = stats.scope("nonheap")
    nonHeapStats.addGauge("committed") { nonHeap.getCommitted() }
    nonHeapStats.addGauge("max") { nonHeap.getMax() }
    nonHeapStats.addGauge("used") { nonHeap.getUsed() }

    val threads = ManagementFactory.getThreadMXBean()
    val threadStats = stats.scope("thread")
    threadStats.addGauge("daemon_count") { threads.getDaemonThreadCount().toLong }
    threadStats.addGauge("count") { threads.getThreadCount().toLong }
    threadStats.addGauge("peak_count") { threads.getPeakThreadCount().toLong }

    val runtime = ManagementFactory.getRuntimeMXBean()
    stats.addGauge("start_time") { runtime.getStartTime() }
    stats.addGauge("uptime") { runtime.getUptime() }

    val os = ManagementFactory.getOperatingSystemMXBean()
    stats.addGauge("num_cpus") { os.getAvailableProcessors().toLong }
    os match {
      case unix: com.sun.management.UnixOperatingSystemMXBean =>
        stats.addGauge("fd_count") { unix.getOpenFileDescriptorCount }
        stats.addGauge("fd_limit") { unix.getMaxFileDescriptorCount }
      case _ =>
    }

    val memPool = ManagementFactory.getMemoryPoolMXBeans.asScala
    val memStats = stats.scope("mem")
    val currentMem = memStats.scope("current")
    // TODO: Refactor postGCStats when we confirmed that no one is using this stats anymore
    // val postGCStats = memStats.scope("postGC")
    val postGCMem = memStats.scope("postGC")
    val postGCStats = BroadcastStatsReceiver(Seq(stats.scope("postGC"), postGCMem))
    memPool foreach { pool =>
      val name = pool.getName.regexSub("""[^\w]""".r) { m => "_" }
      Option(pool.getCollectionUsage) foreach { usage =>
        postGCStats.addGauge(name, "used") { usage.getUsed }
        postGCStats.addGauge(name, "max") { usage.getMax }
      }
      Option(pool.getUsage) foreach { usage =>
        currentMem.addGauge(name, "used") { usage.getUsed }
        currentMem.addGauge(name, "max") { usage.getMax }
      }
    }
    postGCStats.addGauge("used") {
      memPool flatMap(p => Option(p.getCollectionUsage)) map(_.getUsed) sum
    }
    currentMem.addGauge("used") {
      memPool flatMap(p => Option(p.getUsage)) map(_.getUsed) sum
    }

    val gcPool = ManagementFactory.getGarbageCollectorMXBeans.asScala
    val gcStats = stats.scope("gc")
    gcPool foreach { gc =>
      val name = gc.getName.regexSub("""[^\w]""".r) { m => "_" }
      gcStats.addGauge(name, "cycles") { gc.getCollectionCount }
      gcStats.addGauge(name, "msec") { gc.getCollectionTime }
    }

    // note, these could be -1 if the collector doesn't have support for it.
    gcStats.addGauge("cycles") { gcPool map(_.getCollectionCount) filter(_ > 0) sum }
    gcStats.addGauge("msec") { gcPool map(_.getCollectionTime) filter(_ > 0) sum }
  }
}

