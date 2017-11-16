package com.twitter.server.handler.logback.classic

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.server.Admin.Grouping
import com.twitter.server.handler.AdminHttpMuxHandler
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import java.net.URLEncoder
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

private class LoggingHandler extends AdminHttpMuxHandler with Logging {

  implicit val loggerOrder: Ordering[Logger] = Ordering.by(_.getName)
  implicit val levelOrder: Ordering[Level] = Ordering.by(_.levelInt)

  private[this] val levels =
    Seq(Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE, Level.ALL)
      .sorted(levelOrder)

  /** Exposed for testing */
  private[classic] def loggers =
    LoggerFactory.getILoggerFactory
      .asInstanceOf[LoggerContext]
      .getLoggerList
      .asScala
      .sorted(loggerOrder)

  val pattern = "/admin/logging"
  override def route: Route =
    Route(
      pattern = this.pattern,
      handler = this,
      index = Some(
        RouteIndex(alias = "Logging", group = Grouping.Utilities, path = Some(this.pattern))
      )
    )

  override def apply(request: Request): Future[Response] = {
    request.method match {
      case Method.Get | Method.Post =>
        respond(request)
      case _ =>
        newResponse(
          status = Status.MethodNotAllowed,
          contentType = "text/plain;charset=UTF-8",
          content = Buf.Utf8("Method Not Allowed")
        )
    }
  }

  /* Private */

  private def respond(request: Request): Future[Response] = {
    try {
      val toSetLoggerOption = Option(request.getParam("logger"))
      val message = toSetLoggerOption match {
        case Some(loggerString) =>
          try {
            val toSetLevel = request.getParam("level")
            if (toSetLevel == null || toSetLevel.isEmpty) {
              throw new IllegalArgumentException(
                s"Unable to set log level for $loggerString -- undefined logging level!"
              )
            } else if (toSetLevel == "null") {
              val logger = LoggerFactory.getLogger(loggerString).asInstanceOf[Logger]
              logger.setLevel(null)
              val message = s"Removed level override for ${logger.getName}"
              info(message)
              escapeHtml(message)
            } else {
              val logger = LoggerFactory.getLogger(loggerString).asInstanceOf[Logger]
              logger.setLevel(Level.valueOf(toSetLevel))
              val message = s"""Changed ${logger.getName} to Level.$toSetLevel"""
              info(message)
              escapeHtml(message)
            }
          } catch {
            case e: Exception =>
              warn(e.getMessage)
              escapeHtml(e.getMessage)
          }
        case _ => ""
      }

      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(renderHtml(loggers, levels, message))
      )
    } catch {
      case e: Throwable =>
        newResponse(contentType = "text/html;charset=UTF-8", content = Buf.Utf8(e.getMessage))
    }
  }

  private def renderHtml(loggers: Seq[Logger], levels: Seq[Level], updateMsg: String): String =
    s"""<table class="table table-striped table-condensed">
        <caption>${escapeHtml(updateMsg)}</caption>
        <thead>
          <tr>
            <th>ch.qos.logback.classic.Logger</th>
            <th>ch.qos.logback.classic.Level</th>
          </tr>
        </thead>
        ${(for (logger <- loggers) yield {
      val loggerName =
        if (logger.getName == "") org.slf4j.Logger.ROOT_LOGGER_NAME else logger.getName
      val inheritsLevel = logger.getLevel == null

      val buttons = for (level <- levels) yield {
        val isActive = logger.getEffectiveLevel == level

        val activeCss = if (!isActive) "btn-default"
          else if (!inheritsLevel) "btn-primary active disabled"
          else "btn-primary active"

        val href =
          if (isActive && logger.getLevel == logger.getEffectiveLevel) ""
          else {
            s"""?logger=${URLEncoder.encode(loggerName, "UTF-8")}&level=${level.toString}"""
          }

        s"""<a class="btn btn-sm $activeCss"
                              href="$href">${level.toString}</a>"""
      }

      val resetButton = if (!inheritsLevel && loggerName != org.slf4j.Logger.ROOT_LOGGER_NAME) {
          val href = s"""?logger=${URLEncoder.encode(loggerName, "UTF-8")}&level=null"""
          s"""<a class="btn btn-sm btn-warning" href="$href">RESET</a>"""
        } else ""

      s"""<tr>
                <td><h5>${escapeHtml(loggerName)}</h5></td>
                <td><div class="btn-group" role="group">
                  ${buttons.mkString("\n")}
                  $resetButton
                </div></td>
                </tr>"""
    }).mkString("\n")}
         </table>"""
}
