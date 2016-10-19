# Change Log
All notable changes to this project will be documented in this file. 

## 0.2.7 - 2016-10-31

### Changed

* Changed hang-expand to 4.0 most everywhere
* Fixed bug where -> affected :arg1 status of more than immediate args
* Changed fn-map of -> and ->> to :none-body, and catch to :arg2
* Fixed issue #1 with incorrect indent on 3rd arg to (fn ...)
* Considerable changes to support Clojurescript.  All files now .cljc
* Added {:file {:left-space :keep}} or :drop to handle spaces on the
  left of function definitions.
* {:parse-string? true} will always trim spaces off of what is being parsed.
* (:parse-string-all? true} will parse multiple forms in one string.  This
  does not support ;!zprint {<options-map>} comments.

### Removed

### Fixed

## 0.2.6 - 2016-10-10

### Changed

* Changes to support :reader-cond {:force-nl? boolean}
* Added defrecord to function categorization.

### Removed

### Fixed

## 0.2.5 - 2016-10-06

### Changed

* Changed heuristics so that if hang and flow take the same number of
lines, then it will hang.  Can be adjusted with tuning.

### Removed

### Fixed

## 0.2.4 - 2016-10-05

### Changed

* Reworked README.md, internal TOC, added information about several
existing capabilities.  Alphabetized the configurable elements.

### Removed

### Fixed

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

### Removed

### Fixed

## 0.2.1 - 2016-09-22
### Changed

* Moved some configuration functions into zprint.core to match readme.
* Put ALPHA banner in readme.

### Removed

### Fixed

* Fixed a bug where it wouldn't configure if no ~/.zprintrc file.

## 0.2.0 - 2016-09-21
### Added
- Initial project commit.

