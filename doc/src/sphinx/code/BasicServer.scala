//#imports
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
//#imports

//#server
object BasicServer extends TwitterServer {
  val service = new Service[Request, Response] {
    def apply(request: Request) = {
      val response = Response(request.version, Status.Ok)
      response.contentString = "hello"
      Future.value(response)
    }
  }

  def main(): Unit = {
    val server = Http.serve(":8888", service)
    onExit {
      server.close()
    }
    Await.ready(server)
  }
}
//#server
