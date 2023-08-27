# Use babashka to run zprint

[![bb compatible](https://raw.githubusercontent.com/babashka/babashka/master/logo/badge.svg)](https://babashka.org)

You can run zprint using babashka.  This has several benefits:

  - You don't have to download and install anything (other than babashka)
  - It runs faster (typically much faster) than the uberjar for all but extremly long files (>6K lines), and isn't really slow for even those files.
  - It behaves just like the uberjar or the pre-compiled binaries

## 0. Make sure your babashka is sufficiently current

zprint requires at least babashka `1.3.183` to execute properly.
See [how to get babashka.](https://github.com/babashka/babashka#quickstart)

## 1. Make zprint a task for babashka

Put this in your `bb.edn` file.  You will want to adjust the
`:mvn/version` to be whatever version of zprint is current:

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

