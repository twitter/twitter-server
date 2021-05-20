package com.twitter.server.handler.exp

import com.twitter.finagle.stats.exp.{
  AcceptableRange,
  Bounds,
  GreaterThan,
  LessThan,
  MonotoneThresholds,
  Unbounded
}
import com.twitter.server.util.{JsonUtils, AdminJsonConverter}
import org.scalatest.funsuite.AnyFunSuite

class ExpressionSerdeTest extends AnyFunSuite {
  trait BoundsCtx {
    val unbounded = Unbounded.get
    val monotoneWithUnset = MonotoneThresholds(GreaterThan, 10, 20)
    val monotone = MonotoneThresholds(LessThan, 10, 20, Some(0), Some(100))
    val range = AcceptableRange(0, 100)

    val unboundedJson =
      """
        |{
        |  "kind" : "unbounded"
        |}""".stripMargin

    val monotoneWithUnsetJson =
      """
        |{
        |  "kind" : "monotone",
        |  "operator" : ">",
        |  "bad_threshold" : 10.0,
        |  "good_threshold" : 20.0,
        |  "lower_bound_inclusive" : null,
        |  "upper_bound_exclusive" : null
        |}""".stripMargin

    val monotoneJson =
      """
        |{
        |  "kind" : "monotone",
        |  "operator" : "<",
        |  "bad_threshold" : 10.0,
        |  "good_threshold" : 20.0,
        |  "lower_bound_inclusive" : 0.0,
        |  "upper_bound_exclusive" : 100.0
        |}""".stripMargin

    val rangeJson =
      """
        |{
        |  "kind" : "range",
        |  "lower_bound_inclusive" : 0.0,
        |  "upper_bound_exclusive" : 100.0
        |}""".stripMargin
  }

  test("serialize Bounds to Json") {
    new BoundsCtx {
      JsonUtils.assertJsonResponse(AdminJsonConverter.writeToString(unbounded), unboundedJson)
      JsonUtils.assertJsonResponse(
        AdminJsonConverter.writeToString(monotoneWithUnset),
        monotoneWithUnsetJson)
      JsonUtils.assertJsonResponse(AdminJsonConverter.writeToString(monotone), monotoneJson)
      JsonUtils.assertJsonResponse(AdminJsonConverter.writeToString(range), rangeJson)
    }
  }

  test("deserialize Bounds to object") {
    new BoundsCtx {
      val unboundedDeserialized = AdminJsonConverter.parse[Bounds](unboundedJson)
      val monotoneWithUnsetDeserialized =
        AdminJsonConverter.parse[Bounds](monotoneWithUnsetJson)
      val monotoneDeserialized = AdminJsonConverter.parse[Bounds](monotoneJson)
      val rangeDeserialized = AdminJsonConverter.parse[Bounds](rangeJson)

      assert(unboundedDeserialized == unbounded)
      assert(monotoneWithUnsetDeserialized == monotoneWithUnset)
      assert(monotoneDeserialized == monotone)
      assert(rangeDeserialized == range)
    }
  }
}
