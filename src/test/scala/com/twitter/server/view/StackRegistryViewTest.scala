package com.twitter.server.view

import com.twitter.finagle.util.StackRegistry
import com.twitter.finagle.{Stack, StackBuilder, Stackable}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

private[server] object StackRegistryViewTest {
  case class Incr(incrementBy: Int)
  implicit object Incr extends Stack.Param[Incr] {
    val default = Incr(50)
  }

  object Foo {
    def module: Stackable[Int => Int] =
      new Stack.Module1[Incr, Int => Int] {
        val role = Stack.Role("foo")
        val description = "adds incr to every value."
        def make(incr: Incr, next: Int => Int) = { i =>
          val Incr(j) = incr
          next(i+j)
        }
      }
  }

  object Bar {
    def module: Stackable[Int => Int] =
      new Stack.Module0[Int => Int] {
        val role = Stack.Role("bar")
        val description = "adds 2 to every value."
        def make(next: Int => Int) = { i => next(i+2) }
      }
  }

  object Baz {
    def module: Stackable[Int => Int] =
      new Stack.Module0[Int => Int] {
        val role = Stack.Role("baz")
        val description = "adds 3 to every value."
        def make(next: Int => Int) = { i => next(i+3) }
      }
  }

  val sb = new StackBuilder(Stack.Leaf[Int => Int](Stack.Role("identity"), identity[Int]_))
  sb.push(Baz.module)
  sb.push(Bar.module)
  sb.push(Foo.module)
  val stk = sb.result
  val prms = Stack.Params.empty
}

@RunWith(classOf[JUnitRunner])
class StackRegistryViewTest extends FunSuite {
  import StackRegistryViewTest._

  test("render stack") {
    val entry0 = StackRegistry.Entry("test", "", stk, prms)
    val res0 = StackRegistryView.render(entry0, None)
    assert(res0.contains("foo"))
    assert(res0.contains("bar"))
    assert(res0.contains("baz"))

    val entry1 = StackRegistry.Entry("test", "", stk.remove(Stack.Role("baz")), prms)
    val res1 = StackRegistryView.render(entry1, None)
    assert(res1.contains("foo"))
    assert(res1.contains("bar"))
    assert(!res1.contains("baz"))
  }

  test("render params") {
    val entry0 = StackRegistry.Entry("test", "", stk, prms)
    val res0 = StackRegistryView.render(entry0, None)
    assert(res0.contains("incrementBy"))
    assert(res0.contains("50"))

    val entry1 = StackRegistry.Entry("test", "", stk, prms + Incr(10))
    val res1 = StackRegistryView.render(entry1, None)
    assert(res1.contains("incrementBy"))
    assert(res1.contains("10"))
  }
}