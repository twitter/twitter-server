package com.twitter.server.lint

import com.twitter.finagle.Stack
import com.twitter.finagle.client.StackClient
import com.twitter.finagle.param.ProtocolLibrary
import com.twitter.finagle.service.FailFastFactory
import com.twitter.finagle.util.StackRegistry
import org.scalatest.funsuite.AnyFunSuite

class MemcacheFailFastRuleTest extends AnyFunSuite {

  def newRegistry(): StackRegistry =
    new StackRegistry { def registryName: String = "client" }

  test("Empty registry doesn't create issues") {
    val registry = newRegistry()

    val rule = MemcacheFailFastRule(registry)
    assert(rule().size == 0)
  }

  test("Non-Memcache client doesn't create issues") {
    val registry = newRegistry()
    registry.register("localhost:11212", StackClient.newStack, Stack.Params.empty)

    val rule = MemcacheFailFastRule(registry)
    assert(rule().size == 0)
  }

  test("Memcache client with FailFast disabled doesn't create issues") {
    val registry = newRegistry()
    val params = Stack.Params.empty +
      ProtocolLibrary("memcached") +
      FailFastFactory.FailFast(false)
    registry.register("localhost:11211", StackClient.newStack, params)

    val rule = MemcacheFailFastRule(registry)
    assert(rule().size == 0)
  }

  test("Memcache client with FailFast enabled does create issues") {
    val registry = newRegistry()
    val params = Stack.Params.empty +
      ProtocolLibrary("memcached") +
      FailFastFactory.FailFast(true)
    registry.register("localhost:11211", StackClient.newStack, params)

    val rule = MemcacheFailFastRule(registry)
    assert(rule().size == 1)
  }

}
