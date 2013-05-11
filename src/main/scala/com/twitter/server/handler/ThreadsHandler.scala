package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._
import scala.collection.JavaConverters._

class ThreadsHandler extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = {
    val stacks = Thread.getAllStackTraces().asScala.map { case (thread, stack) =>
      (thread.getId().toString, Map("thread" -> thread.getName(),
                                    "daemon" -> thread.isDaemon(),
                                    "state" -> thread.getState(),
                                    "priority" -> thread.getPriority(),
                                    "stack" -> stack.toSeq.map(_.toString)))
    }.toSeq
    val msg = Map("threads" -> Map(stacks: _*))
    Future.value(JsonConverter(msg))
  }
}
