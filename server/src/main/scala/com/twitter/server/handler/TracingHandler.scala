package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status, Uri}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.Future
import com.twitter.util.logging.Logger

private class FinagleTracing(klass: Class[_]) {
  private val enableM = klass.getDeclaredMethod("enable")
  private val disableM = klass.getDeclaredMethod("disable")

  def enable(): Unit = { enableM.invoke(null) }
  def disable(): Unit = { disableM.invoke(null) }
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
  private[this] val log = Logger[TracingHandler]

  def apply(request: Request): Future[Response] = {
    val uri = Uri.fromRequest(request)
    val params = uri.params

    try {
      if (FinagleTracing.instance.isEmpty)
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
            "Could not initialize Finagle tracing classes. Possibly old version of Finagle."
          )
        )
    }

    val tracing = FinagleTracing.instance.get
    val msg = if (params.getOrElse("enable", "") == "true") {
      tracing.enable()
      "Enabled Finagle tracing"
    } else if (params.getOrElse("disable", "") == "true") {
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
