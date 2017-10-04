package com.twitter.server

import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.http.{Request, Response}
import com.twitter.server.handler.HistogramQueryHandler
import com.twitter.util.{Await, Future}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HistogramQueryHandlerTest extends FunSuite {
  test("histograms.json works with no stats") {
    val sr = new InMemoryStatsReceiver
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms.json")
    val resp: Future[Response] = handler(req)
    assert(Await.result(resp).contentString.contains("{ }"))
  }

  test("histograms.json works with stats") {
    val sr = new InMemoryStatsReceiver
    val myStat = sr.stat("my", "cool", "stat")
    myStat.add(5)
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms.json")
    val resp: Future[Response] = handler(req)
    val result = Await.result(resp).contentString

    assert(result.contains("my/cool/stat"))
    assert(result.contains(raw""""lowerLimit" : 5,"""))
    assert(result.contains(raw""""upperLimit" : 6,"""))
    assert(result.contains(raw""""count" : 1"""))
  }

  test("histograms work on a non-existing stat") {
    val sr = new InMemoryStatsReceiver
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms?h=my/cool/stat&fmt=pdf")
    val resp: Future[Response] = handler(req)
    assert(Await.result(resp).contentString.contains("not a valid histogram."))
  }

  test("histograms work with a stat") {
    val sr = new InMemoryStatsReceiver
    val myStat = sr.stat("my", "cool", "stat")
    myStat.add(0)
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms?h=my/cool/stat&fmt=pdf")
    val resp: Future[Response] = handler(req)
    val result = Await.result(resp).contentString
    assert(result.contains(raw""""lowerLimit" : 0,"""))
    assert(result.contains(raw""""upperLimit" : 1,"""))
    assert(result.contains(raw""""percentage" : 1.0"""))
  }
}
