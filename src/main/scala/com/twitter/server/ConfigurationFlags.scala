package com.twitter.server

import com.twitter.app.App

case class Flag(name: String, value: String, description: String)

private[server] class ConfigurationFlags(app: App) {

  private def op2String(op: Option[Any]): String = op match {
    case Some(x) => x.toString
    case None => ""
  }

  private[this] lazy val (set, unset) =
    app.flag.getAll().toList.partition { _.get.isDefined }

  lazy val setFlags: List[Flag] = set map { flag =>
    Flag(flag.name, op2String(flag.get), flag.help)
  }

  lazy val unsetFlags: List[Flag] = unset map { flag =>
    Flag(flag.name, flag.defaultString, flag.help)
  }
}