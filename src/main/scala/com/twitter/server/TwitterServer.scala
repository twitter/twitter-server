package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.Logging

trait TwitterServer extends App
  with Logging
  with EventSink
  with LogFormat
  with Hooks
  with AdminHttpServer
  with Admin
  with Lifecycle
  with Stats
