package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import scala.collection.JavaConverters._

class ThreadsHandler extends Service[Request, Response] {
  def apply(req: Request): Future[Response] = {
    val stacks = Thread.getAllStackTraces().asScala.map { case (thread, stack) =>
      (thread.getId().toString, Map("thread" -> thread.getName(),
                                    "daemon" -> thread.isDaemon(),
                                    "state" -> thread.getState(),
                                    "priority" -> thread.getPriority(),
                                    "stack" -> stack.toSeq.map(_.toString)))
    }.toSeq
    val msg = Map("threads" -> Map(stacks: _*))
    newOk(JsonConverter.writeToString(msg))
  }
}
