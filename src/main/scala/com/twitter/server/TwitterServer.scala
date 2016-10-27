package com.twitter.server

import com.twitter.app.App
import com.twitter.logging.Logging

/**
 * Twitter Server defines a template from which servers at Twitter are built.
 * It provides common application components such as an administrative HTTP
 * server, tracing, stats, etc. These features are wired in correctly for
 * use in production at Twitter.
 *
 * For DI (Dependency Injection) Twitter Server uses self-typed Scala traits
 * that might be mixed in the `TwitterServer` trait. The common practice is
 * to define self-typed traits against the [[App]] trait as shown below.
 *
 * {{{
 *   import com.twitter.app.App
 *   import com.twitter.server.TwitterServer
 *
 *   trait MyModule { self: App =>
 *     // module logic
 *   }
 *
 *   object MyApp extends TwitterServer with MyModule {
 *     // app logic
 *   }
 * }}}
 */
trait TwitterServer extends App
  with Linters
  with Logging
  with EventSink
  with LogFormat
  with Hooks
  with AdminHttpServer
  with Admin
  with Lifecycle
  with Stats

/**
 * A Java-friendly version of the [[TwitterServer]].
 *
 * In addition to [[TwitterServer]], this abstract class defines its own
 * Java-friendly lifecycle methods `onInit`, `preMain`, `postMain` and
 * `onExit` that might be overridden in a concrete class.
 *
 * In order to launch the `AbstractTwitterServer` instance, the `main`
 * method should be explicitly defined. It makes sense to define it
 * within an inner class `Main` as shown below.
 *
 * {{{
 *   public class JavaServer extends AbstractTwitterServer {
 *     public static class Main {
 *       public static void main(String[] args) {
 *         new JavaServer().main(args);
 *       }
 *     }
 *   }
 * }}}
 *
 * The `Main` class containing the `main` method may be launched via
 * `java JavaServer$Main`.
 */
abstract class AbstractTwitterServer extends TwitterServer {

  /**
   * Called prior to application initialization.
   */
  def onInit(): Unit = ()

  /**
   * Called before the `main` method.
   */
  def preMain(): Unit = ()

  /**
   * Called after the `main` method.
   */
  def postMain(): Unit = ()

  /**
   * Called prior to application exiting.
   */
  def onExit(): Unit = ()

  /**
   * The `main` method of this server.
   */
  @throws[Throwable]
  def main(): Unit = ()

  init(onInit())
  premain(preMain())
  postmain(postMain())
  onExit(onExit())
}
