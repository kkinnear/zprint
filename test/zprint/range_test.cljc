(ns zprint.range-test
  (:require [expectations.cljc.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            [zprint.core :refer
             [zprint-file-str set-options!]]
            [zprint.finish :refer [cvec-to-style-vec compress-style]]))

;; Keep some of the test on wrapping so they still work
;!zprint {:comment {:wrap? false}}

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

;
; Set :force-eol-blanks? true here to see if we are catching eol blanks
;

(set-options!
  {:configured? true, :force-eol-blanks? false, :test-for-eol-blanks? true})

(defexpect range-tests

;;
;; # Range
;;
;; See if things print the way they are supposed to.
;;

(def range1
"\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n")

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 12, :end 17}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 7, :end 9}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 19, :end 19}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 10, :end 10}}}))

(expect
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
(zprint-file-str range1 "junk" {:input {:range {:start 6 :end 10}}}))

(expect
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
(zprint-file-str range1 "junk" {:input {:range {:start 1 :end 1}}}))

(expect
  "(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 0, :end 0}}}))

(expect
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
(zprint-file-str range1 "junk" {:input {:range {:start 0 :end 10}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 28, :end 28}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 10, :end 20}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 10, :end 21}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 29, :end 29}}}))

(expect
  "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 0, :end 29}}}))

(expect
  "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start -1, :end 29}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start -1, :end 0}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start -1, :end -1}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 100, :end -1}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 100, :end 200}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 100, :end 200}}}))

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range1 "junk" {:input {:range {:start 200, :end -1}}}))

;;
;; Here is a string that edamame can't parse, because it has a map with
;; only a key and not a value, so the range has no effect and it formats
;; everything

(def range2
"\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list {:a}\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))) \n#?(:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n")

(expect
  "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list {:a}\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n#?(:cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
  (zprint-file-str range2 "junk" {:input {:range {:start 0, :end 100}}}))

;;
;; What if the reader-conditional isn't for :clj or :cljs?  Can we still find
;; it?
;;

(def range3
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:bb (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))) \n")

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n\n#?(:bb (defn zpmap\n         ([options f coll] (if (:parallel? options) (pmap f coll) (map f coll)))\n         ([options f coll1 coll2]\n          (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n"
  (zprint-file-str range3 "junk" {:input {:range {:start 22, :end 22}}}))

; No :end

(expect
  "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n\n#?(:bb (defn zpmap\n         ([options f coll] (if (:parallel? options) (pmap f coll) (map f coll)))\n         ([options f coll1 coll2]\n          (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n"
  (zprint-file-str range3 "junk" {:input {:range {:start 22}}}))

; No :start

(expect
  "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n\n#?(:bb (defn zpmap\n         ([options f coll] (if (:parallel? options) (pmap f coll) (map f coll)))\n         ([options f coll1 coll2]\n          (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n"
  (zprint-file-str range3 "junk" {:input {:range {:end 22}}}))


)
