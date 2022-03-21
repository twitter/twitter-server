package com.twitter.server.logging

import com.twitter.app.App
import com.twitter.{logging => ctl}
import java.util.logging.Logger

trait Logging extends ctl.Logging { self: App =>

  /** ensure when this trait is used there is a defined logger */
  override lazy val log: ctl.Logger = ctl.Logger(name)

  /**
   * Note: we are applying the `defaultFormatter` to any
   * configured handlers on the ROOT logger in the constructor
   * to apply the desired formatting as early as possible to
   * logged statements.
   *
   * A consequence of this is that overrides of the `defaultFormatter`
   * cannot use flags for configuration of a formatter as flags are
   * not parsed until later in the App lifecycle and thus would only
   * have their default values (if any) when the `defaultFormatter`
   * function is called here.
   */
  {
    val formatter = defaultFormatter
    for (h <- Logger.getLogger("").getHandlers)
      h.setFormatter(formatter)
  }

  override def defaultFormatter: ctl.Formatter = new LogFormatter
}
