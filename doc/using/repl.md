# Use zprint at the REPL
zprint was designed to be very helpful at the REPL.  What do you have to do?

[1. Get zprint into the dependencies](#1-get-zprint-into-the-dependencies)  
[2. Require zprint when you run the REPL](#2-require-zprint-when-you-run-the-repl)   
[3. Use zprint](#3-use-zprint)  


## 1. Get zprint into the dependencies
First, you have to make sure zprint shows up in your dependencies.
### Leiningen (project.clj)
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
                 [zprint "1.1.2"]]
  :repl-options {:init-ns zpuse.core})
```

__Even better -- put it in the:__ 

`:profiles {:dev {:dependencies [zprint "1.1.2]}}`

like this:

```clojure
(defproject zpuse "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :profiles {:dev {:dependencies [zprint "1.1.2]}}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :repl-options {:init-ns zpuse.core})
```

### deps.edn
```clojure
{:deps {org.clojure/clojure {:mvn/version "1.9.0"},
        zprint {:mvn/version "1.1.2"}}}
```
## 2. Require zprint when you run the REPL
You need to get it availble to you when you run the REPL.
```clojure
zpuse.core=> (require '[zprint.core :as zp])
```
## 3. Use zprint
### Format a structure
```clojure
zpuse.core=> (def example {:this :is :a :test :it :is :only :a :test :foo :bar :baz :but :better :if :it :does :not :fit :on :one :line})
#'zpuse.core/example
zpuse.core=> example
{:but :better, :one :line, :only :a, :if :it, :fit :on, :this :is, :does :not, :bar :baz, :it :is, :test :foo, :a :test}
zpuse.core=> (zp/czprint example)
{:a :test,
 :bar :baz,
 :but :better,
 :does :not,
 :fit :on,
 :if :it,
 :it :is,
 :one :line,
 :only :a,
 :test :foo,
 :this :is}
nil
zpuse.core=> 
```
### Configure zprint
Let's say that you don't want commas in maps.
```clojure
zpuse.core=> (zp/set-options! {:map {:commas? false}})
Execution error at zprint.config/internal-set-options! (config.cljc:928).
set-options! for repl or api call 2 found these errors: 
In repl or api call 2, In the key-sequence [:map :commas?] the key :commas? was not recognized as valid!

; Oops, it isn't "commas?", it must be "comma?"...

zpuse.core=> (zp/set-options! {:map {:comma? false}})
nil
zpuse.core=> (zp/czprint example)
{:a :test
 :bar :baz
 :but :better
 :does :not
 :fit :on
 :if :it
 :it :is
 :one :line
 :only :a
 :test :foo
 :this :is}
nil
zpuse.core=> 
```

### Show source for a function
Note the specs (if any) are included in the doc-string for the function.
Formatted, of course!
```clojure
zpuse.core=> (zp/czprint-fn defn)
(def
  ^{:doc
      "Same as (def name (fn [params* ] exprs*)) or (def
    name (fn ([params* ] exprs*)+)) with any doc-string or attrs added
    to the var metadata. prepost-map defines a map with optional keys
    :pre and :post that contain collections of pre or post conditions.

  Spec:
    args: (cat :fn-name simple-symbol?
               :docstring (? string?)
               :meta (? map?)
               :fn-tail
                 (alt :arity-1 :clojure.core.specs.alpha/params+body
                      :arity-n
                        (cat :bodies
                               (+ (spec :clojure.core.specs.alpha/params+body))
                             :attr-map (? map?))))
    ret: any?",
    :arglists '([name doc-string? attr-map? [params*] prepost-map? body]
                [name doc-string? attr-map? ([params*] prepost-map? body) +
                 attr-map?]),
    :added "1.0"}
  defn
  (fn defn [&form &env name & fdecl]
    ;; Note: Cannot delegate this check to def because of the call to (with-meta
    ;; name ..)
    (if (instance? clojure.lang.Symbol name)
      nil
      (throw (IllegalArgumentException.
               "First argument to defn must be a symbol")))
    (let [m (if (string? (first fdecl)) {:doc (first fdecl)} {})
          fdecl (if (string? (first fdecl)) (next fdecl) fdecl)
          m (if (map? (first fdecl)) (conj m (first fdecl)) m)
          fdecl (if (map? (first fdecl)) (next fdecl) fdecl)
          fdecl (if (vector? (first fdecl)) (list fdecl) fdecl)
          m (if (map? (last fdecl)) (conj m (last fdecl)) m)
          fdecl (if (map? (last fdecl)) (butlast fdecl) fdecl)
          m (conj {:arglists (list 'quote (sigs fdecl))} m)
          m (let [inline (:inline m)
                  ifn (first inline)
                  iname (second inline)]
              ;; same as: (if (and (= 'fn ifn) (not (symbol? iname))) ...)
              (if (if (clojure.lang.Util/equiv 'fn ifn)
                    (if (instance? clojure.lang.Symbol iname) false true))
                ;; inserts the same fn name to the inline fn if it does not have
                ;; one
                (assoc m
                  :inline (cons ifn
                                (cons (clojure.lang.Symbol/intern
                                        (.concat (.getName ^clojure.lang.Symbol
                                                           name)
                                                 "__inliner"))
                                      (next inline))))
                m))
          m (conj (if (meta name) (meta name) {}) m)]
      (list 'def
            (with-meta name m)
            ;;todo - restore propagation of fn name
            ;;must figure out how to convey primitive hints to self calls first
            ;;(cons `fn fdecl)
            (with-meta (cons `fn fdecl) {:rettag (:tag m)})))))
nil
zpuse.core=> 
```
### Help!
You can get the entire API by asking for help:
```clojure
zpuse.core=> (zp/czprint nil :help)
zprint-1.1.2

 The basic call uses defaults, prints to stdout

   (zprint x)

 All zprint functions also allow the following arguments:

   (zprint x < width >)
   (zprint x < width > < options >)
   (zprint x < options >)

 Format a function to stdout (accepts arguments as above)

   (zprint-fn < fn-name >)

 Output to a string instead of stdout:

   (zprint-str x)
   (zprint-fn-str < fn-name >)

 Syntax color output for an ANSI terminal:

   (czprint x)
   (czprint-fn < fn-name >)
   (czprint-str x)
   (czprint-fn-str < fn-name >)

 The first time you call a zprint printing function, it configures
 itself from $HOME/.zprintrc.

 Explain current configuration, shows all possible configurable
 values as well as source of non-default values:

   (zprint nil :explain)

 Change current configuration from running code:

   (set-options! < options >)

 Format a complete file (recognizing ;!zprint directives):

   (zprint-file infile file-name outfile < options >)

 Format a string containing multiple "top level" forms, essentially
 a file contained in a string, (recognizing ;!zprint directives):

   (zprint-file-str file-str zprint-specifier < options > < doc-str >)

 Output information to include when submitting an issue:

   (zprint nil :support)


nil
zpuse.core=> 
```
## Many more examples...
You can find lots of useful ways to use zprint at the REPL 
[here](../types/repl.md).

