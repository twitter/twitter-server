package com.twitter.server.view

import com.twitter.finagle.loadbalancer.Metadata
import com.twitter.server.util.AdminJsonConverter

private object BalancersJsonView {
  case class Clients(clients: Seq[Balancer])
  case class Balancer(label: String, info: BalancerInfo)
  case class BalancerInfo(
    balancerClass: String,
    status: String,
    numberAvailable: Int,
    numberBusy: Int,
    numberClosed: Int,
    totalLoad: Double,
    size: Int,
    panicMode: String,
    additionalInfo: Map[String, Any])

  def convertMetadata(metadata: Metadata): Balancer =
    Balancer(
      label = metadata.label,
      info = BalancerInfo(
        balancerClass = metadata.balancerClass,
        status = metadata.status,
        numberAvailable = metadata.numAvailable,
        numberBusy = metadata.numBusy,
        numberClosed = metadata.numClosed,
        totalLoad = metadata.totalLoad,
        size = metadata.size,
        panicMode = metadata.panicMode,
        additionalInfo = metadata.additionalInfo
      )
    )
}

private[server] class BalancersJsonView(balancers: Seq[Metadata]) extends View {
  import BalancersJsonView._

  def render: String = {
    val clients = Clients(clients = balancers.map(convertMetadata))
    AdminJsonConverter.prettyObjectMapper.writeValueAsString(clients)
  }
}
