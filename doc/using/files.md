# Use zprint to format entire source files

[1. High Performance Prebuilt Binaries](#1-high-performance-prebuilt-binaries)  
[2. Java Uberjar](#2-java-uberjar)  
[3. Clojure CLI](#3-clojure-cli)  
[4. Lein zprint](#4-lein-zprint)  
[5. zprint-clj (node based)](#5-zprint-clj-node-based)  
[Changing the formatting approach using comments in the file](#changing-the-formatting-approach-using-comments-in-the-file)   


There are several ways to use zprint to format entire source files.

## 1. High Performance Prebuilt Binaries
  * High performance: startup is fast < 50ms
  * Available for macOS and Linux
  * Does not require Java -- available as standalone binaries
  * Accepts source on stdin, produces formatted source on stdout
  * Will also format named files "in place"
  * Reads configuration from `~/.zprintrc`
  * Accepts options map on command line

```
zprintm '{:width 90}` < myfile.clj > myfile.out.clj
```
or
```
zprintm '{:width 90}' -w myfile.clj
```
will read myfile.clj, format the source, and write the result back into
myfile.clj

You can also format all of the clojure files in a directory with:
```
zprint -w *.clj
```
This will format each of the .clj files, and if there are any errors,
it will report the error for that file, and continue on processing
the rest of the files.  If there are errors formatting any file, the
contents of that file remain unchanged.

__Get prebuilt binaries for__:  
  * [macOS](../getting/macos.md)
  * [Linux](../getting/linux.md)

As you might expect:
```
zprint -h
```
is your friend!

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
or
```
java -jar zprint-filter '{:width 90}' -w myfile.clj
```
will read myfile.clj, format the source, and write the result back into
myfile.clj

You can also format all of the clojure files in a directory with:
```
java -jar zprint-filter -w *.clj
```
This will format each of the .clj files, and if there are any errors,
it will report the error for that file, and continue on processing
the rest of the files.  If there are errors formatting any file, the
contents of that file remain unchanged.

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
                         {:mvn/version "1.9.0"},
                       zprint {:mvn/version
                                      "1.0.2"}},
                    :main-opts ["-m" "zprint.main"]}},
 :deps {org.clojure/clojure {:mvn/version "1.9.0"},
        zprint {:mvn/version "1.0.2"}}}'
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
  * Leiningen plugin: `[lein-zprint "1.0.2"]`
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
__Get it__: put `[lein-zprint "1.0.2"]` in the vector that is the value of
the `:plugins` key in `project.clj`:

```clojure
(defproject zpuse "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-zprint "1.0.2"]]
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :repl-options {:init-ns zpuse.core})
```

## 5. zprint-clj (node based)

There is a node based zprint program,
[zprint-clj](https://github.com/clj-commons/zprint-clj).

### Get zprint-clj

```
npm i -g zprint-clj
```

### Try zprint-clj

```
$ zprint-clj -V
0.8.0
$ zprint-clj -h
Usage: zprint-clj [options]

Options:
  -V, --version            output the version number
  -c, --check "<pattern>"  Checks formatting without writing to output, 
                           zprint-clj -c "./out/**/*.{clj,cljs,cljc,edn}"
  -i, --input "<pattern>"  Input file, directory or glob pattern. 
                           If no output specified writes to stdout.
  -o, --out "<path>"       Output path, file or directory
  --hang                   Enable hang mode (better formatting, but 2x slowdown)
  -h, --help               output usage information
```

### Use zprint-clj

```
$zprint-clj -i "myfile.clj" -o "myfile.1.clj"
```

Note that the default for this tool is (effectively) `{:style :no-hang}`, which
means that it will never hang anything.  This doesn't produce the best looking
code, but it _does_ run much faster (about like the graalVM binaries, in
fact).

If you can use the pre-built binaries mentioned above, you would probably
be wise to do so, since they startup as fast or faster 
than Javascript/Clojurescript based programs, and run considerably faster.

### Startup speed

Casual testing indicates that, on a mid 2012 MacBook Air, zprint-clj 0.8.0 
starts up in about 222ms, while zprintm-0.5.4 starts up in about 20ms (after
you have used it once). 

### Proccessing Speed

In additional casual testing, the "... but 2x slowdown" comment 
in the help, above, seems accurate. 

Comparing zprint-clj 0.8.0 with the graalVM binary zprintm-0.5.4, using the
defaults, both took about 5 seconds to process a large file (5241 loc).
The difference is that zprintm-0.5.4 did this with the normal defaults, that
is, it produced code formatted to look as good or better than hand formatted
code since hangs were enabled by default.  The defaults for zprint-clj are 
`{:style :no-hang}`, so the resulting formatting was
not as nice.  The same file run through zprint-clj with `--hang`, to generate
output equivalent to zprintm-0.5.4 took about 12 seconds.

# Changing the formatting approach using comments in the file

You can alter the way that zprint formats a single function or any
part of an entire file by including comments in the file which
contain [zprint comment formatting directives](../bang.md).


