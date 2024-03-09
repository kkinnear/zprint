# zprint Reference

__zprint__ is a library and command line tool providing a variety
of pretty printing capabilities for both Clojure code and Clojure/EDN
structures.  It can meet almost anyone's needs.  As such, it supports
the a variety of major source code formattng approaches.

## Table of Contents

  * [What does zprint do?](#what-does-zprint-do)
  * [See zprint formatting](#see-zprint-formatting)
  * [Features](#features)
  * The zprint [API](#api)
  * Configuration
    * [ Configuration uses an options map](#configuration-uses-an-options-map)
    * [ Where to put an options map](#where-to-put-an-options-map)
    * [ __Simplified Configuration__ -- using `:style`](#style-and-style-map)
      * [  Respect blank lines](#respect-bl)
      * [  Indent Only](#indent-only)
      * [  Format using "community" standards](#community)
      * [  Respect all newlines](#respect-nl)
      * [  Detect and format hiccup vectors](#hiccup)
      * [  Justify all pairs](#justified)
      * [  Backtranslate `quote`, `deref`, `var`, `unquote` in structures](#backtranslate)
      * [  Detect keywords in vectors, if found respect newlines](#keyword-respect-nl)
      * [  Sort dependencies in project.clj](#sort-dependencies)
      * [  Support "How to ns"](#how-to-ns)
      * [  Add newlines between pairs in `let` binding vectors](#map-nl-pair-nl-binding-nl)
      * [  Add newlines between `cond`, `assoc` pairs](#map-nl-pair-nl-binding-nl)
      * [  Add newlines between extend clauses](#extend-nl)
      * [  Add newlines between map pairs](#map-nl-pair-nl-binding-nl)
      * [  Prefer hangs and improve performance for deeply nested code and data](#fast-hang)
    * [ Options map format](#options-map-format)
      * [  Option Validation](#option-validation)
      * [  What is Configurable](#what-is-configurable)
	* [   Generalized Capabilities](#generalized-capabilites)
	* [   Syntax Coloring](#syntax-coloring)
	* [   Function Classification for Pretty Printing](#function-classification-for-pretty-printing)
	  * [    Changing or Adding Function Classifications](#changing-or-adding-function-classifications)
	  * [    Replacing functions with reader-macros](#replacing-functions-with-reader-macros)
	  * [    Controlling single and multi-line output](#controlling-single-and-multi-line-output)
	  * [    A note about two-up printing](#a-note-about-two-up-printing)
	  * [    A note on justifying two-up printing](#a-note-on-justifying-two-up-printing)
    * [ Formatting large or deep collections](#formatting-large-or-deep-collections)
    * [ Widely Used Configuration Parameters](#widely-used-configuration-parameters)
    * [ Configuring functions to make formatting changes based on content](#configuring-functions-to-make-formatting-changes-based-on-content)
    * [ __Configurable Elements__](#configurable-elements)
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
      * [:meta](#meta)
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
      * [:vector-fn](#vector-fn)


## What does zprint do?

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

## See zprint formatting:

  * [__classic zprint__](./types/classic.md) -- ignores whitespace 
  in function definitions and formats code with a variety of heuristics 
  to look as good as hand-formatted code 
  ([_see examples_](./types/classic.md))
  * [__respect blank lines__](./types/respectbl.md) -- similar to 
  classic zprint, but blank lines inside of function defintions are retained, 
  while code is otherwise formatted to look beautiful
  ([_see examples_](./types/respectbl.md))
  * [__indent only__](./types/indentonly.md) -- very different from 
  classic zprint -- no code ever changes lines, it is only correctly 
  indented on whatever line it was already on
  ([_see examples_](./types/indentonly.md))

In addition, zprint is very handy [__to use at the REPL__](./types/repl.md).


## Features

In addition to meeting the goals listed above, zprint has the
following specific features:

* Runs fast enough to be used as a filter while in an editor
* Prints function definitions at the Clojure repl (including clojure.core functions - `czprint-fn`
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
* Optionally preserve all incoming newlines in source
* Optionally properly indent each line, and don't add or remove any newlines

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

Note that classic zprint completely ignores all whitespace and line
breaks in the function definition -- the formatting above is entirely
independent of the source of the function.  When using the zprint
binaries or `lein-zprint` to format source files, whitespace in the
file between function definitions is always preserved.

Zprint has two fundemental regimes -- formatting s-expressions, or parsing
a string and formatting the results of the parsing.  When the `-fn` versions
of the API are used, zprint acquires the source of the function, parses it,
and formats the result at the repl.

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

* [Configuration uses an options map](#configuration-uses-an-options-map)
* [Where to put an options map](#where-to-put-an-options-map)
* [Options map format](#options-map-format)
  * [ Option Validation](#option-validation)
  * [ What is Configurable](#what-is-configurable)
    * [  Generalized Capabilities](#generalized-capabilites)
    * [  Syntax Coloring](#syntax-coloring)
    * [  Function Classification for Pretty Printing](#function-classification-for-pretty-printing)
      * [   Changing or Adding Function Classifications](#changing-or-adding-function-classifications)
      * [   A note about two-up printing](#a-note-about-two-up-printing)
      * [   A note on justifying two-up printing](#a-note-on-justifying-two-up-printing)
  * [ Widely Used Configuration Parameters](#widely-used-configuration-parameters)
  * [ Configuring functions to make formatting changes based on content](#configuring-functions-to-make-formatting-changes-based-on-content)
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
  * [:meta](#meta)
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
  * [:vector-fn](#vector-fn)

## Configuration uses an Options map

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

You can also use `(zprint nil :explain-set)` or `(czprint nil :explain-set)`
to see just the things that have non-default values, as the entire
`:explain` output can be a bit much.

The options map has a few top level configuration options, but
much of the configuration is grouped into sub-maps.  The top level
of the options map looks like this:

```clojure

{:agent {...},
 :array {...},
 :atom {...},
 :binding {...},
 :color-map {...},
 :color false,
 :comment {...},
 :cwd-zprintrc? false,
 :delay {...},
 :extend {...},
 :fn-map {...},
 :fn-obj {...},
 :future {...},
 :list {...},
 :map {...},
 :max-depth 1000,
 :max-length 1000,
 :meta {...},
 :object {...},
 :pair {...},
 :pair-fn {...},
 :parse-string? false,
 :promise {...},
 :reader-cond {...},
 :record {...},
 :search-config? false,
 :set {...},
 :style nil,
 :style-map {...},
 :tab {...},
 :uneval {...},
 :vector {...},
 :width 80,
 :zipper? false}
```

## Where to put an options map

When zprint (or one of the zprint-filters) is called for the first
time it will configure itself from all of the information that it
has available at that time.  It will examine the following information
in order to configure itself:

* The file `$HOME/.zprintrc` or if that file does not exist, the  file
  `$HOME/.zprint.edn` for an options map in EDN format.
* If the file found above or the options map on the command line has 
  `:search-config?` true, it will look
  in the current directory for a file `.zprintrc` and if it doesn't find
  one, it will look for `.zprint.edn`.l  If it doesn't find either of them,
  it will look in the parent of the current directory for the same two files,
  in the same order.  This process will stop when it finds a file or it
  reaches the root of the file system.  If the file it finds is in the
  home directory (that is, it is the same file found in the first step
  in this process, above), it will read the file but it will not use the
  results (because it has already done so).
* If the file found in the home directory or the options map on the 
  command line has `:cwd-zprintrc?` set to true,
  and did not have `:search-config?` set to true, then it will search
  the current directory for `.zprintrc` and `.zprint.edn` in that order,
  and use the information from the first file it finds.

The `{:search-config? true}` capability is designed to allow projects
to have zprint configuration files in various places in the project
structure.  There might be a zprint configuration file at the root
of a project, which would be used for every source file in the project.

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
what appears in any of the external configuration areas.  This would be
of value to anyone using the zprint library to format something a particular
way which they didn't want to be affected by an individual's personal
configuration.

You can add configuration information by:

* Calling `set-options!` with an options map, which is saved in the internal
options map across calls
* Specifing an options map on any call to zprint, which only affects that call
to `zprint` or `czprint`


## Options map format

### .zprintrc or .zprint.edn

The `.zprintrc` file contain a sparse options map in EDN format (see below).
That is to say, you only specify the elements that you wish to alter in
a `.zprintrc` file.  Thus, to change the indent for a map to be 0, you
would have a `.zprintrc` file as follows:

```clojure
{:map {:indent 0}}
```

zprint will configure itself from at most two EDN files containing an options
map:

  * `$HOME/.zprintrc` or `$HOME/.zprint.edn` which is always read
  * `.zprintrc` or `.zprint.edn` in the current working directory or its
  parents, up to the root of the file system if the configuration file in
  `$HOME` has `:search-config?` set to `true`.
  * `.zprintrc` or `.zprint.edn` in the current working directory,
  which is only read if the configuration file in `$HOME` has
  `{:cwd-zprintrc? true}` and does not have `{:search-config? true}`
  in its options map

Note that these files are only read and converted when zprint initially
configures itself, which is at the first use of a zprint or czprint
function.  You can invoke `configure-all!` at any later time which
will cause all of the external forms of configuration (e.g. .zprintrc,
environment variables, and Java system properties) to be read
and converted again.


### set-options!

You call `set-options!` with an EDN map of the specific key-value pairs
that you want changed from the current values.  This is useful both
when using zprint at the REPL, as well as when you are using to output
information from a program that wants to configure zprint
in some particular way.  For example:

```clojure
(require '[zprint.core :as zp])

(zp/set-options! {:map {:indent 0}})
```

You can also call `set-options!` with a string value of an options map,
and zprint will convert that string into an options map using the
Small Clojure Interpreter (sci) linked into zprint.  In this way,
you can safely import configurations from external files and be sure
that they won't define any functions that might do dangerous things
to your system or environment.  Only `set-options!` provides
this string capability -- you cannot do this on individual function
calls to zprint to actually format something.

### Options map on an individual call

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
### Configuring functions in an options map

There are several keys whose values must be functions, in order to
allow complex analysis of the structure or code to be formatted.

When configuring function in files, use the `(fn [x y] ...)` form of 
definition as opposed to the `#(...)` reader-macro form.

You can also call `set-options!` with a string value of a options map,
and zprint will convert that string into an options map using the
Small Clojure Interpreter (sci) linked into zprint.  In this way,
you can safely import configurations from external files and be sure
that they won't define any functions that might do dangerous things
to your system or environment.  Only `set-options!` provides
this string capability -- you cannot do this on individual function
calls to zprint to actually format something.

### Option Validation

All changes to the options map are validated to some degree for
correctness.  When the change to the internal options map is itself
a map, when using the `.zprintrc` file, calling `(set-options! ...)`,
or specifying an options map on an individual call, every key in
the options map is validated, and some level of at least type
validation on the values is also performed.  Thus:

```clojure
(czprint nil {:map {:hang false}})
Exception Option errors in this call: In the key-sequence [:map :hang] the key :hang was not recognized as valid!  zprint.core/determine-options (core.cljc:415)

```

This call will fail validation because there is no `:hang` key in `:map`.  The
"?" is missing from `:hang?`.  

All option validation errors must be fixed, or zprint will not operate.

## What is Configurable

The following categores of information are configurable:

* generalized capabilities
* syntax coloring
* function classification for pretty printing
* specific option values for maps, lists, vectors, pairs, bindings, arrays, etc.

### Generalized Capabilites

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

Note that `zprint-file-str`, the routine used for all of the standalone
zprint binaries, uses `:parse-string-all`.

#### :parallel?

As of 0.3.0, on Clojure zprint will use mutiple threads in several
ways, including `pmap`.   By default, if used as a library in a program,
it will not use any parallel features because if it does, your program
will not exit unless you call `(shutdown-agents)`.  When zprint is
running at the REPL, it __will__ enable parallel features as this
doesn't turn into a problem when exiting the REPL.

In the event that you have configured `{:parallel? false}` in any
of the various `.zprintrc` files, it will not be enabled when running
at the REPL.

If you want it to run more quickly when embedded in a program,
certainly you should set :parallel? to true -- but don't forget to
call `(shutdown-agents)` at the end of your program or your program
won't exit!

#### :coerce-to-false 

__Experimental__ 

This experimental capability exists in order to allow specification
of option maps for remote invocations of zprint, where boolean `true`
and `false` in the  options maps turn into something other than `true`
and `false` when zprint is invoked remotely.  For instance, if `true`
becomes `1` in the remove invocation, and `false` becomes `0`, it would
be impossible to invoke zprint with a reasaonable options map, since
both `1 `and `0` would fail to validate as they are not boolean.  Relaxing
the validation rules would not help, as `0` is never going to be `false`
for Clojure.

In this (very rare) case you could set `:coerce-to-false` to the
value that you want to be `false`.  If you do this the options map you specify
will be searched for all values which must be boolean.  If they are
already boolean (i.e., already `true` or `false`), they are not changed.
If they are not boolean, then if they equal the value of `:coerce-to-false`,
they will be set to `false`, and otherwise they will be set to `true`.

In the example above, `{:coerce-to-false 0}` would correctly set the
various boolean values.

You may rely on this capability not going away as long as you let me
know that you are using it by opening an issue. 

#### :cache

In cases where zprint needs to cache some value, the following keys indicate
a directory where all cached data will reside:

##### :location _"HOME"_

If this does not appear, the location is the home directory ".".  If this
does appear, it must be a string and it will either be considered an
environment variable or a Java system property.  If the string contains
a ".", it will be considered a Java system property and will be looked 
resolved in that fashion, and if it does not contain a ".", it will be 
considered an environment variable and resolved in that fashion.

##### :directory _".zprint"_

This is the directory in which the various aspects of the cache will
reside.  This directory is used or created in the :location (see immediately
above).  Typically this directory would start with a "." so that it would
not normally be visible.  The default is ".zprint".

#### :url

The only things currently cached are the results of URL lookups of option
maps used for configuration.  These lookups are triggered by the `--url`
or `--url-only` switches on the uberjar and graalvm binaries.  There are
two values associated with cacheing of URL lookups.

##### :cache-secs _300_

This the time that for which the result of a URL lookup is cached.  

##### :cache-dir _"urlcache"_

This the name of the directory in which the cached URL results are held.  This
directory is itself located in the [:cache :directory] value described
immediately above.

#### :color?

As of zprint 0.5.1, the `:color?` option key will control whether
or not the output is produced with ANSI escape sequences based on the
`:color-map` option key.  The functions `czprint` and its variants `czprint-*`
essentially simply set `{:color? true}`.

The `:color?` key also controls `zprint-file-str`, which is used inside
of the uberjar and the graalVM binaries that are distributed, and so
you can specify `{:color? true}` in an options map included on the command
line of these utilities to get colorized output.  This output will only be
useful when displayed on a "terminal" which interprets ANSI escapse sequences.

You will want to avoid setting `{:color? true}` in a `$HOME/.zprintrc` file,
as then all of the files produced by the uberjar and graalVM binaries would
always contain ANSI escapse sequences!

#### :modify-sexpr-by-type

Clojure can operate on Java classe like `java.util.ArrayList` and there are
even classes that are native to Clojure that have no syntax for creating
them or printing them, such as `clojure.lang.PersistentQueue`.

When encountering some of these classes at the REPL, zprint will typically
return the `toString` value of these classes, which in many cases is useful
in that it gives you the value of the class.  However, if the class has even a
bit of real data in it, the string can be very long and rarely makes working
with it at the REPL clear and easy.

The `:modify-sexpr-by-type` capability allows you to specify two things
when a particular class is encountered while formatting a structure at
the REPL.  These two things appear in a vector.  The first element of 
this vector is a function which will accept the object and return a replacement
object to be used when it is formatted.  The second element of the vector
is an options map to be used whenever this type is encountered.  

Some examples: 

```
; Create a java.util.ArrayList, and put something in it...

zprint.core=> (def alist (java.util.ArrayList.))
#'zprint.core/alist
zprint.core=> (dotimes [_ 100] (.add alist "SOME STRING"))
nil

; How does it show up?

zprint.core=> (czprint alist)
["SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"]
nil

; Not great.  You can see what is in it, but it doesn't really format
; well at the REPL.

; But if we turn it into a vector, it works just fine:

zprint.core=>
(czprint alist
         {:modify-sexpr-by-type {"java.util.ArrayList" [(fn [options sexpr]
                                                          (vec sexpr)) nil]}})
["SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"]
nil

; If we make it more complex, the value increases

zprint.core=> (def mal {:this :is :a :test :stuff alist})
#'zprint.core/mal
zprint.core=> (czprint mal)
{:a :test,
 :stuff
   ["SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"],
 :this :is}
nil

; Again, turn it into a vector, and you can see the structure so much
; more clearly.

zprint.core=> (czprint mal {:modify-sexpr-by-type {"java.util.ArrayList" [(fn [options sexpr] (vec sexpr)) nil]}})
{:a :test,
 :stuff
   ["SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
    "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"],
 :this :is}
```

NOTE: The use of `vec` in the above example works because `vec` will
take all of the elements out of its single arguement and make a new
vector out of those elements.  Which is exactly what you want.

Were you to use `vector`, however, the results wouldn't be so nice:

```
zprint.core=> (czprint alist {:modify-sexpr-by-type {"java.util.ArrayList" [(fn [options sexpr] (vector sexpr)) nil]}})
Execution error (StackOverflowError) at zprint.zprint/fzprint* (zprint.cljc:8651).
null
```

There is a stack overflow, because the function `vector` creates a vector
out of all of its arguments.  In this case, there is one argument, the
`java.util.ArrayList`, and so it wraps a vector around the 
`java.util.ArrayList` and then formats the vector.  Then it encounters the
`java.util.ArrayList`, and wraps a vector around it, and then formats
that vector.  Then it encounters the `java.util.ArrayList` and then ...
it goes on until the stack overflows.

You can use the function `vector`, but you need to use `apply` so that
it will be applied to all of the elements in the `java.util.ArrayList`, like
this:

```
zprint.core=> (czprint alist {:modify-sexpr-by-type {"java.util.ArrayList" [(fn [options sexpr] (apply vector sexpr)) nil]}})
["SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"
 "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING" "SOME STRING"]
nil
```

The same goes for functions like `list`.

In addition to turning one type into another, you can also affect the
options map with the second element of the vector which is the value of
the type of the object.  Using the (rather little known) option of
supplying a new "l-str" and "r-str" (which are the beginning and
end of a collection internal to zprint), you can enhance zprint to
format data types that have no standard Clojure printed format.

Take, for example, the `clojure.lang.PersistentQueue` data type.
It exists, it can be used, but there is no standard syntax to create
or print a `clojure.lang.PersistentQueue`.  For example:

```
; Create a clojure.lang.PersistentQueue and put something in it.

% (def pq clojure.lang.PersistentQueue/EMPTY)
#'zprint.core/pq
% (def pqm (-> pq (conj :a) (conj :b)))
#'zprint.core/pqm

; What have we got?

% pqm
#object[clojure.lang.PersistentQueue 0x6d05ca59 "clojure.lang.PersistentQueue@8de188a4"]

; Not clear what we've got

: What does zprint do?

% (czprint pqm)
(:a :b)

; Well, we can see the contents, which is good, but is sure looks like
; a list.

; What about clojure.pprint/pprint?

% (clojure.pprint/pprint pqm)
<-(:a :b)-<

; Wow, that's different.  clojure.pprint must special case 
; clojure.lang.PersistentQueue!

; zprint will do that too, with a bit of work...

(czprint pqm
         {:modify-sexpr-by-type
            {"clojure.lang.PersistentQueue"
               [(fn [options zloc] (apply list zloc))
                {:list {:option-fn (fn [options n sexpr]
                                     {:new-l-str "<-(", :new-r-str ")-<"}),
                        :wrap? true,
                        :indent 1},
                 :next-inner-restore [[:list :option-fn] [:list :wrap?]
                                      [:list :indent]]}]}})
<-(:a :b)-<
```

This `czprint` call is modifying the `clojure.lang.PersistentQueue` into a
list.  Note the use of `apply`, as discussed above.  Without using `apply`,
you will overflow the stack.  See the NOTE a few paragraphs previous.

In this example, there is also the specification of an `option-fn`
to be called on every list that is subsequently encountered.  That
`option-fn` is specifying a new `l-str` and `r-str`, which are the
beginning and end of the list.  It is also telling the list formatting
routine `:wrap? true`, which means to fill things out to the
right margin and not to treat a list as a function call.  Then the
`:next-inner-restore` specifies the things that were changed to be
put back as they were so that any list inside of the
`clojure.lang.PersistentQueue` is not formatted like the
`clojure.lang.PersistentQueue`.

The `:indent 1` and `:wrap? true` are illustrated by this example:

```
zprint.core=> (czprint clpq {:modify-sexpr-by-type {"clojure.lang.PersistentQueue" [(fn [options zloc] (apply list zloc)) {:list {:option-fn (fn [options n sexpr] {:new-l-str "<-(" :new-r-str ")-<"}) :wrap? true :indent 1} :next-inner-restore [[:list :option-fn] [:list :wrap?] [:list :indent]]}]}})
{:a :test,
 :only :a,
 :test :how,
 :this :is,
 :this2 :is,
 :this3 :work,
 <-(:aaaaaaaaaa :bbbbbbbbb :ccccccccc :ddddddddddd :eeeeeeeeeeee :fffffffff
    :gggggggggg)-<
   :does}
nil
```

You can see that the elements of the list simply fill out the line until
the `:width` of the output, and then it starts (properly indented) on the
next line.

This next example shows a justified map, with the justification working
correctly with the `clojure.lang.PersistentQueue`:

```
zprint.core=> (czprint clpqs {:modify-sexpr-by-type {"clojure.lang.PersistentQueue" [(fn [options zloc] (apply list zloc)) {:list {:option-fn (fn [options n sexpr] {:new-l-str "<-(" :new-r-str ")-<"}) :wrap? true :indent 1} :next-inner-restore [[:list :option-fn] [:list :wrap?] [:list :indent]]}]} :style :justified})
{:addddddddddddddddddddd      :test,
 :onlybbbbbbbbbbbbbbbbb       :a,
 :testaaaaaaaaaaaaaaaa        :how,
 :this2ccccccccccccccccccc    :is,
 :this3ffffffffffffffffff     :work,
 :thiseeeeeeeeeeeeeeeeeeeeee  :is,
 <-(:aaaaaaaaaa :bbbbbbbbb)-< :does}
nil
```

You can even change the color of the output of the beginning and ending
of the `clojure.lang.PersistentQueue` with a little more work:

```
(czprint clpq
         {:modify-sexpr-by-type
            {"clojure.lang.PersistentQueue"
               [(fn [options zloc] (apply list zloc))
                {:list {:option-fn (fn [options n sexpr]
                                     {:new-l-str "<-(", :new-r-str ")-<"}),
                        :wrap? true,
                        :indent 1},
                 :color-map {:left :cyan, :right :cyan},
                 :next-inner-restore [[:list :option-fn] [:list :wrap?]
                                      [:list :indent] [:color-map :left]
                                      [:color-map :right]]}]}})
{:a :test,
 :only :a,
 :test :how,
 :this :is,
 :this2 :is,
 :this3 :work,
 <-(:aaaaaaaaaa :bbbbbbbbb :ccccccccc :ddddddddddd :eeeeeeeeeeee :fffffffff
    :gggggggggg)-<
   :does}
```

You can't see the colors in the output in the documentation, but
the "<-(" and ")-<" are cyan in the REPL.  This is because of the `{:color-map {:left :cyan :right :cyan}}` in the options map.  If the characters in the
`new-l-str` or `new-z-str` are not recognized as "normal" characters in the
color map (i.e., :paren, :bracket, :brace, etc.), then if they are
the `:left` or `:right` of a collection, then the color map values for
`:left` and `:right` are used.  There are no useful defaults for `:left`
and `:right` colors, so you would need to set them yourself.

### Syntax coloring

Zprint will colorize the output when the czprint and czprint-fn calls
are used.  It is limited to the colors available on an ANSI terminal.
You can get the same output by adding the `{:color? true}` option to
any call to zprint or zprint-fn.

Note that `{:color? true}` will also
affect any uberjar or zprint-filter invocations as well, so you probably
want to avoid placing `{:color? true}` in your `$HOME/.zprintrc` file,
as it will cause the files produced to contain ANSI escape sequences.


The key :color-map contains by default:

```
:color-map {:brace :red,
            :bracket :purple,
            :char :black,      ; Note Clojurescript difference below!
            :comma :none,
            :comment :green,
            :deref :red,
            :false :black,
            :fn :blue,
            :hash-brace :red,
            :hash-paren :green,
            :keyword :magenta,
	    :left :none,
            :nil :yellow,
            :none :black,
            :number :purple,
            :paren :green,
            :quote :red,
            :regex :black,
	    :right :none,
            :string :red,
            :symbol :black,
            :syntax-quote-paren :red
            :true :black,
            :uneval :magenta,
            :user-fn :black},
```
Note that in Clojurescript, you cannot set a unique `:char` color value,
as things that return true from`(char? ...)` also return true from
`(string? ...)`, since in Clojurescript chars are simply single character 
strings. Due to this difference, the color value for `:string` takes 
precedence.

You can change any of these to any other available value.  

For example:
```
(czprint-fn defn {:color-map {:paren :black}})
```

You can include multiple ANSI sequences together:
```
(czprint-fn defn {:color-map {:none [:bright-black :italic]}})
```
The
available values are (including their ANSI codes, for reference):

```
  {:off 0,
   :reset 0,
   :bold 1,
   :faint 2,
   :italic 3,
   :underline 4,
   :blink 5,
   :reverse 7,
   :hidden 8,
   :strike 9,
   :normal 22,
   :italic-off 23,
   :underline-off 24,
   :blink-off 25,
   :reverse-off 27,
   :hidden-off 28,
   :strike-off 29,
   :black 30,
   :none 30,
   :red 31,
   :green 32,  
   :yellow 33, 
   :blue 34,   
   :magenta 35,
   :purple 35,
   :cyan 36, 
   :white 37,
   :xsf 38,
   :back-black 40,  
   :back-red 41,    
   :back-green 42,  
   :back-yellow 43, 
   :back-blue 44,    
   :back-magenta 45, 
   :back-purple 45,
   :back-cyan 46,
   :back-white 47,
   :bright-black 90,
   :bright-red 91,
   :bright-green 92,
   :bright-yellow 93,
   :bright-blue 94,
   :bright-magenta 95,
   :bright-purple 95,
   :bright-cyan 96,
   :bright-white 97,
   :back-bright-black 100,
   :back-bright-red 101,
   :back-bright-green 102,
   :back-bright-yellow 103,
   :back-bright-blue 104,
   :back-bright-magenta 105,
   :back-bright-purple 105,
   :back-bright-cyan 106,
   :back-bright-white 107}
```

There is also a different color map for unevaluated items,
i.e. those prefaced with #_ and ignored by the Clojure reader.
This is the default :uneval color map:

```
:uneval {:color-map {:brace :yellow,
                     :bracket :yellow,
                     :char :magenta,  ; not available in Clojurescript, see above
                     :comma :none,
                     :comment :green,
                     :deref :yellow,
                     :false :yellow,
                     :fn :cyan,
                     :hash-brace :yellow,
                     :hash-paren :yellow,
                     :keyword :yellow,
		     :left :none,
                     :nil :yellow,
                     :none :yellow,
                     :number :yellow,
                     :paren :yellow,
                     :quote :yellow,
                     :regex :yellow,
		     :right :none,
                     :string :yellow,
                     :symbol :cyan,
                     :syntax-quote-paren :yellow,
                     :true :yellow,
                     :uneval :magenta,
                     :user-fn :cyan}},
```

You can also change these to any of the colors specified above.

Note that in this documentation, the syntax coloring of Clojure code is
that provided by the GitHub flavored markdown, and not zprint!

There is a style, `:dark-color-map` which sets both the `:color-map` and
the `:uneval {:color-map ...}` to colors which are visible when using
a dark background.  These may not be your favorite color choices, but at
least things should be visible, allowing you to fine-tune the colors to
better meet your preferences.

### Function Classification for Pretty Printing

While most functions will pretty print without special processing,
some functions are more clearly comprehended when processed specially for
pretty printing.  Generally, if a function call fits on the current
line, none of these classifications matter.  These only come into play
when the function call doesn't fit on the current line, although
you can define a function type to never allow it to formatted on
a single line.  The following
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

There are three possible types values for a key in the `:fn-map`:

  1. A function type (see below for the list of possible function types).

  2. A vector, containing a function type and an options map to use
  whenever this function is formatted.

  3. A string, which is the name of another function in the `:fn-map`.
  In this case, the value of that string will be used.  These can
  chain up to any (reasonable) level, but no circular references
  are allowed and will throw an exception if encountered.


The available classifications (i.e. function types) are:

#### :arg1

Print the first argument on the same line as the function, if
possible.  Later arguments are indented the amount specified by
`:list {:indent-arg n}`, or `:list {:indent n}` if `:indent-arg`
is not specified.

```clojure
 (apply str
   "prepend this one" (generate-strings from arguments))
```

#### :arg1-body

Print the first argument on the same line as the function, if
possible.  Later arguments are indented the amount specified by
`:list {:indent n}`.

```clojure
 (if (= a 1)
   (map inc coll) (map dec coll))
``` #### :arg1-pair

The function has an important first argument, then the rest of the
arguments are paired up. Leftmost part of the pair is indented by
`:list {:indent-arg n}` if it is specified, and `:list {:indent n}`
if it is not.

```clojure
 (assoc my-map
   :key1 :val1 :key2 :val2)
``` #### :arg1-pair-body

The function has an important first argument, then the rest of the
arguments are paired up.  The leftmost part of the pair is indented
by the amount specified by `:list {:indent n}`.

```clojure
 (case fn-style
   :arg1 nil :arg1-pair :pair :arg1-extend :extend :arg2 :arg1
   :arg2-pair :arg1-pair fn-style)
```

#### :arg1-force-nl

This is like `:arg1`, but since it appears in `:fn-force-nl`, it
will never print on one line even if it would otherwise fit.

#### :arg1-mixin

Print Rum `defc`, `defcc`, and `defcs` macros in a standard way.
Puts the mixins under the first line, and above the argument vector.
Does not require `<`, will operate properly with any element in
that position. Allows but does not require a docstring.

```clojure (rum/defcs component
  "This is a docstring for the component." < rum/static
    rum/reactive (rum/local 0 ::count) (rum/local "" ::text)
  [state label] (let [count-atom (::count state)
	text-atom (::text state)]
    [:div]))
```

#### :arg2

Print the first argument on the same line as the function name if
it will fit on the same line. If it does, print the second argument
on the same line as the first argument if it fits. Indentation of
later arguments is controlled by `:list {:indent n}`

```clojure
  (as-> initial-value tag
    (process stuff tag bother) (more-process tag foo bar))
```

Note: This is implemented as a "body" function, as if it were
`:arg2-body`.

#### :arg2-pair

Just like :arg2, but prints the third through last arguments as
pairs.  Indentation of the leftmost elements of the pairs is
controlled by `:list {:indent n}`.  If any of the rightmost elements
end up not fitting or not hanging well, the flow indent is controlled
by `:pair {:indent n}`.

```clojure
  (condp = stuff
    :bother "bother" :foo "foo" :bar "bar" "baz")
``` Note: This is implemented as a "body" function, as if it were
`:arg2-pair-body`.


#### :arg2-fn

Just like :arg2, but prints the third through last arguments as
functions.

```clojure
  (proxy [Classname] []
    (stuff [] bother) (foo [bar] baz))
```

Note: This is implemented as a "body" function, as if it were
`:arg2-fn-body`.

#### :arg2-extend-body

For things like `deftype` and `defrecord`, where the body of the 
form is like `extend`, but the first two arguments are important.

#### :arg2-force-nl, arg2-force-nl-body

Just like `:arg2`, but never print on one line.  Both argument and
body types.

#### :binding _(function type)_

The function has a binding clause as its first argument.  Print the
binding clause two-up (as pairs)  The indent for any wrapped binding
element is :binding `{:indent n}`, the indent for the functions
executed after the binding is `:list {:indent n}`.

```clojure
 (let [first val1
       second
	 (calculate second using a lot of arguments)
       c d]
   (+ a c))
```

#### :pair-fn _(function type)_

The function has a series of clauses which are paired.  Whether or
not the paired clauses use hang or flow with respect to the function
name is controlled by `:pair-fn {:hang? boolean}` and the indent
of the leftmost element is controlled by `:pair-fn {:indent n}`.

The actual formatting of the pairs themselves is controlled by
`:pair`.  The controls for `:pair-fn` govern how to handle the block
of pairs -- whether or not they should be in a hang with respect
to the function name.  The controls for how the elements within the
pairs are printed are governed by `:pair`. For instance, the indent
of any of the rightmost elements of the pair if they don't fit on
the same line or don't hang well is `:pair {:indent n}`.

```clojure
 (cond
   (and (= a 1) (> b 3)) (vector c d e) (= d 4) (inc a))
```

Note that :pair-fn will correctly format pairs where the test is a
keyword and the expression is a vector, as in 'better-cond'.  For
example (drawn from the 'better-cond' readme) this is how zprint
will format the following expression by default:

```clojure (cond
  (odd? a) 1 :let [a (quot a 2)] :when-let [x (fn-which-may-return-falsey
  a)
	     y (fn-which-may-return-falsey (* 2 a))]
  :when-some [b (fn-which-may-return-nil x)
	      c (fn-which-may-return-nil y)]
  :when (seq x) :do (println x) (odd? (+ x y)) 2 3)
```

Every keyword whose symbol appears in the `:fn-map` (and is therefore
likely to be a built-in function) which has a vector following it
will have that vector formatted as a binding vector.  In addition,
every keyword whose symbol appears in the `:fn-map` but does not
have a vector following it, will be formatted in such a way that
the expr after it will be on the same line if at all possible,
regardless of the settings for how to manage pairs.

#### :hang

The function has a series of arguments where it would be nice to
put the first on the same line as the function and then indent the
rest to that level.  This would usually always be nice, but zprint
tries extra hard for these.  The indent when the arguments don't
hang well is `:list {:indent n}`.

```clojure
 (and (= i 1)
      (> (inc j) (stuff k)))
```

#### :extend _(function type)_

The s-expression has a series of symbols with one or more forms
following each.  The level of indent is configurable by `:extend
{:indent n}`.

```clojure
  (reify
    stuff
      (bother [] (println))
    morestuff
      (really [] (print x)) (sure [] (print y)) (more-even [] (print
      z)))
```

#### :arg1-extend

For the several functions which have an single argument prior to
the :extend syntax.  They must have one argument, and if the second
argument is a vector, it is also handled separately from the :extend
syntax.  The level of indent is controlled by `:extend {:indent n}`

```clojure
  (extend-protocol ZprintProtocol
    ZprintType
      (more-stuff [x] (str x)) (more-bother [y] (list y)) (more-foo
      [z] (nil? z))))

  (deftype ZprintType
    [a b c] ZprintProtocol
      (stuff [this x y] a) (bother [this] b) (bother [this x] (list
      x c)) (bother [this x y] (list x y a b)))
```

#### :arg1->

Print the first argument on the same line as the function, if
possible.  Later arguments go indented and `:arg1` and `:arg-1-pair`
top level fns are become `:none` and `:pair`, respectively.

Currently `->` is `:narg1-body`, however, and there are no `:arg1->`
functions.

```clojure
  (-> opts
    (assoc
      :stuff (list "and" "bother")) (dissoc :things))
```

#### :noarg1-body

Print the function in whatever way is possible without special
handling.  However, top level fns become different based on the
lack of their first argument.  Thus, `:arg1` becomes `:none`,
`:arg1-pair` becomes `:pair`, etc.

```clojure
  (-> opts
      (assoc
	:stuff (list "and" "bother"))
      (dissoc :things))
```

#### :force-nl and :force-nl-body

Tag a function which should not format with all of its arguments
on the same line even if they fit.  Note that this function type
has to show up in the set that is the value of :fn-force-nl to have
any effect.

```clojure
  (->> opts
       foo bar baz)
```

#### :fn

Print the first argument on the same line as the `(fn ...)` if it
will fit on the same line. If it does, and the second argument is
a vector, print it on the same line as the first argument if it
fits.  Indentation is controlled by `:list {:indent n}`.

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
has arguments, `:flow-body` assumes that the arguments are body
elements.  The only difference is when there are different indents
for arguments and body elements.  Note that both `:flow` and
`:flow-body` appear in the set `:fn-force-nl`, so that they will
also never print one one line.

```clojure
  (foo
    (bar a b c) (baz d e f))
```

#### :wrap

Output the expression by formatting all of the arguments onto the
same line until that line is full, and then continue placing all
of the arguments on the next line.  This is similar to how vectors
are formatted by default.  Note that the `:indent` for lists is not
changed by this function type.  You may find it useful to set the
`:indent` for `:list` to 1 when using this function type.

```clojure 
{:fn-map {"my-fn" [:wrap {:list {:indent 1}}]}}
```

When using this function type, you may find it useful to use
`{:list {:no-wrap-after #{"stuff"}}}` where `stuff` is some element you 
don't want to ever be the last element on a line.  See `:list :no-wrap-after`
for more details.

#### :list

Output this expression as a list, without inferring that a function
is present as the first element.  While you might use this directly
in the `:fn-map` and associate it with a first list element that is
known to not be a function, there are other options.  You might use
it as the default in the function map, or you might use it as the
output of an `:option-fn`.  In particular, you might use it as the
output of the `:option-fn` `rulesfn`.  See the style `:rules-example`
in this document for details of how to do this.

Some examples:

This is what lists look like normally, if the first thing (the likely
function) isn't recognized in the `:fn-map`:

```
(czprint i283n {:parse-string? true})
(this is
      a
      test
      (this is
            only
            a
            test
            with
            a
            list
            that
            is
            very
            long
            and
            will
            not
            actually
            fit
            on
            one
            line))
```

This is what you get if the first thing isn't assumed to be a function
by using tghe `:list` function type.  Note that the indent was changed
to 1 in this case because otherwise all of the elements after the first
are indented to spaces, which doesn't look all that great:

```
(czprint i283n
         {:parse-string? true, :fn-map {"this" [:list {:list {:indent 1}}]}})
(this
 is
 a
 test
 (this
  is
  only
  a
  test
  with
  a
  list
  that
  is
  very
  long
  and
  will
  not
  actually
  fit
  on
  one
  line))
```

If you were to make this the default (which probably isn't a good
idea in general), this is how you would do it:

```
(czprint i283n
         {:parse-string? true, :fn-map {:default [:list {:list {:indent 1}}]}})
(this
 is
 a
 test
 (this
  is
  only
  a
  test
  with
  a
  list
  that
  is
  very
  long
  and
  will
  not
  actually
  fit
  on
  one
  line))
```

It isn't a good idea to do this all the time because most lists are, 
indeed, Clojure code and have functions as their first elements.  This
is what you might do in a special case in a ;!zprint directive or
as the return from an `:option-fn`.

  

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
on the same line is always `:list {:indent n}`.

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
          "cond" :pair-fn,
	  ...}
```

Note that the function names are strings.  You can add any function
you wish to the :fn-map, and it will be interpreted as described above.

You can also add a key-value pair to the `:fn-map` where the key
is `:default`, and the value will be used for any function which does
not appear in the `:fn-map`, or which does appear in the `:fn-map` but
whose value is `:none`.

You can add a key-value pair to the `:fn-map` where the key
is `:default-not-none`, and the value will be used for any function which does
not appear in the `:fn-map`.  Note that if a function does appear in the
function map and has a value of `:none`, the value of `:default-not-none` 
will __not__ be used!


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
          "cond" :pair-fn,
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

##### Altering the formatting of just the "top level" of a function


You can alter the formatting of just the top level of a function by
resetting some of the configuration when zprint decends one level from
the function in the function map.

For example, say that you wanted to enable `{:list {:respect-nl? true}}`
for the `comment` function, but didn't want that to be in force while the
expressions inside of the comment function were formatted.

Here is the input:

```clojure
(def rnl2x
"(comment
(defn x
  [y]
  (println y))

(this
  is
  a
         thing that is interesting)

(def z


[:this-is-a-test :with-3-blanks-above?])

(def a :more stuff)



(def b :3-blanks-above))")
```
Here is the output when you do nothing special:

```clojure
(zprint rnl2x {:parse-string? true})
(comment (defn x [y] (println y))
         (this is a thing that is interesting)
         (def z [:this-is-a-test :with-3-blanks-above?])
         (def a :more stuff)
         (def b :3-blanks-above))
```
Here is the output when you enable `:list {:respect-nl? true}` for
`comment`:

```clojure
(zprint rnl2x
        {:parse-string? true,
         :fn-map {"comment" [:none {:list {:respect-nl? true}}]}})
(comment
  (defn x
    [y]
    (println y))

  (this
    is
    a
    thing
    that
    is
    interesting)

  (def z


    [:this-is-a-test :with-3-blanks-above?])

  (def a :more stuff)



  (def b :3-blanks-above))
```

Here is the output when you reset the `:respect-nl?` for processing at the
next inner level:

```clojure
(zprint rnl2x
        {:parse-string? true,
         :fn-map {"comment" [:none
                             {:list {:respect-nl? true},
                              :next-inner {:list {:respect-nl? false}}}]}})
(comment
  (defn x [y] (println y))

  (this is a thing that is interesting)

  (def z [:this-is-a-test :with-3-blanks-above?])

  (def a :more stuff)



  (def b :3-blanks-above))
```

You can set keys in the options map to explicit values using `:next-inner`.
You can also reset specific keys to their previous values (without knowing
what those values are) using `:next-inner-restore`.

##### Restoring previous configuration after altering it 

The key `:next-inner-restore` takes a vector of key sequences, and creates
a `:next-inner` value for you which contains the current values of the given
key sequences.  For example, here is `comment` where `:respect-nl?` is true
for all levels inside of the `comment`:

```
(czprint rnl2x
         {:parse-string? true,
          :fn-map {"comment" [:none {:list {:respect-nl? true}}]}})
(comment
  (defn x
    [y]
    (println y))

  (this
    is
    a
    thing
    that
    is
    interesting)

  (def z


    [:this-is-a-test :with-3-blanks-above?])

  (def a :more stuff)



  (def b :3-blanks-above))
```
Here is `comment` where `:respect-nl?` is only true for the top level of the
`comment`:

```
(czprint rnl2x
         {:parse-string? true,
          :fn-map {"comment" [:none
                              {:list {:respect-nl? true},
                               :next-inner-restore [[:list :respect-nl?]]}]}})
(comment
  (defn x [y] (println y))

  (this is a thing that is interesting)

  (def z [:this-is-a-test :with-3-blanks-above?])

  (def a :more stuff)



  (def b :3-blanks-above))
```
The difference from the previous example is when using `:next-inner-restore`, 
the value of `{:list {:respect-nl? ...}}` is restored to what it was previously,
instead of being unconditionally set to `false`.  This will tend to integrate
better with other styles.

In this example, `:list {:respect-nl? true}` is set globally, and the
use of `:next-inner-restore` in `comment` integrates well with that:

```
(czprint rnl2x
         {:parse-string? true,
          :list {:respect-nl? true},
          :fn-map {"comment" [:none
                              {:list {:respect-nl? true},
                               :next-inner-restore [[:list :respect-nl?]]}]}})
(comment
  (defn x
    [y]
    (println y))

  (this
    is
    a
    thing
    that
    is
    interesting)

  (def z


    [:this-is-a-test :with-3-blanks-above?])

  (def a :more stuff)



  (def b :3-blanks-above))
```
where the previous approach using just `:next-inner` and resetting the 
value of `:list {:respect-nl? false}` would be largely incorrect:

```
(czprint rnl2x
         {:parse-string? true,
          :list {:respect-nl? true},
          :fn-map {"comment" [:none
                              {:list {:respect-nl? true},
                               :next-inner {:list {:respect-nl? false}}}]}})
(comment
  (defn x [y] (println y))

  (this is a thing that is interesting)

  (def z [:this-is-a-test :with-3-blanks-above?])

  (def a :more stuff)



  (def b :3-blanks-above))
```

While most of the configuration in the option map is held in the values of
specific keys, some of the configuation resides in sets.  `:next-inner-restore`
will also restore values within sets, but the syntax is extended slightly
to handle set values.

Instead of a vector of keys, when restoring a value for a set element,
the vector contains only two things -- the first being a vector of keys
which identifies the set, and the second being a string which represents
the element within the set that should be restored.  For instance:

```
{:next-inner-restore [[[:map :key-no-sort] ":pre"]]}
```

is used in the next example.

In this next example, any map in a `defn` should be sorted. Any top level 
maps in a `defn` should be formatted onto multiple lines,
and if those top level maps contain a key `:pre`, they should not be 
sorted.  But the restriction on sorting if the key `:pre` is found should
only be enforced for the top level maps in the `defn`.

```
(czprint prepost2
         {:parse-string? true,
          :fn-map {"defn" [:arg1-force-nl-body
                           {:next-inner {:map {:force-nl? true,
                                               :sort-in-code? true,
                                               :key-no-sort #{":pre"}},
                                         :next-inner-restore
                                           [[:map :force-nl?]
                                            [[:map :key-no-sort] ":pre"]]}}]}})
(defn selected-protocol-for-indications
  {:pre [(map? m) (empty? m)],
   :post [(not-empty %)]}
  [{:keys [spec]} procedure-id indications]
  (->> {:indications indications, :pre preceding, :procedure-id procedure-id}
       (sql/op spec queries :selected-protocol-for-indications)
       (map :protocol-id)))
```

The last thing in the `:next-inner-restore` vector is where the set
`:map {:key-no-sort #{...}}` is restored to its previous value regarding the
key `:pre`.  The values of any keys must be given as strings, to ease the
comparison of values when using sets as configuration elements.



__NOTE:__ While you can use `:style <whatever>` in the options map in a
`:fn-map` vector: `[<fn-type> <options-map>]`, if you want to remove that
style when formatting more deeply nested expressions,  you have three 
approaches:

  1. Set a new style with `:next-inner`.

  2. Identify the configuration parameters of that style, and force them
  to be what you think they were with `:next-inner`.

  2. Identify the configuration parameters of that style, and have
  `:next-inner-restore` restore them to their previous values.
  
#### Altering the formatting of lists which begin with collections

In addition to strings containing function names, you can use
the keywords: `:list`, `:map`, `:vector`, and `:set` as keys in the 
`:fn-map`.  When a list shows up with one of these collections as
the first element, the `:fn-map` entry with that collection type will
be used.

#### Altering the formatting of quoted lists

There is an entry in the `:fn-map` for `:quote` which will cause quoted
lists to be formatted assuming that their contents do not contain functions.
In particular, their first elements (and all contained first elements)
will not be looked up in the `:fn-map`.

The default for quoted lists will format them on a single line if possible,
and will format them without a hang if multiple lines are necessary. In
addition, maps contained in a quoted list will have their keys sorted,
since a quoted list is not considered "in-code".

If you change the value of the key `:quote` in the `:fn-map` to be `:none`,
then quoted lists will be handled as any other lists, and will not be 
processed specially.


Some examples:
```
; The current default

% (zprint q {:parse-string? true})
'(redis
  service
  http-client
  postgres
  cassandra
  mongo
  jdbc
  graphql
  service
  sql
  graalvm
  postgres
  rules
  spec)


; No special processing for quoted lists

% (zprint q {:parse-string? true :fn-map {:quote :none}})
'(redis service
        http-client
        postgres
        cassandra
        mongo
        jdbc
        graphql
        service
        sql
        graalvm
        postgres
        rules
        spec)


; If you want quoted lists wrapped like vectors are wrapped

% (zprint q {:parse-string? true :fn-map {:quote [:wrap {:list {:indent 1} :next-inner {:indent 2}}]}})
'(redis service http-client postgres cassandra mongo jdbc graphql service sql
  graalvm postgres rules spec)
```

#### Replacing functions with reader-macros

The `:fn-map` is can be used to allow backtranslation of arbitrary functions
into reader-macros.  For instance, `(quote a)` can be backtranslated into
`'a` by using the following `:fn-map` entry:

```
{:fn-map {"quote" [:replace-w-string {} {:list {:replacement-string "'"}}]}}
```

If there is a function which has a fn-type of `:replace-w-string` __and__ 
the options map has a `{:list {:replacement-string "'"}}` value, then 
that function will be replaced by the string.  The leading and trailing
"()" will be removed, as will the function name.  If there is only one
options map in the vector which is the value of the key-value pair, then
it is used for both structures and source formatting.  However, if there
are two maps as the second and third elements in the vector, the first
map (which is the second element of the vector) is used as the options map 
for source formatting, and the second map (third element of the vector)
is used as the options map for structure formatting.  Thus, the
example above only replaces `(quote a)` with `'a` when formatting structures,
and not when formatting source.  If there was just one options map, it
would perform this replacement when formatting both structures and 
source. 


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

#### :fn-force-nl _#{:force-nl :force-nl-body :noarg1 :noarg1-body :arg1-force-nl :flow :flow-body}_

This is a set that specifies which function types will always format with
a hang or a flow, and never be printed on the same line even if they fit.

#### :fn-gt2-force-nl _#{:gt2-force-nl :binding}_

This is a set that specifies which function types will always format with
a hang or a flow, and never be printed on the same line if they have more
than 2 arguments.

#### :fn-gt3-force-nl _#{:gt3-force-nl :arg1-pair :arg1-pair-body}_

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

### Replacing the entire `:fn-map`

Sometimes you might wish to remove all of the formatting specified by the 
default `:fn-map`, and replace it with only a few elements of your own
choosing.  Doing this by setting every existing function's formatting to
`:none` would work, but is not a good option.  You can remove the entire
`:fn-map` in a simple way, but you __must__ ensure that you replace it
with a new `:fn-map` which is, at least, an null map, or zprint will not
operate correctly.  You can use `:remove-final-keys [[:fn-map]]` to
remove the function map.  

`:remove-final-keys` takes a vector of key sequences.  It will remove the
final key in each key-sequence from the options map, prior to any other
operations specified in the options map.  Thus, you could use this as
an options map:

```
{:remove-final-keys [[:fn-map]]
 :fn-map {"let" :binding}}
```

This would leave only `let` to be actually formatted like a binding function.


### Altering the processing of function types

EXPERIMENTAL: may be removed or changed!

Whenever a function type is encountered, it is looked up in the `:fn-type-map`.
If it isn't found (which is the usual case), then it is processed as defined
above.  If the key is encountered, the value of the key is used.  The values
of the keys have the same format as the value of the keys in the `:fn-map`,
with the exclusion of string values (used for aliasing in the `:fn-map`).
The usual value for a function type in the `:fn-type-map` would be a vector
with a function type, and an options map to use during the formatting of
any function which has that function type.

Modifying the `:fn-type-map` can allow you to change the formatting for
every function which uses that type instead of having to make identical 
changes to every function in the `:fn-map`.

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

; Justified formatting using :max-variance

(zprint-fn compare-ordered-keys
           {:pair {:justify? true :justify {:max-variance 20}}})

(defn compare-ordered-keys
  "Do a key comparison that places ordered keys first."
  [key-value zdotdotdot x y]
  (cond (and (key-value x) (key-value y)) (compare (key-value x) (key-value y))
        (key-value x)    -1
        (key-value y)    +1
        (= zdotdotdot x) +1
        (= zdotdotdot y) -1
        :else            (compare-keys x y)))

```
Zprint will optionally justify `:map`, `:binding`, and `:pair` elements.
There are several detailed configuration parameters used to control the
justification.  Obviously this works best if the keys in a map are
all about the same length (and relatively short), and the test expressions
in a cond are about the same length, and the locals in a binding are
about the same length.

The third example, above, sets the `:max-variance` to 20, which causes
the justification code to look at the lengths of the left hand elements
being justified, and to consider leaving a few of them unjustified if 
the variance of the sizes of the left hand elements is over 20. In
the example above, the first row of the justified pairs is left unjustified.

If leaving a few pairs unjustified will bring the variance under the specified
value, then that is what is done.  If even that doesn't bring the variance
down to the `:max-variance`, then the elements are not justified
at all.  This can help to make justification more generally useful by only
doing it where it will improve readability.

There are two styles that will turn on justification wherever possible:
```
{:style :justified}
{:style :justified-original}
```

The difference is that ```{:style :justified}``` leaves the
`:max-variance` set to `20` so that the results are what is to be
believed the most pleasing.  This is the default.

The style ```{:style :justified-original}``` is the same as ```{:style
:justified}``` except that the `:max-variance` is set to `1000` to
output the original defaults for justification.

By default, if the left hand side of a pair formats on multiple
lines, the right-hand-side of that pair will always flow onto a new
line below (and possibly indented from) the left-hand-side.

However, you can now allow the right-hand-side to format to the
right of the last line of a multi-left-hand-side element.  Enable
`:style :multi-lhs-hang` to allow this to happen.  This style sets
`:multi-lhs-hang? true` in `:binding`, `:map`, and `:pair`.

An example:
```
; The default approach

(czprint i273x {:parse-string? true :width 50})
(cond (and (zero? line-length)
           (not previous-comment?))
        out
      previous-comment? (conj out line-length 0)
      :else (conj out line-length))

; With :multi-lhs-hang? true enabled for :pair

(czprint i273x {:parse-string? true :width 50 :style :multi-lhs-hang})
(cond (and (zero? line-length)
           (not previous-comment?)) out
      previous-comment? (conj out line-length 0)
      :else (conj out line-length))
```

Another example, this one with justification:

```
; Default approach

(czprint dpg {:parse-string? true :style [:justified] :width 70})
(defn defprotocolguide
  "Handle defprotocol with options."
  ([] "defprotocolguide")
  ([options len sexpr]
   (when (= (first sexpr) 'defprotocol)
     (let [third  (nth sexpr 2 nil)
           fourth (nth sexpr 3 nil)
           fifth  (nth sexpr 4 nil)
           [docstring option option-value]
             (cond (and (string? third) (keyword? fourth))
                     [third fourth fifth]
                   (string? third)  [third nil nil]
                   (keyword? third) [nil third fourth]
                   :else            [nil nil nil])
           guide  (cond-> [:element :element-best :newline]
                    docstring (conj :element :newline)
                    option    (conj :element :element :newline)
                    :else     (conj :element-newline-best-*))]
       {:guide guide, :next-inner {:list {:option-fn nil}}}))))

; With :multi-lhs-hang? true enabled for :binding, :map, and :pair

(czprint dpg {:parse-string? true :style [:justified :multi-lhs-hang] :width 70})
(defn defprotocolguide
  "Handle defprotocol with options."
  ([] "defprotocolguide")
  ([options len sexpr]
   (when (= (first sexpr) 'defprotocol)
     (let [third  (nth sexpr 2 nil)
           fourth (nth sexpr 3 nil)
           fifth  (nth sexpr 4 nil)
           [docstring option option-value]
             (cond (and (string? third)
                        (keyword? fourth)) [third fourth fifth]
                   (string? third)         [third nil nil]
                   (keyword? third)        [nil third fourth]
                   :else                   [nil nil nil])
           guide  (cond-> [:element :element-best :newline]
                    docstring (conj :element :newline)
                    option    (conj :element :element :newline)
                    :else     (conj :element-newline-best-*))]
       {:guide guide, :next-inner {:list {:option-fn nil}}}))))
```

Whenever `:multi-lhs-hang?` is true, when doing justification when
when the left hand side of a pair is a colllection, zprint attempts
to format in a space narrower than the rest of the line -- in order
to give multi-line left-hand-side elements the chance to have their
right-hand-sides format onto the same line, as well as increasing
the chances that they will justify at all.  The size of the narrow
space is the number of characters remaining on the line divided by
the value of `:lhs-narrow`, configured in `:justify {:lhs-narrow
2.0}` in `:binding`, `:map`, and `:pair`.  This defaults to 2.0,
which is means "use half of the remaining space for the left-hand-side".
You can change this to anything you wish, and disable it altogether
by configuring it to 1.0.

If you are using justification, you would be well advised to enable
`:style :multi-lhs-hang` and see how that works for you, as it will
probably increase the number of things that justify as well as how
nice they look when justified.

I don't personally find the justified approach my favorite for all code,
though with the `:max-variance` approach there are many more places 
where it looks very good.  With `:multi-lhs-hang` there are even more.

Try:

```clojure
(czprint-fn resultset-seq {:style :justified})
```

and see what you think.  Looks great to me, but it just happens to
have nice locals.

For functions where this looks great, you can always turn it on
just for that function (if you are using lein-zprint or any of the
released pre-built binaries), like so:

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

__NOTE:__ Justification involves extra processing, and because of
the way that zprint tries to do the best job possible, it can cause
a bit of a combinatorial explosion that can make formatting some
functions and structures take a good bit longer than usual.  I have
put scant effort into optimizing this capability, as I have no idea
how interesting it is to people in general.  If you are using it
and like it, and you have situations where it seems to be particularly
slow for you, please enter an issue to let me know.

### Formatting Large or Deep Collections

Sometimes you end up with a collection which is very large or very
deep -- or both.  You want to get an overview of it, but don't
want to output the entire collection because it will take too much
space or too much time.  At one time, these were experimental
capabilities, but they are now fully supported.

There are two limits that can be helpful.

#### :max-length _1000_

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

{:agent ##,
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

{:agent {:object? false},
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

#### :max-depth _1000_

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

Note that the indent values for things with pairs (i.e., `:map`, `:binding`,
`:pair`) are counted differently from other things.  For these things,
an `:indent 2` will leave two blanks to the right of the left "bracket"
(e.g. "{" for maps).  For other things an `:indent 2` will leave one blank
to the right of the left bracket.

### :indent-only? _false_

This is configurable for the major data structures: lists, maps,
sets, and vectors.  When enabled, zprint will not add or remove
newlines from the incoming source, but will otherwise regularize
whitespace.  When `:indent-only?` is specified, other configuration
parameters for the lists, maps, sets, or vectors will be
ignored except for `:indent` (for all of the data types) and
`:indent-only-style` (to control hang or flow, only for lists).

### :respect-bl? _false_

This will cause zprint to respect incoming blank lines. If this is
enabled, zprint will add newlines and remove newlines as necessary,
but will not remove any existing blank lines from incoming source.
Existing formatting configuration will be followed, of course with
the constraint that existing blank lines will be included wherever
they appear.  Note that blank lines at the "top level" (i.e., those
outside of `(defn ...)` and `(def ...)` expressions) are always
respected and never changed. `:respect-bl?` controls what happens
to blank lines __within__ `defn` and `def` expressions.

If you wish to use zprint to enforce a particular format, using
`:respect-bl?` might be a bad idea -- since it depends on the incoming source
with regard to blank lines.

If you use blank lines a lot within function definitions in order
to make them more readable, this can be a good capability to enable
globally.

### :respect-nl? _false_

This will cause zprint to respect incoming newlines. If this is
enabled, zprint will add newlines, but will not remove any existing
newlines from incoming source.  Existing formatting configuration
will be followed, of course with the constraint that existing
newlines will be included wherever they appear.

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

#### :hang? _true_

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
only affects lists, but may be implemented for other collections in
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

#### :flow? _false_

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

#### :force-nl? _false_

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

#### :nl-separator? _false_

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
#### :nl-separator-all? _false_

This will put a blank line between any pair, regardless of how the 
second element of the pair was formatted.  Some examples:

```clojure
; The default approach, when the value (or any second element of a pair)
; doesn't fit on the same line as the key (or first element).
; The second element in this case is indented for clarity.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} {:width 32})

{:a :b,
 :c
   {:e :f, :g :h, :i :j, :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; :nl-separator? will give you a blank line only after every second element 
; that didn't fit on the same line as the first element.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} {:width 32 :map {:nl-separator? true}})

{:a :b,
 :c
   {:e :f, :g :h, :i :j, :k :l},

 :m :n,
 :o {:p {:q :r, :s :t}}}

; :nl-separator-all? will give you a blank line between every pair of elements,
; regardless of how they fit onto the lines.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} {:width 32 :map {:nl-separator-all? true}})

{:a :b,

 :c
   {:e :f, :g :h, :i :j, :k :l},

 :m :n,

 :o {:p {:q :r, :s :t}}}
```
This works independently for `:pair`, `:binding`, and `:map`.

##### :flow-all-if-any? _false_

There are pairs in functions like `cond`, in maps, and in bindings.
When the right hand part of the pair doesn't fit, it "flows" onto the
next line.  You can configure zprint to flow all of the right
hand pairs onto the next line if any of them flow because they don't
fit.  This will certainly make things take more vertical space, but
the additional consistency may make the pairs more easily distinguishable.
You can enable this in any of the three possible places by setting
`:flow-all-if-any? true` in `:pair`, `:map`, or `:binding`.

#### :justify? _false_

Turn on [justification](#a-note-on-justifying-two-up-printing).
Default is nil (justification off).

#### :justify {:max-variance 1000, :ignore-for-variance #{}, :no-justify #{} :lhs-narrow 2.0 :max-depth 100}

Parameters to control justification:

##### :max-variance _1000_

The justification code calculates the variance of the length of the
left hand elements of the group of lines to be justified.  If the variance
is below the `:max-variance`, then they are all justified. 

If the variance is above the `:max-variance`, then the longest
left-hand element is removed from the calculation (or more than one
if there are several which are all equally the longest).  Then the
variance is recalculated.  If it is now below the `:max-variance`,
then the group of lines is justified but the ones previously "removed"
appear but are not justified.  If, however, the variance is still
above the `:max-variance`, then this process is repeated once more,
with the same result if the variance is now below the `:max-variance`.
If it is still above the `:max-variance`, then the group of lines
are not justified.

The default value for `:max-variance` is now 20. 
If you want it to try to justify regardless of the variance of the 
left-hand-sides of the pairs, use `:justify {:max-variance 1000}`.

In some cases, 40 will look good, in some cases even 15 looks better.

##### :ignore-for-variance  _set-of-strings or nil_

When calculating the variance of a set of left-hand-sides of a
series of pairs, ignore any of those left-hand-sides whose string value
matches one of the strings in the set `:ignore-for-variance`.
These will left-hand-sides will still be justified (in that the
proper amount of spaces will be applied after them), but they will
not alter the variance calculation.  This was added primarily to allow
removing the `:else` in a `cond` from the variance calculation, because
it is sometimes short enough compared to the other left-hand-sides in
the `cond` to cause the ensemble to not justify, and excess space after
the last thing in a `cond` rarely causes any confusion.

By default, this is only set for: 
`{:pair {:justify {:ignore-for-variance #{":else"}}}}`.

This can be set to `nil` to consider `:else` in the variance calculation,
or other values can be added to the set to customize the justification
process.  

Note that (for efficiency reasons) you can add only the string values
of things to ignore to the set `:ignore-for-variance`.

##### :no-justify  _set-of-strings or nil_

Much like `:ignore-for-variance`, when calculating the variance of
a set of left-hand-sides of a series of pairs, ignore any of those
left-hand-sides whose string value matches one of the strings in
the set `:no-justify`.  However, the left-hand-sides that appear
in `:no-justify` will also not be justified (i.e., will not have
additional space after them to align the right-hand-side in a visual
column).

This was added primarily to allow removing the `_` in a binding
vector (e.g., `let`) from not only the variance calculation but
from justification altogether.  The very short length of `_` tends
to raise the variance enough to block justification when it otherwise
would still be useful.  In addition, since the `_` is so short,
placing the right-hand-side expression out in a column often adds
little value.

By default, this is only set for: 
`{:binding {:justify {:no-justify #{"_"}}}}`.

This can be set to `nil` for `:binding` to consider `_` as a normal
left-hand-side in justification, or other values can be added to
the set to customize the justification process.

Note that (for efficiency reasons) you can add only the string
values of things to not justify to the set `:no-justify`.

##### :max-gap  _number or nil_

This provides another way to prevent justification of something
that will "look bad".  When `:max-gap` is non-nil, the longest and
shortest of the things on the "left" will be compared, and the
max-gap will be the difference (plus one for the space after).  If
this number is less than or equal to the configured `:max-gap`,
then it will attempt to justify.  Note that it must also pass the
`:max-variance` check before it will successfully justify.

The `:max-gap` is calculated after the `:no-justify` and
`:ignore-for-variance` elements are removed.  So if you want them
included when calculating the `:max-gap`, you need to adjust these
parameters as well. 

##### :lhs-narrow _2.0_

The denominator of the fraction of the remaining space to use
when formatting a collection that is the left-hand-side of a pair.
Only used when `:multi-lhs-hang? true` is configured for this
set of pairs.

By default, if the left hand side of a pair formats on multiple
lines, the right-hand-side of that pair will always flow onto a new
line below (and possibly indented from) the left-hand-side.

However, you can now allow the right-hand-side to format to the
right of the last line of a multi-left-hand-side element.  Enable
`:style :multi-lhs-hang` to allow this to happen.  This style sets
`:multi-lhs-hang? true` in `:binding`, `:map`, and `:pair`.

Whenever `:multi-lhs-hang?` is true, when doing justification when
when the left hand side of a pair is a colllection, zprint attempts
to format in a space narrower than the rest of the line -- in order
to give multi-line left-hand-side elements the chance to have their
right-hand-sides format onto the same line, as well as increasing
the chances that they will justify at all.  The size of the narrow
space is the number of characters remaining on the line divided by
the value of `:lhs-narrow`, configured in `:justify {:lhs-narrow
2.0}` in `:binding`, `:map`, and `:pair`.  This defaults to 2.0,
which is means "use half of the remaining space for the left-hand-side".
You can change this to anything you wish, and disable it altogether
by configuring it to 1.0.

#### :multi-lhs-hang? _false_

By default, if the left hand side of a pair formats on multiple
lines, the right-hand-side of that pair will always flow onto a new
line below (and possibly indented from) the left-hand-side.

However, you can now allow the right-hand-side to format to the
right of the last line of a multi-left-hand-side element.  Enable
`:style :multi-lhs-hang` to allow this to happen.  This style sets
`:multi-lhs-hang? true` in `:binding`, `:map`, and `:pair`.

#### :max-depth _100_

Justification will only be performed down to the max-depth.  At any depth
below that, justification will not be attempted.  The point of this is
because justification is moderately time consuming, and when it is performed
in a deeply nested expression the time it takes can be noticeable.  If
you find justification is taking too much time, try setting `:max-depth` to
4 or 5.  It might help a lot.

## Configuring functions to make formatting changes based on content

There are several places in the options map where user defined
functions can be used to alter the formatting based on the content
of the element to be formatted:

```
{:list {:constant-pair-fn (fn [element] ...)}}
{:vector-fn {:constant-pair-fn (fn [elementx] ...)}}
{:vector {:options-fn-first (fn [options first-element] ...)}}
{:vector {:options-fn (fn [options element-count element-seq] ...)}}
```

If you are using zprint as a library or at the REPL, you can just
specify the functions to be used with the `(fn [x] ...)` or `#(...
% ...)` approach.

If, however, you are configuring one of these functions in a
`.zprintrc` file, there are some potential problems.

Foremost among these is security -- if you can specify a function
in an external file, and then that function can be executed when
someone runs zprint, we have a huge security hole.

Additionally, some environments (e.g., the graalVM binaries) don't
accept new function definitions once they are compiled.

The solution to both of these issues is to use the sandboxed Clojure
interpreter, `sci` to define and execute these functions.
This allows zprint to accept function definitions
in any available `.zprintrc` file, as well as options maps loaded
using the `--url` or `--url-only` switches or from the command line.
Any function defined in an options map cannot reference the file
system or do anything else that outside of the `sci` sandbox in
which it is operating.

WHen defining in-line functions in an options map, `sci` will support
either the `(fn [x] ...)` form of function definition, or the `#(...)`
form of function definition.

### Functions available in `sci`

The functions available in `sci`, and therefore the functions you can
use in a function declared in an options map are as listed below, 
indexed by the namespace in which they appear.  The namespace
`:macro` is used for the special forms interpreted by `sci`.
```clojure
{:macros #{. and as-> case comment declare def defmacro defn do doseq
           expand-constructor expand-dot* fn fn* for if import in-ns lazy-seq
           let loop macroexpand macroexpand-1 new ns or resolve set! try var},
 clojure.core
   #{* *' *err* *file* *in* *ns* *out* *print-length* *print-level* *print-meta*
     *print-namespace-maps* + +' - -' -> ->> -reified-methods .. / < <= = == >
     >= add-watch aget alength alias all-ns alter-meta! alter-var-root ancestors
     any? apply array-map aset assert assoc assoc! assoc-in associative? atom
     bean bigdec bigint biginteger binding binding-conveyor-fn bit-and
     bit-and-not bit-flip bit-not bit-or bit-set bit-shift-left bit-shift-right
     bit-test bit-xor boolean boolean-array boolean? booleans bound?
     bounded-count butlast byte byte-array bytes bytes? cat char char-array
     char-escape-string char-name-string char? chars chunk chunk-append
     chunk-buffer chunk-cons chunk-first chunk-next chunk-rest chunked-seq?
     class class? coll? comp comparator compare compare-and-set! complement
     completing concat cond cond-> cond->> condp conj conj! cons constantly
     contains? count counted? cycle dec dec' decimal? dedupe defmethod defmulti
     defn- defonce defprotocol defrecord delay deliver denominator deref derive
     descendants disj dissoc distinct distinct? doall dorun dotimes doto double
     double-array double? doubles drop drop-last drop-while eduction empty
     empty? ensure-reduced enumeration-seq eval even? every-pred every? ex-cause
     ex-data ex-info ex-message extend extend-protocol extend-type extends?
     false? ffirst filter filterv find find-ns find-var first flatten float
     float-array float? floats flush fn? fnext fnil format frequencies gensym
     get get-in get-method get-thread-binding-frame-impl group-by has-root-impl
     hash hash-map hash-set hash-unordered-coll ident? identical? identity
     if-let if-not if-some ifn? inc inc' indexed? inst? instance? int int-array
     int? integer? interleave intern interpose into into-array ints isa? iterate
     iterator-seq juxt keep keep-indexed key keys keyword keyword? last lazy-cat
     letfn line-seq list list* list? load-string long long-array longs
     make-array make-hierarchy map map-entry? map-indexed map? mapcat mapv max
     max-key memoize merge merge-with meta methods min min-key mod
     multi-fn-add-method-impl multi-fn-impl multi-fn?-impl munge name namespace
     namespace-munge nat-int? neg-int? neg? newline next nfirst nil? nnext not
     not-any? not-empty not-every? not= ns-aliases ns-imports ns-interns ns-map
     ns-name ns-publics ns-refers ns-resolve ns-unmap nth nthnext nthrest num
     number? numerator object-array odd? parents partial partition partition-all
     partition-by peek persistent! pop pop-thread-bindings pos-int? pos? pr
     pr-str prefer-method prefers print print-dup print-method print-str printf
     println prn prn-str promise protocol-type-impl push-thread-bindings
     qualified-ident? qualified-keyword? qualified-symbol? quot rand rand-int
     rand-nth random-sample range ratio? rational? rationalize re-find re-groups
     re-matcher re-matches re-pattern re-seq read read-line read-string
     realized? record? reduce reduce-kv reduced reduced? reductions refer reify
     reify* rem remove remove-all-methods remove-method remove-ns remove-watch
     repeat repeatedly replace replicate require requiring-resolve reset!
     reset-meta! reset-thread-binding-frame-impl reset-vals! resolve rest
     reverse reversible? rseq rsubseq run! satisfies? second select-keys seq
     seq? seqable? seque sequence sequential? set set? short short-array shorts
     shuffle simple-ident? simple-keyword? simple-symbol? some some-> some->>
     some-fn some? sort sort-by sorted-map sorted-map-by sorted-set
     sorted-set-by sorted? special-symbol? split-at split-with str string? subs
     subseq subvec supers swap! swap-vals! symbol symbol? tagged-literal
     tagged-literal? take take-last take-nth take-while the-ns to-array
     trampoline transduce transient tree-seq true? type unchecked-add
     unchecked-add-int unchecked-byte unchecked-char unchecked-dec-int
     unchecked-divide-int unchecked-double unchecked-float unchecked-inc
     unchecked-inc-int unchecked-int unchecked-long unchecked-multiply
     unchecked-multiply-int unchecked-negate unchecked-negate-int
     unchecked-remainder-int unchecked-short unchecked-subtract
     unchecked-subtract-int underive unquote unreduced unsigned-bit-shift-right
     update update-in uri? use uuid? val vals var? vary-meta vec vector vector?
     volatile! vreset! vswap! when when-first when-let when-not when-some while
     with-bindings with-in-str with-meta with-open with-out-str with-redefs
     with-redefs-fn xml-seq zero? zipmap},
 clojure.edn #{read read-string},
 clojure.lang #{IAtom IAtom2 IDeref compareAndSet deref reset resetVals swap
                swapVals},
 clojure.repl #{apropos demunge dir dir-fn doc find-doc print-doc pst source
                source-fn stack-element-str},
 clojure.set #{difference index intersection join map-invert project rename
               rename-keys select subset? superset? union},
 clojure.string #{blank? capitalize ends-with? escape includes? index-of join
                  last-index-of lower-case re-quote-replacement replace
                  replace-first reverse split split-lines starts-with? trim
                  trim-newline triml trimr upper-case},
 clojure.template #{apply-template do-template},
 clojure.walk #{keywordize-keys macroexpand-all postwalk postwalk-demo
                postwalk-replace prewalk prewalk-demo prewalk-replace
                stringify-keys walk}}
```
If you use additional functions not in the list above, zprint will not 
accept the `.zprintrc` file call to change the current options map.

Note that `sci` is used only when reading options maps from `.zprintrc`
files.  It is not used when the options map is changed by using the
`set-options!` call when using zprint as a library or at the REPL.

# Configurable Elements
______
## :agent, :atom, :delay, :fn, :future, :promise

All of these elements are formatted in a readable manner by default,
which shows their current value and minimizes extra information.

#### :object? _false_

All of these elements can be formatted more as Clojure represents
Java objects by setting `:object?` to true.

_____
## :array

Arrays are formatted by default with the values of their elements.

#### :hex? _false_

If the elements are numeric, format them in hex. Useful if you are
doing networking.  See below for an example.

#### :object? _false_

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

#### :wrap? _true_

Should it wrap its contents, or just list each on a separate line
if they don't all fit on one line.?
_____
## :binding

Controls the formatting of the first argument of
any function which has `:binding` as its function type.  `let` is, of
course, the canonical example.

##### :indent _2_
##### :hang? _true_
##### :hang-expand _2_
##### :hang-diff _1_
##### :justify? _false_
##### :justify {:max-variance 1000, :no-justify #{"_"}, :ignore-for-variance nil, :lhs-narrow 2.0 :max-depth 100}
##### :multi-lhs-hang? _false_

#### :force-nl?  _true_

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

#### :flow? _false_
#### :nl-separator? _false_

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

#### :wrap? _true_

Wrap a comment if it doesn't fit within the width.  Works hard to preserve
the initial part of the line and word wraps any of the comment that extends
beyond the `:width` minus the `:border` onto a new line immediately below.
Does not, by itself, bring subsequent lines up on to a previous line.

However, when combined with `:smart-wrap?`, it will do a dramatically 
better job of wrapping.

#### :smart-wrap? _true_

The basic `:wrap?` capability has been around for years in zprint, and
while it managed to make things fit within the `:width`, the results could
be pretty bad.  For instance, when code moved to the right, you could get
something like this:

```clojure
(defn fzprint-map-two-up
  [caller options ind commas? coll]
  (let [len (count coll)]
    (when (not (and one-line? force-nl? (> len 1)))
      (let [caller-options (options caller)]
        (concat-no-nil l-str-vec
                       (prepend-nl options
                                   (+ indent ind)
                                   ; I think that fzprint-extend will sort out
                                   ; which
                                   ; is and isn't the rightmost because of
                                   ; two-up
                                   (fzprint-extend options
                                                   zloc-seq-right-first))
                       r-str-vec)))))
```

Once this happened, it would be like this forever unless you fixed it up
by hand.

However, with `:smart-wrap? true` (which is now the default), things like
this are cleaned up.  When zprint encounters comments like those above,
they will now be formatted like this.  To be clear, it will repair the 
problem above!

```clojure
(defn fzprint-map-two-up
  [caller options ind commas? coll]
  (let [len (count coll)]
    (when (not (and one-line? force-nl? (> len 1)))
      (let [caller-options (options caller)]
        (concat-no-nil l-str-vec
                       (prepend-nl options
                                   (+ indent ind)
                                   ; I think that fzprint-extend will sort
                                   ; out which is and isn't the rightmost
                                   ; because of two-up
                                   (fzprint-extend options
                                                   zloc-seq-right-first))
                       r-str-vec)))))
```

This feature is called `:smart-wrap?` because it will handle many special
cases beyond just doing word wrap.  It will recognize and not mess up
the use of "o", "*", "-" as bullets, as well as numbered lists.  Overall
it will recover most of the problems that the simple version of `:wrap?` 
put into the code in the past, as well as not creating new problems for
the future.

When word wrapping, it will detect punctuation and capitalization,
and try to cleanly join lines that move around by adding those where
it seems appropriate.  While you don't need to capitalize and
punctuate your comments, doing so will give zprint useful information
it can use when it needs to move words from one line to another.

`:smart-wrap?` is also configurable, so that you can configure it to
minimize the wrapping that it will do when processing a file.  There
is a style called `:minimal-smart-wrap` which will configure it to make
minimal changes but will also still fix up problems like those above.
Minimal smart wrap will flow words from one line to the next less
frequently, and will never flow words back up onto a line terminated 
by a period.

There is a less severe version of `:minimal-smart-wrap` called 
`:sentence-smart-wrap` which is identical to normal (default) smart-wrap,
but which will not flow things back up to a line terminated by a period.
Since a line ending with a period terminates the "comment group", each
series of lines where the final line ends in a period becomes a separate
comment group and is handled independently.  Which may be more to your
liking, particularly if you haven't already used smart wrap before.

Note that all comment wrapping (including smart-wrap) is performed after
all of the formatting of any code.  Comment wrapping is thus independent of
indent-only, respect-nl, and respect-bl processing.

#### :smart-wrap

A set of configuration parameters for the smart wrap capability.

##### :border _5_

Smart wrap has a border just for comments.  Comments that reach beyond the
border will be wrapped.

This will also be used as the `:border` for `:comment` when `:smart-wrap?` is 
true.

##### :last-max 5

If the last line of a group of comments is more than 5 characters longer
than the longest previous line in the group, then smart wrap the group.

##### :max-variance 30

If the variance of the lines in a group of comments is more than 30, then
smart wrap the group.  The last line is generally not included in the
variance, though under some conditions it will be included.

##### :space-factor 3

If the longest line in a group of comments is less than the number of
characters from the leftmost character in the comment to the width, divided
by the `:space-factor`, then smart wrap the group of comments.

##### :end+start-cg
##### :end+skip-cg

Two vectors of regular expressions which are used to notice things like
bullets and numbered lists in comments.  The details are ... pretty detailed.
If you want to configure these, please submit an issue.

#### :border? _0_

This is the border for the basic `:wrap?` capability.  It is zero by default,
but will take on the value from `:smart-wrap` when `:smart-wrap?` is true.

#### :inline? _true_

If the a comment is on the same line as some code, keep the comment
on that same line.  The distance from the code is preserved only
when `:inline-align-style :none` is used.  See `:inline-align-style`
for details.  If the comment extends beyond the width, it will be
wrapped just like a comment which is on its own line.

#### :inline-align-style _:aligned_

There are three possible ways that inline comments can be aligned:

  * `:none` -- no effort is made to align inline comments.  The distance from
    the code on input is preserved.  If they are aligned, it is because the
    code didn't move (or moved together).

  * `:aligned` -- the default.  Any comments that are aligned on input and are
    separated by less than 5 lines on output will be aligned in the output.
    All inline comments will shift left to be one space from the widest code.

  * `:consecutive` -- Any inline comments which appear on consecutive lines
    in the output (not the input) will be aligned.  All inline comments
    will shift left to be one space from the widest code.

#### :min-space-after-semi _0_

There can be zero to several spaces after the rightmost semicolon
and before the comment text in a comment.  `:min-space-after-semi`
sets the minimum number of spaces after the rightmost semicolon
before the text of the comment that is allowed.  If there are less
than `:min-space-after-semi` spaces between the rightmost semicolon
and the comment text, then the number of spaces will be increased
to equal the value of `:min-space-after-semi`.

In short, if you don't want comments like this: `;this is a comment`,
you can set `:min-space-after-semi` to 1, and that comment will end
up like this: `; this is a comment` in the formatted source.

The default for this is `0`, so by default no changes will be made.

Note that any length increase in a comment caused by additional
spaces being added to bring the total up to `:min-space-after-semi`
will not be included in the length of the comment as determined by
the `:count?` option (see below).  Note that by default, the length
of comments is not considered when formatting source code because
the default for `:count?` is `false`.

#### :count? _false_

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

##### :indent _2_
##### :hang? _true_
#### :force-nl?  _true_

Forces a new line between one type/fn defn set and the next in the extend.

#### :nl-separator? _false_

Places a blank line between one type/fn defn set and the next if the
fn defn set formats with a flow.

#### :flow? _false_

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
#### :modifiers _#{"static"}_

Contains a set of elements that will be placed on the same line as the
protocol-or-interface-or-Object.  Created largely to support `defui` in
Clojurescript om/next, but may have other utility. Elements specified
by `{:extend {:modifiers #{<element1> <element2>}}}` are added to
the set (as opposed to replacing the set entirely). You can remove
elements from the set by `{:remove {:extend {:modifers #{<thing-to-remove>}}}}`.

#### :nl-count _nil_

This is the number of newlines to put between the fns under each
protocol.

It is either a number of newlines (where 2 would be how to get one
blank line), or a vector of the number of newlines, where `[1 2 3]` would
give you a normal newline for the first, and one blank line between the second
and third elements, and then two blank lines between all remaining elements.
The final value is repeated as necessary if there are not "enough" for the
number of elements in the list.  Comments are kept with the following
elements, so the next element in the vector is only used when it isn't
a comment.  

Note that there is always a single newline before the
first function.

An example:

```
; No :nl-count

(czprint i229ea {:parse-string? true :extend {:nl-separator? true}})
(extend-type ExtendedType
  AProtocol
    (doit [this] (run! println [1 2 3]) (println this))
    (dothat [this that])
    (domore [this that])

  AnotherProtocol
    (xdoit [this])
    (xdothat [this that])
    (xdomore [this that])

  MoreProtocol
    (xdoit [this])
    (xdothat [this that])
    (xdomore [this that]))

; :nl-count 2

(czprint i229ea {:parse-string? true :extend {:nl-separator? true :nl-count 2}})
(extend-type ExtendedType
  AProtocol
    (doit [this] (run! println [1 2 3]) (println this))

    (dothat [this that])

    (domore [this that])

  AnotherProtocol
    (xdoit [this])

    (xdothat [this that])

    (xdomore [this that])

  MoreProtocol
    (xdoit [this])

    (xdothat [this that])

    (xdomore [this that]))
```

_____
## :input

This controls the input that is formatted.

#### :range
Allows specification of the start and end of a range when using
`zprint-file-str` (which is used by all of the file processing binaries
when they format an entire file).

A range specification is itself a map:

`{:start start-line-number :end end-line-number}`

where start-line-number and end-line-number are zero based, and
are inclusive (that is to say, both lines will be formatted).

Since zprint can only format effectively if it knows the left margin,
the start-line-number and end-line-number are expanded outwards to 
encompass one or more top level expressions.  If they both initially
reference a single expression, the start is moved up to the first line
beyond the previous expression, while the end is moved down to the last line
of the expression referenced.  This is in order to encompass any 
`;!zprint {}` directives that might appear directly before the expression.
Note that the range will never start or end on a blank line unless
it is the start or end of the file.

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

If the start-line-number is missing but an end-line-number appears,
the start-line-number will be zero.  If the end-line-number is missing,
but a start-line-number appears, then the range will be from the
start-line-number to the end of the file.

Note that zprint will not leave trailing spaces on a line, but
this is only true for lines that are part of the range -- the other lines
are untouched.

If any problems occur when trying to determine the current or previous
expressions (since a quick parse of the entire string (file) is required
for this to happen), the entire string (file) is formatted. 

Note that `{:output {:range? true}}` will, when coupled with an 
input range, output only the formatted range and the actual range
used for that formatting -- which may well be different from the
range specified on input, as discussed above,  as zprint will adjust 
the range to emcompass
entire top level expressions.  See `:output :range?` for details.

##### :use-previous-!zprint? _false_

When processing a range within file string, examine the lines of the file
prior to the range and extract all of the previous `!zprint` lines, and
interpret those that will affect the formatted range.  The point is to
format the range in the same way as it would be formatted if the entire
file were to be formatted.

##### :continue-after-!zprint-error? _false_

If an error is encountered in a `;!zprint` directive, continue processing
instead of throwing an exception.  If `:output :range?` is true, the errors
encountered will be returned in a vector which is the value of the
key `:errors` in the map.

Note that you can set this to `true` in a `;!zprint` directive, which 
will cause errors in `;!zprint` directives to be ignored if the other
required conditions are met.  However, the caller of `zprint-file-str` 
may not be prepared to handle any errors that appear in the output map!

_____
## :list

Lists show up in lots of places, but mostly they are code.  So
in addition to the various function types described above, the `:list`
configuration affects the look of formatted code.

##### :indent _2_
##### :hang? _true_
##### :hang-avoid _0.5_
##### :hang-expand _2.0_
##### :hang-diff _1_

#### :indent-arg _nil_

The amount to indent the arguments of a function whose arguments do
not contain "body" forms.
See [here](#function-classification-for-pretty-printing)
for an explanation of what this means.  If this is nil, then the value
configured for `:indent` is used for the arguments of functions that
are not "body" functions.  You would configure this value only if
you wanted "arg" type functions to have a different indent from
"body" type functions.  It is configured by `:style :community`.

#### :indent-only? _false_

Do not add or remove newlines.  Just indent the lines that are there and
regularize whitespace.  The `:fn-map` which gives formatting and indentation
information about different functions is ignored.  The default indentation is
flow, however based on the value of the `:indent-only-style`, a hang will
be used in some situations.  See `:indent-only-style` below for details.

#### :indent-only-style _:input-hang_

Controls how `:indent-only` indents a list. If the value is
`:input-hang`, then if the input is formatted as a hang, it will
indent the list as a hang.  The input is considered to be formatted
as a hang if the first two elements of the list are on the same
line, and the third element of the list is on the second line aligned
with the second element.  The determination of alignment is not
affected by the appearance of comments.

#### :hang-size _100_

The maximum number of lines that are allowed in a hang.  If the number
of lines in the hang is greater than the `:hang-size`, it will not do
the hang but instead will format this as a flow.  Together with
`:hang-expand` this will keep hangs from getting too long so that
code (typically) doesn't get very distorted.

#### :constant-pair? _true_

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

#### :constant-pair-min _4_

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

#### :constant-pair-fn _nil_

Constant pairing works by looking for constants in the n-1, n-3, ... locations
in a list.  By default, the following are considered constants:

 * keywords
 * strings
 * numbers
 * true and false

You can alter this behavior by specifying a function which will be called
to determine if something is a constant.   You do this by specifying a
`:constant-pair-fn` value.

Here is an example of where this might be useful, also note the use of
`:next-inner` to restrict the use of `:constant-pair-fn` to just the
top level of `m/app`:

```clojure

(def mapp6
"(m/app :get  (m/app middle1 middle2 middle3\n                    [route] handler\n\t\t    ; How do comments work?\n                    [route] \n        (handler this is \"a\" test \"this\" is \"only a\" test) \n\t\t    )\n       ; How do comments work here?\n       :post (m/app \n                    [route] handler\n                    [route] ; What about comments here?\n\t\t    handler))")

; Let's  see what happens if we just use the default configuration.
; The narrow width is to force constant pairing on the second handler
; of the :get

(czprint mapp6 {:parse-string? true :width 55})

(m/app :get (m/app middle1
                   middle2
                   middle3
                   [route]
                   handler
                   ; How do comments work?
                   [route]
                   (handler this
                            is
                            "a" test
                            "this" is
                            "only a" test))
       ; How do comments work here?
       :post (m/app [route]
                    handler
                    [route] ; What about comments here?
                    handler))

; This is ok, but it would be nice to pair the handlers up with the routes
; Since they fall at the end of the expressions, sounds like we could use
; constant-pairing to force the pair behavior.

; Let's see what we can do if we define our own function to determine
; what constant-pairing will consdier a "constant"

(czprint
  mapp6
  {:parse-string? true,
   :fn-map {"app" [:none
                   {:list {:constant-pair-min 1,
                           :constant-pair-fn #(or (vector? %) (keyword? %))},
                    :next-inner {:list {:constant-pair-fn nil,
                                        :constant-pair-min 4}}}]},
   :width 55})

(m/app :get (m/app middle1
                   middle2
                   middle3
                   [route] handler
                   ; How do comments work?
                   [route] (handler this
                                    is
                                    "a" test
                                    "this" is
                                    "only a" test))
       ; How do comments work here?
       :post (m/app [route] handler
                    [route] ; What about comments here?
                      handler))

; Much nicer.  Note that we had to define both keywords and vectors as
; "constants", to preserve the keyword constant-pairing.
; Note also the use of :next-inner to restore constant-pairing to its
; default behavior down inside of expressions contained in `m/app`.

; If we were to define a :constant-pair-fn which was equivalent to the
; default, it would look like this:

(czprint mapp6
         {:parse-string? true,
          :fn-map {"app" [:none
                          {:list {:constant-pair-fn #(or (keyword? %)
                                                         (string? %)
                                                         (number? %)
                                                         (= true %)
                                                         (= false %))}}]},
          :width 55})

(m/app :get (m/app middle1
                   middle2
                   middle3
                   [route]
                   handler
                   ; How do comments work?
                   [route]
                   (handler this
                            is
                            "a" test
                            "this" is
                            "only a" test))
       ; How do comments work here?
       :post (m/app [route]
                    handler
                    [route] ; What about comments here?
                    handler))

; Of course, you wouldn't do that to restore the defaults, you would
; set the :constant-pair-fn back to nil instead to get the default
; behavior.  .
```

If you wished to keep the default behavior, and have additional things
considered "constant", you could start with the `:constant-pair-fn`
at the end of the last example, above, and add additional elements.

Here is an example where we replicate the behavior from before, where
vectors are considered constant, but all of the existing element are
considered constant as well:

```clojure
; This is where the :constant-pair-fn mimics the default behavior

(def mapp7
  "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    ; How do comments work?
                    [route]
        (handler this is \"a\" test \"this\" is \"only a\" test))
       ; How do comments work here?
       true (should be paired with true)
       false (should be paired with false)
       6 (should be paired with 6)
       \"string\" (should be paired with string)
       :post (m/app
                    [route] handler
                    [route] ; What about comments here?
                    handler))")

(czprint mapp7
         {:parse-string? true,
          :fn-map {"app" [:none
                          {:list {:constant-pair-fn #(or (keyword? %)
                                                         (string? %)
                                                         (number? %)
                                                         (= true %)
                                                         (= false %))}}]},
          :width 55})

(m/app :get (m/app middle1
                   middle2
                   middle3
                   [route]
                   handler
                   ; How do comments work?
                   [route]
                   (handler this is "a" test "this" is "only a" test))
       ; How do comments work here?
       true (should be paired with true)
       false (should be paired with false)
       6 (should be paired with 6)
       "string" (should be paired with string)
       :post (m/app [route]
                    handler
                    [route] ; What about comments here?
                    handler))

; This is where the :constant-pair-fn which mimics the default behavior
; has been extended to include vectors as "constants".

(czprint mapp7
         {:parse-string? true,
          :fn-map {"app" [:none
                          {:list {:constant-pair-min 1,
                                  :constant-pair-fn #(or (keyword? %)
                                                         (string? %)
                                                         (number? %)
                                                         (= true %)
                                                         (= false %)
                                                         (vector? %))}}]},
          :width 55})

(m/app :get (m/app middle1
                   middle2
                   middle3
                   [route] handler
                   ; How do comments work?
                   [route] (handler this
                                    is
                                    "a" test
                                    "this" is
                                    "only a" test))
       ; How do comments work here?
       true (should be paired with true)
       false (should be paired with false)
       6 (should be paired with 6)
       "string" (should be paired with string)
       :post (m/app [route] handler
                    [route] ; What about comments here?
                      handler))
```

#### :return-altered-zipper _nil_

This capability will let you rewrite any list that zprint encounters.  It only
works when zprint is formatting source code -- where `:parse-string?` is
`true`.  When a structure is being formatted, none of this is invoked.

This will call a function that you supply with the zipper of the list
and the function should return a zipper with an altered list.  Zprint will
then format the altered list.

General caveats -- you can really screw things up very easily, as I'm sure
is obvious.  Less obvious is the relative difficulty of actually writing a
function to rewrite the code.  Implementing this feature was very easy,
writng the first example, the style `:sort-dependencies` was a significant
piece of work.  It is hard to rewrite code using rewrite-clj (not that
I have a better approach), in part because you are operating both with
zippers and regular Clojure data types.  Once you have changed something
in the zipper, the zipper is the only place the change exists.  Which
is obvious, but still confusing.

The configuration for `:return-altered-zipper` is a vector: `[<depth> <symbol> <fn>]`, where `<depth>` is the depth to call the function (if the `<symbol>`
matches).  A `<depth>` of `nil` will call at any depth.  The `<symbol>` is the
first element of the list that is passed to the `<fn>`.  If `<symbol>` is
`nil`, then every list is passed to the `<fn>`.   The goal here is to not
severely impact the performance by calling the function to rewrite the zipper
too frequently.  I would recommend against configuring `[nil nil <fn>]`.
See the configuration for the style `:sort-dependencies` for an example.

The `<fn>` requires a signature of `[caller options zloc]`, where
`caller` is the keyword that indicates who called called `fzprint-list*`
(which would be useful only to check values in the option map),
`options` is the current options map, and `zloc` is the zipper that
can be modified.  The `<fn>` should return a zipper which is an
alteration of `zloc`.  See the file `rewrite.cljc` for the current
implementation of `:sort-dependencies` as an example.

This whole capability is largely firmed up.  There are two styles
that use this capbility at present: `:sort-dependencies` and `:sort-require`.

#### :respect-bl? _false_

This will cause zprint to respect incoming blank lines. If this is
enabled, zprint will add newlines and remove newlines as necessary,
but will not remove any existing blank lines when formatting lists.
Existing formatting configuration will be followed, of course with
the constraint that existing blank lines will be included wherever
they appear.  Note that blank lines at the "top level" (i.e., those
outside of `(defn ...)` and `(def ...)` expressions) are always
respected and never changed. `:respect-bl?` controls what happens
to blank lines __within__ `defn` and `def` expressions.

If you wish to use zprint to enforce a particular format, using
`:respect-bl?` might be a bad idea -- since it depends on the
incoming source with regard to blank lines.

If you use blank lines a lot within function definitions in order
to make them more readable, this can be a good capability to enable
globally.

#### :respect-nl? _false_

This will cause zprint to respect incoming newlines. If this is
enabled, zprint will add newlines, but will not remove any existing
newlines when formatting lists.  Existing formatting configuration
will be followed, of course with the constraint that existing
newlines will be included wherever they appear.

#### :nl-count _nil_

This is the number of newlines to put between the elements of the list.

It does not trigger a flow (you would use `:hang? false` for that).

It is either a number of newlines (where 2 would be how to get one
blank line), or a vector of the number of newlines, where `[1 2 3]` would
give you a normal newline for the first, and one blank line between the second
and third elements, and then two blank lines between all remaining elements.
The final value is repeated as necessary if there are not "enough" for the
number of elements in the list.  Comments are kept with the following
elements, so the next element in the vector is only used when it isn't
a comment.  

Note that there is always a single newline before the
list -- the `:nl-count` is used only after the first element.  Note also 
that the first few elements of any list which starts with a function
that has a `fn-type` in the `:fn-map` are formatted explicitly and do
not interact with the `:nl-count`.  The `:nl-count` is only used after
the initial few elements are formatted as specified by the `fn-type`.

This capability is something that is building block to get particular
formatting in some special cases where a `fn-type` is in use`.
It isn't meant to be a generally useful capabiity to, say, 
format all lists with extra lines between them.


#### :nl-separator? _false_

This only has meaning when using the `:list` function type.  In
that case, if it is true, every list element that requires more
than one line will have an additional newline after it, resulting
in a blank line.  In this case (where `:nl-separator? true`),
`:nl-count` (if set) will be used as the number of newlines to use
after some element that takes more than one line.  If `:nl-count`
is unset, the default is 2, but you can set it to be anything you
wish.  In this situation, a vector value for `:nl-count` is not
allowed, and if a vector is configured, the value 2 is used instead.

`:list` supports some of the same keys as does vector:

##### :no-wrap-after _nil_
##### :wrap? _true_
##### :wrap-coll? _true_
##### :wrap-after-multi? _true_

See the section on :vector for information on these keys.  A simple example:

```
(czprint i252d {:parse-string? true})
(stuff example
       vvvvvveeeeeeeerrrrrrryyyyy
       looooooooooooonnnnggggg
       paraaaaaaams
       &
       body)

(czprint i252d {:parse-string? true :list {:wrap? true}})
(stuff example vvvvvveeeeeeeerrrrrrryyyyy looooooooooooonnnnggggg paraaaaaaams &
  body)

(czprint i252d {:parse-string? true :list {:wrap? true :no-wrap-after #{"&"}}})
(stuff example vvvvvveeeeeeeerrrrrrryyyyy looooooooooooonnnnggggg paraaaaaaams
  & body)
```

Generally, `&` isn't something with special meaning in a list, but there
may be something else that you wish to not dangle as the last element on
a line when a list is being wrapped.

##### :option-fn _nil_

You can configure an `:option-fn` which will run whenever a list is
encountered, and can return a new options map or nil.  The option-fn is
an actual Clojure function which is given access to the list.  Typically
you would not configure a `:list {:option-fn ...}` for all lists, but would
instead include in an options map associated with the `:fn-map`.  If you look
at the `:style-map` you will see many of the styles are implemented using
`:option-fn`s and the `:fn-map` only configures most of those styles to be
in play when certain functions are being formatted.

The calling sequence for an `:options-fn` is: 

```
(fn [options element-count non-whitespace-non-comment-element-seq] ... )
```
where the third argument is a Clojure sequence of the essential elements
of the list -- that is, the ones that are not comments or whitespace.  This
allows the Clojure code of the `:option-fn` to easily make decisions about
how to format this list.

The value of the `:option-fn` is a map containing *only* changes to the
current options map.  Do *NOT* make changes to the input options map 
and return that as the result of the `:option-fn` call!

Note that to aid debugging, all `:option-fn`s should have two arities:

```
(fn
  ([] "option-fn-name")
  ([options n exprs]
    (...)))
```

The name as a string should be returned from the zero arity call, allowing
zprint to recover the function name when outputing debugging messages.


In addition to the normal arguments, there are some additional things passed
in options map:  

  - `:zloc` The current structure or zipper
  - `:l-str` A string which is the current left enclosing parenthesis (usually)
  - `:r-str` A string which is the current right enclosing parenthesis (usually)
  - `:ztype` Either `:sexpr` for structure or `:zipper` for source.
  - `:caller` The caller, to allow examining the options maps for configuration



In addiiton to the above elements being added to the options map prior to 
calling the `:option-fn`, the following elements are recovered from the
returned options map and used if they are non-nil:

  - `:new-zloc` A structure or zipper which should have been modified.
  - `:new-l-str` A new string for the left enclosing parenthesis
  - `:new-r-str` A new string for the right enclosing parenthesis

`new-zloc`, `new-l-str`, and `new-r-str` output from an `:option-fn` 
is __EXPERIMENTAL__

Very few `:option-fn`s actually return a `:new-zloc`, `:new-l-str`, or 
`:new-r-str`, but the capability exists.  It is relatively easy to write
Clojure code to modify a structure, but that is only useful at the REPL.
In order to modify Clojure source code, you need to operate on the zippers
produced by the `rewrite-clj V1` Clojure parser library.  Looking at the
contents of 
zippers is not that hard, but making substantial modifications and dealing
with the existance of things like comments is challenging.  It can be done,
but debugging it can take a long time.

Writing an `:option-fn` to return an options map with formatting information
is a perfectly reasonable thing to do.  You will want to avoid writing an
`:option-fn` to actually modify the data being formatted if at all possible.

_____
## :map

Maps support both the __indent__ and __hang__ values, above.  The default
`:hang-expand` value is `1000.0` because maps  don't look bad with a large
hangs.

##### :indent _2_
##### :hang? _true_
##### :hang-expand _1000.0_
##### :hang-diff _1_
##### :justify? _false_
##### :justify {:max-variance 1000, :ignore-for-variance nil, :no-justify nil, :lhs-narrow 2.0 :max-depth 100}
##### :multi-lhs-hang? _false_

#### :flow? _false_

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
#### :nl-separator? _false_

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

####  :comma?  _true_

Put a comma after the value in a key-value pair, if it is not the
last pair in a map.

#### :force-nl? _false_

Force a new-line between each key and value pair in a map.

```clojure
(czprint {:abc :def :ghi :ijk})

{:abc :def, :ghi :ijk}

(czprint {:abc :def :ghi :ijk} {:map {:force-nl? true}})

{:abc :def,
 :ghi :ijk}

```

#### :sort? _true_

Sort the key-value pairs in a map prior to output.  Alternatively, simply output
them in the order in which they come out of the map.

#### :sort-in-code? _false_

If the map appears inside of a list that seems to be code, should it
be sorted.

#### :key-order _nil_

Accepts a vector which contains keys which should sort before all
other keys and after all other keys.  
This __only__ has an effect if sorting of keys is 
specified (see `:sort?` and `:sort-in-code?`) and is actually happening.

Typically these keys would be keywords, strings, or
integers.  The value of this capability is to bring one or more
key-value pairs to the top of a map when it is output, in order to
aid in visually distinguishing one map from the other. Alternatively,
you can use this capability to move some keys to the bottom of a map
when it is output.  Either can
be a significant help in debugging, when looking a lot of maps at
the repl.  Note that `:key-order` only affects the key order when
keys are sorted.

The vector given to `:key-order` contains keys that will sort to
the top of a map by default.  There is a distinghished  separator 
keyword `:|` such that keys specified after that keyword will sort to
the bottom of a map.  If you actually have a keyword `:|`, you cannot
affect where it will sort by using the `:key-order` capability.

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

If you want to have some keys sort to the bottom of a map, you can
use the special keyword `:|` like this:

With this options map:

```
{:map {:key-order [:type :direction :| :detail]}}
```

You get this output:

```
[{:type "get",
  :direction :received,
  :code "58601",
  :connection "2795",
  :reference 14873,
  :time 1425704001,
  :detail {:alternate "64:c1:2f:34",
           :ident "64:c1:2f:34",
           :interface "3.13.168.35",
           :string "datacenter"}}
 {:type "post",
  :direction :transmitted,
  :code "0xe4e9",
  :connection "X13404",
  :reference 14133,
  :time 1425704001,
  :detail
    {:code "0xe4e9", :ident "64:c1:2f:34", :ip4 "3.13.168.151", :time "30m"}}
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
  :reference 14133,
  :time 1425704003,
  :detail {:code "0x1344a676", :ident "50:56:a5:1d:61", :ip4 "3.13.171.81"}}
 {:type "error",
  :direction :transmitted,
  :code "323266166",
  :connection "2796",
  :reference 14873,
  :time 1425704003,
  :detail {:alternate "01:50:56:a5:1d:61",
           :ident "50:56:a5:1d:61",
           :interface "3.13.168.35",
           :string "datacenter"}}]
```

When working with hundreds of maps, even the tiny improvement
made by ordering a few keys in a better way can reduce the cognitive
load, particularly when debugging.

#### :key-ignore _nil_
#### :key-ignore-silent _nil_

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

####  :key-color  _nil_

The value of `:key-color` is a map which relates keys that are
'constants' to a color in which to print that key.  A constant is
a keyword, string, or number.  This way you can have some keys
formatted in a color that is different from the color in which they
would normally be formatted based on their type.  It can go well
with `:key-order [:key1 :key2 ...]` which is another way to distinguish
a special key.  You can place some keys at the front of the map and
you can also adjust their colors to meet your needs.

####  :key-value-color  _nil_

The value of `:key-value-color` is a map which relates keys (that
don't have to be constants) to a color-map which is merged into the
current color-map, and is used when formatting the __value__ of that key.
This way you can have the values of some keys formatted in a color that
is different from the color in which they would normally be formatted
based on their type.

####  :key-depth-color  _nil_

Note that this is an EXPERIMENTAL feature.  The value of `:key-depth-color` is
a vector of colors, and these colors will be used to color the keys which
are at a corresponding depth in the map.  Thus, the first color in the vector
will be the color for the outermost keys in the map, the second color in
vector will be the color for the keys.  You can place a `nil` in the vector,
and for that depth the normal colors (based on the type of the key) will
be used.  If you also have defined a `:key-color` map, any colors speciied
in that map for specific keys will override the color that they would be
given by the `:key-depth-color` vector.

####  :key-value-options  _nil_

The value of `:key-value-options` is a map which relates map keys to options
maps used to format the values of those map keys.  This capability, had
it been around earlier, would make :key-value-color` unnecessary.  
It would also have made `:key-ignore` unnecessary, as the options map
for a specific key could specify a `:max-length` which was very short.
While it seems simple, this is a particularly powerful formatting
capability which can be used to alter the formatting of maps in many 
interesting ways!

####  :key-no-sort  _#{"..."}_

The value of `:key-no-sort` is either nil or a set of strings which,
if they match the string value of a key, will cause sorting of the
map in which it appears to be disabled.  While it can be used for
any key for which you would like to disable sorting of its containing
map, it is particularly useful when paired with `{:parse
{:ignore-if-parse-fails #{ }}}` elements.

####  :lift-ns?  _false_

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

####  :lift-ns-in-code?  _false_

Controls whether to actually lift the namespace if the map is in code.

####  :unlift-ns?  _false_

This only applies when dealing with formatting code or strings.  When
the map was specified with the namespace "lifted", then distribute the
namespace across the keys.

For example:
```clojure
(zprint ":x{a :b :c :d}" {:parse-string? true :map {:lift-ns? false :unlift-ns? true}})
{:x/a :b, :x/c :d}
```

Note that `:unlift-ns? true` only works if `:lifts-ns? false` is present,
since otherwise zprint won't know which keyword to honor, and `:lift-ns?` was
there first.

#### :indent-only? _false_

Do not add or remove newlines.  Just indent the lines that are there and
regularize whitespace.
This forces the indent for maps to be zero, which means that every key
is indented identically since there is no assumption that key-value pairs
are placed on lines in any particular way.   Note that commas are not
added, but existing commas will be included if `:comma?` flag is true.

#### :respect-bl? _false_

This will cause zprint to respect incoming blank lines. If this is
enabled, zprint will add newlines and remove newlines as necessary,
but will not remove any existing blank lines when formatting maps.
Existing formatting configuration will be followed, of course with
the constraint that existing blank lines will be included wherever
they appear.  Note that blank lines at the "top level" (i.e., those
outside of `(defn ...)` and `(def ...)` expressions) are always
respected and never changed. `:respect-bl?` controls what happens
to blank lines in maps __within__ `defn` and `def` expressions.

If you wish to use zprint to enforce a particular format, using
`:respect-bl?` might be a bad idea -- since it depends on the
incoming source with regard to blank lines.

#### :respect-nl? _false_

This will cause zprint to respect incoming newlines. If this is
enabled, zprint will add newlines, but will not remove any existing
newlines when formatting lists.  Existing formatting configuration
will be followed, of course with the constraint that existing
newlines will be included wherever they appear.

_____
## :meta

Affects how metadata elements are processed.

#### :split? _false_

Normally metadata elements are parsed and processed as a single
unit with two elements, the map or keyword for the metadata, and
the symbol to which the metadata is attached.  If you configure
`:split?` to be `true`, these two elements are disconnected from
each other, and they will be handled separately (just like they
appear to be in the code).

Thus, for a `def` with metadata in a map, if the `:meta` is configured
as `:split?`, then the map for the metadata will appear in the
`:arg1-body` position, and the symbol will appear flowed below it.
The same holds true for a keyword for the metadata.

_____
## :object

When elements are formatted with `:object?` `true`, then the output
if formatted using the information specified in the `:object`
information.

Note that `:respect-nl?`, `:respect-bl?`, and `:indent-only?` are not
supported independently for `:object` -- when objects
are processed, the values for `:respect-nl?`, `:respect-bl?` and
`:indent-only?` for `:vector` are used.


##### :indent _1_

##### :wrap-after-multi? _true_

Same as `:vector`.

##### :wrap-coll? _true_

Same as `:vector`.
_____
## :output

This controls the overall output that is produced.

#### :focus
Determines whether to highlight a part of the structure, and which
part to highlight. Only one of `:zloc?` or `:path` can have a value.

Note: `:focus` is not supported with `{:style :indent-only}.

Contains a map with the following possible keys.
##### :zloc? _false_
If true, indicates that the first argument is a zipper, and the zipper
currently "points at" the expression at which to focus.  zprint will
print the entire zipper, and highlight the expression at which the
zipper is currently pointing.
##### :path _nil_
The path is a vector of integers, which indicates where the focus
should be placed.  Each number in the vector indicates moving into
a structure, and the value of the number indicates the element within
that structure on which the focus rests.  Presently, the error
handling for bad paths is some sort of exception.

If you have a structure like this: `[:a [:b [:c :d] :e :f]]`
then the path `[1 1 0]` would highlight the `:c`.  The path `[1 1]` would
highlight the `[:c :d]`.  The path `[0]` would highlight the `:a`.

#### :format _:string_

__EXPERIMENTAL__

Controls the format of the output.  The default is `:string`, which is
what zprint has always produced until release `1.2.4`.  The other
options are `:hiccup` and `:html`.  These other options are only supported for
the library fns ending in `-str`, and the pre-built binaries (which also
use `zprint-file-str` to produce their output).  This is because these
are the only fns whose return values are meaningful.  In the case of 
`{:output {:format :hiccup}}`, the return isn't actually a string, but
rather a vector of hiccup structures.  It was designed to be compatible
with `hiccup.core/html`.  Note that `{:color? true}` is also supported
for all of the `-str` fns and the pre-built binaries, so that you can
have your hiccup or HTML output colored or not.

This capability is __not__ supported for most uses of `:range` in
`zprint-file-str`.  It is, however, supported for the case where
`:range` is in use, and `:output {:range? true}` is used.  This is
where the only output returned is for the specfied (or really, expanded)
range.

The hiccup/html output is wrapped in a single paragraph.  The style
for that paragraph is a string which you can configure.

##### :paragraph _{:style "font-size:20px;font-family: Lucidia Concole, Courier, monospace"}_

This is the style for the paragraph wrapping any hiccup or HTML output.  You
can configure this string to be anything you want.  Of course, if the font
is not a monospace font, the results will be ... probably not what you wanted.

The font size has no relation to the number of characters in a line of
output, which is still controlled by `{:width ...}`.

#### :real-le? _false_
Determines whether to output actual line endings (i.e. `real-le`) instead
of escape characters when they appear within Clojure(script) strings.

The line endings handled by `:real-le?` are these:
```
        (clojure.string/replace "\\n" "\n")
        (clojure.string/replace "\\r\\n" "\r\n")
        (clojure.string/replace "\\r" "\r")))
```

This will turn "\n" into a string containing a single newline character.
If, of course, the `:real-le-length` were set to 2 or less.  Note that
the default for `:real-le-length` is 20.

__NOTE: Avoid enabling this for code, as strings like `"\n"` will be replaced
with quotes around an actual newline character if the `:real-le-length` is
2 or less.  This feature is designed to be used when creating output, not
for code.__

#### :real-le-length _20_

If `:real-le? true` is enabled, escaped line endings will be replaced by
the actual line endings. See `:real-le?` for which line endings will be
changed.  The value in the `:real-le-length` is that only line endings
in strings whose length is equal to or greater than `:real-le-length`
will be converted.  

__NOTE: Avoid enabling this in code, as any string in the code which
contains any of the line endings given above in `:real-le?` will be changed
to the actual ASCII characters for the line ending.  This is almost
certainly NOT what you want when formatting code__.  

#### :range? _false_

Only recognized when calling `zprint-file-str` (which is what all of
the pre-built binaries use), and where there is a specification of an
input range, as in: `{:input {:range {:start n :end m}}}`.

The purpose of the `{:output {:range? true}}` capability is to
allow zprint to be given a stream of lines and a range specification
for what to format within that stream.  Then zprint will figure out
the range of lines which encompasses the smallest top-level expression
or expressions covered by the specified range, and will return the formatted
output for those lines in addition to reporting the actual range of lines
used to create the formatted output. 

The output when `:range?` is `true` consists of a vector where
the first element is a map describing the range actually used, and the
second element is a string which is the result of formatting that
range from the input.

Specifically:

```
[{:range {:actual-start s :actual-end t}} string-or-nil]
```
The second element of the vector will be a string (possibly null) unless
the `actual-start` and `actual-end` are both -1, indicating nothing
was formattd, in which case the second element will be nil.

For example, for a file which looks like this:

```
0
1 (defmacro diff-com
2 "Is community formatting different?"
3 [f]
4 `(if (=(zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))
5 "true"
6 (zprint-fn-str ~f)))
7
8 ;!zprint {:format :next :width 25}
9
10 (defn ortst
11 "This is a test"
12 {:added 1.0 :static true}
13 ([x y]
14 (or (list
15 (list
16 (list
17 y
18 (list x))))
19 ())))
20
21 #?(:clj (defn zpmap
22 ([options f coll]
23 (if (:parallel? options) (pmap f coll) (map f coll)))
24 ([options f coll1 coll2]
25 (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))
26 :cljs (defn zpmap
27 ([options f coll] (map f coll))
28 ([options f coll1 coll2] (map f coll1 coll2))))
```
Then this invocation of `zprint-file-str`:
```
(zprint-file-str range1 "stuff" {:input {:range {:start 11 :end 12}} :output {:range? true}})
```
will yield this ouput:
```
[{:range {:actual-start 8, :actual-end 19}} ";!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n"]
```
You can see that the input range has been expanded to cover the
entire top-level function definition, as well as the `;!zprint`
directive in the comment above it.

The string output looks like this:

```
0 ;!zprint {:format :next :width 25}
1
2 (defn ortst
3   "This is a test"
4   {:added 1.0,
5    :static true}
6   ([x y]
7    (or (list
8          (list
9            (list y
10                  (list
11                    x))))
12        ())))
```

and is one line longer than the difference between the
`actual-start` and `actual-end`.  The `actual-start` and
`actual-end` relate to the lines selected for formatting in the input
stream, and in particular the number of lines in the formatted
output string have no relation to the number of lines between the
`actual-start` and `actual-end`.  It may be larger, smaller, or
the same.

If the input range specification selects only blank lines or
top level comments in the input, then there is no formatting
to be performed, and in this case the output will be:
```
[{:range {:actual-start -1 :actual-end -1}} nil]
```
which is an indication that nothing has changed.

The purpose of this capability is to ease integration of zprint
into an extension for an editor or an IDE.  You can pass in the
`{:input {:range {:start m :end n}}}` without regard to whether
or not they encompass entire top level expressions, and zprint will
figure out how to expand them to cover the minimally correct number
of lines beyond the start and end.  Using `{:output {:range? true}}`,
zprint will tell you the lines that it figured out, and will return
just the formatted output related to those lines.

Were you to use this capability to integrate with an editor or IDE,
you would replace the lines `:actual-start` through `:actual-end`
in the input document with the formatted output returned in the
string.

If `{:input {:range {:continue-after-!zprint-errors? true}}}` is 
configured, any errors encountered during the processing of `;!zprint`
directives are returned in a vector which is the value of the `:errors`
key in the `:range` map returned when `{:output {:range? true}}`
is configured.

______
## :pair

The :pair key controls the printing of the arguments of a function
which has -pair in its function type (e.g. `:arg1-pair`, `:pair-fn`,
`:arg2-pair`).  `:pair`

##### :indent _2_
##### :hang? _true_
##### :hang-expand _2_
##### :hang-diff _1_
##### :justify? _false_
##### :justify {:max-variance 1000, :ignore-for-variance #{":else"}, :no-justify nil, :lhs-narrow 2.0 :max-depth 100}
##### :multi-lhs-hang? _false_

#### :force-nl? _false_

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
#### :flow? _false_

Format the right-hand part of every pair to onto a different
line from the left-hand part of every pair.

#### :nl-separator? _false_

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

If the left hand side of a pair is a keyword whose symbol appears
in the `:fn-map` (and is therefore likely to be a built-in function)
and it has a vector following it will have that vector formatted
as a binding vector.  In addition, every left hand side keyword
whose symbol appears in the `:fn-map` but does not have a vector
following it, will be formatted in such a way that the expr after
it will be on the same line if at all possible, regardless of the
settings for how to manage pairs.

_____
## :pair-fn

The :pair-fn key controls the printing of the arguments of a function
which has :pair-fn as its function type (e.g. `cond`).

##### :hang? _true_
##### :hang-expand _2_
##### :hang-diff _1_

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
## :parse

This controls several aspects of how the input is handled.

#### :interpose _nil_

When a file is being processed by `zprint-file-str` (which is what
the pre-built binaries use, and is also available in the library) or
by other library routines when `:parse-string-all? true` is used,
normally the blank lines between top level
expressions are untouched.

However, you can force a fixed amount of space between top level expressions
by using `:parse :interpose`.  You can use `{:parse {:interpose "\n\n"}}`
in an options map, which will place one blank line between every top
level expression.

You can force top level expressions to have pretty
much anything that ends with a new-line by including an options
map with `:parse {:interpose <string>}` in it.  The `<string>` must end
with a new-line, or the resulting formatting will not be correct.

```clojure
(czprint "(def a :b) (def c :d)" 40 {:parse-string-all? true :parse {:interpose "\n\n"}})
(def a :b)

(def c :d)
```

#### :left-space _:drop_


The choices for `:left-space` are `:keep` and `:drop`, with `:drop` being
the default. `:left-space :keep` will keep any spaces on a line prior to a 
top level expression. 
This is ignored when `:interpose` is anything but `false`.

#### :ignore-if-parse-fails _#{"..."}_

Some incomplete maps (that is, a map with an odd number of elements) will 
go through some level of the parsing succefully, 
but later processing steps may cause failures when an incomplete map is
further processed by the parsing engine.  If you can identify the 
additional element, you can place the string value of the element
in the set which is the value of `:ignore-if-parse-fails`.  The default
value contains `"..."` so that a map that is input with an additional
key value of `...` will be processed correctly.

_____
## :reader-cond

Reader conditionals are controlled by the `:reader-cond` key.  Many
of the keys which are supported for `:map` are supported for `:reader-cond`
(except `:comma?`), albeit with different defaults.  By default,
`:sort?` is nil, so the elements are not reordered.  You could enable
`:sort?` and specify a `:key-order` vector to order the elements of a
reader conditional.  

Note that `:respect-nl?`, `:respect-bl?`, and `:indent-only?` are not
supported independently for `:reader-cond` -- when reader conditionals
are processed, the values for `:respect-nl?`, `:respect-bl?` and
`:indent-only?` for `:map` are used.

##### :indent _2_
##### :hang? _true_
##### :hang-expand _1000.0_
##### :hang-diff _1_
##### :force-nl? _true_
##### :sort? _false_
##### :sort-in-code? _false_
##### :key-order _nil_

_____
## :record

Records are printed with the record-type and value of the record
shown with map syntax, or by calling their `toString()` method.

#### :to-string? _false_

This will output a record by calling its `toString()` java method, which
can be useful for some records. If the record contains a lot of information
that you didn't want to print, for instance. If `:to-string?` is true,
it overrides the other `:record` configuration options.

#### :hang? _true_

Should a hang be attempted?  See example below.

#### :record-type? _true_

Should the record type be output?

An example of `:hang?`, `:record-type?`, and `:to-string?`

```clojure
(require '[zprint.core :as zp])

(defrecord myrecord [left right])

(def x (new myrecord ["a" "lot" "of" "stuff" "so" "that" "it" "doesn't" "fit" "all" "on" "one" "line"] [:more :stuff :but :not :quite :as :much]))

(zp/zprint x 75)

#zprint.core.myrecord {:left ["a" "lot" "of" "stuff" "so" "that" "it"
                              "doesn't" "fit" "all" "on" "one" "line"],
                      :right [:more :stuff :but :not :quite :as :much]}

(zp/zprint x 75 {:record {:hang? nil}})

#zprint.core.myrecord
 {:left ["a" "lot" "of" "stuff" "so" "that" "it" "doesn't" "fit" "all" "on"
         "one" "line"],
  :right [:more :stuff :but :not :quite :as :much]}

(zp/zprint x 75 {:record {:record-type? nil}})

{:left ["a" "lot" "of" "stuff" "so" "that" "it" "doesn't" "fit" "all" "on"
        "one" "line"],
 :right [:more :stuff :but :not :quite :as :much]}

(zp/zprint x {:record {:to-string? true}})

"zprint.core.myrecord@682a5f6b"
```
_____
## :script

This controls what __additional__ options are added when a 
string processed by `zprint-file-str` is determined to be a "script".

Note that `zprint-file-str` is the basic driving function used by
all of the pre-compiled binaries to process entire files.

A script is defined as any string (or file) whose first line
begins with "#!".  When scripts are processed, the first line
is removed, zprint is run on the remaining lines, and the first
line is restored prior to returning the result.

#### :more-options _nil_

This is a options map which will be applied on top of any existing
options currently in force when a string processed by `zprint-file-str`
is determined to be a script.  It is the last set of options applied,
after any that might be on a command line to one of the pre-compiled
binaries.  It does not replace any existing options, it is an options
map applied on top of any existing options.

_____
## :set

`:set` supports the same keys as does vector and a few more.

##### :indent _1_
##### :no-wrap-after _nil_
##### :wrap? _true_
##### :wrap-coll? _true_
##### :wrap-after-multi? _true_

#### :indent-only? _false_

Do not add or remove newlines.  Just indent the lines that are there and
regularize whitespace.

#### :respect-bl? _false_

This will cause zprint to respect incoming blank lines. If this is
enabled, zprint will add newlines and remove newlines as necessary,
but will not remove any existing blank lines when formatting sets.
Existing formatting configuration will be followed, of course with
the constraint that existing blank lines will be included wherever
they appear.  Note that blank lines at the "top level" (i.e., those
outside of `(defn ...)` and `(def ...)` expressions) are always
respected and never changed. `:respect-bl?` controls what happens
to blank lines in sets __within__ `defn` and `def` expressions.

If you wish to use zprint to enforce a particular format, using
`:respect-bl?` might be a bad idea -- since it depends on the
incoming source with regard to blank lines.

If you use blank lines a lot within sets embedded in function
definitions in order to make them more readable, this can be a good
capability to enable globally.

#### :respect-nl? _false_

This will cause zprint to respect incoming newlines. If this is
enabled, zprint will add newlines, but will not remove any existing
newlines when formatting sets.  Existing formatting configuration
will be followed, of course with the constraint that existing
newlines will be included wherever they appear.


#### :sort? _true_

Sort the elements in a set prior to output.  Alternatively, simply output
them in the order in which they come out of the set.

#### :sort-in-code? _false_

If the set appears inside of a list that seems to be code, should it
be sorted.

_____
## :spec

`:spec` controls how specs are integrated into the `(zprint-fn ...)` and
`(czprint-fn ...)` output.  This only operates if the version of Clojure
supports specs, and the function being output _has_ a spec.  If
that is true, and if:

##### :docstring? _true_

is also true, then zprint will format the spec and append it to the
docstring.  The spec formatting is effectively identical to hand-formatted
specs.

______
## :style and :style-map

You specify a style by adding `:style <style>` at the top level
of the options map.  You can also set more than one style by
enclosing the styles in a vector, for example:

```clojure
(set-options! {:style [:binding-nl :extend-nl]})
```
When multiple styles are specified, they are applied in the order
given.  Generally styles will compose well.  

The only cases where
styles don't compose well with each other are when two styles
set an `:option-fn` for a datatype like `:list` or `:vector`
unconditionally.  Typically, styles that set `:option-fn` do so
in option maps associated with the `:fn-map`.  These kinds of
styles will compose well together.  It is when the `:option-fn` for
`:list` or `:vector` is set unconditionally by the style itself that
problems with composition occur.  Only a few styles do this: `:hiccup`,
`:odr` today.

There are three phases of processing an options map:

  1. Any changes to the `:style-map` are processed first.
  2. If a `:style` is specified, the changes to the style map associated
  with that `:style` are processed.
  3. The remaining changes to the options map are processed.

So, you can define a new `:style` in the `:style-map`, and then use
it with `:style`, and then override some of its settings -- all in
the same `.zprintrc` or `set-options!` call.

You can also define one style in terms of another style.  You will receive
an exception if you specify a `:style` which uses another style and ends up 
using the same style twice in the same invocation.

### Available Styles:

#### :all-hang

Some implementations of zprint into runnable programs turn off all hangs
by default, since performance is rather better with them off.  If you
are using one of these programs, you can turn all of the hangs back
on (which is their normal default) by using this style.

#### :anon-fn

When an anonymous fn is compiled, alll `#(...)` forms are turned into
`(fn* ...)` forms.  If you then format the compiled structure, it looks
pretty bad.  If, instead, when you format the compiled structure you
use `:style :anon-fn`, zprint will backtranslate the stucture to 
reconstitute the `#(...)` forms.

#### :areguide

A style that will format `are` functions nicely.  By default it justifies
the list of cases, but can be configured (with a `:style-call`) to not justify
the list.  By default, the `are` function uses the style `:areguide` which
includes justification.  If you wish it to not justify, add this to
the option map:

```
{:fn-map {"are" [:none {:style-call :areguide :justify? false}]}}
```

Alternatively, you can use `{:style :areguide-nl}` in the `:fn-map` in place
of `{:style :areguide}`.

Some examples (drawn from `rewrite-clj`, with thanks!):

```
; This is not the default, but rather what happens to a complex "are" 
; function without using :areguide:

(czprint are11 {:parse-string? true :fn-map {"are" :none}})

(deftest t-parsing-reader-macros
  (are [?s ?t ?children]
       (let [n (p/parse-string ?s)]
         (is (= ?t (node/tag n)))
         (is (= ?s (node/string n)))
         (is (= ?children (map node/tag (node/children n)))))
       "#'a"
       :var
       [:token]
       "#=(+ 1 2)"
       :eval
       [:list]
       "#macro 1"
       :reader-macro
       [:token :whitespace :token]
       "#macro (* 2 3)"
       :reader-macro
       [:token :whitespace :list]
       "#?(:clj bar)"
       :reader-macro
       [:token :list]
       "#? (:clj bar)"
       :reader-macro
       [:token :whitespace :list]
       "#?@ (:clj bar)"
       :reader-macro
       [:token :whitespace :list]
       "#?foo baz"
       :reader-macro
       [:token :whitespace :token]
       "#_abc"
       :uneval
       [:token]
       "#_(+ 1 2)"
       :uneval
       [:list]))

; This is the default behavior, using :areguide

(czprint are11 {:parse-string? true})

(deftest t-parsing-reader-macros
  (are [?s ?t ?children] (let [n (p/parse-string ?s)]
                           (is (= ?t (node/tag n)))
                           (is (= ?s (node/string n)))
                           (is (= ?children (map node/tag (node/children n)))))
    "#'a"            :var          [:token]
    "#=(+ 1 2)"      :eval         [:list]
    "#macro 1"       :reader-macro [:token :whitespace :token]
    "#macro (* 2 3)" :reader-macro [:token :whitespace :list]
    "#?(:clj bar)"   :reader-macro [:token :list]
    "#? (:clj bar)"  :reader-macro [:token :whitespace :list]
    "#?@ (:clj bar)" :reader-macro [:token :whitespace :list]
    "#?foo baz"      :reader-macro [:token :whitespace :token]
    "#_abc"          :uneval       [:token]
    "#_(+ 1 2)"      :uneval       [:list]))

; This is what you get using :areguide without justification:

(czprint are11
         {:parse-string? true,
          :fn-map {"are" [:none
                          {:style {:style-call :areguide, :justify? false}}]}})

(deftest t-parsing-reader-macros
  (are [?s ?t ?children] (let [n (p/parse-string ?s)]
                           (is (= ?t (node/tag n)))
                           (is (= ?s (node/string n)))
                           (is (= ?children (map node/tag (node/children n)))))
    "#'a" :var [:token]
    "#=(+ 1 2)" :eval [:list]
    "#macro 1" :reader-macro [:token :whitespace :token]
    "#macro (* 2 3)" :reader-macro [:token :whitespace :list]
    "#?(:clj bar)" :reader-macro [:token :list]
    "#? (:clj bar)" :reader-macro [:token :whitespace :list]
    "#?@ (:clj bar)" :reader-macro [:token :whitespace :list]
    "#?foo baz" :reader-macro [:token :whitespace :token]
    "#_abc" :uneval [:token]
    "#_(+ 1 2)" :uneval [:list]))

; This is also using :areguide without justification, but using a different
; style instead of a :style-call

(czprint are11
         {:parse-string? true, :fn-map {"are" [:none {:style :areguide-nj}]}})

(deftest t-parsing-reader-macros
  (are [?s ?t ?children] (let [n (p/parse-string ?s)]
                           (is (= ?t (node/tag n)))
                           (is (= ?s (node/string n)))
                           (is (= ?children (map node/tag (node/children n)))))
    "#'a" :var [:token]
    "#=(+ 1 2)" :eval [:list]
    "#macro 1" :reader-macro [:token :whitespace :token]
    "#macro (* 2 3)" :reader-macro [:token :whitespace :list]
    "#?(:clj bar)" :reader-macro [:token :list]
    "#? (:clj bar)" :reader-macro [:token :whitespace :list]
    "#?@ (:clj bar)" :reader-macro [:token :whitespace :list]
    "#?foo baz" :reader-macro [:token :whitespace :token]
    "#_abc" :uneval [:token]
    "#_(+ 1 2)" :uneval [:list]))
```

#### :backtranslate

The built in pretty printer for Clojure, `clojure.pprint`, will
backtranslate `(quote a)` to `'a`, `(var a)` to `#'a`, 
`(clojure.core/deref a)` to `@a` and `(clojure.core/unquote a)` to
`~a`.  `clojure.pprint` only does this when printing data structures, (which
may be code), since that is all it operates on.  Zprint has been
enhanced to optionally perform this backtranslation when formatting
structures.  Use `{:style :backtranslate}` to get this behavior, and
see the definition of that style to see how it was implemented.
Even if you use `{:style :backtranslate}`, there is no change to the
way that source is formatted since zprint has been enhanced to 
distinguish between source and structures in some situations.  You could
configure zprint to do this backtranslation for source if you wished to,
though there is not a pre-defined style to enable that operation.
See the implementation for `{:style :backtranslate}` for a hint for how
to do this (or open an issue and ask). 

#### :community

This attempts to recreate the community standards defined in the
[community style guide](https://github.com/bbatsov/clojure-style-guide).
It is an evolving effort -- if you see something that matters to you
that differs from the community style guide when using `{:style :community}`,
please create an issue explaining the difference.

For more discussion, see [Community](./options/community.md).

#### :dark-color-map

Sets both the `:color-map` and the `:uneval {:color-map ...}` to
colors which are visible when using a dark background.  These may
not be your favorite color choices, but at least things should be
visible, allowing you to fine-tune the colors to better meet your
preferences.

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

This will format `ns` declarations regarding newlines and indentation
as in Stewart Sierra's "How to ns".  Specifically, it will indent
lists by 1 instead of 2, and not hang lists except for `:import`.
If you follow the instructions in the "How to ns" blog post, the
new lines and indentation will be correct.  zprint will not reorganize
the `ns` declaration or change lists to vectors or otherwise change
the order or syntax of what you have entered -- that's still your
responsibility.

#### :hiccup

Recognizes when the information in a vector is in `hiccup` format,
and format just those vectors differently in order make the hiccup
information more understandable.

```clojure

; Here is some hiccup without using this style:

(czprint-fn header)

(defn header
  [{:keys [title icon description]}]
  [:header.container
   [:div.cols {:class "gap top", :on-click (fn [] (println "tsers"))}
    [:div {:class "shrink"} icon]
    [:div.rows {:class "gap-xs"}
     [:dov.cols {:class "middle between"} [:div title]
      [:div {:class "shrink"} [:button "x"]]] [:div description]]]])

; Here is the same hiccup using the :hiccup style:

(czprint-fn header {:style :hiccup})

(defn header
  [{:keys [title icon description]}]
  [:header.container
   [:div.cols {:class "gap top", :on-click (fn [] (println "tsers"))}
    [:div {:class "shrink"} icon]
    [:div.rows {:class "gap-xs"}
     [:dov.cols {:class "middle between"}
      [:div title]
      [:div {:class "shrink"} [:button "x"]]]
     [:div description]]]])

; Not a huge difference, but note the use of :arg1-force-nl to clean up
; the display of the elements beyond the map in each vector.  Were this more
; complex hiccup, the difference would be more valuable.

```

#### :import-justify

Recognizes this key-value pair in a `{:style-call :import-justify}`

__:max-variance__ _1000_  

This will clean up and justify the `(:import ...)` section of the
`ns` macro.  

This is the basic formatting for the `ns` macro:

```
(czprint replns1 {:parse-string? true})
(ns ^:no-doc zprint.example
  (:use [clojure.repl])
  (:require [zprint.zprint :as zprint :refer
             [line-count max-width line-widths determine-ending-split-lines]]
            [zprint.comment :refer
             [blanks length-before get-next-comment-group
              style-lines-in-comment-group flow-comments-in-group]]
            [zprint.optionfn :refer [rodfn]]
            clojure.string
            [clojure.data :as d]
            [clojure.java.io :refer [reader]]
            [zprint.finish :refer [within?]]
            [clojure.zip :as zip]
            #_[sci.core :as sci]
            [rewrite-clj.parser :as p :refer [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only
             [edn* down* up* left* right* prev* string]]
            [clojure.pprint :refer [pprint]]
            #_[better-cond.core :as b])
  (:import (java.io InputStreamReader
                    OutputStreamWriter
                    FileReader
                    LineNumberReader
                    PushbackReader
                    FileWriter
                    BufferedReader
                    File
                    FileInputStream)
           (java.net URL URLConnection)
           (java.util.concurrent Executors)
           (java.util Date)
           #_(clojure.lang RT))
  (:gen-class))
```

This is the formatting for the `ns` macro with style `:import-justify`:

```
(czprint replns1 {:parse-string? true :style :import-justify})
(ns ^:no-doc zprint.example
  (:use [clojure.repl])
  (:require [zprint.zprint :as zprint :refer
             [line-count max-width line-widths determine-ending-split-lines]]
            [zprint.comment :refer
             [blanks length-before get-next-comment-group
              style-lines-in-comment-group flow-comments-in-group]]
            [zprint.optionfn :refer [rodfn]]
            clojure.string
            [clojure.data :as d]
            [clojure.java.io :refer [reader]]
            [zprint.finish :refer [within?]]
            [clojure.zip :as zip]
            #_[sci.core :as sci]
            [rewrite-clj.parser :as p :refer [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only
             [edn* down* up* left* right* prev* string]]
            [clojure.pprint :refer [pprint]]
            #_[better-cond.core :as b])
  (:import
    (java.io              InputStreamReader OutputStreamWriter FileReader
                          LineNumberReader PushbackReader FileWriter
                          BufferedReader File FileInputStream)
    (java.net             URL URLConnection)
    (java.util.concurrent Executors)
    (java.util            Date)
    #_(clojure.lang RT))
  (:gen-class))
```

This is also available together with `:require-justify` and
`:require-macros-justify` in the style `:ns-justify`.


#### :indent-only 

This is __very different__ from classic zprint!

When `:indent-only` is configured, zprint will not add or remove
newlines from the incoming source, but will otherwise regularize
whitespace.  Lists will be formatted as a hang if the incoming list
was formatted as a hang (controlled by `{:list {:indent-only-style
:input-hang}}`.  The indent for maps goes to 0.  Most of the other
configuration parameters will be ignored when `:indent-only` is
enabled.

See `:respect-nl` below for a comparison of `:indent-only`, `:respect-nl`,
and classic zprint.

For more examples, see [Indent Only](./types/indentonly.md).

#### :justified

This sets `:justify? true` in each of `:binding`, `:pair`, and `:map`.
It is useful to see what you think about justfied output.
[Here is more information on justified output.](#a-note-on-justifying-two-up-printing)

#### :justified-original

This sets `:justify? true` in each of `:binding`, `:pair`, and `:map`,
and also sets the `:max-variance` for each to 1000, restoring the
pre-1.1.2 approach to justification.
[Here is more information on justified output.](#a-note-on-justifying-two-up-printing)

#### :keyword-respect-nl

This style is used to preserve most of the existing formatting for
vectors with keywords as the first element.  The typical example was 
hiccup or rum data inside of a function or in a source file. However,
subsequent enhancements allowed the implementation of a better style
for hiccup data -- `:hiccup`. 

If you specify this style, every vector with a keyword as the first
element will preserve the newlines present in the input to zprint.
zprint will still handle the indenting, and none of the existing
newlines will cause zprint to violate its basic formatting rules
(e.g., lines will still fit in the width, etc.).  But the existing
hand-formatting will typically be preserved if it makes any sense
at all.

If you are using hiccup format, you should use the `:hiccup` style.

This is usually only useful formatting source.  Here is an example
using a `rum` macro (taken from GitHub rum/examples/rum/examples/inputs.cljc):

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
      :keyword-respect-nl {:vector
                             {:option-fn-first
                                #(let [k? (keyword? %2)]
                                   (when (not= k? (:respect-nl? (:vector %1)))
                                     {:vector {:respect-nl? k?}}))}},
```
which serves as an example of how to implement an `:option-fn-first`
function for vectors.

#### :map-nl, :pair-nl, :binding-nl

These are convenience styles which simply allow you to set `{:indent
0 :nl-separator? true}` for each of the associated format elements.
They simply exist to save you some typing if these styles are
favorites of yours.  This will add a blank line between any pairs
where the rightmost element of the pair (e.g., the value in a map key-value
pair) formats as a flow.  It will not add a blank line between every pair,
just between pairs where the rightmost element doesn't format as a hang.

Some examples:

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
#### :map-nl-all, :pair-nl-all, :binding-nl-all

These are convenience styles which simply allow you to set `{:indent
0 :nl-separator-all? true}` for each of the associated format elements.
They simply exist to save you some typing if these styles are
favorites of yours.  This will add a blank line between any pairs.

Some examples:

```clojure
; The default approach to handling a value (or second element of a pair) that 
; doesn't fit on the same line as a key (or first element).
; The second element in this case is indented for clarity.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} {:width 32})

{:a :b,
 :c
   {:e :f, :g :h, :i :j, :k :l},
 :m :n,
 :o {:p {:q :r, :s :t}}}

; If you want a blank line after every value that doesn't fit on the same
; line as the associated key.  Note that :map-nl (and all of the -nl styles)
; also set :indent 0.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} {:width 32 :style :map-nl})

{:a :b,
 :c
 {:e :f, :g :h, :i :j, :k :l},

 :m :n,
 :o {:p {:q :r, :s :t}}}

; If you want a blank line between every pair, also with :indent 0, as above.

(czprint {:a :b :c {:e :f :g :h :i :j :k :l} :m :n :o {:p {:q :r :s :t}}} {:width 32 :style :map-nl-all})

{:a :b,

 :c
 {:e :f, :g :h, :i :j, :k :l},

 :m :n,

 :o {:p {:q :r, :s :t}}}
```

This operates similarly for bindings (i.e., `let`, etc.) using 
`:style :binding-nl-all` and for pairs (i.e., things in `cond`, 
as well as constant pairs) when using `:style :pair-nl-all`.

#### :meta-alt

An alternative way to format metadata in `def` and `deftest`.  Tends
to hang just the metadata, instead of hanging the metadata along with
the symbol.

For example:

```
(czprint i224a {:parse-string? true})

(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}
         websocket-diagnostic-report-measurements-updated-event
  (let [foo (bar "1")] foo))


(czprint i224a {:parse-string? true, :style :meta-alt})

(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}
  websocket-diagnostic-report-measurements-updated-event
  (let [foo (bar "1")] foo))
```

#### :moustache

This style is designed to format moustache, self described as
"Moustache is a micro web framework/internal DSL to wire Ring
handlers and middlewares.".  The only thing this style does is to
make the function `app` have a broader definition of what constitutes
a "constant" for constant-pairing.  It adds vectors to the things
that are paired up, even though they aren't constants.  It only adds
vectors to the list of things that are paired up for the top level of
the function `app`, otherwise constant-pairing operates as normal for
expressions down inside the `app` function.

This style also gives one example of how to use the `:constant-pair-fn`
to solve real problems.

#### :multi-lhs-hang

This style sets `:multi-lhs-hang` in all of the places where it is supported:
`:pair`, `:binding`, and `:map`.  `:multi-lhs-hang` affects formatting of
the right-hand-side of pairs -- in particular, if the left-hand-side of 
a pair takes more than one line to format, should the right-hand-side of
the pair be formatting on end of the last line of the left-hand-side of
the pair (i.e., hang).  For example, look at where `(stuff a b c)` ends
up in the following examples:

```
(czprint
  "(let [(aaaaaaaaa bbbbbbbbbb ccccccccc (ddddddddddd (eeeeeeeeeee (ffffffffffff)))) (stuff a b c) (bother x y) (foo bar baz)])"
  {:parse-string? true, :binding {:multi-lhs-hang? false}})

(let [(aaaaaaaaa bbbbbbbbbb
                 ccccccccc
                 (ddddddddddd (eeeeeeeeeee (ffffffffffff))))
        (stuff a b c)
      (bother x y) (foo bar baz)])


(czprint
  "(let [(aaaaaaaaa bbbbbbbbbb ccccccccc (ddddddddddd (eeeeeeeeeee (ffffffffffff)))) (stuff a b c) (bother x y) (foo bar baz)])"
  {:parse-string? true, :binding {:multi-lhs-hang? true}})

(let [(aaaaaaaaa bbbbbbbbbb
                 ccccccccc
                 (ddddddddddd (eeeeeeeeeee (ffffffffffff)))) (stuff a b c)
      (bother x y) (foo bar baz)])
```

#### :no-hang, :all-hang

These two styles will turn on or off all of the `:hang?` booleans
in `:map`, `:list`, `:extend`, `:pair`, `:pair-fn`, `:reader-cond`,
and `:record`.  The `:hang?` capability almost always produces
clearly better results, but can take more time (particularly in
Clojurescript, as it is single-threaded).  `:all-hang` is the
effective default, but `:no-hang` can be used to turn it all off
if you wish.  If `:hang?` is off for some reason, you can use
`:all-hang` to turn it back on.

#### :fast-hang

__EXPERIMENTAL__

This style does two things: it tends to prefer hangs over flows, even
when the line-count of the hang might be more than that of a flow, and
it tends to speed up processing -- frequently doing the same work in 80%
of the time, and sometimes doing the same work in 25% or even 10% of
the time as classic zprint formatting.  It does this by, in many cases,
accepting hangs
(if they work at all) without comparing how many lines they took
to the corresponding flow for the same expression.  This can drastically
reduce the time required to format some code or structures, particularly
those that are very deeply nested.  One downside is that sometimes the
resulting formatted code is longer than it might otherwise be when normal
formatting is used.  The other downside is that, rarely, a lot of code
gets pushed to the right side of the page, which can look awkward.

This is implemented by some not yet documented tuning parameters, which
have been set to try to give a formatting "look" which is similar to
classic zprint formatting and still yield some level of performance
optimization.  The optimization is greater the more deeply nested
the code or structure which is being formatting.

One 6300 line file in zprint itself formats in about 5.5s on a very old
MacBook Air, and with `:style :fast-hang`, formats in about 4.5s.  It doesn't
contain any particularly challenging functions, but there is still an
improvement.  There are about 78 more lines in the `:style :fast-hang`
output.  Nothing looks terrible, but there are a couple of places where
the different "look" isn't as pleasing.  There are probably more places where
the differnt "look" is actually slightly better.

If you have some code or structures that take too long
to format, try `:style :fast-hang`.  If that doesn't work, you can
always try `:style :indent-only`, which will certainly take a much shorter
time.

#### :ns-justify

Recognizes these key-value pairs in a `{:style-call :ns-justify}`:

 __:require-max-variance__ _20_  
 __:require-macros-max-variance__ _20_  
 __:import-max-variance__ _1000_

This will make `ns` statements look more readable.  It pulls together
the styles `:require-justify`, `:require-macros-justify` and `:import-justify`.
If you don't want all three, consider exploring just the one or two that
you do want.

For example:

```
(czprint nsj1 {:parse-string? true})

(ns ^:no-doc zprint.config
  #?(:clj [:refer-clojure :exclude [read-string]])
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require #?@(:clj [[zprint.macros :refer
                       [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
            clojure.string
            [clojure.set :refer [difference]]
            [clojure.data :as d]
            [zprint.spec :refer [validate-basic coerce-to-boolean]]
            [zprint.rewrite :refer [sort-dependencies]]
            [zprint.util :refer [dissoc-two]]
            [zprint.guide :refer
             [jrequireguide defprotocolguide signatureguide1 odrguide guideguide
              rodguide areguide defprotocolguide-s]]
            [zprint.optionfn :refer
             [rodfn meta-base-fn fn*->% sort-deps regexfn rulesfn]]
            #?(:clj [clojure.edn :refer [read-string]]
               :cljs [cljs.reader :refer [read-string]]))
  #?@(:clj [(:import (java.io InputStreamReader FileReader BufferedReader))]))

(czprint nsj1 {:parse-string? true :style :ns-justify})

(ns ^:no-doc zprint.config
  #?(:clj [:refer-clojure :exclude [read-string]])
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer
               [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
    clojure.string
    [clojure.set     :refer [difference]]
    [clojure.data    :as d]
    [zprint.spec     :refer [validate-basic coerce-to-boolean]]
    [zprint.rewrite  :refer [sort-dependencies]]
    [zprint.util     :refer [dissoc-two]]
    [zprint.guide    :refer [jrequireguide defprotocolguide signatureguide1
                             odrguide guideguide rodguide areguide
                             defprotocolguide-s]]
    [zprint.optionfn :refer [rodfn meta-base-fn fn*->% sort-deps regexfn
                             rulesfn]]
    #?(:clj [clojure.edn :refer [read-string]]
       :cljs [cljs.reader :refer [read-string]]))
  #?@(:clj [(:import
              (java.io InputStreamReader FileReader BufferedReader))]))
```

This is not the default for zprint, but only because it came about
much later than was possible to make it the default, as a request
from a user.  Implemention of the various pieces of `:ns-justify`
required one major architectural change and a number of other
changes, but the results, especially for complex `ns` statements,
are quite nice.

But what if your `:require` (or `:require-macros` or `:import`) doesn't
actually come out looking different?  Most frequently this is because
the justification code looks at all of the namespaces in the `:require` 
list and decides that they are just too different in length so they
will not look good.  In that case, it doesn't justify them. 

But you can change the criterion that is used to make that decision.  There
is a value called the "variance", which quantifies the amount of difference
in length among a sequence of namespaces.  There is a default maximum
variance for justification, and if the namespaces have a variance greater
than the default, then justification will not be done.  You can change
this maximum variance when you specify the `:ns-justify` style, allowing
you to essentially force justification even for namespaces which vary
widely in length.  The default maximum variance for `:require` and
`:require-macros` is 20, and for `:import`, it is 1000.

In order to change the variance for `:require` when using `:ns-justify`,
you need to invoke the style `:ns-justify` by using a style-map, as opposed
to simply specifying the keyword `:ns-justify`.  When using a style-map,
you specify the style as a map containing the `:style-call` keyword along
with any other key value pairs known by that style.  Using a style-map
allows you to pass parameters to styles that are able to accept them.  

Here is a reasonable `ns` macro which will not justify using the default
variance for the `:require` list:

```
 (czprint i310i {:parse-string? true :style :ns-justify})
(ns mechanism-net.pages.admin
  "docstriing"
  (:require
    [mechanism-center.app.build-config :as build-config]
    [mechanism-center.adapters.edn-utils :as edn-utils]
    [mechanism-center.adapters.env-variables :as env-vars]
    [mechanism-center.adapters.version :as version]
    [mechanism-net.configuration :as net-conf]
    [mechanism-center.http.request :as request]
    [mechanism-net.components.icons :as net-icons]
    [mechanism-net.components.table :as table]
    [mount.tools.graph :as mount-graph]))
```

If we adjust the maximum variance for the `:require` list to be justified,
we get something a bit nicer:

```
(czprint i310i {:parse-string? true :style {:style-call :ns-justify :require-max-variance 1000}})
(ns mechanism-net.pages.admin
  "docstriing"
  (:require
    [mechanism-center.app.build-config       :as build-config]
    [mechanism-center.adapters.edn-utils     :as edn-utils]
    [mechanism-center.adapters.env-variables :as env-vars]
    [mechanism-center.adapters.version       :as version]
    [mechanism-net.configuration             :as net-conf]
    [mechanism-center.http.request           :as request]
    [mechanism-net.components.icons          :as net-icons]
    [mechanism-net.components.table          :as table]
    [mount.tools.graph                       :as mount-graph]))
```

The parameters used by `:ns-justify` are: `:require-max-variance`,
`:require-macros-max-variance` and `:import-max-variance`.  
Note that there is no validation performed on the key value pairs
in a style-map, so if things aren't working the way you expect,
be sure to check your spelling of the parameters!

#### :original-tuning

The `:tuning` was changed as part of the modifications to zprint
to stop changing the formatting of source when run successively on
a source file.  The style `:original-tuning` replaces the original
tuning, and also will cause zprint to sometimes change the format
of an already formatted source file.

#### :quote-wrap

This will wrap quoted lists to the right margin, similar to how vectors
are formatted by default.

#### :regex-example

This is an example of how to employ regular expression matching in
the `:fn-map`.  It operates just like `:rules-example`, but only does
regular expression matching.  See the explanation of `:rules-example`
for details.

#### :require-justify
#### :require-macros-justify

Both recognize this key-value pair in a `{:style-call :require-justify}`
or a `{:style-call :require-macros-justify}`:

__:max-variance__ _20_  

NOTE: Everything in this section applies equally to `:require-justify`
and `:require-macros-justify`. 

An approach to formatting the `(ns (:require ...))` form, which will
turn this:
```clojure
% (zprint nsc {:parse-string? true})
(ns zprint.core
  (:require [zprint.zprint :as :zprint :refer
             [fzprint line-count max-width line-widths expand-tabs zcolor-map
              determine-ending-split-lines]]
            [zprint.zutil :refer
             [zmap-all zcomment? edn* whitespace? string find-root-and-path-nw]]
            [zprint.finish :refer
             [cvec-to-style-vec compress-style no-style-map color-comp-vec
              handle-lines]]))
```
into this:
```clojure
% (zprint nsc {:parse-string? true :style :require-justify})
(ns zprint.core
  (:require
    [zprint.zprint :as    :zprint
                   :refer [fzprint line-count max-width line-widths expand-tabs
                           zcolor-map determine-ending-split-lines]]
    [zprint.zutil  :refer [zmap-all zcomment? edn* whitespace? string
                           find-root-and-path-nw]]
    [zprint.finish :refer [cvec-to-style-vec compress-style no-style-map
                           color-comp-vec handle-lines]]))
```

It is possible that the list of namespaces will not justify because they
vary too much in length.  This often occurs when the lengths of the
namespaces cluster around some value, and there are some whose lengths do not
conform closely to the value of the others.

For example, this will not justify with just `:require-justify`:

```
(czprint i310i {:parse-string? true :style :require-justify})
(ns mechanism-net.pages.admin
  "docstriing"
  (:require
    [mechanism-center.app.build-config :as build-config]
    [mechanism-center.adapters.edn-utils :as edn-utils]
    [mechanism-center.adapters.env-variables :as env-vars]
    [mechanism-center.adapters.version :as version]
    [mechanism-net.configuration :as net-conf]
    [mechanism-center.http.request :as request]
    [mechanism-net.components.icons :as net-icons]
    [mechanism-net.components.table :as table]
    [mount.tools.graph :as mount-graph]))
```

You can easily adjust the maximum variance for the `:require-justify`
by calling it with a map as opposed to a keyword, and then placing the
correct key value "parameters" in the map, like this:

```
(czprint i310i {:parse-string? true :style {:style-call :require-justify :max-variance 1000}})
(ns mechanism-net.pages.admin
  "docstriing"
  (:require
    [mechanism-center.app.build-config       :as build-config]
    [mechanism-center.adapters.edn-utils     :as edn-utils]
    [mechanism-center.adapters.env-variables :as env-vars]
    [mechanism-center.adapters.version       :as version]
    [mechanism-net.configuration             :as net-conf]
    [mechanism-center.http.request           :as request]
    [mechanism-net.components.icons          :as net-icons]
    [mechanism-net.components.table          :as table]
    [mount.tools.graph                       :as mount-graph]))
```

#### :require-pair

Another approach to formatting the `(ns (:require ...))` form at the
start of a file.  Similar to `:require-justify`, but without justification.

It will turn this:
```clojure
% (zprint nsc {:parse-string? true})
(ns zprint.core
  (:require [zprint.zprint :as :zprint :refer
             [fzprint line-count max-width line-widths expand-tabs zcolor-map
              determine-ending-split-lines]]
            [zprint.zutil :refer
             [zmap-all zcomment? edn* whitespace? string find-root-and-path-nw]]
            [zprint.finish :refer
             [cvec-to-style-vec compress-style no-style-map color-comp-vec
              handle-lines]]))
```
into this
```clojure
% (zprint nsc {:parse-string? true :style :require-pair})
(ns zprint.core
  (:require
    [zprint.zprint :as :zprint
                   :refer [fzprint line-count max-width line-widths expand-tabs
                           zcolor-map determine-ending-split-lines]]
    [zprint.zutil :refer [zmap-all zcomment? edn* whitespace? string
                          find-root-and-path-nw]]
    [zprint.finish :refer [cvec-to-style-vec compress-style no-style-map
                           color-comp-vec handle-lines]]))
```

#### :respect-bl 

Respect blank lines. 

Whenever a blank line appears in the source, it will be "respected", and
will appear in the output.  However, all other formatting will be
applied around any blank lines that may appear.  

Note that blank lines at the top level (i.e., outside of function
definitions and `(def ...)` expressions) are always respected and 
never changed.  This style extends that behavior into the actual function
definitions.

Respect new lines (i.e., `:respect-nl`) sounds like a similar style,
but the actual results are quite different.  With `:respect-nl`,
no lines are ever joined together.  Lines that are long may be
split, but that is the extent of changes allowed concerning where
things appear on lines.  The freedom for zprint to actually format
the code is quite limited with `:respect-nl`.

Alternatively, with `:respect-bl`, there is plenty of freedom for zprint
to format the code in a maximally readable manner, since only blank lines
interrupt zprint's ability to flow code back and forth between lines
as necessary for good formatting.

While `:respect-nl?` was something that you might want to configure
for formatting a single function, `:respect-bl?` is something that
is perfectly reasonable to configure for processing whole files,
or perhaps all of the time in your `~/.zprintrc` file. If you
do that, everything will operate as normal with zprint, but if you
put blank lines inside a function definition, those blank lines
will continue to appear in the output.  And all of the information
will all be formatted correctly around those blank lines.

Note that zprint can be used in a number of ways.  If you are using
zprint to enforce a particular format on code (say in a group setting),
then `:respect-bl` is probably not a great choice, since different people
will want to put blank lines in different places for readability.

There are several ways to get zprint to place blank lines in particular
places when formatting code, and these approaches are compatible with using
zprint to enforce a particular code approach.  Here are some of them:

 * [add newlines between pairs in let binding vectors](#map-nl-pair-nl-binding-nl)
 * [add newlines between cond, assoc pairs](#map-nl-pair-nl-binding-nl)
 * [add newlines between extend clauses](#extend-nl)
 * [add newlines between map pairs](#map-nl-pair-nl-binding-nl)

An example from clojure.core:

```clojure
; Classic zprint 

(czprint-fn ->ArrayChunk)

(deftype ArrayChunk [^clojure.core.ArrayManager am arr ^int off ^int end]
  clojure.lang.Indexed
    (nth [_ i] (.aget am arr (+ off i)))
    (count [_] (- end off))
  clojure.lang.IChunk
    (dropFirst [_]
      (if (= off end)
        (throw (IllegalStateException. "dropFirst of empty chunk"))
        (new ArrayChunk am arr (inc off) end)))
    (reduce [_ f init]
      (loop [ret init
             i off]
        (if (< i end)
          (let [ret (f ret (.aget am arr i))]
            (if (reduced? ret) ret (recur ret (inc i))))
          ret))))
```
```clojure
;Classic zprint {:style :respect-bl}

(czprint-fn ->ArrayChunk {:style :respect-bl})

(deftype ArrayChunk [^clojure.core.ArrayManager am arr ^int off ^int end]
  
  clojure.lang.Indexed
    (nth [_ i] (.aget am arr (+ off i)))
    
    (count [_] (- end off))
  
  clojure.lang.IChunk
    (dropFirst [_]
      (if (= off end)
        (throw (IllegalStateException. "dropFirst of empty chunk"))
        (new ArrayChunk am arr (inc off) end)))
    
    (reduce [_ f init]
      (loop [ret init
             i off]
        (if (< i end)
          (let [ret (f ret (.aget am arr i))]
            (if (reduced? ret) ret (recur ret (inc i))))
          ret))))
```
For more examples, see [Respect Blank Lines](./types/respectbl.md).

#### :respect-bl-off

Set `:respect-bl` to false in all of the places where `:respect-bl` set
it to true.  Useful in `:next-inner` to turn `:respect-bl` off when processing
the rest of an expression. 

#### :respect-nl 

This will cause zprint to respect incoming newlines. If this is enabled,
zprint will add newlines, but will not remove any existing newlines from
incoming source.  Existing formatting configuration will be followed, of
course with the constraint that existing newlines will be included wherever
they appear.  You can configure this style for just a particular function,
if you have one that doesn't format well with classic zprint. See
[Altering the formatting inside of certain functions](#altering-the-formatting-inside-of-certain-functions) for details.


Some examples of classic zprint, `:respect-nl`, and `:indent-only`:

```clojure

(def io2
"
(let
 [stuff
   (bother in
           addition
		    to more)
      foo (bar
          with
	  baz)]
  (if output
         stuff foo))")

; Classic zprint
;
; Attemps to format the source clearly, while also trying to get the maximum
; amount of code possible into a vertical space.  Ignores incoming whitespace
; and formats entirely based on zprint configuration.  This will regularize
; the entire format, effectively enforcing a particular format regardless
; of the input.

(zprint io2 {:parse-string? true})
(let [stuff (bother in addition to more)
      foo (bar with baz)]
  (if output stuff foo))

; :style :respect-nl
;
; Does the same thing as classic zprint, but if there is a newline, it
; will not remove it.  Based on incoming newlines, it will try to make
; the output look as much like classic zprint as possible.
;
; Note that you can configure this style for just a particular function,
; if there is a function that doesn't format well with classic zprint.
;

(czprint io2 {:parse-string? true :style :respect-nl})
(let
  [stuff (bother in
                 addition
                 to
                 more)
   foo (bar
         with
         baz)]
  (if output
    stuff
    foo))

; Notice that `:respect-nl` still knows the kinds of functions -- so
; the two clauses of the `if` won't ever be on the same line unless the
; entire `if` is on the same line. Since this `if` had a newline, that
; forced both clauses to be on separate lines.

; :style :indent-only
;
; This style doesn't add or remove newlines, and doesn't know anything
; about which functions are which.  If you put to things on the same
; line, they stay on the same line (see `to more)` and `stuff foo)`
; below).  If you hang something (see `in addition to more)`), it will
; keep the hang for you.  Compare this with the input `io2` above.
; If `addition` were one character either left or right in the input,
; `:indent-only` would not have aligned it with `in` on output, but
; would have indented it from bother (i.e., made it a flow).

(zprint io2 {:parse-string? true :style :indent-only})
(let
  [stuff
   (bother in
           addition
           to more)
   foo (bar
         with
         baz)]
  (if output
    stuff foo))

```
#### :respect-nl-off

Set `:respect-nl` to false in all of the places where `:respect-nl` set
it to true.  Useful in `:next-inner` to turn `:respect-nl` off when processing
the rest of an expression. 

#### :rod, :rod-no-ma-nl

Recognizes these key-value pairs in a `{:style-call :rod}`:

__:multi-arity-nl?__ _true_  (:rod-no-ma-nl has this as _false_)  
__:one-line-ok?__ _false_   


An alternative way to format `defn` and `defn-` functions.  The
basic `:rod` style will place a blank line between arities of a multi-arity
function.  `:rod-no-ma-nl` is identical to `:rod`, but it will not
put in extra blank lines.

You can configure `:rod` to also continue to place any function
definitions that fit on one line on one line (which it does not do by
default) by invoking it like this:

`{:style {:style-call rod-config :one-line-ok? true}}`

An example of what `:style :rod` does:

```
(czprint rod5 {:parse-string? true})

(defn rod5
  ([a b c d]
   (cond (nil? a) (list d)
         (nil? b) (list c d a b)
         :else (list a b c d)))
  ([a b c] (rod5 a b c nil))
  ([a b] (rod5 a b nil nil)))


(czprint rod5 {:parse-string? true :style :rod})
(defn rod5
  ([a b c d]
   (cond (nil? a) (list d)
         (nil? b) (list c d a b)
         :else (list a b c d)))

  ([a b c]
   (rod5 a b c nil))

  ([a b]
   (rod5 a b nil nil)))
```

#### :rules-example

A example style, showing how to use the built-in `:option-fn`
called `rulesfn` to do regular expression (regex) matching
for the `:fn-map`, as well as other capabilities (for instance,
formatting differently if a function name is longer than some
set value).

Here is the example:

```
  :rules-example
    {:doc "An example of how to use rulesfn to recognize fns"
     :fn-map
       {:default-not-none
	 [:none
	   {:list {:option-fn (partial rulesfn
				       [#(> (count %) 20)
					{:guide [:element :newline
						 :element-wrap-flow-*]}
					#"^are" {:fn-style "are"}
					#"^when" {:fn-style "when"}])}}]}}
```

The `:option-fn` `rulesfn` accepts a vector of pairs of elements
as its first argument, thus the use of `partial` to bind that first
argument to `rulesfn` prior to configuring it into the option map.

This first argument is a vector of pairs.  There are two kinds of pairs:

If the left-hand-side of a pair is a function, it will call that
function with the string format of the function name as its only
argument. If the function returns non-nil, then the right-hand-side
of the pair is returned as the option map.

If the left-hand-side of the pair is not a function, it is assumed
to be a regular expression, and it is matched against the string
format of the function name.  If it matches, then the right-hand-side
of the pair is returned as the option map.

The pairs in the vector are processed in order until one of them
has the right-hand-side returned as an option map or they are all
completed without "triggering", in which case the `:option-fn` 
returns nil.  

To explain this example...

It is attached to the `:fn-map` at `:default-not-none`, which is 
used when a function name is looked up in the function map and it is
not found.

In the example above, any function name which is over 20 characters
long is processed differently from other functions, using the
(as yet undocumented) "guide" capability.

If the function name is 20 characters or less, then it is
regular expression matched to see if it starts with "are" -- if it
does, then it is formatted like the function `are`.  If it doesn't
start with "are", it is checked to see if it starts with "when".
If it does, then it will format like `when`.  If none of these
match, it returns nil -- which does nothing.  The function then
formats like any other function which is not found in the `:fn-map`.

You can create a style similar to this style, with you own information,
and then invoke it.  Or you can just set the `:fn-map` value for
`:default-not-none` with your invocation of `rulesfn` containing
your own values in the vector which is `rulesfn`'s first argument.

#### :sort-dependencies

Sort the dependencies in a leiningen project.clj file in alphabetical
order.  Technically, it will sort the vectors in the value of any
key named `:dependencies` inside any function (macro) named
`defproject`.  Has no effect when formatting a structure (as opposed
to parsing a string and formatting code).  If there is a comment
in any of the dependencies, it will not sort those dependencies.

#### :sort-require

Recognizes these key-value pairs in a {:style-call :sort-require}:

__:regex-vec__ _[]_  
__:sort-refer?__ _true_  

Sort the elements of the `:require` list in an `ns` macro.  In its most
basic form, this style will simply sort the dependencies within the list
starting with `:require`.  It will sort them lexographically, based on the 
string of the namespace involved.  The string may be freestanding, or it 
may be the first element of a vector.  The string may also be unevaluted
(i.e., begin with `#_`) or the first element of a vector that is unevaluated.
In addition, the elements of any `:refer` vector will also be sorted (by
default -- you can disable this if desired).

```
; As written, nothing done to it

(czprint i310b {:parse-string? true})
(ns i310
  (:require [a.b]
            [b.c.e.f]
            [b.c.d.e]
            [a.b.c.e]
            [c.b.a]
            b.g.h
            (a.c.e)
            [a.b.c.d.e.f]
            [c.b.d]
            [a.b.c.d]
            [a.b.c.f]
            [a.b.c]))

; Sorted with basic :sort-require

(czprint i310b {:parse-string? true :style :sort-require})
(ns i310
  (:require [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e]
            [a.b.c.f]
            (a.c.e)
            [b.c.d.e]
            [b.c.e.f]
            b.g.h
            [c.b.a]
            [c.b.d]))
```
Several notes:

  * If there is a comment in the list of requires, the list will be
untouched and not sorted.  
  * Only when `:require` is the first element of a list (not a vector), will the elements be sorted.
  * Unevaluated elements will be sorted.
  * Reader conditionals within the list of requires will not be sorted or moved.  All of the other elements will sort around them.

A slightly more complex example:

```
; Without sorting

(czprint i310d {:parse-string? true})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [b.c.e.f]
            [a.b]
            [b.c.d.e]
            [a.b.c.e]
            [c.b.a]
            #_b.g.h
            (a.c.e)
            [a.b.c.d.e.f]
            [c.b.d]
            [a.b.c.d]
            [a.b.c.f]
            [a.b.c]))

; With sorting -- note the reader-conditional doesn't move

(czprint i310d {:parse-string? true :style :sort-require})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e]
            [a.b.c.f]
            (a.c.e)
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            [c.b.a]
            [c.b.d]))
```
If you wish to have some elements sort either before or after some other 
elements, you can select the groups to move up (or down) with regular
expressions.  To do this, you have to invoke the style with a `:style-call`,
and pass it the regular expressions as arguments in a style map.

For instance, if you wished to move all of the namespaces starting with "b"
to the top, you could do it this way:


```
(czprint i310d {:parse-string? true :style {:style-call :sort-require :regex-vec [#"^b\."]}})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e]
            [a.b.c.f]
            (a.c.e)
            [c.b.a]
            [c.b.d]))
```

You can see that the reader-conditional doesn't move, but now all of the
namespaces starting with "b." are at the top.  You may specify multiple 
regular expressions.

```
 (czprint i310d {:parse-string? true :style {:style-call :sort-require :regex-vec [#"^b\." #"^a\.c\."]}})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            (a.c.e)
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e]
            [a.b.c.f]
            [c.b.a]
            [c.b.d]))
```
The things that match the first regex are placed first in the list, 
those that match the second are placed next in the list, and so on.  When there
are multiple elements that match a particular regex, they are sorted 
amongst the others that match that particular regex.

You can also specify things that go at the end, as opposed to the beginning,
by using the distinguished element `:|`.

```
 (czprint i310d {:parse-string? true :style {:style-call :sort-require :regex-vec [:| #"^b\." #"^a\.c\."]}})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e]
            [a.b.c.f]
            [c.b.a]
            [c.b.d]
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            (a.c.e)))
```

This puts all of the things that match those regexes at the end.  You can put
some at the front and some at the end, by adjusting where you put the `:|`.

```
(czprint i310d {:parse-string? true :style {:style-call :sort-require :regex-vec [#"^b\." :| #"^a\.c\."]}})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e]
            [a.b.c.f]
            [c.b.a]
            [c.b.d]
            (a.c.e)))
```

Of course, if nothing matches a regex, then that isn't a problem:

This style will interoperate well with `:ns-justify` or `:require-justify`.
Just be sure and put `:sort-require` first.  You can also use `how-to-ns`.


```
(czprint i310d {:parse-string? true :style {:style-call :sort-require :regex-vec [#"^aa\." #"^b\." :| #"^a\.c\."]}})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e]
            [a.b.c.f]
            [c.b.a]
            [c.b.d]
            (a.c.e)))
```

The elements of the `:refer` vector are also sorted:

```
; The basic formatting

(czprint i310j {:parse-string? true})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [b.c.e.f]
            [a.b]
            [b.c.d.e]
            [a.b.c.e :refer [y q u n e g t a c p]]
            [c.b.a]
            #_b.g.h
            (a.c.e)
            [a.b.c.d.e.f]
            [c.b.d]
            [a.b.c.d]
            [a.b.c.f]
            [a.b.c]))

; With the default approach to :sort-require

(czprint i310j {:parse-string? true :style {:style-call :sort-require}})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e :refer [a c e g n p q t u y]]
            [a.b.c.f]
            (a.c.e)
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            [c.b.a]
            [c.b.d]))

; Disabling sorting of :refer elements:

(czprint i310j {:parse-string? true :style {:style-call :sort-require :sort-refer? false}})
(ns i310
  (:require #?@(:clj [[e.c.e.f]])
            [a.b]
            [a.b.c]
            [a.b.c.d]
            [a.b.c.d.e.f]
            [a.b.c.e :refer [y q u n e g t a c p]]
            [a.b.c.f]
            (a.c.e)
            [b.c.d.e]
            [b.c.e.f]
            #_b.g.h
            [c.b.a]
            [c.b.d]))
```

### Convenience Styles

#### :areguide
#### :defprotocolguide
#### :jrequireguide

Allow easy (re)use of the respective "guides" in the :fn-map.

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

You can define a style and apply it in the same `.zprintrc` file
or `set-options!` call, as the `:style-map` changes are processed
before the `:style` changes.  Both are processed before the remaining
changes in the options map.

### Configuring Styles

It has long been possible to create a style in the :style-map
containing an option-fn which takes a map of configuration values.
However, it has previously been necessary to create a separate style
for each combination of configuration values which were to be passed
to the option-fn.  This was fine for a while, but with the advent of
more complex option-fns supporting multiple configuration parameters,
the explosion of styles was prohibitive.
  
Now it is possible to actually pass configuration information to a
style when that style is used.  It is also possible to create a style
in the :style-map which has configuration information for another
style in the `:style-map`.
  
Central to all of this is the creation of a style definition which
is not an option map to be applied, but rather a function to be
called -- a map containing a "style-fn".  When a style is defined
(i.e., configured to be in the `:style-map`) using a map containing a
`:style-fn` key, the value of that key is a function to be called
when this style is used (applied).  In addition, the map in which
the key `:style-fn` appears is not validated as an option map.
Rather, the map surrounding the `:style-fn` key is expected to
contain the default values of the configuration keys for that
`:style-fn`.  Thus, any map in the `:style-map` which contains
the key `:style-fn` is not validated as an option map, and can contain
arbitrary map values.
  
The style containing `:style-fn` is used when it is specified, either
by its keyword name, or by a new type of style specifier -- a map
containing the key `:style-call`.  When using a map containing 
`:style-call` key, the value of `:style-call` must be
a keyword that appears in the `:style-map`.
  
When the `:style-fn` is called to generate the option map to be applied
as the result of the style, it is passed both the map in which
:style-fn appears (typically containing the default values of the
configuration parameters for the `:style-fn`) and the map (or maps)
which contained the :style-call itself.  The keys in the `:style-call`
map are typically also configuration parameters for the `:style-fn`.
It has access to all of this information, allowing it to make any
configuration decisions that are desired.

The result of a `:style-fn` call must be an option map and is validated
on return from the `:style-fn` for correctness.  Thus, a `:style-fn`
cannot return a `:style-call`, as the map which surrounds a `:style-call`
is not an option map.  A `:style-fn` can, however, return an option
map containing a `:style` application, where the value of the `:style`
key is itself a map containing `:style-call`.

Below is an example of a style definition containing a `:style-fn`.

When the style is specified, the function which is the value of `:style-fn`
is called with the 4 arguments shown:

  * `existing-options` is the current value of the option map  
 
  * `new-options` is the options map which contains the `:style` invocation which ultimately caused this style to be used

  * `style-fn-map` is the map you see here -- the map that contains the key
`:style-fn`  

  * `style-call` is the map which contained the original `:style-call`, if any
  
The `style-call` may be nil if this style was invoked without using a `:style-call`.

A style-fn must return either an options map which can be validated
as correct or nil, indicating that nothing will be happen as the
result of this style being applied.  

Here is an example of a style defined using a `:style-fn`:


```
{:rod {:doc "Rules of defn approach",
       :multi-arity-nl? true,
       :one-line-ok? false,
       :style-fn (fn
                   ([] "rod-style-fn")
                   ([existing-options new-options style-fn-map style-call]
                    {:fn-map {"defn" [:none
                                      {:list {:option-fn (partial
                                                           rodfn
                                                           (merge-deep
                                                             style-fn-map
                                                             style-call))}}],
                              "defn-" "defn"}}))}}
```

Typically, the map which contains the `:style-fn` key also contains the
default values for the configuration for the option-fn which will be called.
In the example above, these would be `:multi-arity-nl? true` and
`:one-line-ok? false`.

The map which contains the `:style-call` key (if any), will have
configuration values which will override those in the map in the
`:style-map`, and you can see where the style-call values are merged
into (and in some sense "on top of") the values in the style-fn-map
in the example above using `merge-deep`. This isn't required, though
it is a good way to handle default and more specifically configured
values.  The function `merge-deep` is available to all `option-fn`
definitions, including those read in from zprint configuration files
(and therefore processed by `sci`).

Note that only styles that involve a `:style-fn` may be invoked with
a map containing the key `:style-call`.  You cannot invoke any arbitrary
style with a map containing `:style-call` -- only those which ultimately
end up resolving to a map containing a `:style-fn` call.
  
Considerable flexibility is now available when applying styles.  In
addition to simple configuration parameters for an option-fn (see above),
a `:style-fn` can also look at the current option map
and use whatever information is available there to return a different
option map to be used.

______
## :tab

zprint will expand tabs by default when parsing a string, largely
in order to properly size comments.  You can disable tab expansion
and you can set the tab size.

#### :expand? _true_

Expand tabs.

#### :size _8_

An integer for the tab size for tab expansion.



______
## :tagged-literal

The handling of tagged literals (for example, `#js`) is now controlled
by the configuration element: `:tagged-literal`.  By default zprint
will try to put the next thing after the tagged literal on the same
line as the tagged literal.  You can configure zprint to
consider the tagged literal and the element following it as unrelated
by configuring: `{:tagged-literal {:hang? false}}`.

##### :indent _0_
##### :hang? _true_
##### :hang-expand _1000.0_
##### :hang-diff _1_

_____
## :vector

##### :indent _1_

#### :indent-only? _false_

Do not add or remove newlines.  Just indent the lines that are there
and regularize other whitespace.

#### :fn-format _nil_

There are a rich set of formatting options available to lists through
the `:fn-map`, which relates function names to formatting styles.
Typically vectors are formatted simply as individual elements wrapped
to the end of the line.  However, the formatting available to lists
is also available to vectors through the use of `:fn-format`, which
when set will cause a vector to be formatted in the same way as a
list whose first element is a function that maps to the same
formatting style as the value of `:fn-format`.  Thus, the various
function formatting styles such as `:arg1` or `:arg2` are available
to vectors as well as lists by setting the `:vector` key `:fn-format`
to `:arg1` or some other function formatting style.

While you can configure this in the options map for every vector,
typically this configuration value is set by using `option-fn` or
`option-fn-first`, and it is based on the information in the vector
itself.  When this key has a non-nil value, the vector is formatting
like a list whose function (i.e., first element) is associated with
a particular formatting style.  When `:fn-format` is non-nil, the
configuration elements in `:vector-fn` are used instead of the
configuration elements in `:vector` or `:list` when formatting that
vector (but only that vector, not other vectors nested inside of
it).

Note that the `:fn-format` processing is done before testing for
`:indent-only?` (as is the `:option-fn` and `:option-fn-first`
processing as well), so if the result of the `:option-fn` or
`:option-fn-first` processing sets `:fn-format`, then the value of
`:indent-only?` in `:vector-fn` will control whether or not
`:indent-only?` is used, not the value of `:indent-only?` in
`:vector`.  This is worthy of mention in any case, but particularly
because `:style :indent-only` does __not__ set `:indent-only?` for
`:vector-fn`!

As an example, the `:hiccup` style is implemented as follows:

```clojure { ...
      :hiccup {:vector
                 {:option-fn
                    (fn [opts n exprs]
                      (let [hiccup? (and (>= n 2)
                                         (or (keyword? (first exprs))
                                             (symbol? (first exprs)))
                                         (map? (second exprs)))]
                        (cond (and hiccup? (not (:fn-format (:vector opts))))
                                {:vector {:fn-format :arg1-force-nl}}
                              (and (not hiccup?) (:fn-format (:vector opts)))
                                {:vector {:fn-format nil}}
                              :else nil))),
                  :wrap? false}
}

; Here is some hiccup without using this style:

(czprint-fn header)

(defn header
  [{:keys [title icon description]}]
  [:header.container
   [:div.cols {:class "gap top", :on-click (fn [] (println "tsers"))}
    [:div {:class "shrink"} icon]
    [:div.rows {:class "gap-xs"}
     [:dov.cols {:class "middle between"} [:div title]
      [:div {:class "shrink"} [:button "x"]]] [:div description]]]])

; Here is the same hiccup using the :hiccup style:

(czprint-fn header {:style :hiccup})

(defn header
  [{:keys [title icon description]}]
  [:header.container
   [:div.cols {:class "gap top", :on-click (fn [] (println "tsers"))}
    [:div {:class "shrink"} icon]
    [:div.rows {:class "gap-xs"}
     [:dov.cols {:class "middle between"}
      [:div title]
      [:div {:class "shrink"} [:button "x"]]]
     [:div description]]]])

; Not a huge difference, but note the use of :arg1-force-nl to clean up
; the display of the elements beyond the map in each vector.  Were this more
; complex hiccup, the difference would be more valuable.

```

This also points out a difference between enforcing and allowing a formatting
style.  One could use `:respect-nl?` to let whatever formatting was in the
file inform the output for zprint, which is fine if you are using zprint to
clean up the formatting of a file.  But if you are using zprint to enforce
a particular formatting approach, then `:fn-format` and `:option-fn` can
be very useful.

#### :option-fn-first _nil_

Vectors often come with data that needs different formatting than the
default or than the code around them needs.  To support this capability,
you can configure a function as the `:option-fn-first` which will be
given the first element of a vector and may return an options map to be
used to format this vector (and all of the data inside of it).  If this
function returns nil, no change is made.  The function must be a function
of two arguments, the first being the current options map, and the second
being the first element of the vector.  

```clojure
(fn [options first-non-whitespace-non-comment-element] ... )
```

__Note__: Functions are always allowed when using `set-options!`
to update an options map.  In addition, they are allowed on options
maps that appear in calls to the zprint API.  They are also allowed
in `.zprintrc` files and in option maps on the command line.

The functions
defined in the default options map are always allowed -- the above
restrictions only apply to functions being read in from external
files or from the command line.

If you need access to additional data in the vector to determine the proper
formatting, see `option-fn` which gives that access.

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
Note that `:option-fn` and `:option-fn-first` can both be
used. `:option-fn-first` is executed first, and the results of that are given
to `:option-fn` as the options map.


#### :option-fn _nil_

Vectors often come with data that needs different formatting than
the default or than the code around them needs.  To support this
capability, you can configure a function as the `:option-fn` which
will be given the all the elements of a vector and may return an
options map to be used to format this vector (and all of the data
inside of it).  If this function returns nil, no change is made.
The function must be a function of three arguments, the first being
the current options map, and the second being the count of elements
in the vector, and the third being a sequence of the non-comment
and non-whitespace elements of the vector (not necessarily a vector).

The function signature for the `option-fn` is: 

```clojure
(fn [options element-count non-whitespace-non-comment-element-seq] ... )
```

This differs from `option-fn-first` in that `option-fn` gives you access
to all of the elements of the vector in order to make the decision of
how to format it, at a very slight performance impact.

The calling sequence for a `:option-fn` is:

```
(fn [options element-count non-whitespace-non-comment-element-seq] ... )
```

where the third argument is a Clojure sequence of the essential elements
of the list -- that is, the ones that are not comments or whitespace.  This
allows the Clojure code of the `:option-fn` to easily make decisions about
how to format this list.

The value of the `:option-fn` is a map containing *only* changes to the
current options map.  Do *NOT* make changes to the input options map 
and return that as the result of the `:option-fn` call!

Note that to aid debugging, all `:option-fn`s should have two arities:

```
(fn
  ([] "option-fn-name")
  ([options n exprs]
    (...)))
```

The name as a string should be returned from the zero arity call, allowing
zprint to recover the function name when outputing debugging messages.

In addition to the normal arguments, there are some additional things passed
in options map:  

  - `:zloc` The current structure or zipper
  - `:l-str` A string which is the current left enclosing parenthesis (usually)
  - `:r-str` A string which is the current right enclosing parenthesis (usually)
  - `:ztype` Either `:sexpr` for structure or `:zipper` for source.
  - `:caller` The caller, to allow examining the options maps for configuration

In addiiton to the above elements being added to the options map prior to 
calling the `:option-fn`, the following elements are recovered from the
returned options map and used if they are non-nil:

  - `:new-zloc` A structure or zipper which should have been modified.
  - `:new-l-str` A new string for the left enclosing parenthesis
  - `:new-r-str` A new string for the right enclosing parenthesis

Very few `:option-fn`s actually return a `:new-zloc`, `:new-l-str`, or 
`:new-r-str`, but the capability exists.  It is relatively easy to write
Clojure code to modify a structure, but that is only useful at the REPL.
In order to modify Clojure source code, you need to operate on the zippers
produced by the `rewrite-clj V1` Clojure parser library.  Looking at the
contents of 
zippers is not that hard, but making substantial modifications and dealing
with the existance of things like comments is challenging.  It can be done,
but debugging it can take a long time.

Writing an `:option-fn` to return an options map with formatting information
is a perfectly reasonable thing to do.  You will want to avoid writing an
`:option-fn` to actually modify the data being formatted if at all possible.

See `:fn-format` for one example of how to use `:option-fn`.  `:option-fn`
is also used in the implemenation of the `:hiccup` style.

Note that `:option-fn` and `:option-fn-first` can both be
used. `:option-fn-first` is executed first, and the results of that are given
to `:option-fn` as the options map.

#### :respect-bl? _false_

This will cause zprint to respect incoming blank lines. If this is
enabled, zprint will add newlines and remove newlines as necessary,
but will not remove any existing blank lines when formatting vectors.
Existing formatting configuration will be followed, of course with
the constraint that existing blank lines will be included wherever
they appear.  Note that blank lines at the "top level" (i.e., those
outside of `(defn ...)` and `(def ...)` expressions) are always
respected and never changed. `:respect-bl?` controls what happens
to blank lines in vectors __within__ `defn` and `def` expressions.

If you wish to use zprint to enforce a particular format, using
`:respect-bl?` might be a bad idea -- since it depends on the
incoming source with regard to blank lines.

If you use blank lines a lot within vectors embedded in function
definitions in order to make them more readable, this can be a good
capability to enable globally.

#### :respect-nl? _false_

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
formatted.  The simple answer is to use `:style :keyword-respect-nl`
which will cause only vectors whose first element is a keyword to
be formatted with `:respect-nl? true`. The more complex answer is
to employ `:option-fn-first`, above (which is what `:style
:keyword-respect-nl` does).

#### :wrap? _true_

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


#### :wrap-coll? _true_

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
#### :wrap-after-multi? _true_

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

#### :no-wrap-after _nil_

There are times when you might like two elements that appear in a
vector that is wrapping to not be separated by the line break.  For
instance, this is not great:

```
(czprint i252a {:parse-string? true :width 79})
(defn example
  [vvvvvveeeeeeeerrrrrrryyyyy looooooooooooooooooonnnnggggg paraaaaaaams &
   body]
  (+ 1 2 3))
```

You can prevent vectors from wrapping between `&` and whatever comes
immediately after by configuring `:no-wrap-after`, which is a set
that contains strings.  For instance:

```
(czprint i252a {:parse-string? true :width 79 :vector {:no-wrap-after #{"&"}}})
(defn example
  [vvvvvveeeeeeeerrrrrrryyyyy looooooooooooooooooonnnnggggg paraaaaaaams
   & body]
  (+ 1 2 3))
```

will solve this particular problem.  

It would make sense to simply configure `{:vector {:no-wrap-after #{"&"}}}` 
globally, since it probably wouldn't affect anything but
the argument vectors of functions, and in the event that `&` appeared
by itself in a vector, the worst that would happen is that the `&`
would end up on the next line if it was right at the end of the
previous line, which you might not even notice.

_____
## :vector-fn

This is the configuration used when `:fn-format` is enabled for a vector.
By default it is largely the same as `:list`, but it exists so that you 
can change it for this specific case.

Note that the `:fn-format` processing is done before testing for
`:indent-only?` (as is the `:option-fn` and `:option-fn-first`
processing as well), so if the result of the `:option-fn` or
`:option-fn-first` processing sets `:fn-format`, then the value of
`:indent-only?` in `:vector-fn` will control whether or not
`:indent-only?` is used, not the value of `:indent-only?` in
`:vector`.  This is worthy of mention in any case, but particularly
because `:style :indent-only` does __not__ set `:indent-only?` for
`:vector-fn`!

##### :indent _2_
##### :hang? _true_
##### :hang-avoid _0.5_
##### :hang-expand _2.0_
##### :hang-diff _1_

#### :indent-arg _nil_

The amount to indent the arguments of a function whose arguments do
not contain "body" forms.  For vectors, this will depend on the value
of `:fn-format` -- what kind of "function" it is using to format this
vector.

If this is nil, then the value configured for `:indent` is used for 
the arguments of functions that are not "body" functions.
You would configure this value only if you wanted "arg" type functions 
to have a different indent from "body" type functions.
It is configured by `:style :community`.

#### :indent-only? _false_

Do not add or remove newlines.  Just indent the lines that are there and
regularize whitespace.  The `:fn-map` which gives formatting and indentation
information about different functions is ignored.  The default indentation is
flow, however based on the value of the `:indent-only-style`, a hang will
be used in some situations.  See `:indent-only-style` below for details.

#### :indent-only-style _:input-hang_

Controls how `:indent-only` indents a vector. If the value is
`:input-hang`, then if the input is formatted as a hang, it will
indent the vector as a hang.  The input is considered to be formatted
as a hang if the first two elements of the vector are on the same
line, and the third element of the vector is on the second line aligned
with the second element.  The determination of alignment is not
affected by the appearance of comments.

#### :hang-size _100_

The maximum number of lines that are allowed in a hang.  If the number
of lines in the hang is greater than the `:hang-size`, it will not do
the hang but instead will format this as a flow.  Together with
`:hang-expand` this will keep hangs from getting too long so that
code (typically) doesn't get very distorted.

#### :constant-pair? _true_

Vectors being formatted like lists also support something called _**constant
pairing**_.  This capability looks at the end of the vector, and if the
end of the vector appears to contain pairs of constants followed by
anything, it will print them paired up.  A constant in this context
is a keyword, string, or number.  

Note that the formatting of the pairs in a constant pair is controlled
by the `:pair` configuration (just like the pairs in a `cond`, `assoc`,
and any function style with "pair" in the name).

#### :constant-pair-min _4_

An integer specifying the minimum number of required elements capable of being
constant paired before constant pairing is used.  Note that constant
pairing works from the end of the vector back toward the front (not illustrated
in these examples).

### :respect-bl? _false_

This will cause zprint to respect incoming blank lines. If this is
enabled, zprint will add newlines and remove newlines as necessary,
but will not remove any existing blank lines when formatting vectors
similarly to lists.
Existing formatting configuration will be followed, of course with
the constraint that existing blank lines will be included wherever
they appear.  Note that blank lines at the "top level" (i.e., those
outside of `(defn ...)` and `(def ...)` expressions) are always
respected and never changed. `:respect-bl?` controls what happens
to blank lines in vectors __within__ `defn` and `def` expressions.

If you wish to use zprint to enforce a particular format, using
`:respect-bl?` might be a bad idea -- since it depends on the
incoming source with regard to blank lines.

### :respect-nl? _false_

This will cause zprint to respect incoming newlines. If this is
enabled, zprint will add newlines, but will not remove any existing
newlines when formatting lists.  Existing formatting configuration
will be followed, of course with the constraint that existing
newlines will be included wherever they appear.

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

## Testing and Development

Note the default branch is now `main`.

### Clojure

With [Leiningen](https://leiningen.org/) installed, run

```
lein with-profile expectations test
```

from the root directory of this repo.  There are over 1500 Clojure tests.  
It will take upwards of 30 seconds for them all to run.

Another test:

  * You can also run `.test_config 1.0.1 uberjar`, (but use the current
  version), and it will run another series of more complex tests.
  Each test will say what it is testing.  It does produce output, but
  the lines starting with "...." are simply a report of what is being
  tested.  Any failures are clearly delineated as failures  Of course,
  you have to build the uberjar first: `lein clean`, `lein uberjar`.

### Clojurescript

Requirements:

  * You will need to have installed [planck](https://planck-repl.org/)

To run the Clojurescript tests, run:

`clj -A:cljs-runner`

There are over 1400 tests.  They take a long time to execute.

### Babashka

You can run most of the Clojure tests in babashka:
```
bb test:bb
```

### System Tests

There is a system test that tests the various pre-compiled binaries and
how they use configuration files.

```
bb testconfig:bb -v 1.2.8 -s ubarjar
```

Give it a `-s x`, and it will tell you the options.

  

