package com.twitter.server

import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util._
import java.util.concurrent.Executors
import org.scalatest.FunSuite
import scala.collection.mutable
import scala.util.control.NonFatal

class TestTwitterServer(await: Boolean = false) extends TwitterServer {
  override val defaultAdminPort = 0
  /* ensure enough time to close resources */
  override val defaultCloseGracePeriod: Duration = 30.seconds

  val bootstrapSeq: mutable.ArrayBuffer[Symbol] = mutable.ArrayBuffer.empty[Symbol]
  val value: Promise[Unit] = new Promise[Unit]

  def main(): Unit = {
    bootstrapSeq += 'Main
    if (await) {
      value.setDone()
      // block to mirror listening server await
      Await.ready(Future.never)
    }
  }

  init {
    bootstrapSeq += 'Init
  }

  premain {
    bootstrapSeq += 'PreMain
  }

  postmain {
    bootstrapSeq += 'PostMain
  }

  onExit {
    bootstrapSeq += 'Exit
  }

  onExitLast {
    bootstrapSeq += 'ExitLast
  }
}

class MockExceptionHandler extends Service[Request, Response] {
  val pattern = "/exception_please.json"
  def apply(req: Request): Future[Response] = {
    throw new Exception("test exception")
  }
}
class TwitterServerTest extends FunSuite {
  test("TwitterServer does not prematurely execute lifecycle hooks") {
    val twitterServer = new TestTwitterServer()
    assert(twitterServer.bootstrapSeq.isEmpty)
  }

  test("TwitterServer.main(args) executes without error") {
    val twitterServer = new TestTwitterServer()
    twitterServer.main(args = Array.empty[String])
    assert(
      twitterServer.bootstrapSeq ==
        Seq('Init, 'PreMain, 'Main, 'PostMain, 'Exit, 'ExitLast)
    )
  }

  test("TwitterServer.main(args) executes without error when closed explicitly") {
    val twitterServer = new TestTwitterServer() {
      override def main(): Unit = {
        super.main()
        Await.result(close(), 5.seconds)
      }
    }

    twitterServer.main(args = Array.empty[String])
    // PostMain happens last here because close() is called in the main above which calls onExit and onExitLast
    assert(twitterServer.bootstrapSeq == Seq('Init, 'PreMain, 'Main, 'Exit, 'ExitLast, 'PostMain))
  }

  test("TwitterServer.main(args) executes without error when blocking and closed") {
    val twitterServer = new TestTwitterServer(await = true)
    val pool = new ExecutorServiceFuturePool(
      Executors.newCachedThreadPool(new NamedPoolThreadFactory("Test " + getClass.getSimpleName)))

    try {
      // this will block indefinitely
      pool {
        twitterServer.main(args = Array.empty[String])
      }

      val close =
        twitterServer.value
          .flatMap(_ => twitterServer.close(twitterServer.defaultCloseGracePeriod))
      Await.result(close, 5.seconds)
      // note we do not get to the PostMain because the server is blocking and
      // closed outside of the main method. exit blocks are called explicitly by close()
      // thus postMains are effectively skipped.
      assert(twitterServer.bootstrapSeq == Seq('Init, 'PreMain, 'Main, 'Exit, 'ExitLast))
    } finally {
      try {
        pool.executor.shutdown()
      } catch {
        case NonFatal(_) => // do nothing
      }
    }
  }
}
