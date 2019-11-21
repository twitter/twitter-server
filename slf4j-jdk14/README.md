TwitterServer `slf4j-jdk14`
===========================

This library provides the [`slf4j-jdk14`](https://www.slf4j.org/manual.html#swapping) logging 
implementation for use in `TwitterServer` along with the appropriate [SLF4J bridges](https://www.slf4j.org/legacy.html)
for other logging implementations.

Backwards Compatibility
-----------------------

`TwitterServer` previously used [`util-logging`](https://github.com/twitter/util/tree/develop/util-logging) 
directly for framework logging and provided access to a configured `c.t.logging.Logger`.

[`util-logging`](https://github.com/twitter/util/tree/develop/util-logging) is a thin Scala wrapper
over [`java.util.logging` (JUL)](https://docs.oracle.com/javase/7/docs/api/java/util/logging/package-summary.html).

As such backwards compatibility comes from choosing the `slf4j-jdk14` logging implementation which
configures `TwitterServer` to use [`java.util.logging` (JUL)](https://docs.oracle.com/javase/7/docs/api/java/util/logging/package-summary.html)
allowing users to use the [`util-logging`](https://github.com/twitter/util/tree/develop/util-logging) 
library and traits for configuration of the JUL subsystem. 
 
This library provides the `c.t.server.logging.Logging` and `c.t.server.logging.LogFormat` traits 
which easily provides backwards compatibility for use of [`util-logging`](https://github.com/twitter/util/tree/develop/util-logging) 
loggers in `TwitterServer`.

Java users can extend `c.t.server.slf4j.jdk14.AbstractTwitterServer` for backwards compatible 
logging.

Admin HTTP Interface Logging Handler
------------------------------------

Depending on this library will install a logging handler on the [HTTP admin interface](https://twitter.github.io/twitter-server/Admin.html#admin-interface)
which allows users to dynamically change `com.twitter.logging.Logger` log levels.

For more information see the `TwitterServer` user guide section on [Logging](https://twitter.github.io/twitter-server/Features.html#logging)
