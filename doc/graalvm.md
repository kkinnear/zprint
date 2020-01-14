# A fast zprint filter

Given the advent of graalvm from Oracle Labs, you can
now have a native image zprint-filter which will accept Clojure(script)
source code on stdin and emit formatted Clojure(script)
source code on sdtout.  It will both __startup faster than__ the equivalent
code in __node.js__, and __run faster than__ code running on __the JVM__.

__55ms to startup the full application!__

## Performance

These numbers are approximate, and are from a mid-2012 MacBook Air.
Your numbers will almost certainly be better:

| Version of zprint-filter-0.4.9 | Startup (format "hello world") | Format 129 loc | Format 3978 loc |
|---------|--------------------------------|----------------|-----------------|
| graalvm native-image | 0.055s | 0.330s  | 3.425s|
| JVM 1.8 appcds | 1.111s | 2s | 6.981s |
| node | 0.260s | 2s | 18s |

The Linux version is about the same as the MacOS version.
I don't have a directly comparable Linux box to measure
it on.

## Installation

The good news about the graalvm zprint-filter -- it is fast!  The bad
news is that we are back to a different version for each platform.

### Linux

You can download a pre-built Linux image from zprint GitHub, look for `zprintl-0.5.4`
(or the appropriate version) in the latest zprint release.

### MacOS

You can now download a pre-built MacOS image from zprint GitHub, look for `zprintm-0.5.4`
(or the appropriate version) in the latest zprint release.

## Usage

The `zprintl-0.5.4` or `zprintm-0.5.4` It is a regular "unix-like" filter which will
run on Linux or MacOS respectively -- __without any JVM__ involved!.  You
use it like:

```
./zprintm-0.5.4 < core.clj > core.new.clj
```
It reads the source code and spits out formatted source code.  It will accept an options
map as on the command line.  Don't forget to put "'" around the options map, or the results
won't be what you expect.  An example:

```
./zprintm-0.5.4 '{:style :community}' < core.clj > core.new.clj
```
If you put it on your path, you can just run it from anywhere.  I use it in my editor,
to format a function while I'm editing it.  For most functions, it runs fast enough I don't even notice it.

#### Help Text

In addition to an options map, there are a few simple switches.  Here
is the help text:
```
zprint-0.5.4

 zprint <options-map> <input-file >output-file
 zprint <switches <input-file >output-file

 Where zprint is any of:

  zprintm-0.5.4
  zprintl-0.5.4
  java -jar zprint-filter-0.5.4

 <options-map> is a Clojure map containing zprint options.
               Note that since it contains spaces, it must be
               wrapped in quotes, for example:
               '{:width 120}'

               Use the -e switch to see the total options
               map, which will show you what is configurable.

 <switches> may be any single one of:

  -d  --default      Accept no configuration input.
  -h  --help         Output this help text.
  -u  --url URL      Load options from URL.
      --url-only URL Load only options found from URL, ignore all others.
  -v  --version      Output the version of zprint.
  -e  --explain      Output configuration, showing where
                     non-default values (if any) came from.

 You can have either an <options-map> or <switches>, but not both!
 ```

The `-u` and `--url` switches will apply the options map found at the
given URL as though the options were specified on the command-line.  That
is to say that they will be applied last and will thus typically override
any that were specified in the `~/.zprintrc` or in any `.zprintrc` files
found in the project directory.

The `--url-only` switch will configure the zprint program to only use the 
options map found at the given URL to format the source.  Any formatting
options given in the `~/.zprintrc` or any local `.zprintrc` files will be
ignored.  

However, the `~/.zprintrc` and possibly local `.zprintrc` files will be
examined and any `:cache` or `:url` values seen in those files will be used
when the `--url-only` capability creates or examines the options which were
found from a previously looked up URL.

You can specify `{:cwd-zprintrc? true}` or `{:search-config? true}` in
an options map on the command line, and the zprint will honor those 
options when initially configuring.  This is new in version `0.5.4`.
Prior to that version, these two options were only used if set in the
`~/.zprintrc` file.

#### Caveat

Zprint will not build directly with `native-image`, as there are 5 "unsupported"
exceptions.  I haven't done any analysis of what these might be,
since they aren't obvious.  However, it will build with

```-H:+ReportUnsupportedElementsAtRuntime```

and in my testing, I don't
get any "unsupported" elements reported at runtime.  Should you
encounter one of these, __please__ submit an issue!

