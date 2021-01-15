(ns zprint.spec-test
  (:require [expectations.clojure.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            [zprint.spec :refer [explain-more coerce-to-boolean]]
            [zprint.core :refer [set-options!]]
            [#?(:clj clojure.spec.alpha
                :cljs cljs.spec.alpha) :as s]))


;; Keep some of the test from wrapping so they still work
;!zprint {:comment {:wrap? false} :fn-map {"more-of" :arg1}}

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

(set-options! {:configured? true})

(defexpect spec-tests

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

  (expect
    "In the key-sequence [:map :x] the key :x was not recognized as valid!"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:map {:hang? true, :x :y}})))

  (expect "The value of the key-sequence [:map :hang?] -> 0 was not a boolean"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:map {:hang? 0}})))

  ;;
  ;; # Wrong first level key
  ;;

  (expect
    "In the key-sequence [:widthx] the key :widthx was not recognized as valid!"
    (explain-more (s/explain-data :zprint.spec/options {:widthx 80})))
  (expect
    "In the key-sequence [\"a\"] the key \"a\" was not recognized as valid!"
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

  (expect "The value of the key-sequence [:width] -> :a was not a number"
          (explain-more (s/explain-data :zprint.spec/options {:width :a})))
  (expect "The value of the key-sequence [:width] -> \"a\" was not a number"
          (explain-more (s/explain-data :zprint.spec/options {:width "a"})))
  (expect "The value of the key-sequence [:width] -> [:a] was not a number"
          (explain-more (s/explain-data :zprint.spec/options {:width [:a]})))
  (expect "The value of the key-sequence [:width] -> {:a :b} was not a number"
          (explain-more (s/explain-data :zprint.spec/options {:width {:a :b}})))
  (expect "The value of the key-sequence [:width] -> a was not a number"
          (explain-more (s/explain-data :zprint.spec/options {:width 'a})))
  (expect "The value of the key-sequence [:width] -> #{:a} was not a number"
          (explain-more (s/explain-data :zprint.spec/options {:width #{:a}})))
  ;;
  ;; ## Should be a map
  ;;

  (expect "The value of the key-sequence [:map] -> :a was not a map"
          (explain-more (s/explain-data :zprint.spec/options {:map :a})))
  (expect "The value of the key-sequence [:map] -> 5 was not a map"
          (explain-more (s/explain-data :zprint.spec/options {:map 5})))
  (expect "The value of the key-sequence [:map] -> \"a\" was not a map"
          (explain-more (s/explain-data :zprint.spec/options {:map "a"})))
  (expect "The value of the key-sequence [:map] -> [:a] was not a map"
          (explain-more (s/explain-data :zprint.spec/options {:map [:a]})))
  (expect "The value of the key-sequence [:map] -> a was not a map"
          (explain-more (s/explain-data :zprint.spec/options {:map 'a})))
  (expect "The value of the key-sequence [:map] -> #{:a} was not a map"
          (explain-more (s/explain-data :zprint.spec/options {:map #{:a}})))

  ;;
  ;; ## Should be a boolean
  ;;

  (expect
    "The value of the key-sequence [:parse-string?] -> 0 was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:parse-string? 0})))
  (expect
    "The value of the key-sequence [:parse-string?] -> :a was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:parse-string? :a})))
  (expect
    "The value of the key-sequence [:parse-string?] -> \"a\" was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:parse-string? "a"})))
  (expect
    "The value of the key-sequence [:parse-string?] -> [:a] was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:parse-string? [:a]})))
  (expect
    "The value of the key-sequence [:parse-string?] -> {:a :b} was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:parse-string? {:a :b}})))
  (expect
    "The value of the key-sequence [:parse-string?] -> a was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:parse-string? 'a})))
  (expect
    "The value of the key-sequence [:parse-string?] -> #{:a} was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:parse-string? #{:a}})))

  ;;
  ;; ## Should be a set
  ;;

  (expect "The value of the key-sequence [:fn-force-nl] -> :a was not a set"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:fn-force-nl :a})))
  (expect "The value of the key-sequence [:fn-force-nl] -> 0 was not a set"
          (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl 0})))
  (expect "The value of the key-sequence [:fn-force-nl] -> \"a\" was not a set"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:fn-force-nl "a"})))
  (expect "The value of the key-sequence [:fn-force-nl] -> [:a] was not a set"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:fn-force-nl [:a]})))
  (expect
    "The value of the key-sequence [:fn-force-nl] -> {:a :b} was not a set"
    (explain-more (s/explain-data :zprint.spec/options {:fn-force-nl {:a :b}})))
  (expect "The value of the key-sequence [:fn-force-nl] -> a was not a set"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:fn-force-nl 'a})))

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
    "The value of the key-sequence [:map :indent] -> \"a\" was not a number"
    (explain-more (s/explain-data :zprint.spec/options {:map {:indent "a"}})))
  (expect "The value of the key-sequence [:map :indent] -> :a was not a number"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:map {:indent :a}})))
  (expect
    "The value of the key-sequence [:map :indent] -> [:a] was not a number"
    (explain-more (s/explain-data :zprint.spec/options {:map {:indent [:a]}})))
  (expect
    "The value of the key-sequence [:map :indent] -> {:a :b} was not a number"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:map {:indent {:a :b}}})))
  (expect
    "The value of the key-sequence [:map :indent] -> #{:a} was not a number"
    (explain-more (s/explain-data :zprint.spec/options {:map {:indent #{:a}}})))
  (expect "The value of the key-sequence [:map :indent] -> a was not a number"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:map {:indent 'a}})))

  ;;
  ;; ## Should be a boolen
  ;;

  (expect
    "The value of the key-sequence [:list :hang?] -> \"a\" was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:list {:hang? "a"}})))
  (expect "The value of the key-sequence [:list :hang?] -> :a was not a boolean"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:list {:hang? :a}})))
  (expect
    "The value of the key-sequence [:list :hang?] -> [:a] was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:list {:hang? [:a]}})))
  (expect
    "The value of the key-sequence [:list :hang?] -> {:a :b} was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:list {:hang? {:a :b}}})))
  (expect
    "The value of the key-sequence [:list :hang?] -> #{:a} was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options {:list {:hang? #{:a}}})))
  (expect "The value of the key-sequence [:list :hang?] -> a was not a boolean"
          (explain-more (s/explain-data :zprint.spec/options
                                        {:list {:hang? 'a}})))

  ;;
  ;; ## Should be a map
  ;;


  (expect
    "The value of the key-sequence [:uneval :color-map] -> \"a\" was not a map"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:uneval {:color-map "a"}})))

  (expect
    "The value of the key-sequence [:uneval :color-map] -> :a was not a map"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:uneval {:color-map :a}})))
  (expect
    "The value of the key-sequence [:uneval :color-map] -> [:a] was not a map"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:uneval {:color-map [:a]}})))
  (expect
    "The value of the key-sequence [:uneval :color-map] -> #{:a} was not a map"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:uneval {:color-map #{:a}}})))

  (expect
    "The value of the key-sequence [:uneval :color-map] -> a was not a map"
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
    "The value of the key-sequence [:map :key-order] -> :a was not a sequential"
    (explain-more (s/explain-data :zprint.spec/options {:map {:key-order :a}})))
  (expect
    "The value of the key-sequence [:map :key-order] -> \"a\" was not a sequential"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:map {:key-order "a"}})))
  (expect
    "The value of the key-sequence [:map :key-order] -> {:a :b} was not a sequential"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:map {:key-order {:a :b}}})))
  (expect
    "The value of the key-sequence [:map :key-order] -> #{:a} was not a sequential"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:map {:key-order #{:a}}})))
  (expect
    "The value of the key-sequence [:map :key-order] -> a was not a sequential"
    (explain-more (s/explain-data :zprint.spec/options {:map {:key-order 'a}})))
  (expect
    "The value of the key-sequence [:map :key-order] -> 5 was not a sequential"
    (explain-more (s/explain-data :zprint.spec/options {:map {:key-order 5}})))

  ;;
  ;; ## Should be a set
  ;;

  (expect
    "The value of the key-sequence [:extend :modifiers] -> :a was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers :a}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> \"a\" was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers "a"}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers {:a :b}}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers {:a :b}}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers {:a :b}}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> [:a] was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers [:a]}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> a was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers 'a}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> 5 was not a set"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers 5}})))

  ;;
  ;; ## Should be a set of strings
  ;;

  (expect
    "The value of the key-sequence [:extend :modifiers] -> :a was not a string"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers #{:a}}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> {:a :b} was not a string"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers #{{:a :b}}}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> [:a] was not a string"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers #{[:a]}}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> a was not a string"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers #{'a}}})))
  (expect
    "The value of the key-sequence [:extend :modifiers] -> 5 was not a string"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:extend {:modifiers #{5}}})))

  ;;
  ;; # Deeper feature
  ;;

  (expect
    "The value of the key-sequence [:map :key-value-color :deeper :keyword] -> :bluex was not a ansi-codes"
    (explain-more
      (s/explain-data :zprint.spec/options
                      {:map {:key-value-color {:deeper {:string :yellow,
                                                        :keyword :bluex}}}})))
  (expect
    "In the key-sequence [:map :key-value-color :deeper :keywordx] the key :keywordx was not recognized as valid!"
    (explain-more
      (s/explain-data :zprint.spec/options
                      {:map {:key-value-color {:deeper {:string :yellow,
                                                        :keywordx :blue}}}})))


  (expect nil
          (explain-more (s/explain-data :zprint.spec/options
                                        {:map {:key-value-color
                                                 {:deeper {:string :yellow,
                                                           :keyword :blue}}}})))

  ;;
  ;; # coerce-to-false
  ;;

  ; nothing happens to :b because :a is not zprint.spec/boolean
  (expect {:a :b} (coerce-to-boolean {:a :b, :coerce-to-false :b}))

  ; :parse-string? is zprint.spec/boolean so :b becomes false
  (expect {:parse-string? false}
          (coerce-to-boolean {:parse-string? :b, :coerce-to-false :b}))

  ; :parse-string? is zprint.spec/boolean so :b becomes true since it is not
  ; equal to :coerce-to-false
  (expect {:parse-string? true}
          (coerce-to-boolean {:parse-string? :b, :coerce-to-false :c}))

  ; same thing, and false doesn't change
  (expect {:parse-string? true, :vector {:wrap? false}}
          (coerce-to-boolean
            {:parse-string? :b, :coerce-to-false :c, :vector {:wrap? false}}))

  ; if :coerce-to-false is configured as a boolean, nothing will ever change
  ; because only non-boolean things are examined for a match to coerce-to-false
  (expect {:parse-string? true, :vector {:wrap? true}}
          (coerce-to-boolean
            {:parse-string? :b, :coerce-to-false true, :vector {:wrap? true}}))

  ; you can configure :coerce-to-false with pretty much anything
  (expect {:parse-string? true, :vector {:wrap? false}}
          (coerce-to-boolean {:parse-string? :b,
                              :coerce-to-false "stuff",
                              :vector {:wrap? "stuff"}}))

  ; nothing matches 0, all things are boolean spec, so they all go to true
  (expect {:parse-string? true, :vector {:wrap? true}}
          (coerce-to-boolean
            {:parse-string? :b, :coerce-to-false 0, :vector {:wrap? "stuff"}}))

  ; things that do match :coerce-to-false got to false
  (expect {:parse-string? true, :vector {:wrap? false}}
          (coerce-to-boolean
            {:parse-string? :b, :coerce-to-false 0, :vector {:wrap? 0}}))

  ;;
  ;; Some tests for some extensions to explain-more to handle deeper problems,
  ;; as we move to letting spec validate pretty much everything.
  ;;

  ; Now spec validates the fn-map

  (expect
    "The value of the key-sequence [:fn-map \"stuff\"] -> [:arg1 {:width :x}] was not recognized as valid! because the value of the key-sequence [:width] -> :x was not a number"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:fn-map {"stuff" [:arg1 {:width :x}]}})))

  ; It also validates the style maps

  (expect
    "The value of the key-sequence [:style-map :new-style :parse-string?] -> :a was not a boolean"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:style-map {:new-style {:parse-string?
                                                             :a}}})))

  ; It also validates the new options map for :script

  (expect
    "The value of the key-sequence [:script :more-options :style] -> \"stuff\" was not a keyword"
    (explain-more (s/explain-data :zprint.spec/options
                                  {:script {:more-options {:style "stuff"}}})))

  (expect :zprint.spec/fn-type :arg1)

)
