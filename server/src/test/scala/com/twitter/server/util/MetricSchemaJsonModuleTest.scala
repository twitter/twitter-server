package com.twitter.server.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.ScalaObjectMapper
import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.CounterishGaugeType
import com.twitter.finagle.stats.MetricBuilder.GaugeType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats._
import org.scalatest.funsuite.AnyFunSuite

class MetricSchemaJsonModuleTest extends AnyFunSuite {

  private val counterSchema =
    MetricBuilder(
      keyIndicator = true,
      description = "Counts how many cools are seen",
      units = Requests,
      role = SourceRole.Server,
      verbosity = Verbosity.Default,
      sourceClass = Some("finagle.stats.cool"),
      name = Seq("my", "cool", "counter"),
      processPath = Some("dc/role/zone/service"),
      percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
      metricType = CounterType
    )
  private val gaugeSchema =
    MetricBuilder(
      keyIndicator = false,
      description = "Measures how fine the downstream system is",
      units = Percentage,
      role = SourceRole.Client,
      verbosity = Verbosity.Debug,
      sourceClass = Some("finagle.stats.your"),
      name = Seq("your", "fine", "gauge"),
      processPath = Some("dc/your_role/zone/your_service"),
      percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
      metricType = GaugeType
    )
  private val histogramSchema =
    MetricBuilder(
      name = Seq("my", "only", "histo"),
      percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
      metricType = HistogramType,
      histogramFormat = HistogramFormat.ShortSummary
    )

  private val topLevelFieldNameSet =
    Set(
      "name",
      "dimensional_name",
      "relative_name",
      "labels",
      "dimensional_support",
      "kind",
      "source",
      "description",
      "unit",
      "verbosity",
      "key_indicator")

  def jsonStrToMap(jsonStr: String): Map[String, Any] = {
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue[Map[String, Any]](jsonStr)
  }

  test("CounterGauge serializes with kind counter and the correct set of fields") {
    val serializedString = AdminJsonConverter.writeToString(counterSchema)
    val jsonMap = jsonStrToMap(serializedString)
    assert(jsonMap.keys == topLevelFieldNameSet)
    assert(
      jsonMap.get("source").get == Map(
        "class" -> "finagle.stats.cool",
        "category" -> "Server",
        "process_path" -> "dc/role/zone/service"))
    assert(jsonMap.get("kind").get == "counter")
  }

  test("GaugeSchema serializes with kind gauge and the correct set of fields") {
    val serializedString = AdminJsonConverter.writeToString(gaugeSchema)
    val jsonMap = jsonStrToMap(serializedString)
    assert(jsonMap.keys == topLevelFieldNameSet)
    assert(
      jsonMap.get("source").get == Map(
        "class" -> "finagle.stats.your",
        "category" -> "Client",
        "process_path" -> "dc/your_role/zone/your_service"))
    assert(jsonMap.get("kind").get == "gauge")
  }

  test(
    "HistogramSchema serializes with kind histogram and the correct set of fields (including buckets)") {
    val serializedString = AdminJsonConverter.writeToString(histogramSchema)
    val jsonMap = jsonStrToMap(serializedString)
    assert(jsonMap.keys == topLevelFieldNameSet ++ Seq("histogram_format", "buckets"))
    assert(
      jsonMap.get("source").get == Map(
        "class" -> "Unspecified",
        "category" -> "NoRoleSpecified",
        "process_path" -> "Unspecified"))
    assert(jsonMap.get("kind").get == "histogram")
    assert(jsonMap.get("histogram_format").get == "short_summary")
    assert(
      jsonMap.get("buckets").get == Map(
        "count" -> ".count",
        "0.5" -> ".p50",
        "0.99" -> ".p99",
        "0.999" -> ".p9990",
        "0.95" -> ".p95",
        "0.9" -> ".p90",
        "0.9999" -> ".p9999",
        "maximum" -> ".max",
        "average" -> ".avg",
        "minimum" -> ".min",
        "sum" -> ".sum"
      ))
  }

  test("GaugeSchema and CounterishGaugeSchema are recorded as different types") {
    val counterish = gaugeSchema.withCounterishGauge

    for (schema <- Seq(gaugeSchema, counterish)) {
      val serializedString = AdminJsonConverter.writeToString(schema)
      val jsonMap = jsonStrToMap(serializedString)
      val expected =
        if (schema.metricType == CounterishGaugeType) Some("counterish_gauge") else Some("gauge")
      assert(jsonMap.get("kind") == expected)
    }
  }

}
