package com.twitter.server.util

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.Buf

object JsonConverter {
  private[this] val writer = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val printer = new DefaultPrettyPrinter
    printer.indentArraysWith(new DefaultPrettyPrinter.Lf2SpacesIndenter)
    mapper.writer(printer)
  }

  def apply(obj: Any): Response = {
    val msg = writer.writeValueAsString(obj)
    val response = Response(Version.Http11, Status.Ok)
    response.content = Buf.Utf8(msg)
    response
  }

  def writeToString(obj: Any): String = {
    writer.writeValueAsString(obj)
  }
}
