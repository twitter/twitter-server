package com.twitter.server.lint

import com.twitter.app.LoadService
import com.twitter.util.lint.{Category, Issue, Rule}

/**
 * Lint rule for duplicate calls to `LoadService.bind`.
 */
object DuplicateLoadServiceBindings {

  def apply(): Rule =
    Rule(
      Category.Configuration,
      "Duplicate calls to `LoadService.bind`",
      """
      |`LoadService.bind` allows users to specify a specific
      |implementation. If this is getting called multiple
      |times for the same interface, it indicates a setup/configuration
      |issue that may cause surprises.
    """.stripMargin
    ) {
      issues(LoadService.duplicateBindings)
    }

  /** exposed for testing */
  private[lint] def issues(dupes: Set[Class[_]]): Seq[Issue] =
    if (dupes.isEmpty) {
      Nil
    } else {
      dupes.map { dupe => Issue(s"Duplicate for interface: ${dupe.getName}") }.toSeq
    }

}
