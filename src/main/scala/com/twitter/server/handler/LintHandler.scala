package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Response, Request}
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import com.twitter.util.lint.{Rule, GlobalRules}

/**
 * UI for running the globally registered lint [[Rule Rules]].
 */
class LintHandler extends Service[Request, Response] {

  def apply(req: Request): Future[Response] =
    if (expectsHtml(req)) htmlResponse(req) else jsonResponse(req)

  private[this] def htmlResponse(req: Request): Future[Response] = {
    // todo: an HTML version with a reasonable UI
    jsonResponse(req)
  }

  private[this] def jsonString(s: String): String =
    s.replace("\n", " ").trim

  private[this] def jsonRule(rule: Rule): Map[String, Any] =
    Map(
      "category" -> rule.category.toString,
      "id" -> rule.id,
      "name" -> rule.name,
      "description" -> jsonString(rule.description)
    )

  private[this] def jsonResponse(req: Request): Future[Response] = {
    val rules = GlobalRules.get.iterable.toSeq
    val jsonMap = jsonMapFromRules(rules)
    newOk(JsonConverter.writeToString(jsonMap))
  }

  /** exposed for testing */
  private[handler] def jsonMapFromRules(rules: Seq[Rule]): Map[String, Any] = {
    val (oks, nots) = rules
      .map(rule => rule -> rule())
      .partition { case (_, res) => res.isEmpty }

    val numIssues = nots.foldLeft(0) { case (total, (_, issues)) =>
      total + issues.size
    }

    val failureIssues = nots.map { case (rule, issues) =>
      jsonRule(rule) ++
        Map("issues" -> issues.map(i => jsonString(i.details)))
    }

    val okRules = oks.map { case (rule, _) =>
      rule.id
    }

    Map("lint_results" ->
      Map(
        "metadata" ->
          Map(
            "num_rules_run" -> rules.size,
            "num_rules_ok" -> oks.size,
            "num_rules_failed" -> nots.size,
            "num_issues_found" -> numIssues
          ),
        "failed_rules" -> failureIssues,
        "ok_rules" -> okRules
      )
    )
  }

}
