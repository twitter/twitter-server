package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.server.handler.LintHandler.LintView
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import com.twitter.util.lint.{GlobalRules, Issue, Rule}

/**
 * UI for running the globally registered lint [[Rule Rules]].
 */
class LintHandler extends Service[Request, Response] {

  def apply(req: Request): Future[Response] =
    if (expectsHtml(req) && !expectsJson(req)) htmlResponse(req) else jsonResponse(req)

  private[this] def htmlResponse(req: Request): Future[Response] = {
    // first, run the rules.
    val rules = GlobalRules.get.iterable.toSeq
    val (oks, nots) = rules
      .map(rule => rule -> rule())
      .partition { case (_, res) => res.isEmpty }

    // render the ui and respond
    val view = new LintView(rules, oks.map(o => o._1), nots)
    val rendered = view()
    newResponse(
      // note: contentType must be explicit here because of `IndexView.isFragment`
      contentType = "text/html;charset=UTF-8",
      content = Buf.Utf8(rendered)
    )
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
    newOk(AdminJsonConverter.writeToString(jsonMap), "application/json;charset=UTF-8")
  }

  /** exposed for testing */
  private[handler] def jsonMapFromRules(rules: Seq[Rule]): Map[String, Any] = {
    val (oks, nots) = rules
      .map(rule => rule -> rule())
      .partition { case (_, res) => res.isEmpty }

    val numIssues = nots.foldLeft(0) {
      case (total, (_, issues)) =>
        total + issues.size
    }

    val failureIssues = nots.map {
      case (rule, issues) =>
        jsonRule(rule) ++
          Map("issues" -> issues.map(i => jsonString(i.details)))
    }

    val okRules = oks.map {
      case (rule, _) =>
        rule.id
    }

    Map(
      "lint_results" ->
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

private object LintHandler {

  /**
   * Web UI for [[LintHandler]].
   */
  class LintView(rules: Seq[Rule], oks: Seq[Rule], nots: Seq[(Rule, Seq[Issue])]) {

    def apply(): String = {
      scriptHeader +
        summary +
        failedRows +
        okRows
    }

    private def scriptHeader: String =
      """
<script>
  $(document).ready(function(){
    $('[data-toggle="popover"]').popover();
  });
</script>"""

    def summary: String = {
      val numIssues = nots.foldLeft(0) {
        case (total, (_, issues)) =>
          total + issues.size
      }

      s"""
<div class="row">
  <div class="col-md-4">
    <div>
      <h5>Summary</h5>
    </div>
    <div>
      <table class="table table-condensed table-hover">
        <tbody>
          <tr>
            <td><strong>Number of rules run</strong></td>
            <td>${escapeHtml(rules.size.toString)}</td>
          </tr>
          <tr>
            <td><strong>Number of rules ok</strong></td>
            <td>${escapeHtml(oks.size.toString)}</td>
          </tr>
          <tr>
            <td><strong>Number of rules failed</strong></td>
            <td>${escapeHtml(nots.size.toString)}</td>
          </tr>
          <tr>
            <td><strong>Number of issues found</strong></td>
            <td>${escapeHtml(numIssues.toString)}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</div>"""
    }

    def nameWithDescription(rule: Rule): String = {
      val desc = escapeHtml(rule.description)
      s"""
      <a href="#" data-toggle="popover" title="Description" data-content="$desc" data-trigger="hover focus">
        ${escapeHtml(rule.name)}
      </a>
      """
    }

    def failedRow(rule: Rule, issue: Issue): String = {
      s"""
      <tr>
        <td>${nameWithDescription(rule)}</td>
        <td>${escapeHtml(issue.details)}</td>
      </tr>"""
    }

    def failedRows: String = {
      val data = nots
        .map {
          case (rule, issues) =>
            issues
              .map { issue => failedRow(rule, issue) }
              .mkString("")
        }
        .mkString("")

      s"""
<div class="row">
  <div>
    <h5>Failed rules</h5>
  </div>
  <div>
    <table class="table table-hover table-condensed">
      <thead>
        <tr>
          <th>Name</th>
          <th>Issue</th>
        </tr>
      </thead>
      <tbody>
      $data
      </tbody>
    </table>
  </div>
</div>"""
    }

    def okRows: String = {
      val data = oks
        .map { rule =>
          s"""
        <tr>
          <td>
            ${nameWithDescription(rule)}
          </td>
        </tr>
        """
        }
        .mkString("")

      s"""
<div class="row">
  <div>
    <h5>Ok rules</h5>
  </div>
  <div>
    <table class="table table-hover table-condensed">
      <thead>
        <tr>
          <th>Name</th>
        </tr>
      </thead>
      <tbody>
      $data
      </tbody>
    </table>
  </div>
</div>"""
    }

  }

}
