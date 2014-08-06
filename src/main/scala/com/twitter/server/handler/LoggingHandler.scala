package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.logging.Logger
import com.twitter.util.Future
import java.net.URLEncoder
import org.jboss.netty.handler.codec.http._

/**
 * An HTTP [[com.twitter.finagle.Service Service]] that exposes an application's
 * logging configuration state.
 */
class LoggingHandler extends WebHandler {
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

  def apply(request: HttpRequest): Future[HttpResponse] = {
    val req = Request(request)

    val loggerName = req.params.get("logger") map {
      case "root" => ""
      case n => n
    }

    val (html, text) = (req.params.get("level"), loggerName) match {
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

        updated orElse {
          Some("Logging level change failed for %s to %s".format(name, level))
        } map { msg =>
          val (html, text) = helpMessage
          (msg + "<br /><br />" + html, msg + "\n\n" + text)
        } get

      case _ =>
        helpMessage
    }

    makeHttpFriendlyResponse(request, text, html)
  }
}
