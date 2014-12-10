# main twitter-server

scala_library(name='scala',
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'twitter-server',
    repo = artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-core',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala',
    'finagle/finagle-core',
    'finagle/finagle-http',
    'finagle/finagle-zipkin',
    'util/util-app',
    'util/util-core',
    'util/util-jvm',
    'util/util-logging'
  ],
  sources=rglobs('*.scala'),
  resources=['twitter-server/src/main/resources'],
)
