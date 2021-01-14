# Get zprint for macOS
There exists a pre-built binary for macOS which does not require Java,
and starts up in <50ms.

## Download from GitHub

### 1. Go to the latest release for zprint
You can find the latest release [here](https://github.com/kkinnear/zprint/releases/latest).
### 2. Download zprintm from the above directory
The macOS pre-built binary is named `zprintm-0.x.y`, where `x.y` varies.
Click on this to download it.
### 3. Name zprint whatever you want
The downloaded version of zprintm always has the version in the name.
You may wish to name it something different so that any scripts that
you have will use the new version without requiring a change.  We
will assume that you have renamed it `zprint`.
```
mv zprintm-1.1.0 zprint
```

Note that you can always find the version of zprintm (no matter what
you called it), by giving it the -v switch:
```
./zprint -v
zprint-1.1.0
```

### 4. Put zprint into a directory in your path
To be able to run zprint it needs to be in a directory that appears in
your path.

## Install from Homebrew

zprint is available as a [Homebrew Cask](https://formulae.brew.sh/cask/zprint):

### 1. See if it is the version you want

```
brew info --cask zprint
```

If this is the version you want, then ...

### 2. Install it from Homebrew

```
brew install --cask zprint
```

## Usage

### 1. Test it with `--explain-all`

```
zprint --explain-all
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

The `--explain-all` switch will output the configuration zprint will use when
run. For any values that are not the default, this will include where that
value came from (for instance, if you set something in your `~/.zprintrc`, 
that information will appear in the `--explain-all` output). 
If you run `zprint --explain-all`, it should output a very large map showing
all of the configuration options and their current values.  Toward the
end, it will include a key `:version` which should be the version that
you just downloaded.

If you use the `-e` switch, you will see just the configuration options that
are different from the defaults, if any:

```
zprint -e
{:width {:set-by "Home directory file: /Users/kkinnear/.zprintrc", :value 80}}
```

Here you see that I had an options map containing `{:width 80}` in my
`~/.zprintrc` file.

### 2. Try it
The zprint program you have installed will accept Clojure source on stdin
and produce formatted Clojure source on stdout.  It will also 
accept an options map on the command line.  Note the need for single quotes
around any options map value that you specify.

```
zprint '{:width 90}' < myfile.clj 
```
This will output a formatted version of `myfile.clj` to the controlling
terminal, fit into 90 (instead of the default 80) columns of output.

You can check a file's formatting with `-c`:

```
zprint -lfsc myfile.clj
```
You can format a file in place, rewriting it with reformatted source:

```
zprint -lfsw myfile.clj
```

See the [detailed instructions](../using/files.md) on using the prebuilt
binaries (and uberjar) for more information.

```
zprint -h
```

may also be helpful.

