junit_tests(
    sources = ["**/*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/log4j",
        "3rdparty/jvm/org/scalatest",
        "3rdparty/jvm/org/scalatestplus:junit",
        "3rdparty/jvm/org/slf4j:slf4j-api",
        "finagle/finagle-core/src/main",
        "twitter-server/server/src/main/scala",
        "twitter-server/slf4j-log4j12/src/main/scala",
        "util/util-slf4j-api/src/main/scala",
    ],
)
