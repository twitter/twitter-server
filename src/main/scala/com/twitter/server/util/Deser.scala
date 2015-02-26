package com.twitter.server.util

import com.twitter.util.events.{Event, Sink}
import com.twitter.concurrent.Spool
import com.twitter.io.{Reader, Buf}
import com.twitter.util.{Future, Throw, Return, Try}

private[twitter] trait Serializer {
  import Helpers._

  /**
   * Serialize the sink to a lazy byte stream.
   */
  final def serialize(sink: Sink): Reader = {
    val bufs = sink.events.toStream.flatMap { e =>
      e.etype.serialize(e) match {
        case Return(buf) => Some(Reader.fromBuf(buf.concat(CrLf)))
        case Throw(e) =>
          // TODO Report these errors somehow.
          None
      }
    }
    Reader.concat(lazySpool(bufs))
  }
}

/**
 * A serializer and deserializer for sinks.
 *
 */
private[server] trait Deserializer {
  import Helpers._

  /**
   * The Event.Types the parser can construct.
   */
  def types: Seq[Event.Type]

  /**
   * Extract the type id from an opaque Buf.
   */
  protected def typeId(buf: Buf): Try[String]

  /**
   * Find an Event.Type for this id.
   */
  protected def getType(id: String): Option[Event.Type] =
    types.find(_.id == id) 

  /**
   * Parse a byte stream into a list of events.
   */
  final def deserialize(reader: Reader): Future[Spool[Event]] = for {
    lines <- getLines(reader, CrLf)
    events <- flatten(lines.map { line =>
      for {
        id <- typeId(line)
        etype <- getType(id) match {
          case None => Throw(new IllegalArgumentException(s"no type found for: $id"))
          case Some(etype) => Return(etype)
        }
        event <- etype.deserialize(line)
      } yield event
    })
  } yield events
}

private[util] object Helpers {
  val CrLf = Buf.ByteArray('\r'.toByte, '\n'.toByte)

  /**
   * Creates a spool lazily from a stream. seqToSpool is not lazy enough.
   */
  def lazySpool[A](as: Stream[A]): Spool[A] =
    if (as.isEmpty) Spool.empty else as.head *:: Future.value(lazySpool(as.tail))

  /**
   * Return a spool with results given an error handler.
   */
  def flatten[A](spool: Spool[Try[A]]): Future[Spool[A]] =
    if (spool.isEmpty) Future.value(Spool.empty)
    else spool.head match {
      case Return(a) => Future.value(a *:: spool.tail.flatMap(flatten))
      case Throw(e) =>
        // TODO Report errors somehow.
        spool.tail.flatMap(flatten)
    }

  def indexOf(buf: Buf, sep: Buf): Int =
    Buf.ByteArray.Owned.extract(buf).indexOfSlice(Buf.ByteArray.Owned.extract(sep))

  def splitAt(buf: Buf, ix: Int, width: Int): (Buf, Buf) =
    (buf.slice(0, ix), buf.slice(ix + width, buf.length))

  /**
   * Line-buffered reader, where lines are delimited by a configurable separator.
   */
  def getLines(reader: Reader, sep: Buf, acc: Buf = Buf.Empty): Future[Spool[Buf]] =
    reader.read(Int.MaxValue).flatMap {
      case None if acc.length > 0 =>
        // Skip last line if empty
        Future.value(acc *:: Future.value(Spool.empty[Buf]))

      case Some(buf) => indexOf(buf, sep) match {
        case -1 => getLines(reader, sep, acc.concat(buf))
        case ix =>
          val (line, remainder) = splitAt(buf, ix, sep.length)
          Future.value(acc.concat(line) *:: getLines(reader, sep, remainder))
      }

      case _ => Future.value(Spool.empty)
    }
}
