package com.twitter.server.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.core.util.{DefaultIndenter, DefaultPrettyPrinter}
import com.fasterxml.jackson.databind.{
  MappingJsonFactory,
  ObjectMapper,
  ObjectWriter,
  PropertyNamingStrategy
}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.Buf
import com.twitter.util.jackson.ScalaObjectMapper
import java.lang.reflect.{ParameterizedType, Type}

sealed trait JsonConverterBase {
  protected[this] def mapper: ObjectMapper

  protected final val factory = {
    val factory = new MappingJsonFactory()
    factory.disable(JsonFactory.Feature.USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING)
  }

  private final lazy val writer: ObjectWriter = {
    val printer = new DefaultPrettyPrinter
    printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)

    mapper.writer(printer)
  }

  final def apply(obj: Any): Response = {
    val msg = writer.writeValueAsString(obj)
    val response = Response(Version.Http11, Status.Ok)
    response.content = Buf.Utf8(msg)
    response
  }

  final def writeToString(obj: Any): String = writer.writeValueAsString(obj)

  final def parse[T: Manifest](json: String): T =
    mapper.readValue(json, typeReference(manifest[T]))

  private def typeReference[T: Manifest] = new TypeReference[T] {
    override def getType = typeFromManifest(manifest[T])
  }

  private def typeFromManifest(m: Manifest[_]): Type =
    if (m.typeArguments.isEmpty) m.runtimeClass
    else
      new ParameterizedType {
        def getRawType = m.runtimeClass
        def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray
        def getOwnerType = null
      }
}

/**
 * default Java property naming strategy is used, which leaves field names as is,
 * and removes set/get/is prefix from methods (as well as lower-cases initial
 * sequence of capitalized characters).
 *
 * @note Use [[AdminJsonConverter]] for twitter-server endpoints
 */
object JsonConverter extends JsonConverterBase {
  protected[this] val mapper: ObjectMapper =
    new ObjectMapper(factory)
      .registerModule(DefaultScalaModule)
}

/**
 * Twitter Server endpoints should use this converter for the customized ser/deserializers and
 * consistent naming strategy.
 */
object AdminJsonConverter extends JsonConverterBase {
  // We define this writer alongside `mapper` until we migrate
  // all of the admin endpoints
  private[server] val prettyObjectMapper: ObjectWriter =
    ScalaObjectMapper.builder
      .withAdditionalJacksonModules(Seq(MetricSchemaJsonModule))
      .objectMapper(factory)
      .prettyObjectMapper

  protected[this] val mapper: ObjectMapper =
    new ObjectMapper(factory)
      .registerModule(DefaultScalaModule)
      .registerModule(MetricSchemaJsonModule)
      .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
}
