package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.{Dtab, Announcer, Service}
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future

/**
 * Dumps a simple string representation of the current Dtab.
 *
 * From the Dtab docs: A Dtab--short for delegation table--comprises a sequence
 * of delegation rules. Together, these describe how to bind a
 * path to an Addr.
 */
class DtabHandler extends Service[Request, Response] {
  def apply(req: Request): Future[Response] =
    newOk(Dtab.base.toString)
}
