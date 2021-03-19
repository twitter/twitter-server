package com.twitter.server.util

import org.scalatest
import org.scalatest.Assertions

private[server] object JsonUtils {

  // NOTE: these tests assume a specific iteration order over the registries
  // and HashMaps which IS NOT a guarantee. should these tests begin to fail
  // due to that, we will need a more robust approach to validation.
  def assertJsonResponse(actual: String, expected: String): scalatest.Assertion = {
    Assertions.assert(stripWhitespace(actual) == stripWhitespace(expected))
  }

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }
}
