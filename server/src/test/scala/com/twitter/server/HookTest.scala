package com.twitter.server

import com.twitter.conversions.DurationOps._
import com.twitter.util.Duration
import org.scalatest.FunSuite
import scala.collection.mutable

class OnExitHook1 extends Hook {
  override def onExit(): Unit = {
    throw new Exception("onExit 1")
  }
}

class OnExitHook2 extends Hook {
  override def onExit(): Unit = {
    throw new Exception("onExit 2")
  }
}

class HookTestTwitterServer(hooks: Seq[Hook]) extends TwitterServer {
  override val defaultAdminPort = 0
  /* ensure enough time to close resources */
  override val defaultCloseGracePeriod: Duration = 30.seconds

  val bootstrapSeq: mutable.ArrayBuffer[Symbol] = mutable.ArrayBuffer.empty[Symbol]

  def main(): Unit = {
    bootstrapSeq += 'Main
  }

  override def exitOnError(throwable: Throwable): Unit = {
    throw throwable
  }

  init {
    bootstrapSeq += 'Init
  }

  premain {
    bootstrapSeq += 'PreMain
    for (hook <- hooks)
      hook.premain()
  }

  onExit {
    bootstrapSeq += 'Exit
  }

  for (hook <- hooks)
    onExit { hook.onExit() }

  postmain {
    bootstrapSeq += 'PostMain
  }
}

class HookTest extends FunSuite {

  val hooks: Seq[Hook] = Seq(new OnExitHook1(), new OnExitHook2())

  test("Hooks which error in OnExit are properly captured in onExit blocks") {
    val twitterServer = new HookTestTwitterServer(hooks)

    val e = intercept[Exception] {
      twitterServer.main(args = Array.empty[String])
    }

    assert(e.getSuppressed.length == 2)
    assert(
      twitterServer.bootstrapSeq ==
        Seq('Init, 'PreMain, 'Main, 'PostMain, 'Exit)
    )
  }
}
