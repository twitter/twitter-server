# test twitter-server

junit_tests(
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala',
    'finagle/finagle-core/src/main/scala:scala',
    'finagle/finagle-http/src/main/scala:scala',
    'finagle/finagle-toggle',
    'finagle/finagle-zipkin-core/src/main/scala:scala',
    'twitter-server/src/main/scala',
    'twitter-server/src/test/resources',
    'twitter-server/src/test:base',
    'util/util-app/src/main/scala:scala',
    'util/util-core/src/main/scala:scala',
    'util/util-lint/src/main/scala:scala',
    'util/util-logging/src/main/scala:scala',
    'util/util-registry/src/main/scala:scala',
    'util/util-stats/src/main/scala:scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
)
