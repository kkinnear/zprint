# Use zprint to format a range of lines in a file

Usually, zprint is used to format all of the lines in a file.

However, you can format a range of lines in a file using either the
command line zprint programs, or when using zprint as a library.

A range specification looks like:

```clojure
{:input {:range {:start start-line-number :end end-line-number}}}
```
where start-line-number and end-line-number are zero based, and
are inclusive (that is to say, both lines will be formatted).

Since zprint can only format effectively if it knows the left margin,
the start-line-number and end-line-number are expanded outwards to
encompass one or more top level expressions.

The expression in which the start-line-number is located is determined,
and then the start-line-number is moved up to the first line beyond
the previous expression.  This is in order to encompass any `;!zprint
{}` directives that might appear directly before the expression.
The expression in which the end-line-number is located is determined,
and the end-line-number is moved down to the last line of that
expression.

Note that any `;!zprint` directives that appear before the previous
expression will not be recognized when formatting a range of lines.

If any problems occur when trying to determine the current or previous
expressions (since a quick parse of the entire file is required
for this to happen), the entire file is formatted. 

