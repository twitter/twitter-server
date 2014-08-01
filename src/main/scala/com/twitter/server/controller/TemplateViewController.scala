package com.twitter.server.controller

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.mustache.ScalaObjectHandler
import com.twitter.server.view.View
import java.io._
import org.jboss.netty.util.CharsetUtil.UTF_8

private[server] object TemplateViewController {

  val mustache = new TwitterServerMustache("twitter-server/src/main/resources/templates")

  def render(view: View, template: String): String = {
    mustache.render(template, view)
  }
}

class TwitterServerMustache(templateRoot: String) {
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

  def render(template: String, view: View): String = {
    mf.invalidateCaches()
    val mustache = mf.compile(template)
    val output = new StringWriter
    mustache.execute(output, view).flush()
    output.toString
  }
}