(ns zprint.style-sample
  (:require
    [a.a.a :as a])
  (:require
    [b.b.b :refer [b]])
  (:require
    [c.c.c]
    [d.d.d]
    [e.e.e])
  ;; comment
  (:gen-class))


;; comment

(def a 10)


(defn b [] 10)


(defn c ([] 10) ([a] 20))


(let [[a b] [1 2]] (println [a b]))



{:aaaa :bbbb,
 :cccc ({:aaaa :bbbb, :cccccccc :dddddddd, :eeee :ffff})}
;; The comma at the end of the :dddd should be in
;; column 29.  So this should fit with a width of 29, but not 28.
;;
;; Check both sexpression printing and parsing into a zipper printing.
