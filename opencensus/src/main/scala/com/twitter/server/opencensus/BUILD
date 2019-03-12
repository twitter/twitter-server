scala_library(
    sources = globs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    dependencies = [
        "3rdparty/jvm/io/opencensus:opencensus-api",
        "3rdparty/jvm/io/opencensus:opencensus-contrib-zpages",
        "finagle/finagle-core",
        "finagle/finagle-http",
        "twitter-server/server/src/main/scala",
    ],
)
