package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.toggle.{StandardToggleMap, ToggleMap}
import com.twitter.server.util.HttpUtils.newOk
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import scala.collection.mutable

/** package private for testing purposes */
private[handler] object ToggleHandler {

  /**
   * How a `Toggle` is currently configured and in use.
   *
   * @param id corresponds to `Toggle.Metadata.id`
   * @param fraction corresponds to `Toggle.Metadata.fraction`
   * @param description corresponds to `Toggle.Metadata.description`
   */
  case class Current(id: String, fraction: Double, description: Option[String])

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
}

/**
 * Admin UI for seeing details about how the server's
 * `com.twitter.finagle.toggle.Toggles` are configured.
 *
 * Output is currently JSON which looks roughly like:
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
 */
class ToggleHandler(
    registeredLibrariesFn: () => Map[String, ToggleMap])
  extends Service[Request, Response] {

  import ToggleHandler._

  def this() = this(() => StandardToggleMap.registeredLibraries)

  def apply(req: Request): Future[Response] = {
    newOk(jsonResponse)
  }

  private[handler] def jsonResponse: String = {
    JsonConverter.writeToString(toLibraries)
  }

  private[handler] def toLibraries: Libraries = {
    val registered = registeredLibrariesFn()
    Libraries(registered.map { case (name, toggleMap) =>
      val libToggles = toLibraryToggles(toggleMap)
      Library(name, libToggles)
    }.toSeq)
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

    idToComponents.map { case (id, details) =>
      val md = idToMetadata(id)
      LibraryToggle(Current(id, md.fraction, md.description), details)
    }.toSeq
  }

}
