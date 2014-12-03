package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.logging.{Level, Logger}
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import java.net.URLEncoder
import java.util.{logging => javalog}
import scala.annotation.tailrec

private object LoggingHandler {
  implicit val loggerOrder: Ordering[Logger] = Ordering.by(_.name)
  implicit val levelOrder: Ordering[Level] = Ordering.by(_.value)

  def getLevel(logger: Logger): javalog.Level = {
    @tailrec
    def go(l: javalog.Logger): javalog.Level = {
      if (l.getLevel != null) l.getLevel
      else if (l.getParent != null) go(l.getParent)
      else Level.OFF // root has no level set
    }
    go(javalog.Logger.getLogger(logger.name))
  }

  def renderText(loggers: Seq[Logger], updateMsg: String): String = {
    val out = loggers.toSeq.map { logger =>
      val loggerName = if (logger.name == "") "root" else logger.name
      s"$loggerName ${getLevel(logger)}"
    }.mkString("\n")
    if (updateMsg.isEmpty) s"$out" else s"$updateMsg\n$out"
  }

  def renderHtml(loggers: Seq[Logger], levels: Seq[Level], updateMsg: String): String =
    s"""<table class="table table-striped table-condensed">
        <caption>$updateMsg</caption>
        <thead>
          <tr>
            <th>com.twitter.logging.Logger</th>
            <th>com.twitter.logging.Level</th>
          </tr>
        </thead>
        ${
          (for (logger <- loggers) yield {
            val loggerName = if (logger.name == "") "root" else logger.name
            s"""<tr>
                <td><h5>$loggerName</h5></td>
                <td><div class="btn-group" role="group">
                  ${
                     (for (level <- levels) yield {
                       val isActive = getLevel(logger) == level
                       val activeCss = if (!isActive) "btn-default" else {
                        "btn-primary active disabled"
                       }
                       val href = if (isActive) "" else {
                         s"""?logger=${URLEncoder.encode(loggerName, "UTF-8")}&level=${level.name}"""
                       }
                       s"""<a class="btn btn-sm $activeCss"
                              href="$href">${level.name}</a>"""
                     }).mkString("\n")
                   }
                </div></td>
                </tr>"""
            }).mkString("\n")
         }
         </table>"""
}

/**
 * An HTTP [[com.twitter.finagle.Service Service]] that exposes an application's
 * [[com.twitter.logging.Logger]] configuration state and allows for runtime changes
 * via HTTP query strings (?logger=<logger>&level=<level>).
 */
class LoggingHandler extends Service[Request, Response] {

  private[this] val levels = Logger.levels.values.toSeq.sorted(LoggingHandler.levelOrder)

  def apply(request: Request): Future[Response] = {
    val (_, params) = parse(request.getUri)

    val loggerName: Option[String] = params.getOrElse("logger", Seq.empty).headOption map {
      case "root" => ""
      case n => n
    }

    val loggerLevel: Option[String] = params.getOrElse("level", Seq.empty).headOption

    val updateMsg = (loggerLevel, loggerName) match {
      case (Some(level), Some(name)) =>
        val updated = for {
          level <- Logger.levelNames.get(level.toUpperCase)
          logger <- Logger.iterator.find(_.name == name)
        } yield {
          logger.setLevel(level)
          s"""Changed ${if (logger.name == "") "root" else logger.name} to Level.$level"""
        }

        updated.getOrElse(s"Unable to change $name to Level.$level!")

      case _ => ""
    }

    val loggers = Logger.iterator.toSeq.sorted(LoggingHandler.loggerOrder)

    if (!isWebBrowser(request)) {
      newResponse(
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8(LoggingHandler.renderText(loggers, updateMsg))
      )
    } else {
      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(LoggingHandler.renderHtml(loggers, levels, updateMsg))
      )
    }
  }
}
