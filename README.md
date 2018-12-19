# zprint

__zprint__ is a library providing a pretty printing capability for
both Clojure code and Clojure/EDN structures.  It can be used as a library,
either embedded in a larger codebase, or as a useful utility at the
repl.  It can be configured to process entire files or just take small
sections of code and reformat them on demand.

If you want to use the zprint library to format your Clojure source, 
you have many options:

  * A super-fast [native-image](doc/graalvm.md) __prebuilt__ for MacOS or 
    Linux. 
  * Using the released [zprint-filter](doc/filter.md) uberjar, you can 
    pretty-print functions from within many editors
  * Leiningen:  [lein-zprint][leinzprint] to format entire source files
  * Boot: [boot-fmt][bootfmt] to format entire source files
  * Node: [zprint-clj][zprintclj] npm module 
  * Atom: [zprint-atom][zprintatom] Atom plugin 
  * Use `planck` or `lumo` and configure zprint as a Clojure pretty-print filter. See [lein-zprint][leinzprint] for details.

__If you haven't used zprint before and are running on MacOS or Linux, 
check out the [native-image](doc/graalvm.md) approach.__  Now prebuilt
binaries are available for both Linux and MacOS!

Zprint includes support for Clojurescript, both browser based and self-hosted.

Zprint is designed to be a single pretty printer to use for code
and data structures.  It doesn't just re-indent code, it moves
it around from line to line trying to find a good visual representation
for your code.

#### What, really?  Another pretty printer?

Yes, I know we already have several.  See [here](#another-pretty-printer)
for an explanation.

One of the things I like the most about Clojure (and any Lisp) is that 
the logical structure of a function has a visual representation -- if
the function is pretty printed in a known way.  Zprint exists in part to take
any Clojure code, and pretty print it so that you can visually
grasp its underlying structure.

You can see the features available in zprint below, but the major
goals for zprint are:

* Reformat (pretty print) Clojure and Clojurescript code, completely 
  ignoring any existing white space within functions.  Fit the result 
  as strictly as possible within a specified margin, while using the 
  vertical space most efficiently.   

  For example, here is a before and after:

```clojure
(defn apply-style-x
  "Given an existing-map and a new-map, if the new-map specifies a 
  style, apply it if it exists.  Otherwise do nothing. Return 
  [updated-map new-doc-map error-string]"
  [doc-string doc-map existing-map new-map] (let [style-name (get new-map :style :not-specified)]
  (if (= style-name :not-specified) [existing-map doc-map nil] (let [style-map ( if (= style-name :default) 
  (get-default-options) (get-in existing-map [:style-map style-name]))] (cond (nil? style-name) 
  [exist ing-map doc-map "Can't specify a style of nil!"] style-map [(merge-deep existing-map style-map) 
  (when doc-map (diff-deep-doc (str doc-string " specified :style " style-name)
  doc-map existing-map style-map)) nil] :else [existing-map doc-map (str "Style '" style-name "' not found!")])))))

(zprint-fn apply-style-x 90)

(defn apply-style-x
  "Given an existing-map and a new-map, if the new-map specifies a
  style, apply it if it exists.  Otherwise do nothing. Return
  [updated-map new-doc-map error-string]"
  [doc-string doc-map existing-map new-map]
  (let [style-name (get new-map :style :not-specified)]
    (if (= style-name :not-specified)
      [existing-map doc-map nil]
      (let [style-map (if (= style-name :default)
                        (get-default-options)
                        (get-in existing-map [:style-map style-name]))]
        (cond (nil? style-name) [existing-map doc-map "Can't specify a style of nil!"]
              style-map [(merge-deep existing-map style-map)
                         (when doc-map
                           (diff-deep-doc (str doc-string " specified :style " style-name)
                                          doc-map
                                          existing-map
                                          style-map)) nil]
              :else [existing-map doc-map (str "Style '" style-name "' not found!")])))))
```
 
* Do a great job, "out of the box" on formatting code at the repl, code in
files, and EDN data structures.  Also be highly configurable, so you can
adapt zprint to print things the way you like them without having to dive
into the internals of zprint.

* Handle comments while printing Clojure source.  Some folks eschew
comments, which is fine (unless I have to maintain their code, of course).
For these folks, zprint's comment handling will provide no value.
For everyone else, it is nice that comments don't disappear
when pretty printing source code.

* Do all of this with excellent (and competitive) performance.

[leinzprint]: https://github.com/kkinnear/lein-zprint
[bootfmt]: https://github.com/pesterhazy/boot-fmt
[zprintclj]: https://github.com/roman01la/zprint-clj
[zprintatom]: https://github.com/roman01la/zprint-atom

## Usage

### Clojure 1.8:

__Leiningen ([via Clojars](http://clojars.org/zprint))__

[![Clojars Project](http://clojars.org/zprint/latest-version.svg)](http://clojars.org/zprint)

In addition to the zprint dependency, you also need to
include the library: 

```
[clojure-future-spec "1.9.0-alpha17"]
```

### Clojure 1.9, 1.10.0-beta3:

__Leiningen ([via Clojars](http://clojars.org/zprint))__

[![Clojars Project](http://clojars.org/zprint/latest-version.svg)](http://clojars.org/zprint)


### Clojurescript:

zprint uses `clojure.spec.alpha`, and has been tested in each of the
following environments:

  * Clojurescript 1.10.339
  * `lumo` 1.8.0-beta 
  * `planck` 2.8.1

It requires `tools.reader` at least 1.0.5, which all of the environments
above contain.

## Features

In addition to meeting the goals listed above, zprint has the 
following specific features:

* Runs fast enough to be used as a filter while in an editor
* Prints function definitions at the Clojure repl (including clojure.core functions)
* Prints s-expressions (EDN structures) at the repl
* Processes Clojure source files through lein-zprint
* Supports Clojure and Clojurescript
* Competitive performance
* Highly configurable, with an intuitive function classification scheme
* Respects the right margin specification
* Handles comments, will word wrap long ones
* Optionally will indent right hand element of a pair (see below)
* Maximize screen utilization when formatting code
* Sort map keys alphabetically by default
* Order map keys as desired
* Color specified map keys as desired or based on depth (depth is EXPERIMENTAL)
* Constant pairing (for keyword argument functions)
* Does a great job printing spec files
* Justify paired output (maps, binding forms, cond clauses, etc.) if desired
* Syntax coloring at the terminal
* Preserve most hand-formatting of selected vectors (for hiccup or rum HTML)

All of this is just so many words, of course.  Give zprint a try on
your code or data structures, and see what you think!

### API

The API for zprint is small.  A simple example:

```clojure
(require '[zprint.core :as zp])

(zp/zprint {:a "this is a pretty long value" 
 :b "a shorter value" :c '(a pretty long list of symbols)})

{:a "this is a pretty long value",
 :b "a shorter value",
 :c (a pretty long list of symbols)}
```

The basic API (except for the `-fn` variants) is supported
in both Clojure and Clojurescript:

```clojure
;; The basic call uses defaults, prints to stdout
(zprint x)

;; All zprint- functions also allow the following arguments:

(zprint x <width>)
(zprint x <width> <options>)
(zprint x <options>)

;; Format a function to stdout (accepts arguments as above)
(zprint-fn <fn-name>)        ; Clojure only

;; Output to a string instead of stdout
(zprint-str x)
(zprint-fn-str <fn-name>)    ; Clojure only

;; Colorize output for an ANSI terminal
;;
;;   None of the syntax coloring in this readme is from zprint, it is 
;;   all due to the github flavored markdown.
;;
(czprint x)
(czprint-fn <fn-name>)       ; Clojure only
(czprint-str x)
(czprint-fn-str <fn-name>)   ; Clojure only
```

If `<width>` is an integer, it is assumed to be a the width.  If it
is a map, it is assumed to be an options map.  You can have both,
either, or neither in any zprint or czprint call.

In addition to the above API, you can access zprint's file processing
capabilities (as does lein-zprint), by calling:

```clojure
(zprint-file infile file-name outfile options)
```
or format strings containing multiple "top level" forms by calling:

```clojure
(zprint-file-str file-str zprint-specifier new-options doc-str)
```
Both of these functions support the `;!zprint` 
[file comment API](doc/bang.md), which supports changes to the
formatting to be stored in a source file as specially formatted
comments.  See [here](doc/bang.md) for full documentation on
this capability.

__NOTE: The only supported API is what is documented in this readme!__

If you need to refresh your memory for the API while at the repl, try:

```clojure
(zprint nil :help)
```

Note that zprint completely ignores all whitespace and line breaks
in the function definition -- the formatting above is entirely
independent of the source of the function.  When using `lein-zprint`
to format source files, whitespace in the file between function definitions
is preserved.

Zprint has two fundemental regimes -- formatting s-expressions, or parsing
a string and formatting the results of the parsing.  When the `-fn` versions
of the API are used, zprint acquires the source of the function, parses it,
and formats the result at the repl.  

### Support

If you have a problem, file an issue with an example of what doesn't
work, and please also include the output from this call:

```clojure
(require '[zprint.core :as zp])

(zp/zprint nil :support)
```

This will assist me a great deal in reproducing and working on the issue.  Thanks!

### Acknowledgements

At the core of `zprint` is the `rewrite-clj` library by Yannick
Scherer, which will parse Clojure source into a zipper.  This is a
great library!  I would not have attempted `zprint` if `rewrite-clj`
didn't exist to build upon.  The Clojurescript port relies on Magnus
Rundberget's port of `rewrite-clj` to Clojurescript, `rewrite-cljs`.
It too worked with no issues when porting to Clojurescript!

### Another pretty printer

Aren't there enough pretty printers already?  What about:

* [clojure.pprint](https://clojure.github.io/clojure/clojure.pprint-api.html) which 
is built into Clojure, and does both s-expressions as well as code.
This features a port of the redoubtable Common Lisp formatter.

* [fipp](https://github.com/brandonbloom/fipp) "Fast Idiomatic Pretty Printer" and 
[puget](https://github.com/greglook/puget), a useful
library that adds significant capability to fipp.
Fipp features a very cool formatting engine, and puget adds some 
great features on top of fipp (in particular color and sorted keys
in maps).

* [cljfmt](https://github.com/weavejester/cljfmt) Which will pretty print your source files for you.
Cljfmt is truly beautiful code, crazy short and very neat inside.

These are all great packages, they have been around for a while,
and are quite useful.  Moreover, they each have some really high-tech
elements as I mentioned above.

I've been using these packages for years, and have even hacked both
clojure.pprint and fipp/puget to print things in a way a bit more
to my liking.  That said, there were a number of things that I
wanted a pretty printing package to do that none of these presently
do or that I could modify them to do in a straightforward
manner.

# Configuration

## Quick Start

The basic API is:
```clojure
(zprint x <width> <options>)
;or
(zprint x <options>)
```
If the third parameter is a number, it is used as the width of the
output.  Default is 80.  Zprint works hard to fit things into the
width, though strings will cause it to fail, as will very small widths.

`<options>` is a map of options.

Zprint prints out code and s-expressions the
way that I think it looks "best", which is very much like how most
people write Clojure code.  It does many specific things not covered
by the "community" coding standards.  In addition, it does some
things slightly differently than the community standards.  If what
you see as the default formatting doesn't please you, you could try
specifying the `:style` as `:community`, for example:

```clojure
; the default way:

(czprint-fn defn)

; the community way:

(czprint-fn defn {:style :community})
```
If this is more pleasing, you might read below as to how you could
configure this as your personal default.  You can, of course, also
configure loads specific parameters to tune the formatting
to your particular taste.  You can also
direct zprint to format any function (including functions you have
defined) in a wide variety of ways using several different paths to
get zprint to understand your desired configuration.

## Introduction to Configuration

Part of the reason that zprint exists is because I find that the visual
structure of pretty printed Clojure code tells me a lot about the semantics
of the code.  I also have a few areas where my preferred formatting differs
from the the current community standards.  By default zprint will format
Clojure code the way I think it makes the most sense, but it is very easy for
you to configure it to output code and data in a way more to your liking.
You don't need to be an expert in pretty printer engines to figure out
how to alter its configuration.

Since I created zprint to be easily configured, there are *lots* of
configuration options as well as several different ways to configure zprint.

You mostly don't have to care about any of this unless you want to change
the way that zprint outputs your code or data.   If you do, read on...

* [Overview](#overview)  
* [How to Configure zprint](#how-to-configure-zprint)  
* [Configuration Interface](#configuration-interface)  
  * [ Option Validation](#option-validation)  
  * [ What is Configurable](#what-is-configurable)  
    * [  Generalized Capabilities](#generalized-capabilities)  
    * [  Syntax Coloring](#syntax-coloring)  
    * [  Function Classification for Pretty Printing](#function-classification-for-pretty-printing)  
      * [   Changing or Adding Function Classifications](#changing-or-adding-function-classifications)  
      * [   A note about two-up printing](#a-note-about-two-up-printing)  
      * [   A note on justifying two-up printing](#a-note-on-justifying-two-up-printing)  
  * [ Widely Used Configuration Parameters](#widely-used-configuration-parameters)  
* [Configurable Elements](#configurable-elements)
  * [:agent](#agent-atom-delay-fn-future-promise)
  * [:array](#array)
  * [:atom](#agent-atom-delay-fn-future-promise)
  * [:binding](#binding)
  * [:comment](#comment)
  * [:delay](#agent-atom-delay-fn-future-promise)
  * [:extend](#extend)
  * [:fn](#agent-atom-delay-fn-future-promise)
  * [:future](#agent-atom-delay-fn-future-promise)
  * [:list](#list)
  * [:map](#map)
  * [:object](#object)
  * [:pair](#pair)
  * [:pair-fn](#pair-fn)
  * [:promise](#agent-atom-delay-fn-future-promise)
  * [:reader-cond](#reader-cond)
  * [:record](#record)
  * [:set](#set)
  * [:spec](#spec)
  * [:style](#style-and-style-map)
  * [:style-map](#style-and-style-map)
  * [:tab](#tab)
  * [:vector](#vector)

## Overview

The formatting done by zprint is driven off of an options map.
Zprint is built with an internal, default, options map.  This
internal options map is updated at the time that zprint is first
called by examining the .zprintrc file.  You can update this internal
options map at any time by calling `set-options!` with a map
containing the parts of the options map you with to alter.  You can
specify an options map on any individual zprint or czprint call,
and it will only be used for the duration of that call.

When altering zprint's configuration by using a .zprintrc file,
calling `set-options!`, or specifying an options map on an individual
call, the things that you specify in the options map replace the
current values in the internal options map.  Only the specific
values you specify are changed -- you don't have to specify an
entire sub-map when configuring zprint.

You can always see the current internal configuration of zprint by
typing `(zprint nil :explain)` or `(czprint nil :explain)`.  In
addition, the `:explain` output will also show you how each element
in the internal options map received its current value.  Given the
number of ways that zprint can be configured, `(zprint nil :explain)`
can be very useful to sort out how a particular configuration element
was configured with its current value.

The options map has a few top level configuration options, but
much of the configuration is grouped into sub-maps.  The top level
of the options map looks like this:

```clojure

{:agent {...},
 :array {...},
 :atom {...},
 :binding {...},
 :color-map {...},
 :comment {...},
 :delay {...},
 :extend {...},
 :fn-map {...},
 :fn-obj {...},
 :future {...},
 :list {...},
 :map {...},
 :max-depth 1000,
 :max-length 1000,
 :object {...},
 :pair {...},
 :pair-fn {...},
 :parse-string? false,
 :promise {...},
 :reader-cond {...},
 :record {...},
 :set {...},
 :style nil,
 :style-map {...},
 :tab {...},
 :uneval {...},
 :vector {...},
 :width 80,
 :zipper? false}
```

## How to Configure zprint 

When zprint is called for the first time it will configure itself
from all of the information that it has available at that time.
It will examine the following information in order to configure
itself:

* The file `$HOME/.zprintrc` for an options map in EDN format
* The file `.zprintrc` in the current working directory for an options 
  map in EDN format if the file `$HOME/.zprintrc` 
  has `{:cwd-zprintrc? true}` in its options map.
* __DEPRECATED:__ Environment variables for individual option map values
* __DEPRECATED:__ Java properties for individual option map values

You can invoke the function `(configure-all!)` at any time to
cause zprint to re-examine the above information.  It will delete
any current configuration and rebuild it from the information
available at that time.

If you __do not__ want to have zprint configured with the above
external information, your first use of the zprint library should be
the call:

```clojure
(set-options! {:configured? true})
```

This will cause zprint to use the default options map regardless of
what appears in any of the external configuration areas.

You can add configuration information by:

* Calling `set-options!` with an options map, which is saved in the internal 
options map across calls
* Specifing an options map on any call to zprint, which only affects that call
to `zprint` or `czprint`

## Configuration Interface

### .zprintrc

The `.zprintrc` file contain a sparse options map in EDN format (see below).
That is to say, you only specify the elements that you wish to alter in
a `.zprintrc` file.  Thus, to change the indent for a map to be 0, you
would have a `.zprintrc` file as follows:

```clojure
{:map {:indent 0}}
```

There are two possible `.zprintrc` files:

  * `$HOME/.zprintrc` which is always read
  * `.zprintrc` in the current working directory, which is only read
     if `$HOME/.zprintrc` has `{:cwd-zprintrc? true}` in its options map

Note that these files are only read and converted when zprint initially
configures itself, which is at the first use of a zprint or czprint
function.  You can invoke `configure-all!` at any later time which
will cause all of the external forms of configuration (e.g. .zprintrc,
environment variables, and Java system properties) to be read
and converted again.

#### Environment variables: __DEPRECATED__

You can specify an individual configuration element by specifying the
key as the environment variable name and the value as the value of the
environment variable.  For example, to specify the indent for a map
to be 0, you would say

`export zprint__map__indent=0`

which yields this change to the options map:

`{:map {:indent 0}}`

where a double underline `__` translates to a level in the map, and
a single underline `_` translates into a dash.  Thus

`export zprint__pair_fn__hang?=true`

will yield

`{:pair-fn {:hang? true}}`

Note that the strings `true` and `false` will be converted into their
boolean equivalents.

You can add (or change) functions in the :fn-map with environment variables.
Special processing is invoked when something inside the :fn-map is specified,
causing the final token of the environment variable to become a string and
the value of the environment variable is coerced to a keyword.  Thus, to
add the function `new-fn` as an `:arg1` function, use

`export zprint__fn_map__new_fn=arg1`

which will merge this map

`{:fn-map {"new-fn" :arg1}}`

into the existing `:fn-map`.
Alas, there is no way to use environment variables to affect a function
with an underscore in its function name.

The values of the environment variable are converted into EDN data.
Numbers become actual numbers, data structures become data structures, and
everything else becomes a string.  There are not a lot of data structures
in the options map, but the `:map :key-order` key takes a vector of
keys to place first when formatting a map.  You would specify the
following `:key-order`

`{:map {:key-order [:name "important"]}}`

like this

`export zprint__map__key_order='[:name "important"]'`

Note that these values are only read and converted when zprint initially
configures itself, which is at the first use of a zprint or czprint
function.  You can invoke `(configure-all!)` at any later time which
will cause all of the external forms of configuration (e.g. .zprintrc,
environment variables, and Java system properties) to be read and
converted again.  

#### Java Properties: __DEPRECATED__

You can also specify individual configuration elements using Java properties.
In Java properties, a dot is turned into a hypen (dash), and an underscore
represents a level in the map.
Thus,

`System.setProperty("zprint_map_indent" "0")`

will create the map

`{:map {:indent 0}}`
 
to be merged into the internal options map.
Similar to environment variables, numeric strings become numbers, strings
with the value `true` and `false` are turned into booleans, and 
you can specify a structure, so to set the `:key-order` 
to `["first" :second]` you would specify the Java property as:

`System.setProperty("zprint_map_key.order" "[\"first\" :second]")`

or in Clojure

`(System/setProperty "zprint_map_key.order" "[\"first\" :second]")`

Note that these values are only read and converted when zprint initially
configures itself, which is at the first use of a zprint or czprint
function.  You can invoke `(configure-all!)` at any later time which
will cause all of the external forms of configuration (e.g. .zprintrc,
environment variables, and Java system properties) to be read and
converted again.

#### set-options!

You call set-options! with an EDN map of the specific key-value pairs
that you want changed from the current values.  This is useful both
when using zprint at the REPL, as well as when you are using to output
information from a program that wants to configure zprint
in some particular way.  For example:

```clojure
(require '[zprint.core :as zp])

(zp/set-options! {:map {:indent 0}})
```

#### Options map on an individual call

You simply specify the options map on the call itself:

```clojure
(require '[zprint.core :as zp])

(def my-map {:stuff "a fairly long value" 
   :bother 
   "A much longer value, which makes this certainly not fit in 80 columns"})

(zp/zprint my-map {:map {:indent 0}})

{:bother
 "A much longer value, which makes this certainly not fit in 80 columns",
 :stuff "a fairly long value"}

```

### Option Validation

All changes to the options map are validated to some degree for
correctness.  When the change to the internal options map is itself
a map, when using the `.zprintrc` file, calling `(set-options! ...)`,
or specifying an options map on an individual call, every key in
the options map is validated, and some level of at least type 
validation on the values is also performed.  Thus:

```clojure
(czprint nil {:map {:hang false}})

Exception Option errors in this call: Value does not match schema: {:map {:hang disallowed-key}}  
zprint.core/zprint* (core.clj:241)

```

This call will fail validation because there is no `:hang` key in `:map`.  The
"?" is missing from `:hang?`.  My initial motivation for adding options
map validation was forgetting to type the "?" at the end of boolean valued
options and wondering why nothing changed.

There is no key validation performed for environment variables or
Java system properties -- invalid keys are simply ignored.  However, 
value type validation is performed.  Thus:

```clojure
(System/setProperty "zprint_map_indent" "true")

(configure-all!)

"In System property: Value does not match schema: {:map {:indent (not (instance? java.lang.Number true))}}"
```
whichs says that "true" is not an instance of java.lang.Number, and tells
you that any value for `{:map {:indent <value>}}` needs to be a number.

All option validation errors must be fixed, or zprint will not operate.

## What is Configurable

The following categores of information are configurable:

* generalized capabilities
* syntax coloring
* function classification for pretty printing
* specific option values for maps, lists, vectors, pairs, bindings, arrays, etc.

### Generalized capabilites

#### :width

An integer width into which the formatted output should fit.  zprint
will work very hard to fit the formatted output into this width,
though there are limits to its effort.  For instance, it will not
reduce the minimum indents in order to satisfy a particular width
requirement.  This will be most obvious when widths are small, in
the 15 to 30 range.  Normally you might never notice this with the
default 80 column width.

Long strings will also cause zprint to exceed the requested width.
Comments will be wrapped by default so as not to exceed the width,
though you can disable comment wrapping.  See the `:comment` section.

The `:width` specification in the options map is most useful for
specifying the default width, as you can also give a width specification
as the second argument of any of the zprint functions.

#### :parse-string?

By default, zprint expects an s-expression and will format it.  If you
specify `:parse-string? true` in an options map, then the first argument
must be a string, and zprint will parse the string and format the output.
It expects a single expression in the string, and will trim spaces from
before and after that single expression.

#### :parse-string-all?

By default, zprint expects an s-expression and will format it.  If
you specify `:parse-string-all? true` in an options map, then the
first argument must be a string, and zprint will parse the string
and format the output.  It will accept multiple expressions in the
string, and will parse and format each expression independently.
It will drop all whitespace between the expressions (and before the
first expression), and will by default separate each expression
with a new-line, since the expressions are formatted beginning in
column 1.

```clojure
(czprint "(def a :b) (def c :d)" 40 {:parse-string-all? true})
(def a :b)
(def c :d)
```

You can separate the expressions with addtional newlines (or pretty
much anything that ends with a new-line) by including an options
map with `:parse {:interpose string}` in it.  The string must end
with a new-line, or the resulting formatting will not be correct.

```clojure
(czprint "(def a :b) (def c :d)" 40 {:parse-string-all? true :parse {:interpose "\n\n"}})
(def a :b)

(def c :d)
```

#### :parallel?

As of 0.3.0, on Clojure zprint will use mutiple threads in several
ways, including `pmap`.   By default, if used as a library in a program,
it will not use any parallel features because if it does, your program
will not exit unless you call `(shutdown-agents)`.  When zprint is
running at the repl, it __will__ enable parallel features as this
doesn't turn into a problem when exiting the repl.

If you want it to run more quickly when embedded in a program,
certainly you should set :parallel? to true -- but don't forget to
call `(shutdown-agents)` at the end of your program or your program
won't exit!

#### :additional-libraries?

If the first call you make to anything to do with zprint is:

```clojure
(set-options! {:additional-libraries? false})
```

then zprint will not even try to load the libraries: cprop and table.
And you will not be able to use :auto-width? or configure with environment
variables or Java properties.

### Syntax coloring

Zprint will colorize the output when the czprint and czprint-fn calls
are used.  It is limited to the colors available on an ANSI terminal.

The key :color-map contains by default:

```clojure
 :color-map {:brace :red,
 	    :bracket :purple,
	    :comment :green,
	    :deref :red,
	    :fn :blue,
	    :hash-brace :red,
	    :hash-paren :green,
	    :keyword :magenta,
	    :nil :yellow,
	    :none :black,
	    :number :purple,
	    :paren :green,
	    :syntax-quote-paren :red
	    :quote :red,
	    :string :red,
	    :uneval :magenta,
	    :user-fn :black},
```
You can change any of these to any other available value.  The
available values are:

* `:red`
* `:blue`
* `:green`
* `:magenta` (or `:purple`)
* `:yellow`
* `:cyan`
* `:black`

There is also a different color map for unevaluated items,
i.e. those prefaced with #_ and ignored by the Clojure reader.
This is the default :uneval color map:

```clojure
:uneval {:color-map {:brace :yellow,
		    :bracket :yellow,
		    :comment :green, 
		    :deref :yellow,
		    :fn :cyan,
		    :hash-brace :yellow,
		    :hash-paren :yellow,
		    :keyword :yellow,
		    :nil :yellow,
		    :none :yellow,
		    :number :yellow,
		    :paren :yellow,
		    :syntax-quote-paren :yellow
		    :quote :yellow,
		    :string :yellow,
		    :uneval :magenta,
		    :user-fn :cyan}},
```

You can also change these to any of the colors specified above.

Note that in this readme, the syntax coloring of Clojure code is 
that provided by the github flavored markdown, and not zprint.

### Function Classification for Pretty Printing

While most functions will pretty print without special processing,
some functions are more clearly comprehended when processed specially for
pretty printing.  Generally, if a function call fits on the current
line, none of these classifications matter.  These only come into play
when the function call doesn't fit on the current line.  The following
examples are shown with an implied width of well less than 80 columns
in order to demonstrate the function style in a concise manner.

Note that the 
[community style guide](https://github.com/bbatsov/clojure-style-guide)
specifies different indentation amounts for functions (forms) that have
"body" parameters, and functions that just have arguments.  Personally,
I've never really distinguished between these different types of functions
(which is why the default indent for both is 2).  But I've created
classifications so that you can class some functions as having body
arguments instead of just plain arguments, so that if you specify a
different indent for arg-type functions than body-type functions, the
right things will happen.

A function that is not classified explicitly by appearing in the
`:fn-map` is considered an "arg" function as opposed to "body" function,
and the indent for its arguments is controlled by `:list {:indent-arg n}` 
if it appears, and `:list {:indent n}` if it does not. 

How does zprint classify functions that are called with a namespace
on the front?  First, it looks up the string in the fn-map, and if
it finds it, then it uses that.  If it doesn't find it, and the
function string has a "/" in it, it then looks up string to the right
of the "/".


The available classifications are:

#### :arg1

Print the first argument on the same line as the function, if possible.  
Later arguments are indented the amount specified by `:list {:indent-arg n}`, 
or `:list {:indent n}` if `:indent-arg` is not specified.

```clojure
 (apply str
   "prepend this one"
   (generate-strings from arguments))
```

#### :arg1-body

Print the first argument on the same line as the function, if possible.  
Later arguments are indented the amount specified by `:list {:indent n}`.

```clojure
 (if (= a 1)
   (map inc coll)
   (map dec coll))
```
#### :arg1-pair

The function has an important first argument, then the rest of the 
arguments are paired up. Leftmost part of the pair is indented
by `:list {:indent-arg n}` if it is specified, and `:list {:indent n}` 
if it is not.

```clojure
 (assoc my-map
   :key1 :val1
   :key2 :val2)
```
#### :arg1-pair-body

The function has an important first argument, then the rest of the 
arguments are paired up.  The leftmost part of the pair is indented
by the amount specified by `:list {:indent n}`.

```clojure
 (case fn-style
   :arg1 nil
   :arg1-pair :pair
   :arg1-extend :extend
   :arg2 :arg1
   :arg2-pair :arg1-pair
   fn-style)
```

#### :arg1-force-nl

This is like `:arg1`, but since it appears in `:fn-force-nl`, it will
never print on one line even if it would otherwise fit.

#### :arg1-mixin

Print Rum `defc`, `defcc`, and `defcs` macros in a standard
way.  Puts the mixins under the first line, and above the
argument vector.  Does not require `<`, will operate properly
with any element in that position. Allows but does not require
a docstring.

 ```clojure
(rum/defcs component
  "This is a docstring for the component."
  < rum/static
    rum/reactive
    (rum/local 0 ::count)
    (rum/local "" ::text)
  [state label]
  (let [count-atom (::count state)
        text-atom (::text state)]
    [:div]))
```

#### :arg2
 
Print the first argument on the same line as the function name if it will
fit on the same line. If it does, print the second argument
on the same line as the first argument if it fits. Indentation of
later arguments is controlled by `:list {:indent n}`

```clojure
  (as-> initial-value tag
    (process stuff tag bother)
    (more-process tag foo bar))
```

#### :arg2-pair

Just like :arg2, but prints the third through last arguments as pairs.
Indentation of the leftmost elements of the pairs is controlled by
`:list {:indent n}`.  If any of the rightmost elements end up not fitting
or not hanging well, the flow indent is controlled by `:pair {:indent n}`.

```clojure
  (condp = stuff
    :bother "bother"
    :foo "foo"
    :bar "bar"
    "baz")
```
#### :arg2-fn

Just like :arg2, but prints the third through last arguments as functions.

```clojure
  (proxy [Classname] []
    (stuff [] bother)
    (foo [bar] baz))
```

#### :binding

The function has a binding clause as its first argument.  
Print the binding clause two-up (as pairs)  The indent for any wrapped
binding element is :binding `{:indent n}`, the indent for the functions
executed after the binding is `:list {:indent n}`.

```clojure
 (let [first val1 
       second
         (calculate second using a lot of arguments)
       c d]
   (+ a c))
```

#### :pair-fn

The function has a series of clauses which are paired.  Whether or
not the paired clauses use hang or flow with respect to the function
name is controlled by `:pair-fn {:hang? boolen}` and the indent of
the leftmost element is controlled by `:pair-fn {:indent n}`.

The actual formatting of the pairs themselves is controlled by
`:pair`.  The controls for `:pair-fn` are govern how to handle the
block of pairs -- whether or not they should be in a hang with
respect to the function name.  The controls for how the elements
within the pairs are printed are governed by `:pair`. For instance,
the indent of any of the rightmost elements of the pair if they
don't fit on the same line or don't hang well is `:pair {:indent
n}`.

```clojure
 (cond
   (and (= a 1) (> b 3)) (vector c d e)
   (= d 4) (inc a))
```

#### :hang

The function has a series of arguments where it would be nice
to put the first on the same line as the function and then
indent the rest to that level.  This would usually always be nice,
but zprint tries extra hard for these.  The indent when the arguments
don't hang well is `:list {:indent n}`.

```clojure
 (and (= i 1)
      (> (inc j) (stuff k)))
```

#### :extend

The s-expression has a series of symbols with one or more forms 
following each.  The level of indent is configurable by `:extend {:indent n}`.

```clojure
  (reify
    stuff
      (bother [] (println))
    morestuff
      (really [] (print x))
      (sure [] (print y))
      (more-even [] (print z)))
```

#### :arg1-extend

For the several functions which have an single argument
prior to the :extend syntax.  They must have one argument,
and if the second argument is a vector, it is also handled
separately from the :extend syntax.  The level of indent is controlled
by `:extend {:indent n}`

```clojure
  (extend-protocol ZprintProtocol
    ZprintType
      (more-stuff [x] (str x))
      (more-bother [y] (list y))
      (more-foo [z] (nil? z))))

  (deftype ZprintType
    [a b c]
    ZprintProtocol
      (stuff [this x y] a)
      (bother [this] b)
      (bother [this x] (list x c))
      (bother [this x y] (list x y a b)))
```

#### :arg1->

Print the first argument on the same line as
the function, if possible.  Later arguments go
indented and `:arg1` and `:arg-1-pair` top level fns
are become `:none` and `:pair`, respectively.

Currently `->` is `:narg1-body`, however, and there
are no `:arg1->` functions.

```clojure
  (-> opts
    (assoc
      :stuff (list "and" "bother"))
      (dissoc :things))
```

#### :noarg1-body

Print the function in whatever way is possible without
special handling.  However, top level fns become
different based on the lack of their first argument.
Thus, `:arg1` becomes `:none`, `:arg1-pair` becomes `:pair`,
etc.

```clojure
  (-> opts
      (assoc
        :stuff (list "and" "bother"))
      (dissoc :things))
```

#### :force-nl and :force-nl-body

Tag a function which should not format with all of its arguments
on the same line even if they fit.  Note that this function
type has to show up in the set that is the value of :fn-force-nl
to have any effect.

```clojure
  (->> opts
       foo
       bar
       baz) 
```

#### :fn

Print the first argument on the same line as the `(fn ...)` if it will
fit on the same line. If it does, and the second argument is a vector, 
print it on the same line as the first argument if it fits.  Indentation
is controlled by `:list {:indent n}`.

```clojure
  (fn [a b c]
    (let [d c]
      (inc d)))

  (fn myfunc [a b c]
    (let [d c]
      (inc d)))
```

#### :flow and :flow-body

Don't hang under any circumstances. `:flow` assumes that the function
has arguments, `:flow-body` assumes that the arguments are body elements.
The only difference is when there are different indents for arguments
and body elements.  Note that both `:flow` and `:flow-body` appear in
the set `:fn-force-nl`, so that they will also never print one one line.

```clojure
  (foo
    (bar a b c)
    (baz d e f))
```

#### :gt2-force-nl and :gt3-force-nl

These two function styles exist to be assigned to functions that should
be printed on one line if they fit on one line -- unless they have more
than 2 or 3 arguments.  These exist for functions that would otherwise
not fit into any function style.  These function styles appear by default
in the two sets `:fn-gt2-force-nl` and `:fn-gt3-force-nl` respectively.

If function `foo` has a function style of `:gt2-force-nl`, then

```clojure
  (foo (bar a b c) (baz d e f))

  (foo (bar a b c)
       (baz d e f)
       (stuff x y z))
```

#### :none

This is for things like special forms that need to be in this
map to show up as functions for syntax coloring, but don't actually 
trigger the function recognition logic to represent them as such.
Also, `:none` is used to remove the default classification for functions
by specifying it in an option map.  The indent for arguments that
don't hang or fit on the same line is `:list {:indent-arg n}`
if it is specified, and `:list {:indent n}` if it is not.

#### :none-body

Like none, but the indent for arguments that don't hang or fit
on the same is always `:list {:indent n}`.

### Changing or Adding Function Classifications

You can change the classification of an existing function or add
a new one by changing the map at key :fn-map.  A fragment of the existing
map is shown below:

```clojure
:fn-map {"!=" :hang,
          "->" :noarg1-body,
          "->>" :force-nl-body,
          "=" :hang,
          "and" :hang,
          "apply" :arg1,
          "assoc" :arg1-pair,
          "binding" :binding,
          "case" :arg1-pair,
          "catch" :none,
          "cond" :pair,
	  ...}
```

Note that the function names are strings.  You can add any function
you wish to the :fn-map, and it will be interpreted as described above.

#### Altering the formatting inside of certain functions

You can associate an options map with a function classification, and
that options map will be used when formatting inside of that function.
This association is made by using a vector for the function classification,
with the classification first and the options map second.  For example:

```clojure
:fn-map {"!=" :hang,
          "->" :noarg1-body,
          "->>" :force-nl-body,
          "=" :hang,
          "and" :hang,
          "apply" :arg1,
          "assoc" :arg1-pair,
          "binding" :binding,
          "case" :arg1-pair,
          "catch" :none,
          "cond" :pair,
	  ...
	  "defproject" [:arg2-pair {:vector {:wrap? false}}]
	  "defprotocol" :arg1-force-nl
	  ...}
```

This will cause vectors inside of `defproject` to not wrap the elements
in the vector, instead of this (which is what you would get with
just `:arg2-pair`):

```clojure
(defproject name version
  :test :this
  :stuff [:aaaaa :bbbbbbb :ccccccccc :ddddddd
          :eeeeeee])
```

you will get this by default:

```clojure
(defproject name version
  :test :this
  :stuff [:aaaaa
          :bbbbbbb
          :ccccccccc
          :ddddddd
          :eeeeeee])
```

### Configuring the `:fn-map`

Often the :fn-map is configured by changing the `.zprintrc` file so 
that functions are formattted the way you prefer.  You can change the
default formatting of functions as well as configure formatting for
your own functions.  To remove formating for a function which has
previously been configured, set the formatting to `:none`.

### Controlling Single and Multi-line Output

By default, zprint will print any function call (or any structure)
on one line if it will fit on one line.  However, some functions
are generally printed on multiple lines even if they would fit on
one line, and zprint will do this for some functions by default.

There are three sets which control which function styles will never
print on one line even if they would otherwise fit:

#### :fn-force-nl <text style="color:#A4A4A4;"><small>#{:force-nl :force-nl-body :noarg1 :noarg1-body :arg1-force-nl :flow :flow-body}</small></text>

This is a set that specifies which function types will always format with
a hang or a flow, and never be printed on the same line even if they fit.

#### :fn-gt2-force-nl <text style="color:#A4A4A4;"><small>#{:gt2-force-nl :binding}</small></text>

This is a set that specifies which function types will always format with
a hang or a flow, and never be printed on the same line if they have more
than 2 arguments.

#### :fn-gt3-force-nl <text style="color:#A4A4A4;"><small>#{:gt3-force-nl :arg1-pair :arg1-pair-body}</small></text>

This is a set that specifies which function types will always format with
a hang or a flow, and never be printed on the same line if they have more
than 3 arguments.

#### Altering the configuration of sets in the options map

You can add one or more function styles to a set by simply placing a
set containing only the additional function styles as the value of
the appropriate key.  Thus:

```clojure
  (set-options! {:fn-gt2-force-nl #{:arg1-pair}})
```
yields a value for the key `:fn-gt2-force-nl` of
`#{:gt2-force-nl :binding :arg1-pair}`.  It does not replace the
set at that key with the new set, but includes its elements into
the set.  Thus you don't have to specify the entire set to alter its
value by adding something to it.

How, then, do you remove elements from one of the sets in the options
map?  You specify a set of elements to remove, rooted at the `:remove`
key.  Thus:

```clojure
  (set-options! {:remove {:fn-gt3-force-nl #{:arg1-pair}}})
```

will yield a value for `:fn-gt3-force-nl` of `#{:gt3-force-nl :arg1-pair-body}`.

If both additions and removals are specified in the same options map, the
removals are performed first and the additions second.


### Detailed Configuration for maps, lists, vectors, etc.

Internally, there are several formatting capabilities that are
used in slightly different ways to format a wide variety of syntactic
elements.  These basic capabilities are parameterized, and the
parameters are varied based on the syntactic element.  Before going
into detail about the individual elements, let's look at the overview
of the capabilities:

* two-up (pairs (or more) of things that go together)
  * [:binding](#binding)
  * [:map](#map)
  * [:pair](#pair)
  * [:pair-fn](#pair-fn)
  * [:extend](#extend)
  * [:reader-cond](#reader-cond)
* vector (wrap things out to the margin)
  * [:vector](#vector)
  * [:set](#set)
  * [:array](#array)
* list (things that might be code)
  * [:list](#list)
* objects with values (format nicely or print as object)
  * [:agent](#agent-atom-delay-fn-future-promise)
  * [:atom](#agent-atom-delay-fn-future-promise)
  * [:delay](#agent-atom-delay-fn-future-promise)
  * [:fn](#agent-atom-delay-fn-future-promise)
  * [:future](#agent-atom-delay-fn-future-promise)
  * [:promise](#agent-atom-delay-fn-future-promise)
  * [:object](#object)
* misc
  * [:comment](#comment)
  * [:record](#record)
  * [:spec](#spec)
  * [:style](#style-and-style-map)
  * [:style-map](#style-and-style-map)
  * [:tab](#tab)

#### A note about two-up printing

Part of the reason for zprint's existence revolves around the
current approach to indenting used for cond clauses, binding vectors,
and maps and other things with pairs (extend and reader conditionals).

Back in the day some of the key functions that include pairs, e.g.
cond and let, had their pairs nested in parentheses.  Clojure doesn't
follow this convention, which does create cleaner looking code in
the usual case, when the second part of the pair is short and fits
on the same line or when the second part of the pair can be represented
in a hang.  In those cases when the second part of the pair ends
up on the next line (as a flow), it can sometimes become a bit
tricky to separate the test and expr pair in a cond, or a destructured
binding-form from the init-expr, as they will start in the same
column.

While the cases where it is a bit confusing are rather rare, I
find them bothersome, so by default zprint will indent the
second part of these pairs by 2 columns (controlled by `:pair {:indent 2}`
for `cond` and `:binding {:indent 2}` for binding functions).

Maps also have pairs, and perhaps suffer from the potential
for confusion a bit more then binding-forms and cond functions.
By default then, the map indent for the an item that placed on the
next line (i.e., in a flow) is 2 (controlled by `:map {:indent 2}`).
The default is 2 for extend and reader-conditionals as well.

Is this perfect?  No, there are opportunities for confusion here
too, but it works considerably better for me, and it might for
you too. I find this particularly useful for :binding and :map
formatting.

Should you not like what this does to your code or your s-expressions,
the simple answer is to use {:style :community} as an options-map
when calling zprint (specify that in your .zprintrc file, perhaps).

You can change the indent from the default of 2 to 0 individually
in :binding, :map, or :pair if you want to tune it in more detail.

#### A note on justifying two-up printing

I have seen some code where people justify the second element of their
pairs to all line up in the same column.  I call this justifying for
lack of a better term.  Here is an example in code:

```clojure
; Regular formatting

(zprint-fn compare-ordered-keys {:pair {:justify? true}})

(defn compare-ordered-keys
  "Do a key comparison that places ordered keys first."
  [key-value zdotdotdot x y]
  (cond (and (key-value x) (key-value y)) (compare (key-value x) (key-value y))
        (key-value x) -1
        (key-value y) +1
        (= zdotdotdot x) +1
        (= zdotdotdot y) -1
        :else (compare-keys x y)))

; Justified formatting

(zprint-fn compare-ordered-keys {:pair {:justify? true}})

(defn compare-ordered-keys
  "Do a key comparison that places ordered keys first."
  [key-value zdotdotdot x y]
  (cond (and (key-value x) (key-value y)) (compare (key-value x) (key-value y))
        (key-value x)                     -1
        (key-value y)                     +1
        (= zdotdotdot x)                  +1
        (= zdotdotdot y)                  -1
        :else                             (compare-keys x y)))
```
Zprint will optionally justify `:map`, `:binding`, and `:pair` elements.
There are several detailed configuration parameters used to control the
justification.  Obviously this works best if the keys in a map are
all about the same length (and relatively short), and the test expressions
in a cond are about the same length, and the locals in a binding are
about the same length.

I don't personally find the justified approach my favorite in code,
though there are some functions where it looks good.  

Try:

```clojure
(czprint-fn resultset-seq {:style :justified})
```

and see what you think.  Looks great to me, but it just happens to
have nice locals.

For functions where this looks great, you can always turn it on
just for that function (if you are using lein-zprint), like so:

```
;!zprint {:format :next {:style :justified}}
```

As you might gather, there is a `:style :justified` which you can use
to turn this on for maps, pairs, and bindings.

I was surprised what justification could do for some maps, however.
You can see it for yourself if you enter:

```clojure
(czprint nil :explain-justified)
```

This prints out the regular :explain output for the current zprint options
map, but justified.  See what you think.

__NOTE:__ Justification involves extra processing, and because of the way
that zprint tries to do the best job possible, it can cause a bit of a
combinatorial explosion that can make formatting some functions and
structures take a very long time.  I have put scant effort into optimizing
this capability, as I have no idea how interesting it is to people in
general.  If you are using it and like it, and you have situations where
it seems to be particularly slow for you, please enter an issue to let
me know.

### Formatting Large or Deep Collections

Sometimes you end up with a collection which is very large or very
deep -- or both.  You want to get an overview of it, but don't
want to output the entire collection because it will take too much
space or too much time.  At one time, these were experimental
capabilities, but they are now fully supported.

There are two limits that can be helpful.

#### :max-length <text style="color:#A4A4A4;"><small>1000</small></text>

Will limit the length of a sequence on output -- more than this many
will yield a `...`.

```clojure

(czprint [1 2 3 4 5] {:max-length 3})

[1 2 3 ...]
```

That's nice, but sometimes you want to see different amounts of
a collection at different levels.  Perhaps you want to see all of
the keys in a map, but not much of the information lower down in
the values of the map.  

In this situation, the `:max-length` can be a vector, where the
value at each level is the max-length for that level in the collection.
The rightmost value in the vector is used for all of the levels below
the one specified.

So, `{:max-length [3 2 1 0]}` would output 3 things at the top level
of the collection, 2 for everything at the next level down, one for
every collection at the next level, and `##` for any collections
below that.  Since the rightmost value is used for any level beyond
that explicitly specified, `{:max-length n}` and `{:max-length [n]}` 
are equivalent.  Also `{:max-depth 3}` and `{:max-length [1000 1000 1000 0]}`
are also equivalent.

```clojure
(czprint [:a [:b [:c [:d [:e [:f]]]]]] {:max-length [1000 1000 1000 0]})

[:a [:b [:c ##]]]


(czprint [:a [:b [:c [:d [:e [:f]]]]]] {:max-depth 3})

[:a [:b [:c ##]]]
```


Here are some examples with the zprint
options map (where we aren't going to examine all of the keys, but
a few at the beginning):

```clojure
(czprint x {:max-length [10 0]})

{:additional-libraries? true,
 :agent ##,
 :array ##,
 :atom ##,
 :auto-width? false,
 :binding ##,
 :color-map ##,
 :color? true,
 :comment ##,
 :configured? true,
 ...}

(czprint x {:max-length [10 1 0]})

{:additional-libraries? true,
 :agent {:object? false},
 :array {:hex? false, ...},
 :atom {:object? false},
 :auto-width? false,
 :binding {:flow? false, ...},
 :color-map {:brace :red, ...},
 :color? true,
 :comment {:count? false, ...},
 :configured? true,
 ...}
```

If you have a complex structure, a little experimentation with
`:max-length` and a vector can often allow you to generate a useful
overview of the structure without much effort. 

While you might not think this would be useful for looking at code,
for code that has a very regular structure, it can be helpful.  For
instance, if you want an overview of a `deftype`, you could use
`{:max-length [100 2 10 0]}`, as below:

```clojure
(czprint-fn clojure.core.match/->PatternRow {:max-length [100 2 10 0]})

(deftype PatternRow [ps action ...]
  Object
    (equals [_ other] ...)
  IVecMod
    (drop-nth [_ n] ...)
    (prepend [_ x] ...)
    (swap [_ n] ...)
  clojure.lang.Associative
    (assoc [this k v] ...)
  clojure.lang.Indexed
    (nth [_ i] ...)
    (nth [_ i x] ...)
  clojure.lang.ISeq
    (first [_] ...)
    (next [_] ...)
    (more [_] ...)
    (seq [this] ...)
    (count [_] ...)
  clojure.lang.ILookup
    (valAt [this k] ...)
    (valAt [this k not-found] ...)
  clojure.lang.IFn
    (invoke [_ n] ...)
  clojure.lang.IPersistentCollection
    (cons [_ x] ...)
    (equiv [this other] ...))
```

#### :max-depth <text style="color:#A4A4A4;"><small>1000</small></text>

Will limit depth of a collection.  

```clojure
(czprint {:a {:b {:c :d}}} {:max-depth 1})

{:a {:b ##}}
```

## Widely Used Configuration Parameters

There are a several  configuration parameters that are meaningful
across a number of formatting types.  

### :indent

The value for indent is how far to indent the second through nth of 
something if it doesn't all fit on one line (and becomes a flow, see
immediately below).

### hang and flow

zprint uses two concepts: hang and flow, to describe how something is
to be printed.

This is a hang:

```clojure
(symbol "string"
        :keyword
        5
        {:map-key :value})
```

This is the same information in a flow:

```clojure
(symbol
  "string"
  :keyword
  5
  {:map-key :value})
```
zprint will try (by default) to use the *hang* approach when it
will use the same or fewer lines than a *flow*.  Unless the hang takes
too much vertical space (which makes things less clear, instead of more
clear).  There are several values which will tune the output for
hang and flow.

#### :hang?

If `:hang?` is true, zprint will attempt to hang if all of the elements in
the collection don't fit on one line. If it is false, it won't
even try.

#### :hang-avoid

If `:hang-avoid` is non-nil, then it is used to decide if the formatting
is close enough to the right margin to probably not be worth doing. This
is a performance optimization for functions that are very deeply nested
and take a considerable time to format.  For normal functions, this has
no effect, but for a few functions that take a long time to format, it
can cut that time by 30%.  If the value is non-nil, then avoid even
trying to do a hang if the number of top-level elements in the rest
of the collection is greater than the remaining columns times the
hang-avoid value.  The hang-avoid value defaults to 0.5, which changes
only a tiny amount of output visually, but provides useful performance
gains in functions which take a long time to format.  At present this
ony affects lists, but may be implemented for other collections in
the future.

#### :hang-expand

`:hang-expand` is one control used to decide whether or not to do a hang.  It
relates the number of lines in the hang to the number of elements
in the hang thus: `(/ (dec hang-lines) hang-element-count)`.  If every
element in the hang fits on one line, then this ratio will be < 1.
If every element in the hang takes two lines, then this ratio will
be close to 2.  If this ratio is > `:hang-expand`, then the hang
is rejected.  The idea is that hangs that run on and on down the
right side of the page are not ideal, even when they don't take
more lines than a flow.  Unless, in some cases, they are ok -- for
instance for maps.  The `:hang-expand` for :map is 1000.0, since
we expect maps to have large hangs that expand a lot.

#### :hang-diff

The value of `:hang-diff` (an integer) is related to the indent for
a hang and a flow.  Clearly, if the indent for a hang and a flow are
the same, you might as well do a hang, since a flow buys you nothing.
The difference in these indents `(- hang-indent flow-indent)` is compared
to the value of `:hang-diff`, and if this difference is <= then it 
skips the `:hang-expand` check.  `:hang-diff` is by default 1, since even if a
flow buys you one more space to the left, it often looks kind of odd.
You could set `:hang-diff` to 0 if you wanted to be more "strict", and
see if you like the results better.  Probably you won't want to deal
with this level of control.

#### :flow?

If `:flow?` is true, all of the elements of a collection will be forced
onto a new line, even if they would have fit on the same line originally. 
When a function has a function type of `:flow`, all of the arguments will
be flowed below the function, each taking its own line.  The `:flow?` options
configuration key does a similar thing for data structures (both within
code and just in data structures).  For example:

```clojure
(czprint {:a :b :c :d :e :f :g { :i {:j :k} :l :m}} {:map {:flow? false}})

{:a :b, :c :d, :e :f, :g {:i {:j :k}, :l :m}}

(czprint {:a :b :c :d :e :f :g { :i {:j :k} :l :m}} {:map {:flow? true}})

{:a
   :b,
 :c
   :d,
 :e
   :f,
 :g
   {:i
      {:j
         :k},
    :l
      :m}}
```

This looks a bit strange because the keys are very short, making the 
indentation of the second element in each pair odd.  If you do this, you
might want to reduce the indent, thus:

```clojure
(czprint {:a :b :c :d :e :f :g { :i {:j :k} :l :m}} {:map {:indent 0 :flow? true}})

{:a
 :b,
 :c
 :d,
 :e
 :f,
 :g
 {:i
  {:j
   :k},
  :l
  :m}}
```

The `:flow?` capability was added along with `:nl-separator?` to make
formatting `:extend` types work in an alternative way:

```clojure
(czprint-fn ->Typetest)

; Default output, :force-nl? is true

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq (hasheq [this] (list this))
  clojure.lang.Counted (count [_] cnt)
  clojure.lang.IMeta (meta [_] _meta))

(czprint-fn ->Typetest {:extend {:flow? true}})

; Add :flow? true, always keeps fn defns on separate line

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq
    (hasheq [this] (list this))
  clojure.lang.Counted
    (count [_] cnt)
  clojure.lang.IMeta
    (meta [_] _meta))

(czprint-fn ->Typetest {:extend {:flow? true :indent 0}})

; Reduce indent

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq
  (hasheq [this] (list this))
  clojure.lang.Counted
  (count [_] cnt)
  clojure.lang.IMeta
  (meta [_] _meta))

(czprint-fn ->Typetest {:extend {:flow? true :indent 0 :nl-separator? true}})

; Add :nl-separator? true for an altogether different (but commonly used) look

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq
  (hasheq [this] (list this))

  clojure.lang.Counted
  (count [_] cnt)

  clojure.lang.IMeta
  (meta [_] _meta))

```

#### :force-nl?

Very similar to `:flow?`, but operates on pairs, not individual elements
of a pair.  For example:

```clojure
(czprint {:a :b :c :d :e :f :g { :i {:j :k} :l :m}} {:map {:force-nl? false}})

{:a :b, :c :d, :e :f, :g {:i {:j :k}, :l :m}}

(czprint {:a :b :c :d :e :f :g { :i {:j :k} :l :m}} {:map {:force-nl? true}})

{:a :b,
 :c :d,
 :e :f,
 :g {:i {:j :k},
     :l :m}}
```

Also works with `:pair` functions

```clojure
(czprint "(cond abcd b cdef d)" {:parse-string? true :pair {:force-nl? false}})

(cond abcd b cdef d)

(czprint "(cond abcd b cdef d)" {:parse-string? true :pair {:force-nl? true}})

(cond abcd b
      cdef d)
```


#### :nl-separator?

This will put a blank line between any pair where the right part of a pair
was formatted with a flow. Some examples:

```clojure
(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 40 {:map {:nl-separator? false}})

{:a :b,
 :c {:e :f, :g :h, :i :j, :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; No effect if all the pairs print on one line

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 40 {:map {:nl-separator? true}})
{:a :b,
 :c {:e :f, :g :h, :i :j, :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; With a narrower width, one of them takes more than one line

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 30 {:map {:nl-separator? false}})

{:a :b,
 :c {:e :f,
     :g :h,
     :i :j,
     :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; and even now :nl-separator? will not have any effect because none of the
; right hand pairs are formatted with a flow -- that is, none of the right
; hand parts of the pairs start all of the way to the left.  They are still
; formatted as a hang

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 30 {:map {:nl-separator? true}})

{:a :b,
 :c {:e :f,
     :g :h,
     :i :j,
     :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; If you turn off the hang, then now if a pair doesn't fit on one line,
; you get a flow:

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 
         30 
         {:map {:nl-separator? true :hang? false}})

{:a :b,
 :c
   {:e :f,
    :g :h,
    :i :j,
    :k :l},
 
 :m :n,
 :o {:p {:q :r, :s :t}}}

; Most people use the :nl-separator? kind of formatting when they don't
; want the right hand side of a pair indented.  So if you turn off :hang?
; then you probably want to remove the indent as well.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 
         30 
         {:map {:nl-separator? true :hang? false :indent 0}})

{:a :b,
 :c
 {:e :f, :g :h, :i :j, :k :l},
 
 :m :n,
 :o {:p {:q :r, :s :t}}}
```


#### :justify?

Turn on [justification](#a-note-on-justifying-two-up-printing).
Default is nil (justification off).

# Configurable Elements
______
## :agent, :atom, :delay, :fn, :future, :promise 

All of these elements are formatted in a readable manner by default,
which shows their current value and minimizes extra information.

#### :object? <text style="color:#A4A4A4;"><small>false</small></text>

All of these elements can be formatted more as Clojure represents
Java objects by setting `:object?` to true.  

_____
## :array

Arrays are formatted by default with the values of their elements.

#### :hex? <text style="color:#A4A4A4;"><small>false</small></text>

If the elements are numeric, format them in hex. Useful if you are 
doing networking.  See below for an example. 

#### :object? <text style="color:#A4A4A4;"><small>false</small></text>

Don't print the elements of the array, just print it as an 
object. 

A simple example:

```clojure
(require '[zprint.core :as zp])

(def ba (byte-array (range 50)))

(zp/zprint ba 75)

[0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27
 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49]

(zp/zprint ba 75 {:array {:hex? true}})

[00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13 14 15 16 17 18
 19 1a 1b 1c 1d 1e 1f 20 21 22 23 24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30
 31]

;; As an aside, notice that the 8 in 18 was in column 75, and so while the
;; 31 would have fit, the ] would not, so they go on the next line.

(zp/zprint ba 75 {:array {:object? true}})

#object["[B" "0x31ef8e0b" "[B@31ef8e0b"]
```

#### :wrap? <text style="color:#A4A4A4;"><small>true</small></text>

Should it wrap its contents, or just list each on a separate line
if they don't all fit on one line.?
_____
## :binding

Controls the formatting of the first argument of
any function which has `:binding` as its function type.  `let` is, of
course, the canonical example. 

##### :indent <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang? <text style="color:#A4A4A4;"><small>true</small></text>
##### :hang-expand <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang-diff <text style="color:#A4A4A4;"><small>1</small></text>
##### :justify? <text style="color:#A4A4A4;"><small>false</small></text>
#### :force-nl?  <text style="color:#A4A4A4;"><small>true</small></text>

If you never want to see multiple binding pairs on the same line,
like this:

```clojure
(czprint "(let [abcd b cdef d efgh f] (list a f))" {:parse-string? true}

(let [abcd b cdef d efgh f] (list a f))
```

You can configure `:binding` to have `:force-nl? true`, which will yield this:
```clojure
(czprint "(let [abcd b cdef d efgh f] (list a f))" {:parse-string? true :binding {:force-nl? true}})

(let [abcd b
      cdef d
      efgh f]
  (list a f))

(czprint "(let [abcd b] (list a f))" {:parse-string? true :binding {:force-nl? true}})

(let [abcd b]
  (list a f))
```

#### :flow? <text style="color:#A4A4A4;"><small>false</small></text>
#### :nl-separator? <text style="color:#A4A4A4;"><small>false</small></text>

Both `:flow?` and `:nl-separator?` together with `:indent` can significantly
alter the way binding pairs are printed:

```clojure
(czprint "(let [abcd b cdef d efgh f] (list a f))" {:parse-string? true :binding {:flow? false}})

(let [abcd b cdef d efgh f] (list a f))

(czprint "(let [abcd b cdef d efgh f] (list a f))" {:parse-string? true :binding {:flow? true}})

; This isn't all that nice, but we are on the way to something different

(let [abcd
        b
      cdef
        d
      efgh
        f]
  (list a f))

(czprint "(let [abcd b cdef d efgh f] (list a f))" 
         {:parse-string? true :binding {:flow? true :indent 0}})

; Remove the indent

(let [abcd
      b
      cdef
      d
      efgh
      f]
  (list a f))

(czprint "(let [abcd b cdef d efgh f] (list a f))" 
         {:parse-string? true :binding {:flow? true :indent 0 :nl-separator? true}})

; Some people like their binding pairs formatted this way:

(let [abcd
      b

      cdef
      d

      efgh
      f]
  (list a f))
```
_____
## :comment

zprint has two fundemental regimes -- printing s-expressions and
parsing a string and printing the result.  There are no comments
in s-expressions, except in the `comment` function, which is handled
normally. When parsing a string, zprint will deal with comments.
Comments are dealt with in one of two ways -- either they are ignored
from a width standpoint while formatting, or their width is taken
into account when formatting.  In addition, comments can be
word-wrapped if they don't fit the width, or not.  These are
indpendent capabilities.

#### :wrap? <text style="color:#A4A4A4;"><small>true</small></text>

Wrap a comment if it doesn't fit within the width.  Works hard to preserve
the initial part of the line and word wraps the end.  Does not pull 
subsequent lines up on to a wrapped line.  

#### :inline? <text style="color:#A4A4A4;"><small>true</small></text>

If the a comment is on the same line as some code, keep the comment
on that same line.  The distance from the code is preserved (since we
don't really have any better idea yet).  If the comment extends beyond the
width, it will be wrapped just like a comment which is on its own line.

#### :count? <text style="color:#A4A4A4;"><small>false</small></text>

Count the length of the comment when ensuring that things fit within the
width. Doesn't play well with inline comments.  With any kinds of comments,
this tends to mess up the code more than helping, in my view.  

An example (using :parse-string? true to include the comment):

```clojure
(require '[zprint.core :as zp])

(def cd "(let [a (stuff with arguments)] (list (or foo bar baz) (format output now) (and a b c (bother this)) ;; Comment that doesn't fit real well, but is almost a fit to see how it works\n (format other stuff))(list a :b :c \"d\"))")

(zp/zprint cd 75 {:parse-string? true :comment {:count? nil}})

(let [a (stuff with arguments)]
  (list (or foo bar baz)
        (format output now)
        (and a b c (bother this))
        ;; Comment that doesn't fit real well, but is almost a fit to see
        ;; how it works
        (format other stuff))
  (list a :b :c "d"))

zprint.core=> (czprint cd 75 {:parse-string? true :comment {:count? true}})

(let [a (stuff with arguments)]
  (list
    (or foo bar baz)
    (format output now)
    (and a b c (bother this))
    ;; Comment that doesn't fit real well, but is almost a fit to see how
    ;; it works
    (format other stuff))
  (list a :b :c "d"))

(zp/zprint cd 75 {:parse-string? true :comment {:count? nil :wrap? nil}})

(let [a (stuff with arguments)]
  (list (or foo bar baz)
        (format output now)
        (and a b c (bother this))
        ;; Comment that doesn't fit real well, but is almost a fit to see how it works
        (format other stuff))
  (list a :b :c "d"))
```
_____
## :extend

When formatting functions which have extend in their function types.

##### :indent <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang? <text style="color:#A4A4A4;"><small>true</small></text>
#### :force-nl?  <text style="color:#A4A4A4;"><small>true</small></text>

Forces a new line between one type/fn defn set and the next in the extend.

#### :nl-separator? <text style="color:#A4A4A4;"><small>false</small></text>

Places a blank line between one type/fn defn set and the next if the
fn defn set formats with a flow.

#### :flow? <text style="color:#A4A4A4;"><small>false</small></text>

Places a new line between the type and the fn defns in a single 
type/fn defn set in the extend.

Here are some examples of two rather different, but commonly used,
ways to format extend:

```clojure
(czprint-fn ->Typetest)

; Default output, :force-nl? is true

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq (hasheq [this] (list this))
  clojure.lang.Counted (count [_] cnt)
  clojure.lang.IMeta (meta [_] _meta))

(czprint-fn ->Typetest {:extend {:flow? true}})

; Add :flow? true, always keeps fn defns on separate line

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq
    (hasheq [this] (list this))
  clojure.lang.Counted
    (count [_] cnt)
  clojure.lang.IMeta
    (meta [_] _meta))

(czprint-fn ->Typetest {:extend {:flow? true :indent 0}})

; Remove all indent

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq
  (hasheq [this] (list this))
  clojure.lang.Counted
  (count [_] cnt)
  clojure.lang.IMeta
  (meta [_] _meta))

(czprint-fn ->Typetest {:extend {:flow? true :indent 0 :nl-separator? true}})

; Add :nl-separator? true for an altogether different (but commonly used) look

(deftype Typetest
  [cnt _meta]
  clojure.lang.IHashEq
  (hasheq [this] (list this))

  clojure.lang.Counted
  (count [_] cnt)

  clojure.lang.IMeta
  (meta [_] _meta))

```
#### :modifiers <text style="color:#A4A4A4;"><small>#{"static"}</small></text>

Contains a set of elements that will be placed on the same line as the
protocol-or-interface-or-Object.  Created largely to support `defui` in
Clojurescript om/next, but may have other utility. Elements specified
by `{:extend {:modifiers #{<element1> <element2>}}}` are added to
the set (as opposed to replacing the set entirely). You can remove 
elements from the set by `{:remove {:extend {:modifers #{<thing-to-remove>}}}}`.

_____
## :list

Lists show up in lots of places, but mostly they are code.  So
in addition to the various function types described above, the `:list`
configuration affects the look of formatted code.

##### :indent <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang? <text style="color:#A4A4A4;"><small>true</small></text>
##### :hang-avoid <text style="color:#A4A4A4;"><small>0.5</small></text>
##### :hang-expand <text style="color:#A4A4A4;"><small>2.0</small></text>
##### :hang-diff <text style="color:#A4A4A4;"><small>1</small></text>

#### :indent-arg <text style="color:#A4A4A4;"><small>nil</small></text>

The amount to indent the arguments of a function whose arguments do
not contain "body" forms.
See [here](#function-classification-for-pretty-printing)
for an explanation of what this means.  If this is nil, then the value
configured for `:indent` is used for the arguments of functions that
are not "body" functions.  You would configure this value only if
you wanted "arg" type functions to have a different indent from
"body" type functions.  It is configured by `:style :community`.

#### :hang-size <text style="color:#A4A4A4;"><small>100</small></text>

The maximum number of lines that are allowed in a hang.  If the number
of lines in the hang is greater than the `:hang-size`, it will not do
the hang but instead will format this as a flow.  Together with
`:hang-expand` this will keep hangs from getting too long so that
code (typically) doesn't get very distorted.

#### :constant-pair? <text style="color:#A4A4A4;"><small>true</small></text>

Lists (which are frequently code) support something called _**constant
pairing**_.  This capability looks at the end of a list, and if the
end of the list appears to contain pairs of constants followed by
anything, it will print them paired up.  A constant in this context
is a keyword, string, or number.  An example will best illustrate
this.

We will use a feature of zprint, where it will parse a string prior to
formatting, so that the anonymous functions show up right.

```clojure
(require '[zprint.core :as zp])

(def x "(s/fdef spec-test\n :args (s/and (s/cat :start integer? :end integer?)\n #(< (:start %) (:end %)))\n :ret integer?\n :fn (s/and #(>= (:ret %) (-> % :args :start))\n #(< (:ret %) (-> % :args :end))))\n")

;;
;; Without constant pairing, it is ok...
;; 

(zp/zprint x 60 {:parse-string? true :list {:constant-pair? nil}})

(s/fdef spec-test
        :args
        (s/and (s/cat :start integer? :end integer?)
               #(< (:start %) (:end %)))
        :ret
        integer?
        :fn
        (s/and #(>= (:ret %) (-> % :args :start))
               #(< (:ret %) (-> % :args :end))))

;;
;; With constant pairing it is nicer
;;

(zp/zprint x 60 {:parse-string? true :list {:constant-pair true}})

(s/fdef spec-test
        :args (s/and (s/cat :start integer? :end integer?)
                     #(< (:start %) (:end %)))
        :ret integer?
        :fn (s/and #(>= (:ret %) (-> % :args :start))
                   #(< (:ret %) (-> % :args :end))))

;;
;; We can demonstrate another configuration capability here.
;; If we tell zprint that s/fdef is an :arg1 style function, it is better
;; (note that :constant-pair? true is the default).
;;

(zp/zprint x 60 {:parse-string? true :fn-map {"s/fdef" :arg1}})

(s/fdef spec-test
  :args (s/and (s/cat :start integer? :end integer?)
               #(< (:start %) (:end %)))
  :ret integer?
  :fn (s/and #(>= (:ret %) (-> % :args :start))
             #(< (:ret %) (-> % :args :end))))
```
Constant pairing tends to make keyword style arguments come out
looking rather better than they would otherwise.  This feature was added
to handle what I believed was a very narrow use case, but it has shown
suprising generality, making unexpected things look much better.

In particular, try it on your specs!

Note that the formatting of the pairs in a constant pair is controlled
by the `:pair` configuration (just like the pairs in a `cond`, `assoc`,
and any function style with "pair" in the name).

#### :constant-pair-min <text style="color:#A4A4A4;"><small>4</small></text>
 
An integer specifying the minimum number of required elements capable of being
constant paired before constant pairing is used.  Note that constant
pairing works from the end of the list back toward the front (not illustrated
in these examples).  

Using our previous example again:

```clojure
(require '[zprint.core :as zp])

(def x "(s/fdef spec-test\n :args (s/and (s/cat :start integer? :end integer?)\n #(< (:start %) (:end %)))\n :ret integer?\n :fn (s/and #(>= (:ret %) (-> % :args :start))\n #(< (:ret %) (-> % :args :end))))\n")

;;
;; There are 6 elements that can be constant paired
;;

(zp/zprint x 60 {:parse-string? true :list {:constant-pair-min 6}})

(s/fdef spec-test
        :args (s/and (s/cat :start integer? :end integer?)
                     #(< (:start %) (:end %)))
        :ret integer?
        :fn (s/and #(>= (:ret %) (-> % :args :start))
                   #(< (:ret %) (-> % :args :end))))

;;
;; So, if we change the requirements to 8, it won't constant-pair
;;

(zp/zprint x 60 {:parse-string? true :list {:constant-pair-min 8}})

(s/fdef spec-test
        :args
        (s/and (s/cat :start integer? :end integer?)
               #(< (:start %) (:end %)))
        :ret
        integer?
        :fn
        (s/and #(>= (:ret %) (-> % :args :start))
               #(< (:ret %) (-> % :args :end))))
```
_____
## :map

Maps support both the __indent__ and __hang__ values, above.  The default
`:hang-expand` value is `1000.0` because maps  don't look bad with a large
hangs.

##### :indent <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang? <text style="color:#A4A4A4;"><small>true</small></text>
##### :hang-expand <text style="color:#A4A4A4;"><small>1000.0</small></text>
##### :hang-diff <text style="color:#A4A4A4;"><small>1</small></text>
##### :justify? <text style="color:#A4A4A4;"><small>false</small></text>

#### :flow? <text style="color:#A4A4A4;"><small>false</small></text>

Never print the key and value of a single key/value pair on the same
line.

```clojure
(czprint {:abc :def :ghi :ijk})

{:abc :def, :ghi :ijk}

(czprint {:abc :def :ghi :ijk} {:map {:flow? true}})

{:abc
   :def,
 :ghi
   :ijk}
```
#### :nl-separator? <text style="color:#A4A4A4;"><small>false</small></text>

Put an entirely blank line between any key/value pair where the value
part of the pair formats as a flow.

```clojure
(czprint {:abc :def :ghi :ijk})

{:abc :def, :ghi :ijk}

(czprint {:abc :def :ghi :ijk} {:map {:flow? true :indent 0}})

{:abc
 :def,
 :ghi
 :ijk}

(czprint {:abc :def :ghi :ijk} {:map {:flow? true :indent 0 :nl-separator? true}})

{:abc
 :def,
 
 :ghi
 :ijk}
```

But maybe you want to still allow the values of a key/value pair to
print on the same line when possible, and only want a blank line when
the key/value pair formats with the value as a flow.

```clojure
(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 40 {:map {:nl-separator? false}})

{:a :b,
 :c {:e :f, :g :h, :i :j, :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; No effect if all the pairs print on one line

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 40 {:map {:nl-separator? true}})
{:a :b,
 :c {:e :f, :g :h, :i :j, :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; With a narrower width (30 instead of 40), one of them take more than one line

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 30 {:map {:nl-separator? false}})

{:a :b,
 :c {:e :f,
     :g :h,
     :i :j,
     :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; and even now :nl-separator? will not have any effect because none of the
; right hand pairs are formatted with a flow -- that is, none of the right
; hand parts of the pairs start all of the way to the left.  They are still
; formatted as a hang

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 30 {:map {:nl-separator? true}})

{:a :b,
 :c {:e :f,
     :g :h,
     :i :j,
     :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; If you turn off the hang, then now if a pair doesn't fit on one line,
; you get a flow:

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 
         30 
         {:map {:nl-separator? true :hang? false}})

; Most people use the :nl-separator? kind of formatting when they don't
; want the right hand side of a pair indented.  So if you turn off :hang?
; then you probably want to remove the indent as well.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} 
         30 
         {:map {:nl-separator? true :hang? false :indent 0}})

{:a :b,
 :c
 {:e :f, :g :h, :i :j, :k :l},
 
 :m :n,
 :o {:p {:q :r, :s :t}}}
```

####  :comma?  <text style="color:#A4A4A4;"><small>true</small></text>

Put a comma after the value in a key-value pair, if it is not the
last pair in a map.  

#### :force-nl? <text style="color:#A4A4A4;"><small>false</small></text>

Force a new-line between each key and value pair in a map.  

```clojure
(czprint {:abc :def :ghi :ijk})

{:abc :def, :ghi :ijk}

(czprint {:abc :def :ghi :ijk} {:map {:force-nl? true}})

{:abc :def,
 :ghi :ijk}

```

#### :sort? <text style="color:#A4A4A4;"><small>true</small></text>

Sort the key-value pairs in a map prior to output.  Alternatively, simply output
them in the order in which they come out of the map. 

#### :sort-in-code? <text style="color:#A4A4A4;"><small>false</small></text>

If the map appears inside of a list that seems to be code, should it
be sorted.  

#### :key-order <text style="color:#A4A4A4;"><small>nil</small></text>

Accepts a vector which contains keys which should sort before all
other keys.  Typically these keys would be keywords, strings, or
integers.  The value of this capability is to bring one or more
key-value pairs to the top of a map when it is output, in order to
aid in visually distinguishing one map from the other.  This can
be a significant help in debugging, when looking a lot of maps at
the repl.  Note that `:key-order` only affects the key order when
keys are sorted.

Here is a vector of maps formatted with just sorting. 

```clojure
[{:code "58601",
  :connection "2795",
  :detail {:alternate "64:c1:2f:34",
           :ident "64:c1:2f:34",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :received,
  :reference 14873,
  :time 1425704001,
  :type "get"}
 {:code "0xe4e9",
  :connection "X13404",
  :detail
    {:code "0xe4e9", :ident "64:c1:2f:34", :ip4 "3.13.168.151", :time "30m"},
  :direction :transmitted,
  :reference 14133,
  :time 1425704001,
  :type "post"}
 {:code "58601",
  :connection "X13404",
  :direction :transmitted,
  :reference 14227,
  :time 1425704001,
  :type "post"}
 {:code "0x1344a676",
  :connection "2796",
  :detail {:code "0x1344a676", :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"},
  :direction :received,
  :reference 14133,
  :time 1425704003,
  :type "error"}
 {:code "323266166",
  :connection "2796",
  :detail {:alternate "01:50:56:a5:1d:61",
           :ident "50:56:a5:1d:61",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :transmitted,
  :reference 14873,
  :time 1425704003,
  :type "error"}]
```

Lots of information -- at least it is sorted.
But the type and the direction are the important parts if you
were scanning this at the repl, and they are buried pretty deep.

If you were looking for received errors, and needed to see them
in context, you might prefer the following presentation...

Using the following options map:

```clojure
{:map {:key-order [:type :direction]}})
```
yields:

```clojure
[{:type "get",
  :direction :received,
  :code "58601",
  :connection "2795",
  :detail {:alternate "64:c1:2f:34",
           :ident "64:c1:2f:34",
           :interface "3.13.168.35",
           :string "datacenter"},
  :reference 14873,
  :time 1425704001}
 {:type "post",
  :direction :transmitted,
  :code "0xe4e9",
  :connection "X13404",
  :detail
    {:code "0xe4e9", :ident "64:c1:2f:34", :ip4 "3.13.168.151", :time "30m"},
  :reference 14133,
  :time 1425704001}
 {:type "post",
  :direction :transmitted,
  :code "58601",
  :connection "X13404",
  :reference 14227,
  :time 1425704001}
 {:type "error",
  :direction :received,
  :code "0x1344a676",
  :connection "2796",
  :detail {:code "0x1344a676", :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"},
  :reference 14133,
  :time 1425704003}
 {:type "error",
  :direction :transmitted,
  :code "323266166",
  :connection "2796",
  :detail {:alternate "01:50:56:a5:1d:61",
           :ident "50:56:a5:1d:61",
           :interface "3.13.168.35",
           :string "datacenter"},
  :reference 14873,
  :time 1425704003}]
```

When working with hundreds of maps, even the tiny improvement 
made by ordering a few keys in a better way can reduce the cognitive
load, particularly when debugging.

#### :key-ignore <text style="color:#A4A4A4;"><small>nil</small></text>
#### :key-ignore-silent <text style="color:#A4A4A4;"><small>nil</small></text>

You can also ignore keys (or key sequences) in maps when formatting
them.  There are two basic approaches.  `:key-ignore` will replace
the value of the key(s) to be ignored with `:zprint-ignored`, where
`:key-ignore-silent` will simply remove them from the formatted output.

__NOTE:__ This only affects the formatting of s-expressions, and
has no effect on the output when using the `{:parse-string? true}`
capability (as is done when formatting code).  Nobody wants to
lose map keys when formatting code.

You might use this to remove sensitive information from output, or
to remove elements that have more data than you wish to display.

You can also supply key-sequences, in addition to single keys, to
either configuration parameter.

An example of the basic approach.  First the unmodified data:

```clojure
zprint.core=> (czprint sort-demo)

[{:code "58601",
  :connection "2795",
  :detail {:alternate "64:c1:2f:34",
           :ident "64:c1:2f:34",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :received,
  :reference 14873,
  :time 1425704001,
  :type "get"}
 {:code "0xe4e9",
  :connection "X13404",
  :detail
    {:code "0xe4e9", :ident "64:c1:2f:34", :ip4 "3.13.168.151", :time "30m"},
  :direction :transmitted,
  :reference 14133,
  :time 1425704001,
  :type "post"}
 {:code "58601",
  :connection "X13404",
  :direction :transmitted,
  :reference 14227,
  :time 1425704001,
  :type "post"}
 {:code "0x1344a676",
  :connection "2796",
  :detail {:code "0x1344a676", :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"},
  :direction :received,
  :reference 14133,
  :time 1425704003,
  :type "error"}
 {:code "323266166",
  :connection "2796",
  :detail {:alternate "01:50:56:a5:1d:61",
           :ident "50:56:a5:1d:61",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :transmitted,
  :reference 14873,
  :time 1425704003,
  :type "error"}]
```

Here is the data with the `:detail` key ignored:

```clojure
zprint.core=> (czprint sort-demo {:map {:key-ignore [:detail]}})

[{:code "58601",
  :connection "2795",
  :detail :zprint-ignored,
  :direction :received,
  :reference 14873,
  :time 1425704001,
  :type "get"}
 {:code "0xe4e9",
  :connection "X13404",
  :detail :zprint-ignored,
  :direction :transmitted,
  :reference 14133,
  :time 1425704001,
  :type "post"}
 {:code "58601",
  :connection "X13404",
  :direction :transmitted,
  :reference 14227,
  :time 1425704001,
  :type "post"}
 {:code "0x1344a676",
  :connection "2796",
  :detail :zprint-ignored,
  :direction :received,
  :reference 14133,
  :time 1425704003,
  :type "error"}
 {:code "323266166",
  :connection "2796",
  :detail :zprint-ignored,
  :direction :transmitted,
  :reference 14873,
  :time 1425704003,
  :type "error"}]
```

The same as above, with `:key-ignore-silent` instead of `key-ignore`:

```clojure
zprint.core=> (czprint sort-demo {:map {:key-ignore-silent [:detail]}})

[{:code "58601",
  :connection "2795",
  :direction :received,
  :reference 14873,
  :time 1425704001,
  :type "get"}
 {:code "0xe4e9",
  :connection "X13404",
  :direction :transmitted,
  :reference 14133,
  :time 1425704001,
  :type "post"}
 {:code "58601",
  :connection "X13404",
  :direction :transmitted,
  :reference 14227,
  :time 1425704001,
  :type "post"}
 {:code "0x1344a676",
  :connection "2796",
  :direction :received,
  :reference 14133,
  :time 1425704003,
  :type "error"}
 {:code "323266166",
  :connection "2796",
  :direction :transmitted,
  :reference 14873,
  :time 1425704003,
  :type "error"}]
```

An example of the key-sequence approach.  This will remove all
of the elements with key `:code` inside of the maps with the key
`:detail`, but not the elements with the key `:code` elsewhere.
This example uses `:key-ignore` so you can see where it removed
values.  

```clojure
zprint.core=> (czprint sort-demo {:map {:key-ignore [[:detail :code]]}})

[{:code "58601",
  :connection "2795",
  :detail {:alternate "64:c1:2f:34",
           :ident "64:c1:2f:34",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :received,
  :reference 14873,
  :time 1425704001,
  :type "get"}
 {:code "0xe4e9",
  :connection "X13404",
  :detail {:code :zprint-ignored,
           :ident "64:c1:2f:34",
           :ip4 "3.13.168.151",
           :time "30m"},
  :direction :transmitted,
  :reference 14133,
  :time 1425704001,
  :type "post"}
 {:code "58601",
  :connection "X13404",
  :direction :transmitted,
  :reference 14227,
  :time 1425704001,
  :type "post"}
 {:code "0x1344a676",
  :connection "2796",
  :detail {:code :zprint-ignored, :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"},
  :direction :received,
  :reference 14133,
  :time 1425704003,
  :type "error"}
 {:code "323266166",
  :connection "2796",
  :detail {:alternate "01:50:56:a5:1d:61",
           :ident "50:56:a5:1d:61",
           :interface "3.13.168.35",
           :string "datacenter"},
  :direction :transmitted,
  :reference 14873,
  :time 1425704003,
  :type "error"}]
```

####  :key-color  <text style="color:#A4A4A4;"><small>nil</small></text>

The value of `:key-color` is a map which relates keys that are
'constants' to a color in which to print that key.  A constant is
a keyword, string, or number.  This way you can have some keys
formatted in a color that is different from the color in which they
would normally be formatted based on their type.  It can go well
with `:key-order [:key1 :key2 ...]` which is another way to distinguish
a special key.  You can place some keys at the front of the map and
you can also adjust their colors to meet your needs.

####  :key-value-color  <text style="color:#A4A4A4;"><small>nil</small></text>

The value of `:key-value-color` is a map which relates keys (that
don't have to be constants) to a color-map which is merged into the
current color-map, and is used when formatting the __value__ of that key.
This way you can have the values of some keys formatted in a color that
is different from the color in which they would normally be formatted
based on their type.

####  :key-depth-color  <text style="color:#A4A4A4;"><small>nil</small></text>

Note that this is an EXPERIMENTAL feature.  The value of `:key-depth-color` is 
a vector of colors, and these colors will be used to color the keys which
are at a corresponding depth in the map.  Thus, the first color in the vector
will be the color for the outermost keys in the map, the second color in 
vector will be the color for the keys.  You can place a `nil` in the vector,
and for that depth the normal colors (based on the type of the key) will
be used.  If you also have defined a `:key-color` map, any colors speciied
in that map for specific keys will override the color that they would be
given by the `:key-depth-color` vector.

####  :lift-ns?  <text style="color:#A4A4A4;"><small>true</small></text>

When all of the keys in a map are namespaced, and they all have the same
key, "lift" that namespace out of the keys and make it a namespaced map.

For example:
```clojure
(zprint {:x/a :b :x/c :d} {:map {:lift-ns? true}})
#:x{:a :b, :c :d}

(zprint {::a :b ::c :d} {:map {:lift-ns? true}})
#:zprint.core{:a :b, :c :d}
```
This generally works for strings that are parsed as well, with one significant
exception.  If you have an implicitly namespaced keyword, like `::a`, then
this cannot be "lifted" when encountered in a string because there is no
way to reliably infer the implicit namespace.  Thus, the entire map
will not be lifted if it contains a single `::a` type key in it.

####  :lift-ns-in-code?  <text style="color:#A4A4A4;"><small>false</small></text>

Controls whether to actually lift the namespace if the map is in code.
_____
## :object

When elements are formatted with `:object?` `true`, then the output
if formatted using the information specified in the `:object` 
information.  

##### :indent <text style="color:#A4A4A4;"><small>1</small></text>
_____
## :output

This controls the overall output that is produced.  

#### :focus 
Determines whether to highlight a part of the structure, and which
part to highlight. Only one of `:zloc?` or `:path` can have a value.

Contains a map with the following possible keys.
##### :zloc? <text style="color:#A4A4A4;"><small>false</small></text>
If true, indicates that the first argument is a zipper, and the zipper
currently "points at" the expression at which to focus.  zprint will
print the entire zipper, and highlight the expression at which the
zipper is currently pointing.
##### :path <text style="color:#A4A4A4;"><small>nil</small></text>
The path is a vector of integers, which indicates where the focus
should be placed.  Each number in the vector indicates moving into
a structure, and the value of the number indicates the element within
that structure on which the focus rests.  Presently, the error
handling for bad paths is some sort of exception.

If you have a structure like this: `[:a [:b [:c :d] :e :f]]`
then the path `[1 1 0]` would highlight the `:c`.  The path `[1 1]` would
highlight the `[:c :d]`.  The path `[0]` would highlight the `:a`.
______
## :pair

The :pair key controls the printing of the arguments of a function
which has -pair in its function type (e.g. `:arg1-pair`, `:pair-fn`,
`:arg2-pair`).  `:pair`

##### :indent <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang? <text style="color:#A4A4A4;"><small>true</small></text>
##### :hang-expand <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang-diff <text style="color:#A4A4A4;"><small>1</small></text>
##### :justify? <text style="color:#A4A4A4;"><small>false</small></text>

#### :force-nl? <text style="color:#A4A4A4;"><small>false</small></text>

If you wish to force a newline between all things that are paired
(which is more than just `cond`), you can use `:force-nl?`.  For example:

```clojure
(czprint "(cond abcd b cdef d)" {:parse-string? true :pair {:force-nl? false}})

(cond abcd b cdef d)

(czprint "(cond abcd b cdef d)" {:parse-string? true :pair {:force-nl? true}})

(cond abcd b
      cdef d)
```

You could achieve a similar result by placing the function style `:pair-fn` 
into the set of `:fn-gt2-force-nl`, thus:

```clojure
(czprint "(cond abcd b)" {:parse-string? true :fn-gt2-force-nl #{:pair-fn}})

(cond abcd b)

(czprint "(cond abcd b cdef d)" {:parse-string? true :fn-gt2-force-nl #{:pair-fn}})

(cond abcd b
      cdef d)
```
#### :flow? <text style="color:#A4A4A4;"><small>false</small></text>

Format the right-hand part of every pair to onto a different
line from the left-hand part of every pair.

#### :nl-separator? <text style="color:#A4A4A4;"><small>false</small></text>

Insert an entirely blank line between every pair where the right hand part
of the pair is formatted as a flow.

Some examples of how `:flow?` an `:nl-separator?` can interact:

```clojure
(czprint "(cond abcd b cdef d)" {:parse-string? true})

(cond abcd b cdef d)

(czprint "(cond abcd b cdef d)" {:parse-string? true :pair {:flow? true}})

; :flow? causes the right-hand part of each pair to move to another line

(cond abcd
        b
      cdef
        d)

(czprint "(cond abcd b cdef d)" {:parse-string? true :pair {:flow? true :indent 0}})

; Remove the indent

(cond abcd
      b
      cdef
      d)

(czprint "(cond abcd b cdef d)" 
         {:parse-string? true :pair {:flow? true :indent 0 :nl-separator? true}})

; :nl-separator? places an entirely blank line between any pair that formats as a flow

(cond abcd
      b

      cdef
      d)

(czprint "(cond abcd b cdef d)" 
         {:parse-string? true 
          :pair {:flow? true
                 :indent 0 
                 :nl-separator? true} 
          :pair-fn {:hang? false}})

; Just FYI, this is how to cause cond to never hang its pairs

(cond
  abcd
  b

  cdef
  d)

```
_____
## :pair-fn

The :pair-fn key controls the printing of the arguments of a function
which has :pair-fn as its function type (e.g. `cond`). 

##### :hang? <text style="color:#A4A4A4;"><small>true</small></text>
##### :hang-expand <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang-diff <text style="color:#A4A4A4;"><small>1</small></text>

This function type exists largely to allow you to control how the
pairs of a `cond` are formatted with respect to the function name.  
For example:

```clojure
(czprint "(cond abcd efgh ijkl mnop)" 20 {:parse-string? true :pair-fn {:hang? true}})

(cond abcd efgh
      ijkl mnop)

(czprint "(cond abcd efgh ijkl mnop)" 20 {:parse-string? true :pair-fn {:hang? false}})

(cond
  abcd efgh
  ijkl mnop)
```

The formatting of the pairs themselves is controlled by `:pair`.

_____
## :reader-cond

Reader conditionals are controlled by the `:reader-cond` key.  All
of the keys which are supported for `:map` are supported for `:reader-cond`
(except `:comma?`), albeit with different defaults.  By default,
`:sort?` is nil, so the elements are not reordered.  You could enable
`:sort?` and specify a `:key-order` vector to order the elements of a
reader conditional.

##### :indent <text style="color:#A4A4A4;"><small>2</small></text>
##### :hang? <text style="color:#A4A4A4;"><small>true</small></text>
##### :hang-expand <text style="color:#A4A4A4;"><small>1000.0</small></text>
##### :hang-diff <text style="color:#A4A4A4;"><small>1</small></text>
##### :force-nl? <text style="color:#A4A4A4;"><small>true</small></text>
##### :sort? <text style="color:#A4A4A4;"><small>false</small></text>
##### :sort-in-code? <text style="color:#A4A4A4;"><small>false</small></text>
##### :key-order <text style="color:#A4A4A4;"><small>nil</small></text>

_____
## :record

Records are printed with the record-type and value of the record
shown with map syntax, or by calling their `toString()` method.

#### :to-string? <text style="color:#A4A4A4;"><small>false</small></text>

This will output a record by calling its `toString()` java method, which
can be useful for some records. If the record contains a lot of information
that you didn't want to print, for instance. If `:to-string?` is true, 
it overrides the other `:record` configuration options.

#### :hang? <text style="color:#A4A4A4;"><small>true</small></text>

Should a hang be attempted?  See example below. 

#### :record-type? <text style="color:#A4A4A4;"><small>true</small></text>

Should the record type be output?  

An example of `:hang?`, `:record-type?`, and `:to-string?`

```clojure
(require '[zprint.core :as zp])

(defrecord myrecord [left right])

(def x (new myrecord ["a" "lot" "of" "stuff" "so" "that" "it" "doesn't" "fit" "all" "on" "one" "line"] [:more :stuff :but :not :quite :as :much]))

(zp/zprint x 75)

#zprint.core/myrecord {:left ["a" "lot" "of" "stuff" "so" "that" "it"
                              "doesn't" "fit" "all" "on" "one" "line"],
                      :right [:more :stuff :but :not :quite :as :much]}

(zp/zprint x 75 {:record {:hang? nil}})

#zprint.core/myrecord
 {:left ["a" "lot" "of" "stuff" "so" "that" "it" "doesn't" "fit" "all" "on"
         "one" "line"],
  :right [:more :stuff :but :not :quite :as :much]}

(zp/zprint x 75 {:record {:record-type? nil}})

{:left ["a" "lot" "of" "stuff" "so" "that" "it" "doesn't" "fit" "all" "on"
        "one" "line"],
 :right [:more :stuff :but :not :quite :as :much]}

(zprint x {:record {:to-string? true}})

"zprint.core.myrecord@682a5f6b"
```
_____
## :set

`:set` supports the same keys as does vector and a few more.

##### :indent <text style="color:#A4A4A4;"><small>1</small></text>
##### :wrap? <text style="color:#A4A4A4;"><small>true</small></text>
##### :wrap-coll? <text style="color:#A4A4A4;"><small>true</small></text>
##### :wrap-after-multi? <text style="color:#A4A4A4;"><small>true</small></text>

#### :sort? <text style="color:#A4A4A4;"><small>true</small></text>

Sort the elements in a set prior to output.  Alternatively, simply output
them in the order in which they come out of the set.

#### :sort-in-code? <text style="color:#A4A4A4;"><small>false</small></text>

If the set appears inside of a list that seems to be code, should it
be sorted.  

_____
## :spec

`:spec` controls how specs are integrated into the `(zprint-fn ...)` and
`(czprint-fn ...)` output.  This only operates if the version of Clojure
supports specs, and the function being output _has_ a spec.  If
that is true, and if:

##### :docstring? <text style="color:#A4A4A4;"><small>true</small></text>

is also true, then zprint will format the spec and append it to the
docstring.  At present this only works for docstrings in `defn` and
`defmacro` definitions, not functions defined with `def`.

______
## :style and :style-map

You specify a style by adding `:style <style>` at the top level
of the options map.  You can also set more than one style by
enclosing the styles in a vector, for example:

```clojure
(set-options! {:style [:binding-nl :extend-nl]})
```
When multiple styles are specified, they are applied in the order
given.

Note that styles are applied before the rest of the elements
of a options map, so that you can override elements of the style
that you wish to change by specifying an explicit element in the
options map.

### Available Styles:

#### :community

This attempts to recreate the community standards defined in the
[community style guide](https://github.com/bbatsov/clojure-style-guide).
It is an evolving effort -- if you see something that matters to you
that differs from the community style guide when using `:style :community`, 
please create an issue explaining the difference.

#### :justified

This sets `:justify? true` in each of `:binding`, `:pair`, and `:map`.
It is useful to see what you think about justfied output.

#### :extend-nl

This sets up a different way of formatting extend styles, with a new-line
between each group.  For example

```clojure
(czprint-fn ->Typetest1)

; Default output

(deftype Typetest1
  [cnt _meta]
  clojure.lang.IHashEq
    (hasheq [this] (list this) (list this this) (list this this this this))
  clojure.lang.Counted (count [_] cnt)
  clojure.lang.IMeta (meta [_] _meta))

(czprint-fn ->Typetest1 {:style :extend-nl})

; Alternative output with {:style :extend-nl}

(deftype Typetest1
  [cnt _meta]
  clojure.lang.IHashEq
  (hasheq [this] (list this) (list this this) (list this this this this))

  clojure.lang.Counted
  (count [_] cnt)

  clojure.lang.IMeta
  (meta [_] _meta))

```
#### :how-to-ns

__Experimental:__ Still working out some of the details, so the
specifics may change.

This will format `ns` declarations regarding newlines and indentation
as in Stewart Sierra's "How to ns".  Specifically, it will indent
lists by 1 instead of 2, and not hang lists except for `:import`.
If you follow the instructions in the "How to ns" blog post, the
new lines and indentation will be correct.  zprint will not reorganize
the `ns` declaration or change lists to vectors or otherwise change
the order or syntax of what you have entered -- that's still your
responsibility.

#### :map-nl, :pair-nl, :binding-nl

These are convenience styles which simply allow you to set `{:indent 0 :nl-separator? true}` for each of the associated format elements.  They simply exist to
save you some typing if these styles are favorites of yours.

#### :spec

This style is good for formatting clojure.spec files.  The 
`{:list {:constant-pair-min 2}}` is going to be useful for everyone, and
probably the `{:pair {:indent 0}}` will be too.  The `{:vector {:wrap? false}}`
is an open question.  Some people want the keys each on one line, and some
people want them all tucked up together.  Remember, a style is applied before
the rest of the options map, so if you want to have your specs that show up
in a vector all tucked up together, you can do: `{:style :spec, :vector {:wrap? true}}`.

#### :keyword-respect-nl

This style is used to preserve most of the existing formatting for
vectors with keywords as the first element.  The typical example is
hiccup or rum data inside of a function or in a source file.  If
you specify this style, every vector with a keyword as the first element
will preserve the newlines present in the input to zprint.  zprint will
still handle the indenting, and none of the existing newlines will cause
zprint to violate its basic formatting rules (e.g., lines will still
fit in the width, etc.).  But the existing hand-formatting will typically
be preserved if it makes any sense at all.

This is usually only useful formatting source.  Here is an example using
a `rum` macro (taken from GitHub rum/examples/rum/examples/inputs.cljc):

```clojure
; The original way it was defined:

(print re1)

(rum/defc inputs []
  (let [*ref (atom nil)]
    [:dl
      [:dt "Input"]  [:dd (reactive-input *ref)]
      [:dt "Checks"] [:dd (checkboxes *ref)]
      [:dt "Radio"]  [:dd (radio *ref)]
      [:dt "Select"] [:dd (select *ref)]
      [:dt (value *ref)] [:dd (shuffle-button *ref)]]))nil

; An unlovely transformation:

(zprint re1 {:parse-string? true})

(rum/defc inputs
  []
  (let [*ref (atom nil)]
    [:dl [:dt "Input"] [:dd (reactive-input *ref)] [:dt "Checks"]
     [:dd (checkboxes *ref)] [:dt "Radio"] [:dd (radio *ref)] [:dt "Select"]
     [:dd (select *ref)] [:dt (value *ref)] [:dd (shuffle-button *ref)]]))

; A much better approach (close, though not identical, to the input):

(zprint re1 {:parse-string? true :style :keyword-respect-nl})

(rum/defc inputs
  []
  (let [*ref (atom nil)]
    [:dl
     [:dt "Input"] [:dd (reactive-input *ref)]
     [:dt "Checks"] [:dd (checkboxes *ref)]
     [:dt "Radio"] [:dd (radio *ref)]
     [:dt "Select"] [:dd (select *ref)]
     [:dt (value *ref)] [:dd (shuffle-button *ref)]]))
```
The implementation of this style is as follows:

```clojure
:keyword-respect-nl
  {:vector {:option-fn-first #(let [k? (keyword? %2)]
                               (when (not= k? (:respect-nl? (:vector %1)))
                                 {:vector {:respect-nl? k?}}))}},
```
which serves as an example of how to implement an :option-fn-first
function for vectors.

#### :all-hang, :no-hang

These two styles will turn on or off all of the `:hang?` booleans
in `:map`, `:list`, `:extend`, `:pair`, `:pair-fn`, `:reader-cond`,
and `:record`.  The `:hang?` capability almost always produces
clearly better results, but can take more time (particularly in
Clojurescript, as it is single-threaded).  `:all-hang` is the
effective default, but `:no-hang` can be used to turn it all off
if you wish.  If `:hang?` is off for some reason, you can use
`:all-hang` to turn it back on.

### Defining your own styles

You can define your own styles, by adding elements to the `:style-map`.
You can do this the same way you make other configuration changes,
but probably you want to define a style in the .zprintrc file, and
then use it elsewhere.

You can see the existing styles in the `:style-map`, and you would
just add your own along the same lines.  The map associated with a
style must validate successfully just as if you used it as an options
map in an individual call to zprint.

You might wish to define several styles with different color-maps,
perhaps, allowing you to alter the colors more easily.

You cannot define a style and apply it in the same configuration
pass, as styles are applied before the rest of the configuration in
a options map.

______
## :tab

zprint will expand tabs by default when parsing a string, largely in order
to properly size comments.  You can disable tab expansion and you can
set the tab size.

#### :expand? <text style="color:#A4A4A4;"><small>true</small></text>

Expand tabs.  

#### :size <text style="color:#A4A4A4;"><small>8</small></text>

An integer for the tab size for tab expansion.  

_____
## :vector

##### :indent <text style="color:#A4A4A4;"><small>1</small></text>

#### :option-fn-first <text style="color:#A4A4A4;"><small>false</small></text>

Vectors often come with data that needs different formatting than the
default or than the code around them needs.  To support this capability,
you can configure an function as the `:option-fn-first` which will be
given the first element of a vector and may return an options map to be
used to format this vector (and all of the data inside of it).  If this
function returns nil, no change is made.  The function must be a function
of two arguments, the first being the current options map, and the second
being the first element of the vector.

The `:style :keyword-respect-nl` is implemented as an `:option-fn-first`
as follows:

```clojure
:keyword-respect-nl
  {:vector {:option-fn-first #(let [k? (keyword? %2)]
                               (when (not= k? (:respect-nl? (:vector %1)))
                                 {:vector {:respect-nl? k?}}))}},
```

This function will decide whether or not the vector should 
have `:respect-nl? true` used, and then change the options map to have
that value only if necessary.  The "only if necessary" is important,
as every vector will call this function, almost certainly multiple times.
Every time this function returns a non-nil value, that value will be validated
using spec as a valid options map.  Thus, only returning a value when necessary
will mitigate the performance impact of using this capability.

If the options map returned by the function is not valid, an exception will
be thrown.  For example:

```clojure
(zprint-str "[:g :f :d :e :e \n :t :r :a :b]" 
            {:parse-string? true 
             :vector {:option-fn-first 
                       #(do %1 %2 {:vector {:sort? true}})}})

Exception Options resulting from :vector :option-fn-first called with :g 
had these errors: In the key-sequence [:vector :sort?] the key :sort? was 
not recognized as valid!  zprint.zprint/internal-validate (zprint.cljc:2381)
```

#### :respect-nl? <text style="color:#A4A4A4;"><small>false</small></text>

Normally, zprint ignores all newlines when formatting.  However, sometimes
people will hand-format vectors to make them more understandable.  This
shows up with hiccup and rum html data, for instance.  If you enable
`:respect-nl?`, then newlines in a vector will be triggers to move to the
next line instead of filling out the current line because the vector is
wrapping the contents.  The newline will come with the proper indent.

```clojure
(require '[zprint.core :as zp])

(zp/zprint "[:a :b :c :d \n :e :f :g :h]" {:parse-string? true})

[:a :b :c :d :e :f :g :h]

(zp/zprint "[:a :b :c :d \n :e :f :g :h]" {:parse-string? true :vector {:respect-nl? true}})

[:a :b :c :d
 :e :f :g :h]

(zp/zprint "[:a :b [:c :d \n :e] :f :g :h]" {:parse-string? true :vector {:respect-nl? true}})

[:a :b
 [:c :d
  :e] :f :g :h]

(zp/zprint "[:a :b [:c :d \n :e] :f :g :h]" {:parse-string? true :vector {:respect-nl? true :wrap-after-multi? false}})

[:a :b
 [:c :d
  :e]
 :f :g :h]
```
This is usually only useful formatting source.  Here is an example using
a `rum` macro (taken from GitHub rum/examples/rum/examples/inputs.cljc):

```clojure

; This is how it looked originally:

(def re1        
"(rum/defc inputs []  
  (let [*ref (atom nil)] 
    [:dl 
      [:dt \"Input\"]  [:dd (reactive-input *ref)] 
      [:dt \"Checks\"] [:dd (checkboxes *ref)] 
      [:dt \"Radio\"]  [:dd (radio *ref)] 
      [:dt \"Select\"] [:dd (select *ref)] 
      [:dt (value *ref)] [:dd (shuffle-button *ref)]]))")

; A rather unlovely transformation...

(zprint re1 {:parse-string? true})
(rum/defc inputs
  []
  (let [*ref (atom nil)]
    [:dl [:dt "Input"] [:dd (reactive-input *ref)] [:dt "Checks"]
     [:dd (checkboxes *ref)] [:dt "Radio"] [:dd (radio *ref)] [:dt "Select"]
     [:dd (select *ref)] [:dt (value *ref)] [:dd (shuffle-button *ref)]]))

; With :respect-nl? true, this is a lot better

(zprint re1 {:parse-string? true :vector {:respect-nl? true}})
(rum/defc inputs
  []
  (let [*ref (atom nil)]
    [:dl
     [:dt "Input"] [:dd (reactive-input *ref)]
     [:dt "Checks"] [:dd (checkboxes *ref)]
     [:dt "Radio"] [:dd (radio *ref)]
     [:dt "Select"] [:dd (select *ref)]
     [:dt (value *ref)] [:dd (shuffle-button *ref)]]))
```

Enabling this globally may cause argument vectors to become strangely
formatted.  The simple answer is to use `:style :keyword-respect-nl` which
will cause only vectors whose first element is a keyword to be formatted with
`:respect-nl? true`. The
more complex answer is to employ `:option-fn-first`, above (which is what
`:style :keyword-respect-nl` does).

#### :wrap? <text style="color:#A4A4A4;"><small>true</small></text>

Should it wrap its contents, or just list each on a separate line
if they don't all fit on one line?

Vectors wrap their contents, as distinct from maps and lists,
which use hang or flow.  Wrapping means that they will fill out
a line and then continue on the next line.

```clojure
(require '[zprint.core :as zp])

(zp/zprint (vec (range 60)) 70)

[0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25
 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48
 49 50 51 52 53 54 55 56 57 58 59]
```


#### :wrap-coll? <text style="color:#A4A4A4;"><small>true</small></text>

If there is a collection in the vector, should it wrap? 

```clojure
(require '[zprint.core :as zp])

(def v (vec (concat (range 10) '({:key :value "key" "value"}) (range 10 20))))

(zp/zprint v 60)

[0 1 2 3 4 5 6 7 8 9 {:key :value, "key" "value"} 10 11 12
 13 14 15 16 17 18 19]

(zp/zprint v 60 {:vector {:wrap-coll? nil}})

[0
 1
 2
 3
 4
 5
 6
 7
 8
 9
 {:key :value, "key" "value"}
 10
 11
 12
 13
 14
 15
 16
 17
 18
 19]
```
#### :wrap-after-multi? <text style="color:#A4A4A4;"><small>true</small></text>

Should a vector continue to wrap after a multi-line element is 
printed? 

```clojure
(require '[zprint.core :as zp])

(def u (vec (concat (range 10) '({:key :value "key" "value" :stuff 
"the value of stuff is hard to quantify" :bother 
"few people enjoy being bothered"}) (range 10 20))))

(zp/zprint u)

[0 1 2 3 4 5 6 7 8 9
 {:bother "few people enjoy being bothered",
  :key :value,
  :stuff "the value of stuff is hard to quantify",
  "key" "value"} 10 11 12 13 14 15 16 17 18 19]

(zp/zprint u {:vector {:wrap-after-multi? nil}})

[0 1 2 3 4 5 6 7 8 9
 {:bother "few people enjoy being bothered",
  :key :value,
  :stuff "the value of stuff is hard to quantify",
  "key" "value"}
 10 11 12 13 14 15 16 17 18 19
```

______
______
## Experimental Features

The following features are present in the library, but may not be
supported in future versions.  Alternatively, they may be supported
in a very different way in future versions.
_____
#### :auto-width <text style="color:#A4A4A4;"><small>false</small></text>

__DEPRECATED__

This will attempt to determine the width of a terminal window and
set the width to that value.  Seems to work on OS/X, untested on
other platforms.  As of 0.3.0, this now requires adding the
additional library: `[table "0.4.0" :exclusions [[org.clojure/clojure]]]`
to the dependencies.  

______
______
## Debugging the configuration

When something is configurable in a lot of different ways, it can
sometimes be challenging to determine just *how* it got configured.
To aid in figuring out how zprint got configured in a particular way,
you can use the special call:

```clojure
(zprint nil :explain)
```

which will output the entire current configuration, and indicate
exactly where each value came from.  If there is no information
about a configuration value, it is the default.  For all values
that have been changed from the default value, the `:explain` output
will show the current value and indicate how this value was set.
Calls to set-options! are numbered.  For example, if the call is
made:

```clojure
(require '[zprint.core :as zp])

(zp/set-options! {:map {:indent 0}})

(zp/czprint nil :explain)

...
:map {:comma? true,
      :force-nl? nil,
      :hang-diff 1,
      :hang-expand 1000.0,
      :hang? true,
      :indent {:set-by "set-options! call 3", :value 0},
      :key-order nil,
      :sort-in-code? nil,
      :sort? true},
...
```

You can see from this fragment of the output that the indent for
a map has been changed to `0` by a call to `set-options!`.

This will distinguish values set by the `.zprintrc` from values set
by environment variables from values set by Java properties, so you
can more easily determine where a particular value came from if you
wish.

At any time, the `(zprint nil :explain)` or `(czprint nil :explain)`
will show you the entire current configuration of zprint, allowing
you to see all of the default values or any changes that have been
made to them.  Anything you can see with the `:explain` option can
be changed by set-options! or by any of the other configuration
approaches.

## License

Copyright © 2016-2018 Kim Kinnear

Distributed under the MIT License.  See the file LICENSE for details.

