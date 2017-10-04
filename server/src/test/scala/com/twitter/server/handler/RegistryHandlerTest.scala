package com.twitter.server.handler

import com.twitter.util.registry.{GlobalRegistry, SimpleRegistry}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RegistryHandlerTest extends FunSuite {

  private[this] val handler = new RegistryHandler()

  /** used for testing filtering */
  private[this] val filterRegistry = new SimpleRegistry()
  filterRegistry.put(Seq("foo", "bar"), "baz")
  filterRegistry.put(Seq("foo", "qux"), "quux")
  filterRegistry.put(Seq("oof"), "gah")
  filterRegistry.put(Seq("uno", "two"), "tres")
  filterRegistry.put(Seq("one", "two"), "three")
  filterRegistry.put(Seq("1", "a", "3"), "4")
  filterRegistry.put(Seq("1", "b", "3"), "5")

  // NOTE: these tests assume a specific iteration order over the registries
  // and HashMaps which IS NOT a guarantee. should these tests begin to fail
  // due to that, we will need a more robust approach to validation.
  private[this] def assertJsonResponse(filter: Option[String], expected: String) = {
    val actual = stripWhitespace(handler.jsonResponse(filter))
    assert(actual == expected)
  }

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

  test("RegistryHandler generates reasonable json") {
    val simple = new SimpleRegistry
    simple.put(Seq("foo", "bar"), "baz")
    simple.put(Seq("foo", "qux"), "quux")

    GlobalRegistry.withRegistry(simple) {
      assertJsonResponse(None, """{"registry":{"foo":{"bar":"baz","qux":"quux"}}}""")
    }
  }

  test("RegistryHandler.jsonResponse filters with basic matches") {
    GlobalRegistry.withRegistry(filterRegistry) {
      assertJsonResponse(Some("oof"), """{"registry":{"oof":"gah"}}""")
      assertJsonResponse(Some("foo"), """{"registry":{"foo":{"bar":"baz","qux":"quux"}}}""")
      assertJsonResponse(Some("foo/bar"), """{"registry":{"foo":{"bar":"baz"}}}""")
    }
  }

  test("RegistryHandler.jsonResponse filters with globs") {
    GlobalRegistry.withRegistry(filterRegistry) {
      assertJsonResponse(
        Some("*/two"),
        """{"registry":{"uno":{"two":"tres"},"one":{"two":"three"}}}"""
      )
      assertJsonResponse(Some("1/*/3"), """{"registry":{"1":{"b":{"3":"5"},"a":{"3":"4"}}}}""")
    }
  }

  test("RegistryHandler.jsonResponse filters strips off leading registry key") {
    GlobalRegistry.withRegistry(filterRegistry) {
      assertJsonResponse(Some("registry/oof"), """{"registry":{"oof":"gah"}}""")
    }
  }

  test("RegistryHandler.jsonResponse filters when no keys match the filter") {
    GlobalRegistry.withRegistry(filterRegistry) {
      assertJsonResponse(Some("nope"), """{"registry":{}}""")
      assertJsonResponse(Some(""), """{"registry":{}}""")
    }
  }

}
