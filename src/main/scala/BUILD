# main twitter-server

scala_library(name='scala',
  dependencies=[
    pants('3rdparty:jackson-core'),
    pants('3rdparty:jackson-databind'),
    pants('3rdparty:jackson-module-scala'),
    pants('finagle/finagle-core'),
    pants('finagle/finagle-http'),
    pants('finagle/finagle-zipkin'),
    pants('util/util-app'),
    pants('util/util-core'),
    pants('util/util-jvm'),
    pants('util/util-logging')
  ],
  sources=rglobs('*.scala'),
  resources=[pants('twitter-server/src/main/resources')],
  provides=artifact(org='com.twitter',
                    name='twitter-server',
                    repo='pants-support/ivy:internal',),
)
