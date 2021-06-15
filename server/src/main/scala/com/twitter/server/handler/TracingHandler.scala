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

    val msg = (params.get("enable"), params.get("disable")) match {
      case (Some("true"), Some("true")) =>
        """You set tracing to be enabled and disabled at the same time.
          |Either enable (/admin/tracing?enable=true) or disable (/admin/tracing?disable=true) it.
          |""".stripMargin
      case (Some("true"), _) =>
        tracing.enable()
        "Enabled Finagle tracing"
      case (_, Some("true")) =>
        tracing.disable()
        "Disabling Finagle tracing"
      case _ =>
        """You did not set the parameter for tracing.
          |Either enable (/admin/tracing?enable=true) or disable (/admin/tracing?disable=true) it.
          |""".stripMargin
    }

    log.info(msg)
    newResponse(
      contentType = "text/html;charset=UTF-8",
      content = Buf.Utf8(msg)
    )
  }
}
