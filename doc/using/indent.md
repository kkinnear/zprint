# Use zprint to correct indentation, but not otherwise reformat a file

While a common use of zprint is to completely reformat a file based
on its configuration, zprint also has a `:style` which will simply
correct the indentation of a file, and regularize the white space
in the file.  It will not change the formatting of the file.  In
particular, it will not add or remove any newlines in the file, so
everything stays on the line that it was on during input.

To get this behavior use:

```clojure
{:style :indent-only}
```

When using `:style :indent-only`, zprint will recognize existing
hangs, such as this:

```clojure
(this is
      a
      test)
```
by recognizing when the third element of a list falls directly
under the second element of a list.  When it sees this situation,
it will hang all of the elments of the list.

So this:
```clojure
(this is
      a
   test
         this
is
 only
        a
   test)
```
will come out like this:
```clojure
(this is
      a
      test
      this
      is
      only
      a
      test)
```

For many more examples, look [here](../types/indentonly.md).
