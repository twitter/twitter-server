package com.twitter.server.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.tracing.{Trace, Tracer}
import com.twitter.util.Await
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import scala.collection.JavaConversions._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ThreadsHandlerSpec extends FunSuite {
  type Threads = Map[String, Map[String, Map[String, Any]]]

  test("Threads handler display thread info") {
    val reader = new ObjectMapper
    reader.registerModule(DefaultScalaModule)

    val handler = new ThreadsHandler
    val req = Request("/")
    val res = Response(Await.result(handler(req)))

    val threads = reader.readValue(res.contentString, classOf[Threads])
    val stacks = threads("threads")
    val (_, stack) = stacks.head

    assert(stack.get("thread").isDefined)
    assert(stack.get("daemon").isDefined)
    assert(stack.get("state").isDefined)
    assert(stack.get("priority").isDefined)
    assert(stack.get("stack").isDefined)
  }
}
