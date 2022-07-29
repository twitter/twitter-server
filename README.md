# TwitterServer

[![Build Status](https://github.com/twitter/twitter-server/workflows/continuous%20integration/badge.svg?branch=develop)](https://github.com/twitter/twitter-server/actions?query=workflow%3A%22continuous+integration%22+branch%3Adevelop)
[![Project status](https://img.shields.io/badge/status-active-brightgreen.svg)](#status)
[![Gitter](https://badges.gitter.im/twitter/finagle.svg)](https://gitter.im/twitter/finagle?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.twitter/twitter-server_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.twitter/twitter-server_2.12)

TwitterServer defines a template from which servers at Twitter are
built. It provides common application components such as an
administrative HTTP server, tracing, stats, etc. These features are
wired in correctly for use in production at Twitter.

## Status

This project is used in production at Twitter (and many other organizations),
and is being actively developed and maintained.

## Documentation

Browse the [user guide](https://twitter.github.io/twitter-server/).

## Releases

[Releases](https://maven-badges.herokuapp.com/maven-central/com.twitter/twitter-server_2.12)
are done on an approximately monthly schedule. While [semver](https://semver.org/)
is not followed, the [changelogs](CHANGELOG.rst) are detailed and include sections on
public API breaks and changes in runtime behavior.

## Getting involved

* Website: https://twitter.github.io/twitter-server/
* Source: https://github.com/twitter/twitter-server/
* Mailing List: [finaglers@googlegroups.com](https://groups.google.com/forum/#!forum/finaglers)
* Chat: https://gitter.im/twitter/finagle

## Contributing

We feel that a welcoming community is important and we ask that you follow Twitter's
[Open Source Code of Conduct](https://github.com/twitter/.github/blob/main/code-of-conduct.md)
in all interactions with the community.

The `release` branch of this repository contains the latest stable release of
TwitterServer, and weekly snapshots are published to the `develop` branch. In general
pull requests should be submitted against `develop`. See
[CONTRIBUTING.md](https://github.com/twitter/twitter-server/blob/release/CONTRIBUTING.md)
for more details about how to contribute.

## License

Copyright 2013 Twitter, Inc.

Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
