package com.twitter.server.util.exp

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.twitter.finagle.stats.exp.{
  ConstantExpression,
  Expression,
  ExpressionSchema,
  FunctionExpression,
  MetricExpression
}
import com.twitter.finagle.stats.metadataScopeSeparator

object ExpressionSchemaSerializer
    extends StdSerializer[ExpressionSchema](classOf[ExpressionSchema]) {

  def serialize(
    expressionSchema: ExpressionSchema,
    gen: JsonGenerator,
    provider: SerializerProvider
  ): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", expressionSchema.name)

    gen.writeObjectFieldStart("labels")
    gen.writeStringField(
      "process_path",
      expressionSchema.labels.processPath.getOrElse("Unspecified"))
    gen.writeStringField(
      "service_name",
      expressionSchema.labels.serviceName.getOrElse("Unspecified"))
    gen.writeStringField("role", expressionSchema.labels.role.toString)
    gen.writeEndObject()

    gen.writeObjectFieldStart("expression")
    writeExpression(expressionSchema.expr, gen)
    gen.writeEndObject()

    gen.writeStringField("bounds", expressionSchema.bounds.toString)
    gen.writeStringField("description", expressionSchema.description)
    gen.writeStringField("unit", expressionSchema.unit.toString)
    gen.writeEndObject()
  }

  def writeExpression(
    expr: Expression,
    gen: JsonGenerator,
    name: String = "metric"
  ): Unit = {
    expr match {
      case MetricExpression(schema) =>
        gen.writeStringField(name, schema.metricBuilder.name.mkString(metadataScopeSeparator()))
      case FunctionExpression(funcName, exprs) =>
        gen.writeStringField("operator", funcName)
        gen.writeObjectFieldStart("metrics")
        exprs.zipWithIndex.map {
          case (expr, index) => writeExpression(expr, gen, name + "-" + index.toString)
        }
        gen.writeEndObject()
      case ConstantExpression(repr, _) =>
        gen.writeStringField("constant", repr)
    }
  }
}
