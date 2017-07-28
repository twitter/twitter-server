package com.twitter.server.handler

import com.twitter.util.events.Sink
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class EventRecordingHandlerTest extends FunSuite with MockitoSugar {

  test("updateRecording") {
    val sink = mock[Sink]
    val handler = new EventRecordingHandler(sink)

    handler.updateRecording(s"/admin/events/record/${EventRecordingHandler.RecordOn}")
    verify(sink).recording_=(true)

    handler.updateRecording(s"/admin/events/record/${EventRecordingHandler.RecordOff}")
    verify(sink).recording_=(false)

    handler.updateRecording(s"/admin/events/record/foo")
    verifyNoMoreInteractions(sink)
  }

}
