scala_library(
    sources = ["*.scala"],
    compiler_option_sets = ["fatal_warnings"],
    platform = "java8",
    tags = ["bazel-compatible"],
    dependencies = [
        "3rdparty/jvm/io/opencensus:opencensus-api",
        "3rdparty/jvm/io/opencensus:opencensus-contrib-zpages",
        "finagle/finagle-core",
        "finagle/finagle-http",
        "twitter-server/server/src/main/scala",
    ],
)
