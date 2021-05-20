package com.twitter.server.lint

import com.twitter.finagle.Stack
import com.twitter.finagle.client.StackClient
import com.twitter.finagle.param.{Label, ProtocolLibrary}
import com.twitter.finagle.util.StackRegistry
import org.scalatest.funsuite.AnyFunSuite

class StackRegistryDuplicatesRuleTest extends AnyFunSuite {

  def newRegistry(): StackRegistry =
    new StackRegistry { def registryName: String = "client" }

  test("Empty registry doesn't create issues") {
    val registry = newRegistry()

    val rule = StackRegistryDuplicatesRule(registry, Set())
    assert(rule().size == 0)
  }

  test("Single client doesn't create issues") {
    val registry = newRegistry()
    registry.register("localhost:11212", StackClient.newStack, Stack.Params.empty)

    val rule = StackRegistryDuplicatesRule(registry, Set())
    assert(rule().size == 0)
  }

  test("Multiple same name clients does create issues") {
    val registry = newRegistry()
    val params = Stack.Params.empty + Label("thrift-test-client")
    registry.register("localhost:1234", StackClient.newStack, params)
    registry.register("localhost:2345", StackClient.newStack, params)

    val rule = StackRegistryDuplicatesRule(registry, Set())
    assert(rule().size == 1)
  }

  test("Multiple same name non-allowlisted clients does create issues") {
    val registry = newRegistry()
    val params = Stack.Params.empty + Label("thrift-test-client")
    registry.register("localhost:1234", StackClient.newStack, params)
    registry.register("localhost:2345", StackClient.newStack, params)

    val rule = StackRegistryDuplicatesRule(registry, Set("special", "very-special"))
    assert(rule().size == 1)
  }

  test("Multiple same name allowlisted clients does not create issues") {
    val registry = newRegistry()
    val params = Stack.Params.empty + Label("thrift-test-client")
    registry.register("localhost:1234", StackClient.newStack, params)
    registry.register("localhost:2345", StackClient.newStack, params)

    val rule = StackRegistryDuplicatesRule(registry, Set("special", "thrift-test-client"))
    assert(rule().size == 0)
  }
  test("Multiple same name memcache clients doesn't create issues") {
    val registry = newRegistry()
    val params = Stack.Params.empty + Label("memcache-test-client") + ProtocolLibrary("memcached")
    registry.register("localhost:11211", StackClient.newStack, params)
    registry.register("localhost:11212", StackClient.newStack, params)

    val rule = StackRegistryDuplicatesRule(registry, Set())
    assert(rule().size == 0)
  }

}
