package com.twitter.server

import com.fasterxml.jackson.databind.{ObjectMapper, PropertyNamingStrategy}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.ScalaObjectMapper
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.stats.{BucketAndCount, InMemoryStatsReceiver}
import com.twitter.server.handler.HistogramQueryHandler
import com.twitter.util.{Await, Future}
import org.scalatest.funsuite.AnyFunSuite

class HistogramQueryHandlerTest extends AnyFunSuite {
  def await[A](f: Future[A]): A = Await.result(f, 5.seconds)
  private[this] val mapper = new ObjectMapper with ScalaObjectMapper {
    registerModule(DefaultScalaModule)
  }
  mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)

  test("histograms.json works with no stats") {
    val sr = new InMemoryStatsReceiver
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms.json")
    val resp: Future[Response] = handler(req)
    assert(await(resp).contentString.contains("{ }"))
  }

  test("histograms.json works with stats") {
    val sr = new InMemoryStatsReceiver
    val myStat = sr.stat("my", "cool", "stat")
    myStat.add(5)
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms.json")
    val resp: Future[Response] = handler(req)
    val result = await(resp).contentString

    val map = mapper.readValue[Map[String, Seq[BucketAndCount]]](result)
    map("my/cool/stat") match {
      case Seq(BucketAndCount(lowerLimit, upperLimit, count)) =>
        assert(lowerLimit == 5)
        assert(upperLimit == 6)
        assert(count == 1)
    }

    assert(result.contains("my/cool/stat"))
    assert(result.contains(raw""""lower_limit" : 5,"""))
    assert(result.contains(raw""""upper_limit" : 6,"""))
    assert(result.contains(raw""""count" : 1"""))
  }

  test("histograms work on a non-existing stat") {
    val sr = new InMemoryStatsReceiver
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms?h=my/cool/stat&fmt=pdf")
    val resp: Future[Response] = handler(req)
    assert(await(resp).contentString.contains("not a valid histogram."))
  }

  test("histograms work with a stat") {
    val sr = new InMemoryStatsReceiver
    val myStat = sr.stat("my", "cool", "stat")
    myStat.add(0)
    val handler = new HistogramQueryHandler(sr)

    val req = Request("/admin/histograms?h=my/cool/stat&fmt=pdf")
    val resp: Future[Response] = handler(req)
    val result = await(resp).contentString
    val map = mapper.readValue[Map[String, Seq[HistogramQueryHandler.BucketAndPercentage]]](result)
    map("my/cool/stat") match {
      case Seq(HistogramQueryHandler.BucketAndPercentage(lowerLimit, upperLimit, percentage)) =>
        assert(lowerLimit == 0)
        assert(upperLimit == 1)
        assert(percentage == 1.0)
    }
    assert(result.contains(raw""""lower_limit" : 0,"""))
    assert(result.contains(raw""""upper_limit" : 1,"""))
    assert(result.contains(raw""""percentage" : 1.0"""))
  }
}
