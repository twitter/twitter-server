package com.twitter.server

import com.twitter.finagle.http.Request
import com.twitter.finagle.Service
import com.twitter.util.Future
import java.util.logging.Logger
import org.jboss.netty.handler.codec.http._

class FinagleTracing(klass: Class[_]) {
  private val enableM = klass.getDeclaredMethod("enable")
  private val disableM  = klass.getDeclaredMethod("disable")

  def enable() { enableM.invoke(null) }
  def disable() { disableM.invoke(null) }
}

object FinagleTracing {
  val instance: Option[FinagleTracing] = {
    val loader = Thread.currentThread().getContextClassLoader
    try {
      val klass = loader.loadClass("com.twitter.finagle.tracing.Trace")
      Some(new FinagleTracing(klass))
    } catch {
      case _: ClassNotFoundException =>
        None
    }
  }
}

class TracingHandler extends Service[HttpRequest, HttpResponse] {
  private[this] val log = Logger.getLogger(getClass.getName)

  def apply(request: HttpRequest): Future[HttpResponse] = {
    val req = Request(request)
    val res = req.response
    val ret = Future.value(res)

    try {
      if (!FinagleTracing.instance.isDefined) {
        res.statusCode = 500
        res.contentString = "Finagle tracing not found!"
        return ret
      }
    } catch {
      case _ =>
        res.statusCode = 500
        res.contentString =
          "Could not initialize Finagle tracing classes. Possibly old version of Finagle."
        return ret
    }

    val tracing = FinagleTracing.instance.get
    val msg = if (req.params.get("enable").equals(Some("true"))) {
      tracing.enable()
      "Enabled Finagle tracing"
    } else if (req.params.get("disable").equals(Some("true"))) {
      tracing.disable()
      "Disabling Finagle tracing"
    } else {
      "Could not figure out what you wanted to do with tracing. " +
        "Either enable or disable it. This is what we got: " + req.params
    }

    log.info(msg)
    res.statusCode = 200
    res.contentString = msg
    ret
  }
}
