.. Author notes: this file is formatted with restructured text
  (http://docutils.sourceforge.net/docs/user/rst/quickstart.html)
  as it is included in TwitterServer's user's guide.

Note that ``PHAB_ID=#`` and ``RB_ID=#`` correspond to associated messages in commits.

Unreleased
----------

* Escape user-provided string from the returned text.  This removes a potential vector for an XSS
  attack. ``PHAB_ID=D574844``

20.10.0
-------

No Changes

20.9.0
------

No Changes

* Bump version of Jackson to 2.11.2. ``PHAB_ID=D538440``

* Encode the request URL names in /admin/clients/<client_name> and /admin/servers/<server_name>.
  ``PHAB_ID=D540543``

* If a client connecting to an instance of TwitterServer is sending a client certificate,
  its expiry date (i.e. `Not After`) is now included as part of the information listed.
  ``PHAB_ID=D528982``.

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Add relative_name field to metrics in the Metrics Metadata endpoint and bump the
  endpoints version number to 2.0. ``PHAB_ID=D552357``

20.8.1
------

* Check SecurityManager permissions in the `ContentHandler` to ensure that contention
  snapshotting is allowed. ``PHAB_ID=D531873``

20.8.0
------

No Changes

20.7.0
------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Make `Lifecycle` and `Lifecycle.Warmup` self-typed to `TwitterServer`. `Lifecycle` was previously
  self-typed to `c.t.app.App` and `Lifecycle.Warmup` previously had no self-type restrictions. These
  traits can now only be mixed into instances of `TwitterServer`. The `Lifecycle.DetatchedWarmup`
  trait is introduced to allow users to transition to it, where they were previously extending
  `Lifecycle.Warmup` without mixing into a `TwitterServer`. `Lifecycle.DetatchedWarmup`
  is immediately deprecated and will be removed in a future release. ``PHAB_ID=D507392``

20.6.0
------

No Changes

20.5.0
------

* Make lookup of Admin `LoggingHandler` more resilient when multiple implementations are detected.
  Now instead of perhaps using an incorrect handler the server will instead emit a lint rule violation
  and not attempt to install a logging handler ensuring that only when a single `LoggingHandler`
  is located that the functionality is enabled. ``PHAB_ID=D484965``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* Bump jackson version to 2.11.0. ``PHAB_ID=D457496``

20.4.1
------

No Changes

20.4.0
------

No Changes

20.3.0
------

No Changes

20.2.1
------

* Add `c.t.server.AbstractTwitterServer#onExitLast` to allow Java users to
  easily register a final exit function. ``PHAB_ID=D433874``

20.1.0
------

Changed
~~~~~~~

* Upgrade logback to 1.2.3 ``PHAB_ID=D415888``


19.12.0
-------

* Upgrade to jackson 2.9.10 and jackson-databind 2.9.10.1 ``PHAB_ID=D410846``

* Multiple changes have happened around query parameter retrieval in order
  to remove duplicated functionality from Twitter Server that exists in
  Finagle. Users are encouraged to use finagle-http's `Uri` class within their
  own code to retrieve params. ``PHAB_ID=D398387``
  * The `parse` method of `HttpUtils` has been removed.
  * The protected `getParams` method of `TwitterHandler` has been removed.
  * The signature of the `getGraceParam` method of `ShutdownHandler` has
    been changed to take a `Request`.

* Add a `disableAdminHttpServer` property to the `AdminHttpServer` that can be used to
  prevent the `AdminHttpServer` from starting by default on a `TwitterServer`. ``PHAB_ID=D397925``

* The `ResourceHandler` companion object is no longer a `JavaSingleton`.
  ``PHAB_ID=D399947``

* Update ScalaTest to 3.0.8, and ScalaCheck to 1.14.0. ``PHAB_ID=D408331``

19.11.0
-------

* Add initial support for JDK 11 compatibility. ``PHAB_ID=D365075``

* The endpoints section of the clients page has been fixed
  to no longer render an incorrect html line break tag. ``PHAB_ID=D391907``

19.10.0
-------

No Changes

19.9.0
------

* Changed `com.twitter.server.AdminHttpServer.routes` from a setter to getter, use it to get
  all admin http server Routes. Use `com.twitter.server.AdminHttpServer.addAdminRoute` to add
  a Route and `com.twitter.server.AdminHttpServer.addAdminRoutes` to add many Routes.
  ``PHAB_ID=D354013``

19.8.0
------

Changes
~~~~~~~

* Upgrade to Jackson 2.9.9. ``PHAB_ID=D345969``

19.7.0
------

Changes
~~~~~~~

* Remove `c.t.server.util.TwitterStats` as it is dead code. ``PHAB_ID=D330013``

19.6.0
------

Changes
~~~~~~~

* Remove the TwitterServer dependency on Netty 3. ``PHAB_ID=D328148``

New Features
~~~~~~~~~~~~

* Added an admin page, /admin/servers/connections.json with details about incoming connections,
  including encryption status and remote principal ``PHAB_ID=D329940``

19.5.1
------

No Changes

19.5.0
------

Changes
~~~~~~~

* Add `DuplicateFlagDefinitions` lint rule which is violated when multiple Flags with the same
  name are added to the underlying `com.twitter.app.App#flag` `com.twitter.app.Flags` instance.
  ``PHAB_ID=D314410``

19.4.0
------

Changes
~~~~~~~

* Remove deprecated uses of `c.t.server.ShadowAdminServer`. ``PHAB_ID=D269149``

* Mix in the `c.t.finagle.DtabFlags` to allow servers to append to the "base" `c.t.finagle.Dtab`
  delegation table. Users can now call `c.t.finagle.DtabFlags#addDtabs()` when they want to append
  the parsed Flag value to the `Dtab.base` delegation table. Users should note to only call this
  method _after_ Flag parsing has occurred (which is after **init** and before **premain**).

  We also update the `c.t.server.handler.DtabHandler` to always return a proper JSON response of
  the currently configured `c.t.finagle.Dtab.base`. ``PHAB_ID=D297596``

19.3.0
------

* Change the /admin/histograms?h=...-style endpoints to return data in the same style as
  /admin/histograms.json. This should make it easier to use tools to parse data from either
  endpoint. ``PHAB_ID=D279779``

19.2.0
------

No Changes

19.1.0
------

* Propagate the admin server's shutdown to the handlers that are registered with the admin server.
  ``PHAB_ID=D254656``

18.12.0
-------

No Changes

18.11.0
-------

No Changes

18.10.0
-------

Changes
~~~~~~~

* Deprecate `c.t.server.AdminHttpServer#routes`. Routes should be added to the `AdminHttpServer`
  via `c.t.server.AdminHttpServer#addAdminRoutes`. ``PHAB_ID=D230247``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* Update `BuildProperties` to not emit a warning when no `build.properties` file can be
  located. ``PHAB_ID=D229586``

18.9.1
------

No Changes

18.9.0
------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* Move logic to parse the server `build.properties` file out the `c.t.server.handler.ServerInfoHandler`
  and into a utility object, `c.t.server.BuildProperties` to allow for accessing by other server
  logic such that the properties do not need to be re-parsed anytime access is desired. Failure to
  load the properties can result in the server not starting in the case of a Fatal exception
  being thrown. ``PHAB_ID=D201207``

* Update `TwitterServer` trait to override the inherited `ShutdownTimer` to be the Finagle
  `DefaultTimer` instead of the `c.t.util.JavaTimer` defined by default in `c.t.app.App`. Also
  update the overridden `suppressGracefulShutdownErrors` in `TwitterServer` to be a val since
  it is constant (instead of a def). ``PHAB_ID=D212896``

18.8.0
------

New Features
~~~~~~~~~~~~

* Add `onExit` lifecycle callback to `c.t.server.Hook` (which is now an abstract class) to allow
  implemented hooks to execute functions in the `App#onExit` lifecycle phase. Note:
  `c.t.server.Hook#premain` now has a default implementation and requires the `override` modifier.
  ``PHAB_ID=D198379``

18.7.0
------

No Changes

18.6.0
------

New Features
~~~~~~~~~~~~

* Added an admin page at "/admin/balancers.json" with details about client load balancers,
  including both configuration and current status. ``PHAB_ID=D171589``

18.5.0
------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* Overloaded `c.t.server.AdminHttpServer#isolate` to accept a
  `Service[Request, Response]`. ``PHAB_ID=D157891``

18.4.0
------

No Changes

18.3.0
------

No Changes

18.2.0
------

Dependencies
~~~~~~~~~~~~

* Removed 'finagle-zipkin-core' as a depdendency since there was no
  code in twitter-server which used it. ``PHAB_ID=D129515``

18.1.0
------

No Changes

17.12.0
-------

Bug Fixes
~~~~~~~~~

* Treat `io.netty.channel.epoll.Native.epollWait0` as an idle thread on
  "/admin/threads". This method is observed when using Netty 4's native
  transport. ``PHAB_ID=D115058``

17.11.0
-------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Change to apply JUL log format in the `c.t.server.logging.Logging` trait
  constructor instead of in `premain` to apply format as early in the logging
  stack as possible. However, this means that users overriding the
  `def defaultFormatter` will not be able to use any flags to configure their
  formatting, note: the default `LogFormatter` does not use flags.
  ``PHAB_ID=D106534``

17.10.0
-------

Release Version Format
~~~~~~~~~~~~~~~~~~~~~~

* From now on, release versions will be based on release date in the format of
  YY.MM.x where x is a patch number. ``PHAB_ID=D101244``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* All admin endpoints except ping + healthcheck are now by-default served outside
  the global worker pool. ``PHAB_ID=D96633``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Rename AdminHttpServer#defaultHttpPort to AdminHttpServer#defaultAdminPort.
  ``PHAB_ID=D97394``

1.32.0
------

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Removed code related to `util-events` including `EventSink`, `JsonSink`,
  `TraceEventSink`. The corresponding "/admin/events" and "/admin/events/record/"
  admin HTTP endpoints are also removed. ``PHAB_ID=D82346``

1.31.0
------

No Changes

1.30.0
------
Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* The admin server now waits for other registered closables to shut down
  before commencing its own shutdown. ``RB_ID=916421``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Removed `c.t.server.Closer` trait. Behavior has been moved to
  `c.t.app.App`. ``RB_ID=915485``

1.29.0
------

No Changes

1.28.0
------

Dependencies
~~~~~~~~~~~~

* Bump guava to 19.0. ``RB_ID=907807``

1.27.0
------

New Features
~~~~~~~~~~~~

* Add lint error warning on admin summary page. ``RB_ID=898202``

1.26.0
------

Bug Fixes
~~~~~~~~~

* Server graphs are now displaying again on the Twitter Server Summary page.
  ``RB_ID=898422``

1.25.0
------

New Features
~~~~~~~~~~~~

* Add ability to specify admin interface UI grouping, alias, and path for admin
  handlers using the newly added Route and RouteUi. ``RB_ID=886829``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* Removed `scala-xml` dependency. ``RB_ID=890315``

1.24.0
------

No Changes

1.23.0
------

No Changes

1.22.0
------

New Features
~~~~~~~~~~~~

* No longer need to add an additional resolver that points to maven.twttr.com.
  ``RB_ID=878967``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* ShutdownHandler and AbortHandler accept only POST requests and ignore
  non-POST requests. ``RB_ID=848212``

1.21.0
------

Bug Fixes
~~~~~~~~~

* Escape user input that is rendered in HTML, and make bin/travisci publish
  finagle-toggle. ``RB_ID=848579``

New Features
~~~~~~~~~~~~

* Add optional HTTP request parameter `filter` to `/admin/registry.json`
  allowing for simple filtering of the returned JSON. ``RB_ID=842784``

* Add admin endpoint, `/admin/toggles`, for
  `c.t.finagle.toggle.StandardToggleMap` registered `Toggles`.
  ``RB_ID=847434``

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Removed AdminHttpServer#mkRoutex method. This method was created during the
  migration away from direct usage of netty http types and is now
  redundant. ``RB_ID=835083``

* Builds are now only for Java 8 and Scala 2.11. See the
  `blog post <https://finagle.github.io/blog/2016/04/20/scala-210-and-java7/>`_
  for details. ``RB_ID=828898``

1.20.0
------

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* Introduce a new lifecycle event `prebindWarmup` for warmup code
  which needs to run before the service handles traffic. GC has
  moved from `warmupComplete` to `prebindWarmup`. ``RB_ID=819411``

New Features
~~~~~~~~~~~~

* Update to register TwitterServer as library in /admin/registry.json. ``RB_ID=825129``
* Add a FailFast lint rule for Memcached client. ``RB_ID=808727``

1.19.0
------

New Features
~~~~~~~~~~~~

* Add AdminHttpServer#boundAddress to expose the bound address of
  the AdminHttpServer. ``RB_ID=798322``

1.18.0
------

New Features
~~~~~~~~~~~~

* Add new admin endpoint "/" which redirects requests to "/admin". ``RB_ID=777247``

1.17.0
------

NOT RELEASED

1.16.0
------

1.15.0
------

New Features
~~~~~~~~~~~~

* Add new admin endpoint "/admin/lint" which checks for possible issues with
  performance or configuration. ``RB_ID=754348``

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* We no longer export a "scheduler/productivity" stat because various implementation
  details made it difficult to report reliably.

1.14.0
------

Dependencies
~~~~~~~~~~~~

* Converted to finagle-httpx. Projects that depend transitively on
  finagle-http through twitter-server will need to switch to finagle-httpx.
  ``RB_ID=741454`` ``RB_ID=740731``

1.13.0
------

1.12.0
------

* Enable syntax highlighting in the docs

1.11.0
------

New Features
~~~~~~~~~~~~

* Introduce AbstractTwitterServer, a Java-friendly version of TwitterServer. RB_ID=661878

1.10.0
------

New Features
~~~~~~~~~~~~

* TwitterServer collects a statically allocated ring of runtime events, which be viewed
  at /admin/events, and downloaded as JSON with a HTTP client like curl (or by simply
  omitting the User-Agent header in the request).

* TwitterServer exports runtime configuration data about your service, which can be
  downloaded as json at /admin/registry.json.

Dependencies
~~~~~~~~~~~~

* Bumped many dependency versions.

1.9.0
-----

New Features
~~~~~~~~~~~~

* Most noticeably, the admin server received a complete UI redesign. All http endpoints are
  now available via a navigation pane. We provide a simple API for service owners to include
  ad-hoc admin pages which will be part of the navigation pane. Note, it is still possible to
  join the admin server’s namespace via the global com.twitter.finagle.http.HttpMuxer.

* When using the twitter Metrics library, the admin server now scrapes your stats to
  extract a quick summary of how your server is performing. This includes secondly
  success rates for your server and least performant downstreams. We provide a /admin/metrics
  endpoint which can watch stats (at secondly granularity) and extract them via http queries.

* More recently, Finagle clients and servers began to retain information about their composition.
  This is useful in examining the modules and parameters that comprise a specific implementation.
  We now surface this information in the admin server via /admin/clients/<client_name> and
  /admin/servers/<server_name>

* TwitterServer now exposes a /admin/registry.json endpoint, which speaks json and exposes the
  values from util-registry as labels. Most labels are long-lived, and tend to represent something
  about a process that is true for the entire lifetime, like the version of a library, or what a
  flag was set to.

Dependencies
~~~~~~~~~~~~

* Remove dependency on mustache for admin server in favor of templating
  via string interpolation. This is more hygienic for web applications
  and frameworks built atop twitter-server.

1.8.0
-----

New Features
~~~~~~~~~~~~

* Add the ability to promote objects to old gen before serving
* Export everything from build.properties at /admin/server_info

Runtime Behavior Changes
~~~~~~~~~~~~~~~~~~~~~~~~

* Add merge_base merge_base_commit_date and scm_repository to server_info
* AdminHttpServer now disables tracing
* Export gauge on eden allocations
* Improve heuristic for returning html or not in WebHandler
* Initial redesign of admin pages

Breaking API Changes
~~~~~~~~~~~~~~~~~~~~

* Remove ServerInfo class: export /admin/server_info directly from build.properties file

1.7.6
-----

* twitter-server: Add gauge on eden allocations
* twitter-server: Do not trace the admin http server
* twitter-server: JvmStats needs to call Allocations.start()
* twitter-server: Log severely if a flag is read at the wrong time
* twitter-server: Parameterize IndexHandler on a `patterns: Seq[String]`
* twitter-server: Proper resource loading in admin pages
* twitter-server: Redesign of twitter-server admin page

1.7.3
-----

- Add admin endpoint for per-client configuration
- Add trace ID to twitter-server logging
- Create a logging handler for on-the-fly logging updates

1.7.2
-----

- release finagle v6.18.0
- release util v6.18.0
- user guide: Add blurb about filtering out stats

1.7.1
-----

- Upgrade versions of all dependencies
- Admin dtab handler: display base dtab
- Change productivity stat to cpuTime/wallTime

1.7.0
-----

- Bump finagle to 6.16.1-SNAPSHOT
- Bump util to 6.16.1-SNAPSHOT
- Disable admin server stats

1.6.3
-----

- Define type for statsReceiver explicitly so that it can be overloaded
- Store gauge references (otherwise only weakly referenced)
- Enforce close grace period for com.twitter.app.App
- upgrade finagle/util to 6.15.0

1.6.2
-----

- Add com.twitter.io.Charsets and replace the use of org.jboss.netty.util.CharsetUtil
- Fix twitter-server execution test

1.6.1
-----

- upgrade finagle to 6.13.1
- upgrade util to 6.13.2

1.6.0
-----

- upgrade finagle version to 6.13.0
- Implement application-level shutdown handling in App.
- Bug-fix: Refresh JVM memory snapshots on stats collection Motivation
- Bug-fix: set content-length when responding from TwitterHandler

1.5.1
-----

- update finable to 6.12.1
- update util to 6.12.1

1.5.0
-----

- Add logging to TwitterHandlers
- Report on deadlock conditions in admin/contentions
- Twitter server handler for dumping the current dtab
- TwitterHandler: non-root logger
- update finagle version to 6.12.0
- update util version to 6.12.0

1.4.1
-----

- Upgrade finagle to 6.11.1
- Upgrade util to 6.11.1

1.4.0
-----

- Remove finagle-stats dependency so that alternate stats packages can be used such as ostrich
- Add a hooking mechanism and expose an API to install the Dtab using the hooking mechanism
- Upgrade finagle to 6.10.0
- Upgrade util to 6.10.0

1.3.1
-----

- Upgrade finagle to 6.8.1
- Upgrade util to 6.8.1

1.3.0
-----

- Upgrade finagle to 6.8.0
- Upgrade util to 6.8.0
- Adds a cautious registration to HttpMuxer / adds a default metrics endpoint to twitter-server
- Docs: Pointed out that you need the finagle-stats jar on your classpath
- Sync jackson versions in twitter-server
- Revert ordering of TwitterServer mixins.
- Mix in Closer by default... again.

1.2.0
-----

- Support staged names introduced in Finagle
- Add glog-style log formatting
- Remove finagle-stats as a dependency
- Don't stat admin endpoints

1.1.0
-----

- Add ability to defer /health endpoint registration
- Add new stats for current memory usage.
- Change twitter-server admin http server flag and symbol name
- Enable zipkin
- Make Logging trait more flexible for easy extension
- New scheduler "productivity" stats, dispatches.

1.0.3
-----

- bump finagle to 6.5.2
- bump util to 6.3.8

1.0.2
-----

- bump finagle to 6.5.1
- bump util to 6.3.7

1.0.1
-----

- Initial Release
