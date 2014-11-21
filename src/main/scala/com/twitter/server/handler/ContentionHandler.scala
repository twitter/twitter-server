package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.jvm.ContentionSnapshot
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future

class ContentionHandler extends Service[Request, Response] {
  private[this] val snapshotter = new ContentionSnapshot
  def apply(req: Request): Future[Response] = {
    val snap = snapshotter.snap
    val deadlockMsg = if (snap.deadlocks.isEmpty) "" else {
      "DEADLOCKS:\n\n%s\n\n".format(snap.deadlocks.mkString("\n\n"))
    }

    val msg = "%sBlocked:\n%s\n\nLock Owners:\n%s".format(
      deadlockMsg,
      snap.blockedThreads.mkString("\n"),
      snap.lockOwners.mkString("\n"))

    newOk(msg)
  }
}
