package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status, Uri}
import com.twitter.io.Buf
import com.twitter.jvm.Heapster
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.logging.Logger
import com.twitter.util.{Duration, Future}

class HeapResourceHandler extends Service[Request, Response] {
  private[this] val log = Logger[HeapResourceHandler]

  case class Params(pause: Duration, samplingPeriod: Int, forceGC: Boolean)

  def apply(req: Request): Future[Response] = {
    if (Heapster.instance.isEmpty)
      return newResponse(
        status = Status.InternalServerError,
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8("heapster not loaded!")
      )

    val heapster = Heapster.instance.get

    val uri = Uri.fromRequest(req)
    val params = uri.params.foldLeft(Params(10.seconds, 10 << 19, forceGC = true)) {
      case (parameters, ("pause", pauseVal)) =>
        parameters.copy(pause = pauseVal.toInt.seconds)
      case (parameters, ("sample_period", sampleVal)) =>
        parameters.copy(samplingPeriod = sampleVal.toInt)
      case (parameters, ("force_gc", "no")) =>
        parameters.copy(forceGC = false)
      case (parameters, ("force_gc", "0")) =>
        parameters.copy(forceGC = false)
      case (parameters, _) =>
        parameters
    }

    log.info(s"[${req.uri}] collecting heap profile for ${params.pause} seconds")

    // Write out the profile verbatim. It's a pprof "raw" profile.
    val profile = heapster.profile(params.pause, params.samplingPeriod, params.forceGC)
    newResponse(contentType = "pprof/raw", content = Buf.ByteArray.Owned(profile))
  }
}
