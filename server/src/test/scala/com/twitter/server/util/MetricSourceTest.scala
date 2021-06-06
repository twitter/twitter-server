package com.twitter.server.util

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.stats.{StatEntry, StatsRegistry}
import com.twitter.util.{Await, Future, FuturePool, Time}
import java.util.concurrent.{CountDownLatch, Executors}
import org.scalatest.funsuite.AnyFunSuite

private[server] object MetricSourceTest {
  class Ctx {
    case class Entry(delta: Double, value: Double, metricType: String) extends StatEntry
    private[twitter] var underlying = Map[String, StatEntry]()
    private[twitter] var refreshes = 0
    val sr = new StatsRegistry {
      def apply() = underlying;
      override val latched: Boolean = false
    }
    val registry = { () =>
      refreshes += 1
      Seq(sr)
    }
    val source = new MetricSource(registry, 1.second)
  }
}

class MetricSourceTest extends AnyFunSuite {
  import MetricSourceTest._

  test("get") {
    Time.withCurrentTimeFrozen { tc =>
      val ctx = new Ctx
      import ctx._

      underlying = Map("clnt/foo/requests" -> Entry(0.0, 10.0, "counter"))
      assert(source.get("clnt/foo/requests") == None)

      tc.advance(1.second)
      assert(source.get("clnt/foo/requests").get.delta == 0.0)
      assert(source.get("clnt/foo/requests").get.value == 10.0)
      assert(refreshes == 1)
    }
  }

  test("contains") {
    Time.withCurrentTimeFrozen { tc =>
      val ctx = new Ctx
      import ctx._

      underlying = Map("clnt/foo/requests" -> Entry(0.0, 0.0, "counter"))
      assert(source.contains("clnt/foo/requests") == false)

      tc.advance(1.second)
      assert(source.contains("clnt/foo/requests") == true)
      assert(refreshes == 1)
    }
  }

  test("keySet") {
    Time.withCurrentTimeFrozen { tc =>
      val ctx = new Ctx
      import ctx._

      underlying = Map(
        "clnt/foo/requests" -> Entry(0.0, 0.0, "counter"),
        "clnt/foo/success" -> Entry(0.0, 0.0, "counter"))
      assert(source.keySet == Set.empty[String])
      tc.advance(1.second)
      assert(source.keySet == Set("clnt/foo/requests", "clnt/foo/success"))
      assert(refreshes == 1)
    }
  }

  test("only one thread refreshes") {
    Time.withCurrentTimeFrozen { tc =>
      val ctx = new Ctx
      import ctx._
      val exec = Executors.newCachedThreadPool()
      val pool = FuturePool(exec)

      try {
        underlying = Map("clnt/foo/requests" -> Entry(0.0, 10.0, "counter"))
        assert(source.get("clnt/foo/requests") == None)

        tc.advance(1.second)

        val cdl = new CountDownLatch(1)
        val fut =
          Future.collect(List.fill(20)(pool {
            cdl.await()
            source.get("clnt/foo/requests")
          }))
        cdl.countDown()
        Await.result(fut, 1.second)

        assert(source.get("clnt/foo/requests").get.delta == 0.0)
        assert(source.get("clnt/foo/requests").get.value == 10.0)
        assert(refreshes == 1)
      } finally {
        exec.shutdown()
      }
    }
  }
}
