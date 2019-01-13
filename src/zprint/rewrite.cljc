(ns zprint.rewrite
  (:require
    clojure.string
    [zprint.zutil :as zu :refer [string tag zreplace sexpr edn*]]
    [rewrite-clj.parser :as p]
    [rewrite-clj.node :as n]
    [rewrite-clj.zip :as z]
    #?@(:cljs [[rewrite-clj.zip.base :as zb] [rewrite-clj.zip.whitespace :as zw]
               [rewrite-clj.zip.move :as zm] [rewrite-clj.zip.removez :as zr]
               [rewrite-clj.zip.editz :as ze] clojure.zip])))

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

(defn prewalk
  [zloc p? f]
  (z/replace zloc
             (z/root (prewalk-subtree p?
                                      f
                                      ; Make a zipper whose root is zloc
                                      (some-> zloc
                                              z/node
                                              edn*)))))


;;
;; # Routines to modify zippers inside of zprint
;;

(defn get-sortable
  "Given a zloc, get something out of it that is sortable."
  [zloc]
  (loop [nloc zloc]
    (if (= (z/tag nloc) :token)
      (str (z/string nloc)
           (let [next-element (z/right nloc)]
             (if (= (z/tag next-element) :token) (z/string next-element) "")))
      (recur (z/down nloc)))))

(defn sort-val
  "Sort the everything in the vector to the right of zloc."
  [zloc]
  (let [dep-val zloc
        dep-seq (loop [nloc zloc
                       out []]
                  (if nloc (recur (z/right nloc) (conj out nloc)) out))
        #_(println "sort-val: count:" (count dep-seq))
        dep-count (count dep-seq)
        sorted-seq (sort-by get-sortable dep-seq)
        #_(println "sort-val: dep-seq:" (mapv get-sortable dep-seq))
        #_(println "sort-val: sorted-seq:" (mapv get-sortable sorted-seq))]
    (loop [nloc zloc
           new-loc sorted-seq
           last-loc nil]
      #_(println "sort-val: loop: before:" (z/string nloc))
      #_(when nloc (println "sort-val: loop: n/tag:" (n/tag (z/node nloc))))
      #_(when new-loc
          (println "sort-val: loop: after:" (n/string (z/node (first new-loc))))
          (println "sort-val: loop: n/tag:" (n/tag (z/node (first new-loc)))))
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
          (recur (z/right replaced-loc) (next new-loc) replaced-loc))
        (z/up last-loc)))))

(defn sort-down
  "Do a down and a sort-val"
  [zloc]
  (sort-val (z/down (z/right zloc))))

(defn sort-dependencies
  "Reorder the dependencies in a project.clj file."
  [caller options zloc]
  (let [new-dep (prewalk zloc
                         #(and (= (z/tag %1) :token)
                               (= (z/sexpr %1) :dependencies))
                         sort-down)]
    new-dep))
