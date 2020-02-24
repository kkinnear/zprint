# Indent Only
A substantial number of people want a source formatter to only indent the
code that they have written, but __not__ move the code from line to line.  For
this type of source formatting, use `{:style :indent-only}`.  

This is radically different from classic zprint, which essentially
ignores on which line something appears in the input
when formatting the output.

Let's look at some simple examples of what `{:style :indent-only}` will do:

### As written
```clojure
(when something
          body)

(defn f   [x]
      body)

(defn f
            [x]
body)

(defn many args [a b c
                               d e f]
      body)
```
### Indent Only
```clojure
(when something
  body)

(defn f [x]
  body)

(defn f
  [x]
  body)

(defn many args [a b c
                 d e f]
  body)
```
### As written
```clojure
(defn multi-arity
  ([x]
       body)
  ([x y]
       body))

(let [x    1
      y   2]
 body)

[1 2 3
       4 5 6]

{:key-1      v1
   :key-2    v2}

#{a  b   c
  d  e   f}
```
### Indent Only
```clojure
(defn multi-arity
  ([x]
   body)
  ([x y]
   body))

(let [x 1
      y 2]
  body)

[1 2 3
 4 5 6]

{:key-1 v1
 :key-2 v2}

#{a b c
  d e f}
```
## What's not to love?
At this point, it looks like `{:style :indent-only}` will do pretty much
what you want.  To a certain extent that is true -- if the incoming source
is formatted in a reasonably understandable way, `{:style :indent-only}` will
tidy it up a bit and present it in the best possible light.

However, if the incoming code isn't formatted in an understandable way,
`{:style :indent-only}` will not untangle it nor clarify it.  For example...

### As written -- poorly
Here is an example where the incoming format is ... well, wrong by most
anyone's idea for formatting.
```clojure
(defn multi-arity
  ([x] body) ([x y]
       body))

(let [x    1 y   
      2]
 body)

{:key-1      v1 :key-2    
 v2}
```
### Indent Only
The `{:style :indent-only}` will make it better, but won't remove the
"wrong" parts:
```clojure
(defn multi-arity
  ([x] body) ([x y]
              body))

(let [x 1 y
      2]
  body)

{:key-1 v1 :key-2
 v2}
```
### Classic zprint
In contrast to indent-only, classic zprint will "fix" the format so that
it isn't "wrong".  The width was set to 17 to make these trival examples
require multiple lines to ease comparison with the above output.
```clojure
(defn multi-arity
  ([x] body)
  ([x y] body))

(let [x 1
      y 2]
  body)

{:key-1 v1,
 :key-2 v2}
```
## Additional examples with reasonably formatted input
### As written
```clojure
(or (condition-a)
          (condition-b))

(or (condition-a)
    (condition-b))

(filter      even?
(range  1    10))

(clojure.core/filter even?
                   (range 1 10))

(filter
    even?
      (range 1 10))
```
### Indent Only
Note the second `(or ...)` -- if the third element of a list starts out
indented exactly under the second element, then the third and any subsequent
elements are aligned with the second element (called a "hang" in classic zprint parlance).
```clojure
(or (condition-a)
  (condition-b))

(or (condition-a)
    (condition-b))

(filter even?
  (range 1 10))

(clojure.core/filter even?
  (range 1 10))

(filter
  even?
  (range 1 10))
```
### As written
Here is the above example, again formatted, if not wrong, then a bit off. 
```clojure
(or (condition-a)
          (condition-b))

(or (condition-a)
    (condition-b))

(filter      even? (range  
 1    10))

(clojure.core/filter even?
                   (range 1 
		   10))

(filter
    even?
      (range 1 
      10))
```
### Indent Only
The use of `{:style :indent-only}` will clean it up:
```clojure
(or (condition-a)
  (condition-b))

(or (condition-a)
    (condition-b))

(filter even? (range
                1 10))

(clojure.core/filter even?
  (range 1
    10))

(filter
  even?
  (range 1
    10))
```
### Classic zprint
Again, classic zprint (this time with a width of 26) will regularize it
completely, which might be more change than you wanted:
```clojure
(or (condition-a)
    (condition-b))

(or (condition-a)
    (condition-b))

(filter even?
  (range 1 10))

(clojure.core/filter even?
  (range 1 10))

(filter even?
  (range 1 10))
```


## More complex examples
Let's look at some examples drawn from `clojure.core` ...

### As Written
```clojure
(source resultset-seq)
(defn resultset-seq
  "Creates and returns a lazy sequence of structmaps corresponding to
  the rows in the java.sql.ResultSet rs"
  {:added "1.0"}
  [^java.sql.ResultSet rs]
    (let [rsmeta (. rs (getMetaData))
          idxs (range 1 (inc (. rsmeta (getColumnCount))))
          keys (map (comp keyword #(.toLowerCase ^String %))
                    (map (fn [i] (. rsmeta (getColumnLabel i))) idxs))
          check-keys
                (or (apply distinct? keys)
                    (throw (Exception. "ResultSet must have unique column labels")))
          row-struct (apply create-struct keys)
          row-values (fn [] (map (fn [^Integer i] (. rs (getObject i))) idxs))
          rows (fn thisfn []
                 (when (. rs (next))
                   (cons (apply struct row-struct (row-values)) (lazy-seq (thisfn)))))]
      (rows)))
```
### Indent Only
One difference is that `{:style :indent-only}` doesn't know anything about 
how to format specific functions, since nothing can change lines.  So
it doesn't indent the value for `check-keys`.  Other than that, not a lot of
difference here.  That is true in general --  a well formatted function 
will not show a lot of difference when formatted with `{:style :indent-only}`.
```clojure
(czprint-fn resultset-seq {:style :indent-only})

(defn resultset-seq
  "Creates and returns a lazy sequence of structmaps corresponding to
  the rows in the java.sql.ResultSet rs"
  {:added "1.0"}
  [^java.sql.ResultSet rs]
  (let [rsmeta (. rs (getMetaData))
        idxs (range 1 (inc (. rsmeta (getColumnCount))))
        keys (map (comp keyword #(.toLowerCase ^String %))
                  (map (fn [i] (. rsmeta (getColumnLabel i))) idxs))
        check-keys
        (or (apply distinct? keys)
            (throw (Exception. "ResultSet must have unique column labels")))
        row-struct (apply create-struct keys)
        row-values (fn [] (map (fn [^Integer i] (. rs (getObject i))) idxs))
        rows (fn thisfn []
               (when (. rs (next))
                 (cons (apply struct row-struct (row-values)) (lazy-seq (thisfn)))))]
    (rows)))
```
### As Written -- formatting obscures the code
Here is an intentionally confusing format for the same `clojure.core` function.
It is semantically identical, but the pairs in the `let` are not paired up
correctly, and the body of the `let` is tucked up with the pairs.
```clojure
(defn resultset-seq
  "Creates and returns a lazy sequence of structmaps corresponding to
  the rows in the java.sql.ResultSet rs"
  {:added "1.0"}
  [^java.sql.ResultSet rs]
  (let [rsmeta (. rs (getMetaData)) idxs 
        (range 1 (inc (. rsmeta (getColumnCount))))
        keys (map (comp keyword #(.toLowerCase ^String %))
               (map (fn [i] (. rsmeta (getColumnLabel i))) idxs)) check-keys 
               (or (apply distinct? keys)
                       (throw (Exception.
                                "ResultSet must have unique column labels")))
        row-struct (apply create-struct keys) row-values 
        (fn [] (map (fn [^Integer i] (. rs (getObject i))) idxs))
        rows (fn thisfn []
               (when (. rs (next))
                 (cons (apply struct row-struct (row-values))
                       (lazy-seq (thisfn)))))] (rows)))
```
### Indent Only -- not a lot of help
This is what `{:style :indent-only}` will do for the above source.  It will
not clarify it in any way, and leaves the actual meaning pretty obscure.
```clojure
(defn resultset-seq
  "Creates and returns a lazy sequence of structmaps corresponding to
  the rows in the java.sql.ResultSet rs"
  {:added "1.0"}
  [^java.sql.ResultSet rs]
  (let [rsmeta (. rs (getMetaData)) idxs
        (range 1 (inc (. rsmeta (getColumnCount))))
        keys (map (comp keyword #(.toLowerCase ^String %))
               (map (fn [i] (. rsmeta (getColumnLabel i))) idxs)) check-keys
        (or (apply distinct? keys)
          (throw (Exception.
                   "ResultSet must have unique column labels")))
        row-struct (apply create-struct keys) row-values
        (fn [] (map (fn [^Integer i] (. rs (getObject i))) idxs))
        rows (fn thisfn []
               (when (. rs (next))
                 (cons (apply struct row-struct (row-values))
                       (lazy-seq (thisfn)))))] (rows)))
```
### Classic zprint -- cleans it up
Classic zprint will figure out the pairs in the `let` and put them back
in the right place, and bring the body of the `let` into view.
```clojure
(defn resultset-seq
  "Creates and returns a lazy sequence of structmaps corresponding to
  the rows in the java.sql.ResultSet rs"
  {:added "1.0"}
  [^java.sql.ResultSet rs]
  (let [rsmeta (. rs (getMetaData))
        idxs (range 1 (inc (. rsmeta (getColumnCount))))
        keys (map (comp keyword #(.toLowerCase ^String %))
               (map (fn [i] (. rsmeta (getColumnLabel i))) idxs))
        check-keys (or (apply distinct? keys)
                       (throw (Exception.
                                "ResultSet must have unique column labels")))
        row-struct (apply create-struct keys)
        row-values (fn [] (map (fn [^Integer i] (. rs (getObject i))) idxs))
        rows (fn thisfn []
               (when (. rs (next))
                 (cons (apply struct row-struct (row-values))
                       (lazy-seq (thisfn)))))]
    (rows)))
```
### As Written -- from clojure.core
```clojure
(source derive)

(defn derive
  "Establishes a parent/child relationship between parent and
  tag. Parent must be a namespace-qualified symbol or keyword and
  child can be either a namespace-qualified symbol or keyword or a
  class. h must be a hierarchy obtained from make-hierarchy, if not
  supplied defaults to, and modifies, the global hierarchy."
  {:added "1.0"}
  ([tag parent]
   (assert (namespace parent))
   (assert (or (class? tag) (and (instance? clojure.lang.Named tag) (namespace tag))))

   (alter-var-root #'global-hierarchy derive tag parent) nil)
  ([h tag parent]
   (assert (not= tag parent))
   (assert (or (class? tag) (instance? clojure.lang.Named tag)))
   (assert (instance? clojure.lang.Named parent))

   (let [tp (:parents h)
         td (:descendants h)
         ta (:ancestors h)
         tf (fn [m source sources target targets]
              (reduce1 (fn [ret k]
                        (assoc ret k
                               (reduce1 conj (get targets k #{}) (cons target (targets target)))))
                      m (cons source (sources source))))]
     (or
      (when-not (contains? (tp tag) parent)
        (when (contains? (ta tag) parent)
          (throw (Exception. (print-str tag "already has" parent "as ancestor"))))
        (when (contains? (ta parent) tag)
          (throw (Exception. (print-str "Cyclic derivation:" parent "has" tag "as ancestor"))))
        {:parents (assoc (:parents h) tag (conj (get tp tag #{}) parent))
         :ancestors (tf (:ancestors h) tag td parent ta)
         :descendants (tf (:descendants h) parent ta tag td)})
      h))))
```

### Indent Only
Not a lot of difference from the way it was originally written.
The longer lines in the original are still here with indent-only,
since they can't be broken up.  The blank lines were retained (as
they would have been with respect-blank-lines as well).
```clojure
(czprint-fn derive {:style :indent-only})

(defn derive
  "Establishes a parent/child relationship between parent and
  tag. Parent must be a namespace-qualified symbol or keyword and
  child can be either a namespace-qualified symbol or keyword or a
  class. h must be a hierarchy obtained from make-hierarchy, if not
  supplied defaults to, and modifies, the global hierarchy."
  {:added "1.0"}
  ([tag parent]
   (assert (namespace parent))
   (assert (or (class? tag) (and (instance? clojure.lang.Named tag) (namespace tag))))
   
   (alter-var-root #'global-hierarchy derive tag parent) nil)
  ([h tag parent]
   (assert (not= tag parent))
   (assert (or (class? tag) (instance? clojure.lang.Named tag)))
   (assert (instance? clojure.lang.Named parent))
   
   (let [tp (:parents h)
         td (:descendants h)
         ta (:ancestors h)
         tf (fn [m source sources target targets]
              (reduce1 (fn [ret k]
                         (assoc ret k
                           (reduce1 conj (get targets k #{}) (cons target (targets target)))))
                m (cons source (sources source))))]
     (or
       (when-not (contains? (tp tag) parent)
         (when (contains? (ta tag) parent)
           (throw (Exception. (print-str tag "already has" parent "as ancestor"))))
         (when (contains? (ta parent) tag)
           (throw (Exception. (print-str "Cyclic derivation:" parent "has" tag "as ancestor"))))
         {:parents (assoc (:parents h) tag (conj (get tp tag #{}) parent))
          :ancestors (tf (:ancestors h) tag td parent ta)
          :descendants (tf (:descendants h) parent ta tag td)})
       h))))
```
## Want indent-only which will fit into a particular width?
If you like the behavior of `{:style :indent-only}`, but you would also like
zprint to add newlines when a line goes over a particular width, you might
try `{style :respect-nl}`, which is "respect new lines".  This style will
never remove a newline, but will add newlines as necessary in order to try 
to make the format fit within a particular width.  As always, it may not be
possible to make it fit, but usually it will be able to come at least close.

