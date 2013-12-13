import com.twitter.conversions.time._
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.{Http, Service}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Duration, Future, Time}
import java.net.InetSocketAddress
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.util.CharsetUtil.UTF_8

object AdvancedServer extends TwitterServer {

  //#flag
  val what = flag("what", "hello", "String to return")
  //#flag
  //#complex_flag
  val addr = flag("bind", new InetSocketAddress(0), "Bind address")
  val durations = flag("alarms", (1.second, 5.second), "2 alarm durations")
  //#complex_flag
  //#stats
  val counter = statsReceiver.counter("requests_counter")
  //#stats

  val service = new Service[HttpRequest, HttpResponse] {
    def apply(request: HttpRequest) = {
      //#log_usage
      log.debug("Received a request at " + Time.now)
      //#log_usage
      //#stats_usage
      counter.incr()
      //#stats_usage
      val response =
        new DefaultHttpResponse(request.getProtocolVersion, HttpResponseStatus.OK)
      val content = copiedBuffer(what() + "\n", UTF_8)
      response.setContent(content)
      Future.value(response)
    }
  }

  def main() {
    // We can create a new http server but in that case we profit from the
    // one already started for /admin/*
    // The `TwitterServer` trait exposes an `adminHttpServer` that serve all routes
    // registered in the HttpMuxer object, we just have to add our own.
    //#registering_http_service
    HttpMuxer.addHandler("/echo", service)
    HttpMuxer.addHandler("/echo/", service)
    //#registering_http_service
    // And wait on the server
    Await.ready(adminHttpServer)
  }
}
