package com.twitter.server.controller

import com.twitter.finagle.http.HttpMuxer
import com.twitter.server.controller.TemplateViewController.Renderable
import com.twitter.finagle.client.{ClientInfo, ClientModuleInfo, ClientRegistry}
import com.twitter.server.handler.MetricsHandler

private[server] case class Link (name: String, path: String, subLinks: Seq[Link] = Seq.empty)

private[this] class NavigationView(paths: Seq[String]) extends TemplateViewController.Renderable {

   // Customize how links are grouped
  private[this] val customGroups = {
    val groups = Map(
      "Utilities" ->
        Seq("ping", "shutdown", "tracing", "/abortabortabort", "/quitquitquit"),
      "Server Status" ->
        Seq("contention", "/health", "threads", "announcer", "server_info"),
      "Metrics" ->
        Seq("metrics.json", "/stats.json", "metrics_graphs")
    )
    groups flatMap { case (groupName, items) =>
      items map { item =>
        item -> groupName
      }
    }
  }

  // Customize naming of links
  private[this] val customNames = Map(
    "pprof" -> "Profiles",
    "metrics.json" -> "Raw Metrics",
    "stats.json" -> "Raw Stats",
    "server_info" -> "Server Information",
    "metrics_graphs" -> "Metrics Graphs",
    "abortabortabort" -> "Abort",
    "quitquitquit" -> "Quit"
  )
  private[this] val hiddenEndpoints = {
    val he = Seq(
      "dtab",
      "tracing",
      "metrics",
      "files",
      "/admin",
      "clients/"
    )
    if(!MetricsHandler.hasRegistry) he :+ "metrics_graphs"
    else he
  }

  // Prettify naming of links
  private[this] def prettyName(name: String): String = {
    val sName = name.stripPrefix("/")
    customNames.get(sName) match {
      case Some(name) => name
      case None => sName.capitalize
    }
  }

  private[this] def groupLink(link: String): String =
    customGroups.get(link) match {
      case Some(group) => group
      case None => link.takeWhile(_ != '/')
    }

  private[this] def shouldHideEndpoint(link: String): Boolean =
    link.isEmpty || hiddenEndpoints.contains(link)

  private[this] def joinPathsIfNotPath(path1: String, path2: String): String =
   if (path2.startsWith("/")) return path2
   else path1 + path2

  val trimmedLinks = paths.map(_.stripPrefix("/admin/")).filter(!shouldHideEndpoint(_))
  val groups = trimmedLinks.groupBy(link => groupLink(link))
  val links = groups map { case(topLevel, secondLevel) =>
    val subLinks = secondLevel.filter(_!= topLevel) map { subLink =>
      Link(prettyName(subLink.stripPrefix(topLevel)), joinPathsIfNotPath("/admin/", subLink))
    }
    val topLevelLink =
      if (trimmedLinks.contains(topLevel)) joinPathsIfNotPath("/admin/", topLevel)
      else ""
    Link(prettyName(topLevel), topLevelLink, subLinks)
  }
}

trait Navigation {
  val paths = HttpMuxer.patterns.filter(_.startsWith("/"))
  val clientPaths = ClientRegistry.clientList().map("/admin/clients/" + _.name)
  val navigation = TemplateViewController.render(
      new NavigationView(paths ++ clientPaths), "Navigation")

}