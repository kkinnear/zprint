# Community Standards

There have been several efforts over the years to develop some
standards for how well formatted Clojure source code should look.
One of these efforts has been the development of a 
[Clojure style guide](https://github.com/bbatsov/clojure-style-guide) 
otherwise known as the "community standards".

There is sufficient flexibility in zprint to support the community
standards if you wish to use them.  You can use zprint to format
Clojure source to the "community standards" by using 
`{:style :community}`.

### Why aren't the "community standards" the default?

It is not the default for zprint for the simple reason that most
people do not seem to follow all of the formatting guidelines
required by the community standards when formatting their code.
This is not to say that these standards are wrong -- most of them
are used by almost everyone, and most of them are indeed the default
configuration for zprint.  But there are a few differences between
the default zprint configuration and the community standards.

This difference is reflected in the `:style :community`:

```clojure
    :community {:binding {:indent 0},
                :fn-map {"apply" :none,
                         "assoc" :none,
                         "filter" :none,
                         "filterv" :none,
                         "map" :none,
                         "mapv" :none,
                         "reduce" :none,
                         "remove" :none,
                         "with-meta" :none-body},
                :list {:indent-arg 1},
                :map {:indent 0},
                :pair {:indent 0}},
```
This change to the defaults for zprint does several things:

  * Do not indent the second element of a pair when you have to flow 
  the pair.

  * Do not format some functions specially to make them more understandable.

  * Treat "body" functions and "argument" function differently in the way
  that indentation is performed for these functions.

### Do not indent the second element of a pair

You can read lots about this [here](./pairs.md).

### Different indents for body functions and argument functions?

At some point, the "community standards" for Clojure source formatting
made a distinction between "body functions" and "argument functions",
and wanted "argument functions" to have an indent of 1, and "body functions"
to have an indent of 2.  The theory seemed to be that "body functions"
were functions which had executable forms in them, oftern (though not
always) of indeterminate number.  "Argument functions", on the other
hand, had arguments (typically a fixed number) which were values and
not primarily executable forms.  

To support this (to my mind rather unnecessary and not widely adopted)
distinction, zprint will accept the suffix `-body` to many of the function
types, and will also accept a value for `:indent-arg`, which (if non-nil)
will be used as the indent for argument functions (which is 
everything that is not explicitly classified as a body function).

## How to get community endorsed formatting?

You can place the options map `{:style :community}` 
[anywhere an options map is accepted](../altering.md#2-get-the-options-map-recognized-by-zprint-when-formatting).



