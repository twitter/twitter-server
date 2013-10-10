package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.Logging

trait TwitterServer extends App
  with Admin
  with AdminHttpServer
  with Closer
  with Lifecycle
  with LogFormat
  with Logging
  with Stats
