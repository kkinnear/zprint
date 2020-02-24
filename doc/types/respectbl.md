# Respect Blank Lines
One `:style` available for classic zprint is `:respect-bl`, which means
"respect blank lines".  This does everything that classic zprint does, with
the exception of detecting "blank" lines in the source, and preserving
the on the output.  A "blank" line is any line which contains only
whitespace (of any amount or kind).

You may want to have zprint format your source while reserving the option
of placing blank lines in your source, for whatever reason.  To do this,
use `{:style :respect-bl}`.

## Examples from clojure.core

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



