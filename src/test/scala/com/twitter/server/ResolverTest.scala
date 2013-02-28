package com.twitter.server

import com.twitter.app.Flags
import com.twitter.finagle.{Announcer, Resolver}
import com.twitter.util.{Await, RandomSocket}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ResolverTest extends FunSuite {
  test("resolvers resolve from the main resolver") {
    val flag = new Flags("my", includeGlobal=true)
    flag.parse(Array("-com.twitter.server.resolverMap", "foo=:8080"))

    assert(Resolver.resolve("flag!foo").isReturn)
    assert(Resolver.resolve("twitter!foo").isReturn)
  }

  test("announcers resolve from the main announcer") {
    val addr = RandomSocket()
    val flag = new Flags("my", includeGlobal=true)
    flag.parse(Array("-com.twitter.server.announcerMap", "foo=local!foo"))

    assert(Await.ready(Announcer.announce(addr, "flag!foo")).isReturn)
    assert(Await.ready(Announcer.announce(addr, "twitter!foo")).isReturn)
  }
}
