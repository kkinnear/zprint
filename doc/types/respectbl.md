# Respect Blank Lines

[Skip immediately to examples](#examples-from-clojure)

Whenever a blank line appears in the source, it will be "respected", and
will appear in the output.  However, all other formatting will be
applied around any blank lines that may appear.  

Note that blank lines at the top level (i.e., outside of function
definitions and `(def ...)` expressions) are always respected and 
never changed.  This style extends that behavior into the actual function
definitions.

Respect blank lines has been implemeted for lists, maps, vectors, and sets.
It is separately controllable for each, and can be turned on for all of 
them by using the style: `{:style :respect-bl}`.

Another style, "respect new lines" (i.e., `:respect-nl`) sounds
like a similar style, but the actual results are quite different.
With `:respect-nl`, no lines are ever joined together.  Lines that
are too long may be split, but that is the extent of changes allowed
concerning where things appear on lines.  The freedom for zprint
to actually format the code is quite limited with `:respect-nl`.

Alternatively, with `:respect-bl`, there is plenty of freedom for zprint
to format the code in a maximally readable manner, since only blank lines
interrupt zprint's ability to flow code back and forth between lines
as necessary for good formatting.

## Enforcing a particular formatting style

Note that zprint can be used in a number of ways.  If you are using
zprint to enforce a particular format on code (say in a group setting),
then `:respect-bl` is probably not a great choice, since different people
will want to put blank lines in different places for readability.

There are several ways to get zprint to intentionally place blank
lines in particular places when formatting code, and these approaches
are compatible with using zprint to enforce a particular code
formatting approach.  These approaches do not depend on any particular
format for the input -- the blank lines appear regardless of the
input formatting.  That said, they only appear if the rightmost element
of a pair doesn't start on the same line as the leftmost element.

Here are some styles that will place a blank line between pairs of elements
where the rightmost element doesn't format as a hang (i.e., doesn't at least
start on the same line as the leftmost element).

 * [add newlines between pairs that flow in let binding vectors](../reference.md#map-nl-pair-nl-binding-nl)
 * [add newlines between `cond`, `assoc`, etc. pairs that flow](../reference.md#map-nl-pair-nl-binding-nl)
 * [add newlines between extend clauses that flow](../reference.md#extend-nl)
 * [add newlines between map pairs that flow](../reference.md#map-nl-pair-nl-binding-nl)


## Examples from clojure

Note that these examples from `clojure.core` have blank lines in their original
source formatting.  Using `:respect-bl` when formatting them lets these
existing blank lines show through.

### Classic zprint 
```clojure
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
### Classic zprint `{:style :respect-bl}`
```clojure
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
### Classic zprint `{:extend {:nl-separator? true}}`
You can get something similar automatically with 
`{:extend {:nl-separator? true}}`.  But it only works with types.
```clojure
(czprint-fn ->ArrayChunk {:extend {:nl-separator? true}})

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
## Another realistic example from clojure.core

### Classic zprint
```clojure
(czprint-fn clojure.spec.alpha/abbrev)

(defn abbrev
  [form]
  (cond
    (seq? form)
      (walk/postwalk
        (fn [form]
          (cond (c/and (symbol? form) (namespace form)) (-> form
                                                            name
                                                            symbol)
                (c/and (seq? form) (= 'fn (first form)) (= '[%] (second form)))
                  (last form)
                :else form))
        form)
    (c/and (symbol? form) (namespace form)) (-> form
                                                name
                                                symbol)
    :else form))
```
### Classic zprint `{:style :respect-bl}`
This function was written with some blank lines in it for clarity.
```clojure
(czprint-fn clojure.spec.alpha/abbrev {:style :respect-bl})

(defn abbrev
  [form]
  (cond (seq? form)
          (walk/postwalk
            (fn [form]
              (cond
                (c/and (symbol? form) (namespace form)) (-> form
                                                            name
                                                            symbol)
                
                (c/and (seq? form) (= 'fn (first form)) (= '[%] (second form)))
                  (last form)
                
                :else form))
            form)
        
        (c/and (symbol? form) (namespace form)) (-> form
                                                    name
                                                    symbol)
        
        :else form))
```
## When to use `{:style :respect-bl]`
If you like to use blank lines within your function definitions,
it would be reasonable to place `{:style :respect-bl}` in your `~/.zprintrc`
file to use it for all of your files.  Blank lines __between__ function 
definitions (i.e., at the "top level") will always be preserved regardless 
of style.  But if you like to use blank lines to organize your code inside
of function definitions, then using `{:style :respect-bl}` all of the time 
would be a reasonable thing to do.



