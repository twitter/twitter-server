package com.twitter.server.lint

import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.util.StackRegistry
import com.twitter.util.lint.{Category, Issue, Rule}

object StackRegistryDuplicatesRule {

  private[this] def isMemcacheClient(stackReg: StackRegistry)(entry: StackRegistry.Entry): Boolean =
    stackReg.registryName == ClientRegistry.registryName &&
      entry.protocolLibrary == "memcached"

  private[this] def isAllowlisted(allowlist: Set[String])(entry: StackRegistry.Entry): Boolean =
    allowlist.contains(entry.name)

  /**
   * Rule that looks for registered
   * [[com.twitter.finagle.util.StackRegistry.Entry Entries]]
   * which have the same name.
   *
   * Memcache clients with the same name are common due to the
   * sharding of Memcache servers, and so are excluded.
   *
   * @param stackReg Registry used to search for duplicates.
   * @param allowlist Set of entry [[com.twitter.finagle.param.Label names]]
   * to exclude from duplicate checking.
   */
  def apply(stackReg: StackRegistry, allowlist: Set[String]): Rule = {
    Rule(
      Category.Configuration,
      s"Duplicate ${stackReg.registryName} StackRegistry names",
      "Duplicate registry entries indicate that multiple clients or servers are " +
        "being registered with the same `com.twitter.finagle.param.Label`."
    ) {
      stackReg.registeredDuplicates
        .filterNot(isMemcacheClient(stackReg))
        .filterNot(isAllowlisted(allowlist))
        .map(e => Issue(s"name=${e.name} protocolLib=${e.protocolLibrary} addr=${e.addr}"))
    }
  }

}
