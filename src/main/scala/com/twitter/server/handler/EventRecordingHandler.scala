package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.io.Buf
import com.twitter.util.Future
import com.twitter.util.events.Sink
import com.twitter.server.util.HttpUtils.newResponse
import java.util.logging.Logger

private[server] object EventRecordingHandler {

  private val log = Logger.getLogger(getClass.getName)

  val RecordOn = "recordOn"

  val RecordOff = "recordOff"
}


/**
 * Modifies the state of the [[Sink sink's]] event recording.
 */
private[server] class EventRecordingHandler(
    sink: Sink = Sink.default)
  extends Service[Request, Response] {

  import EventRecordingHandler._

  def apply(req: Request): Future[Response] = {
    val uri = req.uri
    val reply = updateRecording(uri)

    newResponse(
      contentType = "text/html;charset=UTF-8",
      content = Buf.Utf8(reply))
  }

  private[handler] def updateRecording(uri: String): String = {
    val action = uri.split("/").last
    action match {
      case RecordOn =>
        sink.recording = true
        "recording enabled"
      case RecordOff =>
        sink.recording = false
        "recording disabled"
      case _ =>
        log.warning(s"Unknown action for event recording '$action' for uri '$uri'")
        s"unknown action '$action', allowed values: '$RecordOn', '$RecordOff'"
    }
  }

}
