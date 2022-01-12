[DEPRECATED] TwitterServer `slf4j-log4j12`
==========================================

This library provides a wrapper over the [`slf4j-log4j12`](https://www.slf4j.org/manual.html#swapping) logging 
implementation for use in `TwitterServer` along with the appropriate [SLF4J bridges](https://www.slf4j.org/legacy.html)
for other logging implementations.

NOTE:
-----

Users MUST provide a compatible version of the [Log4j 1.2](https://logging.apache.org/log4j/1.2/) logging implementation.

Admin HTTP Interface Logging Handler
------------------------------------

Depending on this library will install a logging handler on the [HTTP admin interface](https://twitter.github.io/twitter-server/Admin.html#admin-interface)
which allows users to dynamically change `org.apache.log4j.Logger` log levels.

For more information see the `TwitterServer` user guide section on [Logging](https://twitter.github.io/twitter-server/Features.html#logging)
