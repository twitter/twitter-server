package com.twitter.server

import com.twitter.util.registry.{GlobalRegistry, SimpleRegistry}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EngRegistryTest extends FunSuite {

  def isRegistered(key: String): Boolean = {
    GlobalRegistry.get.exists(_.key.headOption.exists(_ == key))
  }

  test("TwitterServer registers environment variables") {
    GlobalRegistry.withRegistry(new SimpleRegistry) {
      assert(!isRegistered("env"))
      (new TestTwitterServer).main(args = Array.empty[String])
      assert(isRegistered("env"))
    }
  }

  test("TwitterServer registers system properties") {
    GlobalRegistry.withRegistry(new SimpleRegistry) {
      assert(!isRegistered("system.properties"))
      (new TestTwitterServer).main(args = Array.empty[String])
      assert(isRegistered("system.properties"))
    }
  }
}
