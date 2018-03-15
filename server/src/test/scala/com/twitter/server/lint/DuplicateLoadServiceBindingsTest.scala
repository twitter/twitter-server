package com.twitter.server.lint

import org.scalatest.FunSuite

class DuplicateLoadServiceBindingsTest extends FunSuite {

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
