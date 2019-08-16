# Change Log
All notable changes to this project will be documented in this file. 

## 0.5.0 - 2019-08-30

### Added

  * Standard mode.  Some people wanted a zprint that was not configurable.
    If you give the uberjar (or graalVM binaries) "-s" or "--standard" on
    the command line, it will run zprint with no external configuration.  
    The default configuration is what you get, and no other options are 
    read from anywhere.  In particular, the `$HOME/.zprintrc` file is
    __not__ examined, nor is any other external configuration file.
    No matter who runs it, running zprint with "-s" will produce the same
    output from the same input file. 

  * Respect New Lines.  `:respect-nl?` has been implemented for lists, maps,
    sets, and vectors.  Also as a style for all of them: `:style :respect-nl`.
    This will ensure that all newlines in the input also appear in the 
    formatted output.  Additional lines may also be added to format more
    correctly or because lines are too long.  Can be used both as a style
    for everything, or more frequently as a style to be used when formatting
    a particular function where one of the existing formatting styles isn't
    quite right.  If you have a function "func" which would be better 
    handled with `:style :respect-nl`, then an options map of
    `{:fn-map {"func" [:none {:style :respect-nl}]}}` will do that for you.
    Issue #75, and probably #71.

  * Indent Only.  Will not remove or add newlines -- will only regularize
    white space while preserving the content of each line.  This is very
    different from classic zprint, and even from `:style :respect-nl`, above.
    It is available individually for lists, maps, sets, and vectors.  It is
    also available as style: `:style :indent-only`, which is the recommended
    way to use it (if you want this capability).  See the documentation for
    details.  It will clean up the indenting and white space, while doing
    little else.   You can do this for a whole file by specifying the options
    map `{:style :indent-only}`, or for a single function (say "func") by
    giving this options map: 
    `{:fn-map {"func" [:none {:style :indent-only}]}}`.

  * `-v` and `--version` switches.  Issue #94.

  * `-h`, `--help` switches.

  * `-e`, `--explain` switches to output configuration and show where
    any non-default values came from.

### Changed

  * If there was an error on the command line, either in the new
    switches (see above), or the options map, the uberjar and graalVM
    binaries used to format the input and report the error after
    formatting.  Now they do not format at all, and just report the
    error.

  * Configuring zprint with environment variables or Java properties has
    been deprecated for a good while, and is now no longer supported.

### Fixed

  * If you entered: 
```clojure
(;comment
 function this that and the other)
```
    then `function` would not previously have been recognized if
    it was defined in the `:fn-map`.  The "thing" in the first part
    of the list was what was looked up, and if it was a comment,
    that was too bad.  Now zprint will look up only actual symbols,
    not comments (or newlines).

  * If an inline comment wrapped, it would be on a line by itself.
    It would be aligned with the actual inline comment above it.
    But the next time you ran zprint on the file, the wrapped inline
    comment would be seen a a full line comment, and moved (probably
    left) to be where a full line comment should be.  Fixed this,
    so that a comment with nothing else on the line that is aligned
    with an existing inline comment will stay aligned with that
    inline comment.  Issue #67.

## 0.4.16 - 2019-06-12

### Added

### Changed

  * Implemented `:unlift-ns?`.  If this is specified for a map when formatting
  code (the only place it makes sense), a map specified with a namespace
  at the front (e.g., `#:x{:a :b :c :d}` will be formatted with the namespace
  distributed among the keys, as in `{:x/:a :b, :x/:c :d}`. Issue #80.

### Fixed

  * Fixed record output to be something that can also be read back in.
  Replace "/" in record-type with ".".  Issue #105.  

  * Fixed serious bug in handling of pairs with comments: if a comment
  was the last thing in a series of pairs, the following right parens
  would be missing!  Issue #106.

  * Fixed bug in flow indentation underneath things like `"#(...)"`.
  Issue #103.

  * Fixed bug where files that ended with a newline didn't after being
  processed with {:parse {:interpose "\n\n"}}.  Issue #100.

  * Fixed bug where namespaced maps in source code didn't format 
  correctly. Issue #104.

  * Fixed code to correctly detect when running in a REPL and enable parallel
  operation.  It was always enabling parallel operation before, which will
  cause a main program using zprint to not exit correctly unless you call
  (shutdown-agents).  Now it only enables parallel operation automatically
  if you are in a REPL, or if you call `(set-options! {:parallel? true})`.
  Issue #107.

  * Removed LC= from build.zprintm, since that apparently didn't work
  for the macOS version of graalvm.  Issue #96.

## 0.4.15 - 2019-01-14

### Fixed

  * Fixed cljs build warning.

## 0.4.14 - 2019-01-13

### Added

  * New configuration capabilities.  Everywhere zprint looks for
  a `.zprintrc` file, it will also look for a `.zprint.edn` file
  if it does not find a `.zprintrc` file. Issue #83

  * If the `.zprintrc` or `.zprint.edn` file in the `$HOME` directory
  has `:search-config?` set to `true`, zprint will look for a
  `.zprintrc` or `.zprint.edn` file in the current directory, and
  if does not find one there, in that directory's parent, and will
  continue to look in directories up the tree until it either finds
  a configuration file or runs into the root directory.  If
  `:search-confing?` is true, `:cwd-zprintrc` is ignored.
  
  * Major new capability to rewrite zippers during formatting to
  do things like sort dependencies.  This is alpha/experimental,
  which is to say, the capability may go away or change significantly.
  It has had less testing than usual, but has been tested in both
  Clojure and Clojurescript.  You can access this capabiity for
  Leiningen `project.clj` files by using `{:style :sort-dependencies}`.
  Issue #76

  * Information on using Clojure CLI.  Thanks for pull request #82

### Changed

  * Enhanced formattng of `:binding` functions by allowing empty
  body form: `(let [a b c d])`.  This will successfully format as a
  `:binding` form.  Issue #73

### Fixed

  * Wrapped a `dec` function in an `fnil` to avoid an overly enthusiatic
  Clojurescript warning in Clojurescript 1.10.439.  Issue #91.

  * Added build parameter to graalvm binary builds which helps in
  handling a broader character set. Uberjar with JVM worked anyway.

  * Fixed `zprint-file-str` and `{:parse {:interpose "\n\n"}}` which
  interacted badly.  Comments (including ;!zprint comments) would have 
  an extra blank line.  Issue #84.


## 0.4.13 - 2018-11-21

### Changed

### Fixed

  * Fixed exception when formatting complex `:arg2-extend` functions,
    like `defrecord`, which have functions that don't start with simple
    symbols or keywords.  Issue #72.

  * Fixed exception when formatting a parsed map with keys shorter than 2 
    characters.  Doesn't affect structure formatting, just maps that
    show up in strings that we parse (as in formatting source).
    Issue #74.

## 0.4.12 - 2018-11-6

### Changed

  * Allow any number of semicolons when checking for `;!zprint`.
    Thus, `;;!zprint` and `;;;!zprint` also work.

### Fixed

  * Return non-zero exit status from uberjar (and graalvm binaries) if
    it doesn't format correctly or the options map on the command line
    is bad.

  * Place all exception information for the uberjar (and graalvm binaries)
    on stderr, not stdout.

## 0.4.11 - 2018-10-28

### Changed

 * Added zprintm-0.4.11 MacOS zprint filter to release.  Thanks to
   Martin Klepsch for pointing out a "ce" version of graalvm exists
   for MacOS.  Issue #66.

 * Verified correct operation in Clojure 1.10.0-beta3.

 * Moved to rewrite-clj 0.6.1.

 * Added `cond-let` as a `:pair-fn`.

 * Added a bunch of better doc-strings, doc/cljdoc.edn to make doc
   work better with cljdoc.

 * Moved lumo and planck into .cljs files so that they work with cljdoc.

### Fixed

 * Major bug fix, (or enhancement, depending on your viewpoint).  You 
   can now run multiple zprints in the same JVM at the same time, with
   the restriction that they all need to be doing the same 'kind' of zprint,
   either all parsing strings (typically formatting code), or all operating
   on Clojure(script) structures.  Can't mix them.  But you can use
   `pmap` to get zprint-file to operate on a bunch of files at the same
   time (which was the use case that prompted this change).
   Issue #63.

 * Minor bug where `#(` stayed green inside of syntax-quoted structure, 
   instead of turning red like all other parens to.

## 0.4.10 - 2018-7-28

### Changed

 * Put `^:no-doc` in various places to prepare for codox.  Put codox
   configuration in project.clj.

 * Added capability to have a per-project `.zprintrc` file.  If your
   `$HOME/.zprintrc` file has `{:cwd-zprintrc? true}` in its options map,
   then zprint will also look for a `.zprintrc` file in the current working
   directory.

 * Added capability to have an options map associated with a function
   classification.  Support for Issue #46, as well as changes to the
   classification of `defproject`, `s/def`, and `s/fdef`.  The changes
   to `s/def` and `s/fdef` now let specs format correctly by default,
   and the `:spec` style is no longer necessary (but still there for
   compatibility).

 * Added new style `:how-to-ns`, to format `ns` declarations as in
   Stewart Sierra's "How to ns".  Issue #46.

 * Documented how to keep zprint from configuring from a `.zprintrc` 
   file, for those uses where the options should not be affected by
   any local variations: `(set-options! {:configured? true})` must
   be the first use of zprint.

### Fixed

 * Tests now are independent of any `$HOME/.zprintrc` or local
   `.zprintrc`.  

 * Don't close stdout when zprint.main/-main exits, flush instead.
   Fixes use of zprint.main by clj command.  Issue #57.

 * Fixed appcds checking for java version, now it doesn't.
   Issue #54.

 * Fixed extra spaces between newlines when using `(:style :map-nl}`.
   Issue #59.

 * Fixed problem with `#_#_` syntax, where it was missing the second element.
   Issue #58.

 * Fixed some instructions for graalvm -- set script executable, added
   #!/bin/bash to build.zprintm.

## 0.4.9 - 2018-5-14

### Changed

### Fixed

 * Back off to Clojure 1.8, since 1.9 and lein zprint don't agree with
   each other.

## 0.4.8 - 2018-5-13

### Changed

 * Both `:max-length` and `:max-depth` are now offical parts of zprint.
   They are not experimental anymore.

 * Added `{:max-length [n m]}` capability -- different length boundaries
   for different levels.  Sometimes you want to see all of the top
   level keys in a map, and just a little of the complex values.

 * Added ability to set the character for max-depth: `:max-depth-string`
   is default to "##", but could be anything (e.g., "#").

 * Added `match` and `matchm` to the `:fn-map` as `:arg1-pair-body`.  These 
   are from `core.match`.  Issue #50.

### Fixed

 * Removed (:gen-class) from the .cljs version.  Issue #51.

 * `{:max-hang-depth n}` works along with `{:max-hang-count m}` to decide
   to even try to do a hang.  Having these be 3 and 4, respectively,
   makes some maps format faster, but also messes up perfectly reasonable
   maps to the point that they are really just wrong.  So, set
   `:max-hang-depth` to 300 as a default to disable this optimization.
   Issue #48.

 * {:max-length n} didn't work for lists, and had a number of other
   problems that are now fixed.

 * {:max-depth n} also had issues.  In particular, 0 and 1 were
   the same.  Now 0 will print non-collections, but all collections
   are ##.

 * Changed `:community` style to leave `cond->` as `:arg1-pair-body`,
   instead of moving it to `:none-body`.  Issue #49.

 * (zprint nil :explain) would output the options map in color, even
   though it was zprint and not czprint.  Now it doesn't.

## 0.4.7 - 2018-2-19

### Changed

 * Rewrote line-lengths to use iterative instead of really complicated
   functional approach.  Saved maybe 5% on time.  Still complex, but 
   more understandable and slightly faster.

 * Added performance optimization `:hang-avoid` in `:list` which will
   not even bother to try a hang if lots left to do and very close to
   the right margin.  Didn't change output of anything tested or 
   zprint code itself, but saved up to 30% on very pessimal functions.
   Not so much on normal functions, but still a win for those tough
   ones.

 * Added two new styles: `:no-hang` and `:all-hang`, to allow configuring
   zprint without hangs or with hangs.  While I think that code formatted
   without hangs looks rather unlovely, some folks find the way it looks
   to be a worthwhile tradeoff for speedier performance without hangs,
   particularly in Clojurescript.  So, now, you can configure that easily
   one way or the other.

### Fixed

 * Fixed bug in configuration where `:parallel?` wasn't being set to
   true even for repl use (or in lein zprint).  Saved up to 20% in
   lein zprint, which is actually quite a bit.

## 0.4.6 - 2018-1-10

### Changed

 * Added `{:vector {:respect-nl? true}}` support, so that hand-formatted
   vectors can be preserved on ouput.

 * Added `{:vector {:option-fn-first #()}}`, which allows definition
   of a function to be called with the first element of every vector,
   and returns an options map to be used to format that vector and
   all that it contains.  Added primarily to allow targetted use
   of `:respect-nl?`, but is fully general and can certainly be used
   to enable other capabilities.

 * Added new style `:keyword-respect-nl`, which will use the two new
   capabilities above to preserve existing newlines in vectors that
   start with a keyword (as hiccup and rum HTML data do).

 * Extended `zprint-file-str` to accept an options map.  
 
 * Added `zprint-file` and `zprint-file-str` to the external API, and
   documented the use of `;!zprint` directives in the zprint readme.
   (It was already documented in the lein-zprint readme.)

 * Added tests for `zprint-file-str`, which previously was only tested
   by lein-zprint.

### Fixed

 * Additionally, changed `zprint-file-str` to leave the configured options 
   unchanged after processing.  It used to leave them around after processing
   to be retrieved with `(get-options)`, possibly distorting the next call
   to any zprint function. 

## 0.4.5 - 2017-12-9

### Changed

 * Added support for `rum` macros `defc`, `defcc`, and `defcs`,
   using `:arg1-mixin` function tag.  See the readme for details
   of what this looks like.  Issue #41.

 * Add `swap!` as `:arg2`, and `with-redefs-fn` as `:arg1-body`.

## 0.4.4 - 2017-10-26

### Changed

 * Added :color? boolean to options map, which will allow you to select
   color (or not) regardless of whether or not you use czprint or zprint.

 * Added `deftest` and `defexpect` as `:arg1-body` functions in the 
   `fn-map`.

### Fixed

 * Added inline-comment support, on by default.  Controlled by {:comment
   {:inline? true}}.  They will also be wrapped. Issue #33.

 * Fixed problem with focus interacting badly with comment wrapping,
   where a wrapped comment would change the path for later elements
   if it wrapped.  Now wrapping is performed after all focus operations
   are completed.

## 0.4.3 - 2017-10-10

### Changed

* Added support for focus on output.  This allows you to input
  an expression parsed with rewrite-clj and point at some expression
  down inside of the overall expression, and get that printed with
  "focus" (currently a rather garish reverse-video, but that will
  probably become configurable in the fullness of time).

* Added support for outputing only some lines from printing an
  expression, and replacing them with a configurable string to
  indicate an elision.  Documentation on this is coming but not
  here yet.

* Upgraded support to Clojure 1.9-beta2 and Clojurescript 1.9.946.

* Upgraded rewrite-clj to 0.6.0.

* Added `:syntax-quote-paren` to the `:color-map` and the `:uneval` 
  `:color-map`.  By default this turns the parens `:red` inside of
  syntax-quoted expressions.

* Made `assoc-in` and `doto` both be `:arg1` functions.

### Fixed

* Fixed a bug where an `:arg2*` function which wasn't able to
  hang-one threw an exception.  Pretty rare, needs a very deeply
  nested function.  `cgrand/poucet` did manage to expose this
  problem.

* Map with long keys and `:justify?` true does not become justified.
  It is worth noting that `:justify?` also has a heuristic aspect,
  where if the justification takes more width than is available it
  will fall back to unjustified.  But this also exposed a bug where
  the handling of the width didn't work in situations were you
  turned off commas.  This is now fixed.  Issue #31.

* zprint nil prints a blank line, not nil.  Issue #32.

* The latest versions of lumo and planck both have upgraded to
  tools.reader 1.0.5.  Unfortunately, rewrite-cljs 0.4.3 depended
  on an internal API for tools.reader that changed from the alpha
  version used by rewrite-cljs 0.4.3, but we've gotten a new version,
  rewrite-cljs 0.4.4, which now works with tools.reader 1.0.5, so
  both planck 2.8.1 and lumo 1.8-beta now work again with zprint.
  Issue #30.

## 0.4.2 - 2017-6-22

### Changed

* Changed printing of `:pair-fn` functions (i.e. `cond`) so that if
  there is more than one pair (and if there weren't, why would you
  use a cond), it won't print it all on one line.  If you don't
  like this, `{:remove {:fn-gt2-force-nl #{:pair-fn}}}` will turn
  it off.

* Added ability to lift namespaces out of maps with namespaced keys.
  Controlled by `:lift-ns?` and `:lift-ns-in-code?` in `:map`.  Will
  not lift namespaces when specified implicitly in strings.  That is,
  will not try to figure out correct namespace for `::a`, since when
  parsing a file this can't reliably be known.  Note that `#::{}` maps
  are not currently supported, and cannot be handled when using the
  `:parse-string? true` input approach.

### Fixed

* A `:>>` in a `condp` wasn't handled correctly (i.e., at all).  This
  made the pairing up of `condp` clauses all wrong after that.

* A problem when using a "modifier" in an `:extend` (e.g., "static" in
  `defui`) -- the modifier and the thing after it were always formatted
  onto the same line, even if they didn't fit.  Now, if they don't fit
  together, they don't end up on the same line. 

## 0.4.1 - 2017-5-18

### Changed

* Added capability to pass options map to zprint-filter by changing
  the zprint.main namespace to handle a null string, and changing
  appcds script to create a slightly different script.

* Moved to `cljs.spec.alpha` for Clojurescript too.  Zprint now requires
  Clojurescript 1.9.542 or later.

* It is now not even accept to set `:parallel?` to `true` in Clojurescript,
  as the spec has been changed to consider this an error when you try it.

* Added new capability: sorting for sets.  Similar to maps, now
  {:set {:sort? true :sort-in-code? false}} is the default.  When
  you have sets with a lot in them, it is nice to see them sorted.

* Added new capability: key-value-color for maps.  Where key-color colors a
  particular key that matches the color that you specify, key-value-color
  will also match a key, but instead of affecting the color of that key,
  it merges in a color-map which is the value of that key into the current
  color-map, affecting the printing of everything in the value.  Which
  may be a constant like a string or a keyword, but might be an entire
  complex expression.  This only works for maps.

* Removed `{:pair {:indent 0}}` from the `:spec` style.  If you like your
  pairs non-indented, feel free to add it to the style yourself.
  Put this: `{:style-map {:spec {:pair {:indent 0}}}}` in your
  `~/.zprintrc`.

### Fixed

* Fixed Issue #27, where missing function errors came up when using
  zprint in non-self-hosted Clojurescript.

* `czprint-fn` will now print specs for any function that has them.
  It will add them to the end of the doc-string (much like `doc`
  will do, but it will also pretty print them).
  In particular, try `let` or `ns` or even `defn`.  The `defn` ones
  are a little funky, as the indent isn't all that lovely.

## 0.4.0 - 2017-5-5

### Changed

* Changed to use `clojure.spec.alpha` instead of `clojure.spec`.
  Now, the 0.4.0 version runs in 1.9-alpha16, but not 1.9-alpha15.
  It still runs in 1.8 as well, but requires a different supporing
  library -- see the readme.

### Fixed

* Issue #26 -- now it runs in 1.9-alpha16.

## 0.3.3 - 2017-5-4

### Changed

* Added `appcds` script to set up zprint-filter with less opportunity
  for errors. 

* Added instructions for how to use zprint-filter with `emacs` and
  Sublime Text 2 or 3.

* Changed default for `:extend` to `{:flow? true}`, to fix issues in 
  `extend-protocol`, `reify`, and `extend` where things were coming out
  on the same line when they should not.

* Created `:arg2-extend` to better handle `deftype` and `defrecord`.

* Changed fn-type of `defprotocol` to `:arg1-force-nl`, so that it doesn't
  hang several small forms.

### Fixed

* Issue #25, where additional () were added when formatting a `(fn ...)`
  when the `(fn ...)` was in a Clojure data structure.  This didn't happen
  when formatting source in files or with the zprint-filter.

## 0.3.2 - 2017-4-18

### Changed

### Fixed

* Issue #23, where (czprint-fn defn {:style :justified}) would not
  produce any output.  This bug was added in 0.3.0.

* More work on dependencies to make it easier to release.

## 0.3.1 - 2017-4-10

### Fixed

* Fixed dependencies so that `[clojure-future-spec "1.9.0-alpha15"]`
  is actually required to use zprint on Clojure 1.8, and doesn't 
  get brought along into usage on 1.9 by default.

## 0.3.0 - 2017-4-9

### Changed

* __DEPRECATED__ configuration from environment variables and Java system
  properties.  Still available by adding a library to your dependencies.
  File an issue if you care about this!

* __DEPRECATED__ :auto-width capability.  Still available by adding a library to your dependencies.  File an issue if you care about this!

* Added zprint-filter uberjar and documentation.  Uberjar is available as
  a "release" on Github.

* Moved to clojure.spec for all versions, requiring a single additional
  library when using Clojure 1.8.

* Added futures to fzprint-hang-remaining, helped a lot.

* Used pmap to handle two-up generation, helped a lot.

* Added transient to some vectors being built.  Sometimes it helped, somtimes
  it didn't.  Kept the ones where it helped. 

### Fixed

* Dates in this file.

## 0.2.16 - 2017-2-27

### Changed

* Added zprint.lumo namespace to support zprint filter.  Only used when
  initiated by lumo.

* Added zprint.planck namespace to support zprint filter.  Only used when
  initiated by plank.
  
* Replaced function-by-function destructuring with with-redefs in both
  zutil.cljc and sutil.cljs.  Added new namespace zfns to make this work.
  Resulted in small but significant speedup.

* Major changes to Clojurescript port -- removed Prismatic/Plumatic Schema
  and replaced it with spec for checking options maps.  Refactored option
  map validation into two additional namespaces.  Now zprint will work
  in self-hosted Clojurescript, and we are ready for a complete move to
  spec when we can require 1.9.

## 0.2.15 - 2017-1-24

### Changed

* Added `{:map {:key-color {:key1 :color1 :key2 :color2 ...}}}` which will color the specified
  keys with the specified color.

* Added `{:map {:key-depth-color [:level-1-color :level-2-color ...]}}`, which
  will color the map keys based on depth, not their type.  The
  `:key-color {:key :color ...}` map will override any values from the 
  `:key-depth-color` map.  EXPERIMENTAL feature, might go away.

## 0.2.14 - 2017-1-22

### Changed

* Added a new capability to `:extend`, `:modfiers #{"static"}`, which
  will allow "static" to appear on the same line as protocol or type
  when formatting extends.  You remove elements from the set  with 
  `{:remove {:extend {:modifiers #{"static"}}}}` just like the other 
  sets are changed.  Issue #10.

* Added `defui` to the `:fn-map` as `:arg1-extend` so that when formatting
  Clojurescript om/next code you get the `static` elements to format 
  correctly.  Issue #10.

* Added several new styles: `:map-nl`, `:binding-nl`, `:pair-nl`.  Also
  added substantially to the documentation of styles in the readme.

* Added the first tests for the `:explain` output, at least the `:value`
  part of it.

### Fixed

* A problem when adding an element to any set in the options map, where the
  element was added, but the :explain output was incorrect.  Issue #21.

## 0.2.13 - 2017-1-19

### Changed

* Added support for `:hang?` in `:extend`, so that you can control
  whether or not the function definitions end up formatting as a
  hang after the type.  `:hang?` defaults to `true` (and this is
  not a change), but can now be turned off to force a flow.  Note
  that if a type and function defnition all fit on one line, that
  `:hang? false` will not affect it.  If you want them on separate
  lines you need to use `:flow? true`, which will override the normal
  desire to print things on one line.

* Altered the meaning of :nl-separator?.  In its initial release it
  would place an extra new-line whenever a pair took more than one line.
  Now, it only places an extra new-line when the pair did an actual
  flow, so that a multi-line hang does not trigger an extra new-line.
  The previous capability would be easy to add if there is any
  interest in doing that.

* Added the ability to set multiple styles at once, with a sequence
  of style keywords instead of just a single keyword.  Also added
  some great style tests.  Go expectations!

### Fixed

* The new `:nl-separator?` capability does not work right with lists
  that have constant-pairs at the end but regular s-expressions earlier.
  If the earlier s-expressions take more than one line, then they get
  blank lines below them which is wrong from several different perspectives.
  Now multiple line s-expressions don't ever trigger blank lines, since
  the `:nl-separator?` blank lines are triggered by a pair with the right
  hand part of the pair being formatted with a flow. Issue #17.

## 0.2.12 - 2017-1-9

### Fixed

* Accepted pull request to change function style for "ns" to `:arg1-body`.
  Thanks to pesterhazy!

* Accepted pull request to change function "!=" to "not=".  Thanks to
  mynomoto!

## 0.2.11 - 2017-1-8

### Changed

* Removed `:indent n` from `:pair-fn`, as it wasn't used.

* Changed `cond` and `alt` to be `:pair-fn`, and not `:pair`, since
  `:pair` was supposed to not be a function style on its own, but a
  configuration element for functions that have pairs in them, like
  `:arg1-pair` and `:pair-fn`.

* Added capability to produce blank lines after pairs where the second element
  of the pair doesn't fit on the same line as the first.  The options key
  for this is `:nl-separator?`, and is by default false for all occurances
  of this configuration option.  It is supported for `:map`, `:extend`,
  `:binding`, and `:pair` formatting.  This was requested for `:map`, but
  the implementation also allows configuring `:extend` to produce a commonly
  used format as well. Issue #12.

* Added `:flow?` options key for `:map`, `:extend`, `:binding`, and `:pair`.
  By default false for all.  When enabled, this will cause the second
  element of every pair to be formatted on the line after the first element,
  thus acting line a "flow".  See the readme for differences between
  `:flow?`, `:hang?`, `:force-nl?`, and `:nl-separator?`.  This was
  largely added to work together with `:nl-separator?` in `:extend`, but
  also has uses elsewhere.  `:flow?` will override the indent checks in
  when printing things two up, so that it will force a flow even though
  the first element of the pair is so short that it doesn't make any
  sense.  Just because it is so confusing if it doesn't do that.

### Fixed

* Fixed constant pairs to be formatted under control of the configuration
  for `:pair`, which now includes `:flow?` and `:nl-separator?`.

* Serious bug where whitespace after left parenthesis causes either the 
  first or second element in a list to be repeated if the list doesn't fit
  entirely on one line.  Issue #16.


## 0.2.10 - 2016-12-23

### Changed

* Added two new sets in the config, :fn-gt2-force-nl and :fn-gt3-force-nl
  into which you can put fn-styles so that they will never print on the
  same line even if they could.  The gt2 is if it has greater than 2 args
  and the gt3 is likewise if it has greater than 3 args.  Issue #3.

* Refactored `zprint-file`, pulling out `zprint-file-str` so that boot-fmt
  can use it.  Added `zprint-file-str` to the API.  Issue #7.

* Added `:arg2-fn` to support proxy.

* Added `:arg1-force-nl` to support `s/def`.  Put several additional
  spec functions into the `:fn-map` so that spec files now pretty nicely.

* Added experimental options to try to speed up map printing at the
  repl, which slows down when the maps are really deeply nested.
  `:max-hang-span`
  `:max-hang-depth`
  `:max-hang-count`
  These are experimental, and will probably not keep their current names
  or location in the options map.  They are not documented.  Do not 
  depend on them.

* Added `:flow` and `:flow-body` to force some functions to never hang.
  Largely added for `:require` in `ns` declarations.  Issue #5.

* Added `:require` to the `:fn-map` as `:force-nl-body` so that it will
  hang all of the requires.  If you want it to pull them 
  all over to the left and not even have something on the same line as
  the `:require`, define it as `:flow-body`.  Issue #2.

### Fixed

* Added a capability to remove elements from a set, now that sets in
  the config are more common.   Also, fixed it so that you can specify
  additional members for a set, and they will be merged into the set,
  instead of replacing the entire set.  Issue #4.

* Fixed `:fn-force-nl` to accurately match the function type, and not
  do it after the `-body` has been removed.  Issue #6.

* A bug in `:arg1-extend` where, when used for `proxy`, some of the function
  was getting dropped and not output.  Since `proxy` was not defined as
  `:arg1-extend`, this is not as serious as it might seem, but it was not
  good.

## 0.2.9 - 2016-11-13

### Changed

* Added :force-nl and :force-nl-body along with :fn-force-nl set in
  configuration to force some functions to never format onto the same
  line.  Primarily for -> and ->> and other similar functions where
  most people want to see them listed vertically, not on the same line.

* Made keywords format as functions, since they are.  This cleans up
  ns declarations a bit at the start of files.
  
### Fixed

## 0.2.8 - 2016-11-9

### Changed

* Added documentation on experimental features: auto-width, max-length,
  max-depth.

### Fixed

* Fixed bug in handling of :left-space :keep and :drop which involved a 
  complete rework of options map handling in process-multiple-forms
  and zprint-str-internal and czprint-str-internal.  Should have fixed
  a number of other not yet encountered bugs.
* Fixed issue in fzprint-hang-remaining where if the hang-expand value was
  exceeded it wouldn't bother to hang.  Which interacted badly with the
  hang-if-equal-flow? capability.  Added test to ensure this stays fixed.
* Fixed bug where if the first element of a pair was a map that got a 
  comma, and the second element of the pair ended up with a hang, the 
  first line of the hang would have the wrong indent because the size of
  the first element of the pair was calculated incorrectly.
* Fixed a bug where :map {:comma? false} didn't actually work if the map
  fit on one line.  Worked fine for multi-line maps, not one line ones.

## 0.2.7 - 2016-11-1

### Changed

* Changed hang-expand to 4.0 most everywhere
* Changed fn-map of -> to :none-body, and catch to :arg2
* Considerable changes to support Clojurescript.  All files now .cljc
* Added {:file {:left-space :keep}} or :drop to handle spaces on the
  left of function definitions.
* {:parse-string? true} will always trim spaces off of what is being parsed.
* (:parse-string-all? true} will parse multiple forms in one string.  This
  does not support ;!zprint {<options-map>} comments.

### Fixed

* Fixed bug where -> affected :arg1 status of more than immediate args
* Fixed issue #1 with incorrect indent on 3rd arg to (fn ...)

## 0.2.6 - 2016-10-10

### Changed

* Changes to support :reader-cond {:force-nl? boolean}
* Added defrecord to function categorization.

## 0.2.5 - 2016-10-06

### Changed

* Changed heuristics so that if hang and flow take the same number of
lines, then it will hang.  Can be adjusted with tuning.

## 0.2.4 - 2016-10-05

### Changed

* Reworked README.md, internal TOC, added information about several
existing capabilities.  Alphabetized the configurable elements.

## 0.2.3 - 2016-10-01

### Changed

* Removed `:list {:indent-body n}`, made it `:indent`.  Made `:indent-arg`
optional, uses `:indent` if it doesn't appear.
* Added `:support` keyword (replaced undocumented `:explain-all`).
* Added documentation for `:justify?` in readme.
* Added `:key-ignore` and `:key-ignore-silent` for `:map` formatting.
* Added `:to-string?` for `:record` formatting.

### Removed

* Removed (get-options) from zprint.core, took need for it out of readme.

### Fixed

* A problem where the last newline in a file was getting lost.
* A problem where a comment inside of a fn starting in col 0 was getting
an extra newline.

## 0.2.2 - 2016-09-24
### Changed

* Straighten out core and config namespaces for API
* Added :old? and :format to options map for lein-zprint

## 0.2.1 - 2016-09-22
### Changed

* Moved some configuration functions into zprint.core to match readme.
* Put ALPHA banner in readme.

* Fixed a bug where it wouldn't configure if no ~/.zprintrc file.

## 0.2.0 - 2016-09-21
### Added
- Initial project commit.

