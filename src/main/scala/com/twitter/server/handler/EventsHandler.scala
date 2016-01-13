package com.twitter.server.handler

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Service
import com.twitter.finagle.http.{ParamMap, Request, Response}
import com.twitter.finagle.tracing.SpanId
import com.twitter.io.{Reader, Buf}
import com.twitter.server.handler.EventRecordingHandler._
import com.twitter.server.util.HttpUtils.{accepts, expectsJson}
import com.twitter.server.util.{JsonSink, TraceEventSink}
import com.twitter.util.events.{Sink, Event}
import com.twitter.util.{Future, Throw, Try}
import java.util.logging.{LogRecord, Logger}

/**
 * "Controller" for displaying the current state of the sink.
 */
private[server] class EventsHandler(sink: Sink) extends Service[Request, Response] {
  import EventsHandler._

  private[this] val log = Logger.getLogger(getClass.getName)

  def this() = this(Sink.default)

  private[this] def eventFilterFromParams(params: ParamMap): EventFilter = {
    val eventTypeFilter = params.get("eventType") match {
      case Some(eventType) => EventFilter.withEventType(eventType)
      case None => EventFilter.NoEventFilter
    }

    val objectValFilter = params.get("objectVal") match {
      case Some(objectVal) =>
        EventFilter.withObjectVal(objectVal)
      case None =>
        EventFilter.NoEventFilter
    }

    val spanIdFilter = params.get("spanId") match {
      case Some(spanId) => EventFilter.withSpanId(spanId)
      case None => EventFilter.NoEventFilter
    }

    val traceIdFilter = params.get("traceId") match {
      case Some(traceId) => EventFilter.withTraceId(traceId)
      case None => EventFilter.NoEventFilter
    }

    eventTypeFilter
      .and(objectValFilter)
      .and(spanIdFilter)
      .and(traceIdFilter)
  }

  def apply(req: Request): Future[Response] =
    if (accepts(req, "trace/"))
      respond(TraceEvent, TraceEventSink.serialize(sink))
    else if (expectsJson(req))
      respond(LineDelimitedJson, JsonSink.serialize(sink))
    else
      if (!req.params.isEmpty) {
        val eventFilter = eventFilterFromParams(
          req.params.filterNot { case (k, v) => v.isEmpty } )
        respond(Html, tableBodyHtml(sink.events.toSeq.filter(eventFilter)))
      } else {
        respond(Html, Reader.fromBuf(Buf.Utf8(pageHtml(sink))))
      }

  private[this] def respond(contentType: String, reader: Reader): Future[Response] = {
    val response = Response()
    response.contentType = contentType
    response.setChunked(true)
    Reader.copy(reader, response.writer).onFailure { e =>
      log.info("Encountered an error while writing the event stream: " + e)
    }.ensure(response.writer.close())
    Future.value(response)
  }
}

private object EventsHandler {
  import AsyncStream.fromSeq
  import Percentile.annotate

  val Html = "text/html;charset=UTF-8"
  val LineDelimitedJson = "application/x-ldjson;charset=UTF-8"
  val TraceEvent = "trace/json;charset=UTF-8"

  object EventFilter {

    def withEventType(eventType: String): EventFilter = new EventFilter {
      def apply(event: Event): Boolean =
        event.etype.id.contains(eventType)
    }

    def withObjectVal(objectVal: String): EventFilter = new EventFilter {
      def apply(event: Event): Boolean =
        event.objectVal.toString.contains(objectVal)
    }

    def withSpanId(spanId: String): EventFilter = new EventFilter {
      def apply(event: Event): Boolean =
        spanId == SpanId.toString(event.spanIdVal)
    }

    def withTraceId(traceId: String): EventFilter = new EventFilter {
      def apply(event: Event): Boolean =
        traceId == SpanId.toString(event.traceIdVal)
    }

    object NoEventFilter extends EventFilter {
      def apply(event: Event): Boolean =
        true
    }
  }

  abstract class EventFilter extends (Event => Boolean) { self =>
    def apply(event: Event): Boolean

    def and(that: EventFilter): EventFilter =
      if (self == EventFilter.NoEventFilter) that
      else if (that == EventFilter.NoEventFilter) self
      else
        new EventFilter {
          def apply(event: Event): Boolean =
            self(event) && that(event)
        }
  }

  val columns: Seq[String] =
    Seq("Event", "When", "LongVal", "ObjectVal", "DoubleVal", "TraceID", "SpanID")

  val header: String =
    columns.mkString("<tr><th>", """</th><th>""", "</th><th></th></tr>") +
      """<tr>
        <td>
          <div class="filter-input-group">
            <input class="filter-input form-control" id="eventTypeFilter" type="text">
            <span class="filter-input-clear glyphicon glyphicon-remove-circle"></span>
          </div>
        </td>
        <td></td>
        <td></td>
        <td>
          <div class="filter-input-group">
            <input class="filter-input form-control" id="objectValFilter" type="text">
            <span class="filter-input-clear glyphicon glyphicon-remove-circle"></span>
          </div>
        </td>
        <td></td>
        <td>
          <div class="filter-input-group">
            <input class="filter-input form-control" id="traceIdFilter" type="text">
            <span class="filter-input-clear glyphicon glyphicon-remove-circle"></span>
          </div>
        </td>
        <td>
          <div class="filter-input-group">
            <input class="filter-input form-control" id="spanIdFilter" type="text">
            <span class="filter-input-clear glyphicon glyphicon-remove-circle"></span>
          </div>
        </td>
        <td>
          <form id="filter">
            <button id="filter-submit" class="btn btn-mini btn-primary" type="submit">Filter</button>
            <button id="filter-loading" class="btn btn-mini btn-primary"><span class="glyphicon glyphicon-refresh glyphicon-refresh-animate"></span> Loading</button>
          </form>
        </td>
      </tr>"""

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
  ).mkString("<tr><td>", "</td><td>", "</td><td></td></tr>"))

  def newline(buf: Buf): Buf = buf.concat(Buf.Utf8("\n"))


  def tableOf(events: Seq[Event]): AsyncStream[Buf] =
    // Note: The events iterator can be potentially large, so to avoid fully
    // buffering a big HTML document, we stream it as soon as it's ready.
    // HTML tables seemingly were designed with incremental display in mind
    // (see http://tools.ietf.org/html/rfc1942), so user-agents may even be
    // able to take advantage of this and begin rendering the table earlier,
    // and progressively as rows arrive.
      annotate(fromSeq(events)).map(rowOf _ andThen newline)

  def pageHtml(sink: Sink): String = """
  <h2>Events</h2>
  <p>The server publishes interesting events during its operation and this section
  displays a log of the most recent.</p>
  """ + (if (Sink.enabled) {
    val isRecording: Boolean = sink.recording
    val onCheck = if (isRecording) "checked" else ""
    val offCheck = if (isRecording) "" else "checked"
    val onLoad =
      """
      <script>
         $(document).ready(function() {
          var displayEvents = function(data) {
            $("#eventTable > tbody").html(data);
            $("#filter-loading").hide();
            $("#filter-submit").show();
          }
          var loadEvents = function() {
            $("#filter-submit").removeClass("active");
            $("#filter-submit").hide();
            $("#filter-loading").show();
            $(".filter-input").blur();
            $("#eventTable > tbody").empty();

            $.post(
              "/admin/events",
              {
                eventType: $('#eventTypeFilter').val(),
                objectVal: $('#objectValFilter').val(),
                spanId: $('#spanIdFilter').val(),
                traceId: $('#traceIdFilter').val()
              },
              displayEvents)

          }
          $('#filter').submit(function () {
            loadEvents();
            return false;
          });
          $(':input').keypress(function (e) {
            if (e.which == 13) {
              $('#filter').submit();
              return false;
            }
          });
          $(".filter-input-clear").each(function() {
            $(this).click(function() {
              $(this).prev(':input').val('');
              loadEvents();
            });
          });
          $('input:radio[name=recording]').change(function() {
            $.post("/admin/events/record/" + this.value, loadEvents)
            if (this.value == "recordOn") {
              $("#eventTable").show();
            } else {
              $("#eventTable").hide();
            }
          });
          if ($("#rec1").prop("checked") == true) {
            loadEvents();
          } else {
             $("#eventTable").hide()
          }

         });
       </script>"""
    val toggle =
      s"""
      <div>Events are only captured when recording is enabled. Current state:</div>
      <div class="radio">
        <label>
          <input type="radio" name="recording" id="rec1" value="$RecordOn" $onCheck>
          On
        </label>
      </div>
      <div class="radio">
        <label>
          <input type="radio" name="recording" id="rec2" value="$RecordOff" $offCheck>
          Off
        </label>
      </div>
    """

    val table =
      s"""<table id="eventTable" class="table table-condensed table-striped">
      <caption>A log of events originating from this server process.</caption>
      <thead>$header</thead>
      <tbody>
      </tbody>
    </table>"""

    onLoad + toggle + """
    <div class="fr-more"><a
    href="javascript:$('.fr-more-info').show(); $('.fr-more').hide()"
    >Read more...</a></div>
    <div class="fr-more-info" style="display:none"><p>Event capture is currently
    <strong>enabled</strong>. This is the default. To disable event capture,
    use the following flag.
    <dl class="dl-horizontal">
      <dt>sinkEnabled</dt>
      <dd>Turn on event capture (default: true).</dd>
    </dl></p>
    <div>Example usage:<pre><code>
    $ java -Dcom.twitter.util.events.sinkEnabled=false MyApp
    </code></pre></div></div>""" + table

  } else {
    """
    <p>Event capture is currently <strong>disabled</strong>.
    To enable event capture, use the following flags.
    <dl class="dl-horizontal">
      <dt>sinkEnabled</dt>
      <dd>Turn on event capture (default: true).</dd>
      <dt>approxNumEvents</dt>
      <dd>The number of events to keep in memory (default: 10000).</dd>
    </dl></p>
    <div>Example usage:<pre><code>
    $ java -Dcom.twitter.util.events.sinkEnabled=true \
           -Dcom.twitter.util.events.approxNumEvents=10000 \
           MyApp
    </code></pre></div>
    """
  })

  def tableBodyHtml(events: Seq[Event]): Reader =
    Reader.concat(tableOf(events).map(Reader.fromBuf))
}

private object Percentile {
  import AsyncStream.fromFuture
  import java.lang.reflect.Method

  trait Ctx {
    def StatAdd: Object
    def getHistogram(name: String): Object
    def buildSnapshot(histogram: Object): Object
    def getPercentiles(snapshot: Object): Array[Object]
    def getQuantile(percentile: Object): Double
    def getValue(percentile: Object): Long
  }

  def field(cname: String, field: String): Try[Object] =
    Try.withFatals(Class.forName(cname).getField(field).get((): Unit)) {
      case e: NoClassDefFoundError => Throw(e)
    }

  def method(cname: String, method: String, args: Class[_]*): Try[Method] =
    Try.withFatals(Class.forName(cname).getMethod(method, args:_*)) {
      case e: NoClassDefFoundError => Throw(e)
    }

  val nsStats = "com.twitter.finagle.stats"
  val nsMetrics = "com.twitter.common.metrics"
  val tryCtx: Try[Ctx] = for {
    a <- field(nsStats + ".MetricsStatsReceiver$", "MODULE$")
    b <- method(nsStats + ".MetricsStatsReceiver$", "StatAdd")
    c <- Try(b.invoke(a))
    d <- method(nsMetrics + ".Metrics", "createHistogram", classOf[String])
    e <- method(nsMetrics + ".HistogramInterface", "snapshot")
    f <- method(nsMetrics + ".Snapshot", "percentiles")
    g <- method(nsMetrics + ".Percentile", "getQuantile")
    h <- method(nsMetrics + ".Percentile", "getValue")
    i <- method(nsMetrics + ".Metrics", "root").map(_.invoke(null))
  } yield new Ctx {
    val StatAdd = c
    def getHistogram(name: String) = d.invoke(i, name)
    def buildSnapshot(histogram: Object) = e.invoke(histogram)
    def getPercentiles(snapshot: Object) = f.invoke(snapshot).asInstanceOf[Array[Object]]
    def getQuantile(percentile: Object) = g.invoke(percentile).asInstanceOf[Double]
    def getValue(percentile: Object) = h.invoke(percentile).asInstanceOf[Long]
  }

  def percentileFor(ctx: Ctx, snapshot: Object, value: Long): Double = {
    import ctx._
    val ps = getPercentiles(snapshot)
    var v = Event.NoDouble
    for (p <- ps; if value >= getValue(p)) v = getQuantile(p)
    v
  }

  type Snapshots = Map[String, Object]

  def fold(ctx: Ctx, snaps: Snapshots, es: AsyncStream[Event]): AsyncStream[Event] = {
    import ctx._
    fromFuture(es.uncons).flatMap {
      case Some((stat, tail)) if stat.etype == StatAdd =>
        val name = stat.objectVal.toString
        snaps.get(name) match {
          case None =>
            val snap = buildSnapshot(getHistogram(name))
            val d = percentileFor(ctx, snap, stat.longVal)
            stat.copy(doubleVal = d) +:: fold(ctx, snaps + (name -> snap), tail())

          case Some(snap) =>
            val d = percentileFor(ctx, snap, stat.longVal)
            stat.copy(doubleVal = d) +:: fold(ctx, snaps, tail())
        }

      case Some((e, tail)) => e +:: fold(ctx, snaps, tail())
      case None => AsyncStream.empty[Event]
    }
  }

  def annotate(events: AsyncStream[Event]): AsyncStream[Event] =
    if (tryCtx.isThrow) events else fold(tryCtx.get, Map.empty[String, Object], events)
}
