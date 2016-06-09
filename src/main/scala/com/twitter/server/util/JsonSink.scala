package com.twitter.server.util

import com.twitter.finagle.zipkin.core.SamplingTracer
import com.twitter.server.EventSink
import com.twitter.io.Buf
import com.twitter.util.events.Event
import com.twitter.util.{Try, Throw, Return, Time}

// Factored out for testing.
private[util] trait JsonSink extends Deserializer with Serializer {
  protected def typeId(buf: Buf) = buf match {
    case Buf.Utf8(str) if str.length > 0 => Try {
      val node = Json.mapper.readTree(str)
      node.get("id").asText
    }
    case _ =>
      Throw(new IllegalArgumentException("unknown format"))
  }
}

/**
 * A utility to serialize the [[com.twitter.util.events.Sink]] to JSON.
 *
 * Add the following to the `onExit` hook to export the Sink to a file when the
 * server exits, or place it behind an endpoint to be triggered by request.
 *
 * {{{
 * val json = Writer.fromOutputStream(new FileOutputStream("sink.json"))
 * val done = Reader.copy(JsonSink.serialize(Sink.default), json) ensure json.close()
 * Await.result(done, 3.seconds)
 * }}}
 */
object JsonSink extends JsonSink {
  import SamplingTracer.Trace
  import EventSink.Record

  val types = Seq(Trace, Record)

  /**
   * For unknown types, make an attempt to deserialize primitive values.
   */
  override protected def getType(id: String) =
    super.getType(id).orElse(Some(catchAll))

  private val catchAll = new Event.Type {
    val id = "CatchAll"
    def serialize(event: Event) = Return(Buf.Empty)

    type EventTuple = (String, Long, Object)

    def deserialize(buf: Buf) = for {
      (idd, when, data) <- Buf.Utf8.unapply(buf) match {
        case None => Throw(new IllegalArgumentException("unknown format"))
        case Some(str) => Try(Json.mapper.readValue(str, classOf[EventTuple]))
      }
    } yield Event(this, Time.fromMilliseconds(when), objectVal = data)
  }
}
