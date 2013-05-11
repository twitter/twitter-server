package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.jvm.Heapster
import com.twitter.util.{Duration, Future}
import java.util.logging.Logger
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBufferOutputStream}
import org.jboss.netty.handler.codec.http._

class HeapResourceHandler extends Service[HttpRequest, HttpResponse] {
  private val log = Logger.getLogger(getClass.getName)
  case class Params(pause: Duration, samplingPeriod: Int, forceGC: Boolean)

  def apply(request: HttpRequest): Future[HttpResponse] = {
    val req = Request(request)
    val res = req.response
    val ret = Future.value(res)

    if (!Heapster.instance.isDefined) {
      res.statusCode = 500
      res.contentString = "heapster not loaded!"
      return ret
    }

    val heapster = Heapster.instance.get

    val params =
      req.params.foldLeft(Params(10.seconds, 10 << 19, true)) {
        case (params, ("pause", pauseVal)) =>
          params.copy(pause = pauseVal.toInt.seconds)
        case (params, ("sample_period", sampleVal)) =>
          params.copy(samplingPeriod = sampleVal.toInt)
        case (params, ("force_gc", "no")) =>
          params.copy(forceGC = false)
        case (params, ("force_gc", "0")) =>
          params.copy(forceGC = false)
        case (params, _) =>
          params
      }

    log.info("collecting heap profile for %s seconds".format(params.pause))
    val profile = heapster.profile(params.pause, params.samplingPeriod, params.forceGC)

    // Write out the profile verbatim. It's a pprof "raw" profile.
    res.setHeader("Content-Type", "pprof/raw")
    res.statusCode = 200
    res.content = ChannelBuffers.dynamicBuffer
    val output = new ChannelBufferOutputStream(res.content)
    output.write(profile)
    ret
  }
}
