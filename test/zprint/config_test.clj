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

;; Keep some of the test on wrapping so they still work
;!zprint {:comment {:wrap? false}}

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
