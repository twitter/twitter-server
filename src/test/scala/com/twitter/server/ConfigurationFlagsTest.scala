package com.twitter.server

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import com.twitter.app.App

@RunWith(classOf[JUnitRunner])
class ConfigurationFlagsTest extends FunSuite {
  test("Get set flags when flag is set") (new App {
    val cfg = new ConfigurationFlags(this)
    flag.parse(Array("-com.twitter.finagle.loadbalancer.perHostStats=true"))
    assert(!cfg.setFlags.isEmpty)
    flag.parse(Array("-com.twitter.finagle.loadbalancer.perHostStats=false"))
  })

  test("Get unset flags") (new App {
    val cfg = new ConfigurationFlags(this)
    assert(!cfg.setFlags.isEmpty)
  })
}
