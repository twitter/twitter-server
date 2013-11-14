package com.twitter.server

import com.twitter.app.Flags
import com.twitter.finagle.{Announcer, Announcement, Resolver}
import com.twitter.util.{Await, Closable, Future, RandomSocket}
import java.net.InetSocketAddress
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

object TestTwitterServer extends TwitterServer {
  def main() {}
}

@RunWith(classOf[JUnitRunner])
class TwitterServerTest extends FunSuite {
  test("TwitterServer.main() is executes without error") {
    TestTwitterServer.main()
  }
}
