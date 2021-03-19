package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.lint.{GlobalRules, Rule}
import com.twitter.util.{Future, Time}

/**
 * Renders failed lint rule alert in an html fragment onto /admin/failedlint.
 */
class FailedLintRuleHandler extends Service[Request, Response] {

  private[this] val Ttl = 5.minutes
  @volatile private[this] var reloadAfter = Time.Bottom
  @volatile private[this] var failedRules: Seq[Rule] = Nil

  private[this] def failedLintRules: Seq[Rule] = {
    val time = Time.now
    if (time > reloadAfter) synchronized {
      if (time > reloadAfter) {
        failedRules = buildFailedRules
        reloadAfter = time + Ttl
      }
    }
    failedRules
  }

  private[this] def buildFailedRules: Seq[Rule] = {
    val rules = GlobalRules.get.iterable
    rules.filter(_().nonEmpty).toSeq
  }

  def apply(req: Request): Future[Response] = {
    val failedRules = failedLintRules
    val res =
      if (failedRules.isEmpty) ""
      else {
        s"""<div class="alert alert-warning alert-dismissable fade in" role="alert">
        <button type="button" class="close" data-dismiss="alert" aria-label="Close">
          <span aria-hidden="true">&times;</span>
        </button>
        <strong>WARNING: ${failedRules.length} Lint Error(s) Found</strong>
        <br/>
        <p>
          ${(for (rule <- failedRules) yield {
          s"<li>${rule.name}</li>"
        }).mkString("\n")}
          For more information, please see the <a href="/admin/lint">lint</a> page.
        </p>
      </div>"""
      }

    newResponse(
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Utf8(res)
    )
  }

}
