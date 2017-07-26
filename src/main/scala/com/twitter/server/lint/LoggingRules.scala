package com.twitter.server.lint

import com.twitter.util.lint.{Category, Issue, Rule}
import scala.collection.JavaConverters._

object LoggingRules {
  private val jarNameRegex = ".*/([^/]+\\.jar)!.*".r

  private[lint] def jarName(url: String): String = {
    url match {
      case jarNameRegex(name) => name
      case _ => url
    }
  }

  private[lint] def issues(jarPaths: Seq[String]): Seq[Issue] = {
    if (jarPaths.length > 1) {
      jarPaths.map { jarPath =>
        Issue(jarName(jarPath))
      }
    } else {
      Nil
    }
  }

  val MultipleSlf4jImpls = Rule(
    Category.Configuration,
    "Multiple Slf4j Implementations",
    "You should only depend on a single concrete implementation of the slf4j api. " +
      "See http://www.slf4j.org/codes.html#multiple_bindings"
  ) {

    // same logic as org.slf4j.LoggerFactory
    val paths = ClassLoader
      .getSystemResources("org/slf4j/impl/StaticLoggerBinder.class")
      .asScala
      .map(_.getFile)
      .toSeq

    issues(paths)
  }
}
