FAQ
===

Is it TwitterServer or twitter-server?
--------------------------------------

TwitterServer. We only use twitter-server in reference to the project’s code.

What are some recommended best practices?
-----------------------------------------

1. Override ``com.twitter.app.App.failfastOnFlagsNotParsed`` to ``true``.
   This is so that you fail-fast instead of being surprised by code that is
   reading from flags before they have been parsed.

2. Do not register application routes onto
   ``com.twitter.server.HttpMuxer$`` or ``com.twitter.server.AdminHttpServer$``
   as that they are intended to be used for administration/internal pages.

Do I have to use `finagle-stats`?
---------------------------------

No, TwitterServer is agnostic to your metrics implementation.
However, some features are only available or work best when using that library.
