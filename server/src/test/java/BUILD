# The compilation of this target acts as a test to make sure scala APIs work in Java.
# There is no test within this target.
java_library(
    sources = ["**/*.java"],
    compiler_option_sets = [],
    dependencies = [
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/scala-lang:scala-library",
        "3rdparty/jvm/org/scalacheck",
        "3rdparty/jvm/org/scalatestplus:junit",
        "3rdparty/jvm/org/slf4j:slf4j-api",
        scoped(
            "3rdparty/jvm/org/slf4j:slf4j-nop",
            scope = "runtime",
        ),
        "finagle/finagle-core/src/main",
        "finagle/finagle-http/src/main/scala",
        "twitter-server/server/src/main/scala",
        "util/util-app/src/main/scala",
        "util/util-core:scala",
        "util/util-lint/src/main/scala",
        "util/util-slf4j-api/src/main/scala",
        "util/util-stats/src/main/scala",
    ],
)
