package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.MediaType
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.MetricSchemaSource
import com.twitter.util.Future

/**
 * A handler which returns all de-duplicated namespaces carried in the
 * requests to a Twitter Server instance.
 *
 * Example Request:
 *   http://$HOST:$PORT/admin/namespaces
 *
 * Response:
 * {
   "@version" : 1.0,
   "namespaces" : [
      { "name": "my/namespace/0" },
      { "name": "my/namespace/1" },
      { "name": "my/namespace/2" },
      { "name": "my/namespace/3" }
    ]}
 */
class NamespaceHandler(source: MetricSchemaSource = new MetricSchemaSource)
    extends Service[Request, Response] {

  def apply(req: Request): Future[Response] = {
    newResponse(
      contentType = MediaType.JsonUtf8,
      content = Buf.Utf8(
        AdminJsonConverter.writeToString(
          Map(
            "@version" -> 1.0,
            "namespaces" -> buildNamespace(source.namespaces)
          )))
    )
  }

  private[this] def buildNamespace(namespaces: Set[String]) =
    source.namespaces.map { namespace => Map("name" -> namespace) }

}
