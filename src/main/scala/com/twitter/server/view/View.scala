package com.twitter.server.view
import com.twitter.finagle.client.{ClientInfo, ClientModuleInfo}
import com.twitter.server.ConfigurationFlags
import com.twitter.server.Flag
import com.twitter.server.controller.TemplateViewController
import org.jboss.netty.handler.codec.http._
import com.twitter.finagle.client.ClientRegistry

private[server] object ResponderUtils {
  def mapParams(params:  Map[String, String]) =
    params.toList map {
      case (k, v) => Map("key" -> k, "value" -> pretty(v))
    }

  // Strip extraneous symbols from toString'd objects
  private[this] def pretty(value: String): String = 
    """.+\.([\w]+)[$|@]""".r.findFirstMatchIn(value) match {
      case Some(name) => name.group(1)
      case _ => """\((.+)\)""".r.findFirstMatchIn(value) match {
        case Some(name) => name.group(1)
        case _ => value
      }
  }
}
