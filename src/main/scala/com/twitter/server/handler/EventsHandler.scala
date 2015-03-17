package com.twitter.server.handler

import com.twitter.concurrent.Spool
import com.twitter.finagle.Service
import com.twitter.finagle.http
import com.twitter.io.{Reader, Buf}
import com.twitter.server.util.HttpUtils.{Request, Response}
import com.twitter.server.util.{HttpUtils, JsonSink}
import com.twitter.util.events.{Sink, Event}
import com.twitter.util.Future
import java.util.logging.{LogRecord, Logger}

/**
 * "Controller" for displaying the current state of the sink.
 */
private[server] class EventsHandler(sink: Sink) extends Service[Request, Response] {
  import EventsHandler._

  private[this] val log = Logger.getLogger(getClass.getName)

  def this() = this(Sink.default)

  def apply(req: Request): Future[Response] =
    if (HttpUtils.isHtml(req)) doHtml(req) else doJson(req)

  private[this] def doJson(req: Request): Future[Response] = {
    val reader = JsonSink.serialize(sink)
    val response = http.Response()
    response.contentType = "application/x-ldjson"
    response.setChunked(true)
    Reader.copy(reader, response.writer).onFailure { e =>
      log.info("Encountered an error while writing the event stream: " + e)
    }.ensure(response.writer.close())
    Future.value(response)
  }

  private[this] def doHtml(req: Request): Future[Response] = {
    val response = http.Response()
    response.contentType = "text/html;charset=UTF-8"
    response.setChunked(true)
    val reader = content(sink)
    Reader.copy(reader, response.writer).onFailure { e =>
      log.info("Encountered an error while writing the event stream: " + e)
    }.ensure(response.writer.close())
    Future.value(response)
  }
}

private object EventsHandler {
  import Spool.*::

  val columns: Seq[String] =
    Seq("Event", "When", "LongVal", "ObjectVal", "DoubleVal", "TraceID", "SpanID")

  val header: String =
    columns.mkString("<tr><th>", "</th><th>", "</th></tr>")

  def showObject(o: Object): String = o match {
    case r: LogRecord => s"${r.getLevel.toString} ${r.getMessage}"
    case _ => o.toString
  }

  def rowOf(e: Event): Buf = Buf.Utf8(Seq(
    e.etype.id,
    s"<nobr>${e.when.toString}</nobr>",
    if (e.longVal == Event.NoLong) "" else e.longVal.toString,
    if (e.objectVal == Event.NoObject) "" else showObject(e.objectVal),
    if (e.doubleVal == Event.NoDouble) "" else e.doubleVal.toString,
    if (e.traceIdVal == Event.NoTraceId) "" else e.traceIdVal.toString,
    if (e.spanIdVal == Event.NoSpanId) "" else e.spanIdVal.toString
  ).mkString("<tr><td>", "</td><td>", "</td></tr>"))

  def newline(buf: Buf): Buf = buf.concat(Buf.Utf8("\n"))

  def tableOf(sink: Sink): Spool[Buf] =
    Buf.Utf8(s"""<table class="table table-condensed table-striped">
      <caption>A log of events originating from this server process.</caption>
      <thead>$header</thead>
      <tbody>"""
    ) *:: Future.value(
      // Note: The events iterator can be potentially large, so to avoid fully
      // buffering a big HTML document, we stream it as soon as it's ready.
      // HTML tables seemingly were designed with incremental display in mind
      // (see http://tools.ietf.org/html/rfc1942), so user-agents may even be
      // able to take advantage of this and begin rendering the table earlier,
      // and progressively as rows arrive.
      Spool.fromSeq(sink.events.toSeq).map(rowOf _ andThen newline) ++
      Spool.fromSeq(Seq(Buf.Utf8("</tbody></table>")))
    )

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

  def content(sink: Sink): Reader =
    if (sink != Sink.Null) Reader.concat(tableOf(sink).map(Reader.fromBuf))
    else Reader.fromBuf(Buf.Utf8(helpPage))
}
