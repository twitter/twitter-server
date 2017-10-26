package com.twitter.server

import com.twitter.app.App
import com.twitter.util.logging.Slf4jBridgeUtility

private[server] trait Slf4jBridge { self: App =>

  /** Attempt Slf4jBridgeHandler installation */
  Slf4jBridgeUtility.attemptSlf4jBridgeHandlerInstallation()
}
