package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{MediaType, Request, Response}
import com.twitter.finagle.stats.metadataScopeSeparator
import com.twitter.io.Buf
import com.twitter.server.handler.MetricExpressionHandler.Version
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.{AdminJsonConverter, MetricSchemaSource}
import com.twitter.util.Future

object MetricExpressionHandler {
  private val Version = 0.2
}

class MetricExpressionHandler(source: MetricSchemaSource = new MetricSchemaSource)
    extends Service[Request, Response] {

  def apply(request: Request): Future[Response] = {
    newResponse(
      contentType = MediaType.JsonUtf8,
      content = Buf.Utf8(
        AdminJsonConverter.writeToString(
          Map(
            "@version" -> Version,
            "counters_latched" -> source.hasLatchedCounters,
            "separator_char" -> metadataScopeSeparator(),
            "expressions" -> source.expressionList
          ))
      )
    )
  }
}
