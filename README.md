# zprint

__zprint__ is a library and command line tool providing a variety
of pretty printing capabilities for both Clojure code and Clojure/EDN
structures.  It can meet almost anyone's needs.  As such, it supports
the a number of major source code formattng approaches.

## See zprint:

  * [__classic zprint__](./doc/types/classic.md) -- ignores whitespace 
  in function definitions and formats code with a variety of heuristics 
  to look as good as hand-formatted code 
  ([_see examples_](./doc/types/classic.md))
  * [__respect blank lines__](./doc/types/respectbl.md) -- similar to 
  classic zprint, but blank lines inside of function defintions are retained, 
  while code is otherwise formatted to look beautiful
  ([_see examples_](./doc/types/respectbl.md))
  * [__indent only__](./doc/types/indentonly.md) -- very different from 
  classic zprint -- no code ever changes lines, it is only correctly 
  indented on whatever line it was already on
  ([_see examples_](./doc/types/indentonly.md))

In addition, zprint is very handy [__to use at the REPL__](./types/repl.md).

## Use zprint:

  * [to format whole files](./doc/using/files.md)
  * [while using an editor](./doc/using/editor.md)
  * [at the REPL](./doc/using/repl.md)
  * [from inside a Clojure(script) program](./doc/using/library.md)

## Get zprint:

  * [a standalone binary for macOS](./doc/getting/macos.md)    _starts in <50 ms_
  * [a standalone binary for Linux](./doc/getting/linux.md)    _starts in <50 ms_
  * [an uberjar for any Java enabled platform](./doc/getting/uberjar.md)    _starts in several seconds_
  * [an accelerated uberjar for any Java enabled platform](./doc/getting/appcds.md)    _starts in about 1s_
  * [a library to use at the REPL](./doc/using/repl.md)

## Alter zprints formatting behavior:

  * [what do you do to change zprint's behavior](./doc/altering.md)

### I want to change...

  * [how user defined functions are formatted](./doc/options/fns.md)
  * [the indentation in lists](./doc/options/indent.md)
  * [the configuration to track the "community" standard](./doc/options/community.md)
  * [how blank lines in source are handled](./doc/options/blank.md)
  * [how map keys are formatted](./doc/options/maps.md)
  * [the colors used for formatting source](./doc/options/colors.md)
  * [how the second element of a pair is indented](./doc/options/pairs.md)
  * [how comments are handled](./doc/options/comments.md)
  * [anything else...](./doc/reference.md)

## Reference

  * The zprint [API]/(./doc/reference.md#api).
  * 


* [Introduction to Configuration](./doc/reference.md#introduction-to-configuration)
* [Overview](./doc/reference.md#overview)
* [How to Configure zprint](./doc/reference.md#how-to-configure-zprint)
* [Configuration Interface](./doc/reference.md#configuration-interface)
* [What is Configurable](./doc/reference.md#what-is-configurable)
* [Widely Used Configuration Parameters](./doc/reference.md#widely-used-configuration-parameters)
* [:agent, :atom, :delay, :fn, :future, :promise](./doc/reference.md#agent-atom-delay-fn-future-promise)
* [:array](./doc/reference.md#array)
* [:binding](./doc/reference.md#binding-key)
* [:comment](./doc/reference.md#comment)
* [:extend](./doc/reference.md#extend-key)
## :list
## :map
## :object
## :output
## :pair
## :pair-fn
## :reader-cond
## :record
## :set
## :spec
## :style and :style-map
## :tab
## :vector
## Debugging the configuration


## Usage

### Clojure 1.9, 1.10, 1.10.1:

__Leiningen ([via Clojars](http://clojars.org/zprint))__

[![Clojars Project](http://clojars.org/zprint/latest-version.svg)](http://clojars.org/zprint)


### Clojurescript:

zprint has been tested in each of the following environments:

  * Clojurescript 1.10.520
    - figwheel 0.5.19
    - shadow-cljs 2.8.62
  * `lumo` 1.10.1
  * `planck` 2.24.0

It requires `tools.reader` at least 1.0.5, which all of the environments
above contain.

### Clojure 1.8:

The last zprint release built with Clojure 1.8 was [zprint "0.4.15"].

In addition to the zprint dependency, you also need to
include the following library when using Clojure 1.8:

```
[clojure-future-spec "1.9.0-alpha17"]
```
### Contributors

A number of folks have contributed to zprint, not all of whom
show up on GitHub because I have integrated the code or suggestions manually.

  * `:option-fn` and `:fn-format` for enhanced vector formatting: @milankinen
  * Fixed missing require in `spec.cljc`: @Quezion
  * Corrected readme: @griffis
  * Fixed nested reader conditional: @rgould1
  * Clarified and added useful example for clj usage: @bherrmann7
  * Suggested fix for international chars and graalVM native image: @huahaiy

Thanks to everyone who has contributed fixes as well as everyone who has
reported an issue.  I really appreciate all of the help making zprint better
for everybody!

### Acknowledgements

At the core of `zprint` is the `rewrite-clj` library by Yannick
Scherer, which will parse Clojure source into a zipper.  This is a
great library!  I would not have attempted `zprint` if `rewrite-clj`
didn't exist to build upon.  The Clojurescript port relies on Magnus
Rundberget's port of `rewrite-clj` to Clojurescript, `rewrite-cljs`.
It too worked with no issues when porting to Clojurescript!


## License

Copyright © 2016-2020 Kim Kinnear

Distributed under the MIT License.  See the file LICENSE for details.
