package com.twitter.server.lint

import com.twitter.finagle.Stack
import com.twitter.finagle.client.StackClient
import com.twitter.finagle.param.{Label, Stats}
import com.twitter.finagle.stats.{InMemoryStatsReceiver, NullStatsReceiver}
import com.twitter.finagle.util.StackRegistry
import org.scalatest.funsuite.AnyFunSuite

class NullStatsReceiversRuleTest extends AnyFunSuite {

  def newRegistry(name: String): StackRegistry =
    new StackRegistry { def registryName: String = name }

  test("Empty registry doesn't create issues") {
    val registry = newRegistry("client")

    val rule = NullStatsReceiversRule(registry)
    assert(rule().size == 0)
  }

  test("Client with StatsReceiver doesn't create issues") {
    val registry = newRegistry("client")
    val params = Stack.Params.empty + Stats(new InMemoryStatsReceiver)
    registry.register("localhost:1234", StackClient.newStack, params)

    val rule = NullStatsReceiversRule(registry)
    assert(rule().size == 0)
  }

  test("Client with NullStatsReceiver does create issues") {
    val registry = newRegistry("client")
    val params = Stack.Params.empty + Stats(NullStatsReceiver)
    registry.register("localhost:1234", StackClient.newStack, params)

    val rule = NullStatsReceiversRule(registry)
    assert(rule().size == 1)
  }

  test("Server with StatsReceiver doesn't create issues") {
    val registry = newRegistry("server")
    val params = Stack.Params.empty + Stats(new InMemoryStatsReceiver)
    registry.register("localhost:8080", StackClient.newStack, params)

    val rule = NullStatsReceiversRule(registry)
    assert(rule().size == 0)
  }

  test("Server with NullStatsReceiver does create issues") {
    val registry = newRegistry("server")
    val params = Stack.Params.empty + Stats(NullStatsReceiver)
    registry.register("localhost:8080", StackClient.newStack, params)

    val rule = NullStatsReceiversRule(registry)
    assert(rule().size == 1)
  }

  test("Admin Server doesn't create issues") {
    val registry = newRegistry("server")
    val params = Stack.Params.empty + Stats(NullStatsReceiver) + Label("adminhttp")
    registry.register("localhost:8080", StackClient.newStack, params)

    val rule = NullStatsReceiversRule(registry)
    assert(rule().size == 0)
  }

}
