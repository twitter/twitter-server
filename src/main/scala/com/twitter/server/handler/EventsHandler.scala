package com.twitter.server.handler

import com.twitter.concurrent.exp.AsyncStream
import com.twitter.finagle.Service
import com.twitter.finagle.http
import com.twitter.finagle.tracing.SpanId
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
  import TraceEventSink._

  private[this] val log = Logger.getLogger(getClass.getName)

  def this() = this(Sink.default)

  def apply(req: Request): Future[Response] =
    Option(req.headers.get("accept")) match {
      case Some(accept) if accept.startsWith("trace/") =>
        respond(TraceEvent, TraceEventSink.serialize(sink))

      case _ =>
        if (HttpUtils.isHtml(req)) respond(Html, htmlSerialize(sink))
        else respond(LineDelimitedJson, JsonSink.serialize(sink))
    }

  private[this] def respond(contentType: String, reader: Reader): Future[Response] = {
    val response = http.Response()
    response.contentType = contentType
    response.setChunked(true)
    Reader.copy(reader, response.writer).onFailure { e =>
      log.info("Encountered an error while writing the event stream: " + e)
    }.ensure(response.writer.close())
    Future.value(response)
  }
}

private object EventsHandler {
  val Html = "text/html;charset=UTF-8"
  val LineDelimitedJson = "application/x-ldjson;charset=UTF-8"
  val TraceEvent = "trace/json;charset=UTF-8"

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
    if (e.traceIdVal == Event.NoTraceId) "" else SpanId.toString(e.traceIdVal),
    if (e.spanIdVal == Event.NoSpanId) "" else SpanId.toString(e.spanIdVal)
  ).mkString("<tr><td>", "</td><td>", "</td></tr>"))

  def newline(buf: Buf): Buf = buf.concat(Buf.Utf8("\n"))

  def tableOf(sink: Sink): AsyncStream[Buf] =
    Buf.Utf8(s"""<table class="table table-condensed table-striped">
      <caption>A log of events originating from this server process.</caption>
      <thead>$header</thead>
      <tbody>"""
    ) +:: (
      // Note: The events iterator can be potentially large, so to avoid fully
      // buffering a big HTML document, we stream it as soon as it's ready.
      // HTML tables seemingly were designed with incremental display in mind
      // (see http://tools.ietf.org/html/rfc1942), so user-agents may even be
      // able to take advantage of this and begin rendering the table earlier,
      // and progressively as rows arrive.
      AsyncStream.fromSeq(sink.events.toSeq).map(rowOf _ andThen newline) ++
      AsyncStream.of(Buf.Utf8("</tbody></table>"))
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

  def htmlSerialize(sink: Sink): Reader =
    if (sink != Sink.Null) Reader.concat(tableOf(sink).map(Reader.fromBuf))
    else Reader.fromBuf(Buf.Utf8(helpPage))
}

private object TraceEventSink {
  import EventsHandler.showObject

  val comma = Buf.Utf8(",")
  val nl = Buf.Utf8("\n")
  val leftBracket = Buf.Utf8("[")
  val sp = Buf.Utf8(" ")

  def asTraceEvent(e: Event): Buf = Buf.Utf8(
    Json.serialize(Map(
      "name" -> e.etype.id,
      "cat" -> "",
      "ph" -> "i",
      "ts" -> (e.when.inMillis * 1000).toString,
      "pid" -> e.getTraceId.getOrElse(0),
      "tid" -> e.getSpanId.getOrElse(0),
      "args" -> Map(
        Seq(
          "longVal" -> e.getLong,
          "objectVal" -> e.getObject.map(showObject),
          "doubleVal" -> e.getDouble
        ).filterNot(_._2.isEmpty):_*
      )
    ))
  )

  /**
   * Serialize a sink into the [[http://goo.gl/iN9ozV Trace Event]] format.
   */
  def serialize(sink: Sink): Reader = {
    val delim = nl.concat(comma).concat(sp)
    val events: Seq[Buf] = sink.events.toSeq.map(asTraceEvent)

    // Note: we leave out the "]" from the JSON array since it's optional. See:
    // http://goo.gl/iN9ozV#heading=h.f2f0yd51wi15.
    if (events.isEmpty) Reader.fromBuf(leftBracket) else Reader.concat(
      Reader.fromBuf(leftBracket.concat(events.head)) +::
        AsyncStream.fromSeq(events.tail.map { buf =>
          Reader.fromBuf(delim.concat(buf))
        })
    )
  }
}

private object Json {
  import com.fasterxml.jackson.annotation.JsonInclude
  import com.fasterxml.jackson.core.`type`.TypeReference
  import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}
  import com.fasterxml.jackson.databind.annotation.JsonDeserialize
  import com.fasterxml.jackson.module.scala.DefaultScalaModule
  import java.lang.reflect.{Type, ParameterizedType}

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def serialize(o: AnyRef): String = mapper.writeValueAsString(o)

  def deserialize[T: Manifest](value: String): T =
    mapper.readValue(value, typeReference[T])

  def deserialize[T: Manifest](node: JsonNode): T =
    mapper.readValue(node.traverse, typeReference[T])

  private def typeReference[T: Manifest] = new TypeReference[T] {
    override def getType = typeFromManifest(manifest[T])
  }

  private def typeFromManifest(m: Manifest[_]): Type =
    if (m.typeArguments.isEmpty) m.runtimeClass else new ParameterizedType {
      def getRawType = m.runtimeClass
      def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray
      def getOwnerType = null
    }
}
