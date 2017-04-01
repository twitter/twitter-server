package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Fields, MediaType, Status, Method, Response, Request}
import com.twitter.finagle.tunable.StandardTunableMap
import com.twitter.io.Buf
import com.twitter.logging.Logger
import com.twitter.server.util.HttpUtils._
import com.twitter.util.{Throw, Return, Future}
import com.twitter.util.tunable.{JsonTunableMapper, TunableMap}

/**
 * In-memory Tunables can be manipulated using the endpoint `/admin/tunables/`.
 * To update the Tunables for a given id, make a PUT request to `/admin/tunables/$id`
 * with a JSON body in the same format as a Tunable configuration file:
 *
 * {
 *    "tunables":
 *      [
 *         {
 *           "id" : "$id1",
 *           "value" : $value,
 *           "type" : "$class"
 *         },
 *         {
 *           "id" : "$id2",
 *           "value" : $value,
 *           "type" : "$class"
 *         }
 *     ]
 * }
 *
 * These Tunables will be updated or added for the TunableMap corresponding to `id`. Note that
 * this PUT request will *not* cause any existing Tunables to be removed.
 */
class TunableHandler private[handler] (
    registeredIdsFn: () => Map[String, TunableMap])
  extends Service[Request, Response] {
  import TunableHandler._

  def this() = this(() => StandardTunableMap.registeredIds)

  private[this] def respond(
    status: Status,
    content: String,
    headers: Iterable[(String, Object)] = Seq.empty
  ): Future[Response] =
    newResponse(
      status = status,
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Utf8(content))

  private[this] def findMutable(
    maps: Map[String, TunableMap],
    id: String
  ): Option[TunableMap.Mutable] = maps.get(id).flatMap {
      TunableMap.components(_).collectFirst {
        case mut: TunableMap.Mutable => mut
      }
    }

  private[this] def handlePut(req: Request): Future[Response] = req.contentType match {
    case Some(MediaType.Json) =>
      val json = req.contentString
      JsonTunableMapper().parse(json) match {
        case Return(tunableMap) =>
          val id = req.path.stripPrefix(Path)
          findMutable(registeredIdsFn(), id) match {
            case None =>
              respond(Status.NotFound, s"Mutable TunableMap not found for id: $id")
            case Some(mutable) =>
              mutable ++= tunableMap
              val successMsg = s"Successfully updated tunables for id: $id"
              log.info(successMsg)
              respond(Status.Ok, successMsg)
          }
        case Throw(e) =>
          respond(Status.BadRequest, s"Failed to parse JSON for PUT request: ${e.getMessage}")
      }
    case unsupported =>
      respond(Status.BadRequest, s"Expected Content-Type ${MediaType.Json} for PUT request")
  }

  def apply(req: Request): Future[Response] = req.method match {
    case Method.Put =>
      handlePut(req)
    case unsupported =>
      respond(
        Status.MethodNotAllowed,
        s"Unsupported HTTP method: $unsupported",
        Seq((Fields.Allow, "PUT")))
  }
}

object TunableHandler {

  val Path = "/admin/tunables/"

  private val log: Logger = Logger.get()
}