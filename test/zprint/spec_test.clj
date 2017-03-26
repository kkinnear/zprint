(ns zprint.spec-test
  (:require [expectations :refer :all]
            [zprint.core :refer :all]
            [zprint.zprint :refer :all]
            [zprint.config :refer :all]
            [zprint.spec :refer :all]
            [zprint.finish :refer :all]
            [clojure.repl :refer :all]
            [clojure.spec :as s]
            [clojure.string :as str]
            [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only [edn*]]))

;; Keep some of the test from wrapping so they still work
;!zprint {:comment {:wrap? false} :fn-map {"more-of" :arg1}}

;;
;; # Random tests, see more systematic tests below
;;

(expect
  "The value of the key-sequence [:fn-map \"a\"] -> \"b\" was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:fn-map {"a" "b"}})))

(expect
  "The value of the key-sequence [:fn-map \"a\"] -> :a was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:fn-map {"a" :a}})))

(expect nil
        (explain-more (s/explain-data :zprint.spec/options
                                      {:fn-map {"a" :arg1}})))

(expect
  "In the key-sequence [:map [:a]] the key [:a] was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options
                                {:map {:hang? true, [:a] :b}})))

(expect "In the key-sequence [:map :x] the key :x was not recognized as valid!"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:map {:hang? true, :x :y}})))

(expect "The value of the key-sequence [:map :hang?] -> 0 was not a boolean?"
        (explain-more (s/explain-data :zprint.spec/options {:map {:hang? 0}})))

;;
;; # Wrong first level key
;;

(expect
  "In the key-sequence [:widthx] the key :widthx was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:widthx 80})))
(expect "In the key-sequence [\"a\"] the key \"a\" was not recognized as valid!"
        (explain-more (s/explain-data :zprint.spec/options {"a" 80})))
(expect "In the key-sequence [[:a]] the key [:a] was not recognized as valid!"
        (explain-more (s/explain-data :zprint.spec/options {[:a] 80})))
(expect
  "In the key-sequence [{:width 80}] the key {:width 80} was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {{:width 80} 80})))
(expect "In the key-sequence [a] the key a was not recognized as valid!"
        (explain-more (s/explain-data :zprint.spec/options {'a 80})))

;;
;; # Wrong first level value
;;

;;
;; ## Should be an integer
;;

(expect "The value of the key-sequence [:width] -> :a was not a number?"
        (explain-more (s/explain-data :zprint.spec/options {:width :a})))
(expect "The value of the key-sequence [:width] -> \"a\" was not a number?"
        (explain-more (s/explain-data :zprint.spec/options {:width "a"})))
(expect "The value of the key-sequence [:width] -> [:a] was not a number?"
        (explain-more (s/explain-data :zprint.spec/options {:width [:a]})))
(expect "The value of the key-sequence [:width] -> {:a :b} was not a number?"
        (explain-more (s/explain-data :zprint.spec/options {:width {:a :b}})))
(expect "The value of the key-sequence [:width] -> a was not a number?"
        (explain-more (s/explain-data :zprint.spec/options {:width 'a})))
(expect "The value of the key-sequence [:width] -> #{:a} was not a number?"
        (explain-more (s/explain-data :zprint.spec/options {:width #{:a}})))
;;
;; ## Should be a map
;;

(expect "The value of the key-sequence [:map] -> :a was not a map?"
        (explain-more (s/explain-data :zprint.spec/options {:map :a})))
(expect "The value of the key-sequence [:map] -> 5 was not a map?"
        (explain-more (s/explain-data :zprint.spec/options {:map 5})))
(expect "The value of the key-sequence [:map] -> \"a\" was not a map?"
        (explain-more (s/explain-data :zprint.spec/options {:map "a"})))
(expect "The value of the key-sequence [:map] -> [:a] was not a map?"
        (explain-more (s/explain-data :zprint.spec/options {:map [:a]})))
(expect "The value of the key-sequence [:map] -> a was not a map?"
        (explain-more (s/explain-data :zprint.spec/options {:map 'a})))
(expect "The value of the key-sequence [:map] -> #{:a} was not a map?"
        (explain-more (s/explain-data :zprint.spec/options {:map #{:a}})))

;;
;; ## Should be a boolean
;;

(expect "The value of the key-sequence [:parse-string?] -> 0 was not a boolean?"
        (explain-more (s/explain-data :zprint.spec/options {:parse-string? 0})))
(expect
  "The value of the key-sequence [:parse-string?] -> :a was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:parse-string? :a})))
(expect
  "The value of the key-sequence [:parse-string?] -> \"a\" was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:parse-string? "a"})))
(expect
  "The value of the key-sequence [:parse-string?] -> [:a] was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:parse-string? [:a]})))
(expect
  "The value of the key-sequence [:parse-string?] -> {:a :b} was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:parse-string? {:a :b}})))
(expect "The value of the key-sequence [:parse-string?] -> a was not a boolean?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:parse-string? 'a})))
(expect
  "The value of the key-sequence [:parse-string?] -> #{:a} was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:parse-string? #{:a}})))

;;
;; ## Should be a set
;;

(expect "The value of the key-sequence [:fn-force-nl] -> :a was not a set?"
        (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl :a})))
(expect "The value of the key-sequence [:fn-force-nl] -> 0 was not a set?"
        (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl 0})))
(expect "The value of the key-sequence [:fn-force-nl] -> \"a\" was not a set?"
        (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl "a"})))
(expect "The value of the key-sequence [:fn-force-nl] -> [:a] was not a set?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:fn-force-nl [:a]})))
(expect "The value of the key-sequence [:fn-force-nl] -> {:a :b} was not a set?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:fn-force-nl {:a :b}})))
(expect "The value of the key-sequence [:fn-force-nl] -> a was not a set?"
        (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl 'a})))

;;
;; ## Should be a set of fn-types
;;

(expect
  "The value of the key-sequence [:fn-force-nl] -> :xxx was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl #{:xxx}})))
(expect
  "The value of the key-sequence [:fn-force-nl] -> :a was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl #{:a}})))
(expect
  "The value of the key-sequence [:fn-force-nl] -> \"a\" was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl #{"a"}})))
(expect
  "The value of the key-sequence [:fn-force-nl] -> [:a] was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl #{[:a]}})))

;;
;; # Wrong second level key
;;

;;
;; ## Should be :hang?
;;

(expect
  "In the key-sequence [:map :hang?x] the key :hang?x was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:map {:hang?x true}})))
(expect
  "In the key-sequence [:map \"a\"] the key \"a\" was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:map {"a" true}})))
(expect "In the key-sequence [:map 0] the key 0 was not recognized as valid!"
        (explain-more (s/explain-data :zprint.spec/options {:map {0 true}})))
(expect "In the key-sequence [:map a] the key a was not recognized as valid!"
        (explain-more (s/explain-data :zprint.spec/options {:map {'a true}})))
(expect
  "In the key-sequence [:map [:a]] the key [:a] was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:map {[:a] true}})))
(expect
  "In the key-sequence [:map {:a :b}] the key {:a :b} was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:map {{:a :b} true}})))
(expect
  "In the key-sequence [:map #{:a}] the key #{:a} was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options {:map {#{:a} true}})))

;;
;; # Wrong second level value
;;

;;
;; ## Should be an integer
;;

(expect
  "The value of the key-sequence [:map :indent] -> \"a\" was not a number?"
  (explain-more (s/explain-data :zprint.spec/options {:map {:indent "a"}})))
(expect "The value of the key-sequence [:map :indent] -> :a was not a number?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:map {:indent :a}})))
(expect "The value of the key-sequence [:map :indent] -> [:a] was not a number?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:map {:indent [:a]}})))
(expect
  "The value of the key-sequence [:map :indent] -> {:a :b} was not a number?"
  (explain-more (s/explain-data :zprint.spec/options {:map {:indent {:a :b}}})))
(expect
  "The value of the key-sequence [:map :indent] -> #{:a} was not a number?"
  (explain-more (s/explain-data :zprint.spec/options {:map {:indent #{:a}}})))
(expect "The value of the key-sequence [:map :indent] -> a was not a number?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:map {:indent 'a}})))

;;
;; ## Should be a boolen
;;

(expect
  "The value of the key-sequence [:list :hang?] -> \"a\" was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:list {:hang? "a"}})))
(expect "The value of the key-sequence [:list :hang?] -> :a was not a boolean?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:list {:hang? :a}})))
(expect
  "The value of the key-sequence [:list :hang?] -> [:a] was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:list {:hang? [:a]}})))
(expect
  "The value of the key-sequence [:list :hang?] -> {:a :b} was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:list {:hang? {:a :b}}})))
(expect
  "The value of the key-sequence [:list :hang?] -> #{:a} was not a boolean?"
  (explain-more (s/explain-data :zprint.spec/options {:list {:hang? #{:a}}})))
(expect "The value of the key-sequence [:list :hang?] -> a was not a boolean?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:list {:hang? 'a}})))

;;
;; ## Should be a map
;;


(expect
  "The value of the key-sequence [:uneval :color-map] -> \"a\" was not a map?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:uneval {:color-map "a"}})))

(expect
  "The value of the key-sequence [:uneval :color-map] -> :a was not a map?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:uneval {:color-map :a}})))
(expect
  "The value of the key-sequence [:uneval :color-map] -> [:a] was not a map?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:uneval {:color-map [:a]}})))
(expect
  "The value of the key-sequence [:uneval :color-map] -> #{:a} was not a map?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:uneval {:color-map #{:a}}})))

(expect "The value of the key-sequence [:uneval :color-map] -> a was not a map?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:uneval {:color-map 'a}})))

;; ## Should be a color

(expect
  "In the key-sequence [:uneval :color-map :a] the key :a was not recognized as valid!"
  (explain-more (s/explain-data :zprint.spec/options
                                {:uneval {:color-map {:a :b}}})))

;;
;; ## Should be a vector
;;

(expect
  "The value of the key-sequence [:map :key-order] -> :a was not a sequential?"
  (explain-more (s/explain-data :zprint.spec/options {:map {:key-order :a}})))
(expect
  "The value of the key-sequence [:map :key-order] -> \"a\" was not a sequential?"
  (explain-more (s/explain-data :zprint.spec/options {:map {:key-order "a"}})))
(expect
  "The value of the key-sequence [:map :key-order] -> {:a :b} was not a sequential?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:map {:key-order {:a :b}}})))
(expect
  "The value of the key-sequence [:map :key-order] -> #{:a} was not a sequential?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:map {:key-order #{:a}}})))
(expect
  "The value of the key-sequence [:map :key-order] -> a was not a sequential?"
  (explain-more (s/explain-data :zprint.spec/options {:map {:key-order 'a}})))
(expect
  "The value of the key-sequence [:map :key-order] -> 5 was not a sequential?"
  (explain-more (s/explain-data :zprint.spec/options {:map {:key-order 5}})))

;;
;; ## Should be a set
;;

(expect
  "The value of the key-sequence [:extend :modifiers] -> :a was not a set?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers :a}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> \"a\" was not a set?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers "a"}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a set?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers {:a :b}}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a set?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers {:a :b}}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a set?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers {:a :b}}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> [:a] was not a set?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers [:a]}})))
(expect "The value of the key-sequence [:extend :modifiers] -> a was not a set?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:extend {:modifiers 'a}})))
(expect "The value of the key-sequence [:extend :modifiers] -> 5 was not a set?"
        (explain-more (s/explain-data :zprint.spec/options
                                      {:extend {:modifiers 5}})))

;;
;; ## Should be a set of strings
;;

(expect
  "The value of the key-sequence [:extend :modifiers] -> :a was not a string?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers #{:a}}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a string?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers #{{:a :b}}}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> [:a] was not a string?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers #{[:a]}}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> a was not a string?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers #{'a}}})))
(expect
  "The value of the key-sequence [:extend :modifiers] -> 5 was not a string?"
  (explain-more (s/explain-data :zprint.spec/options
                                {:extend {:modifiers #{5}}})))
