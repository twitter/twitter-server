package com.twitter.server.util

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.stats.StatEntry
import com.twitter.finagle.stats.StatsRegistry
import com.twitter.finagle.util.LoadService
import com.twitter.server.util.MetricSource.MetricTypeInfo
import com.twitter.util.Duration
import com.twitter.util.Time
import java.util.concurrent.atomic.AtomicReference

private[server] object MetricSource {
  lazy val registry: Seq[StatsRegistry] = LoadService[StatsRegistry]()
  case class Entry(name: String, delta: Double, value: Double)
  case class MetricTypeInfo(name: String, kind: String)
}

/**
 * A map from stats names to [[com.twitter.finagle.stats.StatEntry StatsEntries]]
 * which allows for stale StatEntries up to `refreshInterval`.
 */
private[server] class MetricSource(
  registry: () => Seq[StatsRegistry] = { () => MetricSource.registry },
  refreshInterval: Duration = 1.second) {

  // null while the map is being refreshed
  private[this] val lastRefresh = new AtomicReference(Time.now - refreshInterval)

  @volatile private[this] var underlying: Map[String, StatEntry] = Map.empty

  private[this] def refresh(): Unit = {
    val last = lastRefresh.get()
    if (last != null && Time.now - last > refreshInterval &&
      lastRefresh.compareAndSet(last, null)) {
      val newStats = registry().foldLeft(Map[String, StatEntry]()) { (map, r) =>
        map ++ r().iterator
      }
      underlying = newStats
      lastRefresh.set(Time.now)
    }
  }

  /** Returns whether or not the MetricSource is using latched Counters
   * @note this relies on the fact that there is only one StatsRegistry and that it is
   *       the finagle implementation.
   */
  def hasLatchedCounters(): Boolean = {
    val statsRegistry = registry().headOption.getOrElse {
      throw new RuntimeException("No StatsRegistries available")
    }
    statsRegistry.latched
  }

  /** Returns the entry for `key` if it exists */
  def get(key: String): Option[MetricSource.Entry] = {
    refresh()
    for (s <- underlying.get(key)) yield MetricSource.Entry(key, s.delta, s.value)
  }

  /** Returns the entry for `key` with type info if the entry exists */
  def getType(key: String): Option[MetricSource.MetricTypeInfo] = {
    refresh()
    for (s <- underlying.get(key))
      yield MetricSource.MetricTypeInfo(key, s.metricType)
  }

  /** Returns true if the map contains `key` and false otherwise. */
  def contains(key: String): Boolean = {
    refresh()
    underlying.contains(key)
  }

  /** Returns the set of stat keys. */
  def keySet: Set[String] = {
    refresh()
    underlying.keySet
  }

  /** Returns a map of metric names to their types */
  def typeMap: Iterable[MetricTypeInfo] = {
    refresh()
    underlying.map {
      case (name, statEntry) => MetricSource.MetricTypeInfo(name, statEntry.metricType)
    }
  }
}
