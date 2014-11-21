package com.twitter.server.handler

import com.twitter.finagle.http.Status
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.io.Charsets
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import java.nio.charset.Charset
import scala.io.Source

/**
 * A handler designed to serve static resources accessible via
 * java's `Class#getResourceAsStream` over http. The serving directory
 * is configurable and set to `www` by default.
 */
class ResourceHandler(basePath: String, servingDir: String = "www")
  extends Service[Request, Response] {
  private[this] def meta(path: String): (Charset, String) = {
    val exts = path.split('.')
    val ext = if (exts.nonEmpty) exts.last else ""
    ext match {
      case "js" => (Charsets.Utf8, s"application/javascript;charset=UTF-8")
      case "css" => (Charsets.Utf8, s"text/css;charset=UTF-8")
      case _ => (Charsets.Iso8859_1, s"application/octet-stream")
    }
  }

  def apply(req: Request): Future[Response] = {
    val (uri, _) = parse(req.getUri)
    val path = uri.stripPrefix(basePath)

    if (path.contains(".."))
      return newResponse(
        status = Status.BadRequest,
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8("Invalid path!"))

    val is = getClass.getClassLoader.getResourceAsStream(s"$servingDir/$path")
    if (is == null) new404("resource not found") else {
      val (charset, mime) = meta(path)
      val source = Source.fromInputStream(is, charset.toString)
      val bytes = source.mkString.getBytes(charset)
      source.close()
      newResponse(contentType = mime, content = Buf.ByteArray(bytes))
    }
  }
}
