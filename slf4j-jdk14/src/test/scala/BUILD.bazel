junit_tests(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scalacheck",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        scoped(
            "twitter-server/slf4j-jdk14/src/main/scala",
            scope = "runtime",
        ),
        "finagle/finagle-base-http/src/main",
        "finagle/finagle-core/src/main",
        "finagle/finagle-http/src/main/scala",
        "twitter-server/server/src/main/scala",
        "twitter-server/slf4j-jdk14/src/main/scala/com/twitter/server/logging",
    ],
)
