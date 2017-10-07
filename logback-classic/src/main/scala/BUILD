scala_library(
  provides = scala_artifact(
    org='com.twitter',
    name='twitter-server-logback-classic',
    repo=artifactory,
  ),
  dependencies=[
    '3rdparty/jvm/org/slf4j:slf4j-api',
    '3rdparty/jvm/ch/qos/logback:logback-classic',
    '3rdparty/jvm/ch/qos/logback:logback-core',
    '3rdparty/jvm/org/slf4j:jcl-over-slf4j',
    '3rdparty/jvm/org/slf4j:log4j-over-slf4j',
    '3rdparty/jvm/org/slf4j:jul-to-slf4j',
    'finagle/finagle-base-http/src/main/scala',
    'finagle/finagle-http/src/main/scala',
    'twitter-server/logback-classic/src/main/resources',
    'twitter-server/server/src/main/scala',
    'util/util-slf4j-api/src/main/scala',
  ],
  fatal_warnings=True,
  sources=rglobs('*.scala'),
  scope='forced',  # Exposes a service-loaded class.
  exports=[
    '3rdparty/jvm/org/slf4j:slf4j-api',
    '3rdparty/jvm/ch/qos/logback:logback-classic',
  ],
)
