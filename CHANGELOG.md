# Change Log
All notable changes to this project will be documented in this file. 

## 0.2.11 - 2016-1-8

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

