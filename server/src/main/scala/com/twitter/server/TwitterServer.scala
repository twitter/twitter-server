package com.twitter.server

import com.twitter.app.App
import com.twitter.app.lifecycle.Event
import com.twitter.app.lifecycle.Event.PreMain
import com.twitter.finagle.DtabFlags
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.Timer
import com.twitter.util.logging.Logging
import com.twitter.util.logging.Slf4jBridge

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
 *
 * Note: the Slf4jBridge trait MUST be defined first to properly bridge legacy
 * logging APIs.
 */
trait TwitterServer
    extends App
    with Slf4jBridge
    with Logging
    with Stats
    with Linters
    with DtabFlags
    with Hooks
    with AdminHttpServer
    with Lifecycle {

  /** Don't let applications opt-out */
  final override val suppressGracefulShutdownErrors: Boolean = false

  /** Use the Finagle DefaultTimer */
  override protected lazy val shutdownTimer: Timer = DefaultTimer

  // The `main()` will block until `close()` is initiated, so we need
  // to use the last phase that completes prior to Main, which is PreMain.
  protected[twitter] def startupCompletionEvent: Event = PreMain
}

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
   * Called prior to application exiting after `onExit`.
   */
  def onExitLast(): Unit = ()

  /**
   * The `main` method of this server.
   */
  @throws[Throwable]
  def main(): Unit = ()

  init(onInit())
  premain(preMain())
  postmain(postMain())
  onExit(onExit())
  onExitLast(onExitLast())
}
