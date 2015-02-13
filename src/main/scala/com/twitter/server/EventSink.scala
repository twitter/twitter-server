package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.context.Contexts
import com.twitter.logging.{Logger, Handler, Formatter, Level}
import com.twitter.util.events.{Event, Sink}
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
  val Record = new Event.Type { }

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
