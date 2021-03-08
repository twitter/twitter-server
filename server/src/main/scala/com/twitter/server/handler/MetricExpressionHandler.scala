package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{MediaType, Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.{JsonConverter, MetricSchemaSource}
import com.twitter.util.Future

class MetricExpressionHandler(source: MetricSchemaSource = new MetricSchemaSource)
    extends Service[Request, Response] {

  def apply(request: Request): Future[Response] = {
    val expressions = source.expressionList
    newResponse(
      contentType = MediaType.JsonUtf8,
      content = Buf.Utf8(
        JsonConverter.writeToString(
          Map("@version" -> 0.1, "expressions" -> expressions)
        )
      )
    )
  }
}
