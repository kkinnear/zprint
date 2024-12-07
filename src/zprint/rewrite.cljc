;!zprint {:style [{:style-call :sort-require :regex-vec [#"^clojure" #"^zprint" #"^rewrite" #"^taoensso"]} :require-justify]}
(ns zprint.rewrite
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer
               [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
    clojure.string
    [zprint.util        :refer [local-abs]]
    [zprint.zutil       :as    zu
                        :refer [zreplace zcount-nc]]
    [rewrite-clj.node   :as n]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip    :as    z
                        :refer [of-node* sexpr string tag]]))

;;
;; No prewalk in rewrite-cljs, so we'll do it ourselves here
;; for both environments, so that we can lean on the clj testing
;; for cljs.
;;

(defn- prewalk-subtree
  [p? f zloc]
  (loop [loc zloc]
    (if (z/end? loc)
      loc
      (if (p? loc)
        (if-let [n (f loc)]
          (recur (z/next n))
          (recur (z/next loc)))
        (recur (z/next loc))))))

(defn ^:no-doc prewalk
  [zloc p? f]
  (z/replace zloc
             (z/root (prewalk-subtree p?
                                      f
                                      ; Make a zipper whose root is zloc
                                      (some-> zloc
                                              z/node
                                              of-node*)))))

;;
;; # Routines to modify zippers inside of zprint
;;

(defn ^:no-doc get-sortable
  "Given a zloc, get something out of it that is sortable."
  [zloc]
  (loop [nloc zloc]
    (if (= (z/tag nloc) :token)
      (str (z/string nloc)
           (let [next-element (z/right nloc)]
             (if (= (z/tag next-element) :token) (z/string next-element) "")))
      ; Dig down to look for something we can sort with
      (recur (z/down nloc)))))

(defn ^:no-doc require-get-sortable
  "Given a zloc of a :require clause, get something out of it that is sortable."
  [zloc]
  (loop [nloc zloc]
    ; If we don't have anything, don't loop forever!
    (if-not nloc
      ""
      (if (= (z/tag nloc) :token)
        ; This will put reader-conditionals where they "go" for the thing
        ; inside.
        (if (= (str (z/sexpr nloc)) "?@")
          (recur (z/right (z/down (z/right nloc))))
          (str (z/string nloc)))
        ; Dig down to look for something we can sort with
        (recur (z/down nloc))))))

(defn ^:no-doc req-skip?
  "Skip things that we don't want to re-order."
  [zloc]
  (when zloc (= (z/tag zloc) :reader-macro)))

(defn ^:no-doc no-skip? "Don't skip something." [zloc] nil)

(defn ^:no-doc right-fn
  "Get the next thing to the right that might be sortable."
  [skip-fn? zloc]
  (loop [nloc (z/right zloc)]
    (when nloc
      ; Skip whatever we should, particularly reader-conditionals
      ; if skip-fn? is req-skip?.
      (if (skip-fn? nloc) (recur (z/right nloc)) nloc))))

(defn ^:no-doc contains-comment?
  "Check a zloc (of a collection) to see if it contains any comments."
  [zloc]
  (loop [nloc zloc]
    (when nloc (if (= (z/tag nloc) :comment) true (recur (z/right* nloc))))))

(defn ^:no-doc sort-val
  "Sort everything in the vector to the right of zloc."
  [get-sortable-fn sort-fn skip-fn? zloc]
  #_(println "sort-val")
  (if (contains-comment? zloc)
    zloc
    (let [dep-val zloc
          ; Put all of the individual zlocs into a vector.
          dep-seq (loop [nloc zloc
                         out []]
                    (if nloc
                      (if (skip-fn? nloc)
                        (recur (z/right nloc) out)
                        (recur (z/right nloc) (conj out nloc)))
                      out))
          #_ (println "sort-val: count:" (count dep-seq))
          dep-count (count dep-seq)
          #_ (println "sort-val: dep-seq:" (mapv z/string dep-seq))
          sorted-seq (sort-fn get-sortable-fn dep-seq)
          #_ (println "sort-val: dep-seq:" (mapv get-sortable-fn dep-seq))
          #_ (println "sort-val: sorted-seq:" (mapv get-sortable-fn sorted-seq))]
      ; Loop through all of the elements of zloc, replacing them one by one
      ; with the elements of sorted-seq.  Since there should be the same
      ; amount of elements in each, this should work.
      (loop [nloc zloc
             new-loc sorted-seq
             last-loc nil]
        #_(println "sort-val: loop: before:" (z/string nloc))
        #_(when nloc (println "sort-val: loop: n/tag:" (n/tag (z/node nloc))))
        #_(when new-loc
            (println "sort-val: loop: after:"
                     (n/string (z/node (first new-loc))))
            (println "sort-val: loop: n/tag:" (n/tag (z/node (first new-loc)))))
        (if nloc
          (if (skip-fn? nloc)
            (recur (z/right nloc) new-loc last-loc)
            (if new-loc
              (let [new-z (first new-loc)
                    ; rewrite-cljs doesn't handle z/node for :uneval
                    ; so we will get an :uneval node a different way
                    new-node (if (= (z/tag new-z) :uneval)
                               (p/parse-string (z/string new-z))
                               (z/node new-z))
                    ; use clojure.zip for cljs, since the z/replace has
                    ; a built-in coerce, which doesn't work for an :uneval
                    replaced-loc #?(:clj (z/replace nloc new-node)
                                    :cljs (clojure.zip/replace nloc new-node))]
                #_(println "sort-val: loop: replaced-loc n/tag:"
                           (n/tag (z/node replaced-loc)))
                ; Why isn't this (z/right nloc)?  Because after modifying a
                ; zipper, the thing you have is the only thing that
                ; contains the modification, so you have to work with that
                ; else you lose what you did.
                (recur (right-fn skip-fn? replaced-loc)
                       (next new-loc)
                       replaced-loc))
              last-loc))
          last-loc)))))

(defn ^:no-doc sort-down
  "Do a down and a sort-val"
  [get-sortable-fn zloc]
  (z/up (sort-val get-sortable-fn sort-by no-skip? (z/down (z/right zloc)))))

(defn sort-dependencies
  "Reorder the dependencies in a project.clj file."
  [caller options zloc]
  (dbg-s options
         #{:sort-dependencies}
         "sort-dependencies: zloc:"
         (z/string zloc))
  ; Note well: we are not doing prewalk on the actual structure shown
  ; in a project.clj file.  Rather we are doing a prewalk on the zipper
  ; of the parsed content of the project.clj file, which is of course
  ; related but certainly not the same.  In particular, prewalk travels
  ; to touch all of the nodes, but once we find the node that is
  ; :dependencies, we can access all of the stuff associated with that
  ; node because the zipper ties it all together.
  (let [new-dep (prewalk zloc
                         #(and (= (z/tag %1) :token)
                               (= (z/sexpr %1) :dependencies))
                         (partial sort-down get-sortable))]
    new-dep))

(declare sort-w-regex)

(defn ^:no-doc sort-within
  "Sort to the right of zloc."
  [get-sortable-fn sort-options zloc]
  (z/leftmost (sort-val get-sortable-fn
                        (partial sort-w-regex sort-options)
                        req-skip?
                        (z/right zloc))))

(defn sort-requires
  "Reorder the requires in an ns macro."
  [caller sort-options options zloc]
  (dbg-s options #{:sort-requires} "sort-requires: zloc:" (z/string zloc))
  ; Note well: we are not doing prewalk on the actual structure shown
  ; in an ns macro.  Rather we are doing a prewalk on the zipper
  ; of the parsed content of the ns macro, which is of course
  ; related but certainly not the same.  In particular, once we find
  ; the node :require, we can access all of the stuff to the right of it
  ; because the zipper ties it all together.  Thus, there is supposed
  ; to be only one call to sort-within, because there is only one :require
  ; in the ns macro.  All of the rest of the zipper nodes should be
  ; untouched.
  (let [new-dep (prewalk
                  zloc
                  #(and (= (z/tag %1) :token) (= (z/sexpr %1) :require))
                  (partial sort-within require-get-sortable sort-options))]
    new-dep))

(defn ^:no-doc try-one-regex
  "Given a regex, see if it is a match with this string.  If it is, return
  i as the end of the reduce, else increment i."
  [s i regex]
  (if (re-find regex s) (reduced i) (inc i)))

(defn ^:no-doc try-regex-on-zloc
  "Given a vector of regexes and a zloc, return the index of the regex or
  the count of regexes if none of them are a hit."
  [regex-vec get-sortable-fn zloc]
  (reduce (partial try-one-regex (get-sortable-fn zloc)) 0 regex-vec))

(defn ^:no-doc find-and-remove-divider
  "Given a keyword divider, find where it is, remove it, and return the
  count and the vector w/out the divider. The count is the position of
  the divider in the vector.  If it is negative, there was no divider
  in the vector."
  [divider-value regex-vec]
  (let [scan-fn (fn [[i out] element]
                  (if (= element divider-value)
                    ; Set the number positive, and skip this element.
                    [(local-abs i) out]
                    (if (pos? i)
                      ; We have already seen the divider-value
                      [i (conj out element)]
                      ; Still looking for the divider-value
                      [(dec i) (conj out element)])))
        ; Note position starts as one-based, with -1 (i.e. neg?),
        ; otherwise 0 doesn't work
        [position new-regex-vec] (reduce scan-fn [-1 []] regex-vec)]
    ; Adjust position back to zero-based
    [(if (pos? position) (dec position) (inc position)) new-regex-vec]))

(defn ^:no-doc assemble-by-numeric-key
  "Given a map where the keys are integers and the values are sequences, 
  assemble all of the sequences into a single sequence based on the
  order of the keys.  If the divider-position is negative, there was no
  divider so it can be ignored.  Otherwise, place the highest key in the
  divider-position, before the key of that number."
  [divider-position group-map]
  (let [group-keys (keys group-map)]
    (if (zero? (count group-keys))
      []
      (let [max-int-key (apply max group-keys)]
        (loop [idx 0
               divider-position divider-position
               max-int-key max-int-key
               out []]
          (if (> idx max-int-key)
            out
            (if (= idx divider-position)
              (recur idx
                     (dec idx) ; make sure we don't traverse this arm again
                     (dec max-int-key)
                     (concat out (get group-map max-int-key)))
              (let [group (get group-map idx)]
                (if group
                  (recur (inc idx)
                         divider-position
                         max-int-key
                         (concat out group))
                  (recur (inc idx) divider-position max-int-key out))))))))))

(defn ^:no-doc sort-group
  "Given a map with numeric keys, sort the group specified by the supplied
  number, and put it back in the map.  Return the updated map."
  [get-sortable-fn m i]
  (let [pre-sorted (get m i)
        sorted-seq (sort-by get-sortable-fn pre-sorted)]
    (assoc m i sorted-seq)))

(defn ^:no-doc sort-refer
  "Sort the elements of a :refer vector (if any) in a zloc of a :require 
  list, and return the modified zloc. Make sure that the :refer is
  followed by a vector."
  [zloc]
  (let [refer-zloc (z/find-value (z/down zloc) :refer)
        refer-right (when refer-zloc (z/right refer-zloc))
        refer-tag (when refer-right (z/tag refer-right))]
    #_(println "sort-refer: zloc:" (z/string zloc))
    #_(println "sort-refer: refer-zloc:" (z/string refer-zloc))
    #_(println "sort-refer: refer-right:" (z/string refer-right))
    #_(println "sort-refer: zcount-nc refer-zloc:" (zcount-nc refer-right))
    ; Ensure that if we have a vector that it also has something in it before
    ; we try to sort it.
    (if (and refer-zloc refer-right (= refer-tag :vector) (pos? (zcount-nc refer-right)))
      (z/up
        (z/up
          (sort-val z/string sort-by no-skip? (z/down (z/right refer-zloc)))))
      zloc)))

(defn ^:no-doc sort-w-regex
  "Given a vector of regexes (possibly empty), sort within the
  regexes (in order) first and then sort the rest at the end. Unless
  there is a divider: :|, in which case the things before the divider
  go first, the things after the divider go last, and everything
  that doesn't match a regex goes where the divider is. get-sortable-fn
  will return something that is sortable from every element of
  req-seq, which contains the things to sort.  regex-vec is a vector
  of regexes."
  [sort-options get-sortable-fn req-seq]
  #_(println "sort-w-regex:" (map z/string req-seq))
  ; First, we need to get any :| out of the regex-vec, and remember where
  ; it was.  This separates the first from the last groups.
  (let [regex-vec (:regex-vec sort-options)
        [divider-position clean-regex-vec] (find-and-remove-divider :|
                                                                    regex-vec)
        group (group-by
                (partial try-regex-on-zloc clean-regex-vec get-sortable-fn)
                req-seq)
        ; Next we need to sort each group amongst themselves
        groups-sorted
          (reduce (partial sort-group get-sortable-fn) group (keys group))
        ; Next we need to put the group back into a single vector by order
        ; of the number in the group map.  Put the last group into the
        ; vector in the divider-position if it is positive.
        out (assemble-by-numeric-key divider-position groups-sorted)
        refer-sorted (if (:sort-refer? sort-options) (mapv sort-refer out) out)]
    #_(println "sort-w-regex: refer-sorted:" (mapv z/string refer-sorted))
    refer-sorted))
