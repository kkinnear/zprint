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

The Linux version (running on docker on the same Mac) takes something 
like 250ms to startup, 722ms for 129 loc, and 4.7s for 3978 loc, which 
is still as fast for startup as node, and about three times faster
to process than node and moderately faster than the JVM. 


## Installation

The good news about the graalvm zprint-filter -- it is fast!  The bad
news is that we are back to a different version for each platform.

### Linux

You can download a pre-build Linux image from zprint GitHub, look for `zprintl-0.4.10`
(or the appropriate version) in the latest zprint release.

### MacOS

You have to build this one yourself, but it is trivial to do.  Four
steps:

  1. Download the Oracle GraalVM EE distribution from
  [here](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html).
  This isn't hard, but you have to create an account by giving them
  your email to get in.  Once I created the account, I had to go
  back to the download link and try again, and then log-in, and
  then I could download graalvm.  Kind of a pain, but then -- what
  is 55ms startup worth?  
  2. Unpack it into a directory, remember the path and version of graalvm for later.  
  3. Download the latest `zprint-filter-0.4.10` and the `build.zprintm` script from the
  releases area of zprint on GitHub.  
  4. Make the `build.zprintm` script executable: `chmod +x build.zprintm`
  5. Run the `build.zprintm` script:

```
./build.zprintm  path-to-graalvm path-to-zprint-filter name-of-output 
```
Note that the `path-to-graalvm` should include directory which is
the version of the distribution.  For instance, the version that
was current when this was written was `graalvm-1.0.0-rc1`, so the
path would have this at the end.

An example:

```
build.zprintm /Users/kkinnear/graalvm/graalvm-1.0.0-rc1 zprint-filter-0.4.10 zprintm-0.4.10
```

This will run for a good long time (several minutes).   It will
periodically generate ouput.  The entire build looks like this:

``` 
Build on Server(pid: 90495, port: 26681)*
   classlist:   2,287.99 ms
       (cap):   2,376.12 ms setup:   4,154.84 ms
  (typeflow): 165,810.81 ms
   (objects):  43,478.36 ms
  (features):     142.56 ms
    analysis: 228,611.48 ms universe:   8,236.15 ms
     (parse):   8,447.98 ms
    (inline):   7,312.73 ms
   (compile):  69,974.27 ms
     compile:  87,621.25 ms
       image:  14,406.50 ms write:  20,466.15 ms
     [total]: 365,910.00 ms
``` 
The first few lines should come out pretty quickly.  It takes
minutes to run after that.

When it completes, you will have `zprintm-0.4.10` (or whatever you
chose to call it), and this is a native image which will run on
MacOS __without__ any JVM involved.

## Usage

The `zprintl-0.4.10` or `zprintm-0.4.10` It is a regular "unix-like" filter which will
run on Linux or MacOS respectively -- __without any JVM__ involed!.  You
use it like:

```
./zprintm-0.4.10 < core.clj > core.new.clj
```
It reads the source code and spits out formatted source code.  It will accept an options
map as on the command line.  Don't forget to put "'" around the options map, or the results
won't be what you expect.  An example:

```
./zprintm-0.4.10 '{:style :community}' < core.clj > core.new.clj
```
If you put it on your path, you can just run it from anywhere.  I use it in my editor,
to format a function while I'm editing it.  For most functions, it runs fast enough I don't even notice it.

#### Caveat

Zprint will not build directly with `native-image`, as there are 5 "unsupported"
exceptions.  I haven't done any analysis of what thiese might be,
since they aren't obvious.  However, it will build with

```-H:+ReportUnsupportedElementsAtRuntime```

and in my testing, I don't
get any "unsupported" elements reported at runtime.  Should you
encounter one of these, __please__ submit an issue!

