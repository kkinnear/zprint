# How blank lines in source are handled

Any blank lines that appear at the "top level", that is, outside of
a function definition and outside of an expression __are never changed
by zprint__.

The question to be discussed here is -- how are blank lines within function
definitions (and within other expressions, for example `def` expressions)
handled? 

By default classic zprint will ignore blank lines in function definitions
and other expressions.  

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

