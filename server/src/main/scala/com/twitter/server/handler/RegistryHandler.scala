package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.HttpUtils.newOk
import com.twitter.util.Future
import com.twitter.util.registry.Formatter
import com.twitter.util.registry.GlobalRegistry
import com.twitter.util.registry.Registry
import com.twitter.util.registry.SimpleRegistry

private object RegistryHandler {
  sealed trait Matcher {
    def matches(key: String): Boolean
  }
  class LiteralMatcher(word: String) extends Matcher {
    def matches(key: String): Boolean = word == key
  }
  object WildcardMatcher extends Matcher {
    def matches(key: String): Boolean = true
  }
}

/**
 * A [[com.twitter.finagle.Service]] for displaying the current state of the
 * registry.
 *
 * It's intended to be used as a handler for TwitterServer.
 * As an admin endpoint, it displays the `GlobalRegistry` in JSON format.
 *
 * It takes an optional HTTP request parameter, "filter", which allows for
 * simple filtering of the returned data.
 *
 * See the
 * [[https://twitter.github.io/twitter-server/Admin.html#admin-registry-json user guide]]
 * for additional details.
 */
class RegistryHandler extends Service[Request, Response] {
  import RegistryHandler._

  // TODO: have nice default HTML rendering for json output
  def apply(req: Request): Future[Response] = {
    val filterParam = req.params.get("filter")
    newOk(jsonResponse(filterParam))
  }

  private[this] def filterRegistry(filter: Option[String]): Registry = {
    val registry = GlobalRegistry.get
    filter match {
      case None => registry
      case Some(f) =>
        val tokens = f.split("/").toList.dropWhile(_ == Formatter.RegistryKey)
        if (tokens.isEmpty) {
          registry
        } else {
          val matchers: Seq[Matcher] =
            tokens.map { t =>
              if (t == "*") WildcardMatcher
              else new LiteralMatcher(t)
            }

          val filtered = new SimpleRegistry()
          registry.foreach { entry =>
            if (matchers.length <= entry.key.length) {
              val allMatch = matchers.zip(entry.key).forall {
                case (matcher, word) =>
                  matcher.matches(word)
              }
              if (allMatch)
                filtered.put(entry.key, entry.value)
            }
          }
          filtered
        }
    }
  }

  private[handler] def jsonResponse(filter: Option[String]): String = {
    val filtered: Registry = filterRegistry(filter)
    val asMap: Map[String, Object] = Formatter.asMap(filtered)
    AdminJsonConverter.prettyObjectMapper.writeValueAsString(asMap)
  }

}
