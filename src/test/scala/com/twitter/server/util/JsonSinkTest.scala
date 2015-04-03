package com.twitter.server.util

import com.twitter.finagle.tracing.Annotation
import com.twitter.finagle.zipkin.thrift.ZipkinTracer.Trace
import com.twitter.io.{Buf, Reader}
import com.twitter.server.EventSink.Record
import com.twitter.util.events.{Sink, Event}
import com.twitter.util.{Await, Return, Throw, Try, Time}
import java.net.InetSocketAddress
import java.util.logging.{Level, LogRecord}
import org.junit.runner.RunWith
import org.scalacheck.{Gen, Arbitrary}
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class JsonSinkTest extends FunSuite with GeneratorDrivenPropertyChecks {
  import Arbitrary.arbitrary
  import JsonSinkTest._

  test("serialize andThen deserialize = identity") {
    val sink = Sink.of(mutable.ListBuffer.empty)

    // Make these arbitrary?
    sink.event(Record, objectVal = new LogRecord(Level.INFO, "hello"))
    sink.event(Record, objectVal = new LogRecord(Level.INFO, "world"))
    sink.event(Trace, objectVal = Annotation.Message("goodbye"))
    sink.event(Trace, objectVal = Annotation.LocalAddr(new InetSocketAddress(0)))

    val identity = JsonSink.serialize _ andThen JsonSink.deserialize
    val events = Await.result(identity(sink))

    assert(Await.result(events.map(normalizeLog).toSeq) == sink.events.map(normalizeLog).toSeq)
  }

  test("JsonSink.serialize: Record") {
    val sink = Sink.of(mutable.ListBuffer.empty)
    sink.event(Record, objectVal = new LogRecord(Level.INFO, "hello"))
    val reader = JsonSink.serialize(sink)
    val f = Reader.readAll(reader).map(buf => Buf.Utf8.unapply(buf).get)

    assert(Await.result(f) == Seq(
      s""""id":"${Record.id}"""",
      s""""when":${sink.events.next.when.inMilliseconds}""",
      s""""data":{"level":"INFO","message":"hello"}"""
    ).mkString("{", ",", "}\r\n"))
  }

  test("JsonSink.serialize: Trace") {
    val sink = Sink.of(mutable.ListBuffer.empty)
    sink.event(Trace, objectVal = Annotation.Message("hello"))
    val reader = JsonSink.serialize(sink)
    val f = Reader.readAll(reader).map(buf => Buf.Utf8.unapply(buf).get)

    assert(Await.result(f) == Seq(
      s""""id":"${Trace.id}"""",
      s""""when":${sink.events.next.when.inMilliseconds}""",
      s""""data":["${classOf[Annotation.Message].getName}",{"content":"hello"}]"""
    ).mkString("{", ",", "}\r\n"))
  }

  test("blind deserializer") {
    val sink = Sink.of(mutable.ListBuffer.empty)
    val Event = mkEtype[String]("Event")

    // A deserializer without any types.
    val blind = mkJsonSink(Nil)
    val blindId = blind.serialize _ andThen blind.deserialize

    // A deserializer of Event types.
    val deser = mkJsonSink(Seq(Event))
    val deserId = deser.serialize _ andThen deser.deserialize

    forAll(genEntries(Event)) { entries =>
      entries.foreach(e => sink.event(e.e, e.l, e.o, e.d))
      val fromBlind = Await.result(blindId(sink).flatMap(_.toSeq))
      val fromDeser = Await.result(deserId(sink).flatMap(_.toSeq))

      assert(fromBlind == Nil)
      assert(fromDeser == sink.events.toSeq)
    }
    
  }

  test("objectVal: String") {
    new TestType[String] {
      forAll(genEntries(Event)) { entries =>
        entries.foreach(e => sink.event(e.e, e.l, e.o, e.d))
        val events = Await.result(identity(sink))
        assert(Await.result(events.toSeq) == sink.events.toSeq)
      }
    }
  }

  test("objectVal: Integer") {
    implicit val arbInteger = Arbitrary(arbitrary[Int].map[Integer](x => x))
    new TestType[Integer] {
      forAll(genEntries(Event)) { entries =>
        entries.foreach(e => sink.event(e.e, e.l, e.o, e.d))
        val events = Await.result(identity(sink))
        assert(Await.result(events.toSeq) == sink.events.toSeq)
      }
    }
  }

  test("objectVal: Integer array") {
    new TestType[List[Int]] {
      forAll(genEntries(Event)) { entries =>
        entries.foreach(e => sink.event(e.e, e.l, e.o, e.d))
        val events = Await.result(identity(sink))
        assert(Await.result(events.toSeq) == sink.events.toSeq)
      }
    }
  }
}

private object JsonSinkTest {
  import Arbitrary.arbitrary

  case class Entry[A](e: Event.Type, l: Long, o: A, d: Double)
  case class Envelope[A](id: String, when: Long, l: Long, o: A, d: Double, t: Long, s: Long)

  // Convenience wrapper around Event.Type that preserves type information for
  // objectVal -- we need this to deserialize.
  abstract class Etype[A] extends Event.Type

  // TODO Near replica of other Event.Type constructions, making Event.Type
  // more generic could help factor out the boiler plate.
  def mkEtype[A <% Object](idd: String): Etype[A] = new Etype[A] {
    val id = idd

    def serialize(event: Event) = event match {
      case Event(etype, when, l, o, d, t, s) if etype eq this =>
        val env = Envelope(id, when.inMilliseconds, l, o, d, t, s)
        Try(Buf.Utf8(Json.serialize(env)))

      case _ => Throw(new IllegalArgumentException("unknown format"))
    }

    def deserialize(buf: Buf) = for {
      (idd, when, l, o, d) <- Buf.Utf8.unapply(buf) match {
        case None => Throw(new IllegalArgumentException("unknown format"))
        case Some(str) => Try {
          val env = Json.mapper.readValue(str, classOf[Envelope[A]])

          // 2.11 won't apply an implicit conversion targeting Object, but we
          // can manually invoke the converter provided by the view bound.
          val obj = implicitly[A => Object].apply(env.o)

          (env.id, Time.fromMilliseconds(env.when), env.l, obj, env.d)
        }
      }
      if idd == id
    } yield Event(this, when, l, o, d)
  }

  def genEntry[A: Arbitrary](etype: Etype[A]): Gen[Entry[A]] = for {
    l <- arbitrary[Long]
    o <- arbitrary[A]
    d <- arbitrary[Double]
  } yield Entry(etype, l, o, d)

  def genEntries[A: Arbitrary](etype: Etype[A]): Gen[List[Entry[A]]] =
    Gen.listOfN(15, genEntry[A](etype))

  // Define LogRecord equality.
  def normalizeLog(e: Event): Event = e match {
    case Event(_, _, _, log: LogRecord, _, _, _) =>
      e.copy(objectVal = log.getLevel -> log.getMessage)
    case _ =>
      e
  }

  def mkJsonSink(etypes: Seq[Event.Type]): JsonSink = new JsonSink {
    val types = etypes
  }

  // Setup sink and Event construction for an arbitrary type.
  class TestType[A <% Object : Arbitrary] {
    val sink = Sink.of(mutable.ListBuffer.empty)
    val Event = mkEtype[A]("Event")
    val util = mkJsonSink(Seq(Event))
    val identity = util.serialize _ andThen util.deserialize
  }
}

