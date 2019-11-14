package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status, Uri}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{new404, newResponse}
import com.twitter.util.{Future, FuturePool}
import java.io.{File, FileInputStream, InputStream}
import java.nio.charset.{Charset, StandardCharsets}
import scala.io.Source

/**
 * A handler designed to serve static resources.
 * @param baseRequestPath   The base uri path handled by this handler (e.g. /admin/)
 * @param resourceResolver  Converts a relative path to a resource to an InputStream for
 *                          that resource's content.  Inputs for which the resolver is undefined
 *                          will be 404s.
 */
class ResourceHandler(
  baseRequestPath: String,
  resourceResolver: PartialFunction[String, InputStream])
    extends Service[Request, Response] {

  // for java + backwards compatibility
  def this(basePath: String, servingDir: String = "www") =
    this(basePath, ResourceHandler.jarResolver(servingDir))

  private[this] def meta(path: String): (Charset, String) = {
    val exts = path.split('.')
    val ext = if (exts.nonEmpty) exts.last else ""
    ext match {
      case "js" => (StandardCharsets.UTF_8, s"application/javascript;charset=UTF-8")
      case "css" => (StandardCharsets.UTF_8, s"text/css;charset=UTF-8")
      case _ => (StandardCharsets.ISO_8859_1, s"application/octet-stream")
    }
  }

  def apply(req: Request): Future[Response] = {
    val uri = Uri.fromRequest(req)
    val path = uri.path.stripPrefix(baseRequestPath)

    if (path.contains(".."))
      return newResponse(
        status = Status.BadRequest,
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8("Invalid path!")
      )

    resourceResolver
      .lift(path)
      .map { is =>
        val (charset, mime) = meta(path)
        val source = Source.fromInputStream(is, charset.toString).withClose(() => is.close())
        FuturePool.unboundedPool {
          val bytes = source.mkString.getBytes(charset)
          source.close()
          newResponse(contentType = mime, content = Buf.ByteArray.Owned(bytes))
        }.flatten
      }
      .getOrElse {
        new404("resource not found")
      }
  }
}

object ResourceHandler {

  /**
   * Constructs a ResourceHandler which tries to read resources from disk on every request,
   * falling back to resources from the jar if needed.
   * This is intended for use in situations in local development (e.g. so one can save a resource,
   * refresh the browser, and see the change immediately).
   * @param baseRequestPath     The base uri path handled by this handler (e.g. /admin/).
   * @param baseResourcePath    The resource path from which this handler will serve resources
   *                            when they are not found in localFilePath.
   * @param localFilePath       The directory from which this handler will serve resources.
   */
  def fromDirectoryOrJar(
    baseRequestPath: String,
    baseResourcePath: String,
    localFilePath: String
  ): ResourceHandler = new ResourceHandler(
    baseRequestPath,
    directoryOrJarResolver(baseResourcePath, localFilePath)
  )

  /**
   * Constructs a ResourceHandler which returns resources from the jar
   * (via java's `Class#getResourceAsStream`)
   * @param baseRequestPath   The base uri path handled by this handler (e.g. /admin/)
   * @param baseResourcePath  The resource path from which this handler will serve resources.
   * (parameter names differ from the other constructors' for backwards compatibility)
   */
  def fromJar(baseRequestPath: String, baseResourcePath: String = "www"): ResourceHandler =
    new ResourceHandler(baseRequestPath, jarResolver(baseResourcePath))

  /**
   * Loads resources relative to the given baseResourcePath (via java's `Class#getResourceAsStream`
   * @param baseResourcePath  The base resource path to which the requested resource path should
   *                          be appended.
   * @return                  A partial function that loads resources from a jar.
   */
  def jarResolver(baseResourcePath: String): PartialFunction[String, InputStream] =
    Function.unlift { requestPath =>
      Option(getClass.getClassLoader.getResourceAsStream(s"$baseResourcePath/$requestPath"))
    }

  /**
   * Loads resources relative to the given baseDirectory.  Re-reads the file from disk on every
   * request.  Intended for use only when developing locally, to enable saving a file, refreshing
   * the browser, and immediately seeing the change.
   * @param baseDirectory  The base directory (either absolute or relative to the process's
   *                       working directory) to which to the requested resource path should be
   *                       appended.
   * @return               A partial function that loads resources from baseDirectory.
   */
  def directoryResolver(baseDirectory: String): PartialFunction[String, InputStream] =
    Function.unlift { requestPath =>
      val file = new File(s"$baseDirectory/$requestPath")
      if (file.exists()) Some(new FileInputStream(file)) else None
    }

  /**
   * Tries to load resources using directoryResolver in baseDirectory, and if that fails, tries
   * jarResolver in baseResourcePath.
   * @param baseResourcePath  The base path for the jarResolver.
   * @param baseDirectory     The base directory for the directory resolver.
   * @return                  A partial function that loads resources from baseDirectory, or a jar.
   */
  def directoryOrJarResolver(
    baseResourcePath: String,
    baseDirectory: String
  ): PartialFunction[String, InputStream] =
    directoryResolver(baseDirectory) orElse jarResolver(baseResourcePath)
}
