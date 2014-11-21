package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.http.Status
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.jvm.Heapster
import com.twitter.server.util.HttpUtils._
import com.twitter.util.{Duration, Future}
import java.util.logging.Logger

class HeapResourceHandler extends Service[Request, Response] {
  private[this] val log = Logger.getLogger(getClass.getName)

  case class Params(pause: Duration, samplingPeriod: Int, forceGC: Boolean)

  def apply(req: Request): Future[Response] = {
    if (!Heapster.instance.isDefined)
      return newResponse(
        status = Status.InternalServerError,
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8("heapster not loaded!")
      )

    val heapster = Heapster.instance.get

    val params = parse(req.getUri)._2.foldLeft(Params(10.seconds, 10 << 19, true)) {
      case (params, ("pause", Seq(pauseVal))) =>
        params.copy(pause = pauseVal.toInt.seconds)
      case (params, ("sample_period", Seq(sampleVal))) =>
        params.copy(samplingPeriod = sampleVal.toInt)
      case (params, ("force_gc", Seq("no"))) =>
        params.copy(forceGC = false)
      case (params, ("force_gc", Seq("0"))) =>
        params.copy(forceGC = false)
      case (params, _) =>
        params
    }

    log.info(s"[${req.getUri}] collecting heap profile for ${params.pause} seconds")

    // Write out the profile verbatim. It's a pprof "raw" profile.
    val profile = heapster.profile(params.pause, params.samplingPeriod, params.forceGC)
    newResponse(contentType = "pprof/raw", content = Buf.ByteArray(profile))
  }
}
