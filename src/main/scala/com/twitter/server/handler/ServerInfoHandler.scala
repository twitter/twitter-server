package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.stats.LoadedStatsReceiver
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import com.twitter.util.registry.GlobalRegistry
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
    "scm_repository" -> "unknown"
  )

  private[this] val combinedInfo = basicServerInfo ++ buildProperties.asScala

  private[this] val registry = GlobalRegistry.get

  combinedInfo.foreach {
    case (key, value) =>
      registry.put(Seq("build.properties", key), value)
  }

  {
    // Note, don't use `scala.sys.props` until we are on 2.12:
    // https://github.com/scala/scala/pull/4372
    val props = System.getProperties
    for (key <- props.stringPropertyNames().asScala) {
      val value = props.getProperty(key)
      // we acquired a snapshot of keys above, but `key`
      // could have since been removed.
      if (value != null)
        registry.put(Seq("system", "properties", key), value)
    }
  }

  sys.env.foreach {
    case (key, value) =>
      registry.put(Seq("system", "env", key), value)
  }

  registry.put(
    Seq("system", "jvm_arguments"),
    ManagementFactory.getRuntimeMXBean.getInputArguments.toString
  )

  // Expose this build revision as a number. Useful to check cluster consistency.
  combinedInfo.get("build.git.revision.number") match {
    case Some(rev) => LoadedStatsReceiver.provideGauge("build.git.revision.number") { rev.toFloat }
    case None =>
  }

  private[this] val serverInfo = combinedInfo ++ Map(
    "build_last_few_commits" ->
      buildProperties.getProperty("build_last_few_commits", "unknown").split("\n"),
    "build" ->
      buildProperties.getProperty("build_name", "unknown"),
    "start_time" -> new Date(mxRuntime.getStartTime).toString
  ) - "build_name"

  def apply(req: Request): Future[Response] = {
    newResponse(
      contentType = "application/json;charset=UTF-8",
      content =
        Buf.Utf8(JsonConverter.writeToString(serverInfo + ("uptime" -> mxRuntime.getUptime)))
    )
  }
}
