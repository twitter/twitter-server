package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.util.Await
import com.twitter.util.lint.{Category, Issue, Rule}
import org.scalatest.funsuite.AnyFunSuite

class LintHandlerTest extends AnyFunSuite {

  private val ruleOk = Rule(Category.Performance, "ok", "desc") { Nil }
  private def nFailed(n: Int): Rule = Rule(Category.Performance, "failures", "desc") {
    Seq.fill(n) { Issue("fail") }
  }

  private val handler = new LintHandler()

  test("json metadata") {
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

  test("json request returns json response") {
    val req = Request("/admin/lint.json")
    val res = Await.result(handler(req), 1.second)
    assert(res.contentType == Some("application/json;charset=UTF-8"))
  }

}
