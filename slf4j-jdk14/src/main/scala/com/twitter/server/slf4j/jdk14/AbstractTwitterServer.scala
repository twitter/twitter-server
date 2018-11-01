package com.twitter.server.slf4j.jdk14

import com.twitter.server.logging.Logging
import com.twitter.server.{AbstractTwitterServer => MainAbstractTwitterServer}

/**
 * A Java-friendly version of the [[com.twitter.server.TwitterServer]] which provides a
 * backwards-compatible JUL logging implementation.
 *
 * @see [[com.twitter.server.AbstractTwitterServer]]
 */
abstract class AbstractTwitterServer extends MainAbstractTwitterServer with Logging
