package com.twitter.server.handler

import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.toggle.{Toggle, ToggleMap}
import com.twitter.server.handler.ToggleHandler._
import scala.collection.immutable
import scala.collection.mutable.ArrayBuffer
import org.scalatest.funsuite.AnyFunSuite

class ToggleHandlerTest extends AnyFunSuite {

  test("renders empty registeredLibraries") {
    val handler = new ToggleHandler(() => Map.empty)
    assert(
      handler.getResponse(ParsedPath(None, None)) ==
        """{
        |  "libraries" : [ ]
        |}""".stripMargin
    )
  }

  test("toLibraryToggles for empty ToggleMaps") {
    val mappings = Map(
      "com.twitter.Empty1" -> ToggleMap.newMutable(),
      "com.twitter.Empty2" -> ToggleMap.newMutable()
    )
    val libs = new ToggleHandler(() => mappings)
      .toLibraries(ParsedPath(None, None))

    // check we have all the library names
    assert(mappings.keySet == libs.libraries.map(_.libraryName).toSet)

    // check the empty ToggleMap
    assert(libs.libraries.forall { lib => lib.libraryName.startsWith("com.twitter.Empty") })
    assert(libs.libraries.forall { lib => lib.toggles.isEmpty })
  }

  test("toLibraryToggles") {
    val t0 = Toggle.Metadata("com.twitter.server.handler.Id0", 0.0, None, "source=tm0")
    val t1 = Toggle.Metadata("com.twitter.server.handler.Id1", 1.0, Some("t1 desc"), "source=tm0")
    val t2 = Toggle.Metadata("com.twitter.server.handler.Id2", 1.0, Some("t2 desc"), "source=tm1")
    val t0b = t0.copy(description = Some("t0 desc"), source = "source=tm1")

    val tm0 = new ToggleMap.Immutable(immutable.Seq(t0, t1))
    val tm1 = new ToggleMap.Immutable(immutable.Seq(t0b, t2))
    val immutableMap = tm0.orElse(tm1)
    val mappings = Map("com.twitter.With" -> new ToggleMap.Mutable with ToggleMap.Proxy {
      def underlying: ToggleMap = immutableMap
      def put(id: String, fraction: Double): Unit = ???
      def remove(id: String): Unit = ???
    })
    val libs = new ToggleHandler(() => mappings)
      .toLibraries(ParsedPath(None, None))

    // check the populated ToggleMap
    val withLib = libs.libraries.find(_.libraryName == "com.twitter.With").get

    // basic sanity check that we have all toggles, before doing
    // a deep equality comparison
    assert(Set(t0, t1, t2).map(_.id) == withLib.toggles.map(_.current.id).toSet)

    // note, toLibraryToggles does not impose an ordering, so we cannot do a
    // simple equality comparison.
    assert("com.twitter.With" == withLib.libraryName)

    def libToggle(id: String): LibraryToggle = {
      withLib.toggles.find(_.current.id == id).get
    }
    val expected0 = LibraryToggle(
      Current(t0.id, t0.fraction, None, t0b.description),
      Seq(Component(t0.source, t0.fraction), Component(t0b.source, t0b.fraction))
    )
    assert(expected0 == libToggle(t0.id))

    val expected1 = LibraryToggle(
      Current(t1.id, t1.fraction, None, t1.description),
      Seq(Component(t1.source, t1.fraction))
    )
    assert(expected1 == libToggle(t1.id))

    val expected2 = LibraryToggle(
      Current(t2.id, t2.fraction, None, t2.description),
      Seq(Component(t2.source, t2.fraction))
    )
    assert(expected2 == libToggle(t2.id))
  }

  test("toLibraries with filter on libraryName") {
    val mut0 = ToggleMap.newMutable()
    mut0.put("com.twitter.map0toggle0", 1.0)
    val mut1 = ToggleMap.newMutable()
    mut1.put("com.twitter.map1toggle0", 1.0)

    val mappings = Map("com.twitter.map0" -> mut0, "com.twitter.map1" -> mut1)
    val handler = new ToggleHandler(() => mappings)

    val libs = handler.toLibraries(ParsedPath(Some("com.twitter.map1"), None))
    assert(1 == libs.libraries.size)
    val lib = libs.libraries.head
    assert("com.twitter.map1" == lib.libraryName)
  }

  test("toLibraries with filter on libraryName and id") {
    val mut0 = ToggleMap.newMutable()
    mut0.put("com.twitter.map0toggle0", 1.0)
    mut0.put("com.twitter.map0toggle1", 1.0)
    val mut1 = ToggleMap.newMutable()
    mut1.put("com.twitter.map1toggle0", 1.0)

    val mappings = Map("com.twitter.map0" -> mut0, "com.twitter.map1" -> mut1)
    val handler = new ToggleHandler(() => mappings)

    val libs =
      handler.toLibraries(ParsedPath(Some("com.twitter.map0"), Some("com.twitter.map0toggle1")))
    assert(1 == libs.libraries.size)
    val lib = libs.libraries.head
    assert("com.twitter.map0" == lib.libraryName)
    assert(1 == lib.toggles.size)
    val libToggle = lib.toggles.head
    assert("com.twitter.map0toggle1" == libToggle.current.id)
  }

  test("toLibraries includes last values") {
    val mut = ToggleMap.newMutable()
    mut.put("com.twitter.map0toggle0", 0.0)
    mut.put("com.twitter.map0toggle1", 1.0)
    mut.put("com.twitter.map0toggleNoneThen1", 1.0)
    val tm = ToggleMap.observed(mut, NullStatsReceiver)

    // use 2 of the toggles, causing the values to be captured
    tm("com.twitter.map0toggle0")(5)
    tm("com.twitter.map0toggle1")(5)

    val mappings = Map("com.twitter.map0" -> new ToggleMap.Mutable with ToggleMap.Proxy {
      def underlying: ToggleMap = tm
      def put(id: String, fraction: Double): Unit = ???
      def remove(id: String): Unit = ???
    })
    val handler = new ToggleHandler(() => mappings)

    def currentForId(id: String): ToggleHandler.Current = {
      val libs = handler.toLibraries(ParsedPath(Some("com.twitter.map0"), Some(id)))
      libs.libraries.head.toggles.head.current
    }

    assert(currentForId("com.twitter.map0toggle0").lastValue.contains(false))
    assert(currentForId("com.twitter.map0toggle1").lastValue.contains(true))
    assert(currentForId("com.twitter.map0toggleNoneThen1").lastValue.isEmpty)

    // update the value and validate we see the change
    tm("com.twitter.map0toggleNoneThen1")(5)
    assert(currentForId("com.twitter.map0toggleNoneThen1").lastValue.contains(true))
  }

  test("setToggle") {
    // initialize some state
    val libName = "com.handler.Mutable"
    val id = "com.handler.T0"
    val mut = ToggleMap.newMutable()
    val tog = mut(id)
    val handler = new ToggleHandler(() => Map(libName -> mut))
    assert(tog.isUndefined)

    // mutate it
    val errors = handler.setToggle(libName, id, Some("1.0"))
    assert(errors.isEmpty, errors.mkString(", "))
    assert(tog.isDefined)
    assert(tog(30))

    // mutate it again.
    val errors2 = handler.setToggle(libName, id, Some("0.0"))
    assert(errors2.isEmpty, errors.mkString(", "))
    assert(tog.isDefined)
    assert(!tog(30))
  }

  test("setToggle missing fraction") {
    val libName = "com.handler.Mutable"
    val handler = new ToggleHandler(() => Map(libName -> ToggleMap.newMutable()))

    val errors = handler.setToggle(libName, "com.handler.T0", None)
    assert(errors.nonEmpty)
    assert(errors.contains("Missing query parameter: 'fraction'"))
  }

  test("setToggle invalid fraction that is not a Double") {
    val libName = "com.handler.Mutable"
    val handler = new ToggleHandler(() => Map(libName -> ToggleMap.newMutable()))

    val errors = handler.setToggle(libName, "com.handler.T0", Some("derp"))
    assert(errors.nonEmpty)
    assert(errors.exists(_.contains("Fraction must be [0.0-1.0]")))
  }

  test("setToggle invalid fraction that is out of range") {
    val libName = "com.handler.Mutable"
    val handler = new ToggleHandler(() => Map(libName -> ToggleMap.newMutable()))

    val errors = handler.setToggle(libName, "com.handler.T0", Some("10.5"))
    assert(errors.nonEmpty)
    assert(errors.exists(_.contains("Fraction must be [0.0-1.0]")))
  }

  test("deleteToggle") {
    // initialize some state
    val libName = "com.handler.Mutable"
    val id = "com.handler.T0"
    val mut = ToggleMap.newMutable()
    mut.put(id, 1.0)
    val tog = mut(id)
    val handler = new ToggleHandler(() => Map(libName -> mut))
    assert(tog.isDefined)
    assert(tog(30))

    // delete it
    val errors = handler.deleteToggle(libName, id)
    assert(errors.isEmpty, errors.mkString(", "))
    assert(tog.isUndefined)
  }

  test("parsePath") {
    def assertParsePathOk(
      path: String,
      expectedLibraryName: Option[String],
      expectedId: Option[String]
    ): Unit = {
      val handler = new ToggleHandler(() => Map("com.twitter.lib" -> ToggleMap.newMutable()))
      val errors = new ArrayBuffer[String]()
      val parsed = handler.parsePath(path, errors)
      assert(errors.isEmpty, errors.mkString(", "))
      assert(expectedLibraryName == parsed.libraryName)
      assert(expectedId == parsed.id)
    }

    assertParsePathOk("/admin/toggles", None, None)
    assertParsePathOk("/admin/toggles/", None, None)
    assertParsePathOk("/admin/toggles/com.twitter.lib", Some("com.twitter.lib"), None)
    assertParsePathOk(
      "/admin/toggles/com.twitter.lib/com.twitter.lib.Toggle",
      Some("com.twitter.lib"),
      Some("com.twitter.lib.Toggle")
    )
  }

  test("parsePath invalid path format") {
    val handler = new ToggleHandler(() => Map("com.twitter.lib" -> ToggleMap.newMutable()))
    def assertPathFormat(path: String): Unit = {
      val errors = new ArrayBuffer[String]()
      handler.parsePath(path, errors)
      assert(errors.exists(_.startsWith("Path must be of the form /admin/toggles")))
    }

    assertPathFormat("")
    assertPathFormat("/admin/toggle")
    assertPathFormat("admin/toggles")
  }

  test("parsePath unknown libraryName") {
    val handler = new ToggleHandler(() => Map("com.twitter.lib" -> ToggleMap.newMutable()))
    val errors = new ArrayBuffer[String]()
    handler.parsePath("/admin/toggles/com.twitter.erm", errors)
    assert(errors.contains("Unknown library name: 'com.twitter.erm'"))
  }

  test("parsePath invalid id") {
    val handler = new ToggleHandler(() => Map("com.twitter.lib" -> ToggleMap.newMutable()))
    val errors = new ArrayBuffer[String]()
    handler.parsePath("/admin/toggles/com.twitter.lib/FLEEK", errors)
    assert(errors.contains("Invalid id: 'FLEEK'"))
  }

}
