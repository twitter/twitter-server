package com.twitter.server.view

import com.twitter.server.handler.ThreadsHandler.{StackTrace, ThreadInfo}
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.view.ThreadsView._

private object ThreadsView {

  private val StackTraceRowClass = "stacktrace_row"

  private val IdleThreadStackClass = "idle_thread_stack"

  private val ScriptHeader =
    """<script type="application/javascript" src="/admin/files/js/threads.js"></script>"""

  private val ScriptFooter = s"""<script>$$('.$StackTraceRowClass').hide();</script>"""

  private[this] val StateTooltipContents =
    "See <a href='https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.State.html'>Thread.State</a>" +
      "Javadocs for details. Idle may include threads that are 'runnable' but are not doing any real " +
      "work, for example threads in epoll wait."

  private val TableHeader = s"""
    <table class="table table-hover table-condensed">
      <thead>
        <tr>
          <th><input
            type="checkbox"
            id="all_stacks_checkbox"
            onClick='toggleAllStacks("$StackTraceRowClass", "$IdleThreadStackClass")'/></th>
          <th>Id</th>
          <th>Name</th>
          <th>State <a href="#" tabindex="0" data-toggle="popover" data-placement="bottom"
            data-html="true" data-trigger="focus"
            data-content="$StateTooltipContents">
              <span class="glyphicon glyphicon-info-sign"></span>
            </a>
          </th>
          <th>Daemon <a href="#" tabindex="0" data-toggle="popover" data-placement="bottom"
            data-html="true" data-trigger="focus"
            data-content="See <a href='https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#isDaemon()'>Thread.isDaemon</a> Javadocs for details">
              <span class="glyphicon glyphicon-info-sign"></span>
            </a>
          </th>
          <th>Priority</th>
        </tr>
      </thead>
      <tbody>"""

  private val TableFooter = "</tbody></table>"

}

/**
 * The web UI for [[com.twitter.server.handler.ThreadsHandler]].
 *
 * @param deadlockedIds Each value corresponds to a `Thread.getId` that is deadlocked.
 */
private[server] class ThreadsView(all: Seq[ThreadInfo], deadlockedIds: Seq[Long]) {
  private def summary: String = {
    val filtered = all.filter { info => deadlockedIds.contains(info.thread.getId) }

    val deadlockLinks =
      if (filtered.isEmpty) "none"
      else {
        filtered
          .map { info =>
            val id = info.thread.getId
            s"""<a href='#threadId-$id'>${escapeHtml(id.toString)}</a>"""
          }
          .mkString(", ")
      }

    s"""
<div class="row">
  <div class="col-md-4">
    <div><h5>Summary</h5></div>
    <div>
      <table class="table table-condensed table-hover">
        <tr>
          <td><strong>Total threads</strong></td>
          <td>${all.size}</td>
        </tr>
        <tr>
          <td><strong>Number active</strong></td>
          <td>${all.count(!_.isIdle)}</td>
        </tr>
        <tr>
          <td><label for="toggle_all_idle">Show idle</label></td>
          <td><input id="toggle_all_idle" type="checkbox" onclick="toggleIdle('$IdleThreadStackClass')" checked="true"></td>
        </tr>
        <tr>
          <td><strong>Deadlocks</strong></td>
          <td>$deadlockLinks</td>
        </tr>
      </table>
    </div>
  </div>
</div>"""
  }

  private def row(t: ThreadInfo): String = {
    def stackTrace(stack: StackTrace): String =
      if (stack.isEmpty) {
        "[no stacktrace]"
      } else {
        stack.map(_.toString).map(escapeHtml).mkString("<br>")
      }

    val rowClassStyle = if (t.isIdle) "" else "success"
    val rowClassIdle = if (t.isIdle) "idle_thread" else ""
    val rowClassStackIdle = if (t.isIdle) IdleThreadStackClass else ""

    val thread = t.thread
    val daemonText =
      if (thread.isDaemon) "Yes" else "<strong><span class=\"text-danger\">No</span></strong>"
    val threadState =
      s"${if (t.isIdle) "Idle" else "Active"} <small>(${escapeHtml(thread.getState.toString)})</small>"
    val domId = s"threadId-${thread.getId}"
    val stackDomId = s"threadId-stack-${thread.getId}"

    s"""
     <tr class="$rowClassStyle $rowClassIdle">
      <td>
        <a name="$domId" href="#" onClick="toggleStack('$stackDomId'); return false;">
          <span class="glyphicon glyphicon-expand"></span> <span class="text-muted"><small>Stack</small></span>
        </a>
      </td>
      <td>${thread.getId.toString}</td>
      <td>${thread.getName}</td>
      <td>${threadState}</td>
      <td>${daemonText}</td>
      <td>${thread.getPriority.toString}</td>
    </tr>
    <tr id="$stackDomId" class="$rowClassStyle $rowClassStackIdle $StackTraceRowClass">
      <td colspan="6">
        <pre>${stackTrace(t.stack)}</pre>
      </td>
    </tr>"""
  }

  def apply(): String = {
    val tableRows = all.map(row).mkString("")

    ScriptHeader +
      summary +
      TableHeader +
      tableRows +
      TableFooter +
      ScriptFooter
  }

}
