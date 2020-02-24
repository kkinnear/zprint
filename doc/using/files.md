# Use zprint to format entire source files
There are several ways to use zprint to format entire source files:
## 1. High Performance Prebuilt Binaries
  * High performance: startup is fast < 50ms
  * Available for macOS and Linux
  * Does not require Java -- available as standalone binaries
  * Accepts source on stdin, produces formatted source on stdout
  * Reads configuration from `~/.zprintrc`
  * Accepts options map on command line

```
zprintm '{:width 90}` < myfile.clj > myfile.out.clj
```
__Get prebuilt binaries for__:  
  * [macOS](../getting/macos.md)
  * [Linux](../getting/linux.md)

## 2. Java Uberjar
  * Works anywhere you can install Java
  * Startup in a few seconds
  * Java appcds will cache startup info, making startup about 1s
  * Accept source on stdin, produces formatted source on stdout
  * Reads configuration from `~/.zprintrc`
  * Accept options map on command line

Uberjar example:

```
java -jar zprint-filter '{:width 90}' < myfile.clj > myfile.out.clj
```
__Get the__: 
  * [uberjar](../getting/uberjar.md) _starts in several seconds_
  * [accelerated uberjar](../getting/appcds.md) _starts in about 1s_

## 3. Clojure CLI

Add the following to the `:aliases` section of `$HOME/.clojure/deps.edn`
file or to a project's `deps.edn`.

For example:

```shell
$ cat > deps.edn <<< $'
{:aliases {:zprint {:extra-deps
                      {org.clojure/clojure
                         #:mvn{:version "1.9.0"},
                       zprint #:mvn{:version
                                      "0.5.4"}},
                    :main-opts ["-m" "zprint.main"]}},
 :deps {org.clojure/clojure #:mvn{:version "1.9.0"},
        zprint #:mvn{:version "0.5.4"}}}'
$ clj -A:zprint < deps.edn
$ clj -m zprint.main <deps.edn
```

Then you can use the following as filter and pretty printer:

```shell
cat /path/to/file.clj | clojure -A:zprint
```

__Note__: If you are going to be doing this a lot (and can't use the 
the high performance prebuilt binaries -- #1, above) the 
[accelerated uberjar](../getting/appcds.md) will 
startup much faster and run as fast once it has started.

## 4. Lein zprint
  * Leiningen plugin: `[lein-zprint "0.5.n"]`
  * Accepts configuration from `:zprint` key in project.clj
  * Will (optionally) replace existing source files with reformatted versions
  * Reads configuration from `~/.zprintrc`
  * Accept options map on command line

For example, you might use it like this:

```
lein zprint '{:width 90}' src/myproj/*.clj
Processing file: src/myproj/myfile.clj
Processing file: src/myproj/myotherfile.clj
```
__Get it__: put `[lein-zprint "0.5.4"]` in the vector that is the value of
the `:plugins` key in `project.clj`:

```clojure
(defproject zpuse "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-zprint "0.5.4"]]
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :repl-options {:init-ns zpuse.core})
```

## 4. Other approaches
Prior the prebuilt, high performance binaries (see above), a number of
approaches were created to execute in various Javascript/Clojurescript
engines.  At this point, the pre-built binaries startup as fast or faster 
and run much faster than any Javascript based zprint.
