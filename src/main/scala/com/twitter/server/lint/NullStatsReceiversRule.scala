package com.twitter.server.lint

import com.twitter.finagle.param.Stats
import com.twitter.finagle.server.ServerRegistry
import com.twitter.finagle.util.StackRegistry
import com.twitter.server.AdminHttpServer
import com.twitter.util.lint.{Category, Issue, Rule}

object NullStatsReceiversRule {

  def isNullStats(entry: StackRegistry.Entry): Boolean =
    entry.params[Stats].statsReceiver.isNull

  def isAdminServer(stackReg: StackRegistry)(entry: StackRegistry.Entry): Boolean =
    stackReg.registryName == ServerRegistry.registryName &&
      entry.name == AdminHttpServer.ServerName

  def toIssue(stackReg: StackRegistry)(entry: StackRegistry.Entry): Issue =
    Issue(s"${stackReg.registryName} name=${entry.name}")

  def apply(stackReg: StackRegistry): Rule = {
    Rule(
      Category.Configuration,
      s"Finagle ${stackReg.registryName} without metrics",
      "Not reporting metrics makes investigating problems exceedingly difficult. " +
        "Wire in a `StatsReceiver` via `ClientBuilder.reportTo()` or " +
        "`Stack.configured(param.Stats)`"
    ) {
      stackReg.registrants
        .filter(isNullStats)
        .filterNot(isAdminServer(stackReg))
        .map(toIssue(stackReg))
        .toSeq
    }
  }

}
