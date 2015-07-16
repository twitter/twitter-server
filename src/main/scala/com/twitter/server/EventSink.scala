package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.context.Contexts
import com.twitter.finagle.tracing.Trace
import com.twitter.io.Buf
import com.twitter.logging.{Logger, Handler, Formatter, Level}
import com.twitter.util.events.{Event, Sink}
import com.twitter.util.{Throw, Time, Try}
import java.util.logging.LogRecord
import scala.annotation.varargs

/**
 * Captures events from a logger.
 *
 * By default `EventSink` uses the root logger and default
 * [[com.twitter.util.events.Sink]]. We can use
 * [[com.twitter.finagle.context.Contexts]] to specify overrides.
 *
 * {{{
 * import EventSink._
 * val spec = Seq(
 *   Capture(Logger("example.MyClass"), Level.DEBUG),
 *   Capture(Logger("example.OtherClass"), Level.CRITICAL)
 * )
 * Contexts.local.let(eventSinkCtx, spec:_*) {
 *   // Start twitter-server...
 * }
 * }}}
 */
trait EventSink { app: App =>
  premain {
    import EventSink._
    val config = Contexts.local.get(eventSinkCtx).getOrElse(DefaultConfig)
    EventSink.runConfig(config)
  }
}

object EventSink {
  /**
   * A specification of how to set up the [[com.twitter.logging.Handler]] for a
   * [[com.twitter.logging.Logger]].
   */
  case class Capture(logger: Logger, level: Level, formatter: Formatter) {
    override def toString = s"Capture(${logger.name}, $level)"
  }

  object Capture {
    def apply(logger: Logger): Capture = Capture(logger, Level.ALL)
    def apply(logger: Logger, level: Level): Capture = Capture(logger, level, new Formatter)
  }

  /**
   * Configuration for the EventSink.
   */
  @varargs
  case class Configuration(sink: Sink, captures: Capture*)

  val DefaultConfig: Configuration = {
    val root = Logger.get("")
    val level = Option(root.getLevel).flatMap(Level.fromJava).getOrElse(Level.ALL)
    Configuration(Sink.default, Capture(root, level))
  }

  val eventSinkCtx: Contexts.local.Key[Configuration] =
    new Contexts.local.Key[Configuration]

  private[this] case class Log(level: String, message: String)

  val Record = {

    new Event.Type {
      val id = "Record"

      def serialize(event: Event) = event match {
        case Event(etype, when, _, log: LogRecord, _, tid, sid) if etype eq this =>
          val (t, s) = serializeTrace(tid, sid)
          val data = Log(log.getLevel.getName, log.getMessage)
          val env = Json.Envelope(id, when.inMilliseconds, t, s, data)
          Try(Buf.Utf8(Json.serialize(env)))

        case _ =>
          Throw(new IllegalArgumentException("unknown format"))
      }

      def deserialize(buf: Buf) = for {
        env <- Buf.Utf8.unapply(buf) match {
          case None => Throw(new IllegalArgumentException("unknown format"))
          case Some(str) => Try(Json.deserialize[Json.Envelope[Log]](str))
        }
        if env.id == id
        level <- Try(Level.parse(env.data.level).get)
      } yield {
        val when = Time.fromMilliseconds(env.when)
        // This line fails without the JsonDeserialize annotation in Envelope.
        val tid = env.traceId.getOrElse(Event.NoTraceId)
        val sid = env.spanId.getOrElse(Event.NoSpanId)
        Event(this, when, objectVal = new LogRecord(level, env.data.message),
          traceIdVal = tid, spanIdVal = sid)
      }
    }
  }

  private def mkHandler(sink: Sink, level: Level, formatter: Formatter): Handler =
    new Handler(formatter, Some(level)) {
      def publish(record: LogRecord) =
        if (sink.recording && isLoggable(record)) {
          if (Trace.hasId) sink.event(
            Record,
            objectVal = record,
            traceIdVal = Trace.id.traceId.self,
            spanIdVal = Trace.id.spanId.self
          ) else sink.event(Record, objectVal = record)
        }
      def close() = ()
      def flush() = ()
    }

  /**
   * Initialize the capture sink with this capture configuration.
   */
  def runConfig(config: Configuration): Unit = {
    config.captures.foreach {
      case Capture(logger, level, formatter) =>
        logger.addHandler(mkHandler(config.sink, level, formatter))
    }
  }
}

private object Json {
  import com.fasterxml.jackson.annotation.JsonInclude
  import com.fasterxml.jackson.core.`type`.TypeReference
  import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}
  import com.fasterxml.jackson.databind.annotation.JsonDeserialize
  import com.fasterxml.jackson.module.scala.DefaultScalaModule
  import java.lang.reflect.{Type, ParameterizedType}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  case class Envelope[A](
      id: String,
      when: Long,
      // We require an annotation here, because for small numbers, this gets
      // deserialized with a runtime type of int.
      // See: https://github.com/FasterXML/jackson-module-scala/issues/106.
      @JsonDeserialize(contentAs = classOf[java.lang.Long]) traceId: Option[Long],
      @JsonDeserialize(contentAs = classOf[java.lang.Long]) spanId: Option[Long],
      data: A)

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

