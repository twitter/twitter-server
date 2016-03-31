package com.twitter.server

import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.http.{Request, Response}
import com.twitter.server.handler.HistogramQueryHandler
import com.twitter.util.{Await, Future}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HistogramQueryHandlerTest extends FunSuite  {
  test("histograms.json works with no stats") {
    val sr = new InMemoryStatsReceiver
    val handler = new HistogramQueryHandler(sr)

    val reqAllHistos = Request("/admin/histograms.json")
    val respAllHistos: Future[Response] = handler(reqAllHistos)
    assert(Await.result(respAllHistos).contentString.contains("{ }"))
  }

  test("histograms.json works with stats") {
    val sr = new InMemoryStatsReceiver
    val myStat = sr.stat("my", "cool", "stat")
    myStat.add(5)
    val handler = new HistogramQueryHandler(sr)

    val reqAllHistos = Request("/admin/histograms.json")
    val respAllHistos: Future[Response] = handler(reqAllHistos)
    val result = Await.result(respAllHistos).contentString
    assert(result.contains("my/cool/stat"))
    assert(result.contains(raw""""lowerLimit" : 5,"""))
    assert(result.contains(raw""""upperLimit" : 6,"""))
    assert(result.contains(raw""""count" : 1"""))
  }
}
