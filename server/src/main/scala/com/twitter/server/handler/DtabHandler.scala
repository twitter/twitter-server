package com.twitter.server.handler

import com.twitter.finagle.http.{MediaType, Request, Response}
import com.twitter.finagle.{Dtab, Service}
import com.twitter.io.Buf
import com.twitter.server.util.{AdminJsonConverter, HttpUtils}
import com.twitter.util.Future

private object DtabHandler {
  case class Dtabs(dtab: Seq[String])
}

/**
 * Dumps a simple JSON string representation of the current [[com.twitter.finagle.Dtab.base]].
 *
 * From the Dtab docs: A Dtab--short for delegation table--comprises a sequence
 * of delegation rules. Together, these describe how to bind a path to an Addr.
 *
 * {{{
 * {
 *   "dtab": [
 *     "/srv => /srv#/production",
 *     "/srv => /srv#/prod",
 *     "/s => /srv/local",
 *     "/$/inet => /$/nil",
 *     "/zk => /$/nil"
 *   ]
 * }
 * }}}
 *
 * @see [[com.twitter.finagle.Dtab]]
 */
final class DtabHandler extends Service[Request, Response] {
  import DtabHandler._

  def apply(req: Request): Future[Response] =
    HttpUtils.newResponse(contentType = MediaType.Json, content = Buf.Utf8(jsonResponse))

  private[this] def jsonResponse: String = {
    AdminJsonConverter.prettyObjectMapper.writeValueAsString(formattedDtabEntries)
  }

  private[this] def formattedDtabEntries: Dtabs = {
    val dtabs = for (dentry <- Dtab.base) yield { s"${dentry.prefix.show} => ${dentry.dst.show}" }
    Dtabs(dtabs)
  }
}
