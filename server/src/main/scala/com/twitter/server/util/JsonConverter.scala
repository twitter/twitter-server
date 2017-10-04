package com.twitter.server.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.util.{DefaultIndenter, DefaultPrettyPrinter}
import com.fasterxml.jackson.databind.{MappingJsonFactory, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.Buf

object JsonConverter {
  private[this] val writer = {
    val factory = new MappingJsonFactory()
    factory.disable(JsonFactory.Feature.USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING)
    val mapper = new ObjectMapper(factory).registerModule(DefaultScalaModule)
    val printer = new DefaultPrettyPrinter
    printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
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
