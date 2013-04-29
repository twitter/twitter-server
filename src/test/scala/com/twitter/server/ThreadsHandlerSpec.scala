package com.twitter.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.tracing.{Trace, Tracer}
import com.twitter.util.Await
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.specs.SpecificationWithJUnit
import scala.collection.JavaConversions._

class ThreadsHandlerSpec extends SpecificationWithJUnit {
  type Threads = Map[String, JMapWrapper[String, JMapWrapper[String, Any]]]

  "Threads handler" should {
    "display thread info" in {
      val reader = new ObjectMapper
      reader.registerModule(DefaultScalaModule)

      val handler = new ThreadsHandler
      val req = Request("/")
      val res = Response(Await.result(handler(req)))

      val threads = reader.readValue(res.contentString, classOf[Threads])
      val stacks = threads("threads")
      val (_, stack) = stacks.head

      stack must haveKey("thread")
      stack must haveKey("daemon")
      stack must haveKey("state")
      stack must haveKey("priority")
      stack must haveKey("stack")
    }
  }
}
