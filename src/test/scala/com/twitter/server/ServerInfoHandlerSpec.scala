package com.twitter.server

import com.twitter.finagle.http.{Response, Request}
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.specs.SpecificationWithJUnit

class ServerInfoHandlerSpec extends SpecificationWithJUnit {
  "ServerInfo handler" should {
    "display server information" in {
      val handler = new ServerInfoHandler(this)
      val req = Request("/")
      val res = Response(handler(req)())

      res.status must be_==(HttpResponseStatus.OK)
      val info = res.contentString
      info mustMatch("\"build\" :")
      info mustMatch("\"build_revision\" :")
      info mustMatch("\"name\" :")
      info mustMatch("\"version\" :")
      info mustMatch("\"start_time\" :")
      info mustMatch("\"uptime\" :")
    }
  }
}
