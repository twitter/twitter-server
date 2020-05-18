package com.twitter.server.handler.slf4j.jdk14

import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.logging.{Level, Logger}
import com.twitter.server.Admin.Grouping
import com.twitter.server.handler.slf4j.jdk14.LoggingHandler._
import com.twitter.server.util.HtmlUtils._
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import java.net.URLEncoder
import java.util.{logging => javalog}
import scala.annotation.tailrec
import scala.collection.JavaConverters._

private object LoggingHandler {
  implicit val loggerOrder: Ordering[javalog.Logger] = Ordering.by(_.getName)
  implicit val levelOrder: Ordering[javalog.Level] = Ordering.by(_.intValue)

  def getLevel(logger: javalog.Logger): javalog.Level = {
    @tailrec
    def go(l: javalog.Logger): javalog.Level = {
      if (l.getLevel != null) l.getLevel
      else if (l.getParent != null) go(l.getParent)
      else Level.OFF // root has no level set
    }
    go(javalog.Logger.getLogger(logger.getName))
  }

  def renderText(loggers: Seq[javalog.Logger], updateMsg: String): String = {
    val out = loggers
      .map { logger =>
        val loggerName = getLoggerDisplayName(logger)
        escapeHtml(s"$loggerName ${getLevel(logger)}")
      }
      .mkString("\n")
    if (updateMsg.isEmpty) s"$out" else s"${escapeHtml(updateMsg)}\n$out"
  }

  def renderHtml(loggers: Seq[javalog.Logger], levels: Seq[Level], updateMsg: String): String =
    s"""<table class="table table-striped table-condensed">
        <caption>${escapeHtml(updateMsg)}</caption>
        <thead>
          <tr>
            <th>com.twitter.logging.Logger</th>
            <th>com.twitter.logging.Level</th>
          </tr>
        </thead>
        ${(for (logger <- loggers) yield {
      val loggerName = getLoggerDisplayName(logger)
      s"""<tr>
                <td><h5>${escapeHtml(loggerName)}</h5></td>
                <td><div class="btn-group" role="group">
                  ${(for (level <- levels) yield {
        val isActive = getLevel(logger) == level
        val activeCss =
          if (!isActive) "btn-default"
          else {
            "btn-primary active disabled"
          }
        val href =
          if (isActive) ""
          else {
            s"""?logger=${URLEncoder.encode(loggerName, "UTF-8")}&level=${level.name}"""
          }
        s"""<a class="btn btn-sm $activeCss"
                              href="$href">${level.name}</a>"""
      }).mkString("\n")}
                </div></td>
                </tr>"""
    }).mkString("\n")}
         </table>"""

  private def getLoggerDisplayName(logger: javalog.Logger): String = logger.getName match {
    case "" => "root"
    case name => name
  }
}

/**
 * An HTTP [[com.twitter.finagle.Service Service]] that exposes an application's
 * [[com.twitter.logging.Logger]] configuration state and allows for runtime changes
 * via HTTP query strings (?logger=<logger>&level=<level>).
 */
private class LoggingHandler extends com.twitter.server.handler.LoggingHandler {

  /** Implementation name */
  override val name: String = "slf4j-jdk14"

  private[this] val levels = Logger.levels.values.toSeq.sorted(LoggingHandler.levelOrder)
  private[this] val logManager = java.util.logging.LogManager.getLogManager
  private[this] val log = Logger(this.getClass)

  val pattern = "/admin/logging"
  override def route: Route =
    Route(
      pattern = this.pattern,
      handler = this,
      index = Some(
        RouteIndex(alias = "Logging", group = Grouping.Utilities, path = Some(this.pattern))
      )
    )

  def apply(request: Request): Future[Response] = {
    val uri = Uri.fromRequest(request)
    val params = uri.params

    val loggerName: Option[String] = parseLoggerName(params)
    val loggerLevel: Option[String] = params.get("level")

    val updateMsg = (loggerLevel, loggerName) match {
      case (Some(level), Some(name)) =>
        val updated = for {
          level <- Logger.levelNames.get(level.toUpperCase)
        } yield {
          val logger = logManager.getLogger(name)
          logger.setLevel(level)
          s"""Changed ${getLoggerDisplayName(logger)} to Level.$level"""
        }

        updated.getOrElse(s"Unable to change $name to Level.$level!")

      case _ => ""
    }
    log.info(updateMsg)

    val loggers = logManager.getLoggerNames.asScala.toSeq
      .map(logManager.getLogger)
      .sorted(LoggingHandler.loggerOrder)
    if (!expectsHtml(request)) {
      newResponse(
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8(LoggingHandler.renderText(loggers, updateMsg))
      )
    } else {
      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(LoggingHandler.renderHtml(loggers, levels, escapeHtml(updateMsg)))
      )
    }
  }

  private[this] def parseLoggerName(params: ParamMap): Option[String] = {
    params.get("logger").map {
      case "root" => ""
      case name => name
    }
  }
}
