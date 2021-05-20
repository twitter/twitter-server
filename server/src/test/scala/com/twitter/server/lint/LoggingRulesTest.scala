package com.twitter.server.lint

import com.twitter.util.lint.Issue
import org.scalatest.funsuite.AnyFunSuite

class LoggingRulesTest extends AnyFunSuite {
  val validUrl = "file:/jars/slf4j-jdk14-1.7.7.jar!/org/slf4j/impl/StaticLoggerBinder.class"
  val invalidUrl = "/classes/org/slf4j/impl/StaticLoggerBinder.class"

  test("Extracts name from valid jar URL") {
    assert(LoggingRules.jarName(validUrl) == "slf4j-jdk14-1.7.7.jar")
  }

  test("Doesn't fail on invalid jar URL") {
    assert(LoggingRules.jarName(invalidUrl) == invalidUrl)
  }

  test("No issues for no paths") {
    assert(LoggingRules.issues(Seq()) == Nil)
  }

  test("No issues for a single path") {
    assert(LoggingRules.issues(Seq(validUrl)) == Nil)
  }

  test("Two issues for two paths") {
    assert(
      LoggingRules.issues(Seq(invalidUrl, validUrl)) ==
        Seq(Issue(invalidUrl), Issue("slf4j-jdk14-1.7.7.jar"))
    )
  }
}
