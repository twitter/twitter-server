package com.twitter.server

import com.twitter.app.GlobalFlag
import com.twitter.finagle.{Addr, Resolver, ResolverNotFoundException}
import com.twitter.util.Var
import java.net.SocketAddress

// TODO: deprecate in favor of Wily dtabs.

object resolverMap extends GlobalFlag(Map[String, String](),
  "A list mapping service names to resolvers (gizmoduck=zk!/gizmoduck)")

class FlagResolver extends Resolver {
  val scheme = "flag"

  private[this] def resolvers = resolverMap()

  def bind(arg: String): Var[Addr] = resolvers.get(arg) match {
    case Some(target) =>
      Resolver.eval(target).bind()
    case None => 
      val a = Addr.Failed(new ResolverNotFoundException(arg))
      Var.value(a)
  }
}
