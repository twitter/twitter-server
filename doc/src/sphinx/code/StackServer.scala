//#imports
import com.twitter.server.AbstractTwitterServer
import com.twitter.server.{Lifecycle, Deciderable}
//#imports

//#server
abstract class StackServer extends AbstractTwitterServer with Lifecycle.Warmup with Deciderable
//#server
