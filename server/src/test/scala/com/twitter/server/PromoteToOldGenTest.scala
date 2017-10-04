package com.twitter.server

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PromoteToOldGenTest extends FunSuite {

  test("beforeServing does nothing if flag is disabled") {
    val promote = new Lifecycle.PromoteToOldGen(Nil)
    assert(!promote.promoted)
    promoteBeforeServing.let(false) {
      promote.beforeServing()
      assert(!promote.promoted)
    }
  }

  test("beforeServing runs if flag is enabled") {
    val promote = new Lifecycle.PromoteToOldGen(Nil)
    promoteBeforeServing.let(true) {
      promote.beforeServing()
      assert(promote.promoted)
    }
  }

  test("beforeServing does not run if ExplicitGCInvokesConcurrent") {
    val promote = new Lifecycle.PromoteToOldGen(Seq("-XX:+ExplicitGCInvokesConcurrent"))
    promoteBeforeServing.let(true) {
      promote.beforeServing()
      assert(!promote.promoted)
    }
  }

}
