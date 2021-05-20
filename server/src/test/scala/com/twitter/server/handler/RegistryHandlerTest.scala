package com.twitter.server.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import com.twitter.util.registry.{GlobalRegistry, SimpleRegistry}
import org.scalatest.funsuite.AnyFunSuite

class RegistryHandlerTest extends AnyFunSuite {

  private[this] val mapper = new ObjectMapper with ScalaObjectMapper {
    registerModule(DefaultScalaModule)
  }

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

  // Some of these tests assume a specific iteration order over the registries
  // and HashMaps which IS NOT a guarantee. should these tests begin to fail
  // due to that, we will need to use `assertJsonResponseFor` for them as well.
  private[this] def assertJsonResponse(filter: Option[String], expected: String) = {
    val actual = stripWhitespace(handler.jsonResponse(filter))
    assert(actual == expected)
  }

  private[this] def stripWhitespace(string: String): String =
    string.filterNot(_.isWhitespace)

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
      type Response = Map[String, Object]

      JsonHelper.assertJsonResponseFor[Response](
        mapper,
        stripWhitespace(handler.jsonResponse(Some("oof"))),
        """{"registry":{"oof":"gah"}}""")
      JsonHelper.assertJsonResponseFor[Response](
        mapper,
        stripWhitespace(handler.jsonResponse(Some("foo"))),
        """{"registry":{"foo":{"bar":"baz","qux":"quux"}}}""")
      JsonHelper.assertJsonResponseFor[Response](
        mapper,
        stripWhitespace(handler.jsonResponse(Some("foo/bar"))),
        """{"registry":{"foo":{"bar":"baz"}}}""")
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
