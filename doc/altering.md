# Changing zprint's behavior
There are two steps to changing the way that zprint formats Clojure 
information.
## 1. Create an options map that configures zprint to behave the way you want
There are a large number of options maps configuration elements which can
be changed to get zprint to format Clojure source or data in a way different
from the default and more to your liking.

There are several ways to determine how to change the options map:
  * See below for references to some commonly configured changes
  * See the [reference](./reference.md) information, which contains
  documentation on everything that can be changed
  * Submit [an issue](https://github.com/kkinnear/zprint/issues) and
  ask how to configure zprint to do what you want

#### I want to configure zprint to change...

  * [how user defined functions are formatted](./options/fns.md)
  * [the indentation in lists](./options/indent.md)
  * [the configuration to track the "community" standard](./options/community.md)
  * [how blank lines in source are handled](./options/blank.md)
  * [how map keys are formatted](./options/maps.md)
  * [the colors used for formatting source](./options/colors.md)
  * [how the second element of a pair is indented](./options/pairs.md)
  * [how comments are handled](./options/comments.md)


## 2. Get the options map recognized by zprint when formatting
Different formatting regimes accept options map information from different
places.

### Formatting whole files
When using either the pre-built binaries, or the uberjar (with or without
appcds acceleration), options maps may be read from:
#### `$HOME/.zprintrc`
The `~/.zprintrc` (also known as `$HOME/.zprintrc`) file will be read
when zprint is first invoked.  You can put options maps changes in that
file and they will always be used (unless overridden by a later options map).
#### `:zprint` key for lein-zprint
If you are using lein-zprint, you can put an options map in the `:zprint` key
in `project.clj`.
#### `./.zprintrc` in the current working directory
If you have configured `{:cwd-zprintrc? true}` in your `~/.zprintrc` or on the
command line options map, zprint will look in the current working directory
(cwd) for a `.zprintrc` file.
#### Search for `.zprintrc` 
If you have configured `{:search-config? true}` in your `~/.zprintc` or
on the command line options map, zprint will look in the current working
directory for a `.zprintrc` file.  If it finds one, it will use it.  If it
doesn't find one, it will look in the parent of the directory for a `.zprintrc`
file and use it if it finds one.  zprint will keep looking up the directory
tree for a `.zprintrc` file, using the first one it finds.
#### Command line options map
zprint will accept an options map on the command line every time it is
called.  These options will only be used for that particular call.
#### Comments in the file
You can alter the way that zprint formats a single function or any
part of an entire file by including comments in the file which
contain [zprint comment formatting directives](./bang.md).

### When using zprint inside another program or at the REPL
When using zprint as a library, it will look for configuration information:
#### On the first call to zprint
##### `$HOME/.zprintrc`
The `~/.zprintrc` (also known as `$HOME/.zprintrc`) file will be read
when zprint is first invoked.  You can put options maps changes in that
file and they will always be used (unless overridden by a later options map).
##### `./.zprintrc` in the current working directory
If you have configured `{:cwd-zprintrc? true}` in your `~/.zprintrc` 
zprint will look in the current working directory
(cwd) for a `.zprintrc` file.
##### Search for `.zprintrc` 
If you have configured `{:search-config? true}` in your `~/.zprintc`
zprint will look in the current working
directory for a `.zprintrc` file.  If it finds one, it will use it.  If it
doesn't find one, it will look in the parent of the directory for a `.zprintrc`
file and use it if it finds one.  zprint will keep looking up the directory
tree for a `.zprintrc` file, using the first one it finds.
#### Whenever executed
##### `set-options!`
A call to `set-options!` containing an options map will be remembered
until another call to `set-options!` changes the internal options map
again.  
##### `configure-all!`
You can  re-initialize zprint by calling `configure-all!`, which
will cause all existing information remembered from `set-options` calls
to be forgotten.  A call to `configure-all!` will cause zprint to reconfigure
itself by using all available external configuration (see the places
immediately above).
##### Options map on a call to zprint
Every time you call zprint to format something, the third argument can
be an options map, which is used for only the formatting being done by
that call.
