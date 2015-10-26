package com.twitter.server.util

import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Buf, Reader}
import com.twitter.util.events.{Event, Sink}
import java.util.logging.LogRecord

/**
 * A utility to serialize the [[com.twitter.util.events.Sink]] in a format
 * readable by [[http://goo.gl/iN9ozV Trace Viewer]].
 *
 * Add the following to the `onExit` hook to export the Sink to a file when the
 * server exits, or place it behind an endpoint to be triggered by request.
 *
 * {{{
 * val trace = Writer.fromOutputStream(new FileOutputStream("sink.trace.json"))
 * val done = Reader.copy(TraceEventSink.serialize(Sink.default), trace) ensure trace.close()
 * Await.result(done, 3.seconds)
 * }}}
 */
object TraceEventSink {
  private val comma = Buf.Utf8(",")
  private val nl = Buf.Utf8("\n")
  private val leftBracket = Buf.Utf8("[")
  private val sp = Buf.Utf8(" ")

  private def showObject(o: Object): String = o match {
    case r: LogRecord => s"${r.getLevel.toString} ${r.getMessage}"
    case _ => o.toString
  }

  private def asTraceEvent(e: Event): Buf = Buf.Utf8(
    Json.serialize(Map(
      "name" -> e.etype.id,
      "cat" -> "",
      "ph" -> "i",
      "ts" -> (e.when.inMillis * 1000).toString,
      "pid" -> e.getTraceId.getOrElse(0),
      "tid" -> e.getSpanId.getOrElse(0),
      "args" -> Map(
        Seq(
          "longVal" -> e.getLong,
          "objectVal" -> e.getObject.map(showObject),
          "doubleVal" -> e.getDouble
        ).filterNot(_._2.isEmpty):_*
      )
    ))
  )

  /**
   * Serialize a sink into the [[http://goo.gl/iN9ozV Trace Event]] format.
   */
  def serialize(sink: Sink): Reader = {
    val delim = nl.concat(comma).concat(sp)
    val events: Seq[Buf] = sink.events.toSeq.map(asTraceEvent)

    // Note: we leave out the "]" from the JSON array since it's optional. See:
    // http://goo.gl/iN9ozV#heading=h.f2f0yd51wi15.
    if (events.isEmpty) Reader.fromBuf(leftBracket) else Reader.concat(
      Reader.fromBuf(leftBracket.concat(events.head)) +::
        AsyncStream.fromSeq(events.tail.map { buf =>
          Reader.fromBuf(delim.concat(buf))
        })
    )
  }
}
