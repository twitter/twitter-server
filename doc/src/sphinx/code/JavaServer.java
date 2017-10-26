//#imports
import com.twitter.server.AbstractTwitterServer;
import com.twitter.util.logging.Logger;
//#imports

//#server
public class JavaServer extends AbstractTwitterServer {

  private static final Logger LOG = Logger.apply("JavaServer");

  //#main
  public static class Main {
    public static void main(String[] args) {
      new JavaServer().main(args);
    }
  }
  //#main

  //#oninit
  @Override
  public void onInit() {
    LOG.info("Java Server initialization...");
  }
  //#oninit
}
//#server
