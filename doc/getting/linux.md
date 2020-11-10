# Get zprint for Linux
There exists a pre-built binary for Linux which does not require Java,
and starts up in <50ms.

## 1. Go to the latest release for zprint
You can find the latest release [here](https://github.com/kkinnear/zprint/releases/latest).
## 2. Download zprintl from the above directory
The Linux pre-built binary is named `zprintl-0.x.y`, where `x.y` varies.
Click on this to download it.
## 3. Name zprint whatever you want
The downloaded version of zprintl always has the version in the name.
You may wish to name it something different so that any scripts that
you have will use the new version without requiring a change.  We
will assume that you have renamed it `zprint`.
```
mv zprintl-1.1 zprint
```

Note that you can always find the version of zprintl (no matter what
you called it), by giving it the -v switch:
```
./zprint -v
zprint-1.1
```

## 3. Put zprint into a directory in your path
To be able to run zprint it needs to be in a directory that appears in
your path.

## 4. Test it with `-e`

```
zprint -e
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

## 5. Try it
The zprint program you have installed will accept Clojure source on stdin
and produce formatted Clojure source on stdout.  It will also 
accept an options map on the command line.  Note the need for single quotes
around any options map value that you specify.
```
zprint '{:width 90}' < myfile.clj 
```
This will output a formatted version of `myfile.clj` to the controlling
terminal, fit into 90 (instead of the default 80) columns of output.

