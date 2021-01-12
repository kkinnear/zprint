# Community Standards

There have been several efforts over the years to develop some
standards for how well formatted Clojure source code should look.
One of these efforts has been the development of a 
[Clojure style guide] (https://guide.clojure.style)
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

This difference is reflected in the `:style :community`, which when specified
will alter the default configuration so that zprint will format code to the
community standards:

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
This change to the defaults for zprint changes several things:

  * Do not indent the second element of a pair when the second element
  of the pair does not fit on the same line as the first and must be
  started on the line under the first element.

  * Do not format some functions specially to make them more understandable.

  * Treat "body" functions and "argument" function differently in the way
  that indentation is performed for these functions.  In my explorations
  on GitHub, this is probably the least frequently followed of the
  community formatting standards.

### Do not indent the second element of a pair

You can read lots about this [here](./pairs.md).

Here is a simple example of the difference, where the width has been narrowed
in order to force the second element onto the next line in each case:
```clojure
; Here is what you get with the default zprint format with a normal width.

(czprint-fn pair-indent {:width 80})

(defn pair-indent
  "An exmple showing how pairs are indented."
  [a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))

; Here is what you get with the community formatting and a normal width.
; There is no difference between these two.

(czprint-fn pair-indent {:style :community :width 80})

(defn pair-indent
  "An exmple showing how pairs are indented."
  [a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))

; Here is the default zprint formatting, where the second element of a
; cond pair is indented when it formats onto the next line due to the 
; narrow width.

(czprint-fn pair-indent {:width 22})

(defn pair-indent
  "An exmple showing how pairs are indented."
  [a b c d]
  (cond
    (nil? a) (list d)
    (nil? b)
      (list c d a b)
    :else
      (list a b c d)))

; Here is the community formatting, where the second element of a
; cond pair is aligned with the first element when it formats onto the
; next line due to the narrow width.

(czprint-fn pair-indent {:style :community :width 22})

(defn pair-indent
  "An exmple showing how pairs are indented."
  [a b c d]
  (cond
    (nil? a) (list d)
    (nil? b)
    (list c d a b)
    :else
    (list a b c d)))

; Some people like to separate the pairs that end up on the next line
; with a blank line, though this isn't community endorsed

(czprint-fn pair-indent {:style [:community :pair-nl] :width 22})

(defn pair-indent
  "An exmple showing how pairs are indented."
  [a b c d]
  (cond
    (nil? a) (list d)
    (nil? b)
    (list c d a b)

    :else
    (list a b c d)))

; Some people like to separate all of the pairs with a blank line,
; though this too isn't community endorsed

(czprint-fn pair-indent {:style [:community :pair-nl-all] :width 22})

(defn pair-indent
  "An exmple showing how pairs are indented."
  [a b c d]
  (cond
    (nil? a) (list d)

    (nil? b)
    (list c d a b)

    :else
    (list a b c d)))
```

Here is a more realistic example of the difference:

```clojure
(czprint-fn cond-let)

(defmacro cond-let
  "An alternative to `clojure.core/cond` where instead of a test/expression pair, it is possible
  to have a :let/binding vector pair."
  [& clauses]
  (cond (empty? clauses) nil
        (not (even? (count clauses)))
          (throw (ex-info (str `cond-let " requires an even number of forms")
                          {:form &form, :meta (meta &form)}))
        :else
          (let [[test expr-or-binding-form & more-clauses] clauses]
            (if (= :let test)
              `(let ~expr-or-binding-form (cond-let ~@more-clauses))
              ;; Standard case
              `(if ~test ~expr-or-binding-form (cond-let ~@more-clauses))))))

; Here it is using {:style :community}, which doesn't indent pairs that flow
; Look at the (throw ...) in the (cond ...)

(czprint-fn cond-let {:style :community})

(defmacro cond-let
  "An alternative to `clojure.core/cond` where instead of a test/expression pair, it is possible
  to have a :let/binding vector pair."
  [& clauses]
  (cond (empty? clauses) nil
        (not (even? (count clauses)))
        (throw (ex-info (str `cond-let " requires an even number of forms")
                        {:form &form, :meta (meta &form)}))
        :else
        (let [[test expr-or-binding-form & more-clauses] clauses]
          (if (= :let test)
            `(let ~expr-or-binding-form (cond-let ~@more-clauses))
            ;; Standard case
            `(if ~test ~expr-or-binding-form (cond-let ~@more-clauses))))))

```

Look at the `(throw ...)` in the `(cond ...)` to see the difference.

### Different indents for body functions and argument functions?

At some point, the "community standards" for Clojure source formatting
made a distinction between "body functions" and "argument functions",
and wanted "argument functions" to have an indent of 1, and "body functions"
to have an indent of 2.  The theory seemed to be that "body functions"
were functions which had executable forms in them, often (though not
always) of indeterminate number.  "Argument functions", on the other
hand, had arguments (typically a fixed number) which were values and
not primarily executable forms.  

To support this (to my mind rather unnecessary and not widely adopted)
distinction, zprint will accept the suffix `-body` to many of the function
types, and will also accept a value for `:indent-arg`, which (if non-nil)
will be used as the indent for argument functions (which is 
everything that is not explicitly classified as a body function).

Here is a simple (and contrived) example that illustrates the difference
between the default zprint indent of 2 for all lists with a symbol as the
first element, and the community formatting which has different indents
for different types of functions:

```clojure
; Note: if you don't restrict the width, both {:style :community} and the
; default zprint formatting are identical

; The default formatting with restricted width to force things onto 
; subsequent lines

(czprint-fn body-indent {:width 16})
(defn
  body-indent
  "An example showing how indent for body fns differs from argument fns."
  [thing
   something-else
   ala bala
   portokala]
  ; Body functions
  (when thing
    (something-else))
  (with-out-str
    (prn "Hi")
    (prn "You"))
  ; Argument functions
  (filter even?
    (list
      (range
        1
        10)
      (range
        100
        1000)))
  (or
    ala
    (list
      bala
      ala
      bala)
    portokala))

; The community formatting, also with restricted width.  The body functions
; don't change, but look at filter, or, list (the first one), and range -- the 
; indent on all of these functions is one less than that above.

(czprint-fn body-indent {:style :community :width 16})

(defn
  body-indent
  "An example showing how indent for body fns differs from argument fns."
  [thing
   something-else
   ala bala
   portokala]
  ; Body functions
  (when thing
    (something-else))
  (with-out-str
    (prn "Hi")
    (prn "You"))
  ; Argument functions
  (filter
   even?
   (list
    (range 1 10)
    (range
     100
     1000)))
  (or
   ala
   (list bala
         ala
         bala)
   portokala))
```

Here is a more realistic (and more confusing) example that illustrates 
the different indent for a body function as well as the indent for the 
second element of a pair:

```clojure
(czprint-fn with-open)

(defmacro with-open
  "bindings => [name init ...]

  Evaluates body in a try expression with names bound to the values
  of the inits, and a finally clause that calls (.close name) on each
  name in reverse order."
  {:added "1.0"}
  [bindings & body]
  (assert-args (vector? bindings)
               "a vector for its binding"
               (even? (count bindings))
               "an even number of forms in binding vector")
  (cond (= (count bindings) 0) `(do ~@body)
        (symbol? (bindings 0))
          `(let ~(subvec bindings 0 2)
                (try (with-open ~(subvec bindings 2) ~@body)
                     (finally (. ~(bindings 0) close))))
        :else (throw (IllegalArgumentException.
                       "with-open only allows Symbols in bindings"))))

(czprint-fn with-open {:style :community})

(defmacro with-open
  "bindings => [name init ...]

  Evaluates body in a try expression with names bound to the values
  of the inits, and a finally clause that calls (.close name) on each
  name in reverse order."
  {:added "1.0"}
  [bindings & body]
  (assert-args (vector? bindings)
               "a vector for its binding"
               (even? (count bindings))
               "an even number of forms in binding vector")
  (cond (= (count bindings) 0) `(do ~@body)
        (symbol? (bindings 0))
        `(let ~(subvec bindings 0 2)
              (try (with-open ~(subvec bindings 2) ~@body)
                   (finally (. ~(bindings 0) close))))
        :else (throw (IllegalArgumentException.
                      "with-open only allows Symbols in bindings"))))
```

Look at the indent of "with-open only allows Symbols in bindings".  It
is different.  In the first example, all lists in code with symbols as 
their first element are indented by
2.  In the second, only known body functions are indented by 2 when formatted
with a flow.  Note that when using `{:style :community}`, unless you tell 
zprint that functions that you define are "body" functions by making 
them at least `:none-body` in the `:fn-map`, then whenever your functions 
are formatted with a flow they will be indented by 1, not 2 spaces.


## How to get community endorsed formatting?

You can place the options map `{:style :community}` 
[anywhere an options map is accepted](../altering.md#2-get-the-options-map-recognized-by-zprint-when-formatting).



