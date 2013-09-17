package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.Logging
import com.twitter.finagle.http.HttpMuxer

trait TwitterServer extends App
  with AdminHttpServer
  with Admin
  with Lifecycle
  with Stats
  with Logging