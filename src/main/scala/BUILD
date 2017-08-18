# main twitter-server

scala_library(
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'twitter-server',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-annotations',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-core',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala',
    '3rdparty/jvm/io/netty:netty',
    '3rdparty/jvm/io/netty:netty4',
    'finagle/finagle-base-http/src/main/scala:scala',
    'finagle/finagle-core',
    'finagle/finagle-http/src/main/scala:scala',
    'finagle/finagle-netty4-http',
    'finagle/finagle-netty4/src/main/scala:scala',
    'finagle/finagle-toggle',
    'finagle/finagle-tunable/src/main/scala:scala',
    'finagle/finagle-zipkin-core',
    'twitter-server/src/main/resources',
    'util/util-app',
    'util/util-core/src/main/scala:scala',
    'util/util-jvm',
    'util/util-lint',
    'util/util-logging',
    'util/util-registry',
    'util/util-stats/src/main/scala:scala',
    'util/util-tunable/src/main/scala:scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  # Exposes a service-loaded class.
  scope='forced',
  strict_deps=True,
)
