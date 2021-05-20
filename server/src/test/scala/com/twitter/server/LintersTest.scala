package com.twitter.server

import org.scalatest.funsuite.AnyFunSuite

class LintersTest extends AnyFunSuite {

  val server = new TestTwitterServer
  val ruleIdsSet = server.linterRules.map(_.id).toSet

  test("Linter has number of StatsReceivers rule") {
    assert(ruleIdsSet.contains("number-of-statsreceivers"))
  }

  test("Linter has duplicate client StackRegistry names rule") {
    assert(ruleIdsSet.contains("duplicate-client-stackregistry-names"))
  }

  test("Linter has duplicate server StackRegistry names rule") {
    assert(ruleIdsSet.contains("duplicate-server-stackregistry-names"))
  }

  test("Linter has NullStatsReceiver client rule") {
    assert(ruleIdsSet.contains("finagle-client-without-metrics"))
  }

  test("Linter has NullStatsReceiver server rule") {
    assert(ruleIdsSet.contains("finagle-server-without-metrics"))
  }

  test("Linter has Memcache fail fast rule") {
    assert(ruleIdsSet.contains("memcache-client-has-failfast-enabled"))
  }

  test("Linter has multiple slf4j implementations rule") {
    assert(ruleIdsSet.contains("multiple-slf4j-implementations"))
  }

}
