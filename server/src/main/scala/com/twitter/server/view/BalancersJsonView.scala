package com.twitter.server.view

import com.twitter.finagle.loadbalancer.Metadata
import com.twitter.server.util.AdminJsonConverter

private[server] class BalancersJsonView(balancers: Seq[Metadata]) extends View {

  private[view] def renderBalancer(balancer: Metadata): Map[String, Object] =
    Map(
      "label" -> balancer.label,
      "info" -> Map(
        "balancer_class" -> balancer.balancerClass,
        "status" -> balancer.status,
        "number_available" -> balancer.numAvailable,
        "number_busy" -> balancer.numBusy,
        "number_closed" -> balancer.numClosed,
        "total_pending" -> balancer.totalPending,
        "total_load" -> balancer.totalLoad,
        "size" -> balancer.size,
        "additional_info" -> balancer.additionalInfo
      )
    )

  def render: String = {
    val clients = balancers.map(renderBalancer)
    val asMap: Map[String, Object] = Map("clients" -> clients)
    AdminJsonConverter.writeToString(asMap)
  }

}
