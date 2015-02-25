package com.twitter.server.handler

import com.twitter.util.NoStacktrace
import com.twitter.util.registry.SimpleRegistry
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RegistryHandlerTest extends FunSuite {

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

  // TODO: use `withRegistry` when it gets merged in
  test("RegistryHandlerTest generates reasonable json") {
    val naive = new SimpleRegistry
    val handler = new RegistryHandler(naive)
    naive.put(Seq("foo", "bar"), "baz")
    naive.put(Seq("foo", "qux"), "quux")

    val actual = stripWhitespace(handler.jsonResponse())
    val expected = """{"registry":{"foo":{"bar":"baz","qux":"quux"}}}"""
    assert(actual == expected)
  }

  test("add should handle empties") {
    assert(
      RegistryHandler.add(Map.empty, Seq.empty, "big") == Map(RegistryHandler.Eponymous -> "big")
    )
  }

  test("add should handle putting an entry in an existing map if nothing's there") {
    assert(
      RegistryHandler.add(Map.empty, Seq("it's"), "big") == Map("it's" -> "big")
    )
  }

  test("add should handle putting recursive entries in an existing map if nothing's there") {
    val actual = RegistryHandler.add(Map.empty, Seq("it's", "very"), "big")
    val expected = Map("it's" -> Map("very" -> "big"))
    assert(actual == expected)
  }

  test("add should handle colliding prefixes") {
    val actual = RegistryHandler.add(Map("it's" -> Map("not" -> "small")), Seq("it's", "very"), "big")
    val expected = Map("it's" -> Map("very" -> "big", "not" -> "small"))
    assert(actual == expected)
  }

  test("add should handle colliding prefixes that are shorter") {
    val actual = RegistryHandler.add(Map("it's" -> "small"), Seq("it's", "very"), "big")
    val expected = Map("it's" -> Map("very" -> "big", RegistryHandler.Eponymous -> "small"))
    assert(actual == expected)
  }

  test("add should bail on collisions") {
    val actual = intercept[Exception with NoStacktrace] {
      RegistryHandler.add(Map("it's" -> "small"), Seq("it's"), "big")
    }
    val expected = RegistryHandler.Collision
    assert(actual == expected)
  }

  test("add should bail on finding a weird type") {
    val actual = intercept[Exception with NoStacktrace] {
      RegistryHandler.add(Map("it's" -> new Object), Seq("it's"), "big")
    }
    val expected = RegistryHandler.InvalidType
    assert(actual == expected)
  }

  test("makeMap should make a map") {
    val seq = Seq("my", "spoon", "is", "too")
    val value = "big"
    val actual = RegistryHandler.makeMap(seq, value)

    val expected = Map("my" -> Map("spoon" -> Map("is" -> Map("too" -> "big"))))
    assert(actual == expected)
  }

  test("makeMap should fail on empties") {
    val seq = Seq.empty
    val value = "big"
    val actual = intercept[Exception with NoStacktrace] {
      RegistryHandler.makeMap(seq, value)
    }
    assert(actual == RegistryHandler.Empty)
  }
}
