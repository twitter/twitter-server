package com.twitter.server

import com.twitter.util._
import java.net.{InetAddress, InetSocketAddress}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.MutableList

class TestTwitterServer extends TwitterServer {
  override val adminPort = flag("admin.port",
    new InetSocketAddress(InetAddress.getLoopbackAddress, 0), "")

  val bootstrapSeq = MutableList.empty[Symbol]

  def main() {
    bootstrapSeq += 'Main
  }

  init {
    bootstrapSeq += 'Init
  }

  premain {
    bootstrapSeq += 'PreMain
  }

  onExit {
    bootstrapSeq += 'Exit
  }

  postmain {
    bootstrapSeq += 'PostMain
  }
}

@RunWith(classOf[JUnitRunner])
class TwitterServerTest extends FunSuite {

  test("TwitterServer does not prematurely execute lifecycle hooks") {
    val twitterServer = new TestTwitterServer
    assert(twitterServer.bootstrapSeq.isEmpty)
  }

  test("TwitterServer.main(args) executes without error") {
    val twitterServer = new TestTwitterServer
    twitterServer.main(args = Array.empty[String])
    assert(twitterServer.bootstrapSeq ===
        Seq('Init, 'PreMain, 'Main, 'PostMain, 'Exit))
  }

  test("TwitterServer.main(args) executes without error when closed explicitly") {
    val twitterServer = new TestTwitterServer {
      override def main() {
        super.main()
        Await.result(close())
      }
    }

    twitterServer.main(args = Array.empty[String])
    assert(twitterServer.bootstrapSeq === Seq('Init, 'PreMain, 'Main, 'Exit, 'PostMain))
  }
}
