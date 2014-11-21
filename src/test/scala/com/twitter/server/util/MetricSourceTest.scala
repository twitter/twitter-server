package com.twitter.server.util

import com.twitter.conversions.time._
import com.twitter.finagle.stats.{StatsRegistry, StatEntry}
import com.twitter.util.Time
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

private[server] object MetricSourceTest {
  class Ctx {
    case class Entry(delta: Double, value: Double) extends StatEntry
    var underlying = Map[String, StatEntry]()
    val sr = new StatsRegistry { def apply() = underlying }
    val registry = { () => Seq(sr) }
    val source = new MetricSource(registry, 1.second)
  }
}

@RunWith(classOf[JUnitRunner])
class MetricSourceTest extends FunSuite {
  import MetricSourceTest._

  test("get") {
    Time.withCurrentTimeFrozen { tc =>
      val ctx = new Ctx
      import ctx._

      underlying = Map("clnt/foo/requests" -> Entry(0.0, 10.0))
      assert(source.get("clnt/foo/requests") === None)

      tc.advance(1.second)
      assert(source.get("clnt/foo/requests").get.delta === 0.0)
      assert(source.get("clnt/foo/requests").get.value === 10.0)
    }
  }

  test("contains") {
    Time.withCurrentTimeFrozen { tc =>
      val ctx = new Ctx
      import ctx._

      underlying = Map("clnt/foo/requests" -> Entry(0.0, 0.0))
      assert(source.contains("clnt/foo/requests") === false)

      tc.advance(1.second)
      assert(source.contains("clnt/foo/requests") === true)
    }
  }

  test("keySet") {
    Time.withCurrentTimeFrozen { tc =>
      val ctx = new Ctx
      import ctx._

      underlying = Map(
        "clnt/foo/requests" -> Entry(0.0, 0.0),
        "clnt/foo/success" -> Entry(0.0, 0.0))
      assert(source.keySet === Set.empty[String])
      tc.advance(1.second)
      assert(source.keySet === Set("clnt/foo/requests", "clnt/foo/success"))
    }
  }
}