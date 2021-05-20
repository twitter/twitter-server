package com.twitter.server.lint

import com.twitter.finagle.stats.InMemoryStatsReceiver
import org.scalatest.funsuite.AnyFunSuite

class NumberOfStatsReceiversRuleTest extends AnyFunSuite {

  test("One stats receiver doesn't create issues") {
    val statsReceivers = Seq(new InMemoryStatsReceiver)

    val rule = NumberOfStatsReceiversRule(statsReceivers)
    assert(rule().size == 0)
  }

  test("Zero stats receivers does create issues") {
    val statsReceivers = Seq()

    val rule = NumberOfStatsReceiversRule(statsReceivers)
    assert(rule().size == 1)
  }

  test("Multiple stats receivers does create issues") {
    val statsReceivers = Seq(new InMemoryStatsReceiver, new InMemoryStatsReceiver)

    val rule = NumberOfStatsReceiversRule(statsReceivers)
    assert(rule().size == 1)
  }

}
