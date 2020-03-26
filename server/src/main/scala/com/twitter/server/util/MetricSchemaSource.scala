package com.twitter.server.util

import com.twitter.finagle.stats.{MetricSchema, SchemaRegistry}
import com.twitter.finagle.util.LoadService

private[server] object MetricSchemaSource {
  lazy val registry: Seq[SchemaRegistry] = LoadService[SchemaRegistry]()
}

/**
 * A map from stats names to [[com.twitter.finagle.stats.StatEntry StatsEntries]]
 * which allows for stale StatEntries up to `refreshInterval`.
 */
private[server] class MetricSchemaSource(
  registry: Seq[SchemaRegistry] = MetricSchemaSource.registry) {

  /**
   * Indicates whether or not the MetricSource is using latched Counters.
   * @note this relies on the fact that there is only one StatsRegistry and that it is
   *       the finagle implementation.
   */
  lazy val hasLatchedCounters: Boolean = {
    assert(registry.length > 0)
    registry.head.hasLatchedCounters
  }

  /** Returns the entry for `key` if it exists */
  def getSchema(key: String): Option[MetricSchema] = synchronized {
    registry.map(_.schemas()).find(_.contains(key)).flatMap(_.get(key))
  }

  /** Returns all schemas */
  def schemaList(): Iterable[MetricSchema] = synchronized {
    registry
      .foldLeft(IndexedSeq[MetricSchema]()) { (seq, r) => seq ++ r.schemas().values }
  }

  /** Returns true if the map contains `key` and false otherwise. */
  def contains(key: String): Boolean = synchronized {
    registry.exists(_.schemas().contains(key))
  }

  /** Returns the set of stat keys. */
  def keySet: Set[String] = synchronized {
    registry
      .foldLeft(Set[String]()) { (set, r) => set ++ r.schemas().keySet }
  }
}
