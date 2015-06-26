package com.twitter.server.handler

import com.twitter.util.registry.{SimpleRegistry, GlobalRegistry}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RegistryHandlerTest extends FunSuite {

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

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
}
