package com.twitter.server

import com.twitter.finagle.context.Contexts
import com.twitter.logging.{Level, Logger}
import com.twitter.util.Time
import com.twitter.util.events.{Event, Sink}
import java.util.logging.{LogManager, LogRecord}
import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class EventSinkTest extends FunSuite with GeneratorDrivenPropertyChecks {
  import EventSink._
  import EventSinkTest._

  test("EventSink publishes to according to Capture specification") {
    // Prevent the root logger from writing to stdout.
    LogManager.getLogManager.reset()

    lazy val loggers = List("a", "b", "c", "d").map(Logger.get)

    Logger.withLazyLoggers(loggers) {
      // Test inputs comprise a journal of logging events (List[Entry]), and a
      // list of captures. An entry is a 3-tuple of Logger, Level and message.
      // These entries are "run" by invoking log with the tuple arguments.
      val genTestInputs = for {
        journal <- Gen.listOf(genEntry(loggers))
        captures <- sequence(journal.map(_.logger).distinct)
      } yield (journal, captures)

      forAll(genTestInputs) { case (journal, captures) =>
        val sink = Sink.of(mutable.ListBuffer.empty)
        val config = Configuration(sink, captures:_*)

        // Attach the handlers with the sink and capture specification.
        Contexts.local.let(eventSinkCtx, config)(runConfig)

        // Interpret the journal by running each entry through the logger.
        journal foreach { case Entry(logger, level, message) =>
          logger.log(level, message)
        }

        assert(sink.events.map(normalize).flatten.toList == journal.filter {
          case Entry(logger, messageLevel, message) =>
            captures.find(_.logger == logger) match {
              case Some(Capture(_, captureLevel, _)) =>
                // This logging event should have made it to the sink unless:
                // - the capture spec turned capture off for this logger
                captureLevel.intValue != Level.OFF.intValue &&
                // - the logger ignores messages of this level
                logger.isLoggable(messageLevel) &&
                // - the handler ignores messages of this level
                messageLevel.intValue >= captureLevel.intValue

              case _ => false
            }
          }.map(e => e.level -> e.message)
        )
      }
    }
  }
}

private object EventSinkTest {
  import EventSink._

  val genLevel: Gen[Level] =
    Gen.oneOf(Level.OFF, Level.FATAL, Level.CRITICAL, Level.ERROR,
      Level.WARNING, Level.INFO, Level.DEBUG, Level.TRACE, Level.ALL)

  // A logging event in suspension.
  case class Entry(logger: Logger, level: Level, message: String) {
    override def toString() = s"Entry(${logger.name}, $level, $message)"
  }

  def genEntry(loggers: Seq[Logger]): Gen[Entry] = for {
    logger <- Gen.oneOf(loggers)
    level <- genLevel
    message <- Gen.alphaStr
  } yield Entry(logger, level, message.take(10))

  def normalize(e: Event) = e match {
    case Event(Record, _, _, log: LogRecord, _) => Some(log.getLevel -> log.getMessage)
    case _ => None
  }

  // Given a list of loggers, we can generate a list of captures by sequencing
  // the actions to generate arbitrary Levels.
  def sequence(loggers: List[Logger]): Gen[List[Capture]] =
    loggers.foldLeft(Gen.wrap(Nil: List[Capture])) { case (captures, logger) =>
      for (c <- captures; level <- genLevel) yield Capture(logger, level) +: c
    }
}
