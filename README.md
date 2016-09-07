# TwitterServer

[![Build status](https://travis-ci.org/twitter/twitter-server.svg?branch=develop)](https://travis-ci.org/twitter/twitter-server)
[![Codecov branch](https://img.shields.io/codecov/c/github/twitter/twitter-server/develop.svg)](http://codecov.io/github/twitter/twitter-server?branch=develop)
[![Project status](https://img.shields.io/badge/status-active-brightgreen.svg)](#status)
[![Gitter](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/twitter/finagle?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/com.twitter/twitter-server_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/com.twitter/twitter-server_2.11)

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

[Releases](https://maven-badges.herokuapp.com/maven-central/com.twitter/twitter-server_2.11)
are done on an approximately monthly schedule. While [semver](http://semver.org/)
is not followed, the [changelogs](CHANGES) are detailed and include sections on
public API breaks and changes in runtime behavior.

## Getting involved

* Website: https://twitter.github.io/twitter-server/
* Source: https://github.com/twitter/twitter-server/
* Mailing List: [finaglers@googlegroups.com](https://groups.google.com/forum/#!forum/finaglers)
* Chat: https://gitter.im/twitter/finagle

## Contributing

We feel that a welcoming community is important and we ask that you follow Twitter's
[Open Source Code of Conduct](https://engineering.twitter.com/opensource/code-of-conduct)
in all interactions with the community.

The `master` branch of this repository contains the latest stable release of
TwitterServer, and weekly snapshots are published to the `develop` branch. In general
pull requests should be submitted against `develop`. See
[CONTRIBUTING.md](https://github.com/twitter/twitter-server/blob/master/CONTRIBUTING.md)
for more details about how to contribute.
