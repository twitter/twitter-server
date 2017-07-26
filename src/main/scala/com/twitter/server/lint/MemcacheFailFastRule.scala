package com.twitter.server.lint

import com.twitter.finagle.service.FailFastFactory
import com.twitter.finagle.util.StackRegistry
import com.twitter.util.lint.{Category, Issue, Rule}

object MemcacheFailFastRule {

  def apply(stackReg: StackRegistry): Rule = {
    Rule(
      Category.Configuration,
      "Memcache client has FailFast enabled",
      """FailFast is only appropriate when a replica set is present. Memcached
        |is usually sharded. Consider disabling FailFast or using the
        |c.t.f.Memcached.client which has recommended settings built in.""".stripMargin
    ) {
      stackReg.registrants
        .filter(_.protocolLibrary == "memcached")
        .groupBy(_.name)
        .filter {
          case (_, entries) =>
            entries.head.params[FailFastFactory.FailFast].enabled == true
        }
        .map {
          case (label, _) =>
            Issue(s"$label memcache client should disable FailFast")
        }
        .toSeq
    }
  }

}
