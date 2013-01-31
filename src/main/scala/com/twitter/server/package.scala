package com.twitter

/**
 * =Twitter Server=
 *
 * Provides a common configuration setup for internal Twitter servers based on
 * [[com.twitter.app.App]].
 *
 * An HTTP server is bound to a configurable port (default: 9900) to which commands can be sent
 * and information queried. Additional handlers can be provided by adding them to
 * [[com.twitter.finagle.http.HttpMuxer]].
 *
 * {{{
 * object MyServer extends TwitterServer {
 *   def main() {
 *     // start my service
 *   }
 * }
 * }}}
 *
 * =Provided handlers=
 *
 * See [[com.twitter.server.TwitterServer]]
 *
 * =Configuration=
 *
 * The default port is set via defaultAdminPort. This can be overridden in the super class
 * or set on the command line with -admin.port.
 *
 */
package object server
