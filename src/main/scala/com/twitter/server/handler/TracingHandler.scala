package com.twitter.server.handler

import com.twitter.finagle.http.Status
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import java.util.logging.Logger

private class FinagleTracing(klass: Class[_]) {
  private val enableM = klass.getDeclaredMethod("enable")
  private val disableM  = klass.getDeclaredMethod("disable")

  def enable() { enableM.invoke(null) }
  def disable() { disableM.invoke(null) }
}

private object FinagleTracing {
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

class TracingHandler extends Service[Request, Response] {
  private[this] val log = Logger.getLogger(getClass.getName)

  def apply(request: Request): Future[Response] = {
    val (_, params) = parse(request.getUri)

    try {
      if (!FinagleTracing.instance.isDefined)
        return newResponse(
          status = Status.InternalServerError,
          contentType = "text/html;charset=UTF-8",
          content = Buf.Utf8("Finagle tracing not found!")
        )
    } catch {
      case _: Throwable =>
        return newResponse(
          status = Status.InternalServerError,
          contentType = "text/html;charset=UTF-8",
          content = Buf.Utf8(
            "Could not initialize Finagle tracing classes. Possibly old version of Finagle.")
        )
    }

    val tracing = FinagleTracing.instance.get
    val msg = if (params.getOrElse("enable", Seq()).headOption.equals(Some("true"))) {
      tracing.enable()
      "Enabled Finagle tracing"
    } else if (params.getOrElse("disable", Seq()).headOption.equals(Some("true"))) {
      tracing.disable()
      "Disabling Finagle tracing"
    } else {
      "Could not figure out what you wanted to do with tracing. " +
        "Either enable or disable it. This is what we got: " + params
    }

    log.info(msg)
    newResponse(
      contentType = "text/html;charset=UTF-8",
      content = Buf.Utf8(msg)
    )
  }
}
