package com.twitter.server

import com.twitter.util.logging.Logger
import java.net.URL
import java.util.Properties
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/**
 * A simply utility for loading information from a build.properties file. The ClassLoader for the
 * given object is used to load the build.properties file, which is first searched for relative to
 * the given object's class's package (class-package-name/build.properties), and if not found there,
 * then it is searched for with an absolute path ("/build.properties").
 */
private[twitter] object BuildProperties {
  private[this] val log = Logger(BuildProperties.getClass)

  private[this] val basicServerInfo: Map[String, String] =
    Map(
      "name" -> "unknown",
      "version" -> "0.0",
      "build" -> "unknown",
      "build_revision" -> "unknown",
      "build_branch_name" -> "unknown",
      "merge_base" -> "unknown",
      "merge_base_commit_date" -> "unknown",
      "scm_repository" -> "unknown"
    )

  private[this] val properties: Map[String, String] = {
    val buildProperties = new Properties
    try {
      buildProperties.load(BuildProperties.getClass.getResource("build.properties").openStream)
    } catch {
      case NonFatal(_) =>
        try {
          BuildProperties.getClass.getResource("/build.properties") match {
            case resource: URL =>
              buildProperties.load(resource.openStream)
            case _ => // do nothing
          }
        } catch {
          case NonFatal(e) =>
            log.warn("Unable to load build.properties file from classpath. " + e.getMessage)
        }
    }
    basicServerInfo ++ buildProperties.asScala
  }

  /**
   * Returns the [[String]] value associated with this key or a `NoSuchElementException` if there
   * is no mapping from the given key to a value.
   *
   * @param key the key
   * @return the value associated with the given key, or a `NoSuchElementException`.
   */
  def get(key: String): String = all(key)

  /**
   * Returns the value associated with a key, or a default value if the key is not contained in the map.
   *
   * @param key the key
   * @param defaultValue a default value in case no binding for `key` is found in the map.
   * @return  the value associated with `key` if it exists, otherwise the `defaultValue`.
   */
  def get(key: String, defaultValue: String): String = all.getOrElse(key, defaultValue)

  /**
   * Return all build properties.
   */
  def all: Map[String, String] = properties
}
