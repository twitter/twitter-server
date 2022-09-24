package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Status
import com.twitter.finagle.server.ServerRegistry
import com.twitter.finagle.service.StatsFilter
import com.twitter.finagle.ssl.session.SslSessionInfo
import com.twitter.finagle.ssl.session.ServiceIdentity
import com.twitter.finagle.ClientConnection
import com.twitter.finagle.Service
import com.twitter.finagle.ServiceFactory
import com.twitter.finagle.Stack
import com.twitter.finagle.param
import com.twitter.io.Buf
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Time
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.security.Principal
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLSession
import org.mockito.Mockito
import org.scalatest.funsuite.AnyFunSuite

object AttachedClientsHandlerTest {
  def await[A](f: Future[A]): A = Await.result(f, 2.seconds)

  val remoteSocketAddress = InetSocketAddress.createUnresolved("/127.0.0.1", 9090)

  def registerServer(registry: ServerRegistry, addr: SocketAddress, name: String): Unit = {
    val ok = ServiceFactory.const(Service.const(Future.value("ok")))
    val leaf = StatsFilter.module.toStack(Stack.leaf(Stack.Role("ok"), ok))

    registry.register(addr.toString, leaf, Stack.Params.empty + param.Label(name))

    val peerCertificate = Mockito.mock(classOf[X509Certificate])
    val remotePrincipal = Mockito.mock(classOf[Principal])
    // We purposely use a date in the past here to validate the formatting
    // of the json returned by this endpoint. Because it has already happened,
    // it is not subject to any shenanigans regarding changes in leap seconds,
    // leap years, or any other changes that might affect time values in the future.
    // When running in production though, this endpoint should (almost) never return
    // a timestamp from the past. Doing so would mean that a connection was established
    // and then some time after, the certificate expired.
    val remoteDate = new Date(1512295640000L) // Sun Dec 03 02:07:20 PST 2017
    Mockito.when(peerCertificate.getSubjectDN).thenReturn(remotePrincipal)
    Mockito.when(peerCertificate.getNotAfter).thenReturn(remoteDate)
    Mockito.when(remotePrincipal.getName).thenReturn("remoteprincipal")

    registry
      .connectionRegistry(addr).register(new ClientConnection {
        override def remoteAddress: SocketAddress = remoteSocketAddress
        override def localAddress: SocketAddress = addr
        override def onClose: Future[Unit] = ???
        override def close(deadline: Time): Future[Unit] = ???
        override def sslSessionInfo: SslSessionInfo = new SslSessionInfo {
          override def usingSsl: Boolean = true
          override def session: SSLSession = ???
          override def sessionId: String = "sessionid"
          override def cipherSuite: String = "cipher?sweeeeet!"
          override def localCertificates: Seq[X509Certificate] = ???
          override def peerCertificates: Seq[X509Certificate] = Seq(peerCertificate)
          override def getLocalIdentity: Option[ServiceIdentity] = None
          override def getPeerIdentity: Option[ServiceIdentity] = None
        }
      })
  }
}

class AttachedClientsHandlerTest extends AnyFunSuite {
  import AttachedClientsHandlerTest._

  private[this] def assertJsonResponse(actualResponse: String, expectedResponse: String) = {
    val actual = stripWhitespace(actualResponse)
    val expected = stripWhitespace(expectedResponse)
    assert(actual == expected)
  }

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

  test("initial state") {
    val registry = new ServerRegistry()
    val handler = new AttachedClientsHandler(registry)
    val response = await(handler(Request("/connections/")))
    val Buf.Utf8(content) = response.content
    assertJsonResponse(content, """{"servers":[]}""")
  }

  test("add a client connection") {

    val registry = new ServerRegistry()
    registerServer(registry, InetSocketAddress.createUnresolved("/127.0.0.1", 8080), "server0")

    val handler = new AttachedClientsHandler(registry)
    val response = await(handler(Request("/connections/")))

    assert(response.status == Status.Ok)
    assertJsonResponse(
      response.contentString,
      """
        |{
        |  "servers": [
        |    {
        |      "address": "127.0.0.1:8080",
        |      "clients": [
        |        {
        |          "address": "127.0.0.1:9090",
        |          "ssl": {
        |            "session_id": "sessionid",
        |            "cipher_suite": "cipher?sweeeeet!",
        |            "peer_certificate": {
        |              "common_name": "remoteprincipal",
        |              "expiry": "2017-12-03T10:07:20.000+0000"
        |            }
        |          }
        |        }
        |      ]
        |    }
        |  ]
        |}
      """.stripMargin
    )
  }
}
