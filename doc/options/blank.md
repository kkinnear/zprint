# How blank lines in source are handled

Any blank lines that appear at the "top level", that is, outside of
a function definition and outside of an expression are usually not
changed by zprint.  However, you can force specific line spacing between
top level expressions.

### Force specific line spacing between top level expressions

While zprint usually leaves the line spacing between top level expressions
alone, you can force specific spacing by using `:parse {:interpose ...}`
in an options map.

You can separate the expressions with addtional newlines (or pretty
much anything that ends with a new-line) by including an options
map with `:parse {:interpose ...}` in it.  The string must end
with a new-line, or the resulting formatting will not be correct.

Here we force a single blank line between all top level expressions:

```clojure
(czprint "(def a :b) (def c :d)" 40 {:parse-string-all? true :parse {:interpose "\n\n"}})
(def a :b)

(def c :d)
```

Note that `zprint-file-str`, the routine used for all of the
standalone zprint binaries, uses `:parse-string-all`, so for instance
the options map: `{:parse {:interpose "\n\n\n"}}` would force two
blank line spacing between all top level expressions when used with one
of the standaline binaries.

Generally, `:parse {:interpose ...}` is not used, but it is an option if
you want to enforce a particular inter-expression spacing.

### How are blank lines handled inside of top level expressions

How are blank lines within function definitions (and within other
expressions, for example `def` expressions) handled?

By default classic zprint will ignore blank lines in function definitions
and other expressions, and place newlines (or sometimes blank lines) 
whereever it thinks that they should go based on its configuration.  

There are several options available to you if you would like blank lines
to be kept in source.

  * Respect blank lines `{:style :respect-bl}`  
  This style will perform the formatting done by classic zprint, with the
  exceptions that any blank lines will be kept intact in the source.
  You can [see several examples here](../types/respectbl.md).

  * Respect new lines `{:style :respect-nl}`  
  This style will keep __all__ of the newlines in the source.  Both blank
  lines and all other newlines will be kept.  zprint will add newlines as
  necessary to attempt to fit to the `:width`, but will never remove a
  newline even if everything would fit fine in fewer lines.
  You can see [examples of this here](../reference.md#respect-nl).
  
  * Indent only `{:style :indent-only}`  
  This still will keep __all__ of the newlines in the source, and will
  __never add any new ones__.  Everything stays on the line it started
  out on.  No width is enforced, because zprint can't do anything about
  a line that is too long.  You can see 
  [examples of this here](../types/indentonly.md).

You can place an options map like `{:style :respect-bl}`
[anywhere an options map is accepted](../altering.md#2-get-the-options-map-recognized-by-zprint-when-formatting).

