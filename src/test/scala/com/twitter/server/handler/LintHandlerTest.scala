package com.twitter.server.handler

import com.twitter.util.lint.{Issue, Category, Rule}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LintHandlerTest extends FunSuite {

  private val ruleOk = Rule(Category.Performance, "ok", "desc") { Nil }
  private def nFailed(n: Int): Rule = Rule(Category.Performance, "failures", "desc") {
    Seq.fill(n) { Issue("fail") }
  }

  test("json metadata") {
    val handler = new LintHandler()
    val numFailures = 3
    val map = handler.jsonMapFromRules(Seq(ruleOk, nFailed(numFailures)))

    val metadata = map("lint_results")
      .asInstanceOf[Map[String, Any]]("metadata")
      .asInstanceOf[Map[String, Any]]

    assert(metadata("num_rules_run") == 2)
    assert(metadata("num_rules_ok") == 1)
    assert(metadata("num_rules_failed") == 1)
    assert(metadata("num_issues_found") == numFailures)
  }

}
