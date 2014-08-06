# test twitter-server

junit_tests(name='scala',
  dependencies=[
    '3rdparty:jackson-databind',
    '3rdparty:jackson-module-scala',
    '3rdparty:junit',
    '3rdparty:mockito-all',
    '3rdparty:netty',
    '3rdparty:scalatest',
    'finagle/finagle-core',
    'finagle/finagle-http',
    'twitter-server',
    'util/util-app',
    'util/util-core'
  ],
  sources=rglobs('*.scala'),
  resources=['twitter-server/src/test/resources']
)
