package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http
import com.twitter.io.{Reader, Buf}
import com.twitter.server.util.{HttpUtils, JsonSink}
import com.twitter.server.util.HttpUtils.{Request, Response}
import com.twitter.util.events.{Sink, Event}
import com.twitter.util.{Future, Throw, Return}
import java.util.logging.LogRecord

/**
 * "Controller" for displaying the current state of the sink.
 */
class EventsHandler extends Service[Request, Response] {
  import EventsHandler._

  def apply(req: Request): Future[Response] =
    if (HttpUtils.isHtml(req)) doHtml(req) else doJson(req)

  private[this] def doJson(req: Request): Future[Response] = {
    val reader = JsonSink.serialize(Sink.default)
    val response = http.Response()
    response.contentType = "application/x-ldjson"
    response.setChunked(true)
    Reader.copy(reader, response.writer) ensure response.writer.close()
    Future.value(response)
  }

  private[this] def doHtml(req: Request): Future[Response] =
    HttpUtils.newResponse(
      contentType = "text/html;charset=UTF-8",
      content = content(Sink.default)
    )
}

private object EventsHandler {
  val columns: Seq[String] =
    Seq("Event", "When", "LongVal", "ObjectVal", "DoubleVal", "TraceID", "SpanID")

  val header: String =
    columns.mkString("<tr><th>", "</th><th>", "</th></tr>")

  def showObject(o: Object): String = o match {
    case r: LogRecord => s"${r.getLevel.toString} ${r.getMessage}"
    case _ => o.toString
  }

  def rowOf(e: Event): String = Seq(
    e.etype.id,
    s"<nobr>${e.when.toString}</nobr>",
    if (e.longVal == Event.NoLong) "" else e.longVal.toString,
    if (e.objectVal == Event.NoObject) "" else showObject(e.objectVal),
    if (e.doubleVal == Event.NoDouble) "" else e.doubleVal.toString,
    if (e.traceIdVal == Event.NoTraceId) "" else e.traceIdVal.toString,
    if (e.spanIdVal == Event.NoSpanId) "" else e.spanIdVal.toString
  ).mkString("<tr><td>", "</td><td>", "</td></tr>")

  def tableOf(sink: Sink): String = s"""
    <table class="table table-condensed table-striped">
      <caption>A log of events originating from this server process.</caption>
      <thead>$header</thead>
      <tbody>${sink.events.map(rowOf).mkString("\n")}</tbody>
    </table>"""

  def helpPage: String = """
  <h2>Events</h2>
  <p>The server publishes interesting events during its operation and this section
  displays a log of the most recent.</p>
  <p>Event capture is currently disabled. To enable it, specify the following
  options when starting the server.
  <dl class="dl-horizontal">
    <dt>sinkEnabled</dt>
    <dd>Turn on event capture.</dd>
    <dt>approxNumEvents</dt>
    <dd>The number of events to keep in memory.</dd>
  </dl>
  Example usage:
  </p>
  <pre><code>
  $ java -Dcom.twitter.util.events.sinkEnabled=true \
         -Dcom.twitter.util.events.approxNumEvents=10000 \
         MyApp
  </code></pre>
  """

  def content(sink: Sink): Buf =
    if (Sink.enabled) Buf.Utf8(tableOf(sink))
    else Buf.Utf8(helpPage)
}
