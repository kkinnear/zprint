(ns zprint.zprint-test
  (:require [expectations.clojure.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            #?(:cljs [cljs.test :refer-macros [deftest is]])
            #?(:clj [clojure.test :refer [deftest is]])
            #?(:cljs [cljs.tools.reader :refer [read-string]])
            [clojure.string :as str]
            [zprint.core :refer
             [zprint-str set-options! zprint-str-internal czprint-str
              zprint-file-str zprint czprint
              #?@(:clj [czprint-fn czprint-fn-str zprint-fn-str zprint-fn])]]
            [zprint.zprint :refer
             [line-count max-width line-lengths make-record contains-nil?
              map-ignore blanks]]
            [zprint.zutil :refer [edn*]]
            #_[zprint.config :refer :all :exclude
               [set-options! configure-all! get-options]]
            #?@(:clj ([clojure.repl :refer [source-fn]]))
            [zprint.core-test :refer [trim-gensym-regex x8]]
            #_[zprint.finish :refer :all]
            [rewrite-clj.parser :as p :refer [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z]))

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

(defexpect zprint-tests
 
;;
;; # Pretty Tests
;;
;; See if things print the way they are supposed to.
;;

; 
; Test with size of 20
;

(def iftest '(if (and :abcd :efbg) (list xxx yyy zzz) (list ccc ddd eee)))

(def iftest-19-str
  "(if (and :abcd\n         :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))")

(def iftest-20-str
  "(if (and :abcd\n         :efbg)\n  (list xxx yyy zzz)\n  (list ccc\n        ddd\n        eee))")

(def iftest-21-str
  "(if (and :abcd :efbg)\n  (list xxx yyy zzz)\n  (list ccc ddd eee))")

(expect iftest-19-str (zprint-str iftest 19 {:list {:constant-pair? nil}}))

(defn lines "Turn a string into lines." [s] (str/split s #"\n"))

(expect (lines iftest-20-str) (lines (zprint-str iftest 20)))
(expect iftest-21-str (zprint-str iftest 21))

(def fzprint-list*str
"(defn fzprint-list*\n  \"Print a list, which might be a list or an anon fn.  \n  Lots of work to make a list look good, as that is typically code. \n  Presently all of the callers of this are :list or :vector-fn.\"\n  [caller l-str r-str\n   ; The options map can get re-written down a bit below, so don't get\n   ; anything with destructuring that might change with a rewritten  options\n   ; map!\n   {:keys [fn-map user-fn-map one-line? fn-style no-arg1? fn-force-nl],\n    :as options} ind zloc]\n  ; We don't need to call get-respect-indent here, because all of the\n  ; callers of fzprint-list* define respect-nl?, respect-bl? and indent-only?\n  (let [max-length (get-max-length options)\n        zloc (modify-zloc caller options zloc)\n        ; zcount does (zmap identity zloc) which counts comments and the\n        ; newline after it, but no other newlines\n        len (zcount zloc)\n        zloc (if (> len max-length) (ztake-append max-length zloc '...) zloc)\n        len (zcount zloc)\n        l-str-len (count l-str)\n        indent (:indent (options caller))\n\t; NOTE WELL -- don't use arg-1-zloc (or arg-2-zloc, etc.) as\n\t; a condition, because it might well be legitimately nil when \n\t; formatting structures.\n        [pre-arg-1-style-vec arg-1-zloc arg-1-count zloc-seq :as first-data]\n          (fzprint-up-to-first-zloc caller options (+ ind l-str-len) zloc)\n        #_(prn \"fzprint-list* zloc-seq:\" (map zstring zloc-seq))\n        arg-1-coll? (not (or (zkeyword? arg-1-zloc) (zsymbol? arg-1-zloc)))\n        ; Use an alternative arg-1-indent if the fn-style is forced on input\n        ; and we don't actually have an arg-1 from which we can get an indent.\n        ; Now, we might want to allow arg-1-coll? to give us an arg-1-indent,\n        ; maybe, someday, so we could hang next to it.\n        ; But for now, this will do.\n        arg-1-indent-alt? (and arg-1-coll? fn-style)\n        fn-str (if-not arg-1-coll? (zstring arg-1-zloc))\n        fn-style (or fn-style (fn-map fn-str) (user-fn-map fn-str))\n        ; if we don't have a function style, let's see if we can get\n        ; one by removing the namespacing\n        fn-style (if (and (not fn-style) fn-str)\n                   (fn-map (last (clojure.string/split fn-str #\"/\")))\n                   fn-style)\n        ; Do we have a [fn-style options] vector?\n        ; **** NOTE: The options map can change here, and if it does,\n        ; some of the things found in it above would have to change too!\n        options\n          ; The config-and-validate allows us to use :style in the options\n          ; map associated with a function.  Don't think that we really needed\n          ; to validate (second fn-style), as that was already done.  But this\n          ; does allow us to use :style and other stuff.  Potential performance\n          ; improvement would be to build a config-and-validate that did the\n          ; same things and didn't validate.\n          ;\n          ; There could be two option maps in the fn-style vector:\n          ;   [:fn-style {:option :map}]\n          ;   [:fn-style {:zipper :option-map} {:structure :option-map}]\n          ;\n          ; If there is only one, it is used for both.  If there are two,\n          ; then we use the appropriate one.\n          (if (vector? fn-style)\n            (first (zprint.config/config-and-validate\n                     \"fn-style:\"\n                     nil\n                     options\n                     (if (= (count fn-style) 2)\n                       ; only one option map\n                       (second fn-style)\n                       (if (= :zipper (:ztype options))\n                         (second fn-style)\n                         (nth fn-style 2)))))\n            options)\n        ; If we messed with the options, then find new stuff.  This will\n        ; probably change only zloc-seq because of :respect-nl? or :indent-only?\n        [pre-arg-1-style-vec arg-1-zloc arg-1-count zloc-seq :as first-data]\n          (if (vector? fn-style)\n            (fzprint-up-to-first-zloc caller options (+ ind l-str-len) zloc)\n            first-data)\n        ; Don't do this too soon, as multiple things are driven off of\n        ; (vector? fn-style), above\n        fn-style (if (vector? fn-style) (first fn-style) fn-style)\n        ; Finish finding all of the interesting stuff in the first two\n        ; elements\n        [pre-arg-2-style-vec arg-2-zloc arg-2-count _ :as second-data]\n          ; The ind is wrong, need arg-1-indent, but we don't have it yet.\n          (fzprint-up-to-next-zloc caller\n                                   options\n                                   ;(+ ind l-str-len)\n                                   (+ ind indent)\n                                   first-data)\n        ; This len doesn't include newlines or other whitespace or\n        len (zcount-zloc-seq-nc-nws zloc-seq)\n        #_(prn \"fzprint-list* pre-arg-1-style-vec:\" pre-arg-1-style-vec\n               \"pre-arg-2-style-vec:\" pre-arg-2-style-vec\n               \"arg-1-zloc:\" (zstring arg-1-zloc)\n               \"arg-2-zloc:\" (zstring arg-2-zloc)\n               \"arg-1-count:\" arg-1-count\n               \"arg-2-count:\" arg-2-count\n               \"len:\" len)\n        ; If fn-style is :replace-w-string, then we have an interesting\n        ; set of things to do.\n        ;\n        [options arg-1-zloc l-str l-str-len r-str len zloc-seq]\n          (if (and (= fn-style :replace-w-string)\n                   (:replacement-string (options caller))\n                   (= len 2))\n            [(assoc (update-in options [caller] dissoc :replacement-string)\n               :rightcnt (dec (:rightcnt options))) arg-2-zloc\n             (:replacement-string (options caller))\n             (count (:replacement-string (options caller))) \"\" 1\n             (remove-one zloc-seq arg-1-count)]\n            [options arg-1-zloc l-str l-str-len r-str len zloc-seq])\n        #_(prn \"fzprint-list*: l-str:\" l-str\n               \"l-str-len:\" l-str-len\n               \"len:\" len\n               \"fn-style:\" fn-style)\n        ; Get indents which might have changed if the options map was\n        ; re-written by the function style being a vector.\n        indent (:indent (options caller))\n        indent-arg (:indent-arg (options caller))\n        indent-only? (:indent-only? (options caller))\n        ; set indent based on fn-style\n        indent (if (body-set fn-style) indent (or indent-arg indent))\n        indent (+ indent (dec l-str-len))\n        one-line-ok? (allow-one-line? options len fn-style)\n        one-line-ok? (when-not indent-only? one-line-ok?)\n        one-line-ok? (if (not= pre-arg-1-style-vec :noseq) nil one-line-ok?)\n        ; remove -body from fn-style if it was there\n        fn-style (or (body-map fn-style) fn-style)\n        ; All styles except :hang, :flow, and :flow-body and :binding need\n        ; three elements minimum. We could put this in the fn-map,\n        ; but until there are more than three (well four) exceptions, seems\n        ; like too much mechanism.\n        fn-style (if (#{:hang :flow :flow-body :binding :replace-w-string}\n                      fn-style)\n                   fn-style\n                   (if (< len 3) nil fn-style))\n        ;fn-style (if (= fn-style :hang) fn-style (if (< len 3) nil fn-style))\n        fn-style (if no-arg1? (or (noarg1-map fn-style) fn-style) fn-style)\n        ; no-arg? only affect one level down...\n        options (if no-arg1? (dissoc options :no-arg1?) options)\n        ; If l-str isn't one char, create an indent adjustment.  Largely\n        ; for anonymous functions, which otherwise would have their own\n        ; :anon config to parallel :list, which would be just too much\n        indent-adj (dec l-str-len)\n        ; The default indent is keyed off of whether or not the first thing\n        ; in the list is itself a list, since that list could evaluate to a\n        ; fn.  You can't replace the zlist? with arg-1-coll?, since if you do\n        ; multi-arity functions aren't done right, since the argument vector\n        ; is a coll?, and so arg-1-coll? is set, and then you get a two space\n        ; indent for multi-arity functions, which is wrong.\n        ; We could, conceivably, use zvector? here to specifically handle\n        ; multi-arity functions.  Or we could remember we are in a defn and\n        ; do something special there, or we could at least decide that we\n        ; were in code when we did this zlist? thing, since that is all about\n        ; code.  That wouldn't work if it was the top-level form, but would\n        ; otherwise.\n        default-indent (if (zlist? arg-1-zloc) indent l-str-len)\n        arg-1-indent (if-not arg-1-coll? (+ ind (inc l-str-len) (count fn-str)))\n        ; If we don't have an arg-1-indent, and we noticed that the inputs\n        ; justify using an alternative, then use the alternative.\n        arg-1-indent (or arg-1-indent (when arg-1-indent-alt? (+ indent ind)))\n        ; If we have anything in pre-arg-2-style-vec, then we aren't hanging\n        ; anything.  But an arg-1-indent of nil isn't good, so we will make it\n        ; like the flow indent so we flow.\n        arg-1-indent (if (= pre-arg-2-style-vec :noseq)\n                       arg-1-indent\n                       (when arg-1-indent (+ indent ind)))\n        ; Tell people inside that we are in code.\n        ; We don't catch places where the first thing in a list is\n        ; a collection or a seq which yields a function.\n        options (if (not arg-1-coll?) (assoc options :in-code? fn-str) options)\n        options (assoc options :pdepth (inc (long (or (:pdepth options) 0))))\n        _ (when (:dbg-hang options)\n            (println (dots (:pdepth options)) \"fzs\" fn-str))\n        new-ind (+ indent ind)\n        one-line-ind (+ l-str-len ind)\n        options (if fn-style (dissoc options :fn-style) options)\n        loptions (not-rightmost options)\n        roptions options\n        l-str-vec [[l-str (zcolor-map options l-str) :left]]\n        ; Fudge the ind a bit for r-str-vec for anon fns: #()\n        r-str-vec (rstr-vec options (+ ind (max 0 (dec l-str-len))) zloc r-str)\n        _ (dbg-pr\n            options\n            \"fzprint-list*:\" (zstring zloc)\n            \"fn-str\" fn-str\n            \"fn-style:\" fn-style\n            \"len:\" len\n            \"ind:\" ind\n            \"indent:\" indent\n            \"default-indent:\" default-indent\n            \"one-line-ok?\" one-line-ok?\n            \"arg-1-coll?\" arg-1-coll?\n            \"arg-1-indent:\" arg-1-indent\n            \"arg-1-zloc:\" (zstring arg-1-zloc)\n            \"pre-arg-1-style-vec:\" pre-arg-1-style-vec\n            \"l-str:\" (str \"'\" l-str \"'\")\n            \"l-str-len:\" l-str-len\n            \"r-str-vec:\" r-str-vec\n            \"indent-adj:\" indent-adj\n            \"one-line?:\" one-line?\n            \"indent-only?:\" indent-only?\n            \"rightcnt:\" (:rightcnt options)\n            \"replacement-string:\" (:replacement-string (caller options))\n            \":ztype:\" (:ztype options))\n        one-line (if (and (zero? len) (= pre-arg-1-style-vec :noseq))\n                   :empty\n                   (when one-line-ok?\n                     (fzprint-one-line options one-line-ind zloc-seq)))]\n    (cond\n      one-line (if (= one-line :empty)\n                 (concat-no-nil l-str-vec r-str-vec)\n                 (concat-no-nil l-str-vec one-line r-str-vec))\n      ; If we are in :one-line mode, and it didn't fit on one line,\n      ; we are done!  We don't see this debugging, below.  Suppose\n      ; we never get here?\n      one-line?\n        (dbg options \"fzprint-list*:\" fn-str \" one-line did not work!!!\")\n      (dbg options \"fzprint-list*: fn-style:\" fn-style) nil\n      (and (= len 0) (= pre-arg-1-style-vec :noseq)) (concat-no-nil l-str-vec\n                                                                    r-str-vec)\n      indent-only? (concat-no-nil l-str-vec\n                                  (fzprint-indent caller\n                                                  l-str\n                                                  r-str\n                                                  options\n                                                  ind\n                                                  zloc\n                                                  fn-style\n                                                  arg-1-indent)\n                                  r-str-vec)\n      (= len 1)\n        ; While len is one, don't assume that there is actually only one\n        ; thing to print and use fzprint*.  len only counts the non-comment\n        ; and non-nl elements, and there might be other things to print.\n        (concat-no-nil l-str-vec\n                       (fzprint-flow-seq roptions one-line-ind zloc-seq)\n                       r-str-vec)\n      ; In general, we don't have a fn-style if we have less than 3 elements.\n      ; However, :binding is allowed with any number up to this point, so we\n      ; have to check here.  :binding is actually allowed with at least two\n      ; elements, the third through n are optional.\n      (and (= fn-style :binding) (> len 1) (zvector? arg-2-zloc))\n        (let [[hang-or-flow binding-style-vec]\n                (fzprint-hang-unless-fail loptions\n                                          (or arg-1-indent (+ indent ind))\n                                          (+ indent ind)\n                                          fzprint-binding-vec\n                                          arg-2-zloc)\n              binding-style-vec (if (= hang-or-flow :hang)\n                                  (concat-no-nil [[\" \" :none :whitespace 14]]\n                                                 binding-style-vec)\n                                  binding-style-vec)]\n          (concat-no-nil l-str-vec\n                         pre-arg-1-style-vec\n                         ; TODO: get rid of inc ind\n                         (fzprint* loptions (inc ind) arg-1-zloc)\n                         pre-arg-2-style-vec\n                         binding-style-vec\n                         (concat-no-nil\n                           ; Here we use options, because fzprint-flow-seq\n                           ; will sort it out.  It will also handle an\n                           ; empty zloc-seq by returning :noseq, so we\n                           ; don't have to check for (> len 2) before\n                           ; we call it.\n                           (fzprint-flow-seq options\n                                             (+ indent ind)\n                                             (get-zloc-seq-right second-data)\n                                             :force-nl\n                                             :newline-first)\n                           r-str-vec)))\n      (= fn-style :pair-fn)\n        (let [zloc-seq-right-first (get-zloc-seq-right first-data)\n              zloc-count (count zloc-seq)]\n          (concat-no-nil l-str-vec\n                         pre-arg-1-style-vec\n                         (fzprint* loptions (inc ind) arg-1-zloc)\n                         (fzprint-hang (assoc-in options\n                                         [:pair :respect-nl?]\n                                         (:respect-nl? (caller options)))\n                                       :pair-fn\n                                       arg-1-indent\n                                       (+ indent ind)\n                                       fzprint-pairs\n                                       zloc-count\n                                       zloc-seq-right-first)\n                         r-str-vec))\n      (= fn-style :extend)\n        (let [zloc-seq-right-first (get-zloc-seq-right first-data)]\n          (concat-no-nil\n            l-str-vec\n            pre-arg-1-style-vec\n            (fzprint* loptions (inc ind) arg-1-zloc)\n            (prepend-nl\n              options\n              (+ indent ind)\n              ; I think fzprint-pairs will sort out which\n              ; is and isn't the rightmost because of\n              ; two-up\n              (fzprint-extend options (+ indent ind) zloc-seq-right-first))\n            r-str-vec))\n      ; needs (> len 2) but we already checked for that above in fn-style\n      (or (and (= fn-style :fn) (not (zlist? arg-2-zloc)))\n          (= fn-style :arg2)\n          (= fn-style :arg2-fn)\n          (= fn-style :arg2-pair)\n          (= fn-style :arg2-extend))\n        (let [[pre-arg-3-style-vec arg-3-zloc arg-3-count _ :as third-data]\n                ; The ind is wrong, need arg-1-indent, but we don't have it yet.\n                (fzprint-up-to-next-zloc caller\n                                         options\n                                         ; This is probably wrong\n                                         ; (+ ind l-str-len)\n                                         (+ ind indent)\n                                         second-data)\n              #_(prn \"pre-arg-1-style-vec:\" pre-arg-1-style-vec)\n              #_(prn \"pre-arg-2-style-vec:\" pre-arg-2-style-vec)\n              #_(prn \"pre-arg-3-style-vec:\" pre-arg-3-style-vec)\n              zloc-seq-right-third (get-zloc-seq-right third-data)\n              second-element (fzprint-hang-one\n                               caller\n                               (if (not arg-3-zloc) options loptions)\n                               ; This better not be nil\n                               arg-1-indent\n                               (+ indent ind)\n                               arg-2-zloc)\n              [line-count max-width]\n                ; arg-1-indent better not be nil here either\n                (style-lines loptions arg-1-indent second-element)\n              first-three\n                (when second-element\n                  (let [first-two-wo-pre-arg-1\n                          (concat-no-nil\n                            (fzprint* loptions (+ indent ind) arg-1-zloc)\n                            pre-arg-2-style-vec\n                            second-element\n                            pre-arg-3-style-vec)\n                        local-options\n                          (if (not zloc-seq-right-third) options loptions)\n                        first-two-one-line?\n                          (fzfit-one-line local-options\n                                          (style-lines local-options\n                                                       (+ ind indent)\n                                                       first-two-wo-pre-arg-1))\n                        ; Add pre-arg-1-style-vec back in, which might push\n                        ; it to two lines (or many lines), but that\n                        ; doesn't matter.\n                        first-two (concat-no-nil pre-arg-1-style-vec\n                                                 first-two-wo-pre-arg-1)]\n                    (when-not first-two-one-line?\n                      (dbg-pr options\n                              \"fzprint-list*: :arg2-* first two didn't fit:\"\n                              first-two))\n                    (concat-no-nil\n                      first-two\n                      (if (or (= fn-style :arg2)\n                              (= fn-style :arg2-pair)\n                              (= fn-style :arg2-fn)\n                              (= fn-style :arg2-extend)\n                              (and (zvector? arg-3-zloc) (= line-count 1)))\n                        (fzprint-hang-one\n                          caller\n                          (if (not zloc-seq-right-third) options loptions)\n                          (if (and (= pre-arg-3-style-vec :noseq)\n                                   first-two-one-line?)\n                            ; hang it if possible\n                            max-width\n                            ; flow it\n                            (+ indent ind))\n                          (+ indent ind)\n                          arg-3-zloc)\n                        (prepend-nl options\n                                    (+ indent ind)\n                                    (fzprint* (if (not zloc-seq-right-third)\n                                                options\n                                                loptions)\n                                              (+ indent ind)\n                                              arg-3-zloc))))))]\n          (when first-three\n            (if (not zloc-seq-right-third)\n              ; if nothing after the third thing, means just three things\n              (concat-no-nil l-str-vec first-three r-str-vec)\n              ; more than three things\n              (concat-no-nil\n                l-str-vec\n                first-three\n                (cond (= fn-style :arg2-pair)\n                        (prepend-nl options\n                                    (+ indent ind)\n                                    (fzprint-pairs options\n                                                   (+ indent ind)\n                                                   zloc-seq-right-third))\n                      (= fn-style :arg2-extend)\n                        (prepend-nl options\n                                    (+ indent ind)\n                                    (fzprint-extend options\n                                                    (+ indent ind)\n                                                    zloc-seq-right-third))\n                      :else (fzprint-hang-remaining caller\n                                                    ;options\n                                                    (if (= fn-style :arg2-fn)\n                                                      (assoc options\n                                                        :fn-style :fn)\n                                                      options)\n                                                    (+ indent ind)\n                                                    ; force flow\n                                                    (+ indent ind)\n                                                    zloc-seq-right-third\n                                                    fn-style))\n                r-str-vec))))\n      (and (= fn-style :arg1-mixin) (> len 3))\n        (let [[pre-arg-3-style-vec arg-3-zloc arg-3-count _ :as third-data]\n                (fzprint-up-to-next-zloc caller\n                                         options\n                                         (+ ind indent)\n                                         second-data)\n              [pre-arg-4-style-vec arg-4-zloc arg-4-count _ :as fourth-data]\n                (fzprint-up-to-next-zloc caller\n                                         options\n                                         (+ ind indent)\n                                         third-data)\n              arg-vec-index (or (zfind-seq #(or (zvector? %)\n                                                (when (zlist? %)\n                                                  (zvector? (zfirst %))))\n                                           zloc-seq)\n                                0)\n              doc-string? (string? (zsexpr arg-3-zloc))\n              mixin-start (if doc-string? arg-4-count arg-3-count)\n              mixin-length (- arg-vec-index mixin-start 1)\n              mixins? (pos? mixin-length)\n              doc-string (when doc-string?\n                           (fzprint-hang-one caller\n                                             loptions\n                                             (+ indent ind)\n                                             ; force flow\n                                             (+ indent ind)\n                                             arg-3-zloc))\n              #_(prn \":arg1-mixin: doc-string?\" doc-string?\n                     \"mixin-start:\" mixin-start\n                     \"mixin-length:\" mixin-length\n                     \"mixins?\" mixins?\n                     \"arg-vec-index:\" arg-vec-index\n                     \"doc-string\" doc-string\n                     \"arg-1-count:\" arg-1-count\n                     \"arg-1-zloc:\" (zstring arg-1-zloc)\n                     \"arg-2-count:\" arg-2-count\n                     \"arg-2-zloc:\" (zstring arg-2-zloc)\n                     \"arg-3-count:\" arg-3-count\n                     \"arg-3-zloc:\" (zstring arg-3-zloc)\n                     \"arg-4-count:\" arg-4-count\n                     \"arg-4-zloc:\" (zstring arg-4-zloc))\n              ; Have to deal with no arg-vec-index!!\n              mixins\n                (when mixins?\n                  (let [mixin-sentinal (fzprint-hang-one\n                                         caller\n                                         loptions\n                                         (+ indent ind)\n                                         ; force flow\n                                         (+ indent ind)\n                                         (if doc-string? arg-4-zloc arg-3-zloc))\n                        [line-count max-width]\n                          (style-lines loptions (+ indent ind) mixin-sentinal)]\n                    (concat-no-nil\n                      (if doc-string? pre-arg-4-style-vec pre-arg-3-style-vec)\n                      mixin-sentinal\n                      (fzprint-hang-remaining\n                        caller\n                        loptions\n                        ; Apparently hang-remaining gives\n                        ; you a\n                        ; space after the current thing,\n                        ; so we\n                        ; need to account for it now,\n                        ; since\n                        ; max-width is the end of the\n                        ; current\n                        ; thing\n                        (inc max-width)\n                        (dec (+ indent indent ind))\n                        (get-zloc-seq-right\n                          (if doc-string fourth-data third-data))\n                        fn-style\n                        mixin-length))))]\n          (concat-no-nil\n            l-str-vec\n            pre-arg-1-style-vec\n            (fzprint* loptions (inc ind) arg-1-zloc)\n            pre-arg-2-style-vec\n            (fzprint-hang-one caller\n                              (if (= len 2) options loptions)\n                              arg-1-indent\n                              (+ indent ind)\n                              arg-2-zloc)\n            (cond (and doc-string? mixins?) (concat-no-nil pre-arg-3-style-vec\n                                                           doc-string\n                                                           (remove-one-newline\n                                                             mixins))\n                  doc-string? (concat-no-nil pre-arg-3-style-vec doc-string)\n                  mixins? (remove-one-newline mixins)\n                  :else :noseq)\n            (fzprint-hang-remaining\n              caller\n              (noarg1 options fn-style)\n              (+ indent ind)\n              ; force flow\n              (+ indent ind)\n              (nthnext zloc-seq\n                       (if mixins?\n                         arg-vec-index\n                         (if doc-string? arg-4-count arg-3-count)))\n              fn-style)\n            r-str-vec))\n      (or (= fn-style :arg1-pair)\n          (= fn-style :arg1)\n          (= fn-style :arg1-force-nl)\n          (= fn-style :arg1->))\n        (concat-no-nil\n          l-str-vec\n          pre-arg-1-style-vec\n          (fzprint* loptions (inc ind) arg-1-zloc)\n          pre-arg-2-style-vec\n          (fzprint-hang-one caller\n                            (if (= len 2) options loptions)\n                            arg-1-indent\n                            (+ indent ind)\n                            arg-2-zloc)\n          ; then either pair or remaining-seq\n          ; we don't do a full hanging here.\n          ; We wouldn't be here if len < 3\n          (if (= fn-style :arg1-pair)\n            (prepend-nl options\n                        (+ indent ind)\n                        (fzprint-pairs options\n                                       (+ indent ind)\n                                       (get-zloc-seq-right second-data)))\n            (fzprint-hang-remaining caller\n                                    (noarg1 options fn-style)\n                                    (+ indent ind)\n                                    ; force flow\n                                    (+ indent ind)\n                                    (get-zloc-seq-right second-data)\n                                    fn-style))\n          r-str-vec)\n      ; we know that (> len 2) if fn-style not= nil\n      (= fn-style :arg1-extend)\n        (let [zloc-seq-right-second (get-zloc-seq-right second-data)]\n          (cond (zvector? arg-2-zloc)\n                  ; This will put the second argument (a vector) on a different\n                  ; line than the function name.  No known uses for this code\n                  ; as of 7/20/19.  It does work with :respect-nl and has tests.\n                  (concat-no-nil\n                    l-str-vec\n                    pre-arg-1-style-vec\n                    (fzprint* loptions (+ indent ind) arg-1-zloc)\n                    pre-arg-2-style-vec\n                    (prepend-nl options\n                                (+ indent ind)\n                                (fzprint* loptions (+ indent ind) arg-2-zloc))\n                    (prepend-nl options\n                                (+ indent ind)\n                                (fzprint-extend options\n                                                (+ indent ind)\n                                                zloc-seq-right-second))\n                    r-str-vec)\n                :else (concat-no-nil\n                        l-str-vec\n                        pre-arg-1-style-vec\n                        (fzprint* loptions (inc ind) arg-1-zloc)\n                        pre-arg-2-style-vec\n                        (fzprint-hang-one caller\n                                          (if (= len 2) options loptions)\n                                          arg-1-indent\n                                          (+ indent ind)\n                                          arg-2-zloc)\n                        (prepend-nl options\n                                    (+ indent ind)\n                                    (fzprint-extend options\n                                                    (+ indent ind)\n                                                    zloc-seq-right-second))\n                        r-str-vec)))\n      ; Unspecified seq, might be a fn, might not.\n      ; If (first zloc) is a seq, we won't have an\n      ; arg-1-indent.  In that case, just flow it\n      ; out with remaining seq.  Since we already\n      ; know that it won't fit on one line.  If it\n      ; might be a fn, try hanging and flow and do\n      ; what we like better.  Note that default-indent\n      ; might be 1 here, which means that we are pretty\n      ; sure that the (zfirst zloc) isn't a function\n      ; and we aren't doing code.\n      ;\n      :else (concat-no-nil\n              l-str-vec\n              pre-arg-1-style-vec\n\t      ; Can't use arg-1-zloc here as the if test, because when\n\t      ; formatting structures, arg-1-zloc might well be nil!\n              (if (not (zero? len))\n                (fzprint* loptions (+ l-str-len ind) arg-1-zloc)\n                :noseq)\n\t      ; Same here -- can't use arg-1-zloc as if test!!\n              (if (not (zero? len))\n                (let [zloc-seq-right-first (get-zloc-seq-right first-data)]\n                  (if zloc-seq-right-first\n                    ; We have something else to format after arg-1-zloc\n                    (if (and arg-1-indent (not= fn-style :flow))\n                      (let [result (fzprint-hang-remaining\n                                     caller\n                                     (noarg1 options fn-style)\n                                     arg-1-indent\n                                     (+ indent ind indent-adj)\n                                     ; Can't do this, because\n                                     ; hang-remaining\n                                     ; doesn't take a seq\n                                     zloc-seq-right-first\n                                     ;(znthnext zloc 0)\n                                     fn-style)]\n                        (dbg-pr options\n                                \"fzprint-list*: r-str-vec:\" r-str-vec\n                                \"result:\" result)\n                        result)\n                      ; This might be a collection as the first thing, or it\n                      ; might be a :flow type.  Do different indents for these.\n                      (let [local-indent (if (= fn-style :flow)\n                                           (+ indent ind)\n                                           (+ default-indent ind indent-adj))]\n                        (concat-no-nil ;[[(str \"\\n\" (blanks local-indent)) :none\n                                       ;:indent]]\n                          (fzprint-flow-seq (noarg1 options fn-style)\n                                            local-indent\n                                            ;(nthnext (zmap identity\n                                            ;zloc) 1)\n                                            zloc-seq-right-first\n                                            :force-nl\n                                            :newline-first))))\n                    ; Nothing else after arg-1-zloc\n                    :noseq))\n                :noseq)\n              r-str-vec))))")


;;
;; Another couple of fidelity tests of two of our actual functions,
;; first with :parallel? false (the current default, but that could 
;; change)
;;

#?(:clj (def y1 (clojure.repl/source-fn 'zprint.zprint/fzprint-map-two-up)))
#?(:clj (expect (read-string y1)
                (read-string
                  (zprint-str y1 {:parallel? false, :parse-string? true}))))

#?(:clj (def y2 (source-fn 'zprint.zprint/partition-all-2-nc)))
#?(:clj (expect (trim-gensym-regex (read-string y2))
                (trim-gensym-regex
                  (read-string
                    (zprint-str y2 {:parallel? false, :parse-string? true})))))

#?(:clj (def y3 (source-fn 'zprint.zprint/fzprint-list*)))
#?(:clj (expect (trim-gensym-regex (read-string y3))
                (trim-gensym-regex
                  (read-string
                    (zprint-str y3 {:parallel? false, :parse-string? true})))))


;;
;; Do it in cljs too
;;

(expect (trim-gensym-regex (read-string fzprint-list*str))
        (trim-gensym-regex (read-string (zprint-str fzprint-list*str
                                                    {:parallel? false,
                                                     :parse-string? true}))))


;;
;; and again with :parallel? true
;;

#?(:clj (expect (read-string y1)
                (read-string
                  (zprint-str y1 {:parallel? true, :parse-string? true}))))

#?(:clj (expect (trim-gensym-regex (read-string y2))
                (trim-gensym-regex
                  (read-string
                    (zprint-str y2 {:parallel? true, :parse-string? true})))))

#?(:clj (expect (trim-gensym-regex (read-string y3))
                (trim-gensym-regex
                  (read-string
                    (zprint-str y3 {:parallel? true, :parse-string? true})))))

;;
;; and again with :parallel? true and {:style :justify}
;;

#?(:clj (expect (read-string y1)
                (read-string (zprint-str y1
                                         {:style :justified,
                                          :parallel? true,
                                          :parse-string? true}))))

#?(:clj (expect
          (trim-gensym-regex (read-string y2))
          (trim-gensym-regex (read-string (zprint-str y2
                                                      {:style :justified,
                                                       :parallel? true,
                                                       :parse-string? true})))))

#?(:clj (expect
          (trim-gensym-regex (read-string y3))
          (trim-gensym-regex (read-string (zprint-str y3
                                                      {:style :justified,
                                                       :parallel? true,
                                                       :parse-string? true})))))

(expect (trim-gensym-regex (read-string fzprint-list*str))
        (trim-gensym-regex (read-string (zprint-str fzprint-list*str
                                                    {:style :justified
						     :parallel? false,
                                                     :parse-string? true}))))


;;
;; Check out line count
;;

(expect 1 (line-count "abc"))

(expect 2 (line-count "abc\ndef"))

(expect 3 (line-count "abc\ndef\nghi"))


(def r {:aaaa :bbbb, :cccc '({:eeee :ffff, :aaaa :bbbb, :cccccccc :dddddddd})})

(def r-str (pr-str r))

;;
;; r should take 29 characters
;;{:aaaa :bbbb,
;; :cccc ({:aaaa :bbbb,
;;         :cccccccc :dddddddd,
;;         :eeee :ffff})}
;;
;; The comma at the end of the :dddd should be in
;; column 29.  So this should fit with a width of 29, but not 28.
;;
;; Check both sexpression printing and parsing into a zipper printing.
;;

(expect 29 (max-width (zprint-str r 30)))
(expect 4 (line-count (zprint-str r 30)))

(expect 29 (max-width (zprint-str r 29)))
(expect 4 (line-count (zprint-str r 29)))

(expect 25 (max-width (zprint-str r 28 {:map {:hang-adjust 0}})))
(expect 23 (max-width (zprint-str r 28 {:map {:hang-adjust -1}})))
(expect 5 (line-count (zprint-str r 28 {:map {:hang-adjust 0}})))

(expect 29 (max-width (zprint-str r-str 30 {:parse-string? true})))
(expect 4 (line-count (zprint-str r-str 30 {:parse-string? true})))

(expect 29 (max-width (zprint-str r-str 29 {:parse-string? true})))
(expect 4 (line-count (zprint-str r-str 29 {:parse-string? true})))

(expect 25
        (max-width
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust 0}})))
(expect 23
        (max-width
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust -1}})))
(expect 5
        (line-count
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust 0}})))

(def t {:cccc '({:dddd :eeee, :fffffffff :ggggggggggg}), :aaaa :bbbb})

(def t-str (pr-str t))

;;
;;{:aaaa :bbbb,
;; :cccc ({:dddd :eeee,
;;         :fffffffff
;;           :ggggggggggg})}
;;
;; This takes 26 characters, and shouldn't fit on a 25
;; character line.

(expect 26 (max-width (zprint-str t 26)))
(expect 4 (line-count (zprint-str t 26)))

(expect 22 (max-width (zprint-str t 25)))
(expect 5 (line-count (zprint-str t 25)))

(expect 26 (max-width (zprint-str t-str 26 {:parse-string? true})))
(expect 4 (line-count (zprint-str t-str 26 {:parse-string? true})))

(expect 22 (max-width (zprint-str t-str 25 {:parse-string? true})))
(expect 5 (line-count (zprint-str t-str 25 {:parse-string? true})))

;;
;; Test line-lengths
;;

(def ll
  [["{" :red :left] [":aaaa" :purple :element] [" " :none :whitespace]
   [":bbbb" :purple :element] [",\n        " :none :whitespace]
   [":ccccccccccc" :purple :element] ["\n          " :none :whitespace]
   [":ddddddddddddd" :purple :element] [",\n        " :none :whitespace]
   [":eeee" :purple :element] [" " :none :whitespace] [":ffff" :purple :element]
   ["}" :red :right]])

(expect [20 20 25 20] (line-lengths {} 7 ll))

(def u
  {:aaaa :bbbb, :cccc {:eeee :ffff, :aaaa :bbbb, :ccccccccccc :ddddddddddddd}})

(def u-str (pr-str u))

;;
;;{:aaaa :bbbb,
;; :cccc {:aaaa :bbbb,
;;        :ccccccccccc
;;          :ddddddddddddd,
;;        :eeee :ffff}}
;;
;; This takes 25 characters and shouldn't fit ona  24 character line
;;

(expect 25 (max-width (zprint-str u 25)))
(expect 5 (line-count (zprint-str u 25)))

(expect 21 (max-width (zprint-str u 24)))
(expect 6 (line-count (zprint-str u 24)))

(expect 25 (max-width (zprint-str u-str 25 {:parse-string? true})))
(expect 5 (line-count (zprint-str u-str 25 {:parse-string? true})))

(expect 21 (max-width (zprint-str u-str 24 {:parse-string? true})))
(expect 6 (line-count (zprint-str u-str 24 {:parse-string? true})))

(def d
  '(+ :aaaaaaaaaa
      (if :bbbbbbbbbb
        :cccccccccc
        (list :ddddddddd
              :eeeeeeeeee :ffffffffff
              :gggggggggg :hhhhhhhhhh
              :iiiiiiiiii :jjjjjjjjjj
              :kkkkkkkkkk :llllllllll
              :mmmmmmmmmm :nnnnnnnnnn
              :oooooooooo :pppppppppp))))

(def d-str (pr-str d))

(expect 16 (line-count (zprint-str d 80 {:list {:constant-pair? nil}})))
(expect
  16
  (line-count
    (zprint-str d-str 80 {:parse-string? true, :list {:constant-pair? nil}})))

(def e {:aaaa :bbbb, :cccc :dddd, :eeee :ffff})
(def e-str (pr-str e))

(expect 39 (max-width (zprint-str e 39)))
(expect 1 (line-count (zprint-str e 39)))
(expect 13 (max-width (zprint-str e 38)))
(expect 3 (line-count (zprint-str e 38)))

(expect 39 (max-width (zprint-str e-str 39 {:parse-string? true})))
(expect 1 (line-count (zprint-str e-str 39 {:parse-string? true})))
(expect 13 (max-width (zprint-str e-str 38 {:parse-string? true})))
(expect 3 (line-count (zprint-str e-str 38 {:parse-string? true})))

(expect {:a nil} (read-string (zprint-str {:a nil})))

;;
;; Check out zero argument functions
;;

(expect 3 (max-width (zprint-str '(+))))

;;
;; Printing atoms
;;

(def atm (atom {:a :b}))

(expect (str "#<Atom " @atm ">")
        (let [result (zprint-str atm)]
          (clojure.string/replace result (re-find #"@[a-fA-F0-9]+" result) "")))

;;
;; Atom printing -- prints the contents of the atom
;; in a pretty way too.
;;

(def atm2 (atom u))

(expect 5 (line-count (zprint-str atm2 48)))
(expect 1 (line-count (pr-str atm2)))

;;
;; Byte arrays
;;

#?(:clj (def ba (byte-array [1 2 3 4 -128])))

#?(:clj (expect "[01 02 03 04 80]" (zprint-str ba {:array {:hex? true}})))

#?(:clj (def ba1
          (byte-array [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22
                       23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41
                       42 43 44 45 46 47 48 49 50]))
   :cljs (def ba1
           (int-array [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22
                       23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41
                       42 43 44 45 46 47 48 49 50])))

#?(:clj (expect 51 (max-width (zprint-str ba1 51 {:array {:hex? true}}))))
#?(:clj (expect 3 (line-count (zprint-str ba1 51 {:array {:hex? true}}))))



;;
;; # Rightmost tests
;;

(def atm1 (atom [:aaaaaaaaa :bbbbbbbbb {:a :b}]))
(def atm1-length (count (zprint-str atm1)))
(expect 1 (line-count (zprint-str atm1 (inc atm1-length))))
(expect 1 (line-count (zprint-str atm1 atm1-length)))
(expect 2 (line-count (zprint-str atm1 (dec atm1-length))))

(def map1 {:abc :def, :hij :klm, "key" "value", :x :y})
(expect 1 (line-count (zprint-str map1 45)))
(expect 1 (line-count (zprint-str map1 44)))
(expect 4 (line-count (zprint-str map1 43)))
;
; Test zipper version
;
(def maps (str map1))
(expect 1 (line-count (zprint-str maps 45 {:parse-string? true})))
(expect 1 (line-count (zprint-str maps 44 {:parse-string? true})))
(expect 4 (line-count (zprint-str maps 43 {:parse-string? true})))


(def vec1 [:asdf :jklx 1 2 3 4 5 "abc" "def"])
(expect 1 (line-count (zprint-str vec1 36)))
(expect 1 (line-count (zprint-str vec1 35)))
(expect 2 (line-count (zprint-str vec1 34)))
;
; Test zipper version
;
(def vecs (str vec1))
(expect 1 (line-count (zprint-str vecs 36 {:parse-string? true})))
(expect 1 (line-count (zprint-str vecs 35 {:parse-string? true})))
(expect 2 (line-count (zprint-str vecs 34 {:parse-string? true})))

(def lis1 '(asdf jklsemi :aaa :bbb :ccc 1 2 3 4 5 "hello"))
(expect 1 (line-count (zprint-str lis1 48)))
(expect 1 (line-count (zprint-str lis1 47)))
(expect 10 (line-count (zprint-str lis1 46 {:list {:constant-pair? nil}})))
(expect 6 (line-count (zprint-str lis1 46 {:list {:constant-pair? true}})))
;
; Test zipper version
;
(def liss (str lis1))
(expect 1 (line-count (zprint-str liss 48 {:parse-string? true})))
(expect 1 (line-count (zprint-str liss 47 {:parse-string? true})))
(expect
  10
  (line-count
    (zprint-str liss 46 {:parse-string? true, :list {:constant-pair? nil}})))
(expect
  6
  (line-count
    (zprint-str liss 46 {:parse-string? true, :list {:constant-pair? true}})))

(def lis2 '(aaaa bbbb cccc (dddd eeee ffff)))
(expect 1 (line-count (zprint-str lis2 34)))
(expect 1 (line-count (zprint-str lis2 33)))
(expect 3 (line-count (zprint-str lis2 32)))

(def set1 #{:aaa :bbb :ccc :ddd "stuff" "bother" 1 2 3 4 5})
(expect 1 (line-count (zprint-str set1 50)))
(expect 1 (line-count (zprint-str set1 49)))
(expect 2 (line-count (zprint-str set1 48)))

(def set2 #{{:a :b} {:c :d} {:e :f} {:g :h}})
(expect 1 (line-count (zprint-str set2 35)))
(expect 1 (line-count (zprint-str set2 34)))
(expect 2 (line-count (zprint-str set2 33)))
;
; Test zipper version
;
(def sets (str set2))
(expect 1 (line-count (zprint-str sets 35 {:parse-string? true})))
(expect 1 (line-count (zprint-str sets 34 {:parse-string? true})))
(expect 2 (line-count (zprint-str sets 33 {:parse-string? true})))

(def rs (make-record :reallylongleft :r))
(expect 1 (line-count (zprint-str rs 53)))
(expect 1 (line-count (zprint-str rs 52)))
(expect 1 (line-count (zprint-str rs 51)))
(expect 2 (line-count (zprint-str rs 50)))
(expect 2 (line-count (zprint-str rs 49)))

;;
;; Lest these look like "of course" tests, remember that
;; the zprint functions can parse a string into a zipper and then
;; print it, so these are all getting parsed and then handled by
;; the zipper code.
;;

(expect "()" (zprint-str "()" {:parse-string? true}))
(expect "[]" (zprint-str "[]" {:parse-string? true}))
(expect "{}" (zprint-str "{}" {:parse-string? true}))
(expect "#()" (zprint-str "#()" {:parse-string? true}))
(expect "#{}" (zprint-str "#{}" {:parse-string? true}))
(expect "#_()" (zprint-str "#_()" {:parse-string? true}))
(expect "#_[]" (zprint-str "#_[]" {:parse-string? true}))
(expect "~{}" (zprint-str "~{}" {:parse-string? true}))
(expect "^{:a :b} stuff" (zprint-str "^{:a :b} stuff" {:parse-string? true}))

;;
;; # Constant pairing
;;

(expect
  "(if (and\n      :abcd :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))"
  (zprint-str iftest
              19
              {:list {:constant-pair-min 2, :constant-pair? true},
               :tuning {:general-hang-adjust 0}}))

(expect
  "(if (and :abcd\n         :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))"
  (zprint-str iftest 19 {:list {:constant-pair-min 4, :constant-pair? true}}))

(expect
  6
  (line-count
    (zprint-str
      "(println :aaaa :bbbb :cccc :dddd :eeee :ffff 
                :gggg :hhhh :iiii :jjjj :kkkk)"
      50
      {:parse-string? true, :list {:constant-pair? true}})))

(expect
  11
  (line-count
    (zprint-str
      "(println :aaaa :bbbb :cccc :dddd :eeee :ffff 
                :gggg :hhhh :iiii :jjjj :kkkk)"
      50
      {:parse-string? true, :list {:constant-pair? nil}})))

(expect "{:a :b, :c nil}" (zprint-str "{:a :b :c nil}" {:parse-string? true}))

;;
;; # Line Lengths
;;

(expect [3 0] (zprint.zprint/line-lengths {} 3 [["; stuff" :none :comment]]))

(expect
  [14 30 20]
  (zprint.zprint/line-lengths
    {}
    12
    [[":c" :magenta :element] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect
  [2 30 20]
  (zprint.zprint/line-lengths
    {}
    0
    [[":c" :magenta :element] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect
  [12 30 20]
  (zprint.zprint/line-lengths
    {}
    12
    [[";" :green :comment] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

; This change came when we started to correctly recognize functions
; even though they were preceded by comments and/or newlines.
(expect "(;a\n list :b\n      :c\n      ;def\n)"
        ;"(;a\n list\n :b\n :c\n ;def\n)"
        (zprint-str "(;a\nlist\n:b\n:c ;def\n)"
                    {:parse-string? true, :comment {:inline? false}}))

;;
;; # Comments at the end of sequences
;;


;(list a
;      b ;def
;  )

(expect "(list a\n      b ;def\n)"
        (zprint-str "(list a b ;def\n)"
                    {:parse-string? true, :comment {:inline? true}}))

;(list a
;      b
;      ;def
;  )

(expect "(list a\n      b\n      ;def\n)"
        (zprint-str "(list a b ;def\n)"
                    {:parse-string? true, :comment {:inline? false}}))
;[list a b ;def
;]

(expect "[list a b ;def\n]"
        (zprint-str "[list a b ;def\n]"
                    {:parse-string? true, :comment {:inline? true}}))

;[list a b ;def
;]

(expect "[list a b\n ;def\n]"
        (zprint-str "[list a b ;def\n]"
                    {:parse-string? true, :comment {:inline? false}}))

;{a b, ;def
; }

(expect "{a b ;def\n}"
        (zprint-str "{ a b ;def\n}"
                    {:parse-string? true, :comment {:inline? true}}))

;{a b,
; ;def
; }
(expect "{a b\n ;def\n}"
        (zprint-str "{ a b ;def\n}"
                    {:parse-string? true, :comment {:inline? false}}))


(expect [6 1 8 1 9 1 11]
        (zprint.zprint/line-lengths
          {}
          0
          [["(" :green :left] ["cond" :blue :element] [" " :none :whitespace]
           ["; one" :green :comment] [" " :none :whitespace]
           ["; two   " :green :comment] [" " :none :whitespace]
           [":stuff" :magenta :element] [" " :none :whitespace]
           ["; middle" :green :comment] [" " :none :whitespace]
           ["; second middle" :green :comment] [" " :none :whitespace]
           [":bother" :magenta :element] [" " :none :whitespace]
           ["; three" :green :comment] [" " :none :whitespace]
           ["; four" :green :comment] [" " :none :whitespace]
           [":else" :magenta :element] [" " :none :whitespace]
           ["nil" :yellow :element] [")" :green :right]]))

(expect
  [1 1 1 1 16]
  (zprint.zprint/line-lengths
    {}
    0
    [["[" :purple :left] [";a" :green :comment] [" " :none :whitespace]
     [";" :green :comment] [" " :none :whitespace] [";b" :green :comment]
     [" " :none :whitespace] [";c" :green :comment] ["\n " :none :whitespace]
     ["this" :black :element] [" " :none :whitespace] ["is" :black :element]
     [" " :none :whitespace] ["a" :black :element] [" " :none :whitespace]
     ["test" :blue :element] ["]" :purple :right]]))


;;
;; # Comments handling
;;

(expect "[;a\n ;\n ;b\n ;c\n this is a test]"
        (zprint-str "[;a\n;\n;b\n;c\nthis is a test]" {:parse-string? true}))

(expect
  "(defn testfn8\n  \"Test two comment lines after a cond test.\"\n  [x]\n  (cond\n    ; one\n    ; two\n    :stuff\n      ; middle\n      ; second middle\n      :bother\n    ; three\n    ; four\n    :else nil))"
  (zprint-str x8
                 {:parse-string? true :pair-fn {:hang? nil}, :comment {:inline? false}}))

(def zctest3str
"(defn zctest3
  \"Test comment forcing things\"
  [x]
  (cond (and (list ;
               (identity \"stuff\")
               \"bother\"))
          x
        :else (or :a :b :c)))")

(def zctest4str
"(defn zctest4
  \"Test comment forcing things\"
  [x]
  (cond (and (list :c (identity \"stuff\") \"bother\")) x
        :else (or :a :b :c)))")

(def zctest5str
"(defn zctest5
  \"Model defn issue.\"
  [x]
  (let [abade :b
        ceered (let [b :d]
                 (if (:a x)
                   ; this is a very long comment that should force things way to the left
                   (assoc b :a :c)))]
    (list :a
          (with-meta name x)
          ; a short comment that might be long if we wanted it to be
          :c)))")


(expect
  "(defn zctest4\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and (list :c\n               (identity \"stuff\")\n               \"bother\"))\n      x\n    :else (or :a :b :c)))"
  (zprint-str zprint.zprint-test/zctest4str 40 {:parse-string? true :pair-fn {:hang? nil}}))

; When :respect-nl? was added for lists, this changed because if you
; have a newline following the "list", then you don't want to hang the
; rest of the things because it looks bad.  And so comments got the same
; treatment.
(expect
  "(defn zctest3\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and (list ;\n           (identity \"stuff\")\n           \"bother\"))\n      x\n    :else (or :a :b :c)))"
    ;  "(defn zctest3\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and
    ;  (list ;\n               (identity \"stuff\")\n               \"bother\"))\n
    ;       x\n    :else (or :a :b :c)))"
    (zprint-str zprint.zprint-test/zctest3str 40 {:parse-string? true :pair-fn {:hang? nil}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let\n    [abade :b\n     ceered\n       (let [b :d]\n         (if (:a x)\n           ; this is a very long comment that should force things way\n           ; to the left\n           (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-str zprint.zprint-test/zctest5str
                 70
                 {:parse-string? true :comment {:count? true, :wrap? true}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force\n                   ; things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-str zprint.zprint-test/zctest5str
                 70
                 {:parse-string? true :comment {:count? nil, :wrap? true}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-str zprint.zprint-test/zctest5str
                 70
                 {:parse-string? true :comment {:wrap? nil, :count? nil}}))

;;
;; # wrapping inline comments, and how they are handled the second time.
;;
;; Issue #67
;;

(expect
  "(def x\n  zprint.zfns/zstart\n  sfirst\n  zprint.zfns/zanonfn?\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
  (zprint-str
    "\n(def x\n  zprint.zfns/zstart sfirst\n  zprint.zfns/zanonfn? (constantly false) ; this only works because lists, anon-fn's, etc. are checked before this is used.\n  zprint.zfns/zfn-obj? fn?)"
    {:parse-string? true}))

(expect
  "(def x\n  zprint.zfns/zstart\n  sfirst\n  zprint.zfns/zanonfn?\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
  (zprint-str
    "(def x\n  zprint.zfns/zstart\n  sfirst\n  zprint.zfns/zanonfn?\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
    {:parse-string? true}))

;;
;; # Tab Expansion
;;

(expect "this is a tab   test to see if it works"
        (zprint.zprint/expand-tabs 8 "this is a tab\ttest to see if it works"))

(expect "this is a taba  test to see if it works"
        (zprint.zprint/expand-tabs 8 "this is a taba\ttest to see if it works"))

(expect "this is a tababc        test to see if it works"
        (zprint.zprint/expand-tabs 8
                                   "this is a tababc\ttest to see if it works"))

(expect "this is a tabab test to see if it works"
        (zprint.zprint/expand-tabs 8
                                   "this is a tabab\ttest to see if it works"))

;;
;; # File Handling
;;

(expect ["\n\n" ";;stuff\n" "(list :a :b)" "\n\n"]
        (zprint.zutil/zmap-all
          (partial zprint-str-internal {:zipper? true, :color? false})
          (edn* (p/parse-string-all "\n\n;;stuff\n(list :a :b)\n\n"))))

;;
;; #Deref
;;

(expect
  "@(list this\n       is\n       a\n       test\n       this\n       is\n       only\n       a\n       test)"
  (zprint-str "@(list this is a test this is only a test)"
              30
              {:parse-string? true}))

;;
;; # Reader Conditionals
;;

(expect "#?(:clj (list :a :b)\n   :cljs (list :c :d))"
        (zprint-str "#?(:clj (list :a :b) :cljs (list :c :d))"
                    30
                    {:parse-string? true}))


(expect "#?@(:clj (list :a :b)\n    :cljs (list :c :d))"
        (zprint-str "#?@(:clj (list :a :b) :cljs (list :c :d))"
                    30
                    {:parse-string? true}))

;;
;; # Var
;;

(expect "#'(list this\n        is\n        :a\n        \"test\")"
        (zprint-str "#'(list this is :a \"test\")" 20 {:parse-string? true}))

;;
;; # Ordered Options in maps
;;

(expect "{:a :test, :second :more-value, :should-be-first :value, :this :is}"
        (zprint-str
          {:this :is, :a :test, :should-be-first :value, :second :more-value}))

(expect "{:should-be-first :value, :second :more-value, :a :test, :this :is}"
        (zprint-str
          {:this :is, :a :test, :should-be-first :value, :second :more-value}
          {:map {:key-order [:should-be-first :second]}}))

;;
;; # Ordered Options in reader-conditionals
;;

(expect "#?(:clj (list :c :d) :cljs (list :a :b))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond {:force-nl? false, :sort? true}}))

(expect "#?(:cljs (list :a :b) :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond {:force-nl? false, :sort? nil}}))

(expect
  "#?(:cljs (list :a :b) :clj (list :c :d))"
  (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
              {:parse-string? true,
               :reader-cond
                 {:force-nl? false, :sort? nil, :key-order [:clj :cljs]}}))

(expect "#?(:cljs (list :a :b)\n   :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond
                       {:force-nl? true, :sort? nil, :key-order [:clj :cljs]}}))

(expect
  "#?(:clj (list :c :d) :cljs (list :a :b))"
  (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
              {:parse-string? true,
               :reader-cond
                 {:force-nl? false, :sort? true, :key-order [:clj :cljs]}}))

;;
;; # Rightmost in reader conditionals
;;

(expect "#?(:cljs (list :a :b)\n   :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    40
                    {:parse-string? true}))

(expect "#?(:cljs (list :a :b) :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    41
                    {:reader-cond {:force-nl? false}, :parse-string? true}))

;;
;; # Reader Literals
;;

(expect "#stuff/bother\n (list :this\n       \"is\"\n       a\n       :test)"
        (zprint-str "#stuff/bother (list :this \"is\" a :test)"
                    20
                    {:parse-string? true}))

(expect "#stuff/bother (list :this \"is\" a :test)"
        (zprint-str "#stuff/bother (list :this \"is\" a :test)"
                    {:parse-string? true}))

;;
;; # The third cond clause in fzprint-two-up
;;

(expect
  "(let\n  [:a\n     ;x\n     ;y\n     :b\n   :c :d]\n  (println\n    :a))"
  (zprint-str "(let [:a ;x\n;y\n :b :c :d] (println :a))"
              10
              {:parse-string? true, :comment {:inline? false}}))

;;
;; # Promise, Future, Delay
;;

(def dd (delay [:d :e]))

(expect "#<Delay pending>"
        (clojure.string/replace (zprint-str dd) #"\@[0-9a-f]*" ""))

(def ee (delay [:d :e]))
(def ff @ee)

(expect "#<Delay [:d :e]>"
        (clojure.string/replace (zprint-str ee) #"\@[0-9a-f]*" ""))

#?(:clj (def pp (promise)))

#?(:clj (expect "#<Promise not-delivered>"
        (clojure.string/replace (zprint-str pp) #"\@[0-9a-f]*" "")))

#?(:clj (def qq (promise)))
#?(:clj (deliver qq [:a :b]))

#?(:clj (expect "#<Promise [:a :b]>"
        (clojure.string/replace (zprint-str qq) #"\@[0-9a-f]*" "")))

#?(:clj (def ff (future [:f :g])))

#?(:clj (Thread/sleep 500))

#?(:clj (expect "#<Future [:f :g]>"
        (clojure.string/replace (zprint-str ff) #"\@[0-9a-f]*" "")))

;;
;; # Agents
;;

#?(:clj (def ag (agent [:a :b])))

#?(:clj (expect "#<Agent [:a :b]>"
                (clojure.string/replace (zprint-str ag) #"\@[0-9a-f]*" "")))

#?(:clj (def agf (agent [:c :d])))
#?(:clj (send agf + 5))

#?(:clj (expect "#<Agent FAILED [:c :d]>"
                (clojure.string/replace (zprint-str agf) #"\@[0-9a-f]*" "")))

;;
;; # Sorting maps in code
;;

; Regular sort

(expect "{:a :b, :g :h, :j :k}"
        (zprint-str "{:g :h :j :k :a :b}" {:parse-string? true}))

; Still sorts in a list, not code 

(expect "({:a :b, :g :h, :j :k})"
        (zprint-str "({:g :h :j :k :a :b})" {:parse-string? true}))

; Doesn't sort in code (where stuff might be a function)

(expect "(stuff {:g :h, :j :k, :a :b})"
        (zprint-str "(stuff {:g :h :j :k :a :b})" {:parse-string? true}))

; Will sort in code if you tell it to

(expect "(stuff {:a :b, :g :h, :j :k})"
        (zprint-str "(stuff {:g :h :j :k :a :b})"
                    {:parse-string? true, :map {:sort-in-code? true}}))

;;
;; # Sorting sets in code
;;

; Regular sort

(expect "#{:a :b :g :h :j :k}"
        (zprint-str "#{:g :h :j :k :a :b}" {:parse-string? true}))

; Still sorts in a list, not code 

(expect "(#{:a :b :g :h :j :k})"
        (zprint-str "(#{:g :h :j :k :a :b})" {:parse-string? true}))

; Doesn't sort in code (where stuff might be a function)

(expect "(stuff #{:g :h :j :k :a :b})"
        (zprint-str "(stuff #{:g :h :j :k :a :b})" {:parse-string? true}))

; Will sort in code if you tell it to

(expect "(stuff #{:a :b :g :h :j :k})"
        (zprint-str "(stuff #{:g :h :j :k :a :b})"
                    {:parse-string? true, :set {:sort-in-code? true}}))



; contains-nil?

(expect nil (contains-nil? [:a :b :c :d]))
(expect true (contains-nil? [:a nil :b :c :d]))
(expect true(contains-nil? [:a :b nil '() :c :d]))

(def e2 {:aaaa :bbbb, :ccc :ddddd, :ee :ffffff})

(expect "{:aaaa :bbbb,\n :ccc  :ddddd,\n :ee   :ffffff}"
        (zprint-str e2 38 {:map {:justify? true}}))

(expect "{:aaaa :bbbb, :ccc :ddddd, :ee :ffffff}"
        (zprint-str e2 39 {:map {:justify? true}}))

;;
;; :wrap? for vectors
;;

(def vba1 (apply vector ba1))

(expect 48 (max-width (zprint-str vba1 48)))
(expect 3 (line-count (zprint-str vba1 48)))

(expect 4 (max-width (zprint-str vba1 48 {:vector {:wrap? nil}})))
(expect 50 (line-count (zprint-str vba1 48 {:vector {:wrap? nil}})))

;;
;; :wrap? for sets
;;

(def svba1 (set vba1))

(expect 46 (max-width (zprint-str svba1 48 {:set {:sort? true}})))
(expect 4 (line-count (zprint-str svba1 48 {:set {:sort? false}})))

(expect 5 (max-width (zprint-str svba1 48 {:set {:wrap? nil}})))
(expect 50 (line-count (zprint-str svba1 48 {:set {:wrap? nil}})))

;;
;; :wrap? for arrays
;;

(expect 48 (max-width (zprint-str ba1 48)))
(expect 3 (line-count (zprint-str ba1 48)))

(expect 4 (max-width (zprint-str ba1 48 {:array {:wrap? nil}})))
(expect 50 (line-count (zprint-str ba1 48 {:array {:wrap? nil}})))

;;
;; # indent-arg and indent-body
;;

(defn zctest5a
  "Test indents."
  [x]
  (let [abade :b
        ceered (let [b :d]
                 (if (:a x)
                   ; this is a slightly long comment
                   ; a second comment line
                   (assoc b :a :c)))]
    (list :a
          (with-meta name x)
          (vector :thisisalongkeyword :anotherlongkeyword
                  :ashorterkeyword :reallyshort)
          ; a short comment that might be long
          :c)))

(def zctest5astr
"(defn zctest5a
  \"Test indents.\"
  [x]
  (let [abade :b
        ceered (let [b :d]
                 (if (:a x)
                   ; this is a slightly long comment
                   ; a second comment line
                   (assoc b :a :c)))]
    (list :a
          (with-meta name x)
          (vector :thisisalongkeyword :anotherlongkeyword
                  :ashorterkeyword :reallyshort)
          ; a short comment that might be long
          :c)))")


#?(:clj
     (expect
       "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n      :a\n      (with-meta name x)\n      (vector\n        :thisisalongkeyword :anotherlongkeyword\n        :ashorterkeyword :reallyshort)\n      ; a short comment that might be long\n      :c)))"
       (zprint-fn-str zprint.zprint-test/zctest5a {:list {:hang? false}})))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n      :a\n      (with-meta name x)\n      (vector\n        :thisisalongkeyword :anotherlongkeyword\n        :ashorterkeyword :reallyshort)\n      ; a short comment that might be long\n      :c)))"
  (zprint-str zprint.zprint-test/zctest5astr
              {:parse-string? true, :list {:hang? false}}))

#?(:clj
     (expect
       "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
       (zprint-fn-str zprint.zprint-test/zctest5a
                      {:list {:hang? false, :indent-arg 1}})))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
  (zprint-str zprint.zprint-test/zctest5astr
              {:parse-string? true, :list {:hang? false, :indent-arg 1}}))

#?(:clj
(expect
  "(defn zctest5a\n \"Test indents.\"\n [x]\n (let [abade :b\n       ceered (let [b :d]\n               (if (:a x)\n                ; this is a slightly long comment\n                ; a second comment line\n                (assoc b :a :c)))]\n  (list\n   :a\n   (with-meta name x)\n   (vector\n    :thisisalongkeyword :anotherlongkeyword\n    :ashorterkeyword :reallyshort)\n   ; a short comment that might be long\n   :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a
                 {:list {:hang? false, :indent-arg 1, :indent 1}})))

(expect
  "(defn zctest5a\n \"Test indents.\"\n [x]\n (let [abade :b\n       ceered (let [b :d]\n               (if (:a x)\n                ; this is a slightly long comment\n                ; a second comment line\n                (assoc b :a :c)))]\n  (list\n   :a\n   (with-meta name x)\n   (vector\n    :thisisalongkeyword :anotherlongkeyword\n    :ashorterkeyword :reallyshort)\n   ; a short comment that might be long\n   :c)))"
  (zprint-str zprint.zprint-test/zctest5astr
              {:parse-string? true,
               :list {:hang? false, :indent-arg 1, :indent 1}}))

#?(:clj
     (expect
       "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
       (zprint-fn-str zprint.zprint-test/zctest5a
                      {:list {:hang? false, :indent-arg 1, :indent 2}})))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
  (zprint-str zprint.zprint-test/zctest5astr
              {:parse-string? true,
               :list {:hang? false, :indent-arg 1, :indent 2}}))

;;
;; # key-ignore, key-ignore-silent
;;

;;
;; ## Basic routines
;;

(def ignore-m {:a :b, :c {:e {:f :g}, :h :i}})

(expect {:a :b, :c {:e {:f :g}, :h :zprint-ignored}}
        (map-ignore :map {:map {:key-ignore [[:c :h]]}} ignore-m))

(expect {:a :b, :c {:e {:f :g}}}
        (map-ignore :map {:map {:key-ignore-silent [[:c :h]]}} ignore-m))

(expect {:a :b, :c {:e {:f :g}}}
        (map-ignore :map {:map {:key-ignore-silent [:f [:c :h]]}} ignore-m))

(expect
  {:a :b}
  (map-ignore :map {:map {:key-ignore-silent [[:c :h] [:c :e]]}} ignore-m))

(expect {:a :b, :c {:e :zprint-ignored, :h :zprint-ignored}}
        (map-ignore :map {:map {:key-ignore [[:c :h] [:c :e]]}} ignore-m))

(expect "{:a :b, :c {:e :zprint-ignored, :h :zprint-ignored}}"
        (zprint-str ignore-m {:map {:key-ignore [[:c :h] [:c :e]]}}))

(expect "{:a :zprint-ignored, :c {:e :zprint-ignored, :h :i}}"
        (zprint-str ignore-m {:map {:key-ignore [[:c :e] :a]}}))

(expect "{:c {:h :i}}"
        (zprint-str ignore-m {:map {:key-ignore-silent [:a [:c :e :f]]}}))

;;
;; Test fix for issue #1
;;

(expect
  "(fn [arg1 arg2 arg3] [:first-keyword-in-vector\n                      :some-very-long-keyword-that-makes-this-wrap\n                      :next-keyword])"
  (zprint-str
    "(fn [arg1 arg2 arg3] [:first-keyword-in-vector :some-very-long-keyword-that-makes-this-wrap :next-keyword])"
    {:parse-string? true}))

;;
;; no-arg? test
;;

(expect
  "(-> context\n    (assoc ::error (throwable->ex-info t\n                                       execution-id\n                                       interceptor\n                                       :error)\n           ::stuff (assoc a-map\n                     :this-is-a-key :this-is-a-value))\n    (update-in [::suppressed] conj ex))"
  (zprint-str
    " (-> context (assoc ::error (throwable->ex-info t execution-id interceptor :error) ::stuff (assoc a-map :this-is-a-key :this-is-a-value)) (update-in [::suppressed] conj ex))"
    55
    {:parse-string? true}))

;;
;; Test equal size hang and flow should hang, particularly issue in
;; fzprint-hang-remaining where it was messing that up unless hang-expand was 4.0
;; instead of the 2.0.  This *should* hang up next to the do, not flow under the do.
;;

(expect
  "(do (afunction :stuff t\n               :reallybother (:rejection-type ex)\n               :downtrodden-id bits-id)\n    (-> pretext\n        (assoc ::error (catchable->my-info u\n                                           pretext-id\n                                           sceptor\n                                           :error))\n        (update-in [::expressed] con ex)))"
  (zprint-str
    "(do (afunction :stuff t :reallybother (:rejection-type ex) :downtrodden-id bits-id) (-> pretext (assoc ::error (catchable->my-info u pretext-id sceptor :error)) (update-in [::expressed] con ex)))"
    60
    {:parse-string? true}))

;;
;; Test for the bug with not calculating the size of the left part of a pair
;; correctly.  Shows up with commas in maps that fit on one line as the
;; left part of a pair.
;;

(expect
  "(defn ctest20\n  ([query-string body]\n   (let [aabcdefghijklmnopqrstuvwxyzabcdefghijkllmnpqr @(http-get query-string\n                                                                  {:body body})]\n     nil)))"
  (zprint-str
    "(defn ctest20\n ([query-string body]\n   (let [aabcdefghijklmnopqrstuvwxyzabcdefghijkllmnpqr @(http-get query-string {:body body})]\n    \n   nil)))"
    {:parse-string? true}))

;;
;; Test new :nl-separator? capability
;;

(def mx
  {:djlsfdjfkld {:jlsdfjsdlk :kjsldkfjdslk,
                 :jsldfjdlsd :ksdfjldsjkf,
                 :jslfjdsfkl :jslkdfjsld},
   :jsdlfjskdlfjldsk :jlksdfdlkfsdj,
   :lsafjsdlfj :ljsdfjsdlk})

(def my
  {:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,
                      :sjlkfjdlf :sdlkfjdsl,
                      :slkfjdlskf :slfjdsfkldsljfk},
   :djlsfdjfkld {:jlsdfjsdlk :kjsldkfjdslk,
                 :jsldfjdlsd :ksdfjldsjkf,
                 :jslfjdsfkl :jslkdfjsld},
   :jsdlfjskdlfjldsk :jlksdfdlkfsdj,
   :lsafjsdlfj :ljsdfjsdlk})

;
; This is the test for where maps only get an extra new-line when the
; right hand part of the pair gets a :flow, and you don't get an extra
; new line when the right hand part gets a hang (i.e. a multi-line hang).
;

(expect
  "{:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,\n                    :sjlkfjdlf :sdlkfjdsl,\n                    :slkfjdlskf :slfjdsfkldsljfk},\n :djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str my
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? true}}))

;
; This is the test for when any multi-line pair gets an extra new-line
; with :nl-separator? true, not just the ones that did flow.
;
#_(expect
    "{:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,\n                    :sjlkfjdlf :sdlkfjdsl,\n                    :slkfjdlskf :slfjdsfkldsljfk},\n \n :djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n \n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
    (zprint-str my
                {:map {:hang? true,
                       :force-nl? false,
                       :flow? false,
                       :indent 0,
                       :nl-separator? true}}))

(expect
  "{:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,\n                    :sjlkfjdlf :sdlkfjdsl,\n                    :slkfjdlskf :slfjdsfkldsljfk},\n :djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str my
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? false}}))

(expect
  "{:djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str mx
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? false}}))

(expect
  "{:djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str mx
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? true}}))

(expect
  "{:djlsfdjfkld\n {:jlsdfjsdlk\n  :kjsldkfjdslk,\n\n  :jsldfjdlsd\n  :ksdfjldsjkf,\n\n  :jslfjdsfkl\n  :jslkdfjsld},\n\n :jsdlfjskdlfjldsk\n :jlksdfdlkfsdj,\n\n :lsafjsdlfj\n :ljsdfjsdlk}"
  (zprint-str mx
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? true,
                     :indent 0,
                     :nl-separator? true}}))

;;
;; # :flow? tests
;;


(expect "(cond a b c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:flow? false},
                     :remove {:fn-gt2-force-nl #{:pair-fn}}}))

(expect "(cond a b\n      c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:flow? false}}))


; Note that this also tests that :flow? overrides the indent checks in
; fzprint-two-up, which would otherwise prevent the flow because the keys
; are only 1 character long.

(expect "(cond a\n        b\n      c\n        d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:flow? true}}))

(expect "{:abc :def, :ghi :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:flow? false}}))

(expect "{:abc\n   :def,\n :ghi\n   :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:flow? true}}))

(expect "(let [a b c d e f] (list a b c d e f))"
        (zprint-str "(let [a b c d e f] (list a b c d e f))"
                    {:parse-string? true, :binding {:flow? false}}))

(expect
  "(let [a\n        b\n      c\n        d\n      e\n        f]\n  (list a b c d e f))"
  (zprint-str "(let [a b c d e f] (list a b c d e f))"
              {:parse-string? true, :binding {:flow? true}}))

;!zprint {:format :skip}
; Something strange going on with source-fn and :clj!
#?(:clj 
(deftype Typetest [cnt _meta]
          clojure.lang.IHashEq
            (hasheq [this] (list this))
          clojure.lang.Counted
            (count [_] cnt)
          clojure.lang.IMeta
            (meta [_] _meta)))

(def Typeteststr
  "(deftype Typetest [cnt _meta]
  clojure.lang.IHashEq
    (hasheq [this] (list this))
  clojure.lang.Counted
    (count [_] cnt)
  clojure.lang.IMeta
    (meta [_] _meta))")

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? false}})))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr
              {:parse-string? true, :extend {:flow? false}}))

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? true}})))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr {:parse-string? true :extend {:flow? true}}))

;;
;; # :force-nl? tests
;;


(expect "(cond a b c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:force-nl? false},
                     :remove {:fn-gt2-force-nl #{:pair-fn}}}))

(expect "(cond a b\n      c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:force-nl? false}}))

(expect "(cond a b\n      c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:force-nl? true}}))

(expect "{:abc :def, :ghi :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:force-nl? false}}))

(expect "{:abc :def,\n :ghi :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:force-nl? true}}))

(expect "(let [a b c d e f] (list a b c d e f))"
        (zprint-str "(let [a b c d e f] (list a b c d e f))"
                    {:parse-string? true, :binding {:force-nl? false}}))

(expect "(let [a b\n      c d\n      e f]\n  (list a b c d e f))"
        (zprint-str "(let [a b c d e f] (list a b c d e f))"
                    {:parse-string? true, :binding {:force-nl? true}}))

;;
;; Test to see if either :flow? true or :force-nl? true will force new lines
;; in :arg2-extend functions.
;;
;; This tests zprint.zprint/allow-one-line? and the map associated with it,
;; fn-style->caller.
;;

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta] clojure.lang.IHashEq (hasheq [this] (list this)) clojure.lang.Counted (count [_] cnt) clojure.lang.IMeta (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest
                      200
                      {:extend {:flow? false, :force-nl? false}})))
(expect
  "(deftype Typetest [cnt _meta] clojure.lang.IHashEq (hasheq [this] (list this)) clojure.lang.Counted (count [_] cnt) clojure.lang.IMeta (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr
              200
              {:parse-string? true, :extend {:flow? false, :force-nl? false}}))

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest
                      200
                      {:extend {:flow? false, :force-nl? true}})))
(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr
              200
              {:parse-string? true, :extend {:flow? false, :force-nl? true}}))

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest
                      200
                      {:extend {:flow? true, :force-nl? true}})))
(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr
              200
              {:parse-string? true, :extend {:flow? true, :force-nl? true}}))

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest
                      200
                      {:extend {:flow? true, :force-nl? false}})))
(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr
              200
              {:parse-string? true, :extend {:flow? true, :force-nl? false}}))


;;
;; # :nl-separator? tests
;;

(expect "(cond a\n        b\n      c\n        d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? false}}))

(expect "(cond a\n        b\n\n      c\n        d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? true}}))

(expect "{:abc\n   :def,\n :ghi\n   :ijk}"
        (zprint-str {:abc :def, :ghi :ijk}
                    {:map {:flow? true, :nl-separator? false}}))

(expect "{:abc\n   :def,\n\n :ghi\n   :ijk}"
        (zprint-str {:abc :def, :ghi :ijk}
                    {:map {:flow? true, :nl-separator? true}}))

(expect
  "(let [a\n        b\n      c\n        d\n      e\n        f]\n  (list a b c d e f))"
  (zprint-str "(let [a b c d e f] (list a b c d e f))"
              {:parse-string? true,
               :binding {:flow? true, :nl-separator? false}}))

(expect
  "(let [a\n        b\n\n      c\n        d\n\n      e\n        f]\n  (list a b c d e f))"
  (zprint-str "(let [a b c d e f] (list a b c d e f))"
              {:parse-string? true,
               :binding {:flow? true, :nl-separator? true}}))

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? true}})))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr
              {:parse-string? true, :extend {:flow? true}}))

#?(:clj
     (expect
       "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n\n  clojure.lang.Counted\n    (count [_] cnt)\n\n  clojure.lang.IMeta\n    (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest
                      {:extend {:flow? true, :nl-separator? true}})))
(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n\n  clojure.lang.Counted\n    (count [_] cnt)\n\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typeteststr
              {:parse-string? true,
               :extend {:flow? true, :nl-separator? true}}))

;;
;; # Does :flow? and :nl-separator? work for constant pairs?
;;

(expect "(println :this :should\n         :constant :pair)"
        (zprint-str "(println :this :should :constant :pair)"
                    37
                    {:parse-string? true, :pair {:flow? false}}))

(expect
  "(println :this\n           :should\n         :constant\n           :pair)"
  (zprint-str "(println :this :should :constant :pair)"
              37
              {:parse-string? true, :pair {:flow? true}}))

(expect
  "(println :this\n           :should\n         :constant\n           :pair)"
  (zprint-str "(println :this :should :constant :pair)"
              37
              {:parse-string? true, :pair {:flow? true, :nl-separator? false}}))

(expect
  "(println :this\n           :should\n\n         :constant\n           :pair)"
  (zprint-str "(println :this :should :constant :pair)"
              37
              {:parse-string? true, :pair {:flow? true, :nl-separator? true}}))

(expect "(println\n  :this\n    :should\n  :constant\n    :pair)"
        (zprint-str "(println :this :should :constant :pair)"
                    15
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? false}}))

(expect "(println\n  :this\n    :should\n\n  :constant\n    :pair)"
        (zprint-str "(println :this :should :constant :pair)"
                    15
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? true}}))

;;
;; # :extend -- support :hang? for :extend
;;


;!zprint {:format :skip}
; Something strange going on with source-fn and :clj!
#?(:clj
     (deftype Typetest1 [cnt _meta]
       clojure.lang.IHashEq
         (hasheq [this] (list this) (list this this) (list this this this this))
       clojure.lang.Counted
         (count [_] cnt)
       clojure.lang.IMeta
         (meta [_] _meta)))

(def Typetest1str
"(deftype Typetest1 [cnt _meta]
  clojure.lang.IHashEq
    (hasheq [this] (list this) (list this this) (list this this this this))
  clojure.lang.Counted
    (count [_] cnt)
  clojure.lang.IMeta
    (meta [_] _meta))")

#?(:clj
     (expect
       "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this]\n                         (list this)\n                         (list this this)\n                         (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest1
                      60
                      {:extend {:flow? false, :hang? true}})))
(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this]\n                         (list this)\n                         (list this this)\n                         (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typetest1str
                 60
                 {:parse-string? true, :extend {:flow? false, :hang? true}}))

#?(:clj
     (expect
       "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest1
                      60
                      {:extend {:flow? false, :hang? false}})))
(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typetest1str
                 60
                 {:parse-string? true :extend {:flow? false, :hang? false}}))

#?(:clj
     (expect
       "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest1
                      60
                      {:extend {:flow? true, :hang? true}})))
(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typetest1str
                 60
                 {:parse-string? true :extend {:flow? true, :hang? true}}))

#?(:clj
     (expect
       "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
       (zprint-fn-str zprint.zprint-test/->Typetest1
                      60
                      {:extend {:flow? true, :hang? false}})))
(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str zprint.zprint-test/Typetest1str
                 60
                 {:parse-string? true :extend {:flow? true, :hang? false}}))

;;
;; # Test a variant form of cond with :nl-separator?
;;

;!zprint {:format :skip}
; Something strange going on with defn/def source-fn and :clj!
#?(:clj 
(defn zctest8x
          []
          (let [a (list 'with 'arguments)
                foo nil
                bar true
                baz "stuff"
                other 1
                bother 2
                stuff 3
                now 4
                output 5
                b 3
                c 5
                this "is"]
            (cond (or foo bar baz) (format output now)
                  :let [stuff (and bother foo bar)
                        bother (or other output foo)]
                  (and a b c (bother this)) (format other stuff))
            (list a :b :c "d"))))

(def zctest8xstr
"(defn zctest8x
  []
  (let [a (list 'with 'arguments)
        foo nil
        bar true
        baz \"stuff\"
        other 1
        bother 2
        stuff 3
        now 4
        output 5
        b 3
        c 5
        this \"is\"]
    (cond (or foo bar baz) (format output now)
          :let [stuff (and bother foo bar)
                bother (or other output foo)]
          (and a b c (bother this)) (format other stuff))
    (list a :b :c \"d\")))")

#?(:clj
     (expect
       "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
       (zprint-fn-str zprint.zprint-test/zctest8x 20)))

(expect
  "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
  (zprint-str zprint.zprint-test/zctest8xstr 20 {:parse-string? true}))

#?(:clj
     (expect
       "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
       (zprint-fn-str zprint.zprint-test/zctest8x
                      20
                      {:pair {:nl-separator? true}})))

(expect
  "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
  (zprint-str zprint.zprint-test/zctest8xstr
              20
              {:parse-string? true, :pair {:nl-separator? true}}))

;;
;; # Issue 17
;;
;; There should be no completely blank lines in the output for this function.
;;

(defn zpair-tst
  []
  (println (list :ajfkdkfdj :bjlfkdsfjsdl)
           (list :cjslkfsdjl :dklsdfjsdsjsldf)
           [:ejlkfjdsfdfklfjsljfsd :fjflksdfjlskfdjlk]
           :const1 "stuff"
           :const2 "bother"))

(def zpair-tststr
  "(defn zpair-tst
  []
  (println (list :ajfkdkfdj :bjlfkdsfjsdl)
           (list :cjslkfsdjl :dklsdfjsdsjsldf)
           [:ejlkfjdsfdfklfjsljfsd :fjflksdfjlskfdjlk]
           :const1 \"stuff\"
           :const2 \"bother\"))")

#?(:clj
     (expect
       "(defn zpair-tst\n  []\n  (println\n    (list :ajfkdkfdj\n          :bjlfkdsfjsdl)\n    (list :cjslkfsdjl\n          :dklsdfjsdsjsldf)\n    [:ejlkfjdsfdfklfjsljfsd\n     :fjflksdfjlskfdjlk]\n    :const1 \"stuff\"\n    :const2 \"bother\"))"
       (zprint-fn-str zprint.zprint-test/zpair-tst
                      30
                      {:pair {:nl-separator? true}})))

(expect
  "(defn zpair-tst\n  []\n  (println\n    (list :ajfkdkfdj\n          :bjlfkdsfjsdl)\n    (list :cjslkfsdjl\n          :dklsdfjsdsjsldf)\n    [:ejlkfjdsfdfklfjsljfsd\n     :fjflksdfjlskfdjlk]\n    :const1 \"stuff\"\n    :const2 \"bother\"))"
  (zprint-str zprint.zprint-test/zpair-tststr
              30
              {:parse-string? true, :pair {:nl-separator? true}}))

;
; Should be one blank line here
;

#?(:clj
     (expect
       "(defn zpair-tst\n  []\n  (println\n    (list\n      :ajfkdkfdj\n      :bjlfkdsfjsdl)\n    (list\n      :cjslkfsdjl\n      :dklsdfjsdsjsldf)\n    [:ejlkfjdsfdfklfjsljfsd\n     :fjflksdfjlskfdjlk]\n    :const1\n      \"stuff\"\n\n    :const2\n      \"bother\"))"
       (zprint-fn-str zprint.zprint-test/zpair-tst
                      17
                      {:pair {:nl-separator? true}})))

(expect
  "(defn zpair-tst\n  []\n  (println\n    (list\n      :ajfkdkfdj\n      :bjlfkdsfjsdl)\n    (list\n      :cjslkfsdjl\n      :dklsdfjsdsjsldf)\n    [:ejlkfjdsfdfklfjsljfsd\n     :fjflksdfjlskfdjlk]\n    :const1\n      \"stuff\"\n\n    :const2\n      \"bother\"))"
  (zprint-str zprint.zprint-test/zpair-tststr
              17
              {:parse-string? true, :pair {:nl-separator? true}}))

;;
;; # {:extend {:modifers #{"static"}}} Tests
;;

(def zextend-tst1
  '(deftype Foo [a b c]
     P
       (foo [this] a)
     Q
       (bar-me [this] b)
       (bar-me [this y] (+ c y))
     R
     S
       (baz [this] a)
     static T
       (baz-it [this] b)
     static V
       (baz-it [this] b)
       (bar-none [this] a)
     stuff
     Q
     R
       (fubar [this] it)))


(expect
  "(deftype Foo [a b c]\n  P (foo [this] a)\n  Q\n    (bar-me [this] b)\n    (bar-me [this y] (+ c y))\n  R\n  S (baz [this] a)\n  static T (baz-it [this] b)\n  static V\n    (baz-it [this] b)\n    (bar-none [this] a)\n  stuff\n  Q\n  R (fubar [this] it))"
  (zprint-str zprint.zprint-test/zextend-tst1 {:extend {:flow? false}}))

(expect
  "(deftype Foo [a b c]\n  P\n    (foo [this] a)\n  Q\n    (bar-me [this] b)\n    (bar-me [this y] (+ c y))\n  R\n  S\n    (baz [this] a)\n  static T\n    (baz-it [this] b)\n  static V\n    (baz-it [this] b)\n    (bar-none [this] a)\n  stuff\n  Q\n  R\n    (fubar [this] it))"
  (zprint-str zprint.zprint-test/zextend-tst1 {:extend {:flow? true}}))

;
; What happens if the modifier and the first element don't fit on the same line?
;

(expect
  "(deftype bax [a b c]\n  static this-is-very-long-and-should-not-work\n    (baz-it [this] b))"
  (zprint-str
    "(deftype bax [a b c] static this-is-very-long-and-should-not-work (baz-it [this] b))"
    {:parse-string? true}))

(expect
  "(deftype bax [a b c]\n  static\n    this-is-very-long-and-should-not-work\n    (baz-it [this] b))"
  (zprint-str
    "(deftype bax [a b c] static this-is-very-long-and-should-not-work (baz-it [this] b))"
    45
    {:parse-string? true}))

;
; Test removal of a modifier to see both that it works and confirm that
; removing it produces the right result.
;

(expect
  "(deftype Foo [a b c]\n  P (foo [this] a)\n  Q\n    (bar-me [this] b)\n    (bar-me [this y] (+ c y))\n  R\n  S (baz [this] a)\n  static\n  T (baz-it [this] b)\n  static\n  V\n    (baz-it [this] b)\n    (bar-none [this] a)\n  stuff\n  Q\n  R (fubar [this] it))"
  (zprint-str zprint.zprint-test/zextend-tst1
              {:remove {:extend {:modifiers #{"static"}}},
               :extend {:flow? false}}))

;;
;; # Tests for key-color and key-depth-color
;;

; key-depth-color


(defn key-color-tst
  []
  {:abc
     ;stuff
     :bother,
   "deep" {"and" "even", :deeper {"that" :is, :just "the", "way" :it-is}},
   "def" "ghi",
   5 "five",
   ["hi"] "there"})

(def key-color-tststr
  "(defn key-color-tst
  []
  {:abc
     ;stuff
     :bother,
   \"deep\" {\"and\" \"even\", :deeper {\"that\" :is, :just \"the\", \"way\" :it-is}},
   \"def\" \"ghi\",
   5 \"five\",
   [\"hi\"] \"there\"})")

; :key-depth-color []


(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["]" :purple :right] ["\n  " :none :indent] ["{" :red :left]
   [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :newline]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] ["," :none :whitespace]
   [" " :none :whitespace] [":just" :magenta :element] [" " :none :whitespace]
   ["\"the\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
   ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-str
    zprint.zprint-test/key-color-tststr
	{:parse-string? true, :map {:key-depth-color []}, :return-cvec? true}))

; :key-depth-color [:blue :yellow :green]

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["]" :purple :right] ["\n  " :none :indent] ["{" :red :left]
   [":abc" :blue :element] ["\n     " :none :indent] [";stuff" :green :comment]
   ["\n     " :none :newline] [":bother" :magenta :element]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"deep\"" :blue :element]
   [" " :none :whitespace] ["{" :red :left] ["\"and\"" :yellow :element]
   [" " :none :whitespace] ["\"even\"" :red :element] ["," :none :whitespace]
   [" " :none :whitespace] [":deeper" :yellow :element] [" " :none :whitespace]
   ["{" :red :left] ["\"that\"" :green :element] [" " :none :whitespace]
   [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
   [":just" :green :element] [" " :none :whitespace] ["\"the\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] ["\"way\"" :green :element]
   [" " :none :whitespace] [":it-is" :magenta :element] ["}" :red :right]
   ["}" :red :right] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"def\"" :blue :element] [" " :none :whitespace] ["\"ghi\"" :red :element]
   ["," :none :whitespace] ["\n   " :none :indent] ["5" :blue :element]
   [" " :none :whitespace] ["\"five\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["[" :purple :left] ["\"hi\"" :red :element]
   ["]" :purple :right] [" " :none :whitespace] ["\"there\"" :red :element]
   ["}" :red :right] [")" :green :right]]
  (czprint-str zprint.zprint-test/key-color-tststr
               {:parse-string? true,
                :map {:key-depth-color [:blue :yellow :green]},
                :return-cvec? true}))

; :key-color {}

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["]" :purple :right] ["\n  " :none :indent] ["{" :red :left]
   [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :newline]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] ["," :none :whitespace]
   [" " :none :whitespace] [":just" :magenta :element] [" " :none :whitespace]
   ["\"the\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
   ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-str zprint.zprint-test/key-color-tststr
               {:parse-string? true, :map {:key-color {}}, :return-cvec? true}))

; :key-color {:abc :blue "deep" :cyan 5 :green}

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["]" :purple :right] ["\n  " :none :indent] ["{" :red :left]
   [":abc" :blue :element] ["\n     " :none :indent] [";stuff" :green :comment]
   ["\n     " :none :newline] [":bother" :magenta :element]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"deep\"" :cyan :element]
   [" " :none :whitespace] ["{" :red :left] ["\"and\"" :red :element]
   [" " :none :whitespace] ["\"even\"" :red :element] ["," :none :whitespace]
   [" " :none :whitespace] [":deeper" :magenta :element] [" " :none :whitespace]
   ["{" :red :left] ["\"that\"" :red :element] [" " :none :whitespace]
   [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
   [":just" :magenta :element] [" " :none :whitespace] ["\"the\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] ["\"way\"" :red :element]
   [" " :none :whitespace] [":it-is" :magenta :element] ["}" :red :right]
   ["}" :red :right] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"def\"" :red :element] [" " :none :whitespace] ["\"ghi\"" :red :element]
   ["," :none :whitespace] ["\n   " :none :indent] ["5" :green :element]
   [" " :none :whitespace] ["\"five\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["[" :purple :left] ["\"hi\"" :red :element]
   ["]" :purple :right] [" " :none :whitespace] ["\"there\"" :red :element]
   ["}" :red :right] [")" :green :right]]
  (czprint-str zprint.zprint-test/key-color-tststr
               {:parse-string? true,
                :map {:key-color {:abc :blue, "deep" :cyan, 5 :green}},
                :return-cvec? true}))

; Test out nil's in the :key-depth-color vector, and if :key-color values
; will override what is in :key-depth-color

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["]" :purple :right] ["\n  " :none :indent] ["{" :red :left]
   [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :newline]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] ["," :none :whitespace]
   [" " :none :whitespace] [":just" :magenta :element] [" " :none :whitespace]
   ["\"the\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
   ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :cyan :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-str zprint.zprint-test/key-color-tststr
               {:parse-string? true,
                :map {:key-depth-color [:blue nil :green],
                      :key-color {"def" :cyan}},
                :return-cvec? true}))

;;
;; Issue #23 -- can't justify a map that has something too big
;;
;; Bug was added in 0.3.0 when pmap showed up
;;

(expect "{:a\n   \"this is a pretty long string\",\n :b :c}"
        (zprint-str {:a "this is a pretty long string", :b :c}
                    30
                    {:map {:justify? true}, :parallel? false}))

;;
;; Test :arg2-pair, see if both data and string versions of zthird
;; work, essentially.
;;

(expect "(defn test-condp\n  [x y]\n  (condp = 1\n    1 :pass\n    2 :fail))"
        (zprint-str '(defn test-condp [x y] (condp = 1 1 :pass 2 :fail)) 20))

(expect "(defn test-condp\n  [x y]\n  (condp = 1\n    1 :pass\n    2 :fail))"
        (zprint-str "(defn test-condp [x y] (condp = 1 1 :pass 2 :fail))"
                    20
                    {:parse-string? true}))

;;
;; Issue #25 -- problem with printing (fn ...) when it is an s-expression
;; but not when it is a string.  concat-no-nil contains a (fn ...) 
;;

(def concat-no-nilstr
"(defn concat-no-nil\n  \"Concatentate multiple sequences, but if any of them are nil or empty\n  collections, return nil. If any of them are :noseq, just skip them.\n  When complete, check the last element-- if it is a :right, and if it\n  the previous element is a :newline or :indent, then ensure that the\n  number of spaces in that previous element matches the number to the\n  right of the :right.\"\n  [& rest]\n  (let [result (reduce (fn [v o]\n                         (if (coll? o)\n                           (if (empty? o) (reduced nil) (reduce conj! v o))\n                           (if (= :noseq o)\n                             ; if the supposed sequence is :noseq, skip it\n                             v\n                             (if (nil? o) (reduced nil) (conj! v o)))))\n                 (transient [])\n                 rest)]\n    (when result\n      (let [result (persistent! result)]\n        (if (< (count result) 2)\n          result\n          (let [[_ _ what right-ind :as last-element] (peek result)]\n            (if (= what :right)\n              ; we have a right paren, bracket, brace as the last thing\n              (let [previous-index (- (count result) 2)\n                    [s color previous-what] (nth result previous-index)]\n                (if (or (= previous-what :newline) (= previous-what :indent))\n                  ; we have a newline or equivalent before the last thing\n                  (if (= (count-right-blanks s) right-ind)\n                    ; we already have the right number of blanks!\n                    result\n                    (let [new-previous [(str (trimr-blanks s)\n                                             (blanks right-ind)) color\n                                        previous-what]]\n                      (assoc result previous-index new-previous)))\n                  result))\n              result)))))))")

(expect (read-string concat-no-nilstr)
        (read-string (zprint-str concat-no-nilstr {:parse-string? true})))

;;
;; Try a large function to see if we can do code in s-expressions correctly
;;

(expect (trim-gensym-regex fzprint-list*str)
        (read-string (zprint-str (trim-gensym-regex fzprint-list*str))))

;;
;; # key-value-color
;;
;; When you find this key, use the color map associated with it when formatting
;; the value.
;;

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["]" :purple :right] ["\n  " :none :indent] ["{" :red :left]
   [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :newline]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] ["," :none :whitespace]
   [" " :none :whitespace]
   [":just" :magenta :element] [" " :none :whitespace] ["\"the\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :blue :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-str zprint.zprint-test/key-color-tststr
               {:parse-string? true,
                :map {:key-value-color {["hi"] {:string :blue}}},
                :return-cvec? true}))

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["]" :purple :right] ["\n  " :none :indent] ["{" :red :left]
   [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :newline]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :blue :element] ["," :none :whitespace]
   [" " :none :whitespace]
   [":just" :blue :element] [" " :none :whitespace] ["\"the\"" :red :element]
   ["," :none :whitespace] [" " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :blue :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-str zprint.zprint-test/key-color-tststr
               {:parse-string? true,
                :map {:key-value-color {:deeper {:keyword :blue}}},
                :return-cvec? true}))

;;
;; # Namespaced key tests
;;

;;
;; First, the parse-string tests
;;

(expect "(list #:x{:a :b, :c :d})"
        (zprint-str "(list {:x/a :b :x/c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? true}}))

(expect "(list {:x/a :b, :x/c :d})"
        (zprint-str "(list {:x/a :b :x/c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? false}}))

(expect "(list {::a :b, ::c :d})"
        (zprint-str "(list {::a :b ::c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? true}}))

(expect "(list {::a :b, ::c :d})"
        (zprint-str "(list {::a :b ::c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? false}}))

;;
;; This generates:
;;
;; #object[Error Error: Namespaced keywords not supported !]
;;
;; while the namespaced keywords in the (list ...) above seem to work ok.
;;

#?(:clj (expect "{::a :b, ::c :d}"
                (zprint-str "{::a :b ::c :d}"
                            {:parse-string? true, :map {:lift-ns? true}})))

(expect "{:x/a :b, :y/c :d}"
        (zprint-str "{:x/a :b :y/c :d}"
                    {:parse-string? true, :map {:lift-ns? true}}))

(expect "#:x{:a :b, :c :d}"
        (zprint-str "{:x/a :b :x/c :d}"
                    {:parse-string? true, :map {:lift-ns? true}}))

;;
;; Second, the repl s-expression tests
;;

(expect "#:zprint.zprint-test{:a :b, :c :d}"
        (zprint-str {::a :b, ::c :d} {:map {:lift-ns? true}}))

(expect "{:zprint.zprint-test/a :b, :zprint.zprint-test/c :d}"
        (zprint-str {::a :b, ::c :d} {:map {:lift-ns? false}}))

(expect "#:zprint.zprint-test{:a :b, :c :d}"
        (zprint-str {::a :b, ::c :d} {:map {:lift-ns? true}}))

(expect "{:x/a :b, :zprint.zprint-test/c :d}"
        (zprint-str {:x/a :b, ::c :d} {:map {:lift-ns? true}}))

(expect "#:x{:a :b, :c :d}"
        (zprint-str {:x/a :b, :x/c :d} {:map {:lift-ns? true}}))

(expect "{:x/a :b, :x/c :d}"
        (zprint-str {:x/a :b, :x/c :d} {:map {:lift-ns? false}}))

(expect "{:c :d, :x/a :b}"
        (zprint-str {:x/a :b, :c :d} {:map {:lift-ns? true}}))

;;
;; # condp
;;
;; Handling :>> in condp
;;

(expect "(condp a b\n  cdkjdfksjkdf :>> djkdsjfdlsjkl\n  e)"
        (zprint-str "(condp a b cdkjdfksjkdf :>> djkdsjfdlsjkl e)"
                    40
                    {:parse-string? true}))

(expect "(condp a b\n  cdkjdfksjkdf :>>\n    djkdsjfdlsjkl\n  e)"
        (zprint-str "(condp a b cdkjdfksjkdf :>> djkdsjfdlsjkl e)"
                    30
                    {:parse-string? true}))

(expect "(condp a b\n  cdkjdfksjkdf\n    :>>\n    djkdsjfdlsjkl\n  e)"
        (zprint-str "(condp a b cdkjdfksjkdf :>> djkdsjfdlsjkl e)"
                    15
                    {:parse-string? true}))

;;
;; # Commas (Issue #31)
;;
;; Even though commas were turned off, it still needed space for the comma.
;;

(expect 20
        (max-width (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                               20
                               {:map {:comma? false}})))
(expect 2
        (line-count (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                                20
                                {:map {:comma? false}})))

(expect 21
        (max-width (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                               21
                               {:map {:comma? true}})))
(expect 2
        (line-count (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                                21
                                {:map {:comma? true}})))

(expect 14
        (max-width (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                               20
                               {:map {:comma? true}})))
(expect 3
        (line-count (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                                20
                                {:map {:comma? true}})))

;;
;; # (czprint nil) doesn't print "nil" (Issue #32)
;;

(expect "nil" (zprint-str nil))
(expect [["nil" :yellow :element]] (czprint-str nil {:return-cvec? true}))

;;
;; # Inline Comments
;;

(def zctest9str
  "(defn zctest9
  \"Test inline comments\"
  []
  (let [a (list 'with 'arguments)
        foo nil ; end of line comment
        bar true
        baz \"stuff\"
        other 1
        bother 2 ; a really long inline comment that should wrap about here
        stuff 3
        ; a non-inline comment
        now ;a middle inline comment
          4
        ; Not an inline comment
        output 5
        b 3
        c 5
        this \"is\"]
    (cond (or foo bar baz) (format output now)  ;test this
          :let [stuff (and bother foo bar) ;test that
                bother (or other output foo)] ;and maybe the other
          (and a b c (bother this)) (format other stuff))
    (list a :b :c \"d\")))")

(expect
  "(defn zctest9\n  \"Test inline comments\"\n  []\n  (let [a (list 'with 'arguments)\n        foo nil ; end of line comment\n        bar true\n        baz \"stuff\"\n        other 1\n        bother 2 ; a really long inline comment that should wrap about\n                 ; here\n        stuff 3\n        ; a non-inline comment\n        now ;a middle inline comment\n          4\n        ; Not an inline comment\n        output 5\n        b 3\n        c 5\n        this \"is\"]\n    (cond (or foo bar baz) (format output now)  ;test this\n          :let [stuff (and bother foo bar) ;test that\n                bother (or other output foo)] ;and maybe the other\n          (and a b c (bother this)) (format other stuff))\n    (list a :b :c \"d\")))"
  (zprint-str zprint.zprint-test/zctest9str
              70
              {:parse-string? true, :comment {:inline? true}}))

(expect
  "(defn zctest9\n  \"Test inline comments\"\n  []\n  (let [a (list 'with 'arguments)\n        foo nil\n        ; end of line comment\n        bar true\n        baz \"stuff\"\n        other 1\n        bother 2\n        ; a really long inline comment that should wrap about here\n        stuff 3\n        ; a non-inline comment\n        now\n          ;a middle inline comment\n          4\n        ; Not an inline comment\n        output 5\n        b 3\n        c 5\n        this \"is\"]\n    (cond (or foo bar baz) (format output now)\n          ;test this\n          :let [stuff (and bother foo bar)\n                ;test that\n                bother (or other output foo)]\n          ;and maybe the other\n          (and a b c (bother this)) (format other stuff))\n    (list a :b :c \"d\")))"
  (zprint-str zprint.zprint-test/zctest9str
              70
              {:parse-string? true, :comment {:inline? false}}))

;
; Maps too
;

(def zctest10str
  "(defn zctest10
  \"Test maps with inline comments.\"
  []
  {:a :b,
   ; single line comment
   :d :e, ; stuff
   :f :g, ; bother
   :i ;middle
     :j})")


(expect
  "(defn zctest10\n  \"Test maps with inline comments.\"\n  []\n  {:a :b,\n   ; single line comment\n   :d :e,\n   ; stuff\n   :f :g,\n   ; bother\n   :i\n     ;middle\n     :j})"
  (zprint-str zprint.zprint-test/zctest10str
              {:parse-string? true, :comment {:inline? false}}))

(expect
  "(defn zctest10\n  \"Test maps with inline comments.\"\n  []\n  {:a :b,\n   ; single line comment\n   :d :e, ; stuff\n   :f :g, ; bother\n   :i ;middle\n     :j})"
  (zprint-str zprint.zprint-test/zctest10str
              {:parse-string? true, :comment {:inline? true}}))

;;
;; Rum :arg1-mixin tests
;;

;; Define things to test (note that these are all
;; structures, not zipper tests, but we also do zipper
;; tests by specifying the strings to zprint-str after
;; the tests with structures).

(def cz1
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     < rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz2
  '(rum/defcs component
     < rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz3
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz4
  '(rum/defcs component
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz5
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     <
     rum/static
     rum/reactive
     (rum/local 0 :count)
     (rum/local "" :text)
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz6
  '(rum/defcs component
     <
     rum/static
     rum/reactive
     (rum/local 0 :count)
     (rum/local "" :text)
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz7
  '(rum/defcs component
              (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz8
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     < rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
     ([state label]
      (let [count-atom (:count state) text-atom (:text state)] [:div]))
     ([state] (component state nil))))

(def cz9
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     {:a :b,
      "this" [is a test],
      :c [this is a very long vector how do you suppose it will work]}
      rum/static
      rum/reactive
      (rum/local 0 :count)
      (rum/local "" :text)
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

;;
;; Does it work with structures
;;

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz1))

(expect
  "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz2))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz3))

(expect
  "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz4))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz5))

(expect
  "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz6))

(expect
  "(rum/defcs component\n           (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz7))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
  (zprint-str cz8))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   \"this\" [is a test],\n   :c [this is a very long vector how do you suppose it will work]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz9))

;;
;; Does it all work with zippers?
;;

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n           (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n           (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   \"this\" [is a test],\n   :c [this is a very long vector how do you suppose it will work]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   \"this\" [is a test],\n   :c [this is a very long vector how do you suppose it will work]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

;;
;; # Respect newline in vectors
;;

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)] [:div \"height\" (int 6)] [:div \"weight\" (int 7)]]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi \n [:div \n  [:div :e (int 5)] \n  [:div  \n\"height\" (int 6)] \n  [:div  \n\"weight\" (int 7)] \n] \n[:div :a :b :c]]]]"
    {:parse-string? true}))

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div\n    [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)]\n    [:div\n     \"weight\" (int 7)]\n   ]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi \n [:div \n  [:div :e (int 5)] \n  [:div  \n\"height\" (int 6)] \n  [:div  \n\"weight\" (int 7)] \n] \n[:div :a :b :c]]]]"
    {:parse-string? true, :vector {:respect-nl? true}}))

;;[:dev.very.top
;; [:dev.top
;;  [:dev.bmi
;;   [:div [:div :e (int 5)] [:div "height" (int 6)] [:div "weight" (int 7)]]
;;   [:div :a :b :c]]]]

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)] [:div \"height\" (int 6)] [:div \"weight\" (int 7)]]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true}))

;;[:dev.very.top
;; [:dev.top
;;  [:dev.bmi
;;   [:div [:div :e (int 5)]
;;    [:div
;;     "height" (int 6)] [:div "weight" (int 7)]] [:div :a :b :c]]]]

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)] [:div \"weight\" (int 7)]] [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true, :vector {:respect-nl? true}}))

;;[:dev.very.top
;; [:dev.top
;;  [:dev.bmi
;;   [:div [:div :e (int 5)]
;;    [:div
;;     "height" (int 6)]
;;    [:div "weight" (int 7)]]
;;   [:div :a :b :c]]]]

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)]\n    [:div \"weight\" (int 7)]]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true,
     :vector {:respect-nl? true, :wrap-after-multi? false}}))

;;
;; option-fn-first, embedded in :style :keyword-respect-nl
;;

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)] [:div \"weight\" (int 7)]] [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true, :style :keyword-respect-nl}))

;;
;; almost the same as above, but explicitly with :repect-nl? enabled
;;

(expect
  "[:dev.v.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)] [:div \"weight\" (int 7)]] [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.v.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true, :vector {:respect-nl? true}}))

;;
;; validation for option-fn-first return
;;

#?(:clj
     (expect
       "java.lang.Exception: Options resulting from :vector :option-fn-first called with :g had these errors: In the key-sequence [:vector :sort?] the key :sort? was not recognized as valid!"
       (try (zprint-str
              "[:g :f :d :e :e \n :t :r :a :b]"
              {:parse-string? true,
               :vector {:respect-nl? true,
                        :option-fn-first
                          #(do %1 %2 (identity {:vector {:sort? true}}))}})
            (catch Exception e (str e))))
   :cljs
     (expect
       "Error: Options resulting from :vector :option-fn-first called with :g had these errors: In the key-sequence [:vector :sort?] the key :sort? was not recognized as valid!"
       (try (zprint-str
              "[:g :f :d :e :e \n :t :r :a :b]"
              {:parse-string? true,
               :vector {:respect-nl? true,
                        :option-fn-first
                          #(do %1 %2 (identity {:vector {:sort? true}}))}})
            (catch :default e (str e)))))

;;
;; # zprint-file-str tests
;;

(expect
  ";!zprint {:format :next :vector {:wrap? false}}\n\n(def help-str-readable\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
  (zprint-file-str
    ";!zprint {:format :next :vector {:wrap? false}}\n\n(def help-str-readable\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
    "test"))

;;
;; :format :next
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :next :vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :next :vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; :format :off
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; :format :on
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :on}\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :on}\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; :format :skip
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :skip}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :skip}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; Change format for the rest of the file (or rest of the string)
;;
;; Note that the next test depends on this one (where the next one ensures that
;; the values set into the options map in this test don't bleed out into the
;; the environment beyond this call to zprint-file-str).
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; See if removing wrap in the previous test bleeds out into the environment.
;;
;; If I change the code to cause {:vector {:wrap? false}} to bleen out from 
;; the previous test, this next test *does* fail, so we can be sure that it
;; will verify this.
;;

(expect true (:wrap? (:vector (zprint.config/get-options))))

;;
;; # Tests for max length as a single number
;;

;; List with constant pair

(expect
  "(abc sdfjsksdfjdskl\n     jkfjdsljdlfjldskfjklsjfjd\n     :a (quote b)\n     :c (quote d)\n     :e (quote f)\n     :g (quote h)\n     :i (quote j))"
  (zprint-str '(abc sdfjsksdfjdskl
                    jkfjdsljdlfjldskfjklsjfjd
                    :a 'b
                    :c 'd
                    :e 'f
                    :g 'h
                    :i 'j)
              {:max-length 13}))

(expect
  "(abc sdfjsksdfjdskl\n     jkfjdsljdlfjldskfjklsjfjd\n     :a (quote b)\n     :c (quote d)\n     :e (quote f)\n     :g (quote h)\n     :i ...)"
  (zprint-str '(abc sdfjsksdfjdskl
                    jkfjdsljdlfjldskfjklsjfjd
                    :a 'b
                    :c 'd
                    :e 'f
                    :g 'h
                    :i 'j)
              {:max-length 12}))

;; Map

(expect "{:a 1, :b 2, :c 3, :d 4, ...}"
        (zprint-str {:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}
                    {:max-length 4}))

(expect "{:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, ...}"
        (zprint-str {:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}
                    {:max-length 8}))

(expect "{:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}"
        (zprint-str {:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}
                    {:max-length 9}))

;; Set

(expect "#{:a :b :c :d ...}"
        (zprint-str #{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}
                    {:max-length 4}))

(expect "#{:a :b :c :d :e :f :g :h :i :j :k :l :m :n ...}"
        (zprint-str #{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}
                    {:max-length 14}))

(expect "#{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}"
        (zprint-str #{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}
                    {:max-length 15}))

;; Vector

(expect "[:a :b :c :d :e ...]"
        (zprint-str [:a :b :c :d :e :f :g :h :i :j :k] {:max-length 5}))

(expect "[:a :b :c :d :e :f :g :h :i :j ...]"
        (zprint-str [:a :b :c :d :e :f :g :h :i :j :k] {:max-length 10}))

(expect "[:a :b :c :d :e :f :g :h :i :j :k]"
        (zprint-str [:a :b :c :d :e :f :g :h :i :j :k] {:max-length 11}))

;; List, multi-level, zipper (i.e. :parse-string? true)

(expect "(a b (c ...) i j ...)"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [5 1 0]}))

(expect "(a b (c ...) i j (k ...))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 1 0]}))

(expect "(a b (c d ...) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 2 0]}))

(expect "(a b (c d ##) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 0]}))

(expect "(a b (c d (e ...)) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 1 0]}))

(expect "(a b (c d (e f ##)) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3 0]}))

(expect "(a b (c d (e f (g h))) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3]}))

(expect "(a b (c d (e f (g ...))) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3 1 0]}))

(expect "(a b (c d (e f (g h))) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3 2 0]}))

;; set, multi-level

(expect "#{#{#{## ...} :c} :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 2 1 0]}))

(expect "#{#{## :c} :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 2 0]}))

(expect "#{#{## ...} :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 1 0]}))

(expect "#{## :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 0]}))

(expect "#{## ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [1 0]}))

(expect "##"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [0]}))


;; map, multi-level


(expect "{#{#{## ...} :c} :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 2 1 0]}))

(expect "{#{## :c} :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 2 0]}))

(expect "{#{## ...} :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 1 0]}))

(expect "{## :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 0]}))

(expect "{## :a, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [1 0]}))

(expect "##"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [0]}))


(expect
  "{:j :k,\n :l :m,\n :n :o,\n :p :q,\n :r :s,\n :t :u,\n :v :w,\n :x :y,\n {:c {:e {:f :g, :h :i}, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2]}))

(expect
  "{:j :k,\n :l :m,\n :n :o,\n :p :q,\n :r :s,\n :t :u,\n :v :w,\n :x :y,\n {:c {:e {:f :g, ...}, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2 1]}))

(expect
  "{:j :k,\n :l :m,\n :n :o,\n :p :q,\n :r :s,\n :t :u,\n :v :w,\n :x :y,\n {:c {:e {:f :g, ...}, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2 1 0]}))

(expect
  "{:j :k, :l :m, :n :o, :p :q, :r :s, :t :u, :v :w, :x :y, {:c {:e ##, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2 0]}))

(expect "{:j :k, :l :m, :n :o, :p :q, :r :s, :t :u, :v :w, :x :y, {:c ##} :a}"
        (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [10 3 0]}))

(expect "{:j :k, :l :m, :n :o, :p :q, :r :s, :t :u, :v :w, :x :y, ## :a}"
        (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [10 0]}))

(expect "{:j :k, :l :m, :n :o, :p :q, :r :s, ...}"
        (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [5 0]}))

;; vector, multi-level

(expect "[:a [:b [:c ...] ...] :o ...]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [3 2 1 0]}))

(expect "[:a [:b [:c ...] ...] :o ...]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [3 2 1]}))

(expect "[:a [:b [:c [:d [:e :f :g] :h ...] :j ...] :l ...] :o ...]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [3]}))

(expect "[:a [:b ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 1 0]}))

(expect "[:a ## :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 0]}))

(expect "[:a [:b ## :l ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 3 0]}))

(expect "[:a [:b ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 1 3 0]}))

(expect "[:a [:b [:c ## :j :k] :l :m ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 4 4 0]}))

(expect "[:a [:b [:c ## :j :k] ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 2 4 0]}))

(expect "[:a [:b [:c [:d ...] :j :k] ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 2 4 1 0]}))

;; record, multi-level

(def rml (make-record :reallylongleft {:r :s, [[:t] :u :v] :x}))

(expect "#zprint.zprint.r {:left :reallylongleft, ...}"
        (zprint-str rml {:max-length 1}))

(expect
  "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, [[:t] :u :v] :x}}"
  (zprint-str rml))

(expect
  "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, [[:t] :u ...] :x}}"
  (zprint-str rml {:max-length 2}))

(expect "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, ...}}"
        (zprint-str rml {:max-length [2 1 0]}))

(expect "#zprint.zprint.r {:left :reallylongleft, :right ##}"
        (zprint-str rml {:max-length [2 0]}))

(expect "#zprint.zprint.r {:left :reallylongleft, :right ##}"
        (zprint-str rml {:max-length [3 0]}))

;; Can we read back records that we have written out?
;;
;; Issue #105
;;
;; This doesn't seem to work in cljs in any case, oddly enough. 

#?(:clj (expect rml (read-string (zprint-str rml))))

;; depth

;; set

(expect "##" (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 0}))

(expect "#{## :a}" (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 1}))

(expect "#{#{## :b} :a}" (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 2}))

(expect "#{#{#{## :c} :b} :a}"
        (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 3}))

(expect "#{#{#{#{:d} :c} :b} :a}"
        (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 4}))

(expect "#{#{#{#{:d} :c} :b} :a}"
        (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 5}))

;; vector

(expect "[:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 5}))

(expect "[:a [:b [:c [:d ## :h :i] :j :k] :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 4}))

(expect "[:a [:b [:c ## :j :k] :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 3}))

(expect "[:a [:b ## :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 2}))

(expect "[:a ## :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 1}))

(expect "##"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 0}))

;; list

(expect "##"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 0}))

(expect "(:a ## :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 1}))

(expect "(:a (:b ## :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 2}))

(expect "(:a (:b (:c ## :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 3}))

(expect "(:a (:b (:c (:d ## :h :i) :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 4}))

(expect "(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 5}))

(expect "(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 6}))

;; map

(expect "{:a {:b {:c {:d :e}}}}"
        (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 5}))

(expect "{:a {:b {:c {:d :e}}}}"
        (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 4}))

(expect "{:a {:b {:c ##}}}" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 3}))

(expect "{:a {:b ##}}" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 2}))

(expect "{:a ##}" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 1}))

(expect "##" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 0}))

;;
;; # Bug in ztake-append.
;;

(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] ...)\n  clojure.lang.Counted\n    (count [_] ...)\n  clojure.lang.IMeta\n    (meta [_] ...))"
  (zprint-str zprint.zprint-test/Typetest1str
              {:parse-string? true, :max-length [100 2 10 0]}))

(expect "(deftype Typetest1 ...)"
        (zprint-str zprint.zprint-test/Typetest1str
                    {:parse-string? true, :max-length 2}))

;;
;; # Bug in printing multiple uneval things -- Issues #58
;;
;; Using this crazy syntax:  (a b c (d #_#_(e f g) h))
;;
;; Instead of: (a b c (d #_(e f g) #_h))
;;
;; Who knew?
;;

(expect "(a b c (d #_#_(e f g) h))"
        (zprint-str "(a b c (d #_#_(e f g) h))" {:parse-string? true}))

;;
;; # prefix-tags tests.  
;;
;; We already have a lot of these, but might as well gather them all
;; together here.
;;
;; These show that the basics works

(expect "'(a b c)" (zprint-str "'(a b c)" {:parse-string? true}))
(expect "`(a b c)" (zprint-str "`(a b c)" {:parse-string? true}))
(expect "~(a b c)" (zprint-str "~(a b c)" {:parse-string? true}))
(expect "~@(a b c)" (zprint-str "~@(a b c)" {:parse-string? true}))
(expect "@(a b c)" (zprint-str "@(a b c)" {:parse-string? true}))
(expect "#'thisisatest" (zprint-str "#'thisisatest" {:parse-string? true}))
(expect "#_(a b c)" (zprint-str "#_(a b c)" {:parse-string? true}))
(expect "#_#_(a b c) d" (zprint-str "#_#_(a b c) d" {:parse-string? true}))

;;
;; These try for the indents
;;

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      '[aaaaaaa bbbbbbbb\n        cccccccccc])"
  (zprint-str
    "(this is a test this is only a test '[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      `[aaaaaaa bbbbbbbb\n        cccccccccc])"
  (zprint-str
    "(this is a test this is only a test `[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      ~[aaaaaaa bbbbbbbb\n        cccccccccc])"
  (zprint-str
    "(this is a test this is only a test ~[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      ~@[aaaaaaa bbbbbbbb\n         cccccccccc])"
  (zprint-str
    "(this is a test this is only a test ~@[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      #'thisisalsoatest)"
  (zprint-str "(this is a test this is only a test #'thisisalsoatest)"
              30
              {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      #_[aaaaaaa bbbbbbbb\n         cccccccccc])"
  (zprint-str
    "(this is a test this is only a test #_[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      #_#_[aaaaaaa bbbbbbbb\n           cccccccccc]\n        [ddddddddd eeeeeeeeee\n         fffffffffff])"
  (zprint-str
    "(this is a test this is only a test #_#_[aaaaaaa bbbbbbbb cccccccccc][ddddddddd eeeeeeeeee fffffffffff])"
    30
    {:parse-string? true}))

;
; When the fn-type (fn-style) is not a keyword, like :arg1, but a vector
; like [:arg1 {:vector {:wrap? false}}], does it actually do anything?  
;
; Configuration of this in tested in config_test.clj, but this is to test
; the functionality.
;

(def dp
  "(defproject name version :test :this :stuff [:aaaaa :bbbbbbb  
:ccccccccc :ddddddd :eeeeeee ])")

(comment
; Does defproject inhibit the wrapping of elements of a vector (which it is
; configured to do)?

(expect
  "(defproject name version\n  :test :this\n  :stuff [:aaaaa\n          :bbbbbbb\n          :ccccccccc\n          :ddddddd\n          :eeeeeee])"
  (redef-state [zprint.config] (zprint-str dp 50 {:parse-string? true})))

; If we remove that configuration, will it stop inhibiting the wrapping of vector
; elements?

(expect
  "(defproject name version\n  :test :this\n  :stuff [:aaaaa :bbbbbbb :ccccccccc :ddddddd\n          :eeeeeee])"
  (redef-state [zprint.config]
               (zprint-str dp
                           50
                           {:parse-string? true,
                            :fn-map {"defproject" :arg2-pair}})))

)

(expect "{a 1}" (zprint-str "{a 1}" {:parse-string? true}))

(expect
  "(defrecord ~tagname ~fields\n  (~-collect-vars\n    [acc]\n    (reduce #(list datascript.par ser/collect-vars-acc %1 %2))))"
  (zprint-str
    "(defrecord ~tagname ~fields (~-collect-vars [acc] (reduce #(list datascript.par
ser/collect-vars-acc %1 %2) )))"
    {:parse-string? true}))

;;
;; Issue 84
;;

(expect "(a)\n;a\n\n(b)\n;b\n(c)"
        (zprint-file-str "(a)\n;a\n\n(b)\n;b\n(c)" "stuff"))

; Issue #101 fix changed this to not interpolate between ;b and (c).
(expect "(a)\n\n;a\n\n(b)\n\n;b\n(c)"
        (zprint-file-str "(a)\n;a\n\n(b)\n;b\n(c)"
                         "stuff"
                         {:parse {:interpose "\n\n"}}))

;;
;; Issue ??  where someone didn't want to require something after the
;; let locals vector for it to be recognized as a let.
;;

(expect "(let [a b\n      c d\n      e f])"
        (zprint-str "(let [a b c d e f])"
                    {:parse-string? true, :binding {:force-nl? true}}))

(expect "(let [a b\n      c d\n      e f]\n  (list a c e))"
        (zprint-str "(let [a b c d e f] (list a c e))"
                    {:parse-string? true, :binding {:force-nl? true}}))

(expect "(let [a b c d e f])"
        (zprint-str "(let [a b c d e f])" {:parse-string? true}))

;;
;; Issue #106
;;
;; Comments as last thing in sequence of pairs causes missing right parens!
;;

(expect "(case bar\n  :a 1\n  3\n  ;comment\n)"
        (zprint-str "(case bar\n:a 1\n3\n;comment\n)" {:parse-string? true}))

(expect "(cond a 1\n      b ;comment\n)"
        (zprint-str "(cond\na 1\nb ;comment\n)" {:parse-string? true}))

(expect "(case bar\n  :a 1\n  3 ;comment\n)"
        (zprint-str "(case bar\n:a 1\n3 ;comment\n)" {:parse-string? true}))

;;
;; Issue #103
;;
;; Flow indent underneath things like "#(" isn't correct.
;;

(expect
  "#(assoc\n   (let\n     [askfl sdjfksd\n      dskfds\n        lkdsfjdslk\n      sdkjfds\n        skdfjdslk\n      sdkfjsk\n        sdfjdslk]\n     {4 5})\n   :a :b)"
  (zprint-str
    "#(assoc (let [askfl sdjfksd dskfds lkdsfjdslk sdkjfds skdfjdslk sdkfjsk sdfjdslk] {4 5}) :a :b)"
    {:parse-string? true, :width 20}))

;;
;; Issue #100
;;
;; Files that end with a newline don't end with a newline if you use
;; {:parse {:interpose "\n\n"}
;;

; This one ends with a newline.
(expect "(ns foo)\n\n\n(defn baz [])\n"
        (zprint-file-str "(ns foo)\n\n(defn baz [])\n\n\n"
                         "junk"
                         {:parse {:interpose "\n\n\n"}}))

; This one does not.

(expect "(ns foo)\n\n\n(defn baz [])"
        (zprint-file-str "(ns foo)\n\n(defn baz [])"
                         "junk"
                         {:parse {:interpose "\n\n\n"}}))


;;
;; Issue #104
;;

(expect "{:a :b, :c #:c{:e :f, :g :h}}"
        (zprint-str "{:a :b, :c #:c{:e :f :g        :h}}"
                    {:parse-string? true}))

;;
;; Issue #80 -- implement unlift-ns?
;;

; Actually unlift something

(expect "{:a :b, :c {:c/e :f, :c/g :h}}"
        (zprint-str "{:a :b, :c #:c{:e :f :g        :h}}"
                    {:parse-string? true,
                     :map {:lift-ns? false, :unlift-ns? true}}))

; Unlift only if lift-ns? is false

(expect "{:a :b, :c #:c{:e :f, :g :h}}"
        (zprint-str "{:a :b, :c #:c{:e :f :g        :h}}"
                    {:parse-string? true,
                     :map {:lift-ns? true, :unlift-ns? true}}))

; What about an incorrect map?  Don't mess with it

(expect "{:a :b, :c #:m{:c/e :f, :x/g :h}}"
        (zprint-str "{:a :b :c #:m{:c/e :f :x/g :h}}"
                    {:parse-string? true,
                     :map {:lift-ns? true, :unlift-ns? false}}))

; Should be the same as above

(expect "{:a :b, :c #:m{:c/e :f, :x/g :h}}"
        (zprint-str "{:a :b :c #:m{:c/e :f :x/g :h}}"
                    {:parse-string? true,
                     :map {:lift-ns? true, :unlift-ns? true}}))

; Even if trying to unlift, if it already has stuff in the keys, don't mess
; with it.

(expect "{:a :b, :c #:m{:c/e :f, :x/g :h}}"
        (zprint-str "{:a :b :c #:m{:c/e :f :x/g :h}}"
                    {:parse-string? true,
                     :map {:lift-ns? false, :unlift-ns? true}}))

;;
;; # Tests for invertability of lift-ns and unlift-ns.  You should be able
;; to go back and forth...
;;
;; Issue #156.
;;

(expect
  "{:a :b, :c {:u/e :f, :u/g :h}}"
  (zprint-str (zprint-str "{:a :b, :c {:u/e :f, :u/g :h}}"
                          {:parse-string? true,
                           :map {:lift-ns? true, :unlift-ns? false}})
              {:parse-string? true, :map {:lift-ns? false, :unlift-ns? true}}))

;; # Tests for comments mixed in with the early part of lists 
;;

(expect "(;stuff\n let;bother\n  [a :x\n   b :y];foo\n  ;bar\n  ;baz\n  5)"
        (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
                    {:parse-string? true}))

(expect "(;stuff\n let;bother\n  [a :x\n   b :y]\n  (nil? nil)\n  5)"
        (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y](nil? nil) 5)"
                    {:parse-string? true}))


(expect "(;stuff\n let;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n  ;baz\n  5)"
        (zprint-str "( ;stuff\n\nlet;bother\n[a :x b :y] ;foo\n;bar\n\n;baz\n5)"
                    {:parse-string? true}))

(expect "(;stuff\n let;bother\n  [a :x\n   b :y];foo\n  ;bar\n  ;baz\n  5)"
        (zprint-str "( ;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
                    {:parse-string? true}))

(expect "(;stuff\n let ;bother\n  [a :x\n   b :y]  ;foo\n  ;bar\n  ;baz\n  5)"
        (zprint-str
          "( ;stuff\n\nlet ;bother\n[a :x b :y]  ;foo\n;bar\n\n;baz\n5)"
          {:parse-string? true}))

(expect
  "(;stuff\n let ;bother\n  [a :x\n   b :y]\n  (list a b)\n  (map a b)\n  5)"
  (zprint-str "( ;stuff\n\nlet ;bother\n[a :x b :y]\n(list a b)\n(map a b)\n5)"
              {:parse-string? true}))

(expect
  "(;stuff\n let ;bother\n  [a :x\n   b :y]\n  (list a b)\n  (map a b)\n  (should be blank before this)\n  5)"
  (zprint-str
    "( ;stuff\n\nlet ;bother\n[a :x b :y]\n(list a b)\n(map a b)\n\n(should be blank before this)\n5)"
    {:parse-string? true}))

;;
;; # :respect-nl? tests
;;
;; These tests are for :respect-nl?
;;

(expect
  "(;stuff\n\n let;bother\n  [a :x\n   b :y];foo\n  ;bar\n\n  ;baz\n  5)"
  (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
              {:parse-string? true, :list {:respect-nl? true}}))

(expect "(;stuff\n\n let;bother\n  [a :x\n   b :y]\n  (nil? nil)\n  5)"
        (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y](nil? nil) 5)"
                    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(;stuff\n\n let;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n\n  ;baz\n  5)"
  (zprint-str "( ;stuff\n\nlet;bother\n[a :x b :y] ;foo\n;bar\n\n;baz\n5)"
              {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(;stuff\n\n let;bother\n  [a :x\n   b :y];foo\n  ;bar\n\n  ;baz\n  5)"
  (zprint-str "( ;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
              {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(;stuff\n\n let ;bother\n  [a :x\n   b :y]  ;foo\n  ;bar\n\n  ;baz\n  5)"
  (zprint-str "( ;stuff\n\nlet ;bother\n[a :x b :y]  ;foo\n;bar\n\n;baz\n5)"
              {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(;stuff\n\n let ;bother\n  [a :x\n   b :y]\n  (list a b)\n  (map a b)\n  5)"
  (zprint-str "( ;stuff\n\nlet ;bother\n[a :x b :y]\n(list a b)\n(map a b)\n5)"
              {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(;stuff\n\n let ;bother\n  [a :x\n   b :y]\n  (list a b)\n  (map a b)\n\n  (should be blank before this)\n  5)"
  (zprint-str
    "( ;stuff\n\nlet ;bother\n[a :x b :y]\n(list a b)\n(map a b)\n\n(should be blank before this)\n5)"
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(this is\n      a\n      test\n      (;stuff\n\n       let ;bother\n        [a :x\n         b :y]\n\n        (list a b)\n        (map a b)\n\n        (should be blank before this)\n        5))"
  (zprint-str
    "(this is a test\n( ;stuff\n\nlet ;bother\n[a :x b :y]\n\n(list a b)\n(map a b)\n\n(should be blank before this)\n5))"
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(this is\n      a\n      test\n      (;stuff\n\n       let [a :x\n            b :y]\n\n        (list a b)\n        (map a b)\n\n        (should be blank before this)\n        5))"
  (zprint-str
    "(this is a test\n( ;stuff\n\nlet [a :x b :y]\n\n(list a b)\n(map a b)\n\n(should be blank before this)\n5))"
    {:parse-string? true, :list {:respect-nl? true}}))

;;
;; If we do it twice, does it change?
;;

(expect
  (zprint-str
    "(this is a test\n( ;stuff\n\nlet [a :x b :y]\n\n(list a b)\n(map a b)\n\n(should be blank before this) ;more stuff\n(list :a :b) ;bother\n\n(should also be a blank line before this)\n5))"
    {:parse-string? true, :list {:respect-nl? true}})
  (zprint-str
    (zprint-str
      "(this is a test\n( ;stuff\n\nlet [a :x b :y]\n\n(list a b)\n(map a b)\n\n(should be blank before this) ;more stuff\n(list :a :b) ;bother\n\n(should also be a blank line before this)\n5))"
      {:parse-string? true, :list {:respect-nl? true}})
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(;stuff\n cond\n\n  (= a 1) ;bother\n    (good stuff)\n  (not= b 2) (bad stuff)\n  :else\n\n    (remaining stuff))"
  (zprint-str
    "(;stuff\n cond\n  \n  (= a 1) ;bother\n    (good stuff)\n  (not= b 2) (bad stuff)\n  :else\n    \n    (remaining stuff))"
    {:parse-string? true, :list {:respect-nl? true}}))

;;
;; :respect-nl for maps
;;

(expect "{:a :b, :c :d, :e :f}"
        (zprint-str "{:a :b :c :d :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}}))

(expect "{:a :b,\n :c :d,\n :e :f}"
        (zprint-str "{:a :b :c :d :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{:a\n   :b,\n :c :d,\n :e :f}"
        (zprint-str "{:a \n :b :c :d :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{:a :b,\n :c :d,\n :e :f}"
        (zprint-str "{:a :b \n :c :d :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{:a :b,\n\n :c :d,\n :e :f}"
        (zprint-str "{:a :b \n\n :c :d :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{:a\n\n   :b,\n :c :d,\n :e :f}"
        (zprint-str "{:a \n\n :b \n :c :d \n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{:a\n\n   :b,\n :c :d,\n :e :f}"
        (zprint-str "{:a \n\n :b \n :c :d \n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 80}))

(expect "{;stuff\n :a :b, ;bother\n :c :d,\n :e :f}"
        (zprint-str "{;stuff\n :a :b ;bother\n :c :d \n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{;stuff\n :a :b, ;bother\n :c :d,\n :e :f}"
        (zprint-str "{;stuff\n :a :b ;bother\n :c :d \n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 80}))

(expect "{;stuff\n\n :a :b, ;bother\n :c\n\n   :d,\n :e :f}"
        (zprint-str "{;stuff\n\n :a :b ;bother\n :c \n\n :d \n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{;stuff\n\n :a :b, ;bother\n :c\n\n   :d,\n :e :f}"
        (zprint-str "{;stuff\n\n :a :b ;bother\n :c \n\n :d \n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 80}))

(expect "{;stuff\n\n :a :b,\n :c ;bother\n\n   :d, ;foo\n\n :e :f}"
        (zprint-str "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 15}))

(expect "{;stuff\n\n :a :b,\n :c ;bother\n\n   :d, ;foo\n\n :e :f}"
        (zprint-str "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
                    {:parse-string? true, :map {:respect-nl? true}, :width 80}))
;;
;; Do things change when we do it twice?
;;

(expect
  (zprint-str "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
              {:parse-string? true, :map {:respect-nl? true}, :width 80})
  (zprint-str (zprint-str
                "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
                {:parse-string? true, :map {:respect-nl? true}, :width 80})
              {:parse-string? true, :map {:respect-nl? true}, :width 80}))

;;
;; If there are only two things and the first is a comment
;;

(expect "(;this is a test\n the-first-thing)"
        (zprint-str "(;this is a test\n the-first-thing)"
                    {:parse-string? true}))

;;
;; :respect-nl? true tests for vectors
;;

(expect "[a b c d]" (zprint-str "[a\nb\nc\nd]" {:parse-string? true}))

(expect "[a b c d]"
        (zprint-str "[a\nb\n\nc\nd]"
                    {:parse-string? true, :vector {:respect-nl? false}}))

(expect "[a\n b\n\n c\n d]"
        (zprint-str "[a\nb\n\nc\nd]"
                    {:parse-string? true, :vector {:respect-nl? true}}))

(expect "[a\n b\n\n c\n [e\n  f]\n d]"
        (zprint-str "[a\nb\n\nc [e \n f]\nd]"
                    {:parse-string? true, :vector {:respect-nl? true}}))

(expect "[this\n\n is a thing]"
        (zprint-str "[this\n\nis a thing]"
                    {:parse-string? true, :vector {:respect-nl? true}}))

(expect "[this\n\n is a thing]"
        (zprint-str "[this\n  \nis a thing]"
                    {:parse-string? true, :vector {:respect-nl? true}}))

;;
;; :respect-bl? true tests for lists
;;

(expect
  "(this is\n      a\n\n      thing\n      with\n      a\n      blank\n      line)"
  (zprint-str "(this is a\n\nthing with a blank line)"
              {:parse-string? true, :list {:respect-bl? true}}))

(expect
  "(this is\n      a\n\n      thing\n      with\n      a\n      blank\n      line)"
  (zprint-str "(this is a\n     \nthing with a blank line)"
              {:parse-string? true, :list {:respect-bl? true}}))

(expect
  "(comment (defn x [y] (println y))\n\n         (this is a thing that is interesting)\n\n         (def z :this-is-a-test)\n\n         (def a :more stuff)\n\n\n\n         (def b :3-blanks-above))"
  (zprint-str
    "(comment\n(defn x\n  [y]\n  (println y))\n\n(this is a\n         thing that is interesting)\n\n(def z :this-is-a-test)\n\n(def a :more stuff)\n\n\n\n(def b :3-blanks-above))"
    {:parse-string? true, :list {:respect-bl? true}}))

(expect "(a b\n\n\n   c\n   d)"
        (zprint-str "(a\nb\n \n\nc\nd)"
                    {:parse-string? true, :style :respect-bl}))

(expect "(a b\n\n\n   c\n\n\n\n\n   d)"
        (zprint-str "(a\nb\n \n\nc \n \n\n \n\nd)"
                    {:parse-string? true, :style :respect-bl}))

;;
;; :respect-bl? true tests for vectors
;;

(expect "[a b\n\n c d]"
        (zprint-str "[a\nb\n\nc\nd]"
                    {:parse-string? true, :vector {:respect-bl? true}}))


(expect "[a b\n\n c [e f] d]"
        (zprint-str "[a\nb\n\nc [e \n f]\nd]"
                    {:parse-string? true, :vector {:respect-bl? true}}))

(expect "[this\n\n is a thing]"
        (zprint-str "[this\n\nis a thing]"
                    {:parse-string? true, :vector {:respect-bl? true}}))

(expect "[this\n\n is a thing]"
        (zprint-str "[this\n  \nis a thing]"
                    {:parse-string? true, :vector {:respect-bl? true}}))

;;
;; :respect-bl? true tests for sets
;;

(expect "#{:a :b :c\n\n  :d :e}"
        (zprint-str "#{:a :b \n :c \n\n :d :e}"
                    {:parse-string? true, :set {:respect-bl? true}}))
(expect "#{:a :b :c\n\n\n  :d\n\n  :e}"
        (zprint-str "#{:a :b \n :c \n\n\n :d \n\n :e}"
                    {:parse-string? true, :set {:respect-bl? true}}))

;;
;; :respect-bl? true for maps
;;

(expect "{:a :b,\n :c :d,\n\n :e\n\n\n   :f}"
        (zprint-str "{:a :b \n :c \n :d \n\n :e \n\n\n :f}"
                    {:parse-string? true, :map {:respect-bl? true}}))

(expect "{:a :b, :c {:g :h, :i :j}, :e :f}"
        (zprint-str "{:a :b \n :c \n {:g \n :h :i \n\n :j} \n\n :e \n\n\n :f}"
                    {:parse-string? true, :map {:respect-bl? false}}))

(expect
  "{:a :b,\n :c {:g :h,\n     :i\n\n       :j},\n\n :e\n\n\n   :f}"
  (zprint-str "{:a :b \n :c \n {:g \n :h :i \n\n :j} \n\n :e \n\n\n :f}"
              {:parse-string? true, :map {:respect-bl? true}}))

(expect
  "{:a :b,\n :c\n   {:g\n      :h,\n    :i\n\n      :j},\n\n :e\n\n\n   :f}"
  (zprint-str "{:a :b \n :c \n {:g \n :h :i \n\n :j} \n\n :e \n\n\n :f}"
              {:parse-string? true, :map {:respect-nl? true}}))


;;
;; partition-all-sym was handling a comment with a symbol on its own
;; incorrectly.

(expect
  "(reify\n  xyzzy1\n  ;comment\n  xyzzy2\n    (rrr [_] \"ghi\")\n    (sss [_] :abc)\n  zzz)"
  (zprint-str
    "(reify xyzzy1 \n ;comment\n xyzzy2 (rrr [_] \"ghi\") \n (sss [_] :abc) zzz)"
    {:parse-string? true}))

;;
;; :respect-nl? true tests for :extend (which basically means reify)
;;

(expect
  "(reify\n  xyzzy1\n\n  ;comment\n\n  xyzzy2\n    (rrr [_] \"ghi\")\n\n    (sss [_] :abc)\n  zzz)"
  (zprint-str
    "(reify xyzzy1 \n\n ;comment\n\n xyzzy2 (rrr [_] \"ghi\") \n\n (sss [_] :abc) zzz)"
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(reify\n  xyzzy1\n\n  ;comment\n\n  xyzzy2\n    (rrr [_] \"ghi\")\n    (sss [_] :abc)\n  zzz)"
  (zprint-str
    "(reify xyzzy1 \n\n ;comment\n\n xyzzy2 (rrr [_] \"ghi\") \n (sss [_] :abc) zzz)"
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(reify\n  xyzzy1\n\n  ;comment\n\n  xyzzy2\n    (rrr [_] \"ghi\")\n    (sss [_] :abc)\n  zzz)"
  (zprint-str
    "(reify xyzzy1 \n\n ;comment\n\n xyzzy2 (rrr [_] \"ghi\") (sss [_] :abc) zzz)"
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(reify\n  xyzzy1\n\n  ;comment\n  xyzzy2\n    (rrr [_] \"ghi\")\n    (sss [_] :abc)\n  zzz)"
  (zprint-str
    "(reify xyzzy1 \n\n ;comment\n xyzzy2 (rrr [_] \"ghi\") (sss [_] :abc) zzz)"
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(reify\n  xyzzy1\n  ;comment\n  xyzzy2\n    (rrr [_] \"ghi\")\n    (sss [_] :abc)\n  zzz)"
  (zprint-str
    "(reify xyzzy1 \n ;comment\n xyzzy2 (rrr [_] \"ghi\") (sss [_] :abc) zzz)"
    {:parse-string? true, :list {:respect-nl? true}}))

(expect
  "(reify\n  xyzzy1 ;comment\n  xyzzy2\n    (rrr [_] \"ghi\")\n    (sss [_] :abc)\n  zzz)"
  (zprint-str
    "(reify xyzzy1 ;comment\n xyzzy2 (rrr [_] \"ghi\") (sss [_] :abc) zzz)"
    {:parse-string? true, :list {:respect-nl? true}}))

;;
;; :arg1-extend tests
;;

(defprotocol ZprintProtocol
  ; an optional doc string
  "This is a test protocol for zprint!"
  ; method signatures
  (stuffx [this x y] "stuff docstring")
  (botherx [this] [this x] [this x y] "bother docstring")
  (foox [this baz] "foo docstring"))

(expect
  "(extend ZprintType\n  ZprintProtocol\n    {:bar (fn [x y] (list x y)), :baz (fn ([x] (str x)) ([x y] (list x y)))})"
  (zprint-str
    "(extend ZprintType 
      ZprintProtocol {:bar (fn [x y] (list x y)), 
                      :baz (fn ([x] (str x)) ([x y] (list x y)))})"
    {:parse-string? true}))

(expect
  "(extend-type ZprintType\n  ZprintProtocol\n    (more [a b] (and a b))\n    (and-more ([a] (nil? a)) ([a b] (or a b))))"
  (zprint-str
    "(extend-type ZprintType 
      ZprintProtocol 
        (more [a b] (and a b)) 
        (and-more ([a] (nil? a)) ([a b] (or a b))))"
    {:parse-string? true}))

(expect
  "(extend-protocol ZprintProtocol\n  ZprintType\n    (more-stuff [x] (str x))\n    (more-bother [y] (list y))\n    (more-foo [z] (nil? z)))"
  (zprint-str
    "(extend-protocol ZprintProtocol 
      ZprintType 
        (more-stuff [x] (str x)) 
        (more-bother [y] (list y)) 
        (more-foo [z] (nil? z)))"
    {:parse-string? true}))

;;
;; :arg1-extend respect-nl? tests
;;

(expect
  "(extend\n  ZprintType\n  ZprintProtocol\n    {:bar (fn [x y] (list x y)),\n     :baz (fn ([x] (str x)) ([x y] (list x y)))})"
  (zprint-str
    "(extend \nZprintType\n      ZprintProtocol \n      {:bar (fn [x y] (list x y)),\n                      :baz (fn ([x] (str x)) ([x y] (list x y)))})"
    {:parse-string? true, :style :respect-nl}))

(expect
  "(extend-type ZprintType\n  ZprintProtocol\n    (more [a b]\n      (and a b))\n    (and-more ([a]\n               (nil? a))\n              ([a b]\n               (or a b))))"
  (zprint-str
    "(extend-type ZprintType\n      ZprintProtocol\n        (more [a b] \n\t(and a b))\n        (and-more ([a] \n\t(nil? a)) ([a b] \n\t(or a b))))"
    {:parse-string? true, :style :respect-nl}))

(expect
  "(extend-protocol ZprintProtocol\n  ZprintType\n\n    (more-stuff [x] (str x))\n\n    (more-bother [y] (list y))\n\n    (more-foo [z] (nil? z)))"
  (zprint-str
    "(extend-protocol ZprintProtocol\n      ZprintType\n\n        (more-stuff [x] (str x))\n\n        (more-bother [y] (list y))\n\n        (more-foo [z] (nil? z)))"
    {:parse-string? true, :style :respect-nl}))

;;
;; :extend tests for stuff (e.g. comments) in difficult places in lists
;;

(expect
  "(;stuff\n reify\n  ;bother\n  xyzzy1\n  ;foo\n  xyzzy2\n    ;bar\n    (rrr [_] \"ghi\"))"
  (zprint-str
    "(;stuff \n reify \n;bother\n xyzzy1 \n;foo\n xyzzy2 \n;bar\n (rrr [_] \"ghi\"))"
    {:parse-string? true}))

(expect
  "(;stuff\n reify\n  ;bother\n  xyzzy1\n  ;foo\n  xyzzy2\n    ;bar\n    (;baz\n     rrr [_]\n      \"ghi\"))"
  (zprint-str
    "(;stuff \n reify \n;bother\n xyzzy1 \n;foo\n xyzzy2 \n;bar\n (;baz\n rrr [_] \"ghi\"))"
    {:parse-string? true}))

;;
;; :fn tests for comments in difficult places
;;

(expect
  "(;does\n fn [a b c]\n  (;work\n   let ;at all\n    [a b\n     c d\n     e f]\n    (list a c e)))"
  (zprint-str
    "(;does\nfn [a b c] (;work\nlet ;at all\n [a b c d e f] (list a c e)))"
    {:parse-string? true, :width 30}))

;;
;; :arg1-extend tests for comments in difficult places
;;

(expect
  "(;is this a problem?\n extend ; and what about this?\n  ZprintType\n  ZprintProtocol\n    {:bar (;this\n           let ;is\n            [x y a b c d]\n            (let [a b\n                  c d\n                  e f\n                  g h]\n              x\n              y)),\n     :baz (fn ([x] (str x)) ([x y] (list x y)))})"
  (zprint-str
    "(;is this a problem?\n            extend ; and what about this?\n\t    ZprintType\n      ZprintProtocol {:bar (;this\n     let ;is\n      [x y a b c d] (let [a b c d e f g h] x y)),\n                      :baz (fn ([x] (str x)) ([x y] (list x y)))})"
    {:parse-string? true}))

;;
;; :arg2 test
;;

(expect "(as-> (list :a) x\n  (repeat 5 x)\n  (do (println x) x)\n  (nth x 2))"
        (zprint-str
          "(as-> (list :a) x (repeat 5 x) (do (println x) x) (nth x 2))"
          {:parse-string? true, :width 20}))

;;
;; :arg2 test that includes test for handling third argument correctly
;; and for handling indent on comments when they are not inline
;;

(expect
  "(;stuff\n as-> ;foo\n  (list :a) ;bar\n  x ;baz\n  (repeat 5 x)\n  (do (println x) x)\n  (nth x 2))"
  (zprint-str
    "(;stuff\nas-> ;foo\n (list :a) ;bar\n x ;baz\n (repeat 5 x) (do (println x) x) (nth x 2))"
    {:parse-string? true, :width 20, :comment {:inline? true}}))

(expect
  "(;stuff\n as->\n  ;foo\n  (list :a)\n  ;bar\n  x\n  ;baz\n  (repeat 5 x)\n  (do (println x) x)\n  (nth x 2))"
  (zprint-str
    "(;stuff\nas-> ;foo\n (list :a) ;bar\n x ;baz\n (repeat 5 x) (do (println x) x) (nth x 2))"
    {:parse-string? true, :width 20, :comment {:inline? false}}))

;;
;; Some more :arg2 testing, looking at where the second arg shows up based
;; on the line count of the first two args.
;;

(expect
  "(as->\n  (list\n    :a)\n  x\n  (repeat\n    5\n    x)\n  (do (println x) x)\n  (nth x 2))"
  (zprint-str
    "(as-> \n (list \n:a) x (repeat \n 5 x) (do (println x) x) (nth x 2))"
    {:parse-string? true,
     :width 20,
     :dbg? false,
     :comment {:inline? true},
     :style :respect-nl}))

(expect
  "(as-> ;foo\n  (list :a)\n  x\n  (repeat 5 x)\n  (do (println x) x)\n  (nth x 2))"
  (zprint-str
    "(as-> ;foo\n (list :a)  x  (repeat 5 x) (do (println x) x) (nth x 2))"
    {:parse-string? true,
     :width 20,
     :dbg? false,
     :comment {:inline? true},
     :style :respect-nl}))

;;
;; :arg2-fn test -- proxy is the only example
;;

(expect
  "(proxy [Stuff] []\n  (configure [a b])\n  (myfn [c d]\n    (let [e c f d] (list (+ e f) c d))))"
  (zprint-str
    "(proxy [Stuff] []\n  (configure [a b])\n  (myfn [c d]\n    (let [e c\n          f d]\n      (list (+ e f) c d))))"
    {:parse-string? true, :width 40}))

;;
;; :arg2-fn with respect-nl
;;

(expect
  "(proxy [Stuff] []\n  (configure [a b])\n  (myfn [c d]\n    (let [e c\n          f d]\n      (list (+ e f) c d))))"
  (zprint-str
    "(proxy [Stuff] []\n  (configure [a b])\n  (myfn [c d]\n    (let [e c f d]\n      (list (+ e f) c d))))"
    {:parse-string? true, :style :respect-nl, :width 40}))

;;
;; :arg2-extend 
;;

(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this) (list this this) (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str
    "(deftype Typetest1 [cnt _meta]\n\n  clojure.lang.IHashEq\n    (hasheq [this] \n      (list this) \n      (list this this) \n      (list this this this this))\n\n  clojure.lang.Counted\n    (count [_] cnt)\n\n  clojure.lang.IMeta\n    (meta [_] _meta))"
    {:parse-string? true}))

;;
;; :arg2-extend with :respect-nl
;;

(expect
  "(deftype Typetest1 [cnt _meta]\n\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n\n  clojure.lang.Counted\n    (count [_] cnt)\n\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-str
    "(deftype Typetest1 [cnt _meta]\n\n  clojure.lang.IHashEq\n    (hasheq [this] \n      (list this) \n      (list this this) \n      (list this this this this))\n\n  clojure.lang.Counted\n    (count [_] cnt)\n\n  clojure.lang.IMeta\n    (meta [_] _meta))"
    {:parse-string? true, :style :respect-nl}))


;;
;; :arg2-pair
;;

(expect
  "(defn test-condp\n  [x y]\n  (;This is a test\n   condp = 1\n    1 :pass\n    2 :fail))"
  (zprint-str
    "(defn test-condp\n  [x y]\n  (;This is a test\n  condp \n  = 1\n  1 \n  :pass\n  2 :fail))"
    {:parse-string? true}))

;;
;; :arg2-pair with respect-nl
;;

(expect
  "(defn test-condp\n  [x y]\n  (;This is a test\n   condp\n    =\n    1\n    1\n      :pass\n    2 :fail))"
  (zprint-str
    "(defn test-condp\n  [x y]\n  (;This is a test\n  condp \n  = 1\n  1 \n  :pass\n  2 :fail))"
    {:parse-string? true, :style :respect-nl}))

;;
;; :arg1-extend with second argument a vector.  No know uses as of 7/20/19.
;;

(expect
  "(;comment1\n this ;comment2\n  [a b c]\n  ;comment3\n  Protocol\n    (should cause it to not fit on one line)\n    (and more test)\n    (and more test)\n    (and more test))"
  (zprint-str
    "(;comment1 \nthis ;comment2\n\n [a \nb c]\n ;comment3\n Protocol\n\n (should cause it to not fit on one line) (and more test) (and more test) (and more test))"
    {:parse-string? true, :fn-map {"this" :arg1-extend}}))

;;
;; :arg1-extend with second argument a vector.  No know uses as of 7/20/19.
;; This time with :respect-nl
;;

(expect
  "(;comment1\n this ;comment2\n\n  [a\n   b c]\n  ;comment3\n  Protocol\n\n    (should cause it to not fit on one line)\n    (and more test)\n    (and more test)\n    (and more test))"
  (zprint-str
    "(;comment1 \nthis ;comment2\n\n [a \nb c]\n ;comment3\n Protocol\n\n (should cause it to not fit on one line) (and more test) (and more test) (and more test))"
    {:parse-string? true, :fn-map {"this" :arg1-extend}, :style :respect-nl}))

;;
;; :arg1 with comments
;;

(expect
  "(;does :arg1 work now?\n defn test-condp\n  [x y]\n  (;This is a test\n   condp = 1\n    1 :pass\n    2 :fail))"
  (zprint-str
    "(;does :arg1 work now?\ndefn test-condp\n  [x y]\n  (;This is a test\n  condp \n  = 1\n  1 \n  :pass\n  2 :fail))"
    {:parse-string? true}))

;;
;; :arg1 with more comments
;;

(expect
  "(;does :arg1 work now?\n defn ;how does this work?\n  test-condp\n  [x y]\n  (;This is a test\n   condp = 1\n    1 :pass\n    2 :fail))"
  (zprint-str
    "(;does :arg1 work now?\ndefn ;how does this work?\ntest-condp\n  [x y]\n  (;This is a test\n  condp \n  = 1\n  1 \n  :pass\n  2 :fail))"
    {:parse-string? true}))

;;
;; :arg1 with more comments and :respect-nl
;;

(expect
  "(;does :arg1 work now?\n defn ;how does this work?\n  test-condp\n  [x y]\n  (;This is a test\n   condp\n    =\n    1\n    1\n      :pass\n    2 :fail))"
  (zprint-str
    "(;does :arg1 work now?\ndefn ;how does this work?\ntest-condp\n  [x y]\n  (;This is a test\n  condp \n  = 1\n  1 \n  :pass\n  2 :fail))"
    {:parse-string? true, :style :respect-nl}))

;;
;; :arg1-force-nl
;;

(expect "(defprotocol P\n  (foo [this])\n  (bar-me [this] [this y]))"
        (zprint-str "(defprotocol P (foo [this]) (bar-me [this] [this y]))"
                    {:parse-string? true}))


;;
;; :arg1-force-nl with comments
;;

(expect
  "(;stuff\n defprotocol\n  ;bother\n  P\n  (foo [this])\n  (bar-me [this] [this y]))"
  (zprint-str
    "(;stuff\ndefprotocol\n ;bother\nP (foo [this]) \n\n(bar-me [this] [this y]))"
    {:parse-string? true}))

;;
;; :arg1-force-nl with comments and respect-nl
;;

(expect
  "(;stuff\n defprotocol\n  ;bother\n  P\n  (foo [this])\n\n  (bar-me [this] [this y]))"
  (zprint-str
    "(;stuff\ndefprotocol\n ;bother\nP (foo [this]) \n\n(bar-me [this] [this y]))"
    {:parse-string? true, :style :respect-nl}))

;;
;; :arg1-pair
;;

(expect
  "(assoc {}\n  :this :is\n  :a :test\n  :but-it-has-to-be :pretty-long-or-it-will\n  :all-fit-on :one-line)"
  (zprint-str
    "(assoc {} :this :is :a :test :but-it-has-to-be :pretty-long-or-it-will :all-fit-on :one-line)"
    {:parse-string? true}))

;;
;; :arg1-pair with comments
;;

(expect
  "(;comment1\n assoc {} ;comment3\n  :this :is\n  :a :test\n  :but-it-has-to-be :pretty-long-or-it-will\n  :all-fit-on :one-line)"
  (zprint-str
    "(;comment1\nassoc {} ;comment3\n:this :is \n\n:a :test :but-it-has-to-be :pretty-long-or-it-will :all-fit-on :one-line)"
    {:parse-string? true}))

;;
;; :arg1-pair with more comments
;;

(expect
  "(;comment1\n assoc ;comment2\n  {} ;comment3\n  :this :is\n  :a :test\n  :but-it-has-to-be :pretty-long-or-it-will\n  :all-fit-on :one-line)"
  (zprint-str
    "(;comment1\nassoc ;comment2\n\n{} ;comment3\n:this :is \n\n:a :test :but-it-has-to-be :pretty-long-or-it-will :all-fit-on :one-line)"
    {:parse-string? true}))

;;
;; :arg1-pair with more comments and respect-nl
;;

(expect
  "(;comment1\n assoc ;comment2\n\n  {} ;comment3\n  :this :is\n\n  :a :test\n  :but-it-has-to-be :pretty-long-or-it-will\n  :all-fit-on :one-line)"
  (zprint-str
    "(;comment1\nassoc ;comment2\n\n{} ;comment3\n:this :is \n\n:a :test :but-it-has-to-be :pretty-long-or-it-will :all-fit-on :one-line)"
    {:parse-string? true, :style :respect-nl}))

;;
;; Make sure that len = 1 works with lots of comments, after changing len
;; in fzprint-list* to be the length of the "good stuff".
;;

(expect "(;precomment\n one;postcomment\n)"
        (zprint-str "(;precomment\n one;postcomment\n)" {:parse-string? true}))


;;
;; :arg1-mixin tests for comments in odd places and :respect-nl
;;

(expect
  "(;comment 1\n rum/defcs ;comment 2\n  component\n  ;comment 3\n  \"This is a component with a doc-string!  How unusual...\"\n  ;comment 4\n  < ;comment 5\n    rum/static\n    rum/reactive\n    ;comment 6\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n    ;comment 7\n  [state label]\n  ;comment 8\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(;comment 1\n  rum/defcs ;comment 2\n  component\n\n  ;comment 3\n  \"This is a component with a doc-string!  How unusual...\"\n  ;comment 4\n  < ;comment 5\n\n  rum/static\n                       rum/reactive\n\t\t       ;comment 6\n                       (rum/local 0 :count)\n\n                       (rum/local \"\" :text)\n  ;comment 7\n  [state label]\n  ;comment 8\n  (let [count-atom (:count state)\n        text-atom  (:text state)]\n    [:div]))"
    {:parse-string? true}))

(expect
"(;comment 1\n rum/defcs ;comment 2\n  component\n\n  ;comment 3\n  \"This is a component with a doc-string!  How unusual...\"\n  ;comment 4\n  < ;comment 5\n\n    rum/static\n    rum/reactive\n    ;comment 6\n    (rum/local 0 :count)\n\n    (rum/local \"\" :text)\n    ;comment 7\n  [state label]\n  ;comment 8\n  (let [count-atom (:count state)\n        text-atom (:text state)]\n    [:div]))"
  (zprint-str
    "(;comment 1\n  rum/defcs ;comment 2\n  component\n\n  ;comment 3\n  \"This is a component with a doc-string!  How unusual...\"\n  ;comment 4\n  < ;comment 5\n\n  rum/static\n                       rum/reactive\n\t\t       ;comment 6\n                       (rum/local 0 :count)\n\n                       (rum/local \"\" :text)\n  ;comment 7\n  [state label]\n  ;comment 8\n  (let [count-atom (:count state)\n        text-atom  (:text state)]\n    [:div]))"
    {:parse-string? true, :style :respect-nl}))

;;
;; :respect-nl? tests for sets
;;
;; First, without :respect-nl?

(expect
  "#{:arg1 :arg1-> :arg1-body :arg1-extend :arg1-force-nl :arg1-pair\n  :arg1-pair-body :arg2 :arg2-extend :arg2-fn :arg2-pair :binding :extend :flow\n  :flow-body :fn :force-nl :force-nl-body :gt2-force-nl :gt3-force-nl :hang\n  :noarg1 :noarg1-body :none :none-body :pair :pair-fn}"
  (zprint-str
    "#{:binding :arg1 \n  :arg1-body :arg1-pair-body \n  :arg1-pair :pair \n  :hang :extend\n    :arg1-extend :fn \n    :arg1-> :noarg1-body \n    :noarg1 :arg2 \n    :arg2-extend :arg2-pair\n    :arg2-fn :none \n    :none-body :arg1-force-nl \n    :gt2-force-nl :gt3-force-nl \n    :flow :flow-body \n    :force-nl-body \n    :force-nl :pair-fn}"
    {:parse-string? true}))

;;
;; Then with :respect-nl? and a set
;;

(expect
  "#{:binding :arg1\n  :arg1-body :arg1-pair-body\n  :arg1-pair :pair\n  :hang :extend\n  :arg1-extend :fn\n  :arg1-> :noarg1-body\n  :noarg1 :arg2\n  :arg2-extend :arg2-pair\n  :arg2-fn :none\n  :none-body :arg1-force-nl\n  :gt2-force-nl :gt3-force-nl\n  :flow :flow-body\n  :force-nl-body\n  :force-nl :pair-fn}"
  (zprint-str
    "#{:binding :arg1 \n  :arg1-body :arg1-pair-body \n  :arg1-pair :pair \n  :hang :extend\n    :arg1-extend :fn \n    :arg1-> :noarg1-body \n    :noarg1 :arg2 \n    :arg2-extend :arg2-pair\n    :arg2-fn :none \n    :none-body :arg1-force-nl \n    :gt2-force-nl :gt3-force-nl \n    :flow :flow-body \n    :force-nl-body \n    :force-nl :pair-fn}"
    {:parse-string? true, :set {:respect-nl? true}}))

;;
;; fzprint-meta, :meta, with :respect-nl.
;;

(expect "(.getName ^clojure.lang.Symbol name)"
        (zprint-str "(.getName ^clojure.lang.Symbol\n name)"
                    {:parse-string? true}))

(expect "(.getName ^clojure.lang.Symbol\n          name)"
        (zprint-str "(.getName ^clojure.lang.Symbol\n name)"
                    {:parse-string? true, :style :respect-nl}))

;;
;; # Fidelity tests :respect-nl and :indent-only
;;


#_(expect
  (trim-gensym-regex (read-string (source-fn 'zprint.zprint/fzprint-list*)))
  (trim-gensym-regex (read-string (zprint-fn-str zprint.zprint/fzprint-list*
                                                 {:style :respect-nl}))))

(expect (trim-gensym-regex (read-string zprint.zprint-test/fzprint-list*str))
        (trim-gensym-regex
          (read-string (zprint-str zprint.zprint-test/fzprint-list*str
                                   {:parse-string? true, :style :respect-nl}))))

#_(expect
  (trim-gensym-regex (read-string (source-fn 'zprint.zprint/fzprint-list*)))
  (trim-gensym-regex (read-string (zprint-fn-str zprint.zprint/fzprint-list*
                                                 {:style :indent-only}))))
(expect (trim-gensym-regex (read-string zprint.zprint-test/fzprint-list*str))
        (trim-gensym-regex
          (read-string (zprint-str zprint.zprint-test/fzprint-list*str
                                   {:parse-string? true,
                                    :style :indent-only}))))

;;
;; # INDENT ONLY TESTS
;;

(expect "(;this is\n fn arg1\n  arg2\n  arg3)"
        (zprint-str "(;this is\nfn arg1 arg2 arg3)" {:parse-string? true}))

(expect "(;this is\n fn arg1 arg2 arg3)"
        (zprint-str "(;this is\nfn arg1 arg2 arg3)"
                    {:parse-string? true, :style :indent-only}))

(expect "(;this is\n fn arg1\n  ;comment2\n  arg2\n  arg3)"
        (zprint-str "(;this is\nfn arg1 \n;comment2\narg2 arg3)"
                    {:parse-string? true}))

(expect "(;this is\n fn arg1\n  ;comment2\n  arg2 arg3)"
        (zprint-str "(;this is\nfn arg1 \n;comment2\narg2 arg3)"
                    {:parse-string? true, :style :indent-only}))

(expect "(this is a test)"
        (zprint-str "\n(this is\n      a\n   test)" {:parse-string? true}))

(expect "(this is\n      a\n      test)"
        (zprint-str "\n(this is\n      a\n   test)"
                    {:parse-string? true, :style :indent-only}))

(expect "(;comment 1\n this is\n      a\n      test)"
        (zprint-str "\n(;comment 1\n this is\n      a\n   test)"
                    {:parse-string? true}))

(expect "(;comment 1\n this is\n      a\n      test)"
        (zprint-str "\n(;comment 1\n this is\n      a\n   test)"
                    {:parse-string? true, :style :indent-only}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n this is\n      a\n      test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n      a\n   test)"
    {:parse-string? true}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n\n this is\n      a\n      test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n      a\n   test)"
    {:parse-string? true, :style :indent-only}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n a this\n   is\n   a\n   test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n a\n this is\n      a\n   test)"
    {:parse-string? true}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n\n a\n  this is\n  a\n  test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n a\n this is\n      a\n   test)"
    {:parse-string? true, :style :indent-only}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n this is\n      a\n      test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n\n      a\n   test)"
    {:parse-string? true}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n\n this is\n\n      a\n      test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n\n      a\n   test)"
    {:parse-string? true, :style :indent-only}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n this is\n      ; comment 4\n      a\n      test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n ; comment 4\n      a\n   test)"
    {:parse-string? true}))

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n\n this is\n      ; comment 4\n      a\n      test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n ; comment 4\n      a\n   test)"
    {:parse-string? true, :style :indent-only}))

(expect "(\n this is\n      a\n      test)"
        (zprint-str "\n(\n this is\n      a\n   test)"
                    {:parse-string? true, :style :indent-only}))

(expect "(\n this is\n  a\n  test)"
        (zprint-str "\n(\n this is\n     a\n   test)"
                    {:parse-string? true, :style :indent-only}))

;;
;; Can we turn off hang detection in the input?
;;

(expect
  "(;comment 1\n ;comment 2\n ;comment 3\n\n this is\n  ; comment 4\n  a\n  test)"
  (zprint-str
    "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n ; comment 4\n      a\n   test)"
    {:parse-string? true,
     :style :indent-only,
     :list {:indent-only-style :none}}))

;;
;; # Vectors and indent-only
;;

(expect "[this is\n a\n test]"
        (zprint-str "\n[this is\n      a\n   test]"
                    {:parse-string? true, :style :indent-only}))

(expect "[this is\n a\n test]"
        (zprint-str "\n[this is\n     a\n   test]"
                    {:parse-string? true, :style :indent-only}))

(expect
  "[[[\"(\" :none :left]] [[\"a\" :none :element]] [[\"b\" :none :element]]\n [[\"x\" :none :newline]]\n [[\"c\" :none :element]]\n [[\"x\" :none :newline] [\"x\" :none :newline]]\n [[\")\" :none :right]]]"
  (zprint-str
    "[[[\"(\" :none :left]] [[\"a\" :none :element]] [[\"b\" :none :element]]\n           [[\"x\" :none :newline]]\n\t   [[\"c\" :none :element]]\n           [[\"x\" :none :newline] [\"x\" :none :newline]]\n\t   [[\")\" :none :right]]])"
    {:parse-string? true, :style :indent-only}))

(expect
"[\"This is the first string and it is very long and if it is that long then it seems they are together?\"\n\n \"Second string\" [\"this\" is the\n                  third thing\n                  fourth thing\n                  [even deeper\n                   and deeper\n                   with depth]]\n \"Third string\"]"
  (zprint-str
    "[\"This is the first string and it is very long and if it is that long then it seems they are together?\"\n\n\"Second string\" [\"this\" is the\nthird thing\nfourth thing\n[even deeper\nand deeper\nwith depth]]\n\"Third string\"]"
    {:parse-string? true, :style :indent-only}))

(expect
"(defstuff stuff\n  [a b]\n  {:stuff\n   [_another_symbol\n    [:1\n     \"This is the first string and it is very long and if it is that long then it seems they are together?\"\n     \"This is the second string and it is very long and if it is that long then it seems they are together?\"]\n\n    _this_is_also_a_symbol\n    [\"This is the first string and it is very long and if it is that long then it seems they are together?\"\n     _this_is_a_symbol\n     \"This is the second string and it is very long and if it is that long then it seems they are together?\"\n     \"Third string\"]\n   ]})"
  (zprint-str
    "(defstuff stuff\n[a b]\n{:stuff\n[_another_symbol\n[:1\n\"This is the first string and it is very long and if it is that long then it seems they are together?\"\n\"This is the second string and it is very long and if it is that long then it seems they are together?\"]\n\n_this_is_also_a_symbol\n[\"This is the first string and it is very long and if it is that long then it seems they are together?\"\n_this_is_a_symbol\n\"This is the second string and it is very long and if it is that long then it seems they are together?\"\n\"Third string\"]\n]})"
    {:parse-string? true, :style :indent-only}))

(expect
  "[jfkdsfkl jfdljfks sdkfjdslk\n [dlkfdks sdjklfds jsdfsldk\n  [jdskfdls dskjlfsd lksfjlsdk\n   [jkdlf sdfjkds sdfksk\n    [jfdklsdjf jsdkfsdj lkjsdjfsj]\n    lkdfjsdk slfjkldfj]\n   jfkldjlskf jsldkjfl]\n  jdlfjdsklsjkldfjs jdlsfjsld]\n jsldkfjsdkl fsjdkljsld]"
  (zprint-str
    " [jfkdsfkl jfdljfks sdkfjdslk \n   [dlkfdks sdjklfds jsdfsldk\n     [jdskfdls dskjlfsd lksfjlsdk \n       [jkdlf sdfjkds sdfksk\n         [jfdklsdjf jsdkfsdj lkjsdjfsj]\n\t lkdfjsdk slfjkldfj]\n\t jfkldjlskf jsldkjfl]\n\t jdlfjdsklsjkldfjs jdlsfjsld]\n\t jsldkfjsdkl fsjdkljsld]"
    {:parse-string? true, :style :indent-only}))

;;
;; # Indent Only for Maps
;;

(expect "{:a\n :b :c :d :e :f}"
        (zprint-str "{:a \n :b :c :d :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "{:a :b\n :c :d :e :f}"
        (zprint-str "{:a :b \n :c :d :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "{:a :b\n\n :c :d :e :f}"
        (zprint-str "{:a :b \n\n :c :d :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "{:a\n\n :b\n :c :d\n :e :f}"
        (zprint-str "{:a \n\n :b \n :c :d \n :e :f}"
                    {:parse-string? true, :style :indent-only}))
(expect "{;stuff\n :a :b ;bother\n :c :d\n :e :f}"
        (zprint-str "{;stuff\n :a :b ;bother\n :c :d \n :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "{;stuff\n\n :a :b ;bother\n :c\n\n :d\n :e :f}"
        (zprint-str "{;stuff\n\n :a :b ;bother\n :c \n\n :d \n :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
        (zprint-str "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "{:a\n\n :b\n :c {:a :b\n\n     :c\n     :d :e :f}\n :e :f}"
        (zprint-str "{:a \n\n :b \n :c {:a :b \n\n :c \n :d :e :f} \n :e :f}"
                    {:parse-string? true, :style :indent-only}))

;;
;; # Indent Only for Sets
;;

(expect "#{:a\n  :b :c :d :e :f}"
        (zprint-str "#{:a \n :b :c :d :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "#{:a :b\n  :c :d :e :f}"
        (zprint-str "#{:a :b \n :c :d :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "#{:a :b\n\n  :c :d :e :f}"
        (zprint-str "#{:a :b \n\n :c :d :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "#{:a\n\n  :b\n  :c :d\n  :e :f}"
        (zprint-str "#{:a \n\n :b \n :c :d \n :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "#{;stuff\n  :a :b ;bother\n  :c :d\n  :e :f}"
        (zprint-str "#{;stuff\n :a :b ;bother\n :c :d \n :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "#{;stuff\n\n  :a :b ;bother\n  :c\n\n  :d\n  :e :f}"
        (zprint-str "#{;stuff\n\n :a :b ;bother\n :c \n\n :d \n :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect "#{;stuff\n\n  :a :b :c ;bother\n\n  :d ;foo\n\n  :e :f}"
        (zprint-str "#{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
                    {:parse-string? true, :style :indent-only}))

(expect
  "#{:a\n\n  :b\n  :c #{:a :b\n\n       :c\n       :d :e :f}\n  :e :f}"
  (zprint-str "#{:a \n\n :b \n :c #{:a :b \n\n :c \n :d :e :f} \n :e :f}"
              {:parse-string? true, :style :indent-only}))



;;
;; # Align inline comments
;;
;; :inline-align-style :none
;;

(expect
  "(def x\n  zprint.zfns/zstart\n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test)\n          (this is the result and it is too long) ; inline comment\n        (this is a second test)\n          (and this is another test that is way too very long)        ; inline\n                                                                      ; comment\n                                                                      ; 2\n        :else (stuff bother)) ; inline comment 3\n  smiddle           ; Not an isolated inline comment\n  zprint.zfns/zend  ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
  (zprint-str
    "(def x\n  zprint.zfns/zstart \n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test) (this is the result and it is too long) ; inline comment\n  (this is a second test) (and this is another test that is way too very long)        ; inline comment 2\n  :else (stuff bother)) ; inline comment 3\n  smiddle           ; Not an isolated inline comment\n  zprint.zfns/zend  ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
    {:parse-string? true, :comment {:inline-align-style :none}}))

;;
;; :inline-align-style :aligned
;;

(expect
  "(def x\n  zprint.zfns/zstart\n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test)\n          (this is the result and it is too long)              ; inline comment\n        (this is a second test)\n          (and this is another test that is way too very long) ; inline comment\n                                                               ; 2\n        :else (stuff bother)) ; inline comment 3\n  smiddle          ; Not an isolated inline comment\n  zprint.zfns/zend ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
  (zprint-str
    "(def x\n  zprint.zfns/zstart \n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test) (this is the result and it is too long) ; inline comment\n  (this is a second test) (and this is another test that is way too very long)        ; inline comment 2\n  :else (stuff bother)) ; inline comment 3\n  smiddle           ; Not an isolated inline comment\n  zprint.zfns/zend  ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
    {:parse-string? true, :comment {:inline-align-style :aligned}}))

;;
;; :inline-align-style :consecutive
;;

(expect
  "(def x\n  zprint.zfns/zstart\n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test)\n          (this is the result and it is too long) ; inline comment\n        (this is a second test)\n          (and this is another test that is way too very long) ; inline comment\n                                                               ; 2\n        :else (stuff bother))                                  ; inline comment\n                                                               ; 3\n  smiddle                                                      ; Not an isolated\n                                                               ; inline comment\n  zprint.zfns/zend                                             ; contiguous\n                                                               ; inline comments\n  sdlfksdj                                                     ; inline comment\n  fdslfk                                                       ; inline comment\n                                                               ; aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false)   ; this only works because lists, anon-fn's, etc. are\n                       ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
  (zprint-str
    "(def x\n  zprint.zfns/zstart \n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test) (this is the result and it is too long) ; inline comment\n  (this is a second test) (and this is another test that is way too very long)        ; inline comment 2\n  :else (stuff bother)) ; inline comment 3\n  smiddle           ; Not an isolated inline comment\n  zprint.zfns/zend  ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
    {:parse-string? true, :comment {:inline-align-style :consecutive}}))


;;
;; # can you change :respect-nl? in a fn-map?
;;

(expect
  "(comment (defn x [y] (println y))\n         (this is a thing that is interesting)\n         (def z [:this-is-a-test :with-3-blanks-above?])\n         (def a :more stuff)\n         (def b :3-blanks-above))"
  (zprint-str
    "(comment\n(defn x\n  [y]\n  (println y))\n\n(this \n  is \n  a\n         thing that is interesting)\n\n(def z \n\n\n[:this-is-a-test :with-3-blanks-above?])\n\n(def a :more stuff)\n\n\n\n(def b :3-blanks-above))"
    {:parse-string? true}))

;;
;; Can we turn on :respect-nl for a function?

(expect
  "(comment\n  (defn x\n    [y]\n    (println y))\n\n  (this\n    is\n    a\n    thing\n    that\n    is\n    interesting)\n\n  (def z\n\n\n    [:this-is-a-test :with-3-blanks-above?])\n\n  (def a :more stuff)\n\n\n\n  (def b :3-blanks-above))"
  (zprint-str
    "(comment\n(defn x\n  [y]\n  (println y))\n\n(this \n  is \n  a\n         thing that is interesting)\n\n(def z \n\n\n[:this-is-a-test :with-3-blanks-above?])\n\n(def a :more stuff)\n\n\n\n(def b :3-blanks-above))"
    {:parse-string? true,
     :fn-map {"comment" [:none {:list {:respect-nl? true}}]}}))

;;
;; Can we turn it off so it only operates at the top level?
;;

(expect
  "(comment\n  (defn x [y] (println y))\n\n  (this is a thing that is interesting)\n\n  (def z [:this-is-a-test :with-3-blanks-above?])\n\n  (def a :more stuff)\n\n\n\n  (def b :3-blanks-above))"
  (zprint-str
    "(comment\n(defn x\n  [y]\n  (println y))\n\n(this \n  is \n  a\n         thing that is interesting)\n\n(def z \n\n\n[:this-is-a-test :with-3-blanks-above?])\n\n(def a :more stuff)\n\n\n\n(def b :3-blanks-above))"
    {:parse-string? true,
     :fn-map {"comment" [:none
                         {:list {:respect-nl? true},
                          :next-inner {:list {:respect-nl? false}}}]}}))

;;
;; Issue #39
;;
;; Full line comments becoming end of line comments
;;

(expect
  "[;first comment\n :a :b ; comment-inline 1\n :c :d\n ; comment one\n :e :f\n ; comment two\n]"
  (zprint-str
    "[;first comment\n   :a :b ; comment-inline 1\n   :c :d\n   ; comment one\n   :e :f\n   ; comment two\n   ]"
    {:parse-string? true}))

;;
;; Issue #86
;;
;; Getting the arg vector on the same line as the function name.
;;

(expect
  "(defn thefn [a b c]\n  (swap! this is (only a test))\n  (list a b c))"
  (zprint-str
    "(defn thefn\n  [a b c]\n  (swap! this is (only a test))\n  (list a b c))"
    {:parse-string? true,
     :width 80,
     :fn-map {"defn" [:arg2
                      {:fn-force-nl #{:arg2},
                       :next-inner {:remove {:fn-force-nl #{:arg2}}}}]}}))

(expect
  "(defn thefn [a b c]\n  (swap! this is\n    (only a test))\n  (list a b c))"
  (zprint-str
    "(defn thefn\n  [a b c]\n  (swap! this is (only a test))\n  (list a b c))"
    {:parse-string? true,
     :width 80,
     :fn-map {"defn" [:arg2 {:fn-force-nl #{:arg2}}]}}))

(expect
  "(defn thefn [a b c] (swap! this is (only a test)) (list a b c))"
  (zprint-str
    "(defn thefn\n  [a b c]\n  (swap! this is (only a test))\n  (list a b c))"
    {:parse-string? true, :width 80, :fn-map {"defn" [:arg2 {}]}}))

;;
;; # option-fn, fn-format tests for vectors
;;

(expect "[this is a test this is only a test]"
        (zprint-str "[this is a test this is only a test]"
                    {:parse-string? true}))

(expect
  "[this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test]"
  (zprint-str "[this is a test this is only a test]"
              {:parse-string? true,
               :vector {:option-fn #(if (= (first %3) 'this)
                                      {:vector {:fn-format :force-nl}})}}))

(expect "[this [is a\n       test this\n       is only]\n  (a test)]"
        (zprint-str "[this [is a test this is only] (a test)]"
                    {:parse-string? true,
                     :vector {:option-fn #(if (= (first %3) 'this)
                                            {:vector {:fn-format :binding}})}}))

(expect "[:arg1-force-nl :a\n  :b :c\n  :d :e\n  :f :g]"
        (zprint-str [:arg1-force-nl :a :b :c :d :e :f :g]
                    {:parse-string? false,
                     :vector {:option-fn #(do {:vector {:fn-format (first
                                                                     %3)}})}}))
(expect "[:arg2 a b\n  c\n  d\n  e\n  f\n  g]"
        (zprint-str "[:arg2 a b c d e f g]"
                    {:parse-string? true,
                     :vector {:option-fn #(do {:vector {:fn-format (first %3)},
                                               :fn-force-nl #{(first %3)}})}}))

(expect "[:force-nl :a\n           :b :c\n           :d :e\n           :f :g]"
        (zprint-str [:force-nl :a :b :c :d :e :f :g]
                    {:parse-string? false,
                     :vector {:option-fn #(do {:vector {:fn-format (first
                                                                     %3)}})}}))

(expect "[:pair a\n       b\n       c\n       d\n       e\n       f\n       g]"
        (zprint-str "[:pair a b c d e f g]"
                    {:parse-string? true,
                     :vector {:option-fn #(do {:vector {:fn-format (first %3)},
                                               :fn-force-nl #{(first %3)}})}}))

(expect "[:pair-fn a b\n          c d\n          e f\n          g]"
        (zprint-str "[:pair-fn a b c d e f g]"
                    {:parse-string? true,
                     :vector {:option-fn #(do {:vector {:fn-format (first
                                                                     %3)}})}}))

;;
;; # Issue #113
;;
;; Let zprint-file-str do things in color.
;;

(expect 55 (count (zprint-file-str "(a :b \"c\")" "test" {:color? true})))

(expect 10 (count (zprint-file-str "(a :b \"c\")" "test" {:color? false})))

;;
;; Can we control color with the rest of the functions?
;;

;; Establish that we have some difference between colored and non-colored

(expect 14 (count (zprint-str "(a :b \"c\")" {:color? false})))

(expect 23 (count (zprint-str "(a :b \"c\")" {:color? true})))

;; See if those differences match what we expect

(expect (czprint-str "(a :b \"c\")" {:color? true})
        (zprint-str "(a :b \"c\")" {:color? true}))

(expect (czprint-str "(a :b \"c\")") (zprint-str "(a :b \"c\")" {:color? true}))

(expect (czprint-str "(a :b \"c\")" {:color? false})
        (zprint-str "(a :b \"c\")" {:color? false}))

(expect (czprint-str "(a :b \"c\")" {:color? false})
        (zprint-str "(a :b \"c\")"))

(expect 15 (count (with-out-str (zprint "(a :b \"c\")" {:color? false}))))

(expect 24 (count (with-out-str (zprint "(a :b \"c\")" {:color? true}))))

;; See if those differences match what we expect

(expect (with-out-str (czprint "(a :b \"c\")" {:color? true}))
        (with-out-str (zprint "(a :b \"c\")" {:color? true})))

(expect (with-out-str (czprint "(a :b \"c\")"))
        (with-out-str (zprint "(a :b \"c\")" {:color? true})))

(expect (with-out-str (czprint "(a :b \"c\")" {:color? false}))
        (with-out-str (zprint "(a :b \"c\")" {:color? false})))

(expect (with-out-str (czprint "(a :b \"c\")" {:color? false}))
	(with-out-str (zprint "(a :b \"c\")")))

(def blanksstr
  "(defn blanks
  \"Produce a blank string of desired size.\"
  [n]
  (apply str (repeat n \" \")))")

(expect 92
        (count (zprint-str zprint.zprint-test/blanksstr {:parse-string? true})))

(expect 227
        (count (czprint-str zprint.zprint-test/blanksstr
                            {:parse-string? true})))

(expect (zprint-str zprint.zprint-test/blanksstr {:parse-string? true :color? false})
        (czprint-str zprint.zprint-test/blanksstr {:parse-string? true :color? false}))

(expect (zprint-str zprint.zprint-test/blanksstr {:parse-string? true})
        (czprint-str zprint.zprint-test/blanksstr {:parse-string? true :color? false}))

(expect (zprint-str zprint.zprint-test/blanksstr {:parse-string? true :color? true})
        (czprint-str zprint.zprint-test/blanksstr {:parse-string? true :color? true}))

(expect (zprint-str zprint.zprint-test/blanksstr {:parse-string? true :color? true})
        (czprint-str zprint.zprint-test/blanksstr {:parse-string? true}))

;;
;; Used to have with-out-str tests here, but they don't work on Windows
;; because of the different line endings.
;;

#_(expect 93 (count (with-out-str (zprint-fn zprint.zprint-test/blanksstr {:parse-string? true}))))

#_(expect 228 (count (with-out-str (czprint-fn zprint.zprint-test/blanksstr))))

;; etc.

;;
;; Used to have
;;
;; # Issue #118, :respect-nl not working inside of binding vectors
;;

(expect "(let [a\n        b\n      c d]\n  e)"
        (zprint-str "(let [a\nb\nc d] e)"
                    {:parse-string? true, :style :respect-nl}))

;;
;; # Issue #121
;;
;; Translate (quote a) to 'a.  But just for structures, not for code.
;;

;; Basic capability tests:

(expect "'a" (zprint-str '(quote a) {:style :backtranslate}))

; Should not change, since it is a zipper
(expect "(quote a)"
        (zprint-str "(quote a)" {:parse-string? true, :style :backtranslate}))

; Should change, since we explicitly did this for zippers
(expect "'a"
        (zprint-str "(quote a)"
                    {:parse-string? true,
                     :fn-map {"quote" [:replace-w-string
                                       {:list {:replacement-string "'"}} {}]}}))


(expect "#'a" (zprint-str '(var a) {:style :backtranslate}))

(expect "(var a)"
        (zprint-str "(var a)" {:parse-string? true, :style :backtranslate}))

(expect "#'a"
        (zprint-str "(var a)"
                    {:parse-string? true,
                     :fn-map {"var" [:replace-w-string
                                     {:list {:replacement-string "#'"}} {}]}}))


(expect "@a" (zprint-str '(clojure.core/deref a) {:style :backtranslate}))

(expect "(clojure.core/deref a)"
        (zprint-str "(clojure.core/deref a)"
                    {:parse-string? true, :style :backtranslate}))

(expect "@a"
        (zprint-str "(clojure.core/deref a)"
                    {:parse-string? true,
                     :fn-map {"clojure.core/deref" [:replace-w-string
                                                    {:list {:replacement-string
                                                              "@"}} {}]}}))


(expect "~a" (zprint-str '(clojure.core/unquote a) {:style :backtranslate}))

(expect "(clojure.core/unquote a)"
        (zprint-str "(clojure.core/unquote a)"
                    {:parse-string? true, :style :backtranslate}))

(expect "~a"
        (zprint-str "(clojure.core/unquote a)"
                    {:parse-string? true,
                     :fn-map {"clojure.core/unquote"
                                [:replace-w-string
                                 {:list {:replacement-string "~"}} {}]}}))

;; Random test...

(expect
  "(this\n  is\n  a\n  test\n  (#'a\n    this\n    is\n    only)\n  a\n  test)"
  (zprint-str '(this is a test ((var a) this is only) a test)
              {:style :backtranslate, :width 10}))

;; What happens with comments when we do this with zippers/strings?

;; Nothing, because this does only structures

(expect "(;a\n quote ;b\n  a ;c\n)"
        (zprint-str "(;a\n quote ;b\n a ;c\n)"
                    {:parse-string? true,
                     :fn-map {"quote" [:replace-w-string {}
                                       {:list {:replacement-string "'"}}]}}))

;; A lot because we do it with both structures and code here

(expect "';a\n ;b\n a ;c\n"
        (zprint-str "(;a\n quote ;b\n a ;c\n)"
                    {:parse-string? true,
                     :fn-map {"quote" [:replace-w-string
                                       {:list {:replacement-string "'"}}]}}))

;; The original issues example:

;; What he found
(expect "[(quote x) (quote y)]" (zprint-str '['x 'y]))

;; What we will do now
(expect "['x 'y]" (zprint-str '['x 'y] {:style :backtranslate}))

;; How do we handle rightcnt?

(expect "(this is a thing (and more thing (and more 'a)))"
        (zprint-str '(this is a thing (and more thing (and more (quote a))))
                    {:fn-map {"quote" [:replace-w-string
                                       {:list {:replacement-string "'"}}]},
                     :width 48}))
(expect "(this is a thing (and more thing (and more 'a)))"
        (zprint-str "(this is a thing (and more thing (and more (quote a))))"
                    {:parse-string? true,
                     :fn-map {"quote" [:replace-w-string
                                       {:list {:replacement-string "'"}}]},
                     :width 48}))
(expect "(this is a thing (and more thing (and more 'a)))"
        (zprint-str "(this is a thing (and more thing (and more 'a)))"
                    {:parse-string? true, :width 48}))

;;
;; # Issue 132
;;
;; Problems with newlines and comments in vectors (and pretty
;; much everywhere, actually).
;;
;; Does :respect-nl? work in :vector if we are spliting the comments?
;;

(expect "(let [a\n        ;stuff\n        b\n      ;bother\n      c d]\n  e)"
        (zprint-str "(let [a\n ;stuff\n b\n;bother\nc d] e)"
                    {:parse-string? true, :vector {:respect-nl? true}}))

(expect "(let [a\n        ;stuff\n        b\n\n      ;bother\n      c d]\n  e)"
  (zprint-str "(let [a\n ;stuff\n b\n\n;bother\nc d] e)"
              {:parse-string? true, :vector {:respect-nl? true}}))

(expect "(let [a\n        ;stuff\n        b\n\n      ;bother\n      c d]\n  e)"
  (zprint-str "(let [a\n ;stuff\n b\n\n;bother\nc d] e)"
              {:parse-string? true, :vector {:respect-bl? true}}))

;;
;; # Issue 132 -- the real problem was the :vector {:wrap? false} takes out
;; any newlines.
;;

(expect "[;first\n\n a\n\n ;second\n\n b\n ;third\n c]"
        (zprint-str "[;first\n\na\n\n;second\n\nb\n;third\nc]"
                    {:parse-string? true,
                     :vector {:respect-nl? true, :wrap? false}}))

(expect "[;first\n\n a\n\n ;second\n\n b\n ;third\n c]"
        (zprint-str "[;first\n\na\n\n;second\n\nb\n;third\nc]"
                    {:parse-string? true,
                     :vector {:respect-bl? true, :wrap? false}}))

;;
;; # Issue 135 -- aligned inline comments don't work right with respect-nl
;;

(expect
  "(def x\n  zprint.zfns/zstart\n  sfirst\n  zprint.zfns/zanonfn?\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
  (zprint-str
    "(def x\n  zprint.zfns/zstart\n  sfirst\n  zprint.zfns/zanonfn?\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
    {:parse-string? true, :style :respect-nl}))

;;
;; # Issue 136 -- constant pairing count is off with comments:
;;

(expect "(;a\n list :b\n      :c ;def\n         ;aligned-inline\n      d)"
        (zprint-str "(;a\nlist\n:b\n:c ;def\n   ;aligned-inline\nd)"
                    {:parse-string? true}))

;;
;; # Issue 136 -- constant pairing count is off with newlines
;;

(expect "(;a\n list\n  :c\n\n  d)"
        (zprint-str "(;a\nlist\n:c\n\n d)"
                 {:parse-string? true, :style :respect-nl}))

(expect "(;a\n list :c\n\n      d)"
        (zprint-str "(;a\nlist\n:c\n\n d)"
                    {:parse-string? true, :style :respect-bl}))

;;
;; # Issue 137 -- last pair in a map has a comma when followed by a comment
;;

(expect "{;commenta\n a b\n ;commentx\n}"
        (zprint-str "{;commenta\na b\n;commentx\n}" {:parse-string? true}))

;;
;; # Issue 138 -- newline ignored when after last map element
;;

(expect "{a\n   b\n}"
        (zprint-str "{a\nb\n}" {:parse-string? true, :map {:respect-nl? true}}))

(expect 
	"{a b\n\n}"
        (zprint-str "{a\nb\n\n}"
                    {:parse-string? true, :map {:respect-bl? true}}))


;;
;; # Issue 139 -- comments in sets cause probems
;;

(expect "#{a\n  ;commentx\n  b ;commenty\n }"
        (zprint-str "#{a\n;commentx\n\nb ;commenty\n}" {:parse-string? true}))

(expect "#{a\n  ;commentx\n\n  b ;commenty\n }"
        (zprint-str "#{a\n;commentx\n\nb ;commenty\n}"
                    {:parse-string? true, :set {:respect-nl? true}}))

(expect "#{a\n  ;commentx\n\n  b ;commenty\n }"
        (zprint-str "#{a\n;commentx\n\nb ;commenty\n}"
                    {:parse-string? true, :set {:respect-bl? true}}))

;;
;; # Issue 141 -- comments in empty list are lost
;;

(expect "(;abc\n ;def\n)"
        (zprint-str "(;abc\n\n;def\n)" {:parse-string? true}))

(expect "(;abc\n\n ;def\n)"
        (zprint-str "(;abc\n\n;def\n)"
                    {:parse-string? true, :style :respect-nl}))

(expect "(;abc\n\n ;def\n)"
        (zprint-str "(;abc\n\n;def\n)"
                    {:parse-string? true, :style :respect-bl}))

;;
;; # Tests to see where the final right thing goes if it was preceded by
;; a blank line
;;

(expect "[a\n ;commentx\n\n b ;commenty\n\n]"
        (zprint-str "[a\n;commentx\n\nb ;commenty\n\n]"
                    {:parse-string? true, :vector {:respect-bl? true}}))

(expect "{a\n   ;commentx\n\n   b ;commenty\n\n}"
        (zprint-str "{a\n;commentx\n\nb ;commenty\n\n}"
                    {:parse-string? true, :map {:respect-bl? true}}))

(expect "#{a\n  ;commentx\n\n  b ;commenty\n\n }"
        (zprint-str "#{a\n;commentx\n\nb ;commenty\n\n}"
                    {:parse-string? true, :set {:respect-bl? true}}))

(expect "#(a ;commentx\n\n   b ;commenty\n\n )"
        (zprint-str "#(a\n;commentx\n\nb ;commenty\n\n)"
                    {:parse-string? true, :list {:respect-bl? true}}))

;;
;; # Tests to see where the final right thing goes if it was preceded by
;; a comment
;;

(expect "[a\n ;commentx\n\n b ;commenty\n]"
        (zprint-str "[a\n;commentx\n\nb ;commenty\n]"
                    {:parse-string? true, :vector {:respect-bl? true}}))

(expect "{a\n   ;commentx\n\n   b ;commenty\n}"
        (zprint-str "{a\n;commentx\n\nb ;commenty\n}"
                    {:parse-string? true, :map {:respect-bl? true}}))

(expect "#{a\n  ;commentx\n\n  b ;commenty\n }"
        (zprint-str "#{a\n;commentx\n\nb ;commenty\n}"
                    {:parse-string? true, :set {:respect-bl? true}}))

(expect "#(a ;commentx\n\n   b ;commenty\n )"
        (zprint-str "#(a\n;commentx\n\nb ;commenty\n)"
                    {:parse-string? true, :list {:respect-bl? true}}))


;;
;; # Issue 143
;;
;; Where to put a hanging closing right paren or whatever
;;
;; Also Issue #149.
;;

(expect
  "(a (b (c (d e\n            f ;stuff\n         )\n         h ;foo\n      ) ;bar\n   )\n   i\n   j)"
  (zprint-str "(a (b (c (d e f ;stuff\n) h ;foo\n) ;bar\n) i j)"
              {:parse-string? true}))

(expect "[a b c\n [d ;foo\n  e ;bar\n  [f g ;stuff\n  ] ;bother\n ] h i j]"
        (zprint-str "[a b c [d ;foo\n e ;bar\n [f g ;stuff\n] ;bother\n] h i j]"
                    {:parse-string? true}))

;;
;; Yes, and how does it work with indent-only and respect-nl?
;;
;; This is also a test for extra blank line when last thing in a list is a
;; comment.
;;

(expect "(a (b (c (d e f ;stuff\n         ) h ;foo\n      ) ;bar\n   ) i j)"
        (zprint-str "(a (b (c (d e f ;stuff\n) h ;foo\n) ;bar\n) i j)"
                    {:parse-string? true, :style :indent-only}))

(expect
  "(a (b (c (d e\n            f ;stuff\n         )\n         h ;foo\n      ) ;bar\n   )\n   i\n   j)"
  (zprint-str "(a (b (c (d e f ;stuff\n) h ;foo\n) ;bar\n) i j)"
              {:parse-string? true, :style :respect-nl}))

;;
;; Another trailing blank problem
;;

(expect "{:a :b\n :c\n   :ddfkdjflajfsdjlfdjldsjldjfdl\n\n :e :f}"
        (zprint-str "{:a :b \n :c :ddfkdjflajfsdjlfdjldsjldjfdl :e :f}"
                    {:parse-string? true,
                     :map {:comma? false, :nl-separator? true},
                     :width 10}))

;;
;; A "how much does the right paren indent when it is on a line by itself?"
;;

(expect "#(a\n   ;commentx\n\n   b ;commenty\n )"
        (zprint-str "#(a\n;commentx\n\nb ;commenty\n)"
                    {:parse-string? true, :style :respect-nl}))

;;
;; # Issue 131 -- long comment lines are wrapped even with indent-only
;;

(expect
  "(this ; is a test, this is only a test, and this is a long comment\n  is also a test)"
  (zprint-str
    "(this ; is a test, this is only a test, and this is a long comment\n is also a test)"
    {:parse-string? true, :width 40, :style :indent-only}))

;;
;; # Issue 144 -- zprint-file-str, uberjar, and binaries drop all but one
;; trailing newline.
;;

(expect "\n(a)\n\n" (zprint-file-str "\n(a)\n\n" "test"))

;;
;; # Issue #101 -- :interpolate splits comments, and distances them
;; from other elements.
;;

(expect
  "(ns foo)\n\n;abc\n\n;!zprint {:format :next :width 10}\n\n;  def ghi\n;  jkl mno\n;  pqr\n(defn baz\n  [])\n"
  (zprint-file-str
    "\n\n(ns foo)\n;abc\n\n;!zprint {:format :next :width 10}\n\n\n\n;  def ghi jkl mno pqr\n(defn baz [])\n\n\n"
    "junk"
    {:parse {:interpose "\n\n"}, :width 10}))

;;
;; # Issue 145 -- reader-conditionals don't work right with indent-only
;;                respect-nl too...
;;

(expect "#stuff/bother\n (list :this \"is\" a :test)"
        (zprint-str "#stuff/bother\n (list :this \"is\" a :test)"
                    {:parse-string? true, :style :indent-only}))

(expect "#stuff/bother (list :this \"is\" a :test)"
        (zprint-str "#stuff/bother\n (list :this \"is\" a :test)"
                    {:parse-string? true}))

(expect "#stuff/bother (list :this\n                \"is\" a :test)"
        (zprint-str "#stuff/bother (list :this\n \"is\" a :test)"
                    {:parse-string? true, :style :indent-only}))

(expect "#stuff/bother\n (list :this\n       \"is\"\n       a\n       :test)"
        (zprint-str "#stuff/bother (list :this\n \"is\" a :test)"
                    {:parse-string? true, :style :respect-nl}))

(expect
  "#?(:clj (defn zpmap ([f] (if x y z)))\n   :cljs (defn zpmap ([f] (if x y z))))"
  (zprint-str
    "#?(:clj (defn zpmap\n          ([f] \n\t   (if x y z)))\n    :cljs (defn zpmap\n          ([f] \n\t   (if x y z))))"
    {:parse-string? true}))

(expect
  "#?(:clj (defn zpmap\n          ([f]\n           (if x y z)))\n   :cljs (defn zpmap\n           ([f]\n            (if x y z))))"
  (zprint-str
    "#?(:clj (defn zpmap\n          ([f] \n\t   (if x y z)))\n    :cljs (defn zpmap\n          ([f] \n\t   (if x y z))))"
    {:parse-string? true, :style :respect-nl}))

(expect
  "#?(:clj (defn zpmap\n          ([f]\n           (if x y z)))\n   :cljs (defn zpmap\n           ([f]\n            (if x y z))))"
  (zprint-str
    "#?(:clj (defn zpmap\n	    ([f] \n\t	(if x y z)))\n	  :cljs (defn zpmap\n	       ([f] \n\t   (if x y z))))"
    {:parse-string? true, :style :indent-only}))

;;
;; This is related to the #145 issue, but it is about :prefix-tags.  The bigger
;; issue was that there were a lot of caller's that didn't have respect
;; or indent configured. 

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      #_\n        (aaaaaaa bbbbbbbb\n                 cccccccccc)\n      (ddddddddd eeeeeeeeee\n                 fffffffffff))"
  (zprint-str
    "(this is a test this is only a test #_\n(aaaaaaa bbbbbbbb cccccccccc)(ddddddddd eeeeeeeeee fffffffffff))"
    {:parse-string? true, :width 30, :style :respect-nl}))

(expect
  "(this is a test\n  this is only a test #_\n                        (aaaaaaa bbbbbbbb cccccccccc)\n  (ddddddddd eeeeeeeeee fffffffffff))"
  (zprint-str
    "(this is a test \nthis is only a test #_\n(aaaaaaa bbbbbbbb cccccccccc)\n(ddddddddd eeeeeeeeee fffffffffff))"
    {:parse-string? true, :width 30, :style :indent-only}))

(expect
  "(this\n  is\n  a\n  test\n  this\n\n  is\n  only\n  a\n  test\n  #_\n\n    (aaaaaaa bbbbbbbb\n             cccccccccc)\n  (ddddddddd\n\n    eeeeeeeeee\n    fffffffffff))"
  (zprint-str
    "(this is a test \nthis \n\nis only a test #_\n\n(aaaaaaa bbbbbbbb cccccccccc)(ddddddddd \n\neeeeeeeeee fffffffffff))"
    {:parse-string? true, :width 30, :style :respect-bl}))
;;
;; Discovered that I left out comma support for :indent-only for maps.
;; Tests did not discover this!
;; Now they will.
;;

(expect "{:a :b, :c :d, :e :f}"
        (zprint-str "{:a :b, :c :d, :e :f}"
                    {:parse-string? true, :style :indent-only}))
(expect "{:a :b, :c :d, :e :f}"
        (zprint-str "{:a :b, :c :d, :e :f}" {:parse-string? true}))

(expect "{:a :b, :c :d, :e :f}"
        (zprint-str "{:a :b, :c :d, :e :f}"
                    {:parse-string? true, :style :respect-nl}))

(expect "{:a :b, :c :d, :e :f}"
        (zprint-str "{:a :b, :c :d, :e :f}"
                    {:parse-string? true, :style :respect-bl}))

;;
;; Issue -- left-space :keep doesn't work for comments
;; #148
;;

(expect
  "\n\n(ns foo)\n;abc\n;!zprint {:format :next :width 20}\n       ;def ghi jkl\n       ;mno pqr\n   (defn baz [])\n\n\n"
  (zprint-file-str
    "\n\n(ns foo)\n;abc\n;!zprint {:format :next :width 20}\n       ;def ghi jkl mno pqr\n   (defn baz [])\n\n\n"
    "junk"
    {:parse {:interpose nil, :left-space :keep}, :width 30}))

(expect
  "\n    (defn\n      thisis\n      [a]\n      test)\n    ;def\n    ;ghi\n    ;jkl\n    ;mno\n    ;pqr\n"
  (zprint-file-str "\n    (defn thisis [a] test)\n    ;def ghi jkl mno pqr\n"
                   "junk"
                   {:parse {:interpose nil, :left-space :keep}, :width 10}))

(def test-fast-hangstr
"(defn test-fast-hang
  \"Try to bring inline comments back onto the line on which they belong.\"
  [{:keys [width], :as options} style-vec]
  (loop [cvec style-vec
         last-out [\"\" nil nil]
         out []]
    (if-not cvec
      (do #_(def fico out) out)
      (let [[s c e :as element] (first cvec)
            [_ _ ne nn :as next-element] (second cvec)
            [_ _ le] last-out
            new-element
              (cond
                (and (or (= e :indent) (= e :newline))
                     (= ne :comment-inline))
                  (if-not (or (= le :comment) (= le :comment-inline))
                    ; Regular line to get the inline comment
                    [(blanks nn) c :whitespace 25]
                    ; Last element was a comment...
                    ; Can't put a comment on a comment, but
                    ; we want to indent it like the last
                    ; comment.
                    ; How much space before the last comment?
                    (do #_(prn \"inline:\" (space-before-comment out))
                        [(str \"\\n\" (blanks out)) c                       
                         :indent 41]
                        #_element))
                :else element)]
        (recur (next cvec) new-element (conj out new-element))))))")

;;
;; # Test :style :fast-hang
;;

(expect
  "(defn test-fast-hang\n  \"Try to bring inline comments back onto the line on which they belong.\"\n  [{:keys [width], :as options} style-vec]\n  (loop [cvec style-vec\n         last-out [\"\" nil nil]\n         out []]\n    (if-not cvec\n      (do #_(def fico out) out)\n      (let [[s c e :as element] (first cvec)\n            [_ _ ne nn :as next-element] (second cvec)\n            [_ _ le] last-out\n            new-element\n              (cond (and (or (= e :indent) (= e :newline))\n                         (= ne :comment-inline))\n                      (if-not (or (= le :comment) (= le :comment-inline))\n                        ; Regular line to get the inline comment\n                        [(blanks nn) c :whitespace 25]\n                        ; Last element was a comment...\n                        ; Can't put a comment on a comment, but\n                        ; we want to indent it like the last\n                        ; comment.\n                        ; How much space before the last comment?\n                        (do #_(prn \"inline:\" (space-before-comment out))\n                            [(str \"\\n\" (blanks out)) c :indent 41]\n                            #_element))\n                    :else element)]\n        (recur (next cvec) new-element (conj out new-element))))))"
  (zprint-str zprint.zprint-test/test-fast-hangstr {:parse-string? true}))

(expect
  "(defn test-fast-hang\n  \"Try to bring inline comments back onto the line on which they belong.\"\n  [{:keys [width], :as options} style-vec]\n  (loop [cvec style-vec\n         last-out [\"\" nil nil]\n         out []]\n    (if-not cvec\n      (do #_(def fico out) out)\n      (let [[s c e :as element] (first cvec)\n            [_ _ ne nn :as next-element] (second cvec)\n            [_ _ le] last-out\n            new-element (cond\n                          (and (or (= e :indent) (= e :newline))\n                               (= ne :comment-inline))\n                            (if-not (or (= le :comment) (= le :comment-inline))\n                              ; Regular line to get the inline comment\n                              [(blanks nn) c :whitespace 25]\n                              ; Last element was a comment...\n                              ; Can't put a comment on a comment, but\n                              ; we want to indent it like the last\n                              ; comment.\n                              ; How much space before the last comment?\n                              (do #_(prn \"inline:\" (space-before-comment out))\n                                  [(str \"\\n\" (blanks out)) c :indent 41]\n                                  #_element))\n                          :else element)]\n        (recur (next cvec) new-element (conj out new-element))))))"
  (zprint-str zprint.zprint-test/test-fast-hangstr
              {:parse-string? true, :style :fast-hang}))

;;
;; # Issue #150 -- structures that start with nil don't print at all!
;;

(expect "(nil\n nil)" (zprint-str '(nil nil) {:width 5}))


;;
;; # PR 152 with colors for more elements
;;

(def element-color-tst "[true false #\"regex\" asymbol {:a :b, :c :d}]")

(expect
  [["[" :purple :left] ["true" :green :element] [" " :none :whitespace]
   ["false" :cyan :element] [" " :none :whitespace] ["#\"regex\"" :red :element]
   [" " :none :whitespace] ["asymbol" :magenta :element] [" " :none :whitespace]
   ["{" :red :left] [":a" :green :element] [" " :none :whitespace]
   [":b" :green :element] ["," :cyan :whitespace] [" " :none :whitespace]
   [":c" :green :element] [" " :none :whitespace] [":d" :green :element]
   ["}" :red :right] ["]" :purple :right]]
  (czprint-str element-color-tst
               {:parse-string? true,
                :color-map {:comma :cyan,
                            :symbol :magenta,
                            :true :green,
                            :false :cyan,
                            :keyword :green,
                            :regex :red},
                :return-cvec? true}))

;; Note that :char doesn't work in cljs, all chars are really strings

(def char-element "\\a")

#?(:clj (expect [["\\a" :blue :element]]
                (czprint-str char-element
                             {:parse-string? true,
                              :color-map {:char :blue},
                              :return-cvec? true}))
   :cljs
     ; Since strings are naturally red, this char is red as well even though
     ; the color-map calls for blue since cljs can't tell a char from a string
     ; and strings are checked for first.
     (expect [["\\a" :red :element]]
             (czprint-str char-element
                          {:parse-string? true,
                           :color-map {:char :blue},
                           :return-cvec? true})))




;;
;; Tests for :default entry to :fn-map -- Issue #155.
;;

(def i154e
  "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n(defn fn-2\n  [_]\n{:not     :quite\n       :formatted    :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))")

(expect
  "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n(defn fn-2\n  [_]\n  {:not :quite,\n   :formatted :properly})\n\n(defn fn-3\n  [woo]\n  (do (woo)))"
  (zprint-file-str i154e
                   "stuff"
                   {:fn-map {"defn" :none,
                             :default [:arg1 {:style :respect-nl}]}}))

(expect
  "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1 [arg] (println arg))\n\n; Comment 2\n\n(defn fn-2 [_] {:not :quite, :formatted :properly})\n\n(defn fn-3 [woo] (do (woo)))"
  (zprint-file-str i154e
                   "stuff"
                   {:fn-map {:default [:arg1 {:style :respect-nl}]}}))

(expect
  "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n      [arg]\n      (println arg))\n\n; Comment 2\n\n(defn fn-2\n      [_]\n      {:not :quite,\n       :formatted :properly})\n\n(defn fn-3\n      [woo]\n      (do (woo)))"
  (zprint-file-str i154e
                   "stuff"
                   {:fn-map {"defn" :none,
                             :default [:none {:style :respect-nl}]}}))

(expect
  "\n\n; Comment 1\n\n(ns test.zprint)\n\n(defn fn-1\n  [arg]\n  (println arg))\n\n; Comment 2\n\n(defn fn-2\n  [_]\n  {:not :quite\n   :formatted :properly})\n\n(defn fn-3 [woo]\n  (do (woo)))"
  (zprint-file-str i154e
                   "stuff"
                   {:fn-map {"defn" :none,
                             :default [:none {:style :indent-only}]}}))


;;
;; Test that :lift-ns? is by default false
;;

(expect
  "{:aliases {:cljs-runner {:extra-deps {expectations/cljc-test\n                                        {:mvn/version \"2.0.0-SNAPSHOT\"}}}}}"
  (zprint-str
    "{:aliases {:cljs-runner {:extra-deps {expectations/cljc-test {:mvn/version \"2.0.0-SNAPSHOT\"}}}}}"
    {:parse-string? true}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; End of defexpect
;;
;; All tests MUST come before this!!!
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 )

