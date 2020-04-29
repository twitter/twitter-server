package com.twitter.server.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.ScalaObjectMapper

object JsonHelper {

  /**
   * Relying on the ordering of HashMaps is a bad idea and is different between 2.13 and earlier versions.
   *
   * This helper will try to deserialize both strings to the given type `T` before comparison, which
   * avoids the ordering issue.
   */
  def assertJsonResponseFor[T: Manifest](
    mapper: ObjectMapper with ScalaObjectMapper,
    actual: String,
    expected: String
  ): Unit = {
    val expectedObj = mapper.readValue[T](expected)
    val actualObj = mapper.readValue[T](actual)

    assert(actualObj == expectedObj)
  }
}
