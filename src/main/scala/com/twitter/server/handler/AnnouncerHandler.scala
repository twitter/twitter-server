package com.twitter.server.handler

import com.twitter.finagle.{Announcer, Service}
import com.twitter.util.Future
import com.twitter.server.util.HttpUtils._

class AnnouncerHandler extends Service[Request, Response] {
  def apply(req: Request): Future[Response] = {
    val msg = (Announcer.announcements map { case (addr, targets) =>
      val indented = targets.zipWithIndex map { case (t, i) => (" " * i * 2) + t }
      addr.toString + "\n" + indented.mkString("\n")
    }).mkString("\n\n")

    newOk(msg)
  }
}
