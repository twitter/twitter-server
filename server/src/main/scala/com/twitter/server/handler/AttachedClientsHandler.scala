package com.twitter.server.handler

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonFormat, JsonInclude}
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.server.ServerRegistry
import com.twitter.server.util.AdminJsonConverter
import com.twitter.util.Future
import java.net.SocketAddress
import java.security.cert.X509Certificate
import java.util.Date

private[handler] object AttachedClientsHandler {

  case class ClientConnectionEntry(address: SocketAddress, ssl: Option[ClientSslInfo])

  @JsonNaming(classOf[PropertyNamingStrategy.SnakeCaseStrategy])
  case class PeerCertInfo(
    commonName: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC") expiry: Date)

  @JsonNaming(classOf[PropertyNamingStrategy.SnakeCaseStrategy])
  @JsonInclude(value = Include.NON_ABSENT, content = Include.NON_ABSENT)
  case class ClientSslInfo(
    sessionId: String,
    cipherSuite: String,
    peerCertificate: Option[PeerCertInfo])

  case class ServerConnectionInfo(address: SocketAddress, clients: Seq[ClientConnectionEntry])

  case class AttachedClientsConnectionInfo(servers: Seq[ServerConnectionInfo])

  private def certToPeerCertInfo(certificate: X509Certificate): PeerCertInfo =
    PeerCertInfo(
      certificate.getSubjectDN.getName,
      certificate.getNotAfter
    )

  private def render(serverRegistry: ServerRegistry): AttachedClientsConnectionInfo = {
    AttachedClientsConnectionInfo(serverRegistry.serverAddresses.flatMap { serverAddress =>
      val connectionRegistry = serverRegistry.connectionRegistry(serverAddress)
      Some(
        ServerConnectionInfo(
          address = serverAddress,
          clients = connectionRegistry.iterator.map {
            clientConnection =>
              ClientConnectionEntry(
                address = clientConnection.remoteAddress,
                ssl = if (!clientConnection.sslSessionInfo.usingSsl) {
                  None
                } else {
                  Some(ClientSslInfo(
                    sessionId = clientConnection.sslSessionInfo.sessionId,
                    cipherSuite = clientConnection.sslSessionInfo.cipherSuite,
                    peerCertificate = clientConnection.sslSessionInfo.peerCertificates.headOption
                      .map(certToPeerCertInfo)
                  ))
                }
              )
          }.toList
        ))
    })
  }
}

/**
 * A handler that reports information about connected clients, by server. For example:
 *
 * {{{
 * {
 *   "servers": [
 *     {
 *       "address": "127.0.0.1:8080",
 *       "clients": [
 *         {
 *           "address": "127.0.0.1:9090",
 *           "ssl": {
 *             "session_id": "sessionid",
 *             "cipher_suite": "cipher?sweeeeet!",
 *             "peer_certificate": {
 *               "common_name": "remoteprincipal"
 *             }
 *           }
 *         }
 *       ]
 *     }
 *   ]
 * }
 * }}}
 */
class AttachedClientsHandler(serverRegistry: ServerRegistry = ServerRegistry)
    extends Service[Request, Response] {
  def apply(request: Request): Future[Response] = {
    val doc = AttachedClientsHandler.render(serverRegistry)
    Future.value(AdminJsonConverter(doc))
  }
}
