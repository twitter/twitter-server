package com.twitter.server

import com.twitter.app.GlobalFlag
import com.twitter.finagle.{Addr, Resolver, Name}
import com.twitter.util.Var

@deprecated(
  "Users should prefer using Dtabs which are overridable by setting the `dtab.add` flag",
  "2019-04-03")
object resolverMap
    extends GlobalFlag[Map[String, String]](
      Map.empty,
      "A list mapping service names to resolvers (gizmoduck=zk!/gizmoduck)"
    )

/**
 * Indicates that a [[com.twitter.finagle.Resolver]] was not found for the
 * given `name` using the FlagResolver.
 *
 * Resolvers are discovered via the com.twitter.server.resolverMap
 */
class NamedResolverNotFoundException(scheme: String, name: String)
    extends Exception(
      s"Resolver not found for scheme '$scheme' with name '$name'. " +
        s"resolverMap = ${resolverMap().keySet.toSeq.sorted.mkString(",")}"
    )

class FlagResolver extends Resolver {
  val scheme = "flag"

  private[this] def resolvers = resolverMap()

  def bind(arg: String): Var[Addr] = resolvers.get(arg) match {
    case Some(target) =>
      Resolver.eval(target) match {
        case Name.Bound(va) => va
        case Name.Path(_) =>
          Var.value(Addr.Failed(new IllegalArgumentException("Cannot bind to trees")))
      }
    case None =>
      val a = Addr.Failed(new NamedResolverNotFoundException(scheme, arg))
      Var.value(a)
  }
}
