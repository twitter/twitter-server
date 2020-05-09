import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{HttpMuxer, Request, Response, Status}
import com.twitter.finagle.Service
import com.twitter.logging.Formatter
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future, Time}
import java.net.InetSocketAddress

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
  //#fail_fast
  override def failfastOnFlagsNotParsed: Boolean = true
  //#fail_fast

  val service = new Service[Request, Response] {
    def apply(request: Request) = {
      //#log_usage
      debug("Received a request at " + Time.now)
      //#log_usage
      //#stats_usage
      counter.incr()
      //#stats_usage
      val response = Response(request.version, Status.Ok)
      response.contentString = what() + "\n"
      Future.value(response)
    }
  }

  def main(): Unit = {
    // We could create a new http server but in this case we use the
    // one already started for /admin/* endpoints.
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
