package com.twitter.server.util

import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Reader, Buf}
import com.twitter.util.events.{Event, Sink}
import com.twitter.util.{Throw, Return, Try}

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
    Reader.concat(AsyncStream.fromSeq(bufs))
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

  private[this] final def readEvent(line: Buf): Try[Event] = for {
    id <- typeId(line)
    etype <- getType(id) match {
      case None => Throw(new IllegalArgumentException(s"no type found for: $id"))
      case Some(etype) => Return(etype)
    }
    event <- etype.deserialize(line)
  } yield event

  /**
   * Parse a byte stream into a list of events.
   */
  final def deserialize(reader: Reader): AsyncStream[Event] =
    for { Return(r) <- getLines(reader, CrLf).map(readEvent) } yield r
}

private[util] object Helpers {
  import AsyncStream.fromFuture

  val CrLf = Buf.Utf8("\r\n")

  def indexOf(buf: Buf, sep: Buf): Int =
    Buf.ByteArray.Owned.extract(buf).indexOfSlice(Buf.ByteArray.Owned.extract(sep))

  def splitAt(buf: Buf, ix: Int, width: Int): (Buf, Buf) =
    (buf.slice(0, ix), buf.slice(ix + width, buf.length))

  /**
   * Line-buffered reader, where lines are delimited by a configurable separator.
   */
  def getLines(reader: Reader, sep: Buf, acc: Buf = Buf.Empty): AsyncStream[Buf] =
    indexOf(acc, sep) match {
      case -1 => for {
        opt <- fromFuture(reader.read(Int.MaxValue))
        buf <- opt match {
          case None if acc.length == 0 => AsyncStream.empty
          case None => AsyncStream.of(acc)
          case Some(b) => getLines(reader, sep, acc.concat(b))
        }
      } yield buf

      case ix =>
        val (line, remainder) = splitAt(acc, ix, sep.length)
        line +:: getLines(reader, sep, remainder)
    }
}
