package com.twitter.server.lint

import org.scalatest.funsuite.AnyFunSuite

class DuplicateLoadServiceBindingsTest extends AnyFunSuite {

  private trait TestTrait

  test("no duplicates") {
    assert(DuplicateLoadServiceBindings.issues(Set.empty).isEmpty)
  }

  test("includes the names of the duplicates") {
    val cls = classOf[TestTrait]
    assert(cls.getName == "com.twitter.server.lint.DuplicateLoadServiceBindingsTest$TestTrait")
    val issues = DuplicateLoadServiceBindings.issues(Set(cls))
    assert(issues.head.details.contains(cls.getName))
  }

}
