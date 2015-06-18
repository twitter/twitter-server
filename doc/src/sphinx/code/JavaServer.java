//#imports
import com.twitter.server.AbstractTwitterServer
//#imports

//#server
public class JavaServer extends AbstractTwitterServer {

  //#main
  class Main {
    public static void main(String[] args) {
      new JavaServer().main(args);
    }
  }
  //#main

  //#oninit
  @Override
  public void onInit() {
    log().info("Java Server initialization...")
  }
  //#oninit
}
//#server