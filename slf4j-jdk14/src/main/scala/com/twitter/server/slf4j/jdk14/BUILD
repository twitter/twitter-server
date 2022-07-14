scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    provides = scala_artifact(
        org = "com.twitter",
        name = "twitter-server-slf4j-jdk14",
        repo = artifactory,
    ),
    strict_deps = True,
    tags = ["bazel-compatible"],
    dependencies = [
        "twitter-server/server/src/main/scala",
        "twitter-server/slf4j-jdk14/src/main/scala/com/twitter/server/logging",
    ],
    exports = [
        "twitter-server/slf4j-jdk14/src/main/scala/com/twitter/server/logging",
    ],
)
