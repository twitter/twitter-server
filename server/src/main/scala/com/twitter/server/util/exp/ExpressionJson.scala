package com.twitter.server.util.exp

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.twitter.finagle.stats.SourceRole
import com.twitter.finagle.stats.exp._

/**
 * A set of serializers, deserializers used to serve admin/metrics/expressions endpoint
 */
object ExpressionJson {

  /**
   * Serialize the ExpressionSchema to JSON
   */
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

      // we need this to evolve the expressions format compatibly
      // in the future, we will only export labels that are set
      val labels = Map(
        ExpressionSchema.ProcessPath -> "Unspecified",
        ExpressionSchema.ServiceName -> "Unspecified",
        ExpressionSchema.Role -> SourceRole.NoRoleSpecified.toString
      ) ++ expressionSchema.labels
      for ((key, value) <- labels) {
        gen.writeStringField(key, value)
      }
      gen.writeEndObject()

      if (expressionSchema.namespace.nonEmpty) {
        gen.writeArrayFieldStart("namespaces")
        expressionSchema.namespace.foreach { p => gen.writeString(p) }
        gen.writeEndArray()
      }

      gen.writeStringField("expression", expressionSchema.exprQuery)

      provider.defaultSerializeField("bounds", expressionSchema.bounds, gen)

      gen.writeStringField("description", expressionSchema.description)
      gen.writeStringField("unit", expressionSchema.unit.toString)
      gen.writeEndObject()
    }
  }

  /**
   * Deserialize bounds/operator to the case object.
   * Serialization is handled by Json annotations.
   */
  object OperatorDeserializer extends StdDeserializer[Operator](classOf[Operator]) {
    override def deserialize(
      parser: JsonParser,
      ctx: DeserializationContext
    ): Operator = {
      parser.readValueAs(classOf[String]) match {
        case ">" => GreaterThan
        case "<" => LessThan
        case other =>
          throw ctx.instantiationException(classOf[Operator], s"Unknown operator: $other")
      }
    }
  }
}
