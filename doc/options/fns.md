# Change how user defined functions (or macros) are formatted

While most functions or macros will format well without special processing,
some functions are more clearly comprehended when processed specially.
There is a part of the options map which maps function names into
a function "type" for formatting.  This is the `:fn-map`.

Generally, if a function call fits on the current
line, none of these classifications matter.  These only come into play
when the function call doesn't fit on the current line.  

You can add additional function name -> function type mappings to
the `:fn-map`, or you can change the existing `:fn-map` mapping to
be one more to your liking.

### What does a function type look like?

A function type can be:
  * a simple keyword, for example: `:arg1`
  * a vector which starts with a keyword function type and
contains an option map to be used only when formatting this particular
function, for example: `[:arg2-pair {:vector {:wrap? false}}]`

### How to figure out the function type you want?

If you want to add a function name -> function type (or change an existing
mapping), you have two approaches you can use to determine the function
type that you would like to use:

1. Find an existing function that formats the way that you want, determine
its function type, and use that.  You can find the function type of an
existing function by entering `(czprint nil :explain)` at the REPL, and
looking at the list of functions under the key `:fn-map` in the output.

2. Look through the [list of possible function types](../reference.md#function-classification-for-pretty-printing), and figure out which
one best matches what you want.

You can also change an existing function name -> function type mapping to
have a function type of `:none`, if you wish to remove an existing mapping
and, essentially, cause zprint to not recognize a particular function name
as requiring special processing.

### How to change the function name -> function type mapping

For example, if you have a function which takes three arguments, and
the first argument is distinguished from the second and third in some
way, and you would like it to appear on the line with the function
name, thus:
```clojure
(myfn one
  two
  three)
```
Then you would use an options map:
```clojure
{:fn-map {"myfn" :arg1}}
```
This options map can appear
[anywhere an options map is accepted](../altering.md#2-get-the-options-map-recognized-by-zprint-when-formatting).
It could be associated with this project,
in a project oriented `./zprintrc` file, or it could be associated
with this person by appearing in their `~/.zprintrc` file.  It could
also appear in a command line options map (to test out the concept
to see if it works), or in an options map in an actual call to
zprint at the REPL.

### How to change the way functions which do not appear in the `:fn-map` are formatted

You can add a key-value pair to the `:fn-map` where the key
is `:default`, and the value will be used for any function which does
not appear in the `:fn-map`, or which does appear in the `:fn-map` but
whose value is `:none`.

