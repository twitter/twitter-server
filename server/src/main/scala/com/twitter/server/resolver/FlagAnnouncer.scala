package com.twitter.server

import com.twitter.app.GlobalFlag
import com.twitter.finagle.{Announcer, Announcement, AnnouncerNotFoundException}
import com.twitter.util.Future
import java.net.InetSocketAddress

object announcerMap
    extends GlobalFlag[Map[String, String]](
      Map.empty,
      "A list mapping service names to announcers (gizmoduck=zk!/gizmoduck)"
    )

class FlagAnnouncer extends Announcer {
  val scheme = "flag"

  private[this] def announcers = announcerMap()

  def announce(addr: InetSocketAddress, name: String): Future[Announcement] = {
    announcers.get(name) match {
      case Some(target) => Announcer.announce(addr, target)
      case None => Future.exception(new AnnouncerNotFoundException(name))
    }
  }
}
