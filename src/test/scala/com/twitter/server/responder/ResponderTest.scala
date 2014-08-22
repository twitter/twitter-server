package com.twitter.server.responder

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ResponderTest extends FunSuite {
  test("Params mapped correctly") {
    val params = Map(
      "statsReceiver" -> "com.twitter.finagle.stats.StatsReceiver$$anon$1@11742dfe",
      "va" -> "Value(Bound(Set(localhost/127.0.0.1:8895)))",
      "addr" -> "localhost/127.0.0.1:8895",
      "idleTime" -> "Duration.Top",
      "label" -> "serverName")
    val mappedParams = ResponderUtils.mapParams(params)
    assert(mappedParams.length == params.size)
    val expectedMap = Map(
      "statsReceiver" -> "StatsReceiver",
      "va" -> "Bound(Set(localhost/127.0.0.1:8895))",
      "addr" -> "localhost/127.0.0.1:8895",
      "idleTime" -> "Duration.Top",
      "label" -> "serverName")
    mappedParams foreach { mapping =>
      (mapping.get("key"), mapping.get("value")) match {
        case (Some(key), Some(value)) =>
          assert(expectedMap.contains(key))
          expectedMap.get(key) map { v =>
            assert(v == value)
          }
        case _ => fail("Map doesn't contain keys 'key' and/or 'value")
      }
    }
  }

  test("Extract existent query value") {
    val extracted = ResponderUtils.extractQueryValue(
      "foo", "http://test.com/testing?foo=bar&baz=qux&hello=world")
    assert(extracted == "bar")
  }

  test("Extract nonexistent query value") {
    val extracted = ResponderUtils.extractQueryValue(
      "fun", "http://test.com/testing?foo=bar&baz=qux&hello=world")
    assert(extracted == "")
  }

  test("Extract existent query value that appears multiple times") {
    val extracted = ResponderUtils.extractQueryValue(
      "baz", "http://test.com/testing?foo=bar&baz=qux&baz=world")
    assert(extracted == "qux")
  }

  test("Extract existent query values") {
    val extracted = ResponderUtils.extractQueryValues(
      "foo", "http://test.com/testing?foo=bar&baz=qux&foo=world")
    assert(extracted == List("bar", "world"))
  }

  test("Extract nonexistent query values") {
    val extracted = ResponderUtils.extractQueryValues(
      "fun", "http://test.com/testing?foo=bar&baz=qux&hello=world")
    assert(extracted == List.empty)
  }
}


