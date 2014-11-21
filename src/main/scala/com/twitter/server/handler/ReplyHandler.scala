package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.server.util.HttpUtils._

class ReplyHandler(msg: String) extends Service[Request, Response] {
  def apply(req: Request) = newOk(msg)
}