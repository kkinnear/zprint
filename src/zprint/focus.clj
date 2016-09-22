(ns zprint.focus
    (:require [clojure.string :as s] [zprint.ansi :refer [color-str]]))

;;
;; # Find focus in a style-vec
;;

(defn type-ssv
  "What is this element in a str-style-vec?"
  [ssv-element]
  (nth ssv-element 2 :whitespace))

(defn skip-whitespace-ssv
  "Skip any whitespace in this ssv starting at n."
  [ssv n]
  (loop [index n]
    (if (>= index (count ssv))
      (dec index)
      (if (not= (type-ssv (nth ssv index)) :whitespace)
        index
        (recur (inc index))))))

(defn down-ssv
  "Given a str-style-vec, move into a collection at element n."
  [ssv n]
  (let [non-ws-n (skip-whitespace-ssv ssv n)]
    (when (= (type-ssv (nth ssv non-ws-n)) :left) (inc non-ws-n))))

(defn next-ssv
  "Given a str-style-vec, move to the next element beyond this
  one.  This will skip over entire collections, if there are any.
  It will also ignore :whitespace elements."
  [ssv n]
  #_(println "next-ssv: n:" n)
  (loop [index n
         skip-to-right? nil
         next-nonws? nil]
    (if (>= index (count ssv))
      nil
      (let [index-type (type-ssv (nth ssv index))
            new-next-nonws? (and (or (not skip-to-right?) (= index-type :right))
                                 (not= index-type :left)
                                 (not= index-type :whitespace))]
        #_(println "next-ssv: index:" index
                   "skip-to-right?" skip-to-right?
                   "next-nonws?" next-nonws?
                   "index-type:" index-type
                   "new-next-nonws?" new-next-nonws?)
        (if next-nonws?
          index
          (recur (if (and (= index-type :left) skip-to-right?)
                   (next-ssv ssv index)
                   (inc index))
                 (and (or (= index-type :left) skip-to-right?)
                      (not= index-type :right))
                 new-next-nonws?))))))

(defn right-ssv
  "Given a str-style-vec, move right n elements."
  [nr ssv n]
  (loop [index n
         moves nr]
    (if (zero? moves) index (recur (next-ssv ssv index) (dec moves)))))

(defn path-ssv
  "Given a non-whitespace path from a zipper, find that same
  collection or element in a str-style-vec."
  [nwpath ssv]
  (loop [idx 0
         nwp nwpath]
    (if (empty? nwp)
      idx
      (recur (right-ssv (first nwp) ssv (down-ssv ssv idx)) (next nwp)))))

(defn range-ssv
  "Use a non-whitespace path from a zipper, and find that
  same collection or element in a str-style-vec, and return
  a vector of the start and end of that collection or element.
  Depends on next-ssv returning one past the end of its input."
  [nwpath ssv]
  (let [start (path-ssv nwpath ssv)
        #_(println "range-ssv: start:" start "nwpath:" nwpath)
        start (skip-whitespace-ssv ssv start)
        ssv-next (next-ssv ssv start)
        end (if (and ssv-next (not= start 0)) (dec ssv-next) (dec (count ssv)))]
    #_(println "range-ssv:" [start end])
    [start end]))