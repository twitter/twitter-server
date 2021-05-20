package com.twitter.server.handler

import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.{InMemoryStatsReceiver, StatEntry, StatsRegistry}
import com.twitter.server.util.MetricSource
import com.twitter.util.{Await, Duration}
import java.util.concurrent.TimeUnit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}

import scala.collection.mutable
import org.scalatest.funsuite.AnyFunSuite

object MetricTypeQueryHandlerTest {
  // Needs to be in companion object because of Jackson. We use `Set` to avoid ordering issues here.
  case class Response(latched: Boolean, metrics: Set[Metric])
  case class Metric(name: String, kind: String)
}

class MetricTypeQueryHandlerTest extends AnyFunSuite {
  import MetricTypeQueryHandlerTest._

  private[this] val mapper = new ObjectMapper with ScalaObjectMapper {
    registerModule(DefaultScalaModule)
  }

  case class TrialStat(delta: Double, value: Double, metricType: String) extends StatEntry

  trait UnlatchedRegistry extends StatsRegistry {
    val latched = false
    val stats: mutable.Map[String, TrialStat] =
      mutable.Map("foo" -> TrialStat(3, 4, "counter"), "bar" -> TrialStat(3, 4, "gauge"))
  }

  trait LatchedRegistry extends StatsRegistry {
    val latched = true
    val stats: mutable.Map[String, TrialStat] =
      mutable.Map("foo" -> TrialStat(3, 4, "counter"), "bar" -> TrialStat(3, 4, "gauge"))
  }

  val latchedStatsRegistry: LatchedRegistry = new LatchedRegistry() {
    override def apply(): Map[String, StatEntry] = {
      stats.toMap
    }
  }

  val unlatchedStatsRegistry: UnlatchedRegistry = new UnlatchedRegistry() {
    override def apply(): Map[String, StatEntry] = stats.toMap
  }

  val latchedMetricSource = new MetricSource(() => {
    Seq(latchedStatsRegistry)
  })

  val unlatchedMetricSource = new MetricSource(() => {
    Seq(unlatchedStatsRegistry)
  })

  // generate artificial WithHistogramDetails as per HistogramQueryHandlerTest.
  val histos: InMemoryStatsReceiver = {
    val sr = new InMemoryStatsReceiver
    val _ = sr.stat("baz")
    sr
  }

  private[this] val latchedHandler =
    new MetricTypeQueryHandler(latchedMetricSource, details = Some(histos))
  private[this] val unlatchedHandler =
    new MetricTypeQueryHandler(unlatchedMetricSource, details = Some(histos))

  val typeRequestNoArg: Request = Request("http://$HOST:$PORT/admin/exp/metric_metadata")

  val typeRequestWithAnArg: Request = Request("http://$HOST:$PORT/admin/exp/metric_metadata?m=bar")

  val typeRequestWithManyArgs: Request = Request(
    "http://$HOST:$PORT/admin/exp/metric_metadata?m=foo&m=bar")

  val typeRequestWithHisto: Request = Request("http://$HOST:$PORT/admin/exp/metric_metadata?m=baz")

  val typeRequestWithHistoAndNon: Request = Request(
    "http://$HOST:$PORT/admin/exp/metric_metadata?m=foo&m=baz")

  val responseToNoArg: String =
    """
      |   "metrics" : [
      |     {
      |       "name" : "foo",
      |       "kind" : "counter"
      |     },
      |     {
      |       "name" : "bar",
      |       "kind" : "gauge"
      |     },
      |     {
      |       "name" : "baz",
      |       "kind" : "histogram"
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToAnArg: String =
    """
      |   "metrics" : [
      |     {
      |       "name" : "bar",
      |       "kind" : "gauge"
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToManyArgs: String =
    """
      |   "metrics" : [
      |     {
      |       "name" : "foo",
      |       "kind" : "counter"
      |     },
      |     {
      |       "name" : "bar",
      |       "kind" : "gauge"
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToHisto: String =
    """
      |   "metrics" : [
      |     {
      |       "name" : "baz",
      |       "kind" : "histogram"
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToHistoAndNon: String =
    """
      |   "metrics" : [
      |     {
      |       "name" : "foo",
      |       "kind" : "counter"
      |     },
      |     {
      |       "name" : "baz",
      |       "kind" : "histogram"
      |     }
      |   ]
      | }
    """.stripMargin

  val testNameStart = "MetricTypeQueryHandler generates reasonable json for "

  val timeout: Duration = Duration(5, TimeUnit.SECONDS)

  def testCase(latched: Boolean, request: Request): Unit = {
    if (request == typeRequestNoArg) {
      testCase(latched, request, testNameStart + "full set of metrics", responseToNoArg)
    } else if (request == typeRequestWithAnArg) {
      testCase(latched, request, testNameStart + "a single requested metric", responseToAnArg)
    } else if (request == typeRequestWithManyArgs) {
      testCase(latched, request, testNameStart + "requested subset of metrics", responseToManyArgs)
    } else if (request == typeRequestWithHisto) {
      testCase(latched, request, testNameStart + "a single requested histogram", responseToHisto)
    } else if (request == typeRequestWithHistoAndNon) {
      testCase(
        latched,
        request,
        testNameStart + "requested subset of metrics with a histogram",
        responseToHistoAndNon)
    }
  }

  def testCase(
    latched: Boolean,
    request: Request,
    testName: String,
    responseMetrics: String
  ): Unit = {
    if (latched) {
      val responseStart =
        """
          | {
          |   "latched" : true,
        """.stripMargin
      test(testName + " when using latched counters") {
        JsonHelper.assertJsonResponseFor[Response](
          mapper,
          responseStart + responseMetrics,
          Await.result(latchedHandler(request), timeout).contentString)
      }

    } else {
      val responseStart =
        """
          | {
          |   "latched" : false,
        """.stripMargin
      test(testName + " when using unlatched counters") {
        JsonHelper.assertJsonResponseFor[Response](
          mapper,
          responseStart + responseMetrics,
          Await.result(unlatchedHandler(request), timeout).contentString)
      }

    }
  }

  Seq(
    testCase(latched = true, typeRequestNoArg),
    testCase(latched = true, typeRequestWithManyArgs),
    testCase(latched = true, typeRequestWithAnArg),
    testCase(latched = true, typeRequestWithHisto),
    testCase(latched = true, typeRequestWithHistoAndNon),
    testCase(latched = false, typeRequestNoArg),
    testCase(latched = false, typeRequestWithManyArgs),
    testCase(latched = false, typeRequestWithAnArg),
    testCase(latched = false, typeRequestWithHisto),
    testCase(latched = false, typeRequestWithHistoAndNon)
  )
}
