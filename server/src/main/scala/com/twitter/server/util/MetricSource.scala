package com.twitter.server.util

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.stats.{StatEntry, StatsRegistry}
import com.twitter.finagle.util.LoadService
import com.twitter.server.util.MetricSource.MetricTypeInfo
import com.twitter.util.{Duration, Time}

private[server] object MetricSource {
  lazy val registry = LoadService[StatsRegistry]()
  case class Entry(name: String, delta: Double, value: Double)
  case class MetricTypeInfo(name: String, kind: String)
}

/**
 * A map from stats names to [[com.twitter.finagle.stats.StatEntry StatsEntries]]
 * which allows for stale StatEntries up to `refreshInterval`.
 */
private[server] class MetricSource(
  registry: () => Seq[StatsRegistry] = { () =>
    MetricSource.registry
  },
  refreshInterval: Duration = 1.second) {
  private[this] var lastRefresh = Time.now - refreshInterval
  private[this] var underlying: Map[String, StatEntry] = Map.empty

  private[this] def refresh(): Unit = {
    if (Time.now - lastRefresh > refreshInterval) {
      val newStats = registry().foldLeft(Map[String, StatEntry]()) { (map, r) =>
        map ++ r()
      }
      underlying = newStats
      lastRefresh = Time.now
    }
  }

  /** Returns whether or not the MetricSource is using latched Counters
   * @note this relies on the fact that there is only one StatsRegistry and that it is
   *       the finagle implementation.
   */
  def hasLatchedCounters(): Boolean = synchronized {
    val statsRegistry = registry().headOption.getOrElse {
      throw new RuntimeException("No StatsRegistries available")
    }
    statsRegistry.latched
  }

  /** Returns the entry for `key` if it exists */
  def get(key: String): Option[MetricSource.Entry] = synchronized {
    refresh()
    for (s <- underlying.get(key)) yield MetricSource.Entry(key, s.delta, s.value)
  }

  /** Returns the entry for `key` with type info if the entry exists */
  def getType(key: String): Option[MetricSource.MetricTypeInfo] = synchronized {
    refresh()
    for (s <- underlying.get(key))
      yield MetricSource.MetricTypeInfo(key, s.metricType)
  }

  /** Returns true if the map contains `key` and false otherwise. */
  def contains(key: String): Boolean = synchronized {
    refresh()
    underlying.contains(key)
  }

  /** Returns the set of stat keys. */
  def keySet: Set[String] = synchronized {
    refresh()
    underlying.keySet
  }

  /** Returns a map of metric names to their types */
  def typeMap: Iterable[MetricTypeInfo] = synchronized {
    refresh()
    underlying.map {
      case (name, statEntry) => MetricSource.MetricTypeInfo(name, statEntry.metricType)
    }
  }
}