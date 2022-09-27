package com.twitter.server.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.MetricBuilder
import com.twitter.finagle.stats.MetricBuilder.IdentityType
import com.twitter.finagle.stats.StatsFormatter
import com.twitter.finagle.stats.metadataScopeSeparator

object SchemaSerializer extends StdSerializer[MetricBuilder](classOf[MetricBuilder]) {

  private[this] val statsFormatter = StatsFormatter.default

  private[this] def writeArray(
    jsonGenerator: JsonGenerator,
    name: String,
    strings: Iterator[String]
  ): Unit = {
    jsonGenerator.writeArrayFieldStart(name)
    strings.foreach(s => jsonGenerator.writeString(convertNullToString(s)))
    jsonGenerator.writeEndArray()
  }

  private[this] def writeDictionary(
    jsonGenerator: JsonGenerator,
    name: String,
    entries: Iterable[(String, String)]
  ): Unit = {
    jsonGenerator.writeObjectFieldStart(name)
    entries.foreach {
      case (k, v) =>
        jsonGenerator.writeStringField(k, v)
    }
    jsonGenerator.writeEndObject()
  }

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
    metricBuilder: MetricBuilder,
    jsonGenerator: JsonGenerator,
    serializerProvider: SerializerProvider
  ): Unit = {
    val formattedName = metricBuilder.name.mkString(metadataScopeSeparator())
    jsonGenerator.writeStartObject()
    jsonGenerator.writeStringField("name", formattedName)

    jsonGenerator.writeStringField(
      "dimensional_name",
      metricBuilder.identity.dimensionalName.mkString(MetricBuilder.DimensionalNameScopeSeparator))

    writeArray(
      jsonGenerator,
      "relative_name",
      (if (metricBuilder.relativeName.nonEmpty) metricBuilder.relativeName
       else metricBuilder.name).iterator)

    writeDictionary(jsonGenerator, "labels", metricBuilder.identity.labels)

    jsonGenerator.writeBooleanField(
      "dimensional_support",
      dimensionalSupport(metricBuilder.identity.identityType))

    val dataType = metricBuilder.metricType.toJsonString
    jsonGenerator.writeStringField("kind", dataType)
    jsonGenerator.writeObjectFieldStart("source")
    jsonGenerator.writeStringField("class", metricBuilder.sourceClass.getOrElse("Unspecified"))
    jsonGenerator.writeStringField("category", metricBuilder.role.toString)
    jsonGenerator.writeStringField(
      "process_path",
      metricBuilder.processPath.getOrElse("Unspecified"))
    jsonGenerator.writeEndObject()
    jsonGenerator.writeStringField("description", metricBuilder.description)
    jsonGenerator.writeStringField("unit", metricBuilder.units.toString)
    jsonGenerator.writeStringField("verbosity", metricBuilder.verbosity.toString)
    jsonGenerator.writeBooleanField("key_indicator", metricBuilder.keyIndicator)

    metricBuilder.metricType match {
      case HistogramType =>
        jsonGenerator.writeStringField("histogram_format", metricBuilder.histogramFormat.toString)
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
        metricBuilder.percentiles.foreach(bucket =>
          jsonGenerator.writeStringField(
            bucket.toString,
            statsFormatter.histogramSeparator + statsFormatter.labelPercentile(bucket)))

        jsonGenerator.writeEndObject()
      case _ => // nop
    }
    jsonGenerator.writeEndObject()
  }

  private[this] def dimensionalSupport(identityType: IdentityType): Boolean =
    IdentityType.toResolvedIdentityType(identityType) == IdentityType.Full

  private def convertNullToString(segment: String): String = {
    if (segment == null) "null"
    else segment
  }
}
