package com.twitter.server.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.finagle.stats._
import org.scalatest.FunSuite

class MetricSchemaJsonModuleTest extends FunSuite {

  private val counterSchema = CounterSchema(
    new MetricBuilder(
      keyIndicator = true,
      description = "Counts how many cools are seen",
      units = Requests,
      role = Server,
      verbosity = Verbosity.Default,
      sourceClass = Some("finagle.stats.cool"),
      name = Seq("my", "cool", "counter"),
      processPath = Some("dc/role/zone/service"),
      percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
      statsReceiver = null
    ))
  private val gaugeSchema = GaugeSchema(
    new MetricBuilder(
      keyIndicator = false,
      description = "Measures how fine the downstream system is",
      units = Percentage,
      role = Client,
      verbosity = Verbosity.Debug,
      sourceClass = Some("finagle.stats.your"),
      name = Seq("your", "fine", "gauge"),
      processPath = Some("dc/your_role/zone/your_service"),
      percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
      statsReceiver = null
    ))
  private val histogramSchema = HistogramSchema(
    new MetricBuilder(
      name = Seq("my", "only", "histo"),
      percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
      statsReceiver = null
    ))

  private val topLevelFieldNameSet =
    Set("name", "kind", "source", "description", "unit", "verbosity", "key_indicator")

  def jsonStrToMap(jsonStr: String): Map[String, Any] = {
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue[Map[String, Any]](jsonStr)
  }

  test("CounterGauge serializes with kind counter and the correct set of fields") {
    val serializedString = JsonConverter.writeToString(counterSchema)
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
    val serializedString = JsonConverter.writeToString(gaugeSchema)
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
    "HistogramSchema serializes with kind histogram and the correct set of fields (includeing buckets)") {
    val serializedString = JsonConverter.writeToString(histogramSchema)
    val jsonMap = jsonStrToMap(serializedString)
    assert(jsonMap.keys == topLevelFieldNameSet ++ Seq("buckets"))
    assert(
      jsonMap.get("source").get == Map(
        "class" -> "Unspecified",
        "category" -> "NoRoleSpecified",
        "process_path" -> "Unspecified"))
    assert(jsonMap.get("kind").get == "histogram")
    assert(jsonMap.get("buckets").get == IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999))
  }

}
