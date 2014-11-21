package com.twitter.server.util

import com.twitter.finagle.stats.{BroadcastStatsReceiver, StatsReceiver}
import com.twitter.util.Try
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import scala.collection.mutable

object JvmStats {
  import com.twitter.conversions.string._
  import scala.collection.JavaConverters._

  // set used for keeping track of jvm gauges (otherwise only weakly referenced)
  private[this] val gauges = mutable.Set.empty[Any]

  private[this] val allocations = new Allocations()
  allocations.start()

  def register(statsReceiver: StatsReceiver) {
    val stats = statsReceiver.scope("jvm")

    val mem = ManagementFactory.getMemoryMXBean()

    def heap = mem.getHeapMemoryUsage()
    val heapStats = stats.scope("heap")
    gauges.add(heapStats.addGauge("committed") { heap.getCommitted() })
    gauges.add(heapStats.addGauge("max") { heap.getMax() })
    gauges.add(heapStats.addGauge("used") { heap.getUsed() })

    def nonHeap = mem.getNonHeapMemoryUsage()
    val nonHeapStats = stats.scope("nonheap")
    gauges.add(nonHeapStats.addGauge("committed") { nonHeap.getCommitted() })
    gauges.add(nonHeapStats.addGauge("max") { nonHeap.getMax() })
    gauges.add(nonHeapStats.addGauge("used") { nonHeap.getUsed() })

    val threads = ManagementFactory.getThreadMXBean()
    val threadStats = stats.scope("thread")
    gauges.add(threadStats.addGauge("daemon_count") { threads.getDaemonThreadCount().toLong })
    gauges.add(threadStats.addGauge("count") { threads.getThreadCount().toLong })
    gauges.add(threadStats.addGauge("peak_count") { threads.getPeakThreadCount().toLong })

    val runtime = ManagementFactory.getRuntimeMXBean()
    gauges.add(stats.addGauge("start_time") { runtime.getStartTime() })
    gauges.add(stats.addGauge("uptime") { runtime.getUptime() })

    val os = ManagementFactory.getOperatingSystemMXBean()
    gauges.add(stats.addGauge("num_cpus") { os.getAvailableProcessors().toLong })
    os match {
      case unix: com.sun.management.UnixOperatingSystemMXBean =>
        gauges.add(stats.addGauge("fd_count") { unix.getOpenFileDescriptorCount })
        gauges.add(stats.addGauge("fd_limit") { unix.getMaxFileDescriptorCount })
      case _ =>
    }

    val compilation = ManagementFactory.getCompilationMXBean()
    val compilationStats = stats.scope("compilation")
    gauges.add(compilationStats.addGauge("time_msec") { compilation.getTotalCompilationTime() })

    val classes = ManagementFactory.getClassLoadingMXBean()
    val classLoadingStats = stats.scope("classes")
    gauges.add(classLoadingStats.addGauge("total_loaded") { classes.getTotalLoadedClassCount() })
    gauges.add(classLoadingStats.addGauge("total_unloaded") { classes.getUnloadedClassCount() })
    gauges.add(classLoadingStats.addGauge("current_loaded") { classes.getLoadedClassCount().toLong })


    val memPool = ManagementFactory.getMemoryPoolMXBeans.asScala
    val memStats = stats.scope("mem")
    val currentMem = memStats.scope("current")
    // TODO: Refactor postGCStats when we confirmed that no one is using this stats anymore
    // val postGCStats = memStats.scope("postGC")
    val postGCMem = memStats.scope("postGC")
    val postGCStats = BroadcastStatsReceiver(Seq(stats.scope("postGC"), postGCMem))
    memPool foreach { pool =>
      val name = pool.getName.regexSub("""[^\w]""".r) { m => "_" }
      if (pool.getCollectionUsage != null) {
        def usage = pool.getCollectionUsage // this is a snapshot, we can't reuse the value
        gauges.add(postGCStats.addGauge(name, "used") { usage.getUsed })
        gauges.add(postGCStats.addGauge(name, "max") { usage.getMax })
      }
      if (pool.getUsage != null) {
        def usage = pool.getUsage // this is a snapshot, we can't reuse the value
        gauges.add(currentMem.addGauge(name, "used") { usage.getUsed })
        gauges.add(currentMem.addGauge(name, "max") { usage.getMax })
      }
    }
    gauges.add(postGCStats.addGauge("used") {
      memPool.flatMap(p => Option(p.getCollectionUsage)).map(_.getUsed).sum
    })
    gauges.add(currentMem.addGauge("used") {
      memPool.flatMap(p => Option(p.getUsage)).map(_.getUsed).sum
    })

    // `BufferPoolMXBean` and `ManagementFactory.getPlatfromMXBeans` are introduced in Java 1.7.
    // Use reflection to add these gauges so we can still compile with 1.6
    val bufferPoolStats = memStats.scope("buffer")
    for {
      bufferPoolMXBean <- Try[Class[_]] {
        ClassLoader.getSystemClassLoader.loadClass("java.lang.management.BufferPoolMXBean")
      }
      getPlatformMXBeans <- classOf[ManagementFactory].getMethods.find { m =>
        m.getName == "getPlatformMXBeans" && m.getParameterTypes.length == 1
      }
      pool <- getPlatformMXBeans.invoke(null /* static method */, bufferPoolMXBean)
        .asInstanceOf[java.util.List[_]].asScala
    } {
      val name = bufferPoolMXBean.getMethod("getName").invoke(pool).asInstanceOf[String]

      val getCount: Method = bufferPoolMXBean.getMethod("getCount")
      gauges.add(bufferPoolStats.addGauge(name, "count") { getCount.invoke(pool).asInstanceOf[Long] })

      val getMemoryUsed: Method = bufferPoolMXBean.getMethod("getMemoryUsed")
      gauges.add(bufferPoolStats.addGauge(name, "used") { getMemoryUsed.invoke(pool).asInstanceOf[Long] })

      val getTotalCapacity: Method = bufferPoolMXBean.getMethod("getTotalCapacity")
      gauges.add(bufferPoolStats.addGauge(name, "max") { getTotalCapacity.invoke(pool).asInstanceOf[Long] })
    }

    val gcPool = ManagementFactory.getGarbageCollectorMXBeans.asScala
    val gcStats = stats.scope("gc")
    gcPool foreach { gc =>
      val name = gc.getName.regexSub("""[^\w]""".r) { m => "_" }
      gauges.add(gcStats.addGauge(name, "cycles") { gc.getCollectionCount })
      gauges.add(gcStats.addGauge(name, "msec") { gc.getCollectionTime })
    }

    // note, these could be -1 if the collector doesn't have support for it.
    gauges.add(gcStats.addGauge("cycles") { gcPool.map(_.getCollectionCount).filter(_ > 0).sum })
    gauges.add(gcStats.addGauge("msec") { gcPool.map(_.getCollectionTime).filter(_ > 0).sum })

    if (allocations.trackingEden) {
      val allocationStats = memStats.scope("allocations")
      val eden = allocationStats.scope("eden")
      gauges.add(eden.addGauge("bytes") { allocations.eden })
    }
  }

}
