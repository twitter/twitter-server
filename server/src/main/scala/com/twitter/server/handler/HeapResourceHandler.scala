package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.jvm.Heapster
import com.twitter.server.util.HttpUtils.{newResponse, parse}
import com.twitter.util.{Duration, Future}
import com.twitter.util.logging.Logger

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

    val params = parse(req.uri)._2.foldLeft(Params(10.seconds, 10 << 19, forceGC = true)) {
      case (parameters, ("pause", Seq(pauseVal))) =>
        parameters.copy(pause = pauseVal.toInt.seconds)
      case (parameters, ("sample_period", Seq(sampleVal))) =>
        parameters.copy(samplingPeriod = sampleVal.toInt)
      case (parameters, ("force_gc", Seq("no"))) =>
        parameters.copy(forceGC = false)
      case (parameters, ("force_gc", Seq("0"))) =>
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
