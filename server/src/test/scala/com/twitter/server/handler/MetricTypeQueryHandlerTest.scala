package com.twitter.server.handler

import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.{InMemoryStatsReceiver, StatEntry, StatsRegistry}
import com.twitter.server.util.MetricSource
import com.twitter.util.{Await, Duration}
import java.util.concurrent.TimeUnit
import org.scalatest.FunSuite
import scala.collection.mutable

class MetricTypeQueryHandlerTest extends FunSuite {

  case class TrialStat(delta: Double, value: Double, metricType: String) extends StatEntry

  trait UnlatchedRegistry extends StatsRegistry {
    val latched = false
    val stats = mutable.Map("foo" -> TrialStat(3, 4, "counter"), "bar" -> TrialStat(3, 4, "gauge"))
  }

  trait LatchedRegistry extends StatsRegistry {
    val latched = true
    val stats = mutable.Map("foo" -> TrialStat(3, 4, "counter"), "bar" -> TrialStat(3, 4, "gauge"))
  }

  val latchedStatsRegistry = new LatchedRegistry() {
    override def apply(): Map[String, StatEntry] = stats.toMap
  }

  val unlatchedStatsRegistry = new UnlatchedRegistry() {
    override def apply(): Map[String, StatEntry] = stats.toMap
  }

  val latchedMetricSource = new MetricSource(
    () => {
      Seq(latchedStatsRegistry)
    }
  )

  val unlatchedMetricSource = new MetricSource(
    () => {
      Seq(unlatchedStatsRegistry)
    }
  )

  // generate artificial WithHistogramDetails as per HistogramQueryHandlerTest.
  val histos = {
    val sr = new InMemoryStatsReceiver
    val myStat = sr.stat("baz")
    sr
  }

  private[this] val latchedHandler =
    new MetricTypeQueryHandler(latchedMetricSource, details = Some(histos))
  private[this] val unlatchedHandler =
    new MetricTypeQueryHandler(unlatchedMetricSource, details = Some(histos))

  val typeRequestNoArg = Request("http://$HOST:$PORT/admin/exp/metric_metadata")

  val typeRequestWithAnArg = Request("http://$HOST:$PORT/admin/exp/metric_metadata?m=bar")

  val typeRequestWithManyArgs = Request("http://$HOST:$PORT/admin/exp/metric_metadata?m=foo&m=bar")

  val typeRequestWithHisto = Request("http://$HOST:$PORT/admin/exp/metric_metadata?m=baz")

  val typeRequestWithHistoAndNon = Request(
    "http://$HOST:$PORT/admin/exp/metric_metadata?m=foo&m=baz")

  val responseToNoArg =
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

  val responseToAnArg =
    """
      |   "metrics" : [
      |     {
      |       "name" : "bar",
      |       "kind" : "gauge"
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToManyArgs =
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

  val responseToHisto =
    """
      |   "metrics" : [
      |     {
      |       "name" : "baz",
      |       "kind" : "histogram"
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToHistoAndNon =
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

  val timeout = Duration(5, TimeUnit.SECONDS)

  // NOTE: these tests assume a specific iteration order over the registries
  // and HashMaps which IS NOT a guarantee. should these tests begin to fail
  // due to that, we will need a more robust approach to validation.
  private[this] def assertJsonResponse(actual: String, expected: String) = {
    assert(stripWhitespace(actual) == stripWhitespace(expected))
  }

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

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
        assertJsonResponse(
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
        assertJsonResponse(
          responseStart + responseMetrics,
          Await.result(unlatchedHandler(request), timeout).contentString)
      }

    }
  }

  Seq(
    testCase(true, typeRequestNoArg),
    testCase(true, typeRequestWithManyArgs),
    testCase(true, typeRequestWithAnArg),
    testCase(true, typeRequestWithHisto),
    testCase(true, typeRequestWithHistoAndNon),
    testCase(false, typeRequestNoArg),
    testCase(false, typeRequestWithManyArgs),
    testCase(false, typeRequestWithAnArg),
    testCase(false, typeRequestWithHisto),
    testCase(false, typeRequestWithHistoAndNon)
  )
}
