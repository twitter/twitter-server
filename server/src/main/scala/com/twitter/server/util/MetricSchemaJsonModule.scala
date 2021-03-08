package com.twitter.server.util

import com.fasterxml.jackson.databind.module.SimpleModule
import com.twitter.server.util.exp.ExpressionSchemaSerializer

object MetricSchemaJsonModule extends SimpleModule {
  addSerializer(SchemaSerializer)
  addSerializer(ExpressionSchemaSerializer)
}
