# Change how blank lines are handled at the top level

By default, all blank lines at the top level of a file are left alone.
Thus, if you have several blank lines between two top level expressions
and none between some other top level expressions, no changes are made.

For example, let's look at this file
```clojure
(ns example.core)

; Comment about blanks
(defn blanks
  "Produce a blank string of desired size."
  [n]
  (apply str (repeat n " ")))
                                       
(defn dots
  "Produce a dot string of desired size."
  [n]
  (apply str (repeat n ".")))
(defn remove-one
  "Remove a single thing from a sequence."
  [s index]
  (concat (take index s) (drop (inc index) s)))
(defn force-vector
  "Ensure that whatever we have is a vector."
  [coll]
  (if (vector? coll) coll (into [] coll)))
```
Normal zprint formatting woud have it look like this (no change):
```clojure
(ns example.core)

; Comment about blanks
(defn blanks
  "Produce a blank string of desired size."
  [n]
  (apply str (repeat n " ")))
                                       
(defn dots
  "Produce a dot string of desired size."
  [n]
  (apply str (repeat n ".")))
(defn remove-one
  "Remove a single thing from a sequence."
  [s index]
  (concat (take index s) (drop (inc index) s)))
(defn force-vector
  "Ensure that whatever we have is a vector."
  [coll]
  (if (vector? coll) coll (into [] coll)))
```
You could change it to have no blank lines between top level expressions
with `{:parse {:interpose "\n"}}`

```clojure
(ns example.core)
; Comment about blanks
(defn blanks
  "Produce a blank string of desired size."
  [n]
  (apply str (repeat n " ")))
(defn dots
  "Produce a dot string of desired size."
  [n]
  (apply str (repeat n ".")))
(defn remove-one
  "Remove a single thing from a sequence."
  [s index]
  (concat (take index s) (drop (inc index) s)))
(defn force-vector
  "Ensure that whatever we have is a vector."
  [coll]
  (if (vector? coll) coll (into [] coll)))
```
You could change it to have two blank lines between every top level
expression with `{:parse {:interpose "\n\n"}}`
```clojure
(ns example.core)

; Comment about blanks
(defn blanks
  "Produce a blank string of desired size."
  [n]
  (apply str (repeat n " ")))

(defn dots
  "Produce a dot string of desired size."
  [n]
  (apply str (repeat n ".")))

(defn remove-one
  "Remove a single thing from a sequence."
  [s index]
  (concat (take index s) (drop (inc index) s)))

(defn force-vector
  "Ensure that whatever we have is a vector."
  [coll]
  (if (vector? coll) coll (into [] coll)))
```
Notice that the comment wasn't separated from `(defn blanks` in this
case.  A comment that is not separated from the next line by a blank
line on input is never separated from the next line by 
`{:parse {:interpose ...}}`.

Note that none of this processing as any effect on the 
`{:input {:range {:start ... :end ...}}}` processing for formatting
ranges of a file.

Note also that you can put anything you want in `:interpose`, but if it doesn't
end with a newline, the formatting won't be correct.
