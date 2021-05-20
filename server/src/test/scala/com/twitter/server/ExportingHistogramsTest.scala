package com.twitter.server

import com.twitter.finagle.stats.BucketAndCount
import org.scalatest.funsuite.AnyFunSuite

class ExportingHistogramsTest extends AnyFunSuite {
  import handler.HistogramQueryHandler.{pdf, cdf, BucketAndPercentage}

  val empty = Seq.empty
  val single = Seq(BucketAndCount(0, 1, 5))
  val corner = Seq(BucketAndCount(0, 1, 1), BucketAndCount(2137204091, Int.MaxValue, 1))
  val larger = Seq(
    BucketAndCount(0, 1, 3),
    BucketAndCount(1, 2, 1),
    BucketAndCount(100, 102, 6),
    BucketAndCount(2137204091, Int.MaxValue, 3)
  )

  test("pdf works on empty") {
    assert(pdf(empty) == Seq.empty)
  }

  test("pdf works on single entry") {
    assert(pdf(single) == Seq(BucketAndPercentage(0, 1, 1.0f)))
  }

  test("pdf works on corner buckets") {
    assert(
      pdf(corner) == Seq(
        BucketAndPercentage(0, 1, 0.5f),
        BucketAndPercentage(2137204091, Int.MaxValue, 0.5f)
      )
    )
  }

  test("pdf works on larger sequence") {
    assert(
      pdf(larger) == Seq(
        BucketAndPercentage(0, 1, 3.0f / 13),
        BucketAndPercentage(1, 2, 1.0f / 13),
        BucketAndPercentage(100, 102, 6.0f / 13),
        BucketAndPercentage(2137204091, Int.MaxValue, 3.0f / 13)
      )
    )
  }

  test("cdf works on empty") {
    assert(cdf(empty) == Seq.empty)
  }

  test("cdf works on single entry") {
    assert(cdf(single) == Seq(BucketAndPercentage(0, 1, 1.0f)))
  }

  test("cdf works on corner buckets") {
    assert(
      cdf(corner) == Seq(
        BucketAndPercentage(0, 1, 0.5f),
        BucketAndPercentage(2137204091, Int.MaxValue, 1.0f)
      )
    )
  }

  test("cdf works on larger sequence") {
    assert(
      cdf(larger) == Seq(
        BucketAndPercentage(0, 1, 3.0f / 13),
        BucketAndPercentage(1, 2, 4.0f / 13),
        BucketAndPercentage(100, 102, 10.0f / 13),
        BucketAndPercentage(2137204091, Int.MaxValue, 1.0f)
      )
    )
  }

}
