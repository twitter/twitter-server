# main twitter-server

scala_library(name='scala',
  provides = artifact(
    org = 'com.twitter',
    name = 'twitter-server',
    repo = pants('pants-support/ivy:internal'),
  ),
  dependencies=[
    '3rdparty:jackson-core',
    '3rdparty:jackson-databind',
    '3rdparty:jackson-module-scala',
    'finagle/finagle-core',
    'finagle/finagle-http',
    'finagle/finagle-zipkin',
    '3rdparty:mustache-compiler',
    'util/util-app',
    'util/util-core',
    'util/util-jvm',
    'util/util-logging'
  ],
  sources=rglobs('*.scala'),
  resources=['twitter-server/src/main/resources'],
)
