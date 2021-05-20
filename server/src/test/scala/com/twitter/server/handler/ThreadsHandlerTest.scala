package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Awaitable}
import org.scalatest.funsuite.AnyFunSuite

class ThreadsHandlerTest extends AnyFunSuite {
  type Threads = Map[String, Map[String, Map[String, Any]]]

  private[this] def await[T](a: Awaitable[T]): T = Await.result(a, 2.seconds)

  test("Threads handler display thread info") {
    val reader = new ObjectMapper().registerModule(DefaultScalaModule)

    val handler = new ThreadsHandler
    val req = Request("/")
    val res = await(handler(req))

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
