# test twitter-server

junit_tests(
  dependencies=[
    'finagle/finagle-toggle',
    'twitter-server/src/test:base',
    'twitter-server/src/main/scala',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala',
    'finagle/finagle-core/src/main/scala:scala',
    'finagle/finagle-http/src/main/scala:scala',
    'finagle/finagle-zipkin-core/src/main/scala:scala',
    'util/util-app/src/main/scala:scala',
    'util/util-core/src/main/scala:scala',
    'util/util-events/src/main/scala:scala',
    'util/util-lint/src/main/scala:scala',
    'util/util-logging/src/main/scala:scala',
    'util/util-registry/src/main/scala:scala',
    'util/util-stats/src/main/scala:scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  resources=['twitter-server/src/test/resources']
)
