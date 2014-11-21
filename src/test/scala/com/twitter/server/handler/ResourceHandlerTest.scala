package com.twitter.server.handler

import com.twitter.finagle.http
import com.twitter.io.Charsets
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ResourceHandlerTest extends FunSuite {
  test("404") {
    val handler = new ResourceHandler("/", "www")
    val res = Await.result(handler(http.Request("nonexistent.filetype")))
    assert(res.getStatus === http.Status.NotFound)
  }

  test("400") {
    val handler = new ResourceHandler("/", "www")
    val res = Await.result(handler(http.Request("../../illegal")))
    assert(res.getStatus === http.Status.BadRequest)
  }

  test("load js") {
    val handler = new ResourceHandler("/", "www")
    val res = Await.result(handler(http.Request("test.js")))
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "application/javascript;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === "var foo = function() { }")
  }

  test("load css") {
    val handler = new ResourceHandler("/", "www")
    val res = Await.result(handler(http.Request("test.css")))
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "text/css;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === "#foo { color: blue; }")
  }

  test("load bytes") {
    val handler = new ResourceHandler("/", "www")
    val res = Await.result(handler(http.Request("test.raw")))
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "application/octet-stream")
  }
}