package com.twitter.server.opencensus

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.server.AdminHttpServer
import com.twitter.util.{Future, FuturePool}
import io.opencensus.contrib.zpages.{ZPageHandler, ZPageHandlers}
import java.io.ByteArrayOutputStream
import scala.collection.JavaConverters._

private object ZPagesAdminRoutes {
  private def zpageHandlerToService(
    handler: ZPageHandler,
    name: String
  ): Service[Request, Response] =
    new Service[Request, Response] {
      override def toString: String = s"ZPageHandlerService($name)"

      def apply(request: Request): Future[Response] = {
        val requestParams =
          request
            .getParamNames().asScala.map { name => name -> request.getParam(name) }.toMap.asJava

        // process in a FuturePool to handle the possibility
        // of zpages having blocking code.
        FuturePool.unboundedPool {
          val output = new ByteArrayOutputStream()
          handler.emitHtml(requestParams, output)
          Response(request)
            .status(Status.Ok)
            .content(Buf.ByteArray.Owned(output.toByteArray))
        }
      }
    }
}

/**
 * Mix into an [[AdminHttpServer]] to serve OpenCensus zPages on admin routes.
 *
 * The zPages will be available at:
 *  - /rpcz
 *  - /statz
 *  - /tracez
 *  - /traceconfigz
 *
 * For example:
 * {{{
 * import com.twitter.server.TwitterServer
 * import com.twitter.server.opencensus.ZPagesAdminRoutes
 *
 * object MyServer extends TwitterServer with ZPagesAdminRoutes {
 *   // ...
 * }
 * }}}
 *
 * @see [[https://opencensus.io/zpages/]]
 */
trait ZPagesAdminRoutes { self: AdminHttpServer =>

  addAdminRoutes {
    val handlers =
      (ZPageHandlers.getRpczZpageHandler, "RPCz") ::
        (ZPageHandlers.getStatszZPageHandler, "Statz") ::
        (ZPageHandlers.getTraceConfigzZPageHandler, "Trace Configz") ::
        (ZPageHandlers.getTracezZPageHandler, "Tracez") ::
        Nil

    handlers.map {
      case (handler, name) =>
        AdminHttpServer.mkRoute(
          path = handler.getUrlPath,
          handler = ZPagesAdminRoutes.zpageHandlerToService(handler, name),
          alias = name,
          group = Some("OpenCensus"),
          includeInIndex = true
        )
    }
  }
}
