package com.twitter.server.handler

import com.twitter.util.registry.{GlobalRegistry, SimpleRegistry}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RegistryHandlerTest extends FunSuite {

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

  private[this] def isRegistered(key: String): Boolean = {
    GlobalRegistry.get.exists(_.key.headOption.exists(_ == key))
  }

  test("RegistryHandler generates reasonable json") {
    val simple = new SimpleRegistry
    val handler = new RegistryHandler
    simple.put(Seq("foo", "bar"), "baz")
    simple.put(Seq("foo", "qux"), "quux")

    GlobalRegistry.withRegistry(simple) {
      val actual = stripWhitespace(handler.jsonResponse())
      val expected = """{"registry":{"foo":{"bar":"baz","qux":"quux"}}}"""
      assert(actual == expected)
    }
  }

  test("RegistryHandler adds env variables to GlobalRegistry") {
    GlobalRegistry.withRegistry(new SimpleRegistry) {
      assert(!isRegistered("env"))
      new RegistryHandler
      assert(isRegistered("env"))
    }
  }

  test("RegistryHandler adds system properties to GlobalRegistry") {
    GlobalRegistry.withRegistry(new SimpleRegistry) {
      assert(!isRegistered("system.properties"))
      new RegistryHandler
      assert(isRegistered("system.properties"))
    }
  }
}
