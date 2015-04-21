package com.twitter.server.util

import com.twitter.io.{Reader, Buf}
import com.twitter.util.Await
import com.twitter.util.events.{Event, Sink}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class TraceEventSinkTest extends FunSuite {
  test("empty sink") {
    val empty = Sink.of(mutable.ListBuffer.empty)
    val reader = TraceEventSink.serialize(empty)
    val Buf.Utf8(json) = Await.result(Reader.readAll(reader))
    assert(json == "[")
  }

  test("base") {
    val sink = Sink.of(mutable.ListBuffer.empty)
    sink.event(Event.nullType, objectVal = "hello")

    val reader = TraceEventSink.serialize(sink)
    val Buf.Utf8(json) = Await.result(Reader.readAll(reader)).concat(Buf.Utf8("]"))

    val doc = Json.deserialize[Seq[Map[String, Any]]](json)
    val args = doc.head("args").asInstanceOf[Map[String, Any]]

    assert(doc.head("name") == Event.nullType.id)
    assert(args("objectVal") == "hello")
  }
}
