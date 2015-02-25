package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.util.registry.{Entry, Registry}
import com.twitter.util.{Future, NoStacktrace}
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.JsonConverter
import java.util.logging.Logger

/**
 * A [[com.twitter.finagle.Service]] for displaying the current state of the
 * registry.
 *
 * It's intended to be used as a handler for TwitterServer, and
 * doesn't take any arguments yet.  As an admin endpoint, it displays
 * the entire registry in JSON.
 */
class RegistryHandler(registry: Registry) extends Service[Request, Response] {
  import RegistryHandler._

  private[this] val log = Logger.getLogger(getClass.getName)

  // TODO: have nice default HTML rendering for json output
  def apply(req: Request): Future[Response] = {
    newOk(jsonResponse())
  }

  private[handler] def jsonResponse(): String = {
    var map: Map[String, Object] = Map.empty[String, Object]
    registry.foreach { case Entry(keys, value) =>
      map = try {
        add(map, keys, value)
      } catch {
        case Collision =>
          log.severe(s"collided on (${keys.mkString(",")}) -> $value")
          map
        case InvalidType =>
          log.severe(s"found an invalid type on (${keys.mkString(",")}) -> $value")
          map
        case Empty =>
          val returnString = s"(${keys.mkString(",")}) -> $value"
          log.severe(s"incorrectly found an empty seq on $returnString")
          map
      }
    }
    val msg = Map("registry" -> map)
    JsonConverter.writeToString(msg)
  }
}

private[handler] object RegistryHandler {
  val Eponymous = "__eponymous"

  /**
   * assumes that you do not create an exact collision
   */
  def add(
    old: Map[String, Object],
    keys: Seq[String],
    value: String
  ): Map[String, Object] = old + (keys match {
    case Nil => (Eponymous -> value)
    case head +: tail => {
      head -> (old.get(head) match {
        case None =>
          if (tail.isEmpty) value
          else makeMap(tail, value)

        // we can't prove that this is anything better than a Map[_, _], but that's OK
        case Some(map: Map[_, _]) => add(map.asInstanceOf[Map[String, Object]], tail, value)
        case Some(string: String) =>
          if (tail.isEmpty) throw Collision
          else makeMap(tail, value) + (Eponymous -> string)
        case Some(_) => throw InvalidType
      })
    }
  })

  /**
   * @param seq is not permitted to be empty
   */
  def makeMap(seq: Seq[String], value: String): Map[String, Object] =
    seq.foldRight[Either[Map[String, Object], String]](Right(value)) {
      case (key, Right(string)) => Left(Map(key -> string))
      case (key, Left(map)) => Left(Map(key -> map))
    } match {
      case Right(string) => throw Empty
      case Left(map) => map
    }

  val Collision = new Exception() with NoStacktrace
  val InvalidType = new Exception() with NoStacktrace
  val Empty = new Exception() with NoStacktrace
}
