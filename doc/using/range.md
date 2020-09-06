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
the start-line-number and end-line-number are adjusted (usually
expanded outwards) to encompass one or more top level expressions.

The expression in which the start-line-number is located is determined,
and then the start-line-number is moved up to the first non-blank line beyond
the previous expression.  This is in order to encompass any `;!zprint
{}` directives that might appear directly before the expression.
The expression in which the end-line-number is located is determined,
and the end-line-number is moved down to the last line of that
expression.

The specifics of how the line numbers are handled are: the
start-line-number is moved to the first non-blank line after the
previous expression, where comments are considered non-blank lines.
If there is no previous expression the start-line-number is set to
the beginning of the file.  The end-line-number is moved to the
last line of the expression in which the end-line-number falls.  If
the end-line-number does not fall inside an expression, it is moved
up to the first previous non-blank line.  The range will never start
or end on a blank line inside of a file.

If the start-line-number is negative, it is considered to be before
the start of the file.  If the end-line-number is negative, nothing
will be formatted in the file.  If the end-line-number is before
the start-line-number, it will be set to the start-line-number  A
start-line-number beyond the end of the file will cause nothing to
be included in the range, while an end-line-number beyond the end
of the file will simply represent that the end of the range should
be the end of the file.

If both start-line-number and end-line-number are within the same
gap between expressions, nothing will be formatted.

Note that any `;!zprint` directives that appear before the previous
expression will not be recognized when formatting a range of lines.

Note also that zprint will not leave trailing spaces on a line, but
this is only true for lines that are part of the range -- the other lines
are untouched.

If any problems occur when trying to determine the current or previous
expressions (since a quick parse of the entire file is required
for this to happen), the entire file is formatted. 

