package com.twitter.server.handler.slf4j.log4j12

import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.server.Admin.Grouping
import com.twitter.server.util.HtmlUtils._
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import java.net.URLEncoder
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import scala.collection.JavaConverters._

@deprecated("Users are encouraged to use twitter-server/logback-classic.", "2022-01-12")
private class LoggingHandler extends com.twitter.server.handler.LoggingHandler with Logging {

  /** Implementation name */
  override def name: String = "slf4j-log412"

  implicit val loggerOrder: Ordering[Logger] = Ordering.by(_.getName)
  implicit val levelOrder: Ordering[Level] = Ordering.by(_.toInt)

  private[this] val levels = Seq(
    Level.OFF,
    Level.FATAL,
    Level.ERROR,
    Level.WARN,
    Level.INFO,
    Level.DEBUG,
    Level.TRACE,
    Level.ALL
  ).sorted(levelOrder)

  /** Exposed for testing */
  private[log4j12] def loggers = {
    LogManager.getCurrentLoggers.asScala.toSeq.asInstanceOf[Seq[Logger]]
  }

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
              val logger = LogManager.getLogger(loggerString)
              logger.setLevel(null)
              val message = s"Removed level override for ${logger.getName}"
              info(message)
              escapeHtml(message)
            } else {
              val logger = LogManager.getLogger(loggerString)
              logger.setLevel(Level.toLevel(toSetLevel))
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

      val showOverriddenOnly = request.getBooleanParam("overridden", false)
      val filteredLoggers = if (showOverriddenOnly) {
        loggers
          .filter(_.getLevel != null)
          .filter(_.getName != org.slf4j.Logger.ROOT_LOGGER_NAME)
      } else loggers
      val html = renderHtml(filteredLoggers, levels, message, showOverriddenOnly)

      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(html)
      )
    } catch {
      case e: Throwable =>
        newResponse(contentType = "text/html;charset=UTF-8", content = Buf.Utf8(e.getMessage))
    }
  }

  private def renderHtml(
    loggerList: Seq[Logger],
    levels: Seq[Level],
    updateMsg: String,
    showOverriddenOnly: Boolean
  ): String =
    s"""<table class="table table-striped table-condensed">
        <caption>${escapeHtml(updateMsg)}</caption>
        <thead>
          <tr>
            <th>org.apache.log4j.Logger</th>
            <th>org.apache.log4j.Level</th>
          </tr>
        </thead>
        ${renderButtons(showOverriddenOnly)}
        ${(for (logger <- loggerList) yield {
      val loggerName =
        if (logger.getName == "") org.slf4j.Logger.ROOT_LOGGER_NAME else logger.getName
      val inheritsLevel = logger.getLevel == null
      val buttons = for (level <- levels) yield {
        val isActive = logger.getEffectiveLevel == level
        val activeCss =
          if (!isActive) "btn-default"
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

  private def renderButtons(showOverriddenOnly: Boolean): String = {
    def buttonClass(inject: String): String = s"""class="btn btn-primary$inject""""
    val disabledButton = buttonClass(""" active disabled""")
    val overrideButton = buttonClass("""" href="?overridden=true""")
    val (showAllBtn, overriddenBtn) =
      if (showOverriddenOnly) (buttonClass("""" href="?"""), disabledButton)
      else (disabledButton, overrideButton)
    s"""<a $showAllBtn>Show all loggers</a>
        <a $overriddenBtn>Show overridden loggers only</a>"""
  }
}
