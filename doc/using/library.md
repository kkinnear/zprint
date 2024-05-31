# Use zprint from inside a Clojure program (as a library)
zprint was designed to be useful as a library whenever you have things
that you need to output in a well formatted way.  It has minimal dependencies.
What do you have to do?
## 1. Get zprint into the dependencies
First, you have to make sure zprint shows up in your dependencies.
#### Leiningen (project.clj)
Put:

[![Clojars Project](https://img.shields.io/clojars/v/zprint.svg)](https://clojars.org/zprint)

in the dependencies.  For example:
```clojure
(defproject zpuse "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [zprint "1.3.0"]]
  :repl-options {:init-ns zpuse.core})
```
#### deps.edn
```clojure
{:deps {org.clojure/clojure {:mvn/version "1.9.0"},
        zprint {:mvn/version "1.3.0"}}}
```
## 2. Require zprint in your ns directive:
```clojure
(:require [zprint.core :refer
           [zprint-str czprint-str zprint czprint zprint-fn czprint-fn
            zprint-fn-str czprint-fn-str zprint-file-str]])
```
## 3. Configure zprint
In the absence of specific directives, zprint will configure itself from
`~/.zprintrc`.  You can configure additional, more specific options by
calling `set-options!`:
```clojure
zpuse.core=> (zp/set-options! {:width 90})
nil
zpuse.core=> 
```
### Want to ignore globally configured zprint options?
If you want your use of zprint as a library to ignore any global zprint
configuration and only respond to configuration you have set with calls to
`set-options!`, then ensure that this is your first use of zprint in
any way:
```clojure
zpuse.core=> (zp/set-options! {:configured? true})
nil
zpuse.core=> 
```
This call will cause zprint to skip looking in the `~/.zprintrc` file and
any other external configuration alternatives.  You can set any configuration
you wish by subsequent calls to `set-options!`.

## 4. Use zprint

You can use any of the available zprint API calls.  Things to note:

### Calls that end in -str 
Calls that end in `-str` produce output as strings, which lets you do with
it what you want.  These are particularly useful when zprint is called as
a library.

### zprint-file-str will format multiple top level forms
If you want to format a file or a buffer, `zprint-file-str` will take
a string which represents a file (containing lots of newlines), format it,
and produce a string in response. It typically takes three arguments:
`[file-str zprint-specifier new-options]`.

  * `file-str`  
  The string which contains the file or multiple top level forms.

  * `zprint-specifier`  
  A string which identifies this call (perhaps a file name or other identifier)
  which can be used in error messages produced by 
  incorrect `;!zprint` directives found in `file-str`.

  * `new-options`  
  An options map containing configuration specific to this call of zprint.

```clojure
zprint.core=> (print t1xc)

(defn multi-arity
  ([x] body) ([x y]
       body))

(let [x    1 y   
      2]
 body)

[1 2 3
       4 5 6]

{:key-1      v1 :key-2    
 v2}

#{a  b   c
  d  e   f}
nil
zprint.core=> (zprint-file-str t1xc "example" {:width 17})
"\n(defn multi-arity\n  ([x] body)\n  ([x y] body))\n\n(let [x 1\n      y 2]\n  body)\n\n[1 2 3 4 5 6]\n\n{:key-1 v1,\n :key-2 v2}\n\n#{a b c d e f}\n"
zprint.core=> (print *1)

(defn multi-arity
  ([x] body)
  ([x y] body))

(let [x 1
      y 2]
  body)

[1 2 3 4 5 6]

{:key-1 v1,
 :key-2 v2}

#{a b c d e f}
nil
zprint.core=> 
```



