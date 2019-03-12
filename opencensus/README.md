# OpenCensus zPages

This module integrates TwitterServer with [OpenCensus zPages](https://opencensus.io/zpages/).

## Current State

This library is in an experimental state.

## Details

By mixing in `ZPagesAdminRoutes` into a `TwitterServer`, zPages
will be served on admin routes:
 
 - /rpcz
 - /statz
 - /tracez
 - /traceconfigz

For example:

``` 
import com.twitter.server.TwitterServer
import com.twitter.server.opencensus.ZPagesAdminRoutes

object MyServer extends TwitterServer with ZPagesAdminRoutes {
  // ...
}
```

