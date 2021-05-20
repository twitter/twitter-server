package com.twitter.server.util

import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.funsuite.AnyFunSuite

class HtmlUtilsTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {

  private val escapedChars = Seq('>', '<', '&', '"', '\'')

  private val escapeGen: Gen[String] = for {
    maybeBefore <- Gen.option(Gen.alphaStr)
    esc <- Gen.oneOf(escapedChars)
    maybeAfter <- Gen.option(Gen.alphaStr)
  } yield {
    maybeBefore.getOrElse("") + esc + maybeAfter.getOrElse("")
  }

  test("escapeHtml leaves Strings untouched if they do not need escaping") {
    forAll(Gen.alphaStr) { s => assert(s == HtmlUtils.escapeHtml(s)) }
  }

  test("escapeHtml escapes Strings if they need escaping") {
    forAll(escapeGen) { s =>
      whenever(s.exists(c => escapedChars.contains(c))) {
        val escaped = HtmlUtils.escapeHtml(s)
        assert(s != escaped)
        assert(
          escaped ==
            s.replace("&", "&amp;")
              .replace("'", "&#39;")
              .replace("<", "&lt;")
              .replace(">", "&gt;")
              .replace("\"", "&quot;")
        )
      }
    }
  }

}
