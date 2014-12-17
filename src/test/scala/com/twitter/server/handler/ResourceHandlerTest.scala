package com.twitter.server.handler

import com.twitter.finagle.http
import com.twitter.io.Charsets
import com.twitter.util.Await
import java.io.{ByteArrayInputStream, File, FileWriter, InputStream}
import java.nio.file.Files
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class ResourceHandlerTest extends FunSuite {
  private def staticResourceResolver(content: String): PartialFunction[String, InputStream] =
    PartialFunction(_ => new ByteArrayInputStream(content.getBytes("UTF8")))

  test("404") {
    val handler = new ResourceHandler("/", PartialFunction.empty)
    val res = Await.result(handler(http.Request("nonexistent.filetype")))
    assert(res.getStatus === http.Status.NotFound)
  }

  test("400") {
    val handler = new ResourceHandler("/", PartialFunction.empty)
    val res = Await.result(handler(http.Request("../../illegal")))
    assert(res.getStatus === http.Status.BadRequest)
  }

  test("load js") {
    val content = "var foo = function() { }"
    val handler = new ResourceHandler("/", staticResourceResolver(content))
    val res = Await.result(handler(http.Request("test.js")))
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "application/javascript;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === content)
  }

  test("load css") {
    val content = "#foo { color: blue; }"
    val handler = new ResourceHandler("/", staticResourceResolver(content))
    val res = Await.result(handler(http.Request("test.css")))
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "text/css;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === content)
  }

  test("load bytes") {
    val content = "jileuhto8q34ty3fni34oqbo87ybq"
    val handler = new ResourceHandler("/", staticResourceResolver(content))
    val res = Await.result(handler(http.Request("test.raw")))
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "application/octet-stream")
    assert(res.getContent.toString(Charsets.Iso8859_1) === content)
  }

  private def createTempFile(filename: String, content: String): File = {
    val dir = Files.createTempDirectory("ResourceHandlerTest")
    val tempFile = dir.resolve(filename).toFile
    val writer = new FileWriter(tempFile)
    writer.write(content)
    writer.close()
    tempFile.deleteOnExit()
    dir.toFile.deleteOnExit()
    tempFile
  }

  private def slurpStream(inputStream: InputStream): String = {
    val source = Source.fromInputStream(inputStream).withClose(() => inputStream.close())
    val result = source.getLines().mkString
    source.close()
    result
  }

  private val jarPath = "www"
  private val resourceName = "test.js"
  private val resourceContent = "var foo = function() { }"
  private val fileName = "only_on_disk.js"
  private val fileContent = "var foo = function() { return 'disk'; }"

  test("fromJar - resource exists") {
    val observed = slurpStream(ResourceHandler.jarResolver(jarPath)(resourceName))
    assert(observed === resourceContent)
  }

  test("fromJar - resource doesn't exist") {
    assert(!ResourceHandler.jarResolver(jarPath).isDefinedAt("nonexistent_file.js"))
  }

  test("fromDirectory - resource exists") {
    val file = createTempFile(fileName, fileContent)
    val observed = slurpStream(ResourceHandler.directoryResolver(file.getParent)(file.getName))
    assert(observed === fileContent)
  }

  test("fromDirectory - resource doesn't exist") {
    assert(!ResourceHandler.directoryResolver(".").isDefinedAt("nonexistent_file.js"))
  }

  test("fromDirectoryOrJar - resource exists in both") {
    val file = createTempFile(resourceName, fileContent)
    val stream = ResourceHandler.directoryOrJarResolver(jarPath, file.getParent)(resourceName)
    val observed = slurpStream(stream)
    assert(observed === fileContent)
  }

  test("fromDirectoryOrJar - resource exists only on disk") {
    val file = createTempFile(fileName, fileContent)
    val stream = ResourceHandler.directoryOrJarResolver(jarPath, file.getParent)(fileName)
    val observed = slurpStream(stream)
    assert(observed === fileContent)
  }

  test("fromDirectoryOrJar - resource exists only in jar") {
    val stream = ResourceHandler.directoryOrJarResolver(jarPath, "nonexistent_dir")(resourceName)
    val observed = slurpStream(stream)
    assert(observed === resourceContent)
  }

  test("fromDirectoryOrJar - resource doesn't exist") {
    assert(
      !ResourceHandler.directoryOrJarResolver(jarPath, "nonexistent_dir")
        .isDefinedAt("nonexistent_file.js")
    )
  }
}