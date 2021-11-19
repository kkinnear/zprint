# Get the zprint uberjar
There exists an uberjar for zprint which requires only Java to run,
and has no external dependencies.

## 1. Go to the latest release for zprint
You can find the latest release [here](https://github.com/kkinnear/zprint/releases/latest).
## 2. Download the uberjar
The uberjar is called `zprint-filter-0.x.y`, where `x.y` varies.
Click on this to download it.
## 3. Name the uberjar whatever you want
The downloaded version of the zprint uberjar always has the version in the name.
You may wish to name it something different so that any scripts that
you have will use the new version without requiring a change.  We
will assume that you have renamed it `zprint-filter`.
```
mv zprint-filter-1.2.1 zprint-filter
```

Note that you can always find the version of zprint-filter (no matter what
you called it), by giving it the -v switch:
```
java -jar zprint-filter -v
zprint-1.2.1
```

## 3. Test it with `-e`

```
java -jar zprint-filter -e
{:agent {:object? false},
 :array {:hex? false, :indent 1, :object? false, :wrap? true},
 :atom {:object? false},
 :binding {:flow? false,
           :force-nl? false,
           :hang-diff 1,
           :hang-expand 2.0,
           :hang? true,
           :indent 2,
           :justify? false,
           :nl-separator? false},
[...]
```

The `-e` switch will output the configuration zprint will use when
run. For any values that are not the default, this will include where that
value came from (for instance, if you set something in your `~/.zprintrc`, 
that information will appear in the `-e` output). 
If you run `zprint -e`, it should output a very large map showing
all of the configuration options and their current values.  Toward the
end, it will include a key `:version` which should be the version that
you just downloaded.

## 4. Try it
The zprint uberjar you have downloaded will accept Clojure source on stdin
and produce formatted Clojure source on stdout.  It will also 
accept an options map on the command line.  Note the need for single quotes
around any options map value that you specify.
```
java -jar zprint-filter '{:width 90}' < myfile.clj 
```
This will output a formatted version of `myfile.clj` to the controlling
terminal, fit into 90 columns (instead of the default 80 columns) of output.

