package com.twitter.server.handler

import com.twitter.finagle.Dtab
import com.twitter.finagle.http.Request
import com.twitter.server.util.JsonUtils
import com.twitter.util.Await
import org.scalatest.funsuite.AnyFunSuite

class DtabHandlerTest extends AnyFunSuite {
  test("renders json") {
    val handler = new DtabHandler
    val dtab = Dtab.read("/foo=>/bar;/baz=>/biz")

    val oldBase = Dtab.base
    val json =
      try {
        Dtab.base = dtab
        Await.result(handler(Request())).contentString
      } finally {
        Dtab.base = oldBase
      }

    val expected =
      s"""{
    |  "dtab" : [
    |    "/foo => /bar",
    |    "/baz => /biz"
    |  ]
    |}""".stripMargin

    JsonUtils.assertJsonResponse(json, expected)
  }
}
