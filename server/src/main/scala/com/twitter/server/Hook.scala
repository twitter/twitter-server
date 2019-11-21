package com.twitter.server

/**
 * Defines a hook into an [[com.twitter.app.App]].
 */
abstract class Hook {

  /**
   * Executed in `App#premain` which is right before the user's main is invoked.
   */
  def premain(): Unit = ()

  /**
   * Executed in `App#onExit` which is when shutdown is requested. All registered exit hooks
   * run in parallel and are executed after all `App#postmain` functions complete.
   */
  def onExit(): Unit = ()
}
