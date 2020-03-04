package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Fields, Method, Request, Response, Status}
import com.twitter.finagle.toggle.{StandardToggleMap, Toggle, ToggleMap}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{newOk, newResponse}
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import com.twitter.util.logging.Logger
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/** package private for testing purposes */
private[handler] object ToggleHandler {

  private val log: Logger = Logger[ToggleHandler]

  /**
   * How a `Toggle` is currently configured and in use.
   *
   * @param id corresponds to `Toggle.Metadata.id`
   * @param fraction corresponds to `Toggle.Metadata.fraction`
   * @param lastValue indicates the last value observed by `Toggle.apply`
   * @param description corresponds to `Toggle.Metadata.description`
   */
  case class Current(
    id: String,
    fraction: Double,
    lastValue: Option[Boolean],
    description: Option[String])

  /**
   * The components that compose the "current" `Toggle`.
   *
   * @param source corresponds to `Toggle.Metadata.source`
   * @param fraction corresponds to `Toggle.Metadata.fraction`
   */
  case class Component(source: String, fraction: Double)

  case class LibraryToggle(current: Current, components: Seq[Component])

  case class Library(libraryName: String, toggles: Seq[LibraryToggle])

  case class Libraries(libraries: Seq[Library])

  private val ParamFraction: String = "fraction"

  private case class GenericResponse(message: String, errors: Seq[String])

  /**
   * The result of parsing the `Request` path which should be of the form:
   * "/admin/toggles{/$libraryName}{/$id}"
   *
   * @param libraryName `Some` if a library name exists and is valid
   * @param id `Some` if a toggle id exists and is valid
   */
  case class ParsedPath(libraryName: Option[String], id: Option[String])
}

/**
 * Admin UI for seeing and modifying the server's `com.twitter.finagle.toggle.Toggles`.
 *
 * This handler should be available at "/admin/toggles".
 *
 * GET requests shows the current state of all [[StandardToggleMap StandardToggleMaps]].
 * Requests should be of the form "/admin/toggles{/$libraryName}{/$id}".
 * Note that the library name and toggle id components are optional
 * but do allow for filtering the output on those constraints.
 * The output is JSON and it looks roughly like:
 * {{{
 * {
 *   "libraries": [
 *     {
 *       "libraryName" : "$libraryName",
 *       "toggles" : [
 *         {
 *           "current" : {
 *             "id" : "$id",
 *             "fraction" : $fraction,
 *             "description" : "$description"
 *           },
 *           "components" : [
 *             {
 *               "source" : "$ToggleMapSource",
 *               "fraction" : $fraction
 *             },
 *             { <other sources here> }
 *           ]
 *         },
 *         { <other toggles here> }
 *       ]
 *     },
 *     { <other libraries here> }
 *   ]
 * }
 * }}}
 *
 * There will be a hash for each library registered with
 * `com.twitter.finagle.toggle.StandardToggleMap`. For each `Toggle`
 * the "current" hash shows the current configuration while the
 * "components" array has a hash per `ToggleMap` source. These
 * are ordered by evaluation order and as such, sources earlier in a
 * component array are used first.
 *
 * PUT requests allow for update/create of the mutable Toggles
 * while DELETE requests allow for removal. These apply only to the
 * [[ToggleMap.Mutable]] within a [[StandardToggleMap]].
 * Requests must be of the form "/admin/toggles/$libraryName/$id".
 * For create and update, and an additional "fraction" request parameter
 * must be set as well.
 */
class ToggleHandler private[handler] (registeredLibrariesFn: () => Map[String, ToggleMap.Mutable])
    extends Service[Request, Response] {

  import ToggleHandler._

  def this() = this(() => StandardToggleMap.registeredLibraries)

  def apply(req: Request): Future[Response] = {
    req.method match {
      case Method.Get =>
        applyGet(req)
      case Method.Put =>
        applyPut(req)
      case Method.Delete =>
        applyDelete(req)
      case unsupported =>
        genericResponse(
          Status.MethodNotAllowed,
          "Unsupported method",
          Seq(s"Unsupported HTTP method: $unsupported"),
          headers = Seq((Fields.Allow, "GET, PUT, DELETE"))
        )
    }
  }

  private[this] def applyGet(req: Request): Future[Response] = {
    val errors = new ArrayBuffer[String]()
    val parsed = parsePath(req.path, errors)
    if (errors.isEmpty) {
      newOk(getResponse(parsed))
    } else {
      genericResponse(Status.BadRequest, "Get failed", errors.toSeq)
    }
  }

  private[this] def applyPut(req: Request): Future[Response] = {
    val errors = new ArrayBuffer[String]()
    val parsed = parsePath(req.path, errors)
    if (errors.isEmpty) {
      for {
        libraryName <- parsed.libraryName
        id <- parsed.id
      } {
        errors ++= setToggle(libraryName, id, req.params.get("fraction"))
      }
    }
    genericResponse(
      if (errors.isEmpty) Status.Ok else Status.BadRequest,
      if (errors.isEmpty) "Update successful" else "Update failed",
      errors.toSeq
    )
  }

  private[this] def applyDelete(req: Request): Future[Response] = {
    val errors = new ArrayBuffer[String]()
    val parsed = parsePath(req.path, errors)
    if (errors.isEmpty) {
      for {
        libraryName <- parsed.libraryName
        id <- parsed.id
      } {
        errors ++= deleteToggle(libraryName, id)
      }
    }
    genericResponse(
      if (errors.isEmpty) Status.Ok else Status.BadRequest,
      if (errors.isEmpty) "Delete successful" else "Delete failed",
      errors.toSeq
    )
  }

  private[this] def genericResponse(
    status: Status,
    msg: String,
    errors: Seq[String],
    headers: Iterable[(String, Object)] = Seq.empty
  ): Future[Response] = {
    // sort the errors to make the response more deterministic.
    val body = JsonConverter.writeToString(GenericResponse(msg, errors.sorted))
    newResponse(
      status = status,
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Utf8(body),
      headers = headers
    )
  }

  /**
   * @param path we expect our path to be /admin/toggles/$libraryName/$id
   * @return `None` in the case of validation failures with details added to `errors`.
   *         Successful executions will be a `Some` of `(libraryName, id)`.
   *
   * @note package protected for testing
   */
  private[handler] def parsePath(path: String, errors: ArrayBuffer[String]): ParsedPath = {
    path.split("/") match {
      case Array("", "admin", "toggles") =>
        ParsedPath(None, None)
      case Array("", "admin", "toggles", libraryName) =>
        if (!registeredLibrariesFn().contains(libraryName)) {
          errors += s"Unknown library name: '$libraryName'"
          ParsedPath(None, None)
        } else {
          ParsedPath(Some(libraryName), None)
        }
      case Array("", "admin", "toggles", libraryName, id) =>
        if (!registeredLibrariesFn().contains(libraryName))
          errors += s"Unknown library name: '$libraryName'"
        if (!Toggle.isValidId(id))
          errors += s"Invalid id: '$id'"
        if (errors.isEmpty) ParsedPath(Some(libraryName), Some(id))
        else ParsedPath(None, None)
      case _ =>
        errors += "Path must be of the form /admin/toggles{/$libraryName}{/$id}"
        ParsedPath(None, None)
    }
  }

  /**
   * Returns any error messages, or an empty `Seq` if successful.
   *
   * @param libraryName will have already been validated and known to exist
   * @param id will have already been validated
   *
   * @note package protected for testing
   */
  private[handler] def setToggle(
    libraryName: String,
    id: String,
    fractionStr: Option[String]
  ): Seq[String] = {
    val errors = new ArrayBuffer[String]()
    fractionStr match {
      case None =>
        errors += s"Missing query parameter: '$ParamFraction'"
      case Some(f) =>
        try {
          val fraction = f.toDouble
          if (Toggle.isValidFraction(fraction)) {
            val toggleMap = registeredLibrariesFn()(libraryName)
            log.info(s"Set $libraryName's toggle $id to $fraction")
            toggleMap.put(id, fraction)
          } else {
            errors += s"Fraction must be [0.0-1.0], was: '$fractionStr'"
          }
        } catch {
          case _: NumberFormatException =>
            errors += s"Fraction must be [0.0-1.0], was: '$fractionStr'"
        }
    }
    errors.toSeq
  }

  /**
   * Returns any error messages, or an empty `Seq` if successful.
   *
   * @param libraryName will have already been validated and known to exist
   * @param id will have already been validated
   *
   * @note package protected for testing
   */
  private[handler] def deleteToggle(libraryName: String, id: String): Seq[String] = {
    val errors = new ArrayBuffer[String]()
    val toggleMap = registeredLibrariesFn()(libraryName)
    log.info(s"Deleted $libraryName's toggle $id")
    toggleMap.remove(id)
    errors.toSeq
  }

  /** package protected for testing */
  private[handler] def getResponse(parsedPath: ParsedPath): String = {
    JsonConverter.writeToString(toLibraries(parsedPath))
  }

  /** package protected for testing */
  private[handler] def toLibraries(parsedPath: ParsedPath): Libraries = {
    val libraryFilter: String => Boolean = parsedPath.libraryName match {
      case Some(name) => _ == name
      case None =>
        _ =>
          true
    }
    val idFilter: String => Boolean = parsedPath.id match {
      case Some(name) => _ == name
      case None =>
        _ =>
          true
    }

    val registered = registeredLibrariesFn()
    val libs = registered
      .filter {
        case (libraryName, _) =>
          libraryFilter(libraryName)
      }
      .map {
        case (name, toggleMap) =>
          val libToggles = toLibraryToggles(toggleMap).filter { libToggle =>
            idFilter(libToggle.current.id)
          }
          Library(name, libToggles)
      }
    Libraries(libs.toSeq)
  }

  private[this] def toLibraryToggles(toggleMap: ToggleMap): Seq[LibraryToggle] = {
    // create a map of id to metadata for faster lookups
    val idToMetadata = toggleMap.iterator.map { md =>
      md.id -> md
    }.toMap

    // create a mapping of id to a seq of its components.
    val idToComponents = mutable.Map.empty[String, mutable.ArrayBuffer[Component]]
    ToggleMap.components(toggleMap).foreach { tm =>
      tm.iterator.foreach { md =>
        val components: mutable.ArrayBuffer[Component] =
          idToComponents.getOrElse(md.id, mutable.ArrayBuffer.empty[Component])
        idToComponents.put(md.id, components += Component(md.source, md.fraction))
      }
    }

    idToComponents.map {
      case (id, details) =>
        val md = idToMetadata(id)
        val lastApply = toggleMap(id) match {
          case captured: Toggle.Captured => captured.lastApply
          case _ => None
        }
        LibraryToggle(Current(id, md.fraction, lastApply, md.description), details.toSeq)
    }.toSeq
  }

}
