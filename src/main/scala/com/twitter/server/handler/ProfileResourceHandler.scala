package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.jvm.CpuProfile
import com.twitter.util.{Duration, Future, Return, Throw}
import java.util.logging.Logger
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBufferOutputStream}
import org.jboss.netty.handler.codec.http._

class ProfileResourceHandler(which: Thread.State) extends Service[HttpRequest, HttpResponse] {
  private[this] val log = Logger.getLogger(getClass.getName)
  case class Params(pause: Duration, frequency: Int)

  def apply(request: HttpRequest) = {
    val req = Request(request)
    val res = req.response
    val ret = Future.value(res)

    val params =
      req.params.foldLeft(Params(10.seconds, 100)) {
        case (params, ("seconds", pauseVal)) =>
          params.copy(pause = pauseVal.toInt.seconds)
        case (params, ("hz", hz)) =>
          params.copy(frequency = hz.toInt)
        case (params, _) =>
          params
      }

    log.info("collecting CPU profile (%s) for %s seconds at %dHz".format(
      which, params.pause, params.frequency))
    CpuProfile.recordInThread(params.pause, params.frequency, which) transform {
      case Return(prof) =>
        // Write out the profile verbatim. It's a pprof "raw" profile.
        res.setHeader("Content-Type", "pprof/raw")
        res.statusCode = 200
        res.content = ChannelBuffers.dynamicBuffer
        prof.writeGoogleProfile(new ChannelBufferOutputStream(res.content))
        ret

      case Throw(exc) =>
        res.statusCode = 500
        res.contentString = exc.toString
        ret
    }
  }
}

