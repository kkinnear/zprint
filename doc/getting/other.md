# Other ways to use zprint

There are a number of other ways to access the formatting capability
provided by zprint.  

## `lumo` and `planck`

You can use zprint as a library in either `lumo` or `planck`.

## zprint-clj (node based)

There is a node based zprint program,
[zprint-clj](https://github.com/clj-commons/zprint-clj) which creates an
executable that will run zprint on source files.  It has slightly
different defaults than other programs -- in particular, it does
not by default allow "hangs" for expressions.  This is because the
processing time for zprint is considerably longer if hangs are
allowed, because zprint formats everything as a hang and a flow,
and compares the results to see which is better.  Since `zprint-clj`
is node based, the additional processing time is more costly than
it would be using the Java virtual machine

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
`{:style :no-hang}`, so the code was
not as nice.  The same file run through zprint-clj with `--hang`, to generate
output equivalent to zprintm-0.5.4 took about 12 seconds.


