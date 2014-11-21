package com.twitter.server.util

import com.twitter.conversions.time._
import com.twitter.finagle.stats.{StatEntry, StatsRegistry}
import com.twitter.finagle.util.LoadService
import com.twitter.util.{Duration, Time}

private[server] object MetricSource {
  lazy val registry = LoadService[StatsRegistry]()
  case class Entry(name: String, delta: Double, value: Double)
}

/**
 * A map from stats names to [[com.twitter.finagle.stats.StatsEntry]]'s
 * which allows for stale StatEntries up to `refreshInterval`.
 */
private[server] class MetricSource(
  registry: () => Seq[StatsRegistry] = { () => MetricSource.registry },
  refreshInterval: Duration = 1.second
) {
  private[this] var lastRefresh = Time.now - refreshInterval
  private[this] var underlying: Map[String, StatEntry] = Map.empty

  private[this] def refresh(): Unit = {
    if (Time.now - lastRefresh > refreshInterval) {
      val newStats = registry().foldLeft(Map[String, StatEntry]()) {
        (map, r) => map ++ r()
      }
      underlying = newStats
      lastRefresh = Time.now
    }
  }

  /** Returns the entry for `key` if it exists */
  def get(key: String): Option[MetricSource.Entry] = synchronized {
    refresh()
    for (s <- underlying.get(key)) yield
      MetricSource.Entry(key, s.delta, s.value)
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
}