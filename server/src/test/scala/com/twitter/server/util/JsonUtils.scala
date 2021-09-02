package com.twitter.server.util

import com.twitter.util.jackson.JsonDiff
import org.scalatest.{Assertion, Assertions}

private[server] object JsonUtils {
  def assertJsonResponse(actual: String, expected: String): Assertion = {
    val result = JsonDiff.diff(expected, actual)
    Assertions.assert(!result.isDefined, result.toString)
  }
}
