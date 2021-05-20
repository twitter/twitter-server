package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Status}
import com.twitter.util.registry.{GlobalRegistry, SimpleRegistry}
import com.twitter.util.{Await, Awaitable}
import org.scalatest.funsuite.AnyFunSuite

class ServerInfoHandlerTest extends AnyFunSuite {

  private[this] def await[T](a: Awaitable[T]): T = Await.result(a, 2.seconds)

  private[this] def isRegistered(key: Seq[String]): Boolean =
    GlobalRegistry.get.exists(_.key.startsWith(key))

  test("ServerInfo handler display server information") {
    val handler = new ServerInfoHandler()
    val req = Request("/")
    val res = await(handler(req))

    assert(res.status == Status.Ok)
    val info = res.contentString
    assert(info.contains("\"build\" : \"unknown\""))
    assert(info.contains("\"build_revision\" : \"unknown\""))
    assert(info.contains("\"name\" : \"twitter-server\""))
    assert(info.contains("\"version\" : \"0.0.0\""))
    assert(info.contains("\"start_time\" :"))
    assert(info.contains("\"uptime\" :"))
    // user-defined properties
    assert(info.contains("\"foo\" : \"bar\""))
  }

  test("ServerInfo handler returns the right content-type") {
    val handler = new ServerInfoHandler()
    val req = Request("/")
    val res = await(handler(req))
    assert(res.contentType.contains("application/json;charset=UTF-8"))
  }

  for (key <- Seq(Seq("build.properties"), Seq("system", "properties"), Seq("system", "env")))
    yield {
      test(s"ServerInfo handler adds ${key.mkString(" ")} to Global Registry on instantiation") {
        GlobalRegistry.withRegistry(new SimpleRegistry) {
          assert(!isRegistered(key))
          new ServerInfoHandler()
          assert(isRegistered(key))
        }
      }
    }
}
