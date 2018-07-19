(ns zprint.config-test
  (:require [expectations :refer :all]
            [zprint.core :refer :all]
            [zprint.zprint :refer :all]
            [zprint.config :refer :all]
            [zprint.finish :refer :all]
            [clojure.repl :refer :all]
            [clojure.string :as str]
            [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only [edn*]]))

;; Keep some of the test from wrapping so they still work
;!zprint {:comment {:wrap? false} :fn-map {"more-of" :arg1}}

;;
;; # :fn-*-force-nl tests
;;

(expect "(if :a\n  :b\n  :c)"
        (zprint-str "(if :a :b :c)"
                    {:parse-string? true, :fn-force-nl #{:arg1-body}}))

(expect "(if :a\n  :b\n  :c)"
        (zprint-str "(if :a :b :c)"
                    {:parse-string? true, :fn-gt2-force-nl #{:arg1-body}}))

(expect "(if :a :b)"
        (zprint-str "(if :a :b)"
                    {:parse-string? true, :fn-gt2-force-nl #{:arg1-body}}))

(expect "(assoc {} :a :b)"
        (zprint-str "(assoc {} :a :b)"
                    {:parse-string? true, :fn-gt3-force-nl #{:arg1-pair}}))

(expect "(assoc {}\n  :a :b\n  :c :d)"
        (zprint-str "(assoc {} :a :b :c :d)"
                    {:parse-string? true, :fn-gt3-force-nl #{:arg1-pair}}))

(expect
  "(:require [boot-fmt.impl :as impl]\n          [boot.core :as bc]\n          [boot.util :as bu])"
  (zprint-str
    "(:require [boot-fmt.impl :as impl] [boot.core :as bc] [boot.util :as bu])"
    {:parse-string? true, :fn-map {":require" :force-nl-body}}))

(expect
  "(:require\n  [boot-fmt.impl :as impl]\n  [boot.core :as bc]\n  [boot.util :as bu])"
  (zprint-str
    "(:require [boot-fmt.impl :as impl] [boot.core :as bc] [boot.util :as bu])"
    {:parse-string? true, :fn-map {":require" :flow}}))

(expect
  "(:require\n  [boot-fmt.impl :as impl]\n  [boot.core :as bc]\n  [boot.util :as bu])"
  (zprint-str
    "(:require [boot-fmt.impl :as impl] [boot.core :as bc] [boot.util :as bu])"
    {:parse-string? true, :fn-map {":require" :flow-body}}))

;;
;; # Style tests
;;

;;
;; First, let's ensure that we know how to do these tests!
;;

(expect false (:justify? (:binding (get-options))))

;;
;; This is how you do config tests, without altering the configuration
;; for all of the rest of the tests.
;;

(expect true
        (redef-state [zprint.config]
                     (set-options! {:style :justified})
                     (:justify? (:binding (get-options)))))

;;
;; And this shows that it leaves things alone!
;;

(expect false (:justify? (:binding (get-options))))

;;
;; Now, to the actual tests
;;

(expect (more-of options
          true
          (:justify? (:binding options))
          true
          (:justify? (:map options))
          true
          (:justify? (:pair options)))
        (redef-state [zprint.config]
                     (set-options! {:style :justified})
                     (get-options)))

(expect (more-of options
          0 (:indent (:binding options))
          1 (:indent-arg (:list options))
          0 (:indent (:map options))
          0 (:indent (:pair options))
          :none ((:fn-map options) "apply")
          :none ((:fn-map options) "assoc")
          :none ((:fn-map options) "filter")
          :none ((:fn-map options) "filterv")
          :none ((:fn-map options) "map")
          :none ((:fn-map options) "mapv")
          :none ((:fn-map options) "reduce")
          :none ((:fn-map options) "remove")
          :none-body ((:fn-map options) "with-meta"))
        (redef-state [zprint.config]
                     (set-options! {:style :community})
                     (get-options)))

(expect (more-of options
          true
          (:justify? (:binding options))
          true
          (:justify? (:map options))
          true
          (:justify? (:pair options))
          0 (:indent (:binding options))
          1 (:indent-arg (:list options))
          0 (:indent (:map options))
          0 (:indent (:pair options))
          :none ((:fn-map options) "apply")
          :none ((:fn-map options) "assoc")
          :none ((:fn-map options) "filter")
          :none ((:fn-map options) "filterv")
          :none ((:fn-map options) "map")
          :none ((:fn-map options) "mapv")
          :none ((:fn-map options) "reduce")
          :none ((:fn-map options) "remove")
          :none-body ((:fn-map options) "with-meta"))
        (redef-state [zprint.config]
                     (set-options! {:style [:community :justified]})
                     (get-options)))

(expect (more-of options
          true
          (:nl-separator? (:extend options))
          true
          (:flow? (:extend options))
          0
          (:indent (:extend options)))
        (redef-state [zprint.config]
                     (set-options! {:style :extend-nl})
                     (get-options)))

(expect
  (more-of options
    true
    (:nl-separator? (:map options))
    0
    (:indent (:map options)))
  (redef-state [zprint.config] (set-options! {:style :map-nl}) (get-options)))

(expect
  (more-of options
    true
    (:nl-separator? (:pair options))
    0
    (:indent (:pair options)))
  (redef-state [zprint.config] (set-options! {:style :pair-nl}) (get-options)))

(expect (more-of options
          true
          (:nl-separator? (:binding options))
          0
          (:indent (:binding options)))
        (redef-state [zprint.config]
                     (set-options! {:style :binding-nl})
                     (get-options)))

;;
;; # Test set element addition and removal
;;

; Define a new style

(expect
  (more-of options
    {:extend {:modifiers #{"stuff"}}}
    (:tst-style-1 (:style-map options)))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (get-options)))

; Apply a new style (which adds a set element)

(expect
  (more-of options #{"static" "stuff"} (:modifiers (:extend options)))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (set-options! {:style :tst-style-1})
               (get-options)))

; Remove a set element

(expect
  (more-of options #{"stuff"} (:modifiers (:extend options)))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (set-options! {:style :tst-style-1})
               (set-options! {:remove {:extend {:modifiers #{"static"}}}})
               (get-options)))

; Do the explained-options work?

; Add and remove something

(expect
  (more-of options #{"stuff"} (:value (:modifiers (:extend options))))
  (redef-state [zprint.config]
               (set-options! {:style-map {:tst-style-1
                                            {:extend {:modifiers #{"stuff"}}}}})
               (set-options! {:style :tst-style-1})
               (set-options! {:remove {:extend {:modifiers #{"static"}}}})
               (get-explained-all-options)))

; Add without style

(expect (more-of options
          #{:force-nl :flow :noarg1 :noarg1-body :force-nl-body :binding
            :arg1-force-nl :flow-body}
          (:value (:fn-force-nl options)))
        (redef-state [zprint.config]
                     (set-options! {:fn-force-nl #{:binding}})
                     (get-explained-all-options)))
;
; Tests for argument types that include options maps 
; in :fn-map --> [<arg-type> <options-map>]
;

; Does defproject have an options map, and is it correct?

(expect (more-of options
          true (vector? ((:fn-map options) "defproject"))
	  {:vector {:wrap? false}} (second ((:fn-map options) "defproject")))
        (redef-state [zprint.config]
		     (get-options)))

; Can we set an options map on let?

(expect (more-of options
          true (vector? ((:fn-map options) "let"))
	  {:width 99} (second ((:fn-map options) "let")))
        (redef-state [zprint.config]
	             (set-options! {:fn-map {"let" [:binding {:width 99}]}})
		     (get-options)))

; Will we get an exception when setting an invalid options map?

(expect Exception
        (redef-state [zprint.config]
	             (set-options! {:fn-map {"let" [:binding {:width "a"}]}})
		     (get-options)))


