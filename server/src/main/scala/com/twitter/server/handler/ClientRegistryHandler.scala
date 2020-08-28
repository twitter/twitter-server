package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.client.{ClientRegistry, EndpointRegistry}
import com.twitter.finagle.http.{Request, Response, Uri}
import com.twitter.finagle.util.StackRegistry
import com.twitter.io.Buf
import com.twitter.server.model.ClientProfile
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.util.HttpUtils.{new404, newResponse}
import com.twitter.server.util.MetricSource
import com.twitter.server.view.{BalancerHtmlView, EndpointRegistryView, StackRegistryView}
import com.twitter.util.Future
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private object ClientRegistryHandler {

  private lazy val emptyEntry = Some(MetricSource.Entry("", 0.0, 0.0))

  val profileOrdering: Ordering[ClientProfile] = Ordering.by(_.successRate)

  def prettyRate(sr: Double): String =
    if (sr < 0.0) "N/A" else f"${sr * 100.0}%2.2f%%"

  def rateStyle(sr: Double): String =
    if (sr < 0.0) "sr-undefined"
    else if (sr < 0.9) "sr-bad"
    else if (sr < 0.99) "sr-poor"
    else "sr-good"

  /** Renders `profiles` in an html template. */
  def render(title: String, profiles: Seq[ClientProfile]): String =
    s"""<h4 class="header text-center">${escapeHtml(title)}</h4>
        <hr/>
        <div id="clients" class="row">
        ${(for (ClientProfile(name, addr, scope, sr, unavailable) <- profiles) yield {
      s"""<div class="col-md-3">
                  <div class="client">
                    <h4 class="name"><a href="/admin/clients/$name">${escapeHtml(name)}</a></h4>
                    <p class="dest text-muted">${escapeHtml(addr)}</p>
                    ${if (unavailable == 0) ""
      else {
        s"""<a href="/admin/metrics#$scope/loadbalancer/available"
                            data-toggle="tooltip" data-placement="top"
                            class="conn-trouble btn-xs btn-default">
                            <span class="glyphicon glyphicon-exclamation-sign"
                            aria-hidden="true"></span> ${escapeHtml(
          unavailable.toString)} unavailable endpoint(s)</a>"""
      }}
                    <hr/>
                    <div class="row">
                    <h6 class='sr-header col-xs-6'>success rate</h6>
                    <h3 class='sr-text col-xs-6 ${rateStyle(sr)}'>${prettyRate(sr)}</h3>
                    </div>
                  </div>
                </div>"""
    }).mkString("\n")}
        </div>"""

}

/**
 * Renders information about clients registered to Finagle's ClientRegistry in
 * an html fragment. Clients can be queried by passing in the client name as
 * part of the uri (ex. "/admin/clients/myclient").
 */
class ClientRegistryHandler(
  uriPrefix: String,
  source: MetricSource = new MetricSource,
  stackRegistry: StackRegistry = ClientRegistry)
    extends Service[Request, Response] {
  import ClientRegistryHandler._

  // Search the metrics source for the stat scope that includes `clientName`.
  // The search namespace includes both "$clientName/" and "clnt/$clientName"
  // to take into account finagle's ClientStatsReceiver. Note, unnamed clients are
  // ignored as we can't disambiguate their stats.
  private[this] def findClientScope(clientName: String): Option[String] = {
    val k0 = s"$clientName"
    val k1 = s"clnt/$clientName"
    if (source.contains(s"$k0/loadbalancer/adds")) Some(k0)
    else if (source.contains(s"$k1/loadbalancer/adds")) Some(k1)
    else None
  }

  // Finagle's StatFilter usually reports to the root of the `clientScope`.
  // However, the ClientBuilder API provides an easy to install retry filter
  // with a separate StatsFilter that reports to "tries".
  private[this] def findReqScope(clientScope: String): Option[String] = {
    val k0 = s"$clientScope/tries"
    val k1 = s"$clientScope"
    if (source.contains(s"$k0/requests")) Some(k0)
    else if (source.contains(s"$k1/requests")) Some(k1)
    else None
  }

  // Compute a profile for each client that includes the success rate and
  // the availability of endpoints the client connects to. The success rate
  // represents the health of the client on the request path while availability
  // is a measure of session health.
  private[this] def clientProfiles: Seq[ClientProfile] =
    (stackRegistry.registrants flatMap {
      case e: StackRegistry.Entry if e.name.nonEmpty =>
        for {
          scope <- findClientScope(e.name)
          reqScope <- findReqScope(scope)
          size <- source.get(s"$scope/loadbalancer/size")
          available <- source.get(s"$scope/loadbalancer/available")
          req <- source.get(s"$reqScope/requests")
          reqFail <- source.get(s"$reqScope/failures").orElse(emptyEntry)
          if req.value > 0.0 || (size.delta - available.delta) > 0.0
        } yield {
          val unavailable = (size.delta - available.delta).toInt
          val sr =
            if (req.value == 0.0) -1.0
            else if (req.delta == 0.0) 1.0 - reqFail.value / req.value
            else 1.0 - reqFail.delta / req.delta
          ClientProfile(e.name, e.addr, scope, sr, unavailable)
        }

      case _ => Nil
    }).toSeq

  def apply(req: Request): Future[Response] = {
    val uri = Uri.fromRequest(req)
    uri.path.stripPrefix(uriPrefix) match {
      case idx @ ("index.html" | "index.htm" | "index.txt" | "clients") =>
        val leastPerformant = clientProfiles.sorted(profileOrdering).take(4)
        val html =
          if (leastPerformant.isEmpty) ""
          else {
            render("Least Performant Downstream Clients", leastPerformant)
          }
        // This is useful to avoid the returned fragment being wrapped
        // with an index in the context of an ajax call.
        val typ = if (idx.endsWith(".txt")) "text/plain" else "text/html"
        newResponse(
          contentType = s"$typ;charset=UTF-8",
          content = Buf.Utf8(html)
        )

      case name =>
        val decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8.name)
        val clientEntries = stackRegistry.registrants.filter(_.name == decodedName)
        if (clientEntries.isEmpty) new404(s"$name could not be found.")
        else {
          val client = clientEntries.head

          val scope = findClientScope(client.name)
          val stackHtml = StackRegistryView.render(client, scope)

          val loadBalancerData = LoadBalancersHandler.getBalancer(Some(name))
          val loadBalancerView =
            new BalancerHtmlView(loadBalancerData, LoadBalancersHandler.RoutePath)
          val loadBalancerHtml = loadBalancerView.render

          val endpointEntry = EndpointRegistry.registry.endpoints(name)
          val endpointHtml = EndpointRegistryView.render(endpointEntry)

          newResponse(
            contentType = "text/html;charset=UTF-8",
            content = Buf.Utf8(stackHtml + loadBalancerHtml + endpointHtml)
          )
        }
    }
  }
}
