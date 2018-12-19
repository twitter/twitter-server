import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.Service
import com.twitter.server.TwitterServer
import com.twitter.server.logging.{Logging => JDK14Logging}
import com.twitter.util.{Await, Future, Time}
import java.net.InetSocketAddress

//#server_obj
object BackwardsCompatServer extends TwitterServer with JDK14Logging {
//#server_obj

  //#formatter
  override def defaultFormatter = new Formatter(
    timezone = Some("UTC"),
    prefix = "<yyyy-MM-dd HH:mm:ss.SSS> [%.3s] %s: "
  )
  //#formatter

  val service = new Service[Request, Response] {
    def apply(request: Request) = {
      //#log_usage
      log.debug("Received a request at " + Time.now)
      //#log_usage
      val response = Response(request.version, Status.Ok)
      response.contentString = what() + "\n"
      Future.value(response)
    }
  }

  def main() {
    ...
  }
}
