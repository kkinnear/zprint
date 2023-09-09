# Use babashka to run zprint

[![bb compatible](https://raw.githubusercontent.com/babashka/babashka/master/logo/badge.svg)](https://babashka.org)

You can format files with  zprint using babashka.  This has several benefits:

  - You don't have to download and install anything (other than babashka)
  - It runs faster (typically much faster) than the uberjar. Even on very long files, it runs about 20% faster than the uberjar.
  - The user interface is identical to the uberjar or the pre-compiled binaries
  - Parallel operation is automatically enabled

There are several approaches to using babashka to run zprint:

  * Add zprint as a task in `bb.edn`
  * Use `bbin` to install install the latest zprint

The differences are minor but worth knowing:

  * When installing zprint as a task in `bb.edn`, you must specify the
  version of zprint you want.  Upgrading is easy, just specify a newer
  version.

  * The `bbin` approach will install the latest version of zprint. Latest
  as of when you executed `bbin`.  The `bbin` approach adds a small increment
  to the startup time, on the order of perhaps 0.1s.  Not a lot, and you 
  might not notice it.

# Add zprint as a task to bb.edn

## 0. Make sure your babashka is sufficiently current

zprint requires at least babashka `1.3.183` to execute properly.
See [how to get babashka.](https://github.com/babashka/babashka#quickstart)

## 1. Make zprint a task for babashka

Add the following map to your `bb.edn` file.  You will want to adjust the
`:mvn/version` to be whatever version of zprint is current. This is 
the current version: [![Clojars Project](https://img.shields.io/clojars/v/zprint.svg)](https://clojars.org/zprint)


```
{:tasks {zprint {:extra-deps {zprint/zprint {:mvn/version "1.2.8"}},
                 :requires ([zprint.main]),
                 :task (apply zprint.main/-main *command-line-args*)}}}
```

## 2. Invoke zprint using babashka

You can now invoke the zprint task using babashka like this (assuming `bb` invokes babashka):

```
bb zprint --help
```

All of the normal switches for the zprint uberjar or zprint pre-compiled
binaries work except for `--url`, `-u`, and `--url-only`.  Which you probably
weren't interested in anyway.

# Install zprint using bbin

## -1. Make sure your babashka is sufficiently current

zprint requires at least babashka `1.3.183` to execute properly.
See [how to get babashka.](https://github.com/babashka/babashka#quickstart)

## 0. Get bbin

You must have [installed bbin](https://github.com/babashka/bbin).

## 1. Install zprint 

You just execute:
```
bbin install io.github.kkinnear/zprint
```
The default name for what is installed is `zprint`.  Using a different
name is easy:
```
bbin install io.github.kkinnear/zprint --as zprint-myname
```
where `zprint-myname` is whatever name you want.

## 2. Try zprint

The normal switches are supported:
```
zprint --help
```



