package com.twitter.server.controller

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.mustache.ScalaObjectHandler
import java.io._
import org.jboss.netty.util.CharsetUtil.UTF_8

private[server] object TemplateViewController {

  trait Renderable

  val mustache = new TwitterServerMustache("twitter-server/src/main/resources/templates")

  case class RootView(contents: String) extends Renderable with Navigation

  def renderInRoot(view: Renderable, template: String): String = {
    val contents = mustache.render(template, view)
    val rootView = RootView(contents)
    mustache.render("Root", rootView)
  }

  def render(view: Renderable, template: String): String = {
    mustache.render(template, view)
  }
}

class TwitterServerMustache(templateRoot: String) {
  import TemplateViewController.Renderable

  class TwitterServerMustache extends DefaultMustacheFactory() {
    override def getReader(rn: String): Reader = {
      val name = rn + ".mustache"
      val file = new File(templateRoot, name)
      new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))
    }

    def invalidateCaches() {
      mustacheCache.invalidateAll()
      templateCache.invalidateAll()
    }
  }

  private[this] val mf = new TwitterServerMustache
  mf.setObjectHandler(new ScalaObjectHandler)

  def render(template: String, view: Renderable): String = {
    mf.invalidateCaches()
    val mustache = mf.compile(template)
    val output = new StringWriter
    mustache.execute(output, view).flush()
    output.toString
  }
}