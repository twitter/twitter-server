package com.twitter.server.handler

import com.twitter.finagle.toggle.{Toggle, ToggleMap}
import com.twitter.server.handler.ToggleHandler._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.immutable

@RunWith(classOf[JUnitRunner])
class ToggleHandlerTest extends FunSuite {

  test("renders empty registeredLibraries") {
    val handler = new ToggleHandler(() => Map.empty)
    assert(handler.jsonResponse ==
      """{
        |  "libraries" : [ ]
        |}""".stripMargin)
  }

  test("toLibraryToggles for empty ToggleMaps") {
    val mappings = Map(
      "com.twitter.Empty1" -> new ToggleMap.Immutable(immutable.Seq.empty),
      "com.twitter.Empty2" -> new ToggleMap.Immutable(immutable.Seq.empty)
    )
    val libs = new ToggleHandler(() => mappings).toLibraries

    // check we have all the library names
    assert(mappings.keySet == libs.libraries.map(_.libraryName).toSet)

    // check the empty ToggleMap
    assert(libs.libraries.forall { lib =>
      lib.libraryName.startsWith("com.twitter.Empty")
    })
    assert(libs.libraries.forall { lib =>
      lib.toggles.isEmpty
    })
  }

  test("toLibraryToggles") {
    val t0 = Toggle.Metadata(
      "com.twitter.server.handler.Id0", 0.0, None, "source=tm0")
    val t1 = Toggle.Metadata(
      "com.twitter.server.handler.Id1", 1.0, Some("t1 desc"), "source=tm0")
    val t2 = Toggle.Metadata(
      "com.twitter.server.handler.Id2", 1.0, Some("t2 desc"), "source=tm1")
    val t0b = t0.copy(description = Some("t0 desc"), source = "source=tm1")

    val tm0 = new ToggleMap.Immutable(immutable.Seq(t0, t1))
    val tm1 = new ToggleMap.Immutable(immutable.Seq(t0b, t2))
    val mappings = Map("com.twitter.With" -> tm0.orElse(tm1))
    val libs = new ToggleHandler(() => mappings).toLibraries

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
      Current(t0.id, t0.fraction, t0b.description),
      Seq(Component(t0.source, t0.fraction), Component(t0b.source, t0b.fraction)))
    assert(expected0 == libToggle(t0.id))

    val expected1 = LibraryToggle(
      Current(t1.id, t1.fraction, t1.description),
      Seq(Component(t1.source, t1.fraction)))
    assert(expected1 == libToggle(t1.id))

    val expected2 = LibraryToggle(
      Current(t2.id, t2.fraction, t2.description),
      Seq(Component(t2.source,t2.fraction)))
    assert(expected2 == libToggle(t2.id))
  }

}
