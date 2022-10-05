package com.twitter.server.util

import com.twitter.finagle.http.MediaType
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.http.Version
import com.twitter.io.Buf
import com.twitter.util.Future
import io.netty.handler.codec.http.QueryStringDecoder

private[server] object HttpUtils {

  /**
   * Determines (by examine the "Accept" header on `req`) if the client accepts given `contentType`.
   *
   * Note that this method simply checks if the given `contentType` is a substring of the "Accept"
   * header.
   */
  def accepts(req: Request, contentType: String): Boolean =
    req.headerMap.get("Accept").exists(_.contains(contentType))

  /**
   * Determines if the client expects to receive `text/html` content type.
   */
  def expectsHtml(req: Request): Boolean = {
    val decoder = new QueryStringDecoder(req.uri)
    decoder.path().endsWith(".html") || accepts(req, MediaType.Html)
  }

  /**
   * Determines if the client expects to receive `application/json` content type.
   */
  def expectsJson(req: Request): Boolean = {
    val decoder = new QueryStringDecoder(req.uri)
    decoder.path().endsWith(".json") || accepts(req, MediaType.Json)
  }

  /**
   * Create an http response with the give params.
   * Some of the headers like content length are inferred.
   *
   * @param version The HTTP version for this response.
   * @param status The HTTP status code.
   * @param headers Additional headers to include in the response.
   * @param contentType The content type header, defaults to text/plain
   * @param content The content body of the HTTP response.
   */
  def newResponse(
    version: Version = Version.Http11,
    status: Status = Status.Ok,
    headers: Iterable[(String, Object)] = Seq(),
    contentType: String,
    content: Buf
  ): Future[Response] = {
    val response = Response(version, status)
    response.content = content
    for ((k, v) <- headers) response.headerMap.add(k, v.toString)
    response.headerMap.add("Content-Language", "en")
    response.headerMap.add("Content-Length", content.length.toString)
    response.headerMap.add("Content-Type", contentType)
    Future.value(response)
  }

  /** Returns a new 200 OK with contents set to `msg` */
  def newOk(msg: String, contentType: String = "text/plain;charset=UTF-8"): Future[Response] =
    newResponse(
      contentType = contentType,
      content = Buf.Utf8(msg)
    )

  /** Returns a new 404 with contents set to `msg` */
  def new404(msg: String, contentType: String = "text/plain;charset=UTF-8"): Future[Response] =
    newResponse(
      status = Status.NotFound,
      contentType = contentType,
      content = Buf.Utf8(msg)
    )

}
