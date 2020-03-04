package com.twitter.server.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{MediaType, Method, Request, Status}
import com.twitter.util.Await
import com.twitter.util.tunable.TunableMap
import org.scalatest.FunSuite

object TunableHandlerTest {
  // Needs to be in companion object because of Jackson. We use `Set` to avoid ordering issues here.
  case class Response(id: String, tunables: Set[Tunable])
  case class Tunable(id: String, value: String, components: Set[Component])
  case class Component(source: String, value: String)
}

class TunableHandlerTest extends FunSuite {
  import TunableHandlerTest._

  private[this] val mapper = new ObjectMapper with ScalaObjectMapper {
    registerModule(DefaultScalaModule)
  }

  test("Non-{DELETE, GET, PUT} request returns 'MethodNotAllowed response") {
    val handler = new TunableHandler
    val resp = handler(Request(Method.Post, "/"))
    assert(Await.result(resp, 1.second).status == Status.MethodNotAllowed)
  }

  def testJsonErrorHandling(method: Method) = {
    test(method + ": Returns response with error if Media-Type is not JSON") {
      val handler = new TunableHandler
      val req = Request(method, "/")
      req.contentType = MediaType.Csv
      val resp = handler(req)
      assert(Await.result(resp, 1.second).contentString.contains("Expected Content-Type"))
    }

    test(method + ": Returns response with error if JSON cannot be parsed") {
      val handler = new TunableHandler
      val req = Request(method, "/admin/tunables/foo")
      req.contentType = MediaType.Json
      req.contentString = "i am not valid json..."
      val resp = handler(req)
      assert(Await.result(resp, 1.second).contentString.contains("Failed to parse JSON"))
    }
  }

  def testIdErrorHandling(method: Method) = {
    test(method + ": Returns response with Status.NotFound if id in path is not registered") {
      val handler = new TunableHandler
      val req = Request(method, "/admin/tunables/foo")
      req.contentType = MediaType.Json
      req.contentString = """{"tunables": [] }"""
      val resp = Await.result(handler(req), 1.second)
      assert(resp.contentString.contains("TunableMap not found for id: foo"))
      assert(resp.status == Status.NotFound)
    }
  }

  /**
   * Relying on the ordering of HashMaps is a bad idea and is different between 2.13 and earlier versions.
   *
   * This helper will try to deserialize both strings to the given type `T` before comparison, which
   * avoids the ordering issue.
   */
  private[this] def assertJsonResponseFor[T: Manifest](actual: String, expected: String) = {
    val expectedObj = mapper.readValue[T](expected)
    val actualObj = mapper.readValue[T](actual)

    assert(actualObj == expectedObj)
  }

  testIdErrorHandling(Method.Get)

  testJsonErrorHandling(Method.Put)
  testIdErrorHandling(Method.Put)

  testJsonErrorHandling(Method.Delete)
  testIdErrorHandling(Method.Delete)

  test("GET: returns tunables for id") {
    val map1 = TunableMap.newMutable("map1")
    map1.put("key1", 5.seconds)
    val map2 = TunableMap.newMutable("map2")
    map2.put("key1", 3.seconds)
    map2.put("key2", "value2")
    val composed = TunableMap.of(map1, map2)

    val registry = Map[String, TunableMap]("foo" -> composed)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Get, "/admin/tunables/foo")

    val resp = Await.result(handler(req), 1.second)
    assert(resp.status == Status.Ok)
    val expected =
      """{
        |  "id" : "foo",
        |  "tunables" : [
        |    {
        |      "id" : "key2",
        |      "value" : "value2",
        |      "components" : [
        |        {
        |          "source" : "map2",
        |          "value" : "value2"
        |        }
        |      ]
        |    },
        |    {
        |      "id" : "key1",
        |      "value" : "5.seconds",
        |      "components" : [
        |        {
        |          "source" : "map1",
        |          "value" : "5.seconds"
        |        },
        |        {
        |          "source" : "map2",
        |          "value" : "3.seconds"
        |        }
        |      ]
        |    }
        |  ]
        |}""".stripMargin
    assertJsonResponseFor[Response](resp.contentString, expected)
  }

  test("GET: returns tunables for all ids") {
    val map1 = TunableMap.newMutable("map1")
    map1.put("key1", 5.seconds)
    val map2 = TunableMap.newMutable("map2")
    map2.put("key2", "value2")

    val registry = Map[String, TunableMap]("foo" -> map1, "bar" -> map2)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Get, "/admin/tunables")

    val resp = Await.result(handler(req), 1.second)
    assert(resp.status == Status.Ok)
    val expected =
      """[
        |  {
        |    "id" : "bar",
        |    "tunables" : [
        |      {
        |        "id" : "key2",
        |        "value" : "value2",
        |        "components" : [
        |          {
        |            "source" : "map2",
        |            "value" : "value2"
        |          }
        |        ]
        |      }
        |    ]
        |  },
        |  {
        |    "id" : "foo",
        |    "tunables" : [
        |      {
        |        "id" : "key1",
        |        "value" : "5.seconds",
        |        "components" : [
        |          {
        |            "source" : "map1",
        |            "value" : "5.seconds"
        |          }
        |        ]
        |      }
        |    ]
        |  }
        |]""".stripMargin
    assert(resp.contentString == expected)
  }

  test("PUT: Updates tunable map with new tunables") {
    val map = TunableMap.newMutable()
    val registry = Map[String, TunableMap]("foo" -> map)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Put, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString = """{"tunables":
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
    req.contentString = """{"tunables":
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
    req.contentString = """{"tunables":
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

  test("DELETE: Removes specified existing tunables") {
    val map = TunableMap.newMutable()
    val key = map.put("test_id", "hello")
    val registry = Map[String, TunableMap]("foo" -> map)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Delete, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString = """{"tunables":
        |  [
        |     {
        |         "id": "test_id",
        |         "type": "java.lang.String",
        |         "value": "remove me"
        |     }
        |  ]
        |}""".stripMargin
    val resp = handler(req)
    assert(map(TunableMap.Key[String]("test_id"))() == None)
    assert(Await.result(resp, 1.second).status == Status.Ok)
  }

  test("PUT: Does not remove existing tunables not present in update JSON") {
    val map = TunableMap.newMutable()
    val key1 = map.put("test_id1", "hello")
    val key2 = map.put("test_id2", "i'd better stick around")
    val registry = Map[String, TunableMap]("foo" -> map)
    val handler = new TunableHandler(() => registry)

    val req = Request(Method.Delete, "/admin/tunables/foo")
    req.contentType = MediaType.Json
    req.contentString = """{"tunables":
        |  [
        |     {
        |         "id": "test_id1",
        |         "type": "java.lang.String",
        |         "value": "remove me"
        |     }
        |  ]
        |}""".stripMargin
    assert(map(key1)() == Some("hello"))
    assert(map(key2)() == Some("i'd better stick around"))
    val resp = handler(req)
    assert(map(key1)() == None)
    assert(map(key2)() == Some("i'd better stick around"))
    assert(Await.result(resp, 1.second).status == Status.Ok)
  }
}
