package com.twitter.server

import com.twitter.finagle.{Announcer, Announcement, AnnouncerNotFoundException}
import com.twitter.util.Future
import java.net.InetSocketAddress

/**
 * A Twitter specific announcer using a pre-configured set of sub-announcers
 * for each service. This allows for resolution based only on service name:
 * Announcer.announce(addr, "twitter!gizmoduck")
 */
class TwitterAnnouncer extends Announcer {
  val scheme = "twitter"

  private[this] val announcers = List("flag!")

  def announce(addr: InetSocketAddress, name: String) = {
    def tryAnnounce(announcers: List[String]): Future[Announcement] = announcers match {
      case Nil => Future.exception(new AnnouncerNotFoundException(name))
      case r :: rs => Announcer.announce(addr, r) rescue { case _ => tryAnnounce(rs) }
    }
    val announcement = tryAnnounce(announcers map(_ + name))

    announcement rescue { case _ =>
      // TODO: ensure we're not in production
      Announcer.announce(addr, "local!" + name)
    }
  }

}
