package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.util.LoadService

/**
 * Defines a hook into an App.
 */
trait Hook {
  def premain()
}

/**
 * Create a new hook for the given App. NewHooks are
 * service-loaded.
 */
trait NewHook extends (App => Hook)

/**
 * Mix-in to include service-loaded hooks.
 */
trait Hooks { self: App =>
  private val hooks = LoadService[NewHook]() map {
    newHook => newHook(self)
  }

  premain {
    for (hook <- hooks)
      hook.premain()
  }
}
