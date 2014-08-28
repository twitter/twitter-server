package com.twitter.server.controller

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.http.HttpMuxer
import com.twitter.mustache.ScalaObjectHandler
import java.io.{Reader, StringWriter}

private[server] object TemplateViewController {

  trait Renderable

  /**
   * Couples `content` with a default template that contains the navigation
   * view. See `Navigation.mustache` for more details.
   */
  private[this] case class RootView(val contents: String) extends Renderable {
    val paths = HttpMuxer.patterns.filter(_.startsWith("/"))
    val clientPaths = ClientRegistry.clientList().map("/admin/clients/" + _.name)
    val navigation = render(new NavigationView(paths ++ clientPaths), "Navigation")
  }

  private[this] val mf = new DefaultMustacheFactory("templates/") {
    override def getReader(template: String): Reader = {
      super.getReader(template+".mustache")
    }

    // TODO: not sure we want to invalidate both caches or if
    // it's even okay to mess with this internal state. Should we
    // instead create a new DefaultMustacheFactory if we don't
    // want caching?
    def invalidateCaches() {
      mustacheCache.invalidateAll()
      templateCache.invalidateAll()
    }
  }

  mf.setObjectHandler(new ScalaObjectHandler)

  private[this] def render(view: Renderable, template: String): String = {
    mf.invalidateCaches()
    val mustache = mf.compile(template)
    val output = new StringWriter
    mustache.execute(output, view).flush()
    output.toString
  }

  /**
   * Renders the `view` with the given mustache `template` resource.
   * Note, templates are loaded with the default MustacheResolver
   * from "<resources>/templates/<template>.mustache". The file
   * extension should be ommited from the `template` string.
   */
  def renderInRoot(view: Renderable, template: String): String = {
    val contents = render(view, template)
    val rootView = RootView(contents)
    render(rootView, "Root")
  }
}