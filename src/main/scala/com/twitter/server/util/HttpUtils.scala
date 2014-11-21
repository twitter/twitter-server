package com.twitter.server.util

import com.twitter.finagle.http.{Status, Version}
import com.twitter.finagle.netty3.BufChannelBuffer
import com.twitter.finagle.Service
import com.twitter.io.{Buf, BufReader, Charsets}
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._
import scala.collection.JavaConverters._
import scala.collection.{Map, Seq}

/**
 * Contain all netty3 http types. We avoid using c.t.f.http.{Request, Response}
 * directly because in the absence of an Http{Muxer, Server} written in
 * terms of them, we have to jump through hoops to convert between the types.
 */
private[server] object HttpUtils {
  type Request = HttpRequest
  type Response = HttpResponse

  /**
   * Creates a http [[com.twitter.finagle.Service]] which attempts a
   * request on `svc0` and falls back to `svc1` if the request
   * fails with a 404.
   */
  def combine(
    svc0: Service[Request, Response],
    svc1: Service[Request, Response]
  ): Service[Request, Response] =
    new Service[Request, Response] {
      def apply(req: Request) = svc0(req) flatMap { rep =>
        if (rep.getStatus == Status.NotFound) svc1(req)
        else Future.value(rep)
      }
    }

  /**
   * Uses basic heuristics to determine if the request is coming from a web browser.
   */
  def isWebBrowser(req: Request): Boolean = {
    val ua = req.headers.get(HttpHeaders.Names.USER_AGENT)
    if (ua == null) false else ua.startsWith("Mozilla")
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
    version: HttpVersion = Version.Http11,
    status: HttpResponseStatus = Status.Ok,
    headers: Iterable[(String, Object)] = Seq(),
    contentType: String,
    content: Buf
  ): Future[Response] = {
    val response = new DefaultHttpResponse(version, status)
    response.setContent(BufChannelBuffer(content))
    for ((k, v) <- headers) response.headers.set(k, v)
    response.headers.set(HttpHeaders.Names.CONTENT_LANGUAGE , "en")
    response.headers.set(HttpHeaders.Names.CONTENT_LENGTH, content.length)
    response.headers.set(HttpHeaders.Names.CONTENT_TYPE, contentType)
    Future.value(response)
  }

  /** Returns a new 200 OK with contents set to `msg` */
  def newOk(msg: String): Future[Response] =
    newResponse(
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Utf8(msg)
    )

  /** Returns a new 404 with contents set to `msg` */
  def new404(msg: String): Future[Response] =
    newResponse(
      status = Status.NotFound,
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Utf8(msg)
    )

  /** Parse uri into (path, params) */
  def parse(uri: String): (String, Map[String, Seq[String]]) = {
    val qsd = new QueryStringDecoder(uri)
    val params = qsd.getParameters.asScala.mapValues { _.asScala.toSeq }
    (qsd.getPath, params)
  }
}