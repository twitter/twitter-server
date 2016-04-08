package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.service.FailFastFactory
import com.twitter.finagle.param
import com.twitter.finagle.server.ServerRegistry
import com.twitter.finagle.stats.{StatsReceiverWithCumulativeGauges, LoadedStatsReceiver,
  DelegatingStatsReceiver}
import com.twitter.finagle.util.StackRegistry
import com.twitter.server.lint.LoggingRules
import com.twitter.util.lint._

/**
 * Registers any global linter [[Rule rules]].
 */
trait Linters { app: App =>

  private[this] def rules: Rules =
    GlobalRules.get

  premain {
    registerLinters()
  }

  /** Exposed for testing */
  private[server] def registerLinters(): Unit = {
    numberOfStatsReceivers()
    tooManyCumulativeGauges()
    Seq(ClientRegistry, ServerRegistry).foreach { registry =>
      stackRegistryDuplicates(registry)
      nullStatsReceivers(registry)
    }
    memcacheFailFast()
    rules.add(LoggingRules.MultipleSlf4jImpls)
  }

  private[this] def numberOfStatsReceivers(): Unit = {
    val rule = Rule(
      Category.Performance,
      "Number of StatsReceivers",
      "More than one StatsReceiver loaded causes a larger than necessary " +
        "memory footprint and slower runtime usage. Examine your (transitive) " +
        "dependencies and remove unwanted StatsReceivers either via dependency " +
        "management or the com.twitter.finagle.util.loadServiceIgnoredPaths flag. " +
        "Alternatively, having none loaded indicates that the service will not " +
        "have any telemetry reported which is dangerous way to operate a service."
    ) {
      val srs = DelegatingStatsReceiver.all(LoadedStatsReceiver)
      if (srs.size == 1) {
        Nil
      } else if (srs.isEmpty) {
        Seq(Issue("No StatsReceivers registered"))
      } else {
        Seq(Issue(s"Multiple StatsReceivers registered: ${srs.mkString(", ")}"))
      }
    }
    rules.add(rule)
  }

  private[this] def tooManyCumulativeGauges(): Unit = {
    val srs = DelegatingStatsReceiver.all(LoadedStatsReceiver)
    srs.foreach {
      case srwg: StatsReceiverWithCumulativeGauges =>
        srwg.registerLargeGaugeLinter(rules)
      case _ =>
    }
  }

  private[this] def stackRegistryDuplicates(stackReg: StackRegistry): Unit = {
    val rule = Rule(
      Category.Configuration,
      s"Duplicate ${stackReg.registryName} StackRegistry names",
      "Duplicate registry entries indicate that multiple clients or servers are " +
        "being registered with the same `com.twitter.finagle.param.Label`."
    ) {
      val dups = stackReg.registeredDuplicates
      if (dups.isEmpty) Nil
      else {
        dups.map { e =>
          Issue(s"name=${e.name} protocolLib=${e.protocolLibrary} addr=${e.addr}")
        }
      }
    }
    rules.add(rule)
  }

  private[this] def nullStatsReceivers(stackReg: StackRegistry): Unit = {
    val rule = Rule(
      Category.Configuration,
      s"Finagle ${stackReg.registryName} without metrics",
      "Not reporting metrics makes investigating problems exceedingly difficult. " +
        "Wire in a `StatsReceiver` via `ClientBuilder.reportTo()` or " +
        "`Stack.configured(param.Stats)`"
    ) {
      stackReg.registrants.flatMap { entry =>
        val sr = entry.params[param.Stats].statsReceiver
        if (!sr.isNull) None else {
          if (stackReg == ServerRegistry && entry.name == AdminHttpServer.ServerName)
            None
          else
            Some(Issue(s"${stackReg.registryName} name=${entry.name}"))
        }
      }.toSeq
    }
    rules.add(rule)
  }

  private[this] def memcacheFailFast(): Unit = {
    val rule = Rule(
      Category.Configuration,
      "Memcache client has FailFast enabled",
      """FailFast is only appropriate when a replica set is present. Memcached
        |is usually sharded. Consider disabling FailFast or using the
        |c.t.f.Memcached.client which has recommended settings built in.""".stripMargin
    ) {
      ClientRegistry.registrants
        .filter(_.protocolLibrary == "memcached")
        .groupBy(_.name)
        .filter { case (_, entries) =>
          entries.head.params[FailFastFactory.FailFast].enabled == true
        }.map { case (label, _) =>
          Issue(s"$label memcache client should disable FailFast")
        }.toSeq
    }
    GlobalRules.get.add(rule)
  }
}
