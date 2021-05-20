package com.twitter.server.view

import org.scalatest.funsuite.AnyFunSuite

class CompositeViewTest extends AnyFunSuite {

  class CheckView extends View {
    def render: String = "Check"
  }

  class OneView extends View {
    def render: String = "one"
  }

  class TwoView extends View {
    def render: String = "two"
  }

  private[this] val views = Seq(new CheckView, new OneView, new TwoView)

  test("CompositeView renders multiple views in order") {
    val compView = new CompositeView(views)
    val result = compView.render
    assert(result == "Checkonetwo")
  }

  test("CompositeView separator is configurable") {
    val compView = new CompositeView(views, " ")
    val result = compView.render
    assert(result == "Check one two")
  }
}
