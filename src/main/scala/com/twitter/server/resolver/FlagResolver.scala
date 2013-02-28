package com.twitter.server

import com.twitter.app.GlobalFlag
import com.twitter.finagle.{Group, Resolver, ResolverNotFoundException}
import com.twitter.util.{Try, Throw}
import java.net.SocketAddress

object resolverMap extends GlobalFlag(Map[String, String](),
  "A list mapping service names to resolvers (gizmoduck=zk!/gizmoduck)")

class FlagResolver extends Resolver {
  val scheme = "flag"

  private[this] def resolvers = resolverMap()

  def resolve(name: String): Try[Group[SocketAddress]] = resolvers.get(name) match {
    case Some(target) => Resolver.resolve(target)
    case None => Throw(new ResolverNotFoundException(name))
  }
}
