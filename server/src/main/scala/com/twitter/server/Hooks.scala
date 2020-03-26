package com.twitter.server

import com.twitter.app.App
import com.twitter.finagle.util.LoadService

/**
 * Mix-in to include service-loaded hooks.
 */
trait Hooks { self: App =>
  private val hooks = LoadService[NewHook]().map { newHook => newHook(self) }

  premain {
    for (hook <- hooks)
      hook.premain()
  }

  for (hook <- hooks)
    onExit { hook.onExit() }
}
