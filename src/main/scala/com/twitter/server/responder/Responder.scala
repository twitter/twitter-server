package com.twitter.server.responder

import org.jboss.netty.handler.codec.http._

private[server] case class Response(htmlResponse: String, curlResponse: String = "")

private[server] trait Responder {
  def respond(req: HttpRequest): Response
}

private[twitter] object ResponderUtils {
  def mapParams(params: Map[String, String]): List[Map[String, String]] =
    params.toList map {
      case (k, v) => Map("key" -> k, "value" -> pretty(v))
    }

  // Strip extraneous symbols from toString'd objects
  private[this] def pretty(value: String): String =
    """.+\.([a-zA-Z]+)[$|@]""".r.findFirstMatchIn(value) match {
      case Some(name) => name.group(1)
      case _ => """\((.+)\)""".r.findFirstMatchIn(value) match {
        case Some(name) => name.group(1)
        case _ => value
      }
    }

  /**
   * Extract the values of a parameter for a string of the format:
   * "....param1=value1&param1=value2..."
   */
  def extractQueryValues(parameter: String, string: String): List[String] = {
    val regex = ("""[?|&]""" + parameter + """=([^&]*)""").r
    regex.findAllIn(string).matchData.map(_.group(1)).toList
  }

  /**
   * Extract only the first value of a parameter for a string of the format:
   * "....?param1=value1&param1=value2..."
   */
  def extractQueryValue(parameter: String, string: String): String = {
    val regex = ("""[?|&]""" + parameter + """=([^&]*)""").r
    regex.findFirstMatchIn(string) match {
      case Some(value) => value.group(1)
      case None => ""
    }
  }
}
