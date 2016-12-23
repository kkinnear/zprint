(ns zprint.zutil-test
  (:require [expectations :refer :all]
            [clojure.repl :refer :all]
            [clojure.string :as str]
            [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only [edn*]]))

;; Keep some of the test on wrapping so they still work
;!zprint {:comment {:wrap? false}}

;;
;; Test for ( def x :yabcdefghijklmnopqrstuvwxyz) which duplicates x because
;; znth didn't work correctly if there was whitespace at the start of a
;; seq.
;;

(def zz (z/edn* (p/parse-string "( 0 1 2 3 4 )")))

(expect "0" (z/string (zprint.zutil/znth zz 0)))
(expect "1" (z/string (zprint.zutil/znth zz 1)))
(expect "2" (z/string (zprint.zutil/znth zz 2)))
(expect "3" (z/string (zprint.zutil/znth zz 3)))
(expect "4" (z/string (zprint.zutil/znth zz 4)))
(expect nil (z/string (zprint.zutil/znth zz 5)))

(def za (z/edn* (p/parse-string "(0 1 2 3 4)")))

(expect "0" (z/string (zprint.zutil/znth za 0)))
(expect "1" (z/string (zprint.zutil/znth za 1)))
(expect "2" (z/string (zprint.zutil/znth za 2)))
(expect "3" (z/string (zprint.zutil/znth za 3)))
(expect "4" (z/string (zprint.zutil/znth za 4)))
(expect nil (z/string (zprint.zutil/znth za 5)))
