package com.twitter.server.lint

import com.twitter.app.App
import org.scalatest.funsuite.AnyFunSuite

/** does not have any duplicate flags */
class FlagApp extends App {
  flag[String]("flag1", "This is a flag")
  flag[Int]("flag2", "This is a different flag.")
}

/** duplicates "flag1" */
class DuplicateFlagApp extends App {
  flag[String]("flag1", "This is a flag")
  flag[Int]("flag1", "Same flag name, different type.")
}

/** duplicates "flag1" and "flag2" */
class MultipleDuplicatesFlagApp extends App {
  flag[String]("flag1", "This is a String flag")
  flag[Int]("flag1", "Same flag name, different type.")
  flag[Boolean]("flag2", "This is a boolean flag")
  flag[String]("flag2", "Same flag name, different type.")
}

/** duplicates "flag1" three times */
class SameFlagDuplicateFlagApp extends App {
  flag[String]("flag1", "This is a String flag")
  flag[Int]("flag1", "Same flag name, different type.")
  flag[Boolean]("flag1", "This is a boolean flag with the same name.")
  flag[String]("flag2", "Same flag name, different type.")
}

class DuplicateFlagDefinitionsTest extends AnyFunSuite {

  test("no duplicates") {
    val app = new FlagApp
    val computedRule = DuplicateFlagDefinitions(app)
    assert(computedRule.apply().isEmpty)
  }

  test("includes the names of the duplicates") {
    val app = new DuplicateFlagApp
    val computedRule = DuplicateFlagDefinitions(app)
    val issues = computedRule.apply()
    assert(issues.length == 1)
    assert(issues.exists(_.details.contains("flag1")))
  }

  test("includes the names of the multiple duplicates") {
    val app = new MultipleDuplicatesFlagApp
    val computedRule = DuplicateFlagDefinitions(app)
    val issues = computedRule.apply()
    assert(issues.length == 2)
    assert(issues.exists(_.details.contains("flag1")))
    assert(issues.exists(_.details.contains("flag2")))
  }

  test("includes the name of the one flag duplicated") {
    val app = new SameFlagDuplicateFlagApp
    val computedRule = DuplicateFlagDefinitions(app)
    val issues = computedRule.apply()
    assert(issues.length == 1)
    assert(issues.exists(_.details.contains("flag1")))
  }
}
