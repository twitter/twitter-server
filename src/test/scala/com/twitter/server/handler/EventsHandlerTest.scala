package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.events.{Sink, Event}
import com.twitter.util.{Await, FuturePool, Promise, Time}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EventsHandlerTest extends FunSuite {

  test("streaming html") {
    val p = new Promise[Stream[Event]]
    val e = Event(Event.nullType, Time.now)

    val stream = e #:: Await.result(p)

    val sink = new Sink {
      def event(e: Event.Type, l: Long, o: Object, d: Double, t: Long, s: Long) = ()
      def events = stream.toIterator
    }

    val controller = new EventsHandler(sink)
    val req = Request()
    // Necessary for controller to determine that this is a request for HTML.
    req.headerMap.add("User-Agent", "Mozilla")

    val res = Await.result(controller(req)).asInstanceOf[Response]
    val preamble = res.reader.read(Int.MaxValue)
    assert(preamble.isDefined)

    // We have to run this in a pool or Reader.read ends up blocking, because
    // we call Await.result in the sink events iterator. This is ok, this
    // Future is still useful for the assertion that `sink` and Response reader
    // are connected.
    val content = FuturePool.unboundedPool { res.reader.read(Int.MaxValue) }
    assert(!content.isDefined)

    // Doesn't matter that this is an exception, it's just used as a control
    // signal for resumption of `stream`'s tail.
    p.setException(new Exception)

    assert(Await.result(content).isDefined)
    res.reader.discard()
  }
}
