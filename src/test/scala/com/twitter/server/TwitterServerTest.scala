package com.twitter.server

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.MutableList


class TestTwitterServer extends TwitterServer {
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
    assert(twitterServer.bootstrapSeq === Seq('Init, 'PreMain, 'Main, 'PostMain))
  }

}
