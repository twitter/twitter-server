package com.twitter.server.util

import com.twitter.finagle.zipkin.thrift.ZipkinTracer
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

object JsonSink extends JsonSink {
  import ZipkinTracer.Trace
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

private[util] object Json {
  import com.fasterxml.jackson.core.`type`.TypeReference
  import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}
  import com.fasterxml.jackson.module.scala.DefaultScalaModule
  import java.lang.reflect.{Type, ParameterizedType}

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def serialize(o: AnyRef): String = mapper.writeValueAsString(o)

  def deserialize[T: Manifest](value: String): T =
    mapper.readValue(value, typeReference[T])

  def deserialize[T: Manifest](node: JsonNode): T =
    mapper.readValue(node.traverse, typeReference[T])

  private def typeReference[T: Manifest] = new TypeReference[T] {
    override def getType = typeFromManifest(manifest[T])
  }

  private def typeFromManifest(m: Manifest[_]): Type =
    if (m.typeArguments.isEmpty) m.runtimeClass else new ParameterizedType {
      def getRawType = m.runtimeClass
      def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray
      def getOwnerType = null
    }
}

