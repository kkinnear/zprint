# How the second element of a pair is indented

Part of the reason for zprint's existence revolves around the
current approach to indenting used for `cond` clauses, `let` binding vectors,
and maps and other things with pairs (extend and reader conditionals).

Historically, some of the key functions that include pairs, e.g.
`cond` and `let`, had their pairs nested in parentheses.  Clojure doesn't
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
second part of these pairs by 2 columns (controlled by `{:pair {:indent 2}}`
for `cond` and `{:binding {:indent 2}}` for binding functions).

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

; Here is the default zprint formatting, when the second element of a
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

; Some peope like to separate the pairs that end up on the next line
; with a blank line

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

```
Here is a realistic and more complex example of both approaches:

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

Maps also have pairs, and perhaps suffer from the potential
for confusion a bit more then binding-forms and cond functions.
By default then, the map indent for the value that is placed on the
next line (i.e., in a flow) is 2 (controlled by `{:map {:indent 2}}`).
The default is 2 for extend and reader-conditionals as well.

Is this perfect?  No, there are opportunities for confusion here
too, but it works considerably better for me, and it might for
you too. I find this particularly useful for `:binding` and `:map`
formatting.

Should you not like what this does to your code or your s-expressions,
the simple answer is to use `{:style :community}` as an options-map
when calling zprint (specify that in your `.zprintrc` file, perhaps).

You can change the indent from the default of 2 to 0 individually
in `:binding`, `:map`, or `:pair` if you want to tune it in more detail.
