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
      jarPaths.map { jarPath => Issue(jarName(jarPath)) }
    } else {
      Nil
    }
  }

  val MultipleSlf4jImpls: Rule = Rule(
    Category.Configuration,
    "Multiple Slf4j Implementations",
    "You should only depend on a single concrete implementation of the slf4j api. " +
      "See https://www.slf4j.org/codes.html#multiple_bindings"
  ) {

    // same logic as org.slf4j.LoggerFactory
    val paths = ClassLoader
      .getSystemResources("org/slf4j/impl/StaticLoggerBinder.class")
      .asScala
      .map(_.getFile)
      .toSeq

    issues(paths)
  }

  /** Run only after it has been computed that the server does not have a configured logging handler implementation. */
  val NoLoggingHandler: Rule = Rule(
    Category.Configuration,
    "Admin logging handler implementation to dynamically change log levels is not configured",
    "To configure, please add a dependency on one of the supported TwitterServer logging " +
      "implementations which will provide the ability to dynamically change the logging levels " +
      "for that implementation: logback-classic, slf4j-log4j12, or slf4j-jdk14."
  ) {
    Seq(Issue("No logging handler implementation configured."))
  }

  def multipleLoggingHandlers(names: Seq[String]): Rule = Rule(
    Category.Configuration,
    "Multiple Admin logging handler implementations detected",
    "To properly configure the ability to dynamically change log levels, please specify only " +
      "one of the supported TwitterServer logging implementations on your " +
      "classpath: logback-classic, slf4j-log4j12, or slf4j-jdk14."
  ) {
    Seq(Issue(s"Multiple logging handler implementations found: ${names.mkString(", ")}"))
  }
}
