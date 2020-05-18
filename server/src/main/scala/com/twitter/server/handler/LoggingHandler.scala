package com.twitter.server.handler

/** Marker trait for LoggingHandlers */
trait LoggingHandler extends AdminHttpMuxHandler {

  /** Implementation name */
  def name: String
}
