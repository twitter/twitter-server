scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    provides = scala_artifact(
        org = "com.twitter",
        name = "twitter-server-slf4j-jdk14-handler",
        repo = artifactory,
    ),
    strict_deps = True,
    dependencies = [
        "finagle/finagle-http/src/main/scala",
        "twitter-server/server/src/main/scala",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-logging/src/main/scala/com/twitter/logging",
    ],
    exports = [],
)
