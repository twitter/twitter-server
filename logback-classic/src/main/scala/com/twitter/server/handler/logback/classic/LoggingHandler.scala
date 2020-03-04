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
import java.util.{logging => javalog}
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import scala.collection.JavaConverters._

private class LoggingHandler extends AdminHttpMuxHandler with Logging {

  implicit val loggerOrder: Ordering[Logger] = Ordering.by(_.getName)
  implicit val levelOrder: Ordering[Level] = Ordering.by(_.levelInt)

  private[this] val levels =
    Seq(Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE, Level.ALL)
      .sorted(levelOrder)

  private[this] implicit val julLevelOrder: Ordering[javalog.Level] = Ordering.by(_.intValue)
  private[this] val julLogManager = javalog.LogManager.getLogManager
  private[this] val julLoggers = julLogManager.getLoggerNames.asScala.toSeq
    .map(julLogManager.getLogger)

  private[this] val julLevels =
    Seq(
      javalog.Level.OFF,
      javalog.Level.SEVERE,
      javalog.Level.WARNING,
      javalog.Level.INFO,
      javalog.Level.CONFIG,
      javalog.Level.FINE,
      javalog.Level.FINER,
      javalog.Level.FINEST,
      javalog.Level.ALL
    ).sorted(julLevelOrder)

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
            val isJul = request.getBooleanParam("isJul", false)
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
              val message = if (!isJul) {
                val logger = LoggerFactory.getLogger(loggerString).asInstanceOf[Logger]
                logger.setLevel(Level.valueOf(toSetLevel))
                s"""Changed ${logger.getName} to Level.$toSetLevel"""
              } else {
                val julLogger = julLogManager.getLogger(loggerString)
                julLogger.setLevel(javalog.Level.parse(toSetLevel))
                s"""Changed ${julLogger.getName} to Level.$toSetLevel"""
              }

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
      val html = renderHtml(filteredLoggers.toSeq, julLoggers, message, showOverriddenOnly)

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
    renderLoggers: Seq[Logger],
    julLoggers: Seq[javalog.Logger],
    updateMsg: String,
    showOverriddenOnly: Boolean
  ): String = {
    s"""<h3>Logback Loggers</h3>
        ${renderFilterButtons(showOverriddenOnly)}
    <table class="table table-striped table-condensed">
        <caption>${escapeHtml(updateMsg)}</caption>
        <thead>
          <tr>
            <th>ch.qos.logback.classic.Logger</th>
            <th>ch.qos.logback.classic.Level</th>
          </tr>
        </thead>
          ${(for (logger <- renderLoggers) yield {
      val loggerName =
        if (logger.getName == "") org.slf4j.Logger.ROOT_LOGGER_NAME else logger.getName
      val inheritsLevel = logger.getLevel == null
      val filterQueryParams = if (showOverriddenOnly) {
        "?overridden=true&"
      } else "?"

      val buttons = for (level <- levels) yield {
        val isActive = logger.getEffectiveLevel == level

        val activeCss =
          if (!isActive) "btn-default"
          else if (!inheritsLevel) "btn-primary active disabled"
          else "btn-primary active"

        val queryParams =
          if (isActive && logger.getLevel == logger.getEffectiveLevel) ""
          else {
            s"""logger=${URLEncoder.encode(loggerName, "UTF-8")}&level=${level.toString}"""
          }

        s"""<a class="btn btn-sm $activeCss"
                              href="$filterQueryParams$queryParams">${level.toString}</a>"""
      }

      val resetButton = if (!inheritsLevel && loggerName != org.slf4j.Logger.ROOT_LOGGER_NAME) {
        val queryParams = s"""logger=${URLEncoder.encode(loggerName, "UTF-8")}&level=null"""
        s"""<a class="btn btn-sm btn-warning" href="$filterQueryParams$queryParams">RESET</a>"""
      } else ""

      s"""<tr>
                <td><h5>${escapeHtml(loggerName)}</h5></td>
                <td><div class="btn-group" role="group">
                  ${buttons.mkString("\n")}
                  $resetButton
                </div></td>
                </tr>"""
    }).mkString("\n")}
         </table>
    <h3>java.util.Logging Loggers</h3>
    <table class="table table-striped table-condensed">
            <thead>
              <tr>
                <th>java.util.logging.Logger</th>
                <th>java.util.logging.Level</th>
              </tr>
            </thead>
    ${
      val filterQueryParams = if (showOverriddenOnly) {
        "?overridden=true&"
      } else "?"

      (for (logger <- julLoggers) yield {
        val loggerName = getLoggerDisplayName(logger)
        val buttons = (for (level <- julLevels) yield {
          val isActive = getLevel(logger) == level
          val activeCss =
            if (!isActive) "btn-default"
            else {
              "btn-primary active disabled"
            }
          val queryParams =
            if (isActive) ""
            else {
              s"""logger=${URLEncoder
                .encode(loggerName, "UTF-8")}&level=${level.toString}&isJul=true"""
            }
          s"""<a class="btn btn-sm $activeCss"
                            href="$filterQueryParams$queryParams">${level.toString}</a>"""
        }).mkString("\n")

        s"""<tr>
          <td><h5>${escapeHtml(loggerName)}</h5></td>
          <td><div class="btn-group" role="group">
            $buttons
          </div></td>
          </tr>"""
      }).mkString("\n")
    }
         </table>"""

  }

  private def getLoggerDisplayName(logger: javalog.Logger): String = logger.getName match {
    case "" => "root"
    case name => name
  }

  def getLevel(logger: javalog.Logger): javalog.Level = {
    @tailrec
    def go(l: javalog.Logger): javalog.Level = {
      if (l.getLevel != null) l.getLevel
      else if (l.getParent != null) go(l.getParent)
      else javalog.Level.OFF // root has no level set
    }
    go(javalog.Logger.getLogger(logger.getName))
  }

  private def renderFilterButtons(showOverriddenOnly: Boolean): String = {
    def buttonClass(inject: String): String = s"""class="btn btn-primary$inject""""
    val disabledBtn = buttonClass(" active disabled")
    val overrideBtn = buttonClass("""" href="?overridden=true""")
    val (showAllBtn, overriddenBtn) =
      if (showOverriddenOnly) (buttonClass("""" href="?"""), disabledBtn)
      else (disabledBtn, overrideBtn)
    s"""<a $showAllBtn>Show all loggers</a>
        <a $overriddenBtn>Show overridden loggers only</a>"""
  }
}
