package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.HttpUtils.{expectsHtml, expectsJson, newOk, newResponse}
import com.twitter.server.view.ThreadsView
import com.twitter.util.Future
import java.lang.management.ManagementFactory
import scala.collection.JavaConverters._

private[server] object ThreadsHandler {

  type StackTrace = Seq[StackTraceElement]

  case class ThreadInfo(thread: Thread, stack: StackTrace, isIdle: Boolean)

  private val IdleClassAndMethod: Set[(String, String)] = Set(
    ("io.netty.channel.epoll.Native", "epollWait0"),
    ("sun.nio.ch.EPollArrayWrapper", "epollWait"),
    ("sun.nio.ch.KQueueArrayWrapper", "kevent0")
  )

}

/**
 * "Controller" for displaying the current state of threads.
 *
 * Possibilities for future endeavors in the web ui:
 * - group threads by "similarity"
 * - provide a mechanism for exp/imp
 * - javascript control for searching within stacktraces
 *
 * @see [[ThreadsView]]
 */
class ThreadsHandler extends Service[Request, Response] {
  import ThreadsHandler._

  def apply(req: Request): Future[Response] =
    if (expectsHtml(req) && !expectsJson(req)) htmlResponse(req) else jsonResponse(req)

  private def jsonResponse(req: Request): Future[Response] = {
    val stacks = Thread.getAllStackTraces.asScala.map {
      case (thread, stack) =>
        thread.getId.toString ->
          Map[String, Any](
            "thread" -> thread.getName,
            "daemon" -> thread.isDaemon,
            "state" -> thread.getState,
            "priority" -> thread.getPriority,
            "stack" -> stack.toSeq.map(_.toString)
          )
    }
    val msg = Map("threads" -> stacks)
    newOk(AdminJsonConverter.writeToString(msg), "application/json;charset=UTF-8")
  }

  private def htmlResponse(req: Request): Future[Response] = {
    // first, gather the data
    val raw: Seq[ThreadInfo] =
      Thread.getAllStackTraces.asScala.toMap.map {
        case (thread, stack) =>
          ThreadInfo(thread, stack.toSeq, isIdle = false)
      }.toSeq

    val withIdle = markedIdle(raw)

    val sorted = withIdle.sortWith {
      case (t1, t2) =>
        (t1.isIdle, t2.isIdle) match {
          case (true, false) => false
          case (false, true) => true
          case _ => t1.thread.getId < t2.thread.getId
        }
    }

    val view = new ThreadsView(sorted, deadlockedIds)
    val rendered = view()

    newResponse(
      // note: contentType must be explicit here because of `IndexView.isFragment`
      contentType = "text/html;charset=UTF-8",
      content = Buf.Utf8(rendered)
    )
  }

  private def markedIdle(in: Seq[ThreadInfo]): Seq[ThreadInfo] = {
    // pretty obvious they are idle
    def idleState(state: Thread.State): Boolean =
      state == Thread.State.TIMED_WAITING || state == Thread.State.WAITING

    // Threads that say they are runnable, but are actually doing nothing.
    def idleRunnable(info: ThreadInfo): Boolean =
      info.stack.headOption.exists { elem =>
        IdleClassAndMethod.contains((elem.getClassName, elem.getMethodName))
      }

    in.map { info =>
      if (idleState(info.thread.getState) || idleRunnable(info)) {
        info.copy(isIdle = true)
      } else {
        info
      }
    }
  }

  private def deadlockedIds: Seq[Long] = {
    val ids = Option(ManagementFactory.getThreadMXBean.findDeadlockedThreads())
    ids.map(_.toSeq).getOrElse(Nil)
  }

}
