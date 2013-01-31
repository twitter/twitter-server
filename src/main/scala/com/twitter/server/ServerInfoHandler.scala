package com.twitter.server

import com.twitter.finagle.Service
import com.twitter.util.Future
import java.lang.management.ManagementFactory
import java.util.{Date, Properties}
import org.jboss.netty.handler.codec.http._

class ServerInfoHandler(obj: AnyRef) extends Service[HttpRequest, HttpResponse] {
  private[this] val mxRuntime = ManagementFactory.getRuntimeMXBean

  private[this] val buildProperties = new Properties

  try {
    buildProperties.load(obj.getClass.getResource("build.properties").openStream)
  } catch {
    case _ =>
  }

  case class ServerInfo(
    name: String,
    version: String,
    build: String,
    build_revision: String,
    build_branch_name: String,
    build_last_few_commits: Seq[String],
    start_time: String,
    var uptime: Long = 0)

  private[this] val serverInfo =
    ServerInfo(
      buildProperties.getProperty("name", "unknown"),
      buildProperties.getProperty("version", "0.0"),
      buildProperties.getProperty("build_name", "unknown"),
      buildProperties.getProperty("build_revision", "unknown"),
      buildProperties.getProperty("build_branch_name", "unknown"),
      buildProperties.getProperty("build_last_few_commits", "unknown").split("\n"),
      (new Date(mxRuntime.getStartTime())).toString)

  def apply(req: HttpRequest) = {
    serverInfo.uptime = mxRuntime.getUptime
    Future.value(JsonConverter(serverInfo))
  }
}
