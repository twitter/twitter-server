package com.twitter.server.view

import org.scalatest.FunSuite

class ViewTest extends FunSuite {

  class TestView extends View {
    def render: String = "Hello"
  }

  test("View provides a render method") {
    val view: View = new TestView
    val result = view.render
    assert(result == "Hello")
  }

}
