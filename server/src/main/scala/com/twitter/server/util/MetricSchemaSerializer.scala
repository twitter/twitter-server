package com.twitter.server.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.twitter.finagle.stats.{
  CounterSchema,
  GaugeSchema,
  HistogramSchema,
  MetricSchema,
  StatsFormatter,
  metadataScopeSeparator
}

object SchemaSerializer extends StdSerializer[MetricSchema](classOf[MetricSchema]) {

  private[this] val statsFormatter = StatsFormatter.default

  /**
   * This custom serializer is used to convert MetricSchemas to JSON for the metric_metadata
   * endpoint.
   */
  // The impetus for a customer serializer over case class serde is:
  //    1) The nested nature of MetricBuilder with MetricSchema means we do not have the ability to
  //       make decisions about which MetricBuilder fields to include based on MetricSchema type
  //       (ie, buckets should only be present for HistogramSchema).
  //    2) Reshaping the MetricBuilder to be the top level JSON object removes the ability to inject
  //       the "kind" field based on the MetricSchema type.
  //    3) The MetricBuilder class would need to be reworked to have a Source case class
  //       nested in it which would contain a few of the currently MetricBuilder level values.
  def serialize(
    metricSchema: MetricSchema,
    jsonGenerator: JsonGenerator,
    serializerProvider: SerializerProvider
  ): Unit = {
    val formattedName = metricSchema.metricBuilder.name.mkString(metadataScopeSeparator())
    jsonGenerator.writeStartObject()
    jsonGenerator.writeStringField("name", formattedName)
    jsonGenerator.writeArrayFieldStart("relative_name")
    if (metricSchema.metricBuilder.relativeName != Seq.empty) {
      metricSchema.metricBuilder.relativeName.foreach(segment =>
        jsonGenerator.writeString(convertNullToString(segment)))
    } else {
      metricSchema.metricBuilder.name.foreach(segment =>
        jsonGenerator.writeString(convertNullToString(segment)))
    }
    jsonGenerator.writeEndArray()
    val dataType = metricSchema match {
      case _: CounterSchema => "counter"
      case _: GaugeSchema => "gauge"
      case _: HistogramSchema => "histogram"
    }
    jsonGenerator.writeStringField("kind", dataType)
    jsonGenerator.writeObjectFieldStart("source")
    jsonGenerator.writeStringField(
      "class",
      metricSchema.metricBuilder.sourceClass.getOrElse("Unspecified"))
    jsonGenerator.writeStringField("category", metricSchema.metricBuilder.role.toString)
    jsonGenerator.writeStringField(
      "process_path",
      metricSchema.metricBuilder.processPath.getOrElse("Unspecified"))
    jsonGenerator.writeEndObject()
    jsonGenerator.writeStringField("description", metricSchema.metricBuilder.description)
    jsonGenerator.writeStringField("unit", metricSchema.metricBuilder.units.toString)
    jsonGenerator.writeStringField("verbosity", metricSchema.metricBuilder.verbosity.toString)
    jsonGenerator.writeBooleanField("key_indicator", metricSchema.metricBuilder.keyIndicator)

    metricSchema match {
      case _: GaugeSchema =>
        if (metricSchema.metricBuilder.isCounterishGauge) {
          jsonGenerator.writeBooleanField("counterish_gauge", true)
        }
      case _: HistogramSchema =>
        jsonGenerator.writeObjectFieldStart("buckets")
        jsonGenerator.writeStringField("count", statsFormatter.histogramSeparator + "count")
        jsonGenerator.writeStringField("sum", statsFormatter.histogramSeparator + "sum")
        jsonGenerator.writeStringField(
          "average",
          statsFormatter.histogramSeparator + statsFormatter.labelAverage)
        jsonGenerator.writeStringField(
          "minimum",
          statsFormatter.histogramSeparator + statsFormatter.labelMin)
        jsonGenerator.writeStringField(
          "maximum",
          statsFormatter.histogramSeparator + statsFormatter.labelMax)
        metricSchema.metricBuilder.percentiles.foreach(bucket =>
          jsonGenerator.writeStringField(
            bucket.toString,
            statsFormatter.histogramSeparator + statsFormatter.labelPercentile(bucket)))

        jsonGenerator.writeEndObject()
      case _ => // nop
    }
    jsonGenerator.writeEndObject()
  }

  private def convertNullToString(segment: String): String = {
    if (segment == null) "null"
    else segment
  }
}
