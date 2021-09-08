package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.stats.LoadedStatsReceiver
import com.twitter.io.Buf
import com.twitter.server.BuildProperties
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.Future
import com.twitter.util.registry.GlobalRegistry
import java.lang.management.ManagementFactory
import java.util.Date
import scala.collection.JavaConverters._

/**
 * A simple http service for serving up information pulled from a build.properties
 * file.
 */
class ServerInfoHandler() extends Service[Request, Response] {
  private[this] val mxRuntime = ManagementFactory.getRuntimeMXBean

  private[this] val registry = GlobalRegistry.get

  BuildProperties.all.foreach {
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
  BuildProperties.all.get("build.git.revision.number") match {
    case Some(rev) => LoadedStatsReceiver.provideGauge("build.git.revision.number") { rev.toFloat }
    case None =>
  }

  private[this] val serverInfo = BuildProperties.all ++ Map(
    "build_last_few_commits" ->
      BuildProperties.get("build_last_few_commits", "unknown").split("\n"),
    "build" ->
      BuildProperties.get("build_name", "unknown"),
    "start_time" -> new Date(mxRuntime.getStartTime).toString
  ) - "build_name"

  def apply(req: Request): Future[Response] = {
    newResponse(
      contentType = "application/json;charset=UTF-8",
      content = Buf.Utf8(
        AdminJsonConverter.prettyObjectMapper.writeValueAsString(
          serverInfo + ("uptime" -> mxRuntime.getUptime)))
    )
  }
}
