# test twitter-server

junit_tests(name='scala',
  dependencies=[
    pants('3rdparty:jackson-module-scala'),
    pants('3rdparty:junit'),
    pants('3rdparty:mockito-all'),
    pants('3rdparty:netty'),
    pants('3rdparty:scalatest'),
    pants('finagle/finagle-core'),
    pants('finagle/finagle-http'),
    pants('twitter-server'),
    pants('util/util-app'),
    pants('util/util-core')
  ],
  sources=rglobs('*.scala'),
  resources=[pants('twitter-server/src/test/resources')]
)
