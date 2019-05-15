package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.netty4.referenceLeakLintRule
import com.twitter.finagle.server.ServerRegistry
import com.twitter.server.lint._
import com.twitter.util.lint._

/**
 * Registers any global linter [[Rule rules]].
 */
trait Linters { app: App =>

  premain {
    registerLinters()
  }

  /** Exposed for testing */
  def linterRules: Seq[Rule] = {
    TooManyCumulativeGaugesRules() ++
      MetricsCollisionsRules() ++
      Seq(
        SchedulerBlockingRule(),
        DuplicateLoadServiceBindings(),
        NumberOfStatsReceiversRule(),
        StackRegistryDuplicatesRule(ClientRegistry, Set.empty),
        StackRegistryDuplicatesRule(ServerRegistry, Set.empty),
        NullStatsReceiversRule(ClientRegistry),
        NullStatsReceiversRule(ServerRegistry),
        MemcacheFailFastRule(ClientRegistry),
        LoggingRules.MultipleSlf4jImpls,
        referenceLeakLintRule.rule(),
        DuplicateFlagDefinitions(app)
      )
  }

  /** Exposed for testing */
  private[server] def registerLinters(): Unit = {
    linterRules.foreach(GlobalRules.get.add)
  }

}
