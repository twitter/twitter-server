package com.twitter.server.lint

import com.twitter.app.App
import com.twitter.util.lint.{Category, Issue, Rule}

object DuplicateFlagDefinitions {

  def apply(app: App): Rule = {
    Rule(
      Category.Configuration,
      "Duplicate flags registered with the same name",
      """
        |Multiple flags were registered with the same Flag name.
        |Registering multiple flags with the same name can lead
        |to unexpected behavior and is not recommended.
      """.stripMargin
    ) {
      issues(app.flag.registeredDuplicates)
    }
  }

  private def issues(dupes: Set[String]): Seq[Issue] =
    if (dupes.isEmpty) {
      Nil
    } else {
      dupes.map { dupe => Issue(s"Duplicate flag named: $dupe") }.toSeq
    }
}
