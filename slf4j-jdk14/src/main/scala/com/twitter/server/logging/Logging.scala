package com.twitter.server.logging

import com.twitter.app.App
import com.twitter.{logging => ctl}
import java.util.logging.Logger

trait Logging extends ctl.Logging { self: App =>

  premain {
    for (h <- Logger.getLogger("").getHandlers)
      h.setFormatter(defaultFormatter)
  }

  override val defaultFormatter: ctl.Formatter = new LogFormatter
}
