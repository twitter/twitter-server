package com.twitter.server.handler

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.conversions.time._
import com.twitter.finagle.client.ClientRegistry
import com.twitter.finagle.util.StackRegistry
import com.twitter.finagle.Service
import com.twitter.finagle.stats.{StatEntry, StatsRegistry}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.util.LoadService
import com.twitter.io.Charsets
import com.twitter.server.responder.ResponderUtils
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http._
import scala.collection.{mutable, immutable}
import scala.util.Sorting.stableSort

/**
 * Gets a list of clients from the [[com.twitter.finagle.client.ClientRegistry ClientRegistry]] and
 * displays individual client information at <baseUrl><client name>
 */

private[server] case class ViewMetric(val name: String, val value: Double)

private[server] class Client(
  val name: String,
  val ports: String,
  val metrics: Seq[ViewMetric])

private[server] class MetricsResponse(
  val metrics: Seq[ViewMetric],
  val server: Seq[ViewMetric],
  val clients: Seq[Client])

private[server] object MetricsHandler {
  private[this] lazy val registry = LoadService[StatsRegistry]()
  private[this] val maintainCache = DefaultTimer.twitter
  private[this] var metrics = Map.empty[String, StatEntry]
  private[this] var isCached = false

  def hasRegistry(): Boolean = !registry.isEmpty

  private def getMetrics(): immutable.Map[String, StatEntry] = synchronized {
    if (!isCached) {
      metrics = registry.foldLeft(Map[String, StatEntry]()) { (map, r) =>
        map ++ r.getStats()
      }
      isCached = true
      maintainCache.schedule(1.second.fromNow) {
        isCached = false
      }
    }
    metrics.toMap
  }
}

private[server] class MetricsHandler(baseUrl: String) extends WebHandler {
  private[this] val serverNames = List("thriftmux", "thrift", "http")
  private[this] def safeDivide(a: Double, b: Double): Double =
    if (b == 0) 0.0
    else a/b

  private[this] def getMetricForClient(client: Client, metric: String): Option[Double] =
    client.metrics.find(_.name.endsWith(metric)).map(_.value)

  private[this] def sortMetricsList(
    metrics: Seq[ViewMetric],
    sortOrderDesc: Boolean
  ): Seq[ViewMetric] = {
    def compare(metric1: ViewMetric, metric2: ViewMetric): Boolean = {
      metric1.name < metric2.name
    }
    val sorted = stableSort(metrics, compare _)
    if (sortOrderDesc) sorted.reverse
    else sorted
  }

  private[this] def sortClientsList(
    clients: Seq[Client],
    sortBy: String,
    sortOrderDesc: Boolean
  ): Seq[Client] = {
    def compare(client1: Client, client2: Client): Boolean = {
      val client1Metric = getMetricForClient(client1, sortBy)
      val client2Metric = getMetricForClient(client2, sortBy)
      (client1Metric, client2Metric) match {
        case (Some(c1), Some(c2)) => c1 < c2
        case (Some(c1), None) => false
        case _ => true
      }
    }
    val sorted = stableSort(clients, compare _)
    if (sortOrderDesc) sorted.reverse
    else sorted
  }

  //get a list of metrics from a list of names
  private[this] def getMetricsList(
    names: Seq[String],
    metrics: Map[String, StatEntry]
  ): Seq[ViewMetric] =
    names flatMap { name =>
      if (name.endsWith("successRate")){
        for {
          requests <- metrics.get(name.stripSuffix("successRate") + "requests")
          successes <- metrics.get(name.stripSuffix("successRate") + "success")
        } yield ViewMetric(name, safeDivide(successes.totalValue, requests.totalValue))
      } else {
        metrics.get(name) map { metric => ViewMetric(name, metric.value) }
      }
    }

  def apply(req: HttpRequest) = {
    val response = new DefaultHttpResponse(req.getProtocolVersion, HttpResponseStatus.OK)
    val metrics = MetricsHandler.getMetrics()

    val query = req.getUri().stripPrefix(baseUrl)

    val wantMetrics = ResponderUtils.extractQueryValues("metric", query)
    val sortMetricsOrder = ResponderUtils.extractQueryValue("sortMetricsOrder", query)

    val wantServerMetrics = ResponderUtils.extractQueryValues("serverMetric", query)
    val wantClientMetrics = ResponderUtils.extractQueryValues("clientMetric", query)
    val sortClientsBy = ResponderUtils.extractQueryValue("sortClientsBy", query)
    val sortClientsOrder = ResponderUtils.extractQueryValue("sortClientsOrder", query)

    val metricsList = {
      val unsortedMetricsList = wantMetrics match {
        case "all" :: Nil => getMetricsList(metrics.keySet.toSeq, metrics)
        case _ => getMetricsList(wantMetrics, metrics)
      }
      val sortOrderDesc = sortMetricsOrder != "asc"
      sortMetricsList(unsortedMetricsList, sortOrderDesc)
    }

    // TODO:
    // This assumes servers are labeled by protocol and will not properly discover servers
    // that vary in name. We should export more detailed information about listening servers
    // from finagle that we can consume here.
    val serverMetricsList = {
      if (wantServerMetrics.isEmpty) Seq.empty else {
        wantServerMetrics flatMap { metricName: String =>
          val metricsNames: Seq[String] = serverNames map { name =>
            "srv/" + name + "/" + metricName
          }
          getMetricsList(metricsNames :+ metricName, metrics)
        }
      }
    }

    val clientsList = {
      val unsortedClientsList =
        if (wantClientMetrics.isEmpty) Seq.empty else {
          ClientRegistry.registrants map { case StackRegistry.Entry(name, port, _, _) =>
            val metricsNames = wantClientMetrics map { metricName =>
              "clnt/" + name + "/" + metricName
            }
            val metricsList = getMetricsList(metricsNames, metrics)
            new Client(name, port, metricsList)
          }
        }
      if (sortClientsBy.isEmpty) unsortedClientsList else {
        val sortOrderDesc = sortClientsOrder != "asc"
        sortClientsList(unsortedClientsList.toSeq, sortClientsBy, sortOrderDesc)
      }
    }
    val metricsResponse = new MetricsResponse(metricsList, serverMetricsList, clientsList.toSeq)
    response.setContent(copiedBuffer(JsonConverter.writeToString(metricsResponse), Charsets.Utf8))
    Future.value(response)
  }
}
