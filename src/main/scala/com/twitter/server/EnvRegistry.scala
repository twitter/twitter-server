package com.twitter.server

import com.twitter.app.App
import com.twitter.util.registry.GlobalRegistry

trait EnvRegistry { self: App =>

  premain {
    sys.env.foreach { case (key, value) =>
      GlobalRegistry.get.put(Seq("env", key), value)
    }
    sys.props.foreach { case (key, value) =>
      GlobalRegistry.get.put(Seq("system.properties", key), value)
    }
  }
}
