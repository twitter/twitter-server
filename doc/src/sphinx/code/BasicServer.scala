//#imports
import com.twitter.finagle.{Httpx, Service}
import com.twitter.io.Charsets
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
//#imports

//#server
object BasicServer extends TwitterServer {
  val service = new Service[Request, Response] {
    def apply(request: Request) = {
      val response = new Response(request.version, Status.Ok)
      response.contentString = "hello"
      Future.value(response)
    }
  }

  def main() {
    val server = Httpx.serve(":8888", service)
    onExit {
      server.close()
    }
    Await.ready(server)
  }
}
//#server
