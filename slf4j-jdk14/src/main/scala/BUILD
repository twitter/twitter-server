scala_library(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "twitter-server-slf4j-jdk14",
        repo = artifactory,
    ),
    scope = "forced",  # Exposes a service-loaded class.
    tags = ["logging_impl_check_whitelisted_target"],
    dependencies = [
        "3rdparty/jvm/org/slf4j:jcl-over-slf4j",
        "3rdparty/jvm/org/slf4j:log4j-over-slf4j",
        "3rdparty/jvm/org/slf4j:slf4j-api",
        "3rdparty/jvm/org/slf4j:slf4j-jdk14",
        "finagle/finagle-base-http/src/main/scala",
        "finagle/finagle-http/src/main/scala",
        "twitter-server/server/src/main/scala",
        "twitter-server/slf4j-jdk14/src/main/resources",
        "util/util-logging/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/org/slf4j:slf4j-api",
        "3rdparty/jvm/org/slf4j:slf4j-jdk14",
        "util/util-logging/src/main/scala",
    ],
)
