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
  * Available for macOS (both Intel and Apple Silicon) and Linux
  * Does not require Java -- available as standalone binaries
  * Accepts source on stdin, produces formatted source on stdout
  * Will also format or check format on named files "in place"
  * Reads configuration from `~/.zprintrc`
  * Accepts options map on command line

```
zprintm '{:width 90}` < myfile.clj > myfile.out.clj
```
will read myfile.clj, format the source for a width of 90 characters
(the default is 80), and write the result back into myfile.out.clj.

Alternatively:

```
zprintm '{:width 90}' -w myfile.clj
```
will read myfile.clj, format the source for a width of 90 characters
(the default is 80), and write the result back into myfile.clj if
it is different than the source.

You can also format all of the clojure files in a directory with:

```
zprint -w *.clj
```

This will format each of the `.clj` files, and if there are any errors,
it will report the error for that file, and continue on processing
the rest of the files.  If there are errors formatting any file, the
contents of that file remain unchanged.  The exit status is contains
the number of files with errors.

Similarly, you can check the formatting on any files with the `-c`
switch:

```
zprint -c *.clj
```

will check the formatting on all of the files with a `.clj` extension
in the current directory.  The exit status will be the number of files
which require formatting.

For both the `-w` and `-c` switches (which "write" and "check" the
specified files, respectively), there are additional options that
can be specified to produce explanatory output.  These options
must be combined with the `-w` or `-c` switches to be recognized.

They are (shown combined with the `-w` switch here, but they also
work with `-c`):

  - `-lw`   List the files being processed before they are opened
  - `-fw`   report on all files that needed to be Formatted
  - `-sw`   output a Summary when processing is completed

These can be combined:

  `-lfsw` List files as they are processed, output about 
          those that required Formatting, and output 
          a Summary at the end of the entire operation.

The exit status is unchanged by any of these additional switches:

  - for `-w` it is the number of files where there was some failure of formatting
  - for `-c` it is the number of files requiring formattting

For example:

```
% ./zprintm-1.3.0 -lfsc src/zprint/*.cljc
Processing file src/zprint/ansi.cljc
Processing file src/zprint/config.cljc
Formatting required in file src/zprint/config.cljc
Processing file src/zprint/core.cljc
Formatting required in file src/zprint/core.cljc
Processing file src/zprint/finish.cljc
Processing file src/zprint/focus.cljc
Processing file src/zprint/macros.cljc
Processing file src/zprint/main.cljc
Formatting required in file src/zprint/main.cljc
Processing file src/zprint/range.cljc
Processing file src/zprint/redef.cljc
Processing file src/zprint/rewrite.cljc
Processing file src/zprint/smacros.cljc
Processing file src/zprint/spec.cljc
Formatting required in file src/zprint/spec.cljc
Processing file src/zprint/sutil.cljc
Processing file src/zprint/zfns.cljc
Processing file src/zprint/zprint.cljc
Formatting required in file src/zprint/zprint.cljc
Processing file src/zprint/zutil.cljc
Processed 16 files, 5 of which require formatting.
```
The exit status was 5 following the above operation.

Here is another example, using the same files, where the formatting
width is changed to 90:

```
 % ./zprintm-1.3.0 '{:width 90}' -lfsc src/zprint/*.cljc
Processing file src/zprint/ansi.cljc
Processing file src/zprint/config.cljc
Formatting required in file src/zprint/config.cljc
Processing file src/zprint/core.cljc
Formatting required in file src/zprint/core.cljc
Processing file src/zprint/finish.cljc
Formatting required in file src/zprint/finish.cljc
Processing file src/zprint/focus.cljc
Formatting required in file src/zprint/focus.cljc
Processing file src/zprint/macros.cljc
Formatting required in file src/zprint/macros.cljc
Processing file src/zprint/main.cljc
Formatting required in file src/zprint/main.cljc
Processing file src/zprint/range.cljc
Formatting required in file src/zprint/range.cljc
Processing file src/zprint/redef.cljc
Formatting required in file src/zprint/redef.cljc
Processing file src/zprint/rewrite.cljc
Formatting required in file src/zprint/rewrite.cljc
Processing file src/zprint/smacros.cljc
Processing file src/zprint/spec.cljc
Formatting required in file src/zprint/spec.cljc
Processing file src/zprint/sutil.cljc
Formatting required in file src/zprint/sutil.cljc
Processing file src/zprint/zfns.cljc
Processing file src/zprint/zprint.cljc
Formatting required in file src/zprint/zprint.cljc
Processing file src/zprint/zutil.cljc
Formatting required in file src/zprint/zutil.cljc
Processed 16 files, 13 of which require formatting.
```
Despite being previously formatted for a width of 80, not all
of the files formatted differently for a width of 90, though 
all but 3 did.  Those three are short files with nothing that
would change were they formatted for a width of 90.


__Get prebuilt binaries for__:  
  * [macOS](../getting/macos.md)
  * [Linux](../getting/linux.md)

As you might expect:
```
zprint -h
```
is your friend!

```
 % ./zprintm-1.3.0 -h
zprint-1.3.0

 zprint <options-map> <input-file >output-file
 zprint <switches> <input-file >output-file
 zprint -w input-and-output-file(s)
 zprint <options-map> -w input-and-output-file(s)
 zprint <switches> -w input-and-output-file(s)

 Where zprint is any of:

  zprintm-1.3.0
  zprintl-1.3.0
  java -jar zprint-filter-1.3.0

 <options-map> is a Clojure map containing zprint options. Must be first.
               Note that since it contains spaces, it must be
               wrapped in quotes, for example:
               '{:width 120}'

               Use the -e switch to see the total options
               map, which will show you what is configurable.

 <switches> which do no formatting, only one allowed:

  -h  --help         Output this help text.
  -v  --version      Output the version of zprint.
  -e  --explain      Output non-default configuration values, showing
                     where any non-default values where set.
      --explain-all  Output full configuration, including all default
                     values, while showing where non-default values set.

 <switches> which control configuration, only one allowed:

  -d  --default      Accept no configuration input.
  -u  --url URL      Load options from URL.
      --url-only URL Load only options found from URL,
                     ignore all .zprintrc, .zprint.edn files.

 <switches> which process named files:  May follow a configuration switch
                                        or an options map, but not both!

  -w  --write FILE                read, format, and write to FILE (or FILEs),
                                  -w *.clj is supported.
  -c  --check FILE                read and check format of FILE (or FILEs)
                                  -c *.clj is supported.

 Variations on -w, --write and -c, -check:

  -lw  --list-write      FILE  like -w, but indicate which files processed.
  -fw  --formatted-write FILE  like -w, but indicate which files changed.
  -sw  --summary-write   FILE  like -w, but include a summary of the number
                               of files processed and how many required a
                               format change, as well as any errors.

 Combinations are allowed, w/write and c/check must always be last,
 and order matters for -- switches.  Examples:

   -lfw, -lfsw, -fsw, -flw, -sflw, etc.
   --list-formatted-write, --list-formatted-summary-write, etc.

 All combinations of -w and --write switches are also allowed
 with -c and --check switches:

   -lfc, -lfsc, -fsc, -flc, -sflc, etc.
   --list-formatted-check, --list-formatted-summary-check, etc.

 The -w ,-c, and -e switches are the only switches where you may also
 have an options map!
```

## 2. Java Uberjar
  * Works anywhere you can install Java
  * Startup in a few seconds
  * Java appcds will cache startup info (for some platforms), making startup about 1s
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

## 3. Using Babashka

You can use babashka to run zprint!  

  * Works anywhere you can install babashka
  * No zprint installation required
  * Startup is very fast (faster than the uberjar)
  * Processing speed is faster than the uberjar for all but the very largest files
  * Accepts source on stdin, produces formatted source on stdout
  * Reads configuration from `~/.zprintrc`
  * Accepts options map on command line

The simple instructions are [here](../getting/babashka.md).

## 4. Clojure CLI

Add the following to the `:aliases` section of `$HOME/.clojure/deps.edn`
file or to a project's `deps.edn`.

For example:

```shell
$ cat > deps.edn <<< $'
{:aliases {:zprint {:extra-deps
                      {org.clojure/clojure
                         {:mvn/version "1.9.0"},
                       zprint {:mvn/version
                                      "1.3.0"}},
                    :main-opts ["-m" "zprint.main"]}},
 :deps {org.clojure/clojure {:mvn/version "1.9.0"},
        zprint {:mvn/version "1.3.0"}}}'
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

## 5. Lein zprint
  * Leiningen plugin: `[lein-zprint "1.3.0"]`
  * Accepts configuration from `:zprint` key in project.clj
  * Will (optionally) replace existing source files with reformatted versions
  * Reads configuration from `~/.zprintrc`
  * Accept options map on command line

The only real benefit of lein-zprint over the pre-compiled binaries is that
you don't have to install new versions of zprint when using it.  You just
put the version of lein-zprint in the `project.clj` file and leiningen 
downloads it for you.

That said, using zprint as a task in babashka is equally convienent
and requies no installation beyond installing babashka. 

For example, you might use it like this:

```
lein zprint '{:width 90}' src/myproj/*.clj
Processing file: src/myproj/myfile.clj
Processing file: src/myproj/myotherfile.clj
```
__Get it__: put `[lein-zprint "1.3.0"]` in the vector that is the value of
the `:plugins` key in `project.clj`:

```clojure
(defproject zpuse "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-zprint "1.3.0"]]
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :repl-options {:init-ns zpuse.core})
```

## 6. zprint-clj (node based)

There is a node based zprint program,
[zprint-clj](https://github.com/clj-commons/zprint-clj).

The original need for this approach was that the Java based zprint tools did
not start up quickly enough.  These days that is not the case.  The
pre-compiled binaries start up very quickly, and using zprint with
babashka also starts up very quicly.  There is little reason to use
node based solutions at this time.

Also, the current version of zprint-clj uses a very old version of zprint.

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

Casual testing indicates that, on a mid 2012 MacBook Air, zprint-clj
0.8.0 starts up in about 222ms, while zprintm-0.5.4 starts up in
about 20ms (after you have used it once).  

These days, the landscape
possibilities are more complex, and even if you don't want the
pre-compiled binaries, using zprint with babashka starts up very
quickly.

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


