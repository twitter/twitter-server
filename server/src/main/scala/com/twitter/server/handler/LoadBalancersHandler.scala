package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.loadbalancer.{BalancerRegistry, Metadata}
import com.twitter.server.util.HttpUtils.newOk
import com.twitter.server.view.BalancersJsonView
import com.twitter.util.Future

/**
 * Renders information about clients registered in Finagle's `BalancerRegistry`.
 *
 * Filtering by client label is supported by passing a "label" query string param.
 * e.g. "/admin/balancers.json?label=cool_service"
 */
final class LoadBalancersHandler extends Service[Request, Response] {

  import LoadBalancersHandler._

  def apply(request: Request): Future[Response] = {
    val filter = request.params.get("label")
    val balancers = getBalancers(filter)
    val view = new BalancersJsonView(balancers)
    val content = view.render
    newOk(content)
  }

}

private[server] object LoadBalancersHandler {
  val RoutePath: String = "/admin/balancers.json"

  def getBalancers(filter: Option[String]): Seq[Metadata] = filter match {
    case None => BalancerRegistry.get.allMetadata
    case Some(label) => BalancerRegistry.get.allMetadata.filter(_.label == label)
  }

  def getBalancer(filter: Option[String]): Option[Metadata] = getBalancers(filter).headOption

}
