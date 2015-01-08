package com.twitter.server

import com.twitter.finagle.{Announcer, Announcement, Resolver}
import com.twitter.util.{Await, Future}
import java.net.{InetAddress, InetSocketAddress}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

case class TestAnnouncement(addr: InetSocketAddress, target: String) extends Announcement {
  def unannounce() = Future.Done
}

class TestAnnouncer extends Announcer {
  val scheme = "test"
  def announce(addr: InetSocketAddress, target: String) =
    Future.value(TestAnnouncement(addr, target))
}

// Called InternalResolverTest to avoid conflict with twitter-server
@RunWith(classOf[JUnitRunner])
class ResolverTest extends FunSuite {
  test("resolvers resolve from the main resolver") {
    resolverMap.let(Map("foo" -> ":8080")) {
      Resolver.eval("flag!foo") // doesn't throw.
    }
  }

  test("announcers resolve from the main announcer") {
    val addr = new InetSocketAddress(InetAddress.getLoopbackAddress, 0)

    announcerMap.let(Map("foo" -> "test!127.0.0.1:80")) {
      // checks for non-exceptional
      Await.result(Announcer.announce(addr, "flag!foo"))
    }
  }
}
