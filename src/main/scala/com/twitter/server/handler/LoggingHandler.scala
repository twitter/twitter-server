package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.logging.Logger
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import java.net.URLEncoder

/**
 * An HTTP [[com.twitter.finagle.Service Service]] that exposes an application's
 * logging configuration state.
 */
class LoggingHandler extends Service[Request, Response] {
  val levelNames = Logger.levelNames.keys.toSeq.sorted

  def helpMessage: (String, String) = {
    val tableBody = Logger.iterator map { logger =>
      val loggerName = if (logger.name == "") "root" else logger.name

      val links = levelNames map { levelName =>
        Option(logger.getLevel) match {
          case Some(level) if level.getName == levelName =>
            "<strong>%s</strong>".format(levelName)

          case _ =>
            "<a href='?logger=%s&level=%s'>%s</a>".format(
              URLEncoder.encode(loggerName, "UTF-8"), levelName, levelName)
        }
      }

      "<tr><td>%s</td><td>%s</td></tr>".format(loggerName, links.mkString(" "))
    } mkString("")

    val html = "<table>%s</table>".format(tableBody)

    val text = Logger.iterator map { logger =>
      "%s %s".format(
        if (logger.name == "") "root" else logger.name,
        Option(logger.getLevel).map(_.getName).getOrElse("default"))
    } mkString("\n")

    (html, text)
  }

  def apply(request: Request): Future[Response] = {
    val (_, params) = parse(request.getUri)

    val loggerName: Option[String] = params.getOrElse("logger", Seq.empty).headOption map {
      case "root" => ""
      case n => n
    }

    val loggerLevel = params.getOrElse("level", Seq.empty).headOption

    val (html, text) = (loggerLevel, loggerName) match {
      case (Some(level), Some(name)) =>
        val updated = for {
          level <- Logger.levelNames.get(level.toUpperCase)
          logger <- Logger.iterator.find(_.name == name)
        } yield {
          logger.setLevel(level)
          "Changed %s to %s".format(
            if (logger.name == "") "root" else logger.name,
            level)
        }

        (updated orElse {
          Some("Logging level change failed for %s to %s".format(name, level))
        } map { msg =>
          val (html, text) = helpMessage
          (msg + "<br /><br />" + html, msg + "\n\n" + text)
        }).get

      case _ =>
        helpMessage
    }

    if (!isWebBrowser(request)) newOk(text) else {
      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(html)
      )
    }
  }
}
