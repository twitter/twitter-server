package com.twitter.server.lint

import com.twitter.finagle.stats.InMemoryStatsReceiver
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class NumberOfStatsReceiversRuleTest extends FunSuite {

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
