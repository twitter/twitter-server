package com.twitter.server

import com.twitter.app.App

/**
 * Create a new hook for the given App. NewHooks are service-loaded.
 *
 * To use, extend the [[NewHook]] trait and implement an apply method which
 * returns a [[Hook]] implementation, e.g.,
 *
 * Add the Hook as a service-loaded class in
 * /META-INF/services/com.twitter.server.NewHook
 *
 * {{{
 *   class MyHook extends NewHook {
 *     def apply(app: App) = new Hook {
 *
 *       override def premain(): Unit = ???
 *
 *       override def onExit(): Unit = ???
 *     }
 *   }
 * }}}
 *
 * @see [[com.twitter.server.Hook]]
 * @see [[com.twitter.finagle.util.LoadService]]
 */
trait NewHook extends (App => Hook)
