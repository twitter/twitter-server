package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.loadbalancer.BalancerRegistry
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.util.HttpUtils.newOk
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future

/**
 * Renders information about clients registered in Finagle's `BalancerRegistry`.
 *
 * Filtering by client label is supported by passing a "label" query string param.
 * e.g. "/admin/balancers.json?label=cool_service"
 */
final class LoadBalancersHandler extends Service[Request, Response] {
  def apply(request: Request): Future[Response] = {
    val labelFilter = request.params.get("label")
    newOk(jsonResponse(labelFilter))
  }

  private def jsonResponse(labelFilter: Option[String]): String = {
    val filtered = labelFilter match {
      case None =>
        BalancerRegistry.get.allMetadata
      case Some(label) =>
        BalancerRegistry.get.allMetadata.filter(_.label == label)
    }
    val mds = filtered.map { md =>
      Map(
        "label" -> md.label,
        "info" -> Map(
          "balancer_class" -> md.balancerClass,
          "status" -> md.status,
          "number_available" -> md.numAvailable,
          "number_busy" -> md.numBusy,
          "number_closed" -> md.numClosed,
          "total_pending" -> md.totalPending,
          "total_load" -> md.totalLoad,
          "size" -> md.size,
          "additional_info" -> md.additionalInfo
        )
      )
    }

    val asMap: Map[String, Object] = Map("clients" -> mds)
    JsonConverter.writeToString(asMap)
  }
}

private[server] object LoadBalancersHandler {
  val RoutePath: String = "/admin/balancers.json"

  def renderHtml(clientLabel: String): String = {
    val details = BalancerRegistry.get.allMetadata.find(_.label == clientLabel) match {
      case None =>
        "Load balancer not found in registry."
      case Some(md) =>
        s"""
        |<table>
        |  <tr><td>Label</td><td>${escapeHtml(md.label)}</td></tr>
        |  <tr><td>Balancer Class</td><td>${escapeHtml(md.balancerClass)}</td></tr>
        |  <tr><td>Status</td><td>${escapeHtml(md.status)}</td></tr>
        |  <tr><td>Number Nodes Available</td><td>${md.numAvailable}</td></tr>
        |  <tr><td>Number Nodes Busy</td><td>${md.numBusy}</td></tr>
        |  <tr><td>Number Nodes Closed</td><td>${md.numClosed}</td></tr>
        |  <tr><td>Total pending requests</td><td>${md.totalPending}</td></tr>
        |  <tr><td>Total load</td><td>${md.totalLoad}</td></tr>
        |  <tr><td>Size</td><td>${md.size}</td></tr>
        |  <tr><td><a href="$RoutePath?label=${escapeHtml(md.label)}">More details</a></td><td></td></tr>
        |</table>
         """.stripMargin
    }

    s"""
      |<div class="row">
      |  <div class="col-md-12">
      |    <a name="load_balancer"></a>
      |    <h3>Load Balancer</h3>
      |    $details
      |  </div>
      |</div>
    """.stripMargin
  }
}
