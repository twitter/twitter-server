scala_library(
    sources = ["com/twitter/server/handler/slf4j/jdk14/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "twitter-server-slf4j-jdk14",
        repo = artifactory,
    ),
    scope = "forced",  # Exposes a service-loaded class.
    strict_deps = True,
    tags = [
        "bazel-compatible",
        "logging_impl",
        "logging_impl_check_whitelisted_target",
    ],
    dependencies = [
        "3rdparty/jvm/org/slf4j:slf4j-api",
        scoped(
            "3rdparty/jvm/org/slf4j:jcl-over-slf4j",
            scope = "runtime",
        ),
        scoped(
            "3rdparty/jvm/org/slf4j:log4j-over-slf4j",
            scope = "runtime",
        ),
        "3rdparty/jvm/org/slf4j:slf4j-jdk14",
        "finagle/finagle-base-http/src/main",
        "finagle/finagle-http/src/main/scala",
        "twitter-server/server/src/main/scala",
        "twitter-server/slf4j-jdk14/src/main/resources",
        "util/util-core/src/main/scala/com/twitter/io",
        "util/util-logging/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/org/slf4j:slf4j-api",
        "util/util-logging/src/main/scala",
    ],
)
