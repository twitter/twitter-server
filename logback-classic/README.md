TwitterServer `logback-classic`
===============================

This library provides a wrapper over the [`logback-classic`](https://www.slf4j.org/manual.html#swapping) logging 
implementation for use in `TwitterServer` along with the appropriate [SLF4J bridges](https://www.slf4j.org/legacy.html)
for other logging implementations.

NOTE:
-----

Users MUST provide a compatible version of the [Logback](https://logback.qos.ch/) logging implementation.

Admin HTTP Interface Logging Handler
------------------------------------

Depending on this library will install a logging handler on the [HTTP admin interface](https://twitter.github.io/twitter-server/Admin.html#admin-interface)
which allows users to dynamically change `ch.qos.logback.classic.Logger` log levels.

For more information see the `TwitterServer` user guide section on [Logging](https://twitter.github.io/twitter-server/Features.html#logging)


