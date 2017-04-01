package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.http.{MediaType, Method, Request, Status}
import com.twitter.util.Await
import com.twitter.util.tunable.TunableMap
import org.scalatest.FunSuite

class TunableHandlerTest extends FunSuite {

  test("PUT: Non-PUT request returns 'MethodNotAllowed response") {
    val handler = new TunableHandler
    val resp = handler(Request(Method.Post, "/"))
    assert(Await.result(resp, 1.second).status == Status.MethodNotAllowed)
  }

  test("PUT: Returns response with error if Media-Type is not JSON") {
    val handler = new TunableHandler
    val req = Request(Method.Put, "/")
    req.contentType = MediaType.Csv
    val resp = handler(req)
    assert(Await.result(resp, 1.second).contentString.contains("Expected Content-Type"))
  }

  test("PUT: Returns response with error if JSON cannot be parsed") {
    val handler = new TunableHandler
    val req = Request(Method.Put, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString = "i am not valid json..."
    val resp = handler(req)
    assert(Await.result(resp, 1.second).contentString.contains("Failed to parse JSON"))
  }

  test("PUT: Returns response with Status.NotFound if id in path is not registered") {
    val handler = new TunableHandler
    val req = Request(Method.Put, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString = """{"tunables": [] }"""
    val resp = Await.result(handler(req), 1.second)
    assert(resp.contentString.contains(
      "Mutable TunableMap not found for id: foo"))
    assert(resp.status == Status.NotFound)
  }

  test("PUT: Updates tunable map with new tunables") {
    val map = TunableMap.newMutable()
    val registry = Map[String, TunableMap]("foo" -> map)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Put, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString =
      """{"tunables":
        |  [
        |     {
        |         "id": "test_id",
        |         "type": "java.lang.String",
        |         "value": "hello"
        |     }
        |  ]
        |}""".stripMargin
    val resp = handler(req)
    assert(map(TunableMap.Key[String]("test_id"))() == Some("hello"))
    assert(Await.result(resp, 1.second).status == Status.Ok)
  }

  test("PUT: Updates existing tunable with new value") {
    val map = TunableMap.newMutable()
    val key = map.put("test_id", "hello")
    val registry = Map[String, TunableMap]("foo" -> map)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Put, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString =
      """{"tunables":
        |  [
        |     {
        |         "id": "test_id",
        |         "type": "java.lang.String",
        |         "value": "goodbye"
        |     }
        |  ]
        |}""".stripMargin
    assert(map(key)() == Some("hello"))
    val resp = handler(req)
    assert(map(key)() == Some("goodbye"))
    assert(Await.result(resp, 1.second).status == Status.Ok)
  }

  test("PUT: Does not remove existing tunables") {
    val map = TunableMap.newMutable()
    val key1 = map.put("test_id1", "hello")
    val key2 = map.put("test_id2", "i'd better stick around")
    val registry = Map[String, TunableMap]("foo" -> map)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Put, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString =
      """{"tunables":
        |  [
        |     {
        |         "id": "test_id1",
        |         "type": "java.lang.String",
        |         "value": "goodbye"
        |     }
        |  ]
        |}""".stripMargin
    assert(map(key1)() == Some("hello"))
    assert(map(key2)() == Some("i'd better stick around"))
    val resp = handler(req)
    assert(map(key1)() == Some("goodbye"))
    assert(map(key2)() == Some("i'd better stick around"))
    assert(Await.result(resp, 1.second).status == Status.Ok)
  }
}
