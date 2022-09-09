package com.twitter.server.view

import com.twitter.finagle.loadbalancer.Metadata
import com.twitter.server.util.HtmlUtils.escapeHtml

private[server] class BalancerHtmlView(balancer: Option[Metadata], routePath: String) extends View {

  private[view] def renderNoBalancerDetails: String =
    "Load balancer not found in registry."

  private[view] def renderBalancerDetails(md: Metadata): String =
    s"""
    |<table>
    |  <tr><td>Label</td><td>${escapeHtml(md.label)}</td></tr>
    |  <tr><td>Balancer Class</td><td>${escapeHtml(md.balancerClass)}</td></tr>
    |  <tr><td>Status</td><td>${escapeHtml(md.status)}</td></tr>
    |  <tr><td>Number Nodes Available</td><td>${md.numAvailable}</td></tr>
    |  <tr><td>Number Nodes Busy</td><td>${md.numBusy}</td></tr>
    |  <tr><td>Number Nodes Closed</td><td>${md.numClosed}</td></tr>
    |  <tr><td>Total load</td><td>${md.totalLoad}</td></tr>
    |  <tr><td>Size</td><td>${md.size}</td></tr>
    | <tr><td>Panic Mode</td><td>${md.panicMode}</td></tr>
    |  <tr><td><a href="$routePath?label=${escapeHtml(md.label)}">More details</a></td><td></td></tr>
    |</table>
     """.stripMargin

  private[view] def renderDetails: String = balancer match {
    case None => renderNoBalancerDetails
    case Some(metadata) => renderBalancerDetails(metadata)
  }

  def render: String =
    s"""
      |<div class="row">
      |  <div class="col-md-12">
      |    <a name="load_balancer"></a>
      |    <h3>Load Balancer</h3>
      |    ${renderDetails}
      |  </div>
      |</div>
    """.stripMargin
}
