package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Fields, MediaType, Status, Method, Response, Request}
import com.twitter.finagle.tunable.StandardTunableMap
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.JsonConverter
import com.twitter.util.{Throw, Return, Future}
import com.twitter.util.logging.Logger
import com.twitter.util.tunable.{JsonTunableMapper, TunableMap}
import scala.collection.mutable

/**
 * In-memory Tunables can be manipulated using the endpoint `/admin/tunables/`. PUT and DELETE
 * requests to update the Tunables for a given id should be made to `/admin/tunables/$id`
 * and have a JSON body in the same format as a Tunable configuration file:
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
 * In the case of a PUT, these Tunables will be updated or added for the TunableMap corresponding
 * to `id`. Note that this PUT request will *not* cause any existing Tunables to be removed.
 *
 * In the case of a DELETE, these Tunables will cleared from the TunableMap corresponding
 * to `id`. The Tunables are keyed by "id" and "type"; the "value" for each of Tunables to delete
 * can be any valid value for this Tunable. Because the value of a Tunable is the result of a
 * composition of TunableMaps (see [[StandardTunableMap]]), deleting an in-memory Tunable will cause
 * the value from the composition of the other TunableMaps to be used.
 */
class TunableHandler private[handler] (registeredIdsFn: () => Map[String, TunableMap])
    extends Service[Request, Response] {
  import TunableHandler._

  def this() = this(() => StandardTunableMap.registeredIds)

  // Classes used to compose a "view" of a TunableMap, which is returned to the user as a JSON
  // string.

  /**
   * View of a [[TunableMap]] suitable for presentation
   *
   * @param id  id which this [[TunableMap]] representation corresponds to
   * @param tunables  representation of the [[Tunable]]s in the [[TunableMap]]
   */
  private[this] case class TunableMapView(id: String, tunables: Seq[TunableView])

  /**
   * View of a [[Tunable]] suitable for presentation
   *
   * @param id  id of the [[Tunable]]
   * @param value  the current value of the [[Tunable]]
   * @param components  the components that this [[Tunable]] is composed of. These compositions
   *                    are a reflection of the composition of [[TunableMap]]s that make up the
   *                    [[TunableMap]] for a given id. If a [[Tunable]] with the same key occurs
   *                    in multiple of these maps, the different values will be the [[Components]]
   */
  private[this] case class TunableView(id: String, value: String, components: Seq[Component])

  /**
   * View of a [[Tunable]] value that composes a [[Tunbable]] in a composed [[TunableMap]]
   *
   * @param source  the source of the value, i.e. `TunableMap.Entry.source`
   * @param value   value of the component
   */
  private[this] case class Component(source: String, value: String)

  private[this] def respond(
    status: Status,
    content: String,
    headers: Iterable[(String, Object)] = Seq.empty
  ): Future[Response] =
    newResponse(
      status = status,
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Utf8(content)
    )

  private[this] def findMutable(
    maps: Map[String, TunableMap],
    id: String
  ): Option[TunableMap.Mutable] = maps.get(id).flatMap {
    TunableMap.components(_).collectFirst {
      case mut: TunableMap.Mutable => mut
    }
  }

  private[this] def handleGet(req: Request): Future[Response] = {
    val id = req.path.stripPrefix(PathForId)
    registeredIdsFn().get(id) match {
      case None =>
        respond(Status.NotFound, s"TunableMap not found for id: $id")
      case Some(map) =>
        val view = toTunableMapView(id, map)
        respond(Status.Ok, JsonConverter.writeToString(view))
    }
  }

  private[this] def handleGetAll(): Future[Response] = {
    val view = registeredIdsFn().toSeq
      .sortBy {
        case (id, _) => id
      }
      .map {
        case (id, map) => toTunableMapView(id, map)
      }
    respond(Status.Ok, JsonConverter.writeToString(view))
  }

  private[this] def toTunableMapView(id: String, tunableMap: TunableMap): TunableMapView = {

    val currentsMap: Map[String, TunableMap.Entry[_]] = tunableMap.entries.map {
      case entry @ TunableMap.Entry(key, _, _) => key.id -> entry
    }.toMap

    val componentsMap = mutable.Map.empty[String, mutable.ArrayBuffer[Component]]

    for {
      map <- TunableMap.components(tunableMap)
      entry <- map.entries
    } yield {
      val components: mutable.ArrayBuffer[Component] =
        componentsMap.getOrElse(entry.key.id, mutable.ArrayBuffer.empty[Component])
      componentsMap.put(entry.key.id, components += Component(entry.source, entry.value.toString))
    }

    val tunables = componentsMap.map {
      case (id, components) =>
        val md = currentsMap(id)
        TunableView(id, md.value.toString, components.toSeq)
    }.toSeq

    TunableMapView(id, tunables)
  }

  private[this] def handlePut(req: Request): Future[Response] = req.contentType match {
    case Some(MediaType.Json) =>
      val json = req.contentString
      JsonTunableMapper().parse(json) match {
        case Return(tunableMap) =>
          val id = req.path.stripPrefix(PathForId)
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

  private[this] def handleDelete(req: Request): Future[Response] = req.contentType match {
    case Some(MediaType.Json) =>
      val json = req.contentString
      JsonTunableMapper().parse(json) match {
        case Return(tunableMap) =>
          val id = req.path.stripPrefix(PathForId)
          findMutable(registeredIdsFn(), id) match {
            case None =>
              respond(Status.NotFound, s"Mutable TunableMap not found for id: $id")
            case Some(mutable) =>
              mutable --= tunableMap
              val successMsg = s"Successfully deleted tunables for id: $id"
              log.info(successMsg)
              respond(Status.Ok, successMsg)
          }
        case Throw(e) =>
          respond(Status.BadRequest, s"Failed to parse JSON for DELETE request: ${e.getMessage}")
      }
    case unsupported =>
      respond(Status.BadRequest, s"Expected Content-Type ${MediaType.Json} for DELETE request")
  }

  def apply(req: Request): Future[Response] = req.path match {
    case Path =>
      req.method match {
        case Method.Get =>
          handleGetAll()
        case unsupported =>
          respond(
            Status.MethodNotAllowed,
            s"Unsupported HTTP method: $unsupported",
            Seq((Fields.Allow, "GET"))
          )
      }
    case _ =>
      req.method match {
        case Method.Get =>
          handleGet(req)
        case Method.Put =>
          handlePut(req)
        case Method.Delete =>
          handleDelete(req)
        case unsupported =>
          respond(
            Status.MethodNotAllowed,
            s"Unsupported HTTP method: $unsupported",
            Seq((Fields.Allow, "GET, PUT, DELETE"))
          )
      }
  }
}

object TunableHandler {

  val Path = "/admin/tunables"

  val PathForId: String = Path + "/"

  private val log: Logger = Logger[TunableHandler]
}
