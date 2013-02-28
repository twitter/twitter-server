package com.twitter.server

import com.twitter.finagle.{Group, Resolver, ResolverNotFoundException}
import com.twitter.util.{Throw, Try}
import java.net.SocketAddress

/**
 * A Twitter specific resolver using a pre-configured set of sub-resolvers
 * for each service. This allows for resolution based only on service name:
 * Resolver.resolve("twitter!gizmoduck")
 */
class TwitterResolver extends Resolver {
  val scheme = "twitter"

  private[this] val resolvers = List("flag!")

  def resolve(name: String) = {
    def tryResolve(resolvers: List[String]): Try[Group[SocketAddress]] = resolvers match {
      case Nil => Throw(new ResolverNotFoundException(name))
      case r :: rs => Resolver.resolve(r) rescue { case _ => tryResolve(rs) }
    }
    val resolution = tryResolve(resolvers.map(_ + name))

    resolution rescue { case _ =>
      // TODO: ensure we're not in production
      Resolver.resolve("local!" + name)
    }
  }
}
