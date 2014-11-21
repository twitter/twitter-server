package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import java.lang.management.ManagementFactory
import java.util.{Date, Properties}
import scala.collection.JavaConverters._

/**
 * A simple http service for serving up information pulled from a build.properties
 * file. The ClassLoader for the given object is used to load the build.properties file,
 * which is first searched for relative to the given object's class's package
 * (class-package-name/build.properties), and if not found there, then it is searched for
 * with an absolute path ("/build.properties").
 */
class ServerInfoHandler(obj: AnyRef) extends Service[Request, Response] {
  private[this] val mxRuntime = ManagementFactory.getRuntimeMXBean

  private[this] val buildProperties = new Properties

  try {
    buildProperties.load(obj.getClass.getResource("build.properties").openStream)
  } catch {
    case _: Throwable =>
      try {
        buildProperties.load(obj.getClass.getResource("/build.properties").openStream)
      } catch {
        case _: Throwable =>
      }
  }

  private[this] val basicServerInfo = Map(
    "name" -> "unknown",
    "version" -> "0.0",
    "build" -> "unknown",
    "build_revision" -> "unknown",
    "build_branch_name" -> "unknown",
    "merge_base" -> "unknown",
    "merge_base_commit_date" -> "unknown",
    "scm_repository" -> "unknown",
    "start_time" -> new Date(mxRuntime.getStartTime).toString
  )

  private[this] val serverInfo = basicServerInfo ++ buildProperties.asScala ++ Map(
    "build_last_few_commits" ->
      buildProperties.getProperty("build_last_few_commits", "unknown").split("\n"),
    "build" ->
      buildProperties.getProperty("build_name", "unknown")
  ) - "build_name"

  def apply(req: Request): Future[Response] = {
    newResponse(
      contentType = "application/json;charset=UTF-8",
      content = Buf.Utf8(JsonConverter.writeToString(serverInfo + ("uptime" -> mxRuntime.getUptime)))
    )
  }
}
