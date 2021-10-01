# Comment Formatting Directives

In order to properly format a file, sometimes you want to alter the
value of the zprint options map for a single function definition,
or turn it off completely and then on again later.  Or, possibly,
set some defaults which hold while formatting only this file.

While the defaults that you have established with your `~/.zprintrc`
file may be perfectly appropriate for the vast majority of the
functions and data given in a file, there are frequently one or two
functions which would benefit from a different formatting configuration.
For example, sometimes `{:style :justified}` will make one function
look much more understandable, while not being appropriate for most
of the remaining functions in the file.

Trying to find the common denominator zprint configuration can be
a frustrating effort, and isn't actually required.  You can include
lines in the file which will alter the zprint formatting for one
or more functions in the file.  You can alter the formatting for
the next function definition (really, the next top level form), or
you can alter the formatting for the rest of the file.  You can
turn off all zprint processing for one top level form or until it
is turned back on by another zprint formatting directive.

This is all possible because of the zprint comment API.

If a comment starts with the string `;!zprint` (beginning in column 1), 
then the rest of the string will be parsed as a zprint options map.

For example:
```
;!zprint {:vector {:wrap? false}}
```
will turn off vector wrapping in the file and it will stay that way
until the end of the file (or another `;!zprint` comment alters it).

### ;!zprint Directives

The formatting directives are only recognized when they start in
column 1 and begin with:

```
;!zprint 
```

When `zprint-file-str` (when using zprint as a library) or any of the 
pre-built binaries distributed with zprint, find a comment line that 
begins with `;!zprint`, they will read the rest of the line as a 
Clojure s-expression, and assume that it is a zprint options map.  
In addition, there is one additional key for the options map given 
to a `;!zprint` directive beyond those used for other zprint option maps; 
the `:format` key.  Using the `;!zprint` API you can:

  * Turn off formatting in the file:

  ```
  ;!zprint {:format :off}
  ```

  * Turn the formatting back on in the file (it is on by default):

  ```
  ;!zprint {:format :on}
  ```

  * Skip formatting the next non-whitespace, non-comment element
  in the file (e.g., the next definition):

  ```
  ;!zprint {:format :skip}
  ```

  * Change the formatting used for the remainder of the file (until
  it is changed again, of course):

  ```
  ;!zprint {<<zprint-options>zprint-options>}
  ```

  * Change the formatting used for just the next non-whitespace and
  non-comment element of the file (e.g., the next definition):

  ```
  ;!zprint {:format :next <<zprint-options>zprint-options>}
  ```


#### Example -- turn off `[:vector :wrap?]` for a single definition

The help text you get from `(zprint nil :help)` is organized as a
vector of strings, one per line.  If I were to run `zprint-file-str`
on a string which contained the source file where the help is
defined, it will wrap all of the elements of the vector (which in this
case are strings of the help) as tightly as it can into
80 columns. When that happens, the help text doesn't read clearly
in the source file, making it hard to modify.

Here are two definitions, essentially identical, of the beginning
of the help text for zprint.  The second is readable due to the
`;!zprint` directive in the source file:

```
(def help-str
  (vec-str-to-str [(about) "" " The basic call uses defaults, prints to stdout"
                   "" "   (zprint x)" ""
                   " All zprint functions also allow the following arguments:"
                   "" "   (zprint x <width>)" "   (zprint x <width> <options>)"
                   "   (zprint x <options>)"]))

;!zprint {:format :next :vector {:wrap? false}}

(def help-str-readable
  (vec-str-to-str [(about)
                   ""
                   " The basic call uses defaults, prints to stdout"
                   ""
                   "   (zprint x)"
                   ""
                   " All zprint functions also allow the following arguments:"
                   ""
                   "   (zprint x <width>)"
                   "   (zprint x <width> <options>)"
                   "   (zprint x <options>)"]))

```

### Not getting what you expect?

The same validation used for option maps given to the zprint API
at the repl is performed on the options map given to every `;!zprint`
directive.

If a `;!zprint ` directive contains an options map that is formatted
incorrectly, you will get an exception indicating the problem.  The
exception will reference the particular `;!zprint` directive by
number, starting with 1, in the file-string given to `zprint-file-str`.

