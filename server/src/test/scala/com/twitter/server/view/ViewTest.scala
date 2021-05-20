package com.twitter.server.view

import org.scalatest.funsuite.AnyFunSuite

class ViewTest extends AnyFunSuite {

  class TestView extends View {
    def render: String = "Hello"
  }

  test("View provides a render method") {
    val view: View = new TestView
    val result = view.render
    assert(result == "Hello")
  }

}
