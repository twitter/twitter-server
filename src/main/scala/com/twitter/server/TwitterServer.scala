package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.Logging
import com.twitter.finagle.http.HttpMuxer

trait TwitterServer extends App
  with Admin
  with Stats
  with HttpServer
  with Logging
