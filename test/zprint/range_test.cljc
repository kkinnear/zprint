(ns zprint.range-test
  (:require [expectations.clojure.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            [zprint.core :refer [zprint-file-str set-options!]]
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
    (zprint-file-str range1 "junk" {:input {:range {:start 6, :end 10}}}))

  (expect
    "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 1, :end 1}}}))

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 0, :end 0}}}))

  (expect
    "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 0, :end 10}}}))

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 28, :end 28}}}))

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
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
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:bb (defn zpmap\n         ([options f coll] (if (:parallel? options) (pmap f coll) (map f coll)))\n         ([options f coll1 coll2]\n          (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n"
    (zprint-file-str range3 "junk" {:input {:range {:start 22, :end 22}}}))

  ; No :end

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:bb (defn zpmap\n         ([options f coll] (if (:parallel? options) (pmap f coll) (map f coll)))\n         ([options f coll1 coll2]\n          (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n"
    (zprint-file-str range3 "junk" {:input {:range {:start 22}}}))

  ; No :start

  (expect
    "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list\n         (list\n           (list y\n                 (list\n                   x))))\n       ())))\n\n#?(:bb (defn zpmap\n         ([options f coll] (if (:parallel? options) (pmap f coll) (map f coll)))\n         ([options f coll1 coll2]\n          (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n"
    (zprint-file-str range3 "junk" {:input {:range {:end 22}}}))

  ;;
  ;; # Issue #154 -- problems between range and interpose, fixed by changing
  ;; how range numbers are interpreted.
  ;;

  (def i154e
    "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n(defn fn-2\n  [_]\n{:not     :quite\n       :formatted    :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))")


  (expect
    "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n\n(defn fn-2\n  [_]\n  {:not :quite,\n   :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:style :respect-nl,
                      :input {:range {:start 11, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2\n  [_]\n  {:not :quite,\n   :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:style :respect-nl,
                      :input {:range {:start 5, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "; Comment 1\n\n\n(ns test.zprint)\n\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2\n  [_]\n  {:not :quite,\n   :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:style :respect-nl,
                      :input {:range {:start 3, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "; Comment 1\n\n\n(ns test.zprint)\n\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2\n  [_]\n  {:not :quite,\n   :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:style :respect-nl,
                      :input {:range {:start 1, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "; Comment 1\n\n\n(ns test.zprint)\n\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2\n  [_]\n  {:not :quite,\n   :formatted :properly})\n\n\n(defn fn-3\n  [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:style :respect-nl,
                      :input {:range {:start -5, :end 100}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n(defn fn-2\n  [_]\n  {:not :quite,\n   :formatted :properly})\n\n(defn fn-3\n  [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:style :respect-nl,
                      :input {:range {:start -5, :end 100}}}))

  (expect
    "; Comment 1\n\n\n(ns test.zprint)\n\n\n(defn fn-1 [arg] (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2 [_] {:not :quite, :formatted :properly})\n\n\n(defn fn-3 [woo] (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:input {:range {:start -5, :end 100}},
                      :parse {:interpose "\n\n\n"},
                      :dbg? false}))

  (expect
    "; Comment 1\n\n\n(ns test.zprint)\n\n\n(defn fn-1 [arg] (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2 [_] {:not :quite, :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:input {:range {:start 1, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "; Comment 1\n\n\n(ns test.zprint)\n\n\n(defn fn-1 [arg] (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2 [_] {:not :quite, :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:input {:range {:start 3, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1 [arg] (println arg))\n\n\n; Comment 2\n\n\n(defn fn-2 [_] {:not :quite, :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:input {:range {:start 5, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  ;;
  ;; Tests for ranges that land on blank lines -- nothing happens
  ;;

  (expect
    "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n(defn fn-2\n  [_]\n{:not     :quite\n       :formatted    :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:input {:range {:start 16, :end 16}},
                      :parse {:interpose "\n\n\n"}}))

  (expect
    "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n(defn fn-2\n  [_]\n{:not     :quite\n       :formatted    :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
    (zprint-file-str i154e
                     "stuff"
                     {:input {:range {:start 0, :end 1}},
                      :parse {:interpose "\n\n\n"}}))

  ;;
  ;; Issue #190
  ;;
  ;; Problems with parser not recognizing :uneval expressions at the top level
  ;;

  (expect
    "(ns demo)\n\n(def foo :foo)\n\n#_(def bar :bar)\n\n(def cat :cat)\n"
    (zprint-file-str
      "(ns demo)\n\n(def foo\n  :foo\n)\n\n#_\n  (def bar\n    :bar\n  )\n\n(def cat :cat)\n"
      "stuff"
      {:input {:range {:start 3, :end 7}}, :dbg? false}))


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
  ;; End of defexpect
  ;;
  ;; All tests MUST come before this!!!
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
)
