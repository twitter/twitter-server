package com.twitter.server.util

import com.fasterxml.jackson.databind.module.SimpleModule
import com.twitter.finagle.stats.exp.Operator
import com.twitter.server.util.exp.ExpressionJson.{ExpressionSchemaSerializer, OperatorDeserializer}

object MetricSchemaJsonModule extends SimpleModule {
  addSerializer(SchemaSerializer)
  addSerializer(ExpressionSchemaSerializer)
  addDeserializer(classOf[Operator], OperatorDeserializer)
}
