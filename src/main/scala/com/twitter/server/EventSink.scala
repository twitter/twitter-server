package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.context.Contexts
import com.twitter.io.Buf
import com.twitter.logging.{Logger, Handler, Formatter, Level}
import com.twitter.util.events.{Event, Sink}
import com.twitter.util.{Return, Throw, Time, Try}
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
    EventSink.runConfig()
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

  val DefaultConfig = Configuration(Sink.default, Capture(Logger.get(""), Level.ALL))
  val eventSinkCtx = new Contexts.local.Key[Configuration]

  val Record = {
    case class Log(level: String, message: String)
    case class Envelope(id: String, when: Long, data: Log)

    new Event.Type {
      val id = "Record"

      def serialize(event: Event) = event match {
        case Event(etype, when, _, log: LogRecord, _) if etype eq this =>
          val data = Log(log.getLevel.getName, log.getMessage)
          val env = Envelope(id, when.inMilliseconds, data)
          Try(Buf.Utf8(Json.serialize(env)))

        case _ =>
          Throw(new IllegalArgumentException("unknown format"))
      }

      def deserialize(buf: Buf) = for {
        (idd, when, data) <- Buf.Utf8.unapply(buf) match {
          case None => Throw(new IllegalArgumentException("unknown format"))
          case Some(str) => Try {
            val env = Json.deserialize[Envelope](str)
            (env.id, Time.fromMilliseconds(env.when), env.data)
          }
        }
        if idd == id
        level <- Try(Level.parse(data.level).get)
      } yield Event(this, when, objectVal = new LogRecord(level, data.message))
    }
  }

  private def mkHandler(sink: Sink, level: Level, formatter: Formatter): Handler =
    new Handler(formatter, Some(level)) {
      def publish(record: LogRecord) =
        if (isLoggable(record)) sink.event(Record, objectVal = record)
      def close() = ()
      def flush() = ()
    }

  private[server] def runConfig(): Unit = {
    val config = Contexts.local.get(eventSinkCtx).getOrElse(DefaultConfig)
    config.captures.foreach {
      case Capture(logger, level, formatter) =>
        logger.addHandler(mkHandler(config.sink, level, formatter))
    }
  }
}

private object Json {
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

