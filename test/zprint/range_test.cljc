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
"\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 12, :end 17}}}))

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 7, :end 9}}}))

  (expect
"\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 19, :end 19}}}))

  (expect
"\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 10, :end 10}}}))

  (expect
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 6, :end 10}}}))

  (expect
    "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 1, :end 1}}}))

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 0, :end 0}}}))

  (expect
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 0, :end 10}}}))

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 28, :end 28}}}))

  (expect
"\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 10, :end 20}}}))

  (expect
"\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 10, :end 21}}}))

  (expect
    "\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n\"This is a test\"\n{:added 1.0 :static true}\n([x y]\n(or (list\n(list\n(list\ny\n(list x))))               \n())))\n               \n#?(:clj (defn zpmap \n([options f coll]   \n(if (:parallel? options) (pmap f coll) (map f coll)))    \n([options f coll1 coll2]   \n(if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))    \n:cljs (defn zpmap \n([options f coll] (map f coll))    \n([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 29, :end 29}}}))

  (expect
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
    (zprint-file-str range1 "junk" {:input {:range {:start 0, :end 29}}}))

  (expect
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
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
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               {:a}\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n#?(:cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"
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
"\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:bb (defn zpmap\n         ([options f coll] (if (:parallel? options) (pmap f coll) (map f coll)))\n         ([options f coll1 coll2]\n          (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2)))))\n"
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

  ;;
  ;; New addtion: {:output {:range? true}} returns the actual-start and
  ;; actual-end in a vector.
  ;;

  (def sb2
    "#!/usr/bin/env bb\n\n(defmacro diff-com\n\"Is community formatting different?\"\n[f]\n`(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n\"true\"\n(zprint-fn-str ~f)))\n\n(defn ortst\n\"This is a test\"\n{:added 1.0, :static true}\n([x y] (or (list (list (list y (list x)))) ())))\n\n\n")


  (expect
    [{:range {:actual-start 0, :actual-end 6}}
     "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n"]
    (zprint-file-str range1
                     "stuff"
                     {:input {:range {:start 2, :end 5}},
                      :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str range1
                           "stuff"
                           {:input {:range {:start 7, :end 9}},
                            :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str range1
                           "stuff"
                           {:input {:range {:start -1, :end -1}},
                            :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str range1
                           "stuff"
                           {:input {:range {:start -1, :end -2}},
                            :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str range1
                           "stuff"
                           {:input {:range {:start 0, :end -2}},
                            :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str range1
                           "stuff"
                           {:input {:range {:start 0, :end 0}},
                            :output {:range? true}}))

  (expect
    [{:range {:actual-start 0, :actual-end 6}}
     "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n"]
    (zprint-file-str range1
                     "stuff"
                     {:input {:range {:start 5, :end 3}},
                      :output {:range? true}}))

  (expect
[{:range {:actual-start 0, :actual-end 28}} "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"]
    (zprint-file-str range1
                     "stuff"
                     {:input {:range {:start -1, :end 1000}},
                      :output {:range? true}}))

  (expect
[{:range {:actual-start 0, :actual-end 28}} "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"]
    (zprint-file-str range1
                     "stuff"
                     {:input {:range {:start nil, :end 1000}},
                      :output {:range? true}}))

  (expect
[{:range {:actual-start 0, :actual-end 28}} "\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n;!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"]
    (zprint-file-str range1
                     "stuff"
                     {:input {:range {:start 5, :end nil}},
                      :output {:range? true}}))

  (expect
[{:range {:actual-start 8, :actual-end 28}} ";!zprint {:format :next :width 25}\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0,\n   :static true}\n  ([x y]\n   (or (list (list\n               (list\n                 y\n                 (list\n                   x))))\n       ())))\n\n#?(:clj (defn zpmap\n          ([options f coll]\n           (if (:parallel? options) (pmap f coll) (map f coll)))\n          ([options f coll1 coll2]\n           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))\n   :cljs (defn zpmap\n           ([options f coll] (map f coll))\n           ([options f coll1 coll2] (map f coll1 coll2))))\n"]

    (zprint-file-str range1
                     "stuff"
                     {:input {:range {:start 9, :end nil}},
                      :output {:range? true}}))

  (expect
    [{:range {:actual-start 0, :actual-end 14}}
     "#!/usr/bin/env bb\n\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0, :static true}\n  ([x y] (or (list (list (list y (list x)))) ())))\n\n\n"]
    (zprint-file-str sb2
                     "stuff"
                     {:input {:range {:start 0, :end nil}},
                      :output {:range? true}}))

  (expect
    [{:range {:actual-start 0, :actual-end 14}}
     "#!/usr/bin/env bb\n\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n\n(defn ortst\n  \"This is a test\"\n  {:added 1.0, :static true}\n  ([x y] (or (list (list (list y (list x)))) ())))\n\n\n"]
    (zprint-file-str sb2
                     "stuff"
                     {:input {:range {:start -1, :end nil}},
                      :output {:range? true}}))

  (expect
    [{:range {:actual-start 0, :actual-end 7}}
     "#!/usr/bin/env bb\n\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n"]
    (zprint-file-str sb2
                     "stuff"
                     {:input {:range {:start 3, :end 4}},
                      :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str sb2
                           "stuff"
                           {:input {:range {:start 8, :end 8}},
                            :output {:range? true}}))

  (expect
    [{:range {:actual-start 0, :actual-end 7}}
     "#!/usr/bin/env bb\n\n(defmacro diff-com\n  \"Is community formatting different?\"\n  [f]\n  `(if (= (zprint-fn-str ~f) (zprint-fn-str ~f {:style :community}))\n     \"true\"\n     (zprint-fn-str ~f)))\n"]
    (zprint-file-str sb2
                     "stuff"
                     {:input {:range {:start 7, :end 7}},
                      :output {:range? true}}))

  (expect
    [{:range {:actual-start 9, :actual-end 12}}
     "(defn ortst\n  \"This is a test\"\n  {:added 1.0, :static true}\n  ([x y] (or (list (list (list y (list x)))) ())))\n"]
    (zprint-file-str sb2
                     "stuff"
                     {:input {:range {:start 9, :end 9}},
                      :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str sb2
                           "stuff"
                           {:input {:range {:start 13, :end 1000}},
                            :output {:range? true}}))

  (expect [{:range {:actual-start -1, :actual-end -1}} nil]
          (zprint-file-str sb2
                           "stuff"
                           {:input {:range {:start 13, :end nil}},
                            :output {:range? true}}))

  (def rg2 "0\n1\n2\n3\n4\n5\n6\n7\n8\n9")

  (expect [{:range {:actual-start 3, :actual-end 9}} "3\n4\n5\n6\n7\n8\n9"]
          (zprint-file-str rg2
                           "stuff"
                           {:style :respect-nl,
                            :input {:range {:start 3, :end 9}},
                            :output {:range? true}}))

  (expect [{:range {:actual-start 3, :actual-end 8}} "3\n4\n5\n6\n7\n8\n"]
          (zprint-file-str rg2
                           "stuff"
                           {:style :respect-nl,
                            :input {:range {:start 3, :end 8}},
                            :output {:range? true}}))

  (expect [{:range {:actual-start 3, :actual-end 9}} "3\n4\n5\n6\n7\n8\n9"]
          (zprint-file-str rg2
                           "stuff"
                           {:style :respect-nl,
                            :input {:range {:start 3, :end 10}},
                            :output {:range? true}}))

  ;;
  ;; :use-previous-!zprint?
  ;; :continue-after-!zprint-error?
  ;;

  ; has a bad width and a bad wrap?
  (def capi4
    "(def abc
:def
:ijk)

;!zprint {:format :off}

(def r
:s :t
:u
:v)

;!zprint {:format :on}

(def b
:c 
:d
:e
:f)

;!zprint {:format :skip}

(def h
:i 
:j
:k)

;!zprint {:format :next :vector {:wrapx? false}}

(def j 
[:t 
:u
:v
:w])

;!zprint {:widthx 20}

(def jfkdsj dlkfjsdl
djlfsdj
sdjlfdjsl
jsdlfjl
jsdfjsl
 sdjfj
 jskldfjls
 jsdlfkdsjlk
 sjfldj)

;!zprint {:style :respect-bl}

(def a
c
d
g
e)

;!zprint {:style :pair-nl-all}

(defn abc
[d e f]

(h fkds ldskfjls sjlsld lkdsjfl lkjsldfj 
i 
j))
")

  ; has a bad width and a bad wrap? and :continue-after-!zprint-error? true
  (def capi4a
    "(def abc
:def
:ijk)

;!zprint {:format :off}

(def r
:s :t
:u
:v)

;!zprint {:format :on :input {:range {:continue-after-!zprint-error? true}}}

(def b
:c 
:d
:e
:f)

;!zprint {:format :skip}

(def h
:i 
:j
:k)

;!zprint {:format :next :vector {:wrapx? false}}

(def j 
[:t 
:u
:v
:w])

;!zprint {:widthx 20}

(def jfkdsj dlkfjsdl
djlfsdj
sdjlfdjsl
jsdlfjl
jsdfjsl
 sdjfj
 jskldfjls
 jsdlfkdsjlk
 sjfldj)

;!zprint {:style :respect-bl}

(def a
c
d
g
e)

;!zprint {:style :pair-nl-all}

(defn abc
[d e f]

(h fkds ldskfjls sjlsld lkdsjfl lkjsldfj 
i 
j))
")


  (expect
    [{:range {:actual-start 54, :actual-end 61}}
     ";!zprint {:style :pair-nl-all}\n\n(defn abc [d e f] (h fkds ldskfjls sjlsld lkdsjfl lkjsldfj i j))\n"]
    (zprint-file-str capi4
                     "test"
                     {:input {:range {:start 57,
                                      :end 57,
                                      :continue-after-!zprint-error? true}},
                      :output {:range? true}}))

  (expect
    [{:range
        {:actual-start 54,
         :actual-end 61,
         :errors
           ["In ;!zprint number 5 in test, In the key-sequence [:widthx] the key :widthx was not recognized as valid!"]}}
     ";!zprint {:style :pair-nl-all}\n\n(defn abc\n  [d e f]\n\n  (h fkds ldskfjls sjlsld lkdsjfl lkjsldfj i j))\n"]
    (zprint-file-str capi4
                     "test"
                     {:input {:range {:start 57,
                                      :end 57,
                                      :continue-after-!zprint-error? true,
                                      :use-previous-!zprint? true}},
                      :output {:range? true}}))

  (expect
    "!zprint number 5 in test, In the key-sequence [:widthx] the key :widthx was not recognized as valid!"
    (try (zprint-file-str capi4
                          "test"
                          {:input {:range {:start 57,
                                           :end 57,
                                           :continue-after-!zprint-error? false,
                                           :use-previous-!zprint? true}},
                           :output {:range? true}})
         (catch #?(:clj Exception
                   :cljs :default)
           e
           (clojure.string/replace (str e) #".*In ;" ""))))

  (expect
    [{:range
        {:actual-start 26,
         :actual-end 32,
         :errors
           ["In ;!zprint number 4 in test, In the key-sequence [:vector :wrapx?] the key :wrapx? was not recognized as valid!"]}}
     ";!zprint {:format :next :vector {:wrapx? false}}\n\n(def j [:t :u :v :w])\n"]
    (zprint-file-str capi4
                     "test"
                     {:input {:range {:start 30,
                                      :end 30,
                                      :continue-after-!zprint-error? true,
                                      :use-previous-!zprint? true}},
                      :output {:range? true}}))

  (expect
    [{:range {:actual-start 54, :actual-end 61}}
     ";!zprint {:style :pair-nl-all}\n\n(defn abc [d e f] (h fkds ldskfjls sjlsld lkdsjfl lkjsldfj i j))\n"]
    (zprint-file-str capi4a
                     "test"
                     {:input {:range {:start 57,
                                      :end 57,
                                      :continue-after-!zprint-error? false,
                                      :use-previous-!zprint? false}},
                      :output {:range? true}}))

  (expect
    [{:range
        {:actual-start 54,
         :actual-end 61,
         :errors
           ["In ;!zprint number 5 in test, In the key-sequence [:widthx] the key :widthx was not recognized as valid!"]}}
     ";!zprint {:style :pair-nl-all}\n\n(defn abc\n  [d e f]\n\n  (h fkds ldskfjls sjlsld lkdsjfl lkjsldfj i j))\n"]
    (zprint-file-str capi4a
                     "test"
                     {:input {:range {:start 57,
                                      :end 57,
                                      :continue-after-!zprint-error? false,
                                      :use-previous-!zprint? true}},
                      :output {:range? true}}))


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
  ;; End of defexpect
  ;;
  ;; All tests MUST come before this!!!
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
)
