package com.twitter.server.view

private[server] class CompositeView(views: Seq[View], separator: String = "") {

  def render: String = views.map(_.render).mkString(separator)

}
