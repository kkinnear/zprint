;!zprint {:style :require-justify :style-map {:rj-var {:pair {:justify {:max-variance 10}}}}}
(ns zprint.zprint-test
  (:require
    [expectations.clojure.test #?(:clj :refer
                                  :cljs :refer-macros)
                                 [defexpect expect]]
    #?(:cljs [cljs.test :refer-macros [deftest is]])
    #?(:clj [clojure.test :refer [deftest is]])
    #?(:cljs [cljs.tools.reader :refer [read-string]])
    [clojure.string     :as str]
    [zprint.core        :refer [zprint-str set-options! zprint-str-internal
                                czprint-str zprint-file-str zprint czprint
                                #?@(:clj [czprint-fn czprint-fn-str
                                          zprint-fn-str zprint-fn])]]
    [zprint.zprint      :refer [line-count max-width line-lengths make-record
                                contains-nil? map-ignore]]
    [zprint.comment     :refer [blanks]]
    [zprint.zutil]
    [zprint.config      :refer [merge-deep]]
    [zprint.optionfn    :refer [rodfn regexfn rulesfn]]
    #?@(:clj ([clojure.repl :refer [source-fn]]))
    [zprint.core-test   :refer [trim-gensym-regex x8]]
    [rewrite-clj.parser :as    p
                        :refer [parse-string parse-string-all]]
    [rewrite-clj.node   :as n]
    [rewrite-clj.zip    :as    z
                        :refer [edn*]]))

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
  ;; Trim exceptions
  ;;

  (defn clean-exception
    "Clean out specific information from end of exceptions."
    [s]
    (clojure.string/replace s #"because:.*" "because:"))

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
                    (read-string (zprint-str y2
                                             {:parallel? false,
                                              :parse-string? true})))))

  #?(:clj (def y3 (source-fn 'zprint.zprint/fzprint-list*)))
  #?(:clj (expect (trim-gensym-regex (read-string y3))
                  (trim-gensym-regex
                    (read-string (zprint-str y3
                                             {:parallel? false,
                                              :parse-string? true})))))


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
  ;; and again with :parallel? true and {:style :justified}
  ;;

  #?(:clj (expect (read-string y1)
                  (read-string (zprint-str y1
                                           {:style :justified,
                                            :parallel? true,
                                            :parse-string? true}))))

  #?(:clj (expect (trim-gensym-regex (read-string y2))
                  (trim-gensym-regex
                    (read-string (zprint-str y2
                                             {:style :justified,
                                              :parallel? true,
                                              :parse-string? true})))))

  #?(:clj (expect (trim-gensym-regex (read-string y3))
                  (trim-gensym-regex
                    (read-string (zprint-str y3
                                             {:style :justified,
                                              :parallel? true,
                                              :parse-string? true})))))

  (expect (trim-gensym-regex (read-string fzprint-list*str))
          (trim-gensym-regex (read-string (zprint-str fzprint-list*str
                                                      {:style :justified,
                                                       :parallel? false,
                                                       :parse-string? true}))))


  ;;
  ;; Check out line count
  ;;

  (expect 1 (line-count "abc"))

  (expect 2 (line-count "abc\ndef"))

  (expect 3 (line-count "abc\ndef\nghi"))


  (def r
    {:aaaa :bbbb, :cccc '({:eeee :ffff, :aaaa :bbbb, :cccccccc :dddddddd})})

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
  (expect
    23
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
     [":eeee" :purple :element] [" " :none :whitespace]
     [":ffff" :purple :element] ["}" :red :right]])

  (expect [20 20 25 20] (line-lengths {} 7 ll))

  (def u
    {:aaaa :bbbb,
     :cccc {:eeee :ffff, :aaaa :bbbb, :ccccccccccc :ddddddddddddd}})

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
    '(+
      :aaaaaaaaaa
      (if
       :bbbbbbbbbb
       :cccccccccc
       (list
        :ddddddddd
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

  (expect
    (str "#<Atom " @atm ">")
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
            (byte-array [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21
                         22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39
                         40 41 42 43 44 45 46 47 48 49 50]))
     :cljs (def ba1
             (int-array [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21
                         22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39
                         40 41 42 43 44 45 46 47 48 49 50])))

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
  (expect 1
          (line-count (zprint-str rs
                                  #?(:bb 64
                                     :clj 53
                                     :cljs 53))))
  (expect 1
          (line-count (zprint-str rs
                                  #?(:bb 63
                                     :clj 52
                                     :cljs 52))))
  (expect 1
          (line-count (zprint-str rs
                                  #?(:bb 62
                                     :clj 51
                                     :cljs 51))))
  (expect 2
          (line-count (zprint-str rs
                                  #?(:bb 61
                                     :clj 50
                                     :cljs 50))))
  (expect 2
          (line-count (zprint-str rs
                                  #?(:bb 60
                                     :clj 49
                                     :cljs 49))))

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
    (zprint-str
      x8
      {:parse-string? true, :pair-fn {:hang? nil}, :comment {:inline? false :smart-wrap {:space-factor 100 :last-max 80}}}))

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
    (zprint-str zprint.zprint-test/zctest4str
                40
                {:parse-string? true, :pair-fn {:hang? nil} :pair {:multi-lhs-hang? false}}))

  ; When :respect-nl? was added for lists, this changed because if you
  ; have a newline following the "list", then you don't want to hang the
  ; rest of the things because it looks bad.  And so comments got the same
  ; treatment.
  (expect
    "(defn zctest3\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and (list ;\n           (identity \"stuff\")\n           \"bother\"))\n      x\n    :else (or :a :b :c)))"
    ;  "(defn zctest3\n  \"Test comment forcing things\"\n  [x]\n  (cond\n
    ;  (and
    ;  (list ;\n               (identity \"stuff\")\n
    ;  \"bother\"))\n
    ;       x\n    :else (or :a :b :c)))"
    (zprint-str zprint.zprint-test/zctest3str
                40
                {:parse-string? true, :pair-fn {:hang? nil} :pair {:multi-lhs-hang? false}}))

  (expect

"(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let\n    [abade :b\n     ceered\n       (let [b :d]\n         (if (:a x)\n           ; this is a very long comment that should force things\n           ; way to the left\n           (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to\n          ; be\n          :c)))"

    (zprint-str zprint.zprint-test/zctest5str
                70
                {:parse-string? true, :comment {:count? true, :wrap? true}}))

  (expect
    "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force\n                   ; things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
    (zprint-str zprint.zprint-test/zctest5str
                70
                {:parse-string? true, :comment {:count? nil, :wrap? true :smart-wrap? false}}))

  (expect
    "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
    (zprint-str zprint.zprint-test/zctest5str
                70
                {:parse-string? true, :comment {:wrap? nil, :count? nil}}))

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
          (zprint.zprint/expand-tabs 8
                                     "this is a tab\ttest to see if it works"))

  (expect "this is a taba  test to see if it works"
          (zprint.zprint/expand-tabs 8
                                     "this is a taba\ttest to see if it works"))

  (expect
    "this is a tababc        test to see if it works"
    (zprint.zprint/expand-tabs 8 "this is a tababc\ttest to see if it works"))

  (expect
    "this is a tabab test to see if it works"
    (zprint.zprint/expand-tabs 8 "this is a tabab\ttest to see if it works"))

  ;;
  ;; # File Handling
  ;;

  (expect ["\n\n" ";;stuff\n" "(list :a :b)" "\n\n"]
          (zprint.zutil/zmap-all (partial zprint-str-internal
                                          (zprint.config/get-options)
                                          {:zipper? true, :color? false})
                                 (edn* (p/parse-string-all
                                         "\n\n;;stuff\n(list :a :b)\n\n"))))

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

  (expect
    "{:a :test, :second :more-value, :should-be-first :value, :this :is}"
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

  (expect
    "#?(:cljs (list :a :b)\n   :clj (list :c :d))"
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

  #?(:bb nil
     :clj (def ag (agent [:a :b])))

  #?(:bb nil
     :clj (expect "#<Agent [:a :b]>"
                  (clojure.string/replace (zprint-str ag) #"\@[0-9a-f]*" "")))

;
; This test causes cognitect test-runner to hang instead of exiting cleanly.
; I have been unable to figure out what to do to prevent that, and since this
; is clearly marginal functionality, I've left it out for now (and probably
; forever).
;
;  #?(:bb nil
;     :clj (def agf (agent [:c :d])))
;
;  #?(:bb nil
;   :clj (expect
;          "#<Agent FAILED [:c :d]>"
;          (let [agf (agent [:c :d])]
;            (send agf + 5)
;           ; Wait a bit for the send to get to the agent and for the
;            ; agent to fail
;            (Thread/sleep 100)
;            (let [result
;                    (clojure.string/replace (zprint-str agf) #"\@[0-9a-f]*" "")]
;              ; If we don't restart the agent, the tests will hang
;              ; forever with , even though there is a shutdown-agents call
;	      (agent-error agf) 
;              (restart-agent agf [:c :d] :clear-actions true)
;              result))))

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
  (expect true (contains-nil? [:a :b nil '() :c :d]))

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
  ;; fzprint-hang-remaining where it was messing that up unless hang-expand was
  ;; 4.0
  ;; instead of the 2.0.  This *should* hang up next to the do, not flow under
  ;; the do.
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

  #?(:bb nil
     :clj  ; Keep the deftype on the next line
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

  #?(:bb nil
     :clj
       (expect
         "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
         (zprint-fn-str zprint.zprint-test/->Typetest
                        {:extend {:flow? false}})))

  (expect
    "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
    (zprint-str zprint.zprint-test/Typeteststr
                {:parse-string? true, :extend {:flow? false}}))

  #?(:bb nil
     :clj
       (expect
         "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
         (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? true}})))

  (expect
    "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
    (zprint-str zprint.zprint-test/Typeteststr
                {:parse-string? true, :extend {:flow? true}}))

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

  #?(:bb nil
     :clj
       (expect
         "(deftype Typetest [cnt _meta] clojure.lang.IHashEq (hasheq [this] (list this)) clojure.lang.Counted (count [_] cnt) clojure.lang.IMeta (meta [_] _meta))"
         (zprint-fn-str zprint.zprint-test/->Typetest
                        200
                        {:extend {:flow? false, :force-nl? false}})))
  (expect
    "(deftype Typetest [cnt _meta] clojure.lang.IHashEq (hasheq [this] (list this)) clojure.lang.Counted (count [_] cnt) clojure.lang.IMeta (meta [_] _meta))"
    (zprint-str zprint.zprint-test/Typeteststr
                200
                {:parse-string? true,
                 :extend {:flow? false, :force-nl? false}}))

  #?(:bb nil
     :clj
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

  #?(:bb nil
     :clj
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

  #?(:bb nil
     :clj
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

  #?(:bb nil
     :clj
       (expect
         "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
         (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? true}})))

  (expect
    "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
    (zprint-str zprint.zprint-test/Typeteststr
                {:parse-string? true, :extend {:flow? true}}))

  #?(:bb nil
     :clj
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
                {:parse-string? true,
                 :pair {:flow? true, :nl-separator? false}}))

  (expect
    "(println :this\n           :should\n\n         :constant\n           :pair)"
    (zprint-str "(println :this :should :constant :pair)"
                37
                {:parse-string? true,
                 :pair {:flow? true, :nl-separator? true}}))

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

  #?(:bb nil
     :clj  ; Keep the deftype on the next line
       (deftype Typetest1 [cnt _meta]
         clojure.lang.IHashEq
           (hasheq [this]
             (list this)
             (list this this)
             (list this this this this))
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

  #?(:bb nil
     :clj
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

  #?(:bb nil
     :clj
       (expect
         "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
         (zprint-fn-str zprint.zprint-test/->Typetest1
                        60
                        {:extend {:flow? false, :hang? false}})))
  (expect
    "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
    (zprint-str zprint.zprint-test/Typetest1str
                60
                {:parse-string? true, :extend {:flow? false, :hang? false}}))

  #?(:bb nil
     :clj
       (expect
         "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
         (zprint-fn-str zprint.zprint-test/->Typetest1
                        60
                        {:extend {:flow? true, :hang? true}})))
  (expect
    "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
    (zprint-str zprint.zprint-test/Typetest1str
                60
                {:parse-string? true, :extend {:flow? true, :hang? true}}))

  #?(:bb nil
     :clj
       (expect
         "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
         (zprint-fn-str zprint.zprint-test/->Typetest1
                        60
                        {:extend {:flow? true, :hang? false}})))
  (expect
    "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
    (zprint-str zprint.zprint-test/Typetest1str
                60
                {:parse-string? true, :extend {:flow? true, :hang? false}}))

  ;;
  ;; # Test a variant form of cond with :nl-separator?
  ;;

  #?(:clj  ; Keep the defn off the same line.
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

  ; The :bb behavior of the #?(:clj ; comment\n (defn ...)) is diffeent
  ; from the :clj behavior.  Enough so that this test doesn't really
  ; work for ::
  #?(:bb nil
     :clj
       (expect
         "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
         (zprint-fn-str zprint.zprint-test/zctest8x 20)))

  (expect
    "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
    (zprint-str zprint.zprint-test/zctest8xstr 20 {:parse-string? true}))

  #?(:bb nil
     :clj
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
    '(deftype
      Foo
      [a b c]
      P
      (foo [this] a)
      Q
      (bar-me [this] b)
      (bar-me [this y] (+ c y))
      R
      S
      (baz [this] a)
      static
      T
      (baz-it [this] b)
      static
      V
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
  ; What happens if the modifier and the first element don't fit on the same
  ; line?
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
     ["key-color-tst" :black :element] ["\n  " :none :indent]
     ["[" :purple :left] ["]" :purple :right] ["\n  " :none :indent]
     ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
     [";stuff" :green :comment] ["\n     " :none :newline]
     [":bother" :magenta :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["\"deep\"" :red :element] [" " :none :whitespace]
     ["{" :red :left] ["\"and\"" :red :element] [" " :none :whitespace]
     ["\"even\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     [":deeper" :magenta :element] [" " :none :whitespace] ["{" :red :left]
     ["\"that\"" :red :element] [" " :none :whitespace]
     [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
     [":just" :magenta :element] [" " :none :whitespace]
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
     ["key-color-tst" :black :element] ["\n  " :none :indent]
     ["[" :purple :left] ["]" :purple :right] ["\n  " :none :indent]
     ["{" :red :left] [":abc" :blue :element] ["\n     " :none :indent]
     [";stuff" :green :comment] ["\n     " :none :newline]
     [":bother" :magenta :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["\"deep\"" :blue :element] [" " :none :whitespace]
     ["{" :red :left] ["\"and\"" :yellow :element] [" " :none :whitespace]
     ["\"even\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     [":deeper" :yellow :element] [" " :none :whitespace] ["{" :red :left]
     ["\"that\"" :green :element] [" " :none :whitespace]
     [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
     [":just" :green :element] [" " :none :whitespace] ["\"the\"" :red :element]
     ["," :none :whitespace] [" " :none :whitespace] ["\"way\"" :green :element]
     [" " :none :whitespace] [":it-is" :magenta :element] ["}" :red :right]
     ["}" :red :right] ["," :none :whitespace] ["\n   " :none :indent]
     ["\"def\"" :blue :element] [" " :none :whitespace]
     ["\"ghi\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
     ["5" :blue :element] [" " :none :whitespace] ["\"five\"" :red :element]
     ["," :none :whitespace] ["\n   " :none :indent] ["[" :purple :left]
     ["\"hi\"" :red :element] ["]" :purple :right] [" " :none :whitespace]
     ["\"there\"" :red :element] ["}" :red :right] [")" :green :right]]
    (czprint-str zprint.zprint-test/key-color-tststr
                 {:parse-string? true,
                  :map {:key-depth-color [:blue :yellow :green]},
                  :return-cvec? true}))

  ; :key-color {}

  (expect
    [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
     ["key-color-tst" :black :element] ["\n  " :none :indent]
     ["[" :purple :left] ["]" :purple :right] ["\n  " :none :indent]
     ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
     [";stuff" :green :comment] ["\n     " :none :newline]
     [":bother" :magenta :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["\"deep\"" :red :element] [" " :none :whitespace]
     ["{" :red :left] ["\"and\"" :red :element] [" " :none :whitespace]
     ["\"even\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     [":deeper" :magenta :element] [" " :none :whitespace] ["{" :red :left]
     ["\"that\"" :red :element] [" " :none :whitespace]
     [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
     [":just" :magenta :element] [" " :none :whitespace]
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
      {:parse-string? true, :map {:key-color {}}, :return-cvec? true}))

  ; :key-color {:abc :blue "deep" :cyan 5 :green}

  (expect
    [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
     ["key-color-tst" :black :element] ["\n  " :none :indent]
     ["[" :purple :left] ["]" :purple :right] ["\n  " :none :indent]
     ["{" :red :left] [":abc" :blue :element] ["\n     " :none :indent]
     [";stuff" :green :comment] ["\n     " :none :newline]
     [":bother" :magenta :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["\"deep\"" :cyan :element] [" " :none :whitespace]
     ["{" :red :left] ["\"and\"" :red :element] [" " :none :whitespace]
     ["\"even\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     [":deeper" :magenta :element] [" " :none :whitespace] ["{" :red :left]
     ["\"that\"" :red :element] [" " :none :whitespace]
     [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
     [":just" :magenta :element] [" " :none :whitespace]
     ["\"the\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     ["\"way\"" :red :element] [" " :none :whitespace]
     [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
     ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
     [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["5" :green :element] [" " :none :whitespace]
     ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
     ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
     [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
     [")" :green :right]]
    (czprint-str zprint.zprint-test/key-color-tststr
                 {:parse-string? true,
                  :map {:key-color {:abc :blue, "deep" :cyan, 5 :green}},
                  :return-cvec? true}))

  ; Test out nil's in the :key-depth-color vector, and if :key-color values
  ; will override what is in :key-depth-color

  (expect
    [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
     ["key-color-tst" :black :element] ["\n  " :none :indent]
     ["[" :purple :left] ["]" :purple :right] ["\n  " :none :indent]
     ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
     [";stuff" :green :comment] ["\n     " :none :newline]
     [":bother" :magenta :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["\"deep\"" :red :element] [" " :none :whitespace]
     ["{" :red :left] ["\"and\"" :red :element] [" " :none :whitespace]
     ["\"even\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     [":deeper" :magenta :element] [" " :none :whitespace] ["{" :red :left]
     ["\"that\"" :red :element] [" " :none :whitespace]
     [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
     [":just" :magenta :element] [" " :none :whitespace]
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
  ;; When you find this key, use the color map associated with it when
  ;; formatting
  ;; the value.
  ;;

  (expect
    [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
     ["key-color-tst" :black :element] ["\n  " :none :indent]
     ["[" :purple :left] ["]" :purple :right] ["\n  " :none :indent]
     ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
     [";stuff" :green :comment] ["\n     " :none :newline]
     [":bother" :magenta :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["\"deep\"" :red :element] [" " :none :whitespace]
     ["{" :red :left] ["\"and\"" :red :element] [" " :none :whitespace]
     ["\"even\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     [":deeper" :magenta :element] [" " :none :whitespace] ["{" :red :left]
     ["\"that\"" :red :element] [" " :none :whitespace]
     [":is" :magenta :element] ["," :none :whitespace] [" " :none :whitespace]
     [":just" :magenta :element] [" " :none :whitespace]
     ["\"the\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     ["\"way\"" :red :element] [" " :none :whitespace]
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
     ["key-color-tst" :black :element] ["\n  " :none :indent]
     ["[" :purple :left] ["]" :purple :right] ["\n  " :none :indent]
     ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
     [";stuff" :green :comment] ["\n     " :none :newline]
     [":bother" :magenta :element] ["," :none :whitespace]
     ["\n   " :none :indent] ["\"deep\"" :red :element] [" " :none :whitespace]
     ["{" :red :left] ["\"and\"" :red :element] [" " :none :whitespace]
     ["\"even\"" :red :element] ["," :none :whitespace] [" " :none :whitespace]
     [":deeper" :magenta :element] [" " :none :whitespace] ["{" :red :left]
     ["\"that\"" :red :element] [" " :none :whitespace] [":is" :blue :element]
     ["," :none :whitespace] [" " :none :whitespace] [":just" :blue :element]
     [" " :none :whitespace] ["\"the\"" :red :element] ["," :none :whitespace]
     [" " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
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
"(defn zctest9\n  \"Test inline comments\"\n  []\n  (let [a (list 'with 'arguments)\n        foo nil ; end of line comment\n        bar true\n        baz \"stuff\"\n        other 1\n        bother 2 ; a really long inline comment that should wrap\n                 ; about here\n        stuff 3\n        ; a non-inline comment\n        now ;a middle inline comment\n          4\n        ; Not an inline comment\n        output 5\n        b 3\n        c 5\n        this \"is\"]\n    (cond (or foo bar baz) (format output now) ;test this\n          :let [stuff (and bother foo bar) ;test that\n                bother (or other output foo)] ;and maybe the other\n          (and a b c (bother this)) (format other stuff))\n    (list a :b :c \"d\")))"



    (zprint-str zprint.zprint-test/zctest9str
                70
                {:parse-string? true, :comment {:inline? true}}))

  (expect

"(defn zctest9\n  \"Test inline comments\"\n  []\n  (let [a (list 'with 'arguments)\n        foo nil\n        ; end of line comment\n        bar true\n        baz \"stuff\"\n        other 1\n        bother 2\n        ; a really long inline comment that should wrap about\n        ; here\n        stuff 3\n        ; a non-inline comment\n        now\n          ;a middle inline comment\n          4\n        ; Not an inline comment\n        output 5\n        b 3\n        c 5\n        this \"is\"]\n    (cond (or foo bar baz) (format output now)\n          ;test this\n          :let [stuff (and bother foo bar)\n                ;test that\n                bother (or other output foo)]\n          ;and maybe the other\n          (and a b c (bother this)) (format other stuff))\n    (list a :b :c \"d\")))"

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
    '(rum/defcs
      component
      "This is a component with a doc-string!  How unusual..."
      <
      rum/static
      rum/reactive
      (rum/local 0 :count)
      (rum/local "" :text)
      [state label]
      (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cz2
    '(rum/defcs
      component
      <
      rum/static
      rum/reactive
      (rum/local 0 :count)
      (rum/local "" :text)
      [state label]
      (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cz3
    '(rum/defcs
      component
      "This is a component with a doc-string!  How unusual..."
      [state label]
      (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cz4
    '(rum/defcs
      component
      [state label]
      (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cz5
    '(rum/defcs
      component
      "This is a component with a doc-string!  How unusual..."
      <
      rum/static
      rum/reactive
      (rum/local 0 :count)
      (rum/local "" :text)
      (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cz6
    '(rum/defcs
      component
      <
      rum/static
      rum/reactive
      (rum/local 0 :count)
      (rum/local "" :text)
      (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cz7
    '(rum/defcs
      component
      (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cz8
    '(rum/defcs
      component
      "This is a component with a doc-string!  How unusual..."
      <
      rum/static
      rum/reactive
      (rum/local 0 :count)
      (rum/local "" :text)
      ([state label]
       (let [count-atom (:count state) text-atom (:text state)] [:div]))
      ([state] (component state nil))))

  (def cz9
    '(rum/defcs
      component
      "This is a component with a doc-string!  How unusual..."
      {:a :b,
       :c [this is a very long vector how do you suppose it will work],
       "this" [is a test]}
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
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   :c [this is a very long vector how do you suppose it will work],\n   \"this\" [is a test]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
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
  ;; Demonstrate that styles in option-fn returns actually work
  ;;

  (expect
    "[stuff\n bother\n and\n (this\n  is\n  a\n  test\n  (and it has to be really really ong)\n  so\n  that\n  we\n  can\n  see\n  how\n  it\n  indents)\n has\n to\n be\n really\n long\n so\n that\n it\n doesn\n print\n all\n on\n one\n line]"
    (zprint-str
      "[stuff bother and (this is a test (and it has to be really really ong) so that we can see how it indents) has to be really long so that it doesn print all on one line]"
      {:parse-string? true,
       :vector {:option-fn (fn [options len sexpr]
                             {:vector {:wrap? false},
                              :list {:hang? false},
                              :style :community})}}))



  ;;
  ;; # Error's in option-fn's
  ;;

  #?(:clj
       (expect
         "java.lang.Exception:  When :list called an option-fn named 'test' it failed because:"
         (try
           (zprint "(a b c)"
                   {:parse-string? true,
                    :list {:option-fn
                             (fn ([] "test") ([options len sexpr] (+ :a 0)))}})
           (catch Exception e (clean-exception (str e)))))
     :cljs
       (expect
         "Error:  When :list called an option-fn named 'test' it failed because: Error: 0 is not ISeqable"
         (try
           (zprint "(a b c)"
                   {:parse-string? true,
                    :list {:option-fn
                             (fn ([] "test") ([options len sexpr] (seq 0)))}})
           (catch :default e (str e)))))

  #?(:clj
       (expect
         "java.lang.Exception:  When :list called an option-fn it failed because:"
         (try (zprint "(a b c)"
                      {:parse-string? true,
                       :list {:option-fn (fn ([options len sexpr] (+ :a 0)))}})
              (catch Exception e (clean-exception (str e)))))
     :cljs
       (expect
         "Error:  When :list called an option-fn it failed because: Error: 0 is not ISeqable"
         (try (zprint "(a b c)"
                      {:parse-string? true,
                       :list {:option-fn (fn ([options len sexpr] (seq 0)))}})
              (catch :default e (str e)))))

  #?(:clj
       (expect
         "java.lang.Exception: When :vector called an option-fn-first with ':a' failed because:"
         (try (zprint "[:a :b :c]"
                      {:parse-string? true,
                       :vector {:option-fn-first
                                  (fn ([options sexpr] (+ :a 0)))}})
              (catch Exception e (clean-exception (str e)))))
     :cljs
       (expect
         "Error: When :vector called an option-fn-first with ':a' failed because: Error: 0 is not ISeqable"
         (try (zprint "[:a :b :c]"
                      {:parse-string? true,
                       :vector {:option-fn-first (fn
                                                   ([options sexpr] (seq 0)))}})
              (catch :default e (str e)))))

  #?(:clj
       (expect
         "java.lang.Exception: When :vector called an option-fn-first named 'test' with ':a' failed because:"
         (try (zprint "[:a :b :c]"
                      {:parse-string? true,
                       :vector {:option-fn-first
                                  (fn ([] "test") ([options sexpr] (+ :a 0)))}})
              (catch Exception e (clean-exception (str e)))))
     :cljs
       (expect
         "Error: When :vector called an option-fn-first named 'test' with ':a' failed because: Error: 0 is not ISeqable"
         (try (zprint "[:a :b :c]"
                      {:parse-string? true,
                       :vector {:option-fn-first
                                  (fn ([] "test") ([options sexpr] (seq 0)))}})
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
  ;; Note that the next test depends on this one (where the next one ensures
  ;; that
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
    (zprint-str '(abc
                  sdfjsksdfjdskl
                  jkfjdsljdlfjldskfjklsjfjd
                  :a 'b
                  :c 'd
                  :e 'f
                  :g 'h
                  :i 'j)
                {:max-length 13}))

  (expect
    "(abc sdfjsksdfjdskl\n     jkfjdsljdlfjldskfjklsjfjd\n     :a (quote b)\n     :c (quote d)\n     :e (quote f)\n     :g (quote h)\n     :i ...)"
    (zprint-str '(abc
                  sdfjsksdfjdskl
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

  (expect #?(:bb "#sci.impl.records.SciRecord {:left :reallylongleft, ...}"
             :clj "#zprint.zprint.r {:left :reallylongleft, ...}"
             :cljs "#zprint.zprint.r {:left :reallylongleft, ...}")
          (zprint-str rml {:max-length 1}))

  (expect
    #?(:bb
         "#sci.impl.records.SciRecord {:left :reallylongleft,\n                             :right {:r :s, [[:t] :u :v] :x}}"
       :clj
         "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, [[:t] :u :v] :x}}"
       :cljs
         "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, [[:t] :u :v] :x}}")
    (zprint-str rml))

  (expect
    #?(:bb
         "#sci.impl.records.SciRecord {:left :reallylongleft,\n                             :right {:r :s, [[:t] :u ...] :x}}"
       :clj
         "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, [[:t] :u ...] :x}}"
       :cljs
         "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, [[:t] :u ...] :x}}")
    (zprint-str rml {:max-length 2}))

  (expect
    #?(:bb
         "#sci.impl.records.SciRecord {:left :reallylongleft,\n                             :right {:r :s, ...}}"
       :clj "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, ...}}"
       :cljs "#zprint.zprint.r {:left :reallylongleft, :right {:r :s, ...}}")
    (zprint-str rml {:max-length [2 1 0]}))

  (expect
    #?(:bb
         "#sci.impl.records.SciRecord {:left :reallylongleft,\n                             :right ##}"
       :clj "#zprint.zprint.r {:left :reallylongleft, :right ##}"
       :cljs "#zprint.zprint.r {:left :reallylongleft, :right ##}")
    (zprint-str rml {:max-length [2 0]}))

  (expect
    #?(:bb
         "#sci.impl.records.SciRecord {:left :reallylongleft,\n                             :right ##}"
       :clj "#zprint.zprint.r {:left :reallylongleft, :right ##}"
       :cljs "#zprint.zprint.r {:left :reallylongleft, :right ##}")
    (zprint-str rml {:max-length [3 0]}))

  ;; Can we read back records that we have written out?
  ;;
  ;; Issue #105
  ;;
  ;; This doesn't seem to work in cljs in any case, oddly enough.
  ;; Nor does it work for :bb, you get:
  ;; java.lang.Exception: No reader function for tag sci.impl.records.SciRecord

  #?(:bb nil
     :clj (expect rml (read-string (zprint-str rml))))

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

  (expect "{:a {:b {:c ##}}}"
          (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 3}))

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
    ; If we remove that configuration, will it stop inhibiting the wrapping of
    ; vector
    ; elements?
    (expect
      "(defproject name version\n  :test :this\n  :stuff [:aaaaa :bbbbbbb :ccccccccc :ddddddd\n          :eeeeeee])"
      (redef-state [zprint.config]
                   (zprint-str dp
                               50
                               {:parse-string? true,
                                :fn-map {"defproject" :arg2-pair}}))))

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

  (expect "{:a :b, :c {:u/e :f, :u/g :h}}"
          (zprint-str (zprint-str "{:a :b, :c {:u/e :f, :u/g :h}}"
                                  {:parse-string? true,
                                   :map {:lift-ns? true, :unlift-ns? false}})
                      {:parse-string? true,
                       :map {:lift-ns? false, :unlift-ns? true}}))

  ;; # Tests for comments mixed in with the early part of lists
  ;;

  (expect 
"(;stuff\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n  ;baz\n  5)"
          (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
                      {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  (expect 
"(;stuff\n let ;bother\n  [a :x\n   b :y]\n  (nil? nil)\n  5)"
          (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y](nil? nil) 5)"
                      {:parse-string? true}))


  (expect 
 "(;stuff\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n  ;baz\n  5)" 
          (zprint-str
            "( ;stuff\n\nlet;bother\n[a :x b :y] ;foo\n;bar\n\n;baz\n5)"
            {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  (expect 
"(;stuff\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n  ;baz\n  5)"
          (zprint-str
            "( ;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
            {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  (expect 
"(;stuff\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n  ;baz\n  5)"
          (zprint-str
            "( ;stuff\n\nlet ;bother\n[a :x b :y]  ;foo\n;bar\n\n;baz\n5)"
            {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  (expect
    "(;stuff\n let ;bother\n  [a :x\n   b :y]\n  (list a b)\n  (map a b)\n  5)"
    (zprint-str
      "( ;stuff\n\nlet ;bother\n[a :x b :y]\n(list a b)\n(map a b)\n5)"
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
"(;stuff\n\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n\n  ;baz\n  5)"
    (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
                {:parse-string? true, :list {:respect-nl? true}}))

  (expect 
"(;stuff\n\n let ;bother\n  [a :x\n   b :y]\n  (nil? nil)\n  5)"
          (zprint-str "(;stuff\n\nlet;bother\n[a :x b :y](nil? nil) 5)"
                      {:parse-string? true, :list {:respect-nl? true}}))

  (expect
"(;stuff\n\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n\n  ;baz\n  5)"
    (zprint-str "( ;stuff\n\nlet;bother\n[a :x b :y] ;foo\n;bar\n\n;baz\n5)"
                {:parse-string? true, :list {:respect-nl? true}}))

  (expect
"(;stuff\n\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n\n  ;baz\n  5)"
    (zprint-str "( ;stuff\n\nlet;bother\n[a :x b :y];foo\n;bar\n\n;baz\n5)"
                {:parse-string? true, :list {:respect-nl? true}}))

  (expect
"(;stuff\n\n let ;bother\n  [a :x\n   b :y] ;foo\n  ;bar\n\n  ;baz\n  5)"
    (zprint-str "( ;stuff\n\nlet ;bother\n[a :x b :y]  ;foo\n;bar\n\n;baz\n5)"
                {:parse-string? true, :list {:respect-nl? true}}))

  (expect
    "(;stuff\n\n let ;bother\n  [a :x\n   b :y]\n  (list a b)\n  (map a b)\n  5)"
    (zprint-str
      "( ;stuff\n\nlet ;bother\n[a :x b :y]\n(list a b)\n(map a b)\n5)"
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
          (zprint-str
            "{:a :b :c :d :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{:a\n   :b,\n :c :d,\n :e :f}"
          (zprint-str
            "{:a \n :b :c :d :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{:a :b,\n :c :d,\n :e :f}"
          (zprint-str
            "{:a :b \n :c :d :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{:a :b,\n\n :c :d,\n :e :f}"
          (zprint-str
            "{:a :b \n\n :c :d :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{:a\n\n   :b,\n :c :d,\n :e :f}"
          (zprint-str
            "{:a \n\n :b \n :c :d \n :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{:a\n\n   :b,\n :c :d,\n :e :f}"
          (zprint-str
            "{:a \n\n :b \n :c :d \n :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 80}))

  (expect "{;stuff\n :a :b, ;bother\n :c :d,\n :e :f}"
          (zprint-str
            "{;stuff\n :a :b ;bother\n :c :d \n :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{;stuff\n :a :b, ;bother\n :c :d,\n :e :f}"
          (zprint-str
            "{;stuff\n :a :b ;bother\n :c :d \n :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 80}))

  (expect "{;stuff\n\n :a :b, ;bother\n :c\n\n   :d,\n :e :f}"
          (zprint-str
            "{;stuff\n\n :a :b ;bother\n :c \n\n :d \n :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{;stuff\n\n :a :b, ;bother\n :c\n\n   :d,\n :e :f}"
          (zprint-str
            "{;stuff\n\n :a :b ;bother\n :c \n\n :d \n :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 80}))

  (expect "{;stuff\n\n :a :b,\n :c ;bother\n\n   :d, ;foo\n\n :e :f}"
          (zprint-str
            "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
            {:parse-string? true, :map {:respect-nl? true}, :width 15}))

  (expect "{;stuff\n\n :a :b,\n :c ;bother\n\n   :d, ;foo\n\n :e :f}"
          (zprint-str
            "{;stuff\n\n :a :b :c ;bother\n\n :d ;foo\n\n :e :f}"
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
    "(comment\n  (defn x [y] (println y))\n\n  (this is a thing that is interesting)\n\n  (def z :this-is-a-test)\n\n  (def a :more stuff)\n\n\n\n  (def b :3-blanks-above))"
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

  (expect "{:a :b,\n :c {:g :h,\n     :i\n\n       :j},\n\n :e\n\n\n   :f}"
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
     (stuffx [this x y]
       "stuff docstring")
     (botherx [this]
              [this x]
              [this x y]
       "bother docstring")
     (foox [this baz]
       "foo docstring"))

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
    "(extend-type ZprintType\n  ZprintProtocol\n    (more [a b]\n      (and a b))\n    (and-more\n      ([a]\n       (nil? a))\n      ([a b]\n       (or a b))))"
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

  (expect
    "(as-> (list :a) x\n  (repeat 5 x)\n  (do (println x) x)\n  (nth x 2))"
    (zprint-str "(as-> (list :a) x (repeat 5 x) (do (println x) x) (nth x 2))"
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

  (expect
    "(defprotocol P\n  (foo [this])\n  (bar-me [this]\n          [this y]))"
    (zprint-str "(defprotocol P (foo [this]) (bar-me [this] [this y]))"
                {:parse-string? true}))


  ;;
  ;; :arg1-force-nl with comments
  ;;

  (expect
    "(;stuff\n defprotocol\n  ;bother\n  P\n  (foo [this])\n  (bar-me [this]\n          [this y]))"
    (zprint-str
      "(;stuff\ndefprotocol\n ;bother\nP (foo [this]) \n\n(bar-me [this] [this y]))"
      {:parse-string? true}))

  ;;
  ;; :arg1-force-nl with comments and respect-nl
  ;;

  (expect
    "(;stuff\n defprotocol\n  ;bother\n  P\n  (foo [this])\n\n  (bar-me [this]\n          [this y]))"
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

  (expect 
"(;precomment\n one ;postcomment\n)"
          (zprint-str "(;precomment\n one;postcomment\n)"
                      {:parse-string? true}))


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



  (expect (trim-gensym-regex (read-string zprint.zprint-test/fzprint-list*str))
          (trim-gensym-regex
            (read-string (zprint-str zprint.zprint-test/fzprint-list*str
                                     {:parse-string? true,
                                      :style :respect-nl}))))

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
      {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  (expect
    "(;comment 1\n ;comment 2\n ;comment 3\n\n this is\n      a\n      test)"
    (zprint-str
      "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n      a\n   test)"
      {:parse-string? true, :style :indent-only}))

  (expect
    "(;comment 1\n ;comment 2\n ;comment 3\n a this\n   is\n   a\n   test)"
    (zprint-str
      "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n a\n this is\n      a\n   test)"
      {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  (expect
    "(;comment 1\n ;comment 2\n ;comment 3\n\n a\n  this is\n  a\n  test)"
    (zprint-str
      "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n a\n this is\n      a\n   test)"
      {:parse-string? true, :style :indent-only}))

  (expect
    "(;comment 1\n ;comment 2\n ;comment 3\n this is\n      a\n      test)"
    (zprint-str
      "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n\n      a\n   test)"
      {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  (expect
    "(;comment 1\n ;comment 2\n ;comment 3\n\n this is\n\n      a\n      test)"
    (zprint-str
      "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n\n      a\n   test)"
      {:parse-string? true, :style :indent-only}))

  (expect
    "(;comment 1\n ;comment 2\n ;comment 3\n this is\n      ; comment 4\n      a\n      test)"
    (zprint-str
      "\n(;comment 1\n ;comment 2\n ;comment 3 \n\n this is\n ; comment 4\n      a\n   test)"
      {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

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

  (expect "#{:a\n\n  :b\n  :c #{:a :b\n\n       :c\n       :d :e :f}\n  :e :f}"
          (zprint-str
            "#{:a \n\n :b \n :c #{:a :b \n\n :c \n :d :e :f} \n :e :f}"
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
      {:parse-string? true, :comment {:inline-align-style :none :smart-wrap {:last-max 80 :border 0}}}))

  ;;
  ;; :inline-align-style :aligned
  ;;

  (expect
    "(def x\n  zprint.zfns/zstart\n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test)\n          (this is the result and it is too long)              ; inline comment\n        (this is a second test)\n          (and this is another test that is way too very long) ; inline comment\n                                                               ; 2\n        :else (stuff bother)) ; inline comment 3\n  smiddle          ; Not an isolated inline comment\n  zprint.zfns/zend ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
    (zprint-str
      "(def x\n  zprint.zfns/zstart \n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test) (this is the result and it is too long) ; inline comment\n  (this is a second test) (and this is another test that is way too very long)        ; inline comment 2\n  :else (stuff bother)) ; inline comment 3\n  smiddle           ; Not an isolated inline comment\n  zprint.zfns/zend  ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
      {:parse-string? true, :comment {:inline-align-style :aligned :smart-wrap {:last-max 80 :border 0}}}))

  ;;
  ;; :inline-align-style :consecutive
  ;;

  (expect
    "(def x\n  zprint.zfns/zstart\n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test)\n          (this is the result and it is too long) ; inline comment\n        (this is a second test)\n          (and this is another test that is way too very long) ; inline comment\n                                                               ; 2\n        :else (stuff bother))                                  ; inline comment\n                                                               ; 3\n  smiddle                                                      ; Not an isolated\n                                                               ; inline comment\n  zprint.zfns/zend                                             ; contiguous\n                                                               ; inline comments\n  sdlfksdj                                                     ; inline comment\n  fdslfk                                                       ; inline comment\n                                                               ; aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false)   ; this only works because lists, anon-fn's, etc. are\n                       ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
    (zprint-str
      "(def x\n  zprint.zfns/zstart \n  sfirst\n  ; not an line comment\n  ; another not inline comment\n  zprint.zfns/zmiddle\n  (cond (this is a test this is onlyh a test) (this is the result and it is too long) ; inline comment\n  (this is a second test) (and this is another test that is way too very long)        ; inline comment 2\n  :else (stuff bother)) ; inline comment 3\n  smiddle           ; Not an isolated inline comment\n  zprint.zfns/zend  ; contiguous inline comments\n  sdlfksdj ; inline comment\n  fdslfk   ; inline comment aligned\n  dflsfjdsjkfdsjl\n  send\n  zprint.zfns/zanonfn? ; This too is a comment\n  (constantly false) ; this only works because lists, anon-fn's, etc. are\n                     ; checked before this is used.\n  zprint.zfns/zfn-obj?\n  fn?)"
      {:parse-string? true, :comment {:inline-align-style :consecutive :smart-wrap {:last-max 80 :border 0}}}))


  ;;
  ;; # can you change :respect-nl? in a fn-map?
  ;;

  (expect
    "(comment\n  (defn x [y] (println y))\n  (this is a thing that is interesting)\n  (def z [:this-is-a-test :with-3-blanks-above?])\n  (def a :more stuff)\n  (def b :3-blanks-above))"
    #_"(comment (defn x [y] (println y))\n         (this is a thing that is interesting)\n         (def z [:this-is-a-test :with-3-blanks-above?])\n         (def a :more stuff)\n         (def b :3-blanks-above))"
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

  (expect
    "[this [is a\n       test this\n       is only]\n  (a test)]"
    (zprint-str "[this [is a test this is only] (a test)]"
                {:parse-string? true,
                 :vector {:option-fn #(if (= (first %3) 'this)
                                        {:vector {:fn-format :binding}})}}))

  (expect "[:arg1-force-nl :a\n  :b :c\n  :d :e\n  :f :g]"
          (zprint-str [:arg1-force-nl :a :b :c :d :e :f :g]
                      {:parse-string? false,
                       :vector {:option-fn #(do {:vector {:fn-format
                                                            (first %3)}})}}))
  (expect
    "[:arg2 a b\n  c\n  d\n  e\n  f\n  g]"
    (zprint-str "[:arg2 a b c d e f g]"
                {:parse-string? true,
                 :vector {:option-fn #(do {:vector {:fn-format (first %3)},
                                           :fn-force-nl #{(first %3)}})}}))

  (expect "[:force-nl :a\n           :b :c\n           :d :e\n           :f :g]"
          (zprint-str [:force-nl :a :b :c :d :e :f :g]
                      {:parse-string? false,
                       :vector {:option-fn #(do {:vector {:fn-format
                                                            (first %3)}})}}))

  (expect
    "[:pair a\n       b\n       c\n       d\n       e\n       f\n       g]"
    (zprint-str "[:pair a b c d e f g]"
                {:parse-string? true,
                 :vector {:option-fn #(do {:vector {:fn-format (first %3)},
                                           :fn-force-nl #{(first %3)}})}}))

  (expect "[:pair-fn a b\n          c d\n          e f\n          g]"
          (zprint-str "[:pair-fn a b c d e f g]"
                      {:parse-string? true,
                       :vector {:option-fn #(do {:vector {:fn-format
                                                            (first %3)}})}}))

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

  (let [s "(a :b \"c\")"
        cf {:color? false}
        ct {:color? true}]
    (expect 14 (count (zprint-str s cf)))
    (expect 23 (count (zprint-str s ct)))
    (expect (zprint-str s cf) (str/trim-newline (with-out-str (zprint s cf))))
    (expect (zprint-str s ct) (str/trim-newline (with-out-str (zprint s ct)))))

  ;; See if those differences match what we expect

  (expect (czprint-str "(a :b \"c\")" {:color? true})
          (zprint-str "(a :b \"c\")" {:color? true}))

  (expect (czprint-str "(a :b \"c\")")
          (zprint-str "(a :b \"c\")" {:color? true}))

  (expect (czprint-str "(a :b \"c\")" {:color? false})
          (zprint-str "(a :b \"c\")" {:color? false}))

  (expect (czprint-str "(a :b \"c\")" {:color? false})
          (zprint-str "(a :b \"c\")"))

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
          (count (zprint-str zprint.zprint-test/blanksstr
                             {:parse-string? true})))

  (expect 227
          (count (czprint-str zprint.zprint-test/blanksstr
                              {:parse-string? true})))

  (expect (zprint-str zprint.zprint-test/blanksstr
                      {:parse-string? true, :color? false})
          (czprint-str zprint.zprint-test/blanksstr
                       {:parse-string? true, :color? false}))

  (expect (zprint-str zprint.zprint-test/blanksstr {:parse-string? true})
          (czprint-str zprint.zprint-test/blanksstr
                       {:parse-string? true, :color? false}))

  (expect (zprint-str zprint.zprint-test/blanksstr
                      {:parse-string? true, :color? true})
          (czprint-str zprint.zprint-test/blanksstr
                       {:parse-string? true, :color? true}))

  (expect (zprint-str zprint.zprint-test/blanksstr
                      {:parse-string? true, :color? true})
          (czprint-str zprint.zprint-test/blanksstr {:parse-string? true}))

  ;;
  ;; Used to have with-out-str tests here, but they don't work on Windows
  ;; because of the different line endings.
  ;;

  #_(expect 93
            (count (with-out-str (zprint-fn zprint.zprint-test/blanksstr
                                            {:parse-string? true}))))

  #_(expect 228
            (count (with-out-str (czprint-fn zprint.zprint-test/blanksstr))))

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
  (expect
    "'a"
    (zprint-str "(quote a)"
                {:parse-string? true,
                 :fn-map {"quote" [:replace-w-string
                                   {:list {:replacement-string "'"}} {}]}}))


  (expect "#'a" (zprint-str '(var a) {:style :backtranslate}))

  (expect "(var a)"
          (zprint-str "(var a)" {:parse-string? true, :style :backtranslate}))

  (expect
    "#'a"
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
                       :fn-map {"clojure.core/deref"
                                  [:replace-w-string
                                   {:list {:replacement-string "@"}} {}]}}))


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
		       :comment {:smart-wrap? false}
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

  (expect
    "(let [a\n        ;stuff\n        b\n\n      ;bother\n      c d]\n  e)"
    (zprint-str "(let [a\n ;stuff\n b\n\n;bother\nc d] e)"
                {:parse-string? true, :vector {:respect-nl? true}}))

  (expect
    "(let [a\n        ;stuff\n        b\n\n      ;bother\n      c d]\n  e)"
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
                      {:parse-string? true :comment {:smart-wrap {:last-max 80 :max-variance 500 :space-factor 100}}}))

  ;;
  ;; # Issue 136 -- constant pairing count is off with newlines
  ;;

  (expect "(;a\n list\n  :c\n\n  d)"
          (zprint-str "(;a\nlist\n:c\n\n d)"
                      {:parse-string? true, :style :respect-nl :comment {:smart-wrap {:last-max 80 :max-variance 500 :space-factor 100}}}))

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
          (zprint-str "{a\nb\n}"
                      {:parse-string? true, :map {:respect-nl? true}}))

  (expect "{a b\n\n}"
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
          (zprint-str "(;abc\n\n;def\n)" {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

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

  ; This used to have the ;commentx on the same line as the "a", but
  ; you can't hang a comment that didn't come in as an inline comment.
  (expect "#(a\n   ;commentx\n\n   b ;commenty\n\n )"
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

  (expect "#(a\n   ;commentx\n\n   b ;commenty\n )"
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
          (zprint-str
            "[a b c [d ;foo\n e ;bar\n [f g ;stuff\n] ;bother\n] h i j]"
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
      {:parse {:interpose "\n\n"}, :width 10 :comment {:smart-wrap {:border 0}}}))

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
                      {:parse-string? true, :style :respect-nl :tagged-literal {:hang? false}}))

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
      "#?(:clj (defn zpmap\n          ([f] \n\t   (if x y z)))\n    :cljs (defn zpmap\n          ([f] \n\t   (if x y z))))"
      {:parse-string? true, :style :indent-only}))

  ;;
  ;; This is related to the #145 issue, but it is about :prefix-tags.  The
  ;; bigger
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
      {:parse {:interpose nil, :left-space :keep}, :width 30 :comment {:smart-wrap {:border 0}}}))

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
    (zprint-str zprint.zprint-test/test-fast-hangstr {:parse-string? true :comment {:smart-wrap? false}}))

  (expect
"(defn test-fast-hang\n  \"Try to bring inline comments back onto the line on which they belong.\"\n  [{:keys [width], :as options} style-vec]\n  (loop [cvec style-vec\n         last-out [\"\" nil nil]\n         out []]\n    (if-not cvec\n      (do #_(def fico out) out)\n      (let [[s c e :as element] (first cvec)\n            [_ _ ne nn :as next-element] (second cvec)\n            [_ _ le] last-out\n            new-element (cond (and (or (= e :indent) (= e :newline))\n                                   (= ne :comment-inline))\n                                (if-not (or (= le :comment)\n                                            (= le :comment-inline))\n                                  ; Regular line to get the inline comment\n                                  [(blanks nn) c :whitespace 25]\n                                  ; Last element was a comment...\n                                  ; Can't put a comment on a comment, but\n                                  ; we want to indent it like the last\n                                  ; comment.\n                                  ; How much space before the last comment?\n                                  (do #_(prn \"inline:\"\n                                             (space-before-comment out))\n                                      [(str \"\\n\" (blanks out)) c :indent 41]\n                                      #_element))\n                              :else element)]\n        (recur (next cvec) new-element (conj out new-element))))))"
    (zprint-str zprint.zprint-test/test-fast-hangstr
                {:parse-string? true, :style :fast-hang :comment {:smart-wrap? false}}))

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
     ["false" :cyan :element] [" " :none :whitespace]
     ["#\"regex\"" :red :element] [" " :none :whitespace]
     ["asymbol" :magenta :element] [" " :none :whitespace] ["{" :red :left]
     [":a" :green :element] [" " :none :whitespace] [":b" :green :element]
     ["," :cyan :whitespace] [" " :none :whitespace] [":c" :green :element]
     [" " :none :whitespace] [":d" :green :element] ["}" :red :right]
     ["]" :purple :right]]
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

  ;;
  ;; Issue #56, changes to constant pairing
  ;;

  (def mapp6
    "(m/app :get  (m/app middle1 middle2 middle3\n                    [route] handler\n\t\t    ; How do comment work?\n                    [route] \n        (handler this is \"a\" test \"this\" is \"only a\" test) \n\t\t    )\n       :post (m/app middle of the road\n                    [route] handler\n                    [route] ; What about comments here?\n\t\t    handler))")

  (expect
    "(m/app :get (m/app middle1\n                   middle2\n                   middle3\n                   [route] handler\n                   ; How do comment work?\n                   [route] (handler this\n                                    is\n                                    \"a\" test\n                                    \"this\" is\n                                    \"only a\" test))\n       :post (m/app middle\n                    of\n                    the\n                    road\n                    [route] handler\n                    [route] ; What about comments here?\n                      handler))"
    (zprint-str mapp6
                {:parse-string? true,
		 :comment {:smart-wrap {:border 0}}
                 :fn-map {"app" [:none
                                 {:list {:constant-pair-min 1,
                                         :constant-pair-fn #(or (vector? %)
                                                                (keyword? %))},
                                  :next-inner {:list {:constant-pair-fn nil,
                                                      :constant-pair-min 2}}}]},
                 :width 55}))

  (expect
    "(m/app :get (m/app middle1\n                   middle2\n                   middle3\n                   [route]\n                   handler\n                   ; How do comment work?\n                   [route]\n                   (handler this\n                            is\n                            \"a\" test\n                            \"this\" is\n                            \"only a\" test))\n       :post (m/app middle\n                    of\n                    the\n                    road\n                    [route]\n                    handler\n                    [route] ; What about comments here?\n                    handler))"
    (zprint-str mapp6 {:parse-string? true, :width 55 :comment {:smart-wrap {:border 0}}}))

  ;; Let's see if the :style works

  (def mapp7
    "(m/app :get  (m/app middle1 middle2 middle3\n                    [route] handler\n\t\t    ; How do comments work?\n                    [route] \n        (handler this is \"a\" test \"this\" is \"only a\" test) \n\t\t    )\n       ; How do comments work here?\n       true (should be paired with true)\n       false (should be paired with false)\n       6 (should be paired with 6)\n       \"string\" (should be paired with string)\n       :post (m/app \n                    [route] handler\n                    [route] ; What about comments here?\n\t\t    handler))")


  (expect
    "(m/app\n  :get     (m/app\n             middle1\n             middle2\n             middle3\n             [route] handler\n             ; How do comments work?\n             [route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  ; How do comments work here?\n  true     (should be paired with true)\n  false    (should be paired with false)\n  6        (should be paired with 6)\n  \"string\" (should be paired with string)\n  :post    (m/app\n             [route] handler\n             [route] ; What about comments here?\n               handler))"
    (zprint-str mapp7 {:parse-string? true, :style :moustache}))

  ;;
  ;; Line endings
  ;;
  ;; zprint-file-str

  (def lendu
    "(this is a\ntest this is\nonly a test ; comment\n stuff\n bother)")
  (def lendd
    "(this is a\r\ntest this is\r\nonly a test ; comment\r\n stuff\r\n bother)")
  (def lendr
    "(this is a\rtest this is\ronly a test ; comment\r stuff\r bother)")

  (def lendmu
    "(this is)\n (a test) \n (this is) ; a comment \n (only a test\n)")
  (def lendmd
    "(this is)\r\n (a test) \r\n (this is) ; a comment \r\n (only a test\r\n)")
  (def lendmr
    "(this is)\r (a test) \r (this is) ; a comment \r (only a test\r)")

  (expect
    "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test ; comment\n      stuff\n      bother)"
    (zprint-file-str lendu "stuff" {}))

  (expect
    "(this is\r\n      a\r\n      test\r\n      this\r\n      is\r\n      only\r\n      a\r\n      test ; comment\r\n      stuff\r\n      bother)"
    (zprint-file-str lendd "stuff" {}))

  (expect
    "(this is\r      a\r      test\r      this\r      is\r      only\r      a\r      test ; comment\r      stuff\r      bother)"
    (zprint-file-str lendr "stuff" {}))

  (expect "(this is)\n(a test)\n(this is) ; a comment\n(only a test)"
          (zprint-file-str lendmu "stuff" {}))

  (expect "(this is)\r\n(a test)\r\n(this is) ; a comment\r\n(only a test)"
          (zprint-file-str lendmd "stuff" {}))

  (expect "(this is)\r(a test)\r(this is) ; a comment\r(only a test)"
          (zprint-file-str lendmr "stuff" {}))

  ;;
  ;; parse-string?
  ;;

  (expect
    "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test ; comment\n      stuff\n      bother)"
    (zprint-str lendu {:parse-string? true}))

  (expect
    "(this is\r\n      a\r\n      test\r\n      this\r\n      is\r\n      only\r\n      a\r\n      test ; comment\r\n      stuff\r\n      bother)"
    (zprint-str lendd {:parse-string? true}))

  (expect
    "(this is\r      a\r      test\r      this\r      is\r      only\r      a\r      test ; comment\r      stuff\r      bother)"
    (zprint-str lendr {:parse-string? true}))

  ;;
  ;; parse-string-all?
  ;;

  (expect "(this is)\n(a test)\n(this is)\n; a comment\n(only a test)"
          (zprint-str lendmu {:parse-string-all? true}))

  (expect "(this is)\r\n(a test)\r\n(this is)\r\n; a comment\r\n(only a test)"
          (zprint-str lendmd {:parse-string-all? true}))

  (expect "(this is)\r(a test)\r(this is)\r; a comment\r(only a test)"
          (zprint-str lendmr {:parse-string-all? true}))

  ;;
  ;; New style
  ;;

  (def style-m1
    {:fn-map {"app" [:none
                     {:list {:constant-pair-min 1,
                             :constant-pair-fn #(or (keyword? %)
                                                    (string? %)
                                                    (number? %)
                                                    (= true %)
                                                    (= false %)
                                                    (vector? %))},
                      :pair {:justify? true},
                      :next-inner {:list {:constant-pair-min 4,
                                          :constant-pair-fn nil},
                                   :pair {:justify? false}}}]}})

  (def mapp9
    "(m/app :get  (m/app middle1 middle2 middle3 
                    [route] handler 
                    [longer route]  
        (handler this is \"a\" test \"this\" is \"only a\" test)) 
       ; How do comments work here? 
       true (should be paired with true) 
       false (should be paired with false) 
       6 (should be paired with 6) 
       \"string\" (should be paired with string) 
       :post (m/app  
                    [a really long route] handler 
                    [route]  
                    handler))")

  (expect
    "(m/app :get     (m/app middle1\n                       middle2\n                       middle3\n                       [route]        handler\n                       [longer route] (handler this is \"a\" test \"this\" is \"only a\" test))\n       ; How do comments work here?\n       true     (should be paired with true)\n       false    (should be paired with false)\n       6        (should be paired with 6)\n       \"string\" (should be paired with string)\n       :post    (m/app [a really long route] handler [route] handler))"
    (zprint-str mapp9 (merge-deep style-m1 {:parse-string? true, :width 100})))

  ;;
  ;; Problems with hang and flow and anon-fn's  It was hanging :ret in the
  ;; second < thing.
  ;;

  (expect
    "(and #(>= (:ret %)\n          (-> %\n              :args\n              :start))\n     #(< :ret\n         :stuff\n         (this is a test this is only a test)\n         (more of a test when will it ever be long enough)\n         :bother))"
    (zprint-str
      "(and #(>= (:ret %)\n          (-> %\n              :args\n              :start))\n     #(<\n         :ret \n\t :stuff\n\t (this is a test, this is only a test)\n\t (more of a test, when will it ever be long enough)\n\t :bother))"
      {:parse-string? true}))

  ;;
  ;; Clean up -> so that it doesn't constant-pair top level things.
  ;;

  (expect
    "(and #(>= (:ret %)\n          (-> %\n              :args\n              :start))\n     #(< (:ret %)\n         (-> %\n             :args\n             :stuff\n             :bother\n             :lots\n             (of stuff\n                 that\n                 is\n                 long\n                 enough\n                 that\n                 it\n                 doesn't\n                 fit\n                 on\n                 one\n                 line\n                 and\n                 :should be\n                 :paired up)\n             :keywords\n             :end)))"
    (zprint-str
      "(and #(>= (:ret %)\n          (-> %\n              :args\n              :start))\n     #(<\n         (:ret %)\n         (-> %\n             :args\n\t     :stuff\n\t     :bother\n\t     :lots\n\t     (of stuff that is long enough that it doesn't fit on one line and :should be :paired up)\n\t     :keywords\n             :end)))"
      {:parse-string? true}))

  ;;
  ;; Tests for :nl-separator-all?
  ;;

  (expect
    "{:a :b,\n\n :c {:e :f, :g :h, :i :j, :k :l},\n\n :m :n,\n\n :o {:p {:q :r, :s :t}}}"
    (zprint-str
      {:a :b, :c {:e :f, :g :h, :i :j, :k :l}, :m :n, :o {:p {:q :r, :s :t}}}
      {:width 40, :map {:nl-separator-all? true}}))



  (expect "(let [a b\n\n      c d\n\n      e f\n\n      g h]\n  nil)"
          (zprint-str "(let [a b c d e f g h] nil)"
                      {:parse-string? true,
                       :width 20,
                       :binding {:nl-separator-all? true}}))


  (expect "(cond a b\n\n      c d\n\n      e f\n\n      g h)"
          (zprint-str
            "(cond a b c d e f g h)"
            {:parse-string? true, :width 20, :pair {:nl-separator-all? true}}))

  ;;
  ;; Missing code!  Issue #173
  ;;

  (expect "(fn [x]\n  (bar)\n)"
          (zprint-str "(fn [x]\n  (bar)\n   )\n"
                      {:parse-string? true, :list {:respect-nl? true}}))

  ;;
  ;; :wrap for lists, now using wrap-zmap
  ;;

  ; this should just fit

  (expect "(stuff (caller aaaa bbb\n         ccc))"
          (zprint-str "(stuff (caller aaaa bbb ccc))"
                      {:parse-string? true,
                       :list {:respect-nl? false},
                       :fn-map {"caller" :wrap, "this" :wrap},
                       :width 23}))


  ; this should not fit

  (expect "(stuff (caller aaaa\n         bbb ccc))"
          (zprint-str "(stuff (caller aaaa bbb ccc))"
                      {:parse-string? true,
                       :list {:respect-nl? false},
                       :fn-map {"caller" :wrap, "this" :wrap},
                       :width 22}))

  ; longer version of the same thing

  (expect
    "(stuff (caller aaaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll mmm nnn ooo\n         ppp qqq rrr sss ttt uuu vvv))"
    (zprint-str
      "(stuff (caller aaaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll mmm nnn ooo ppp qqq rrr sss ttt uuu vvv))"
      {:parse-string? true,
       :list {:respect-nl? false},
       :fn-map {"caller" :wrap, "this" :wrap},
       :width 75}))

  (expect
    "(stuff (caller aaaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll mmm nnn\n         ooo ppp qqq rrr sss ttt uuu vvv))"
    (zprint-str
      "(stuff (caller aaaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll mmm nnn ooo ppp qqq rrr sss ttt uuu vvv))"
      {:parse-string? true,
       :list {:respect-nl? false},
       :fn-map {"caller" :wrap, "this" :wrap},
       :width 74}))

  (expect
"(stuff (caller aaaa\n         (this is a\n           (test this is (only a test))) a b\n         c))"
    (zprint-str
      "(stuff (caller aaaa (this is a (test this is (only a test))) a b c))"
      {:parse-string? true,
       :list {:respect-nl? false},
       :fn-map {"caller" :wrap, "this" :wrap},
       :width 46}))

  (expect
"(stuff (caller aaaa\n         (this is a\n           (test this is (only a test))) a b\n         c))"
    (zprint-str
      "(stuff (caller aaaa (this is a (test this is (only a test))) a b c))"
      {:parse-string? true,
       :list {:respect-nl? false},
       :fn-map {"caller" :wrap, "this" :wrap},
       :width 45}))

  ;;
  ;; Better cond -- look at how :do, :when are handled.  These don't have binding
  ;; vectors after them.
  ;;

  (def bc1
    "(cond\n   (odd? a) 1\n   :let [a (quot a 2)]\n   :when-let [x (fn-which-may-return-falsey a),\n              y (fn-which-may-return-falsey (* 2 a))]\n   :when-some [b (fn-which-may-return-nil x),\n               c (fn-which-may-return-nil y)]\n   :when (seq x)\n   :do (println x)\n   (odd? (+ x y)) 2\n   3)")

  (expect
"(cond (odd? a) 1\n      :let [a (quot a 2)]\n      :when-let [x (fn-which-may-return-falsey a)\n                 y (fn-which-may-return-falsey (* 2 a))]\n      :when-some [b (fn-which-may-return-nil x)\n                  c (fn-which-may-return-nil y)]\n      :when (seq x)\n      :do (println x)\n      (odd? (+ x y)) 2\n      3)"
    (zprint-str bc1
                {:parse-string? true,
                 :pair {:flow? false, :nl-separator-all? false}}))

  (expect
"(cond (odd? a)\n        1\n      :let [a (quot a 2)]\n      :when-let [x (fn-which-may-return-falsey a)\n                 y (fn-which-may-return-falsey (* 2 a))]\n      :when-some [b (fn-which-may-return-nil x)\n                  c (fn-which-may-return-nil y)]\n      :when (seq x)\n      :do (println x)\n      (odd? (+ x y))\n        2\n      3)"
    (zprint-str bc1
                {:parse-string? true,
                 :pair {:flow? true, :nl-separator-all? false}}))

  (expect
"(cond (odd? a)\n        1\n\n      :let [a (quot a 2)]\n\n      :when-let [x (fn-which-may-return-falsey a)\n                 y (fn-which-may-return-falsey (* 2 a))]\n\n      :when-some [b (fn-which-may-return-nil x)\n                  c (fn-which-may-return-nil y)]\n\n      :when (seq x)\n\n      :do (println x)\n\n      (odd? (+ x y))\n        2\n\n      3)"
    (zprint-str bc1
                {:parse-string? true,
                 :pair {:flow? true, :nl-separator-all? true}}))

  ;;
  ;; Make sure that the better cond formatting doesn't show up in maps
  ;;

  (expect
    "{(odd? (+ x y)) 2,\n (odd? a) 1,\n :do (println x),\n :else 3,\n :let [a (quot a 2)],\n :when (seq x),\n :when-let [x (fn-which-may-return-falsey a) y\n            (fn-which-may-return-falsey (* 2 a))],\n :when-letter [x (fn-which-may-return-falsey a) y\n               (fn-which-may-return-falsey (* 2 a))],\n :when-some [b (fn-which-may-return-nil x) c (fn-which-may-return-nil y)]}"
    (zprint-str
      "{\n   (odd? a) 1\n   :let [a (quot a 2)]\n   :when-let [x (fn-which-may-return-falsey a),\n              y (fn-which-may-return-falsey (* 2 a))]\n   :when-letter [x (fn-which-may-return-falsey a),\n              y (fn-which-may-return-falsey (* 2 a))]\n   :when-some [b (fn-which-may-return-nil x),\n               c (fn-which-may-return-nil y)]\n   :when (seq x)\n   :do (println x)\n   (odd? (+ x y)) 2\n   :else 3}"
      {:parse-string? true}))

  ;;
  ;; Small bug is justification with rightcnt when doing the first thing
  ;;

  (expect
    "(let [a    1\n      bb   2\n      ccc  3\n      dddd 4\n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam}\n        1\n      bb   2\n      ccc  3\n      dddd 4])"
    (zprint-str
      "(let [a 1\n      bb 2\n      ccc 3\n      dddd 4\n      {:keys [foo bar baz bark key1 key2 key3 key4] :as spam} 1\n      bb 2\n      ccc 3\n      dddd 4])\n"
      {:parse-string? true,
       :binding {:justify? true, :justify {:max-variance 20}},
       :width 62}))

  ;;
  ;; Justification tests for underscore (and variance)
  ;;

  (def i179g
    "(let [bb 2 \n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam} 1 \n      ccc 3\n      _ (this is a (test this (is only a (test))))\n      dddd 4])\n")

  (expect
    "(let [bb   2\n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam} 1\n      ccc  3\n      _    (this is a (test this (is only a (test))))\n      dddd 4])"
    (zprint-str i179g
                {:parse-string? true,
                 :binding {:justify? true, :justify {:max-variance 20}},
                 :remove {:binding {:justify {:no-justify #{"_"}}}},
                 :width 64}))
  (expect
    "(let [bb   2\n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam}\n        1\n      ccc  3\n      _    (this is a (test this (is only a (test))))\n      dddd 4])"
    (zprint-str i179g
                {:parse-string? true,
                 :binding {:justify? true, :justify {:max-variance 20}},
                 :remove {:binding {:justify {:no-justify #{"_"}}}},
                 :width 63}))

  (expect
    "(let [bb   2\n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam}\n        1\n      ccc  3\n      _ (this is a (test this (is only a (test))))\n      dddd 4])"
    (zprint-str i179g
                {:parse-string? true,
                 :binding {:justify? true, :justify {:max-variance 20}},
                 :width 63}))

  (expect
    "(let [bb   2\n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam}\n        1\n      ccc  3\n      _ (this is a (test this (is only a (test))))\n      dddd 4])"
    (zprint-str i179g
                {:parse-string? true,
                 :binding {:justify? true, :justify {:max-variance 20}},
                 :width 63}))

  (expect
    "(let [bb                                                       2\n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam} 1\n      ccc                                                      3\n      _ (this is a (test this (is only a (test))))\n      dddd                                                     4])"
    (zprint-str i179g
                {:parse-string? true,
                 :binding {:justify? true, :justify {:max-variance 600}},
                 :width 80}))

  (expect
    "(let [bb   2\n      {:keys [foo bar baz bark key1 key2 key3 key4], :as spam} 1\n      ccc  3\n      _ (this is a (test this (is only a (test))))\n      dddd 4])"
    (zprint-str i179g {:parse-string? true, :style :justified}))

  ;;
  ;; Issue #175 -- clean up formatting of quoted lists
  ;;

  (def i175
    "(def ^:private config-keys\n  '(bootstrapper cassandra\n                 graphql\n                 http-client\n                 measurer\n                 postgres\n                 redis\n                 service\n                 graphql\n                 http-client\n                 measurer\n                 postgres\n                 redis\n                 graphql\n                 http-client\n                 measurer\n                 postgres\n                 redis\n                 graphql\n                 http-client\n                 measurer\n                 postgres\n                 redis))")

  (expect
    "(def ^:private config-keys\n  '(bootstrapper\n    cassandra\n    graphql\n    http-client\n    measurer\n    postgres\n    redis\n    service\n    graphql\n    http-client\n    measurer\n    postgres\n    redis\n    graphql\n    http-client\n    measurer\n    postgres\n    redis\n    graphql\n    http-client\n    measurer\n    postgres\n    redis))"
    (zprint-str i175 {:parse-string? true}))

  (expect
    "(def ^:private config-keys\n  '(bootstrapper cassandra graphql http-client measurer postgres redis service\n    graphql http-client measurer postgres redis graphql http-client measurer\n    postgres redis graphql http-client measurer postgres redis))"
    (zprint-str i175
                {:parse-string? true,
                 :fn-map {:quote [:wrap
                                  {:list {:indent 1},
                                   :next-inner {:list {:indent 2}}}]}}))

  ;;
  ;; Check that we can turn it off!
  ;;

  (expect
    "'(let\n  [aadsf bdfds cdfs ddffsd djfdls djfldsfj]\n  (cdfdf dffds flkdsjfl sdfkdjl fjdsfj a))"
    (zprint-str
      "'(let [aadsf bdfds cdfs ddffsd djfdls djfldsfj] (cdfdf dffds flkdsjfl sdfkdjl fjdsfj a))"
      {:parse-string? true}))

  (expect
    "'(let [aadsf bdfds\n       cdfs ddffsd\n       djfdls djfldsfj]\n   (cdfdf dffds flkdsjfl sdfkdjl fjdsfj a))"
    (zprint-str
      "'(let [aadsf bdfds cdfs ddffsd djfdls djfldsfj] (cdfdf dffds flkdsjfl sdfkdjl fjdsfj a))"
      {:parse-string? true, :fn-map {:quote :none}}))

  ;;
  ;; And that one line output works, but can be overridden
  ;;

  (expect "'(a\n  b\n  c\n  d\n  e\n  f\n  g)"
          (zprint-str "'(a b c d e f g)"
                      {:parse-string? true,
                       :fn-map {:quote [:flow
                                        {:list {:indent 1},
                                         :next-inner {:list {:indent 2}}}]}}))
  (expect "'(a b c d e f g)"
          (zprint-str "'(a b c d e f g)" {:parse-string? true}))

  ;;
  ;; Changes to justification -- change how good enough tests for justification
  ;;

  (def dfg
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third  (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth  (nth sexpr 4 nil)\n           [docstring option option-value]\n             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]\n                   (string? third)  [third nil nil]\n                   (keyword? third) [nil third fourth]\n                   :else            [nil nil nil])\n           guide  (cond-> [:element :element-best :newline]\n                    docstring (conj :element :newline)\n                    option    (conj :element :element :newline)\n                    :else     (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))\n")


  (expect
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth (nth sexpr 4 nil)\n           [docstring option option-value]\n             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]\n                   (string? third) [third nil nil]\n                   (keyword? third) [nil third fourth]\n                   :else [nil nil nil])\n           guide (cond-> [:element :element-best :newline]\n                   docstring (conj :element :newline)\n                   option (conj :element :element :newline)\n                   :else (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"
    (zprint-str dfg {:parse-string? true}))

  (expect
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third  (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth  (nth sexpr 4 nil)\n           [docstring option option-value]\n             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]\n                   (string? third) [third nil nil]\n                   (keyword? third) [nil third fourth]\n                   :else [nil nil nil])\n           guide  (cond-> [:element :element-best :newline]\n                    docstring (conj :element :newline)\n                    option    (conj :element :element :newline)\n                    :else     (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"
    (zprint-str dfg
                {:parse-string? true,
                 :style :justified,
                 :pair {:justify {:ignore-for-variance nil}}}))

  (expect
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third  (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth  (nth sexpr 4 nil)\n           [docstring option option-value]\n             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]\n                   (string? third)  [third nil nil]\n                   (keyword? third) [nil third fourth]\n                   :else            [nil nil nil])\n           guide  (cond-> [:element :element-best :newline]\n                    docstring (conj :element :newline)\n                    option    (conj :element :element :newline)\n                    :else     (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"
    (zprint-str dfg {:parse-string? true, :style :justified}))

  ;;
  ;; Issue #187 -- loss of comments in meta-data
  ;;


  (expect "(def ^{:meta :x}\n     ; one\n     ; two\n     ; three\n     :body)"
          (zprint-str
            "(def\n  ^{:meta :x}\n  ; one\n  ; two\n  ; three\n  :body)\n"
            {:parse-string? true :comment {:smart-wrap {:space-factor 100}}}))

  ;;
  ;; :pair {:justify {:ignore-for-variance ...}}
  ;; :style :justified
  ;;

  (def dpg
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third  (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth  (nth sexpr 4 nil)\n           [docstring option option-value] (cond (and (string? third)\n                                                      (keyword? fourth))\n                                                   [third fourth fifth]\n                                                 (string? third) [third nil nil]\n                                                 (keyword? third) [nil third\n                                                                   fourth]\n                                                 :else [nil nil nil])\n           guide  (cond-> [:element :element-best :newline]\n                    docstring (conj :element :newline)\n                    option    (conj :element :element :newline)\n                    :else     (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))\n")

  (expect
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third  (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth  (nth sexpr 4 nil)\n           [docstring option option-value]\n             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]\n                   (string? third) [third nil nil]\n                   (keyword? third) [nil third fourth]\n                   :else [nil nil nil])\n           guide  (cond-> [:element :element-best :newline]\n                    docstring (conj :element :newline)\n                    option    (conj :element :element :newline)\n                    :else     (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"
    (zprint-str dpg
                {:parse-string? true,
                 :style :justified,
                 :pair {:justify {:ignore-for-variance nil}}}))


  (expect
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third  (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth  (nth sexpr 4 nil)\n           [docstring option option-value]\n             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]\n                   (string? third)  [third nil nil]\n                   (keyword? third) [nil third fourth]\n                   :else            [nil nil nil])\n           guide  (cond-> [:element :element-best :newline]\n                    docstring (conj :element :newline)\n                    option    (conj :element :element :newline)\n                    :else     (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"
    (zprint-str dpg {:parse-string? true, :style :justified}))


  (expect

"(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third                           (nth sexpr 2 nil)\n           fourth                          (nth sexpr 3 nil)\n           fifth                           (nth sexpr 4 nil)\n           [docstring option option-value] (cond (and (string? third)\n                                                      (keyword? fourth))\n                                                   [third fourth fifth]\n                                                 (string? third) [third nil nil]\n                                                 (keyword? third) [nil third\n                                                                   fourth]\n                                                 :else [nil nil nil])\n           guide                           (cond-> [:element :element-best\n                                                    :newline]\n                                             docstring (conj :element :newline)\n                                             option\n                                               (conj :element :element :newline)\n                                             :else (conj\n                                                     :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"

    (zprint-str dpg
                {:parse-string? true,
                 :pair {:justify {:max-variance 1000}},
                 :binding {:justify {:max-variance 1000}},
                 :style :justified}))

  (expect
    "(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third (nth sexpr 2 nil)\n           fourth (nth sexpr 3 nil)\n           fifth (nth sexpr 4 nil)\n           [docstring option option-value]\n             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]\n                   (string? third) [third nil nil]\n                   (keyword? third) [nil third fourth]\n                   :else [nil nil nil])\n           guide (cond-> [:element :element-best :newline]\n                   docstring (conj :element :newline)\n                   option (conj :element :element :newline)\n                   :else (conj :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"
    (zprint-str dpg {:parse-string? true}))

  ;;
  ;; Test :parse {:ignore-if-parse-fails ...}
  ;; and :map {:key-no-sort ...}
  ;;

  #?(:clj
       (expect
         "java.lang.Exception: Unable to parse the string '[{k 1 g 2 c 3 aaa}]' because of 'java.lang.IllegalArgumentException: No value supplied for key: aaa'.  Consider adding any unallowed elements to {:parse {:ignore-if-parse-fails #{ <string> }}}"
         (try (zprint-str "[{k 1 g 2 c 3 aaa}]"
                          {:parse-string? true, :style :odr})
              (catch Exception e (str e))))
     :cljs
       (expect
         "Error: Unable to parse the string '[{k 1 g 2 c 3 aaa}]' because of 'Error: No value supplied for key: aaa'.  Consider adding any unallowed elements to {:parse {:ignore-if-parse-fails #{ <string> }}}"
         (try (zprint-str "[{k 1 g 2 c 3 aaa}]"
                          {:parse-string? true, :style :odr})
              (catch :default e (str e)))))

  (expect "[{aaa, c 3, g 2, k 1}]"
          (zprint-str "[{k 1 g 2 c 3 aaa}]"
                      {:parse-string? true,
                       :style :odr,
                       :parse {:ignore-if-parse-fails #{"aaa"}}}))
  (expect "[{k 1, g 2, c 3, aaa}]"
          (zprint-str "[{k 1 g 2 c 3 aaa}]"
                      {:parse-string? true,
                       :style :odr,
                       :parse {:ignore-if-parse-fails #{"aaa"}},
                       :map {:key-no-sort #{"aaa"}}}))

  ;;
  ;; Issue 188
  ;;

  (def i188 "{:a 1,\n :b 2,\n ...}\n")

  (expect "{:a 1, :b 2, ...}" (zprint-str i188 {:parse-string? true}))

  (def vbm "[{:a 1 :b 2 ...}]")


  (expect "[{..., :a 1, :b 2}]"
          (zprint-str vbm
                      {:parse-string? true,
                       :remove {:map {:key-no-sort #{"..."}}},
                       :style :odr}))

  #?(:clj
       (expect
         "java.lang.Exception: Unable to parse the string '[{:a 1 :b 2 ...}]' because of 'java.lang.IllegalArgumentException: No value supplied for key: ...'.  Consider adding any unallowed elements to {:parse {:ignore-if-parse-fails #{ <string> }}}"
         (try (zprint-str vbm
                          {:parse-string? true,
                           :remove {:map {:key-no-sort #{"..."}},
                                    :parse {:ignore-if-parse-fails #{"..."}}},
                           :style :odr})
              (catch Exception e (str e))))
     :cljs
       (expect
         "Error: Unable to parse the string '[{:a 1 :b 2 ...}]' because of 'Error: No value supplied for key: ...'.  Consider adding any unallowed elements to {:parse {:ignore-if-parse-fails #{ <string> }}}"
         (try (zprint-str vbm
                          {:parse-string? true,
                           :remove {:map {:key-no-sort #{"..."}},
                                    :parse {:ignore-if-parse-fails #{"..."}}},
                           :style :odr})
              (catch :default e (str e)))))

  ;;
  ;; Lots of #188 issue with "..."
  ;;

  (expect "({:c/d :e, {a b, ...} :b})"
          (zprint-str "({{a b ...} :b :c/d :e})"
                      {:parse-string? true, :map {:lift-ns? true}}))

  (expect "({:a 1, ...} {:b 2, ...})"
          (zprint-str "({:a 1, ...} {:b 2, ...})" {:parse-string? true}))

  (expect "#{:c {:a :b, ...}}"
          (zprint-str "#{{:a :b ...} :c}" {:parse-string? true}))

  (expect
    "(test {:a 1, ...}\n      {:b 2, ...}\n      {:c 3, ...}\n      {:e 4, ...})"
    (zprint-str "(test {:a 1 ...}{:b 2 ...} {:c 3 ...} {:e 4 ...})\n"
                {:parse-string? true,
                 :fn-map {"test" [:none {:style :vector-pairs}]},
                 :width 30}))

  ;;
  ;; unlift-ns? does it to symbols too
  ;;

  (expect "#:c{:b 2, a 1}"
          (zprint-str "#:c{a 1 :b 2}"
                      {:parse-string? true, :map {:unlift-ns? true}}))

  ;;
  ;; Issue #176 -- defprotocol and defrecord need -body when using
  ;; style community
  ;;

  (expect
    "\n(ns fmt\n  (:require [com.stuartsierra.component :as component]\n            [clojure.string :as str]))\n\n(defprotocol IThing\n  (thing [this]))\n\n(defrecord App [db]\n  component/Lifecycle\n    (start [this] (assoc this :db (atom {:fmt \"please\"})))\n    (stop [this] (dissoc this :db))\n  IThing\n    (thing [this] (:db this)))"
    (zprint-file-str
      "\n(ns fmt\n  (:require\n   [com.stuartsierra.component :as component]\n   [clojure.string :as str]))\n\n(defprotocol IThing\n (thing [this]))\n\n(defrecord App [db]\n component/Lifecycle\n (start [this] (assoc this :db (atom {:fmt \"please\"})))\n (stop [this] (dissoc this :db))\n\n IThing\n (thing [this] (:db this)))"
      "x"
      {:style :community}))

  ;;
  ;; :set in fn-map
  ;;

  (expect "(#{:stuff}\n :stuff)"
          (zprint-str "(#{:stuff} :stuff)"
                      {:parse-string? true,
                       :fn-map {:set [:force-nl {:list {:hang? false}}]}}))


  ;;
  ;; :map in fn-map
  ;;

  (expect "({:a :b}\n d\n e\n f)"
          (zprint-str "({:a :b} d e f)"
                      {:parse-string? true, :fn-map {:map :force-nl}}))

  ;;
  ;; :list in fn-map
  ;;

  (expect "((a b c)\n  d\n  e\n  f)"
          (zprint-str "((a b c) d e f)"
                      {:parse-string? true, :fn-map {:list :force-nl}}))

  ;;
  ;; :vector in fn-map
  ;;

  (expect "([a b c]\n d\n e\n f)"
          (zprint-str "([a b c] d e f)"
                      {:parse-string? true, :fn-map {:vector :force-nl}}))

  ;;
  ;; :style :require-pair
  ;;

  (expect
"(ns zprint.core\n  (:require [zprint.zprint :as :zprint\n                           :refer [fzprint line-count max-width line-widths\n                                   expand-tabs zcolor-map\n                                   determine-ending-split-lines]]\n            [zprint.zutil :refer [zmap-all zcomment? edn* whitespace? string\n                                  find-root-and-path-nw]]\n            [zprint.finish :refer [cvec-to-style-vec compress-style no-style-map\n                                   color-comp-vec handle-lines]]))"
    (zprint-str
      "(ns zprint.core\n  (:require\n    [zprint.zprint :as :zprint\n                   :refer [fzprint line-count max-width line-widths expand-tabs\n                           zcolor-map determine-ending-split-lines]]\n    [zprint.zutil :refer [zmap-all zcomment? edn* whitespace? string\n                          find-root-and-path-nw]]\n    [zprint.finish :refer [cvec-to-style-vec compress-style no-style-map\n                           color-comp-vec handle-lines]]))\n"
      {:parse-string? true, :style :require-pair}))

  ;;
  ;; are
  ;;

  (def are3 "(are [x y z] (= x y z)  
  2 (+ 1 1) (- 4 2)
  4 (* 2 2) (/ 8 2))")

  (expect "(are [x y z] (= x y z)\n  2 (+ 1 1) (- 4 2)\n  4 (* 2 2) (/ 8 2))"
          (zprint-str are3 {:parse-string? true}))

  ;;
  ;; rules of defn tests (mostly for guides)
  ;;

  (def rod3
    "
(defn rod3
  ([a b c d]
   (cond (nil? a) (list d)
         (nil? b) (list c d a b)
         :else (list a b c d)))
  ([a b c] (rod3 a b c nil)))")

  (expect
    "(defn rod3\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) (list c d a b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod3 a b c nil)))"
    (zprint-str rod3 {:parse-string? true, :style :rod, :width 32}))

  (expect
    "(defn rod3\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b)\n           (list c d a b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod3 a b c nil)))"
    (zprint-str rod3 {:parse-string? true, :style :rod, :width 31}))

  (expect
    "(defn rod3\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b)\n           (list c d a b)\n         :else\n           (list a b c d)))\n\n  ([a b c]\n   (rod3 a b c nil)))"
    (zprint-str rod3 {:parse-string? true, :style :rod, :width 30}))

  ;;
  ;; Rules of defn test to see if :next-inner-restore strategy works
  ;;

  (expect
    "(defn rod3a\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) ([list]\n                   c\n                   d\n                   a\n                   b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod3 a b c nil)))"
    (zprint-str
      "\n(defn rod3a\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) ([list] c d a b)\n         :else (list a b c d)))\n  ([a b c] (rod3 a b c nil)))"
      {:parse-string? true, :style :rod, :fn-map {:vector :force-nl}}))

  (expect
    "(defn rod3a\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) ([list] c d a b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod3 a b c nil)))"
    (zprint-str
      "\n(defn rod3a\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) ([list] c d a b)\n         :else (list a b c d)))\n  ([a b c] (rod3 a b c nil)))"
      {:parse-string? true, :style :rod}))

  ;;
  ;; Test about whether things that are quoted are in-code? or not.  If there
  ;; is something in the :fn-map for :quote, they are not.  There is something
  ;; in the :fn-map for :quote by default.  To change that, set :quote to
  ;; :none in the :fn-map.
  ;;

  (def qy "(def x '(a {:a :b \"this\" [is a test] :c :d}))")
  (def qz "(def x (a {:a :b \"this\" [is a test] :c :d}))")

  (expect "(def x (a {:a :b, \"this\" [is a test], :c :d}))"
          (zprint-str qz {:parse-string? true}))

  (expect "(def x '(a {:a :b, :c :d, \"this\" [is a test]}))"
          (zprint-str qy {:parse-string? true}))

  (expect "(def x '(a {:a :b, \"this\" [is a test], :c :d}))"
          (zprint-str qy {:parse-string? true, :fn-map {:quote :none}}))

  (def qb
    "(def a '(let [kdlf sdklfjs sdlkfjs lskdfdsj sdljkfdslk slkdjfdkl jsdflkdsj lksdjfkls] (let [dlkfds jdsflkds sdjfkds lkdsfjlk jkldsf jklsdfl ] (more stuff and  bother))))")

  (expect
    "(def a\n  '(let\n    [kdlf sdklfjs sdlkfjs lskdfdsj sdljkfdslk slkdjfdkl jsdflkdsj lksdjfkls]\n    (let\n     [dlkfds jdsflkds sdjfkds lkdsfjlk jkldsf jklsdfl]\n     (more stuff and bother))))"
    (zprint-str qb {:parse-string? true}))

  (expect
    "(def a\n  '(let [kdlf sdklfjs\n         sdlkfjs lskdfdsj\n         sdljkfdslk slkdjfdkl\n         jsdflkdsj lksdjfkls]\n     (let [dlkfds jdsflkds\n           sdjfkds lkdsfjlk\n           jkldsf jklsdfl]\n       (more stuff and bother))))"
    (zprint-str qb {:parse-string? true, :fn-map {:quote :none}}))


  ;;
  ;; # :next-inner-restore
  ;;

  (expect
    "(defn restore\n\n  \"this is a test\"\n\n  [this is only a test]\n\n  (let [does this have any blank lines] (in it)))"
    (zprint-str
      "(defn restore\n\n  \"this is a test\"\n\n  [this is only a test]\n\n  (let [does this\n\n        have any\n\t\n\tblank lines]\n   \n\n     (in it)))\n"
      {:parse-string? true,
       :fn-map {"defn" [:arg1-body
                        {:list {:respect-nl? true},
                         :map {:respect-nl? true},
                         :vector {:respect-nl? true},
                         :set {:respect-nl? true},
                         :next-inner-restore
                           [[:list :respect-nl?] [:map :respect-nl?]
                            [:vector :respect-nl?] [:set :respect-nl?]]}]}}))

  (expect
    "(defn restore\n\n  \"this is a test\"\n\n  [this is only a test]\n\n  (let [does this\n\n        have any\n\n        blank lines]\n\n\n    (in it)))"
    (zprint-str
      "(defn restore\n\n  \"this is a test\"\n\n  [this is only a test]\n\n  (let [does this\n\n        have any\n\t\n\tblank lines]\n   \n\n     (in it)))\n"
      {:parse-string? true,
       :fn-map {"defn" [:arg1-body {:style :respect-nl}]}}))

  ;;
  ;; :next-inner-restore for things that are nil initially
  ;;

  (expect "{:e {:e :f, :c :d, :a :b}, :c :d, :a :b}"
          (zprint-str {:a :b, :c :d, :e {:a :b, :c :d, :e :f}}
                      {:map {:key-order [:e :c :a]}}))

  (expect "{:a :b, :c :d, :e {:a :b, :c :d, :e :f}}"
          (zprint-str {:a :b, :c :d, :e {:a :b, :c :d, :e :f}}
                      {:map {:key-order [:e :c :a]},
                       :next-inner-restore [[:map :key-order]]}))

  (expect "{:e {:a :b, :c :d, :e :f}, :c :d, :a :b}"
          (zprint-str {:a :b, :c :d, :e {:a :b, :c :d, :e :f}}
                      {:next-inner {:map {:key-order [:e :c :a]},
                                    :next-inner-restore [[:map :key-order]]}}))

  ;;
  ;; :next-inner-restore for changes in constant-pairing configuration
  ;;

  (expect
    "(m/app :get (m/app middle1\n                   middle2\n                   middle3\n                   [route] handler\n                   ; How do comment work?\n                   [route] (handler this\n                                    is\n                                    \"a\" test\n                                    \"this\" is\n                                    \"only a\" test))\n       :post (m/app middle\n                    of\n                    the\n                    road\n                    [route] handler\n                    [route] ; What about comments here?\n                      handler))"
    (zprint-str
      mapp6
      {:parse-string? true,
       :comment {:smart-wrap {:border 0}}
       :fn-map {"app" [:none
                       {:list {:constant-pair-min 1,
                               :constant-pair-fn #(or (vector? %)
                                                      (keyword? %))},
                        :next-inner-restore [[:list :constant-pair-min]
                                             [:list :constant-pair-fn]]}]},
       :width 55}))

  ;;
  ;; :next-inner-restore for sets
  ;;

  (expect
    "(defn selected-protocol-for-indications\n  {:pre [(map? m) (empty? m)],\n   :post [(not-empty %)]}\n  [{:keys [spec]} procedure-id indications]\n  (->> {:procedure-id procedure-id,\n        :pre preceding,\n        :indications indications}\n       (sql/op spec queries :selected-protocol-for-indications)\n       (map :protocol-id)))"
    (zprint-str
      "(defn selected-protocol-for-indications\n  { :pre [(map? m) (empty? m)] :post [(not-empty %)] }\n  [{:keys [spec]} procedure-id indications]\n  (->> {:procedure-id procedure-id, :pre preceding :indications indications}\n       (sql/op spec queries :selected-protocol-for-indications)\n       (map :protocol-id)))\n"
      {:parse-string? true,
       :fn-map {"defn" [:arg1-force-nl-body
                        {:map {:force-nl? true,
                               :sort-in-code? true,
                               :key-no-sort #{":pre"}}}]}}))

  (expect
    "(defn selected-protocol-for-indications\n  {:pre [(map? m) (empty? m)],\n   :post [(not-empty %)]}\n  [{:keys [spec]} procedure-id indications]\n  (->> {:indications indications, :pre preceding, :procedure-id procedure-id}\n       (sql/op spec queries :selected-protocol-for-indications)\n       (map :protocol-id)))"
    (zprint-str
      "(defn selected-protocol-for-indications\n  { :pre [(map? m) (empty? m)] :post [(not-empty %)] }\n  [{:keys [spec]} procedure-id indications]\n  (->> {:procedure-id procedure-id, :pre preceding :indications indications}\n       (sql/op spec queries :selected-protocol-for-indications)\n       (map :protocol-id)))\n"
      {:parse-string? true,
       :fn-map {"defn" [:arg1-force-nl-body
                        {:next-inner {:map {:force-nl? true,
                                            :sort-in-code? true,
                                            :key-no-sort #{":pre"}},
                                      :next-inner-restore [[:map :force-nl?]
                                                           [[:map :key-no-sort]
                                                            ":pre"]]}}]}}))

  ;;
  ;; # Issue #200 -- exception when using :arg2-extend when the input doesn't
  ;; match extend-like input.
  ;;

  (expect "(deffoo Xyz []\n  (go []))"
          (zprint-str "(deffoo Xyz []\n  (go []))\n"
                      {:parse-string? true,
                       :list {:respect-nl? true},
                       :fn-map {"deffoo" :arg2-extend}}))
  (expect "(defrecord ~tagname ~fields\n\n  (stuff)\n\n)"
          (zprint-str
            " (defrecord ~tagname ~fields\n\n          (stuff)\n\n          )"
            {:parse-string? true, :list {:respect-nl? true}}))

  ;;
  ;; ## Test ##NaN
  ;;

  (expect "(def y ##NaN)" (zprint-str "(def y ##NaN)" {:parse-string? true}))

  ;;
  ;; # Issue #199 namespaced maps
  ;;

  (expect
    "(is (= #::sut{:groups #{\"FOO_ADMIN\" \"FOO_USER\"}}\n       (sut/map->user {:groups [\"FOO_USER\" \"FOO_ADMIN\"]})))"
    (zprint-str
      "(is (= #::sut{:groups #{\"FOO_ADMIN\" \"FOO_USER\"}} (sut/map->user {:groups [\"FOO_USER\" \"FOO_ADMIN\"]})))"
      {:parse-string? true}))


  ;;
  ;; Issue #184 -- don't print vectors on one line even if they fit.
  ;;

  (expect
    "(s/def ::record\n  (s/keys :req-un [::reference-id\n                   ::record-number\n                   ::addresses\n                   ::contacts]\n          :opt-un [::birth-date\n                   ::family-name\n                   ::names]))"
    (zprint-str
      "(s/def ::record\n  (s/keys :req-un [::reference-id\n                   ::record-number\n                   ::addresses\n                   ::contacts]\n          :opt-un [::birth-date\n                   ::family-name\n                   ::names]))\n"
      {:parse-string? true,
       :fn-map {"s/keys" [:none {:vector {:force-nl? true}}]}}))

  ;;
  ;; Same thing for lists
  ;;

  (expect
    "(def ^:private config-keys\n  '(bootstrapper\n    cassandra\n    graphql\n    http-client))"
    (zprint-str
      "(def ^:private config-keys  '(bootstrapper cassandra graphql http-client))\n"
      {:parse-string? true, :list {:force-nl? true}}))

  ;;
  ;; :key-value-options
  ;;

  (expect "{:a :b, :c (this is a ...), :d (more testing)}"
          (zprint-str {:a :b,
                       :c '(this is a test this is only a test),
                       :d '(more testing)}
                      {:map {:key-value-options {:c {:max-length 3}}}}))

  (expect
    "{:a :b,\n :c ##,\n :d (more testing)}"
    (zprint-str
      {:a :b, :c '(this is a test this is only a test), :d '(more testing)}
      {:map {:key-value-options {:c {:max-length 0},
                                 :d {:list {:force-nl? true}}}}}))

  ;;
  ;; Issue 153 where new stuff didn't format like old stuff
  ;;

  (expect
    "(this is\n      (->> (this is a test this is only a test of whether or it fits in eightyx)\n           more\n           stuff)\n      still\n      a\n      test)"
    (zprint-str
      "(this is\n      (->> (this is a test this is only a test of whether or it fits in eightyx)\n           more\n\t   stuff)\n      still\n      a\n      test)\n"
      {:parse-string? true, :width 80}))

  ;;
  ;; More 153 issues, where restore didn't quite do it all
  ;;

  (expect
    "(defn stuff\n  [{:keys [bother]}]\n  (let [bother-node (:node bother)\n        all (-> (bother/db bother-node)\n                (bother/q '{:find [(pull md [*])],\n                            :where [[md :xxxxxxx/type\n                                     :procurement-consideration]]}))\n        xf (comp (map first)\n                 (map mapper.procurment-consideration/bother-entity->model))]\n    (into [] xf all)))"
    (zprint-str
      "(defn stuff\n  [{:keys [bother]}]\n  (let [bother-node (:node bother)\n        all (-> (bother/db bother-node)\n                (bother/q '{:find [(pull md [*])]\n                          :where [[md :xxxxxxx/type :procurement-consideration]]}))\n        xf (comp (map first) (map mapper.procurment-consideration/bother-entity->model))]\n    (into [] xf all)))\n"
      {:parse-string? true, :width 80}))

  ;;
  ;; This is where the fixes for extend processing got too carried away, and
  ;; didn't allow a "nil" as a symbol.
  ;;

  (expect
    "(extend-protocol interface.static-bus/Troubler\n  nil\n    (publish [_ trouble]\n      (logging/warn \"Troubler received nil payload, not troubling.\"\n                    (some->> trouble\n                             :id\n                             (hash-map :trouble-id)))))"
    (zprint-str
      "(extend-protocol interface.static-bus/Troubler\n nil\n   (publish [_ trouble]\n     (logging/warn \"Troubler received nil payload, not troubling.\"\n                   (some->> trouble\n                            :id\n                            (hash-map :trouble-id)))))\n"
      {:parse-string? true}))

  ;;
  ;; Issue #204 -- odd behavior with :style :hiccup
  ;;

  (expect
    "[a {:a (let [val1 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"\n             val2 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"]\n         (str val1 val2))}]"
    (zprint-str
      "[a {:a (let [val1 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\" val2 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"]\n         (str val1 val2))}]\n"
      {:parse-string? true, :style :hiccup, :vector {:wrap? true}}))


  (expect
    "[a {:a (let [val1 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"\n             val2 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"]\n         (str val1 val2))}]"
    (zprint-str
      "[a {:a (let [val1 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\" val2 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"]\n         (str val1 val2))}]\n"
      {:parse-string? true, :style :hiccup}))


  (expect
    "[a\n {:a (let [val1 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"\n           val2 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"]\n       (str val1 val2))}]"
    (zprint-str
      "[a {:a (let [val1 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\" val2 \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\"]\n         (str val1 val2))}]\n"
      {:parse-string? true}))

  ;;
  ;; Issue at end of #153 -- problems with alpha version of :next-inner :restore
  ;;

  (def i153pcs
    {:parse-string? true,
     :fn-map {"abc" [:none
                     {:next-inner {:map {:force-nl? true},
                                   :next-inner-restore [[:map :force-nl?]]}}],
              "def" [:none
                     {:next-inner {:list {:force-nl? true},
                                   :next-inner-restore [[:list :force-nl?]]}}],
              "ghi" [:none
                     {:list {:force-nl? true},
                      :next-inner-restore [[:list :force-nl?]]}]}})

  (expect
    "(abc {:a :b,\n      :c :d}\n     (this is a test {:e :f, :g :h})\n     (def {:a :b, :c :d}\n          (this is\n                a\n                test\n                {:e :f, :g :h}))\n     (ghi {:a :b, :c :d}\n          (this is a test {:e :f, :g :h})))"
    (zprint-str
      "(abc {:a :b :c :d} (this is a test {:e :f :g :h}) \n     (def {:a :b :c :d} (this is a test {:e :f :g :h}))\n     (ghi {:a :b :c :d} (this is a test {:e :f :g :h}))\n     )\n"
      i153pcs))


  (expect
    "(def {:a :b, :c :d}\n     (this is\n           a\n           test\n           {:e :f, :g :h})\n     (abc {:a :b,\n           :c :d}\n          (this is a test {:e :f, :g :h}))\n     (ghi {:a :b, :c :d}\n          (this is a test {:e :f, :g :h})))"
    (zprint-str
      "(def {:a :b :c :d} (this is a test {:e :f :g :h}) \n     (abc {:a :b :c :d} (this is a test {:e :f :g :h}))\n     (ghi {:a :b :c :d} (this is a test {:e :f :g :h}))\n     )\n"
      i153pcs))

  (expect
    "(ghi {:a :b, :c :d}\n     (this is a test {:e :f, :g :h})\n     (abc {:a :b,\n           :c :d}\n          (this is a test {:e :f, :g :h}))\n     (def {:a :b, :c :d}\n          (this is\n                a\n                test\n                {:e :f, :g :h})))"
    (zprint-str
      "(ghi {:a :b :c :d} (this is a test {:e :f :g :h}) \n     (abc {:a :b :c :d} (this is a test {:e :f :g :h}))\n     (def {:a :b :c :d} (this is a test {:e :f :g :h}))\n     )\n"
      i153pcs))

  (def i153tas
    {:fn-map {"->" [:noarg1-body
                    {:list {:constant-pair? false},
                     :next-inner-restore [[:list :constant-pair?]]}],
              "->>" [:force-nl-body
                     {:list {:constant-pair? false},
                      :next-inner-restore [[:list :constant-pair?]]}],
              "defn" [:arg1-body
                      {:next-inner {:map {:force-nl? true,
                                          :key-order [:pre :post],
                                          :sort-in-code? false,
                                          :sort? true},
                                    :next-inner-restore
                                      [[:map :force-nl?] [:map :key-order]
                                       [:map :sort-in-code?] [:map :sort?]]}}]},
     :parse-string? true})

  (expect
    "(defn selected-protocol-for-indications\n  {:pre [(map? m) (empty? m)],\n   :post [(not-empty %)]}\n  [{:keys [spec]} procedure-id indications]\n  (->> {:procedure-id procedure-id, :indications indications}\n       (sql/op spec queries :selected-protocol-for-indications)\n       (map :protocol-id)))"
    (zprint-str
      "(defn selected-protocol-for-indications\n  {:pre [(map? m) (empty? m)], :post [(not-empty %)]}\n  [{:keys [spec]} procedure-id indications]\n  (->> {:procedure-id procedure-id, :indications indications}\n       (sql/op spec queries :selected-protocol-for-indications)\n       (map :protocol-id)))\n"
      i153tas))


  ;;
  ;; Fix bug encountered when dealing with issue #205 discussion
  ;;

  (expect
    "(when errors\n  (json-api/error \"\" :errors errors))"
    (zprint-str
      "(when errors (json-api/error \"\" :errors errors))\n"
      {:parse-string? true,
       :fn-map
         {"when" [:arg1-body
                  {:list
                     {:option-fn
                        (fn
                          ([] "(when * (json-api/error ...)) never on one line")
                          ([opts n exprs]
                           (when (and (>= n 3)
                                      (= (first (nth exprs 2)) 'json-api/error))
                             {:fn-style :arg1-force-nl-body})))},
                   :next-inner {:list {:option-fn nil}}}]}}))

  ;;
  ;; Problem when the first thing in a vector is not sexpr-able, and you use
  ;; :style :keyword-respect-nl  Issue #206

  (expect "[#_a 1]"
          (zprint-str "[#_a\n 1]\n"
                      {:parse-string? true, :style :keyword-respect-nl}))

  (expect "[#_a :test\n 1]"
          (zprint-str "[#_a :test\n1]\n"
                      {:parse-string? true, :style :keyword-respect-nl}))

  ;;
  ;; Issue #209 -- indent-only causes any expression containing () to be
  ;; dropped!
  ;;

  (expect "(def a ())"
          (zprint-str "(def a ())\n"
                      {:parse-string? true, :style :indent-only}))

  (expect "()" (zprint-str "()" {:parse-string? true, :style :indent-only}))

  ;;
  ;; Issue #166 -- problems with justification of strings!
  ;;

  (expect
    "(ns my.awesome.app\n  (:require\n    [example.library         :as library]\n    [\"@vimeo/player$default\" :as vimeo]\n    [\"@f/app$default\"        :as firebase])\n  (:require-macros [cljs.analyzer.macros :refer\n                    [allowing-redef disallowing-ns* disallowing-recur]]\n                   [cljs.env.macros :as env]\n                   [devcards.core :as dc :refer\n                    [defcard defcard-doc deftest dom-node defcard-om-next]])\n  (:import [java.io File]\n           [java.util HashMap ArrayList]\n           [org.apache.storm.task OutputCollector IBolt TopologyContext]\n           [goog.net XhrIo])\n  (:use [backtype.storm cluster util thrift config log]))"
    (zprint-str
      "(ns my.awesome.app\n  (:require \n    [example.library :as library]\n    [\"@vimeo/player$default\" :as vimeo]\n    [\"@f/app$default\" :as firebase])\n  (:require-macros \n    [cljs.analyzer.macros :refer [allowing-redef disallowing-ns* disallowing-recur]]\n    [cljs.env.macros :as env]\n    [devcards.core :as dc :refer [defcard defcard-doc deftest dom-node defcard-om-next]])\n  (:import\n    [java.io File]\n    [java.util HashMap ArrayList]\n    [org.apache.storm.task OutputCollector IBolt TopologyContext]         \n    [goog.net XhrIo])\n  (:use\n    [backtype.storm cluster util thrift config log]))\n"
      {:parse-string? true,
       :style :require-justify,
       :style-map {:rj-var {:pair {:justify {:max-variance 1000}}}}}))

  ;;
  ;; # :indent-here and friends
  ;;

  (expect "(stuff (caller aaaa bbbb\n                    ccc dddddd))"
          (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                      {:parse-string? true,
                       :list {:respect-nl? false},
                       :guide-debug [:list 2
                                     [:element :element :indent-here :element
                                      :newline :element-*]],
                       :width 80}))

  (expect "(stuff (caller aaaa    bbbb\n                       ccc dddddd))"
          (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                      {:parse-string? true,
                       :list {:respect-nl? false},
                       :guide-debug [:list 2
                                     [:element :element :spaces 4 :indent-here
                                      :element :newline :element-*]],
                       :width 80}))

  ;;
  ;; # :spaces before and after :align
  ;;

  (expect
    "(stuff (caller aaaa    bbbb\n                            ccc))"
    (zprint-str "(stuff (caller aaaa bbbb ccc))"
                {:parse-string? true,
                 :list {:respect-nl? false},
                 :guide-debug [:list 2
                               [:element :element :spaces 4 :mark 0 :element
                                :newline :align 0 :spaces 5 :element]],
                 :width 36}))

  (expect
    "(stuff (caller aaaa    bbbb\n         ccc                ddd))"
    (zprint-str "(stuff (caller aaaa bbbb ccc ddd))"
                {:parse-string? true,
                 :list {:respect-nl? false},
                 :guide-debug [:list 2
                               [:element :element :spaces 4 :mark 0 :element
                                :newline :element :align 0 :spaces 5 :element]],
                 :width 36}))

  (expect
    "(stuff (caller aaaa    bbbb\n         ccc           ddd))"
    (zprint-str "(stuff (caller aaaa bbbb ccc ddd))"
                {:parse-string? true,
                 :list {:respect-nl? false},
                 :guide-debug [:list 2
                               [:element :element :spaces 4 :mark 0 :element
                                :newline :element :spaces 5 :align 0 :element]],
                 :width 36}))

  ;;
  ;; # Spaces before and after align, with :indent-here involved
  ;;

  (expect
    "(stuff\n  (caller aaaa    bbbb\n                       ccc\n                       dddd eeee\n                       fffff gggg\n                       hhhh iii jjj\n                       kkk lll mmm))"
    (zprint-str
      "(stuff (caller aaaa bbbb ccc dddd eeee fffff gggg hhhh iii jjj kkk lll mmm))"
      {:parse-string? true,
       :list {:respect-nl? false},
       :guide-debug [:list 2
                     [:element :element :spaces 4 :mark 0 :element :newline
                      :align 0 :spaces 5 :indent-here :element :newline
                      :element-*]],
       :width 36}))

(expect
  "(stuff (caller aaaa    bbbb\n                     ccc\n                     dddd eeee fffff\n                     gggg hhhh iii\n                     jjj kkk lll\n                     mmm))"
  (zprint-str
    "(stuff (caller aaaa bbbb ccc dddd eeee fffff gggg hhhh iii jjj kkk lll mmm))"
    {:parse-string? true,
     :list {:respect-nl? false},
     :guide-debug [:list 2
                   [:element :element :spaces 4 :mark 0 :element :newline :align
                    0 :spaces -2 :indent-here :element :newline :element-*]],
     :width 36}))

  ;;
  ;; # negative space after align
  ;;

  (expect
    "(stuff (caller aaaa    bbbb\n                    ccc\n                       dddd))"
    (zprint-str "(stuff (caller aaaa bbbb ccc dddd))"
                {:parse-string? true,
                 :list {:respect-nl? false},
                 :guide-debug [:list 2
                               [:element :element :spaces 4 :mark 0 :element
                                :newline :align 0 :spaces -3 :indent-align 0
                                :element :newline :element-*]],
                 :width 50}))


  ;;
  ;; # :spaces are additive in guides
  ;;

  (expect
    "(stuff (caller aaaa       bbbb ccc dddd))"
    (zprint-str "(stuff (caller aaaa bbbb ccc dddd))"
                {:parse-string? true,
                 :list {:respect-nl? false},
                 :guide-debug
                   [:list 2 [:element :element :spaces 4 :spaces 3 :element-*]],
                 :width 80}))

  ;;
  ;; # :indent-align
  ;;

  (expect
    "(stuff (caller aaaa    bbbb\n                                 ccc\n                       dddd))"
    (zprint-str "(stuff (caller aaaa bbbb ccc dddd))"
                {:parse-string? true,
                 :list {:respect-nl? false},
                 :guide-debug [:list 2
                               [:element :element :spaces 4 :mark 0 :element
                                :newline :align 0 :spaces 5 :spaces 5
                                :indent-align 0 :element :newline :element-*]],
                 :width 50}))


  (expect
    "(stuff (caller aaaa    bbbb\n                                 ccc\n         dddd))"
    (zprint-str "(stuff (caller aaaa bbbb ccc dddd))"
                {:parse-string? true,
                 :list {:respect-nl? false},
                 :guide-debug [:list 2
                               [:element :element :spaces 4 :mark 0 :element
                                :newline :align 0 :spaces 5 :spaces 5
                                :indent-align 1 :element :newline :element-*]],
                 :width 50}))

  ;;
  ;; # Some tests for comment API, also Issue #191 changes
  ;;

  (expect
    ";!zprint {:format :off}\n  (this is\n a test)\n\n\n    (stuff\n bother)\n\n"
    (zprint-file-str
      ";!zprint {:format :off}\n  (this is\n a test)\n   \n   \n    (stuff \n bother)\n  \n"
      ""
      {}))

  (expect
    ";!zprint {:format :skip}\n  (this is\n a test)\n\n\n(stuff bother)\n\n"
    (zprint-file-str
      ";!zprint {:format :skip}\n  (this is\n a test)\n   \n   \n    (stuff \n bother)\n  \n"
      ""
      {}))

  (expect
    ";!zprint {:parse {:left-space :keep}}\n  (this is a test)\n\n\n    (stuff bother)\n\n"
    (zprint-file-str
      ";!zprint {:parse {:left-space :keep}}\n  (this is\n a test)\n   \n   \n    (stuff \n bother)\n  \n"
      ""
      {}))

  ;;
  ;; Issue #224 -- print just the map from metadata on the same line
  ;; as the def.
  ;;

  (def i224a
    "(deftest ^{:database true ::test.hooks/system-init-keys system-keys}\n         websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")]\n    foo))\n")

  (expect
    "(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}\n         websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str i224a {:parse-string? true}))

  (expect
    "(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str i224a {:parse-string? true, :meta {:split? true}}))

  (expect
    "(deftest ^{:database true,\n           ::test.hooks/system-init-keys system-keys,\n           :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^{:database true \n ::test.hooks/system-init-keys \n system-keys :another-map-key :another-map-value}\n\n         websocket-diagnostic-report-measurements-updated-event\n\n  (let [foo (bar \"1\")]\n    foo))\n"
      {:parse-string? true, :meta {:split? true}}))

  (expect
    "(deftest ^{:database true,\n           ::test.hooks/system-init-keys\n             system-keys,\n           :another-map-key :another-map-value}\n\n  websocket-diagnostic-report-measurements-updated-event\n\n  (let [foo (bar \"1\")]\n    foo))"
    (zprint-str
      "(deftest ^{:database true \n ::test.hooks/system-init-keys \n system-keys :another-map-key :another-map-value}\n\n         websocket-diagnostic-report-measurements-updated-event\n\n  (let [foo (bar \"1\")]\n    foo))\n"
      {:parse-string? true, :meta {:split? true}, :style :respect-nl}))

  (def i224c
    "(deftest ^{:database true} websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")]\n    foo))\n")

  (expect
    "(deftest ^{:database true} websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))"
    (zprint-str i224c {:parse-string? true, :meta {:split? false}}))

  (expect
    "(deftest ^{:database true}\n  websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))"
    (zprint-str i224c {:parse-string? true, :meta {:split? true}}))

  ;;
  ;; Issue #223 -- :community and :rod don't compose well
  ;;

  (expect
    "(defn foo [x]\n  (println x \"Hello, World!\")\n  (println x \"Hello, World!\")\n  (println x \"Hello, World!\"))"
    (zprint-str
      "(defn foo [x]\n (println x \"Hello, World!\")\n (println x \"Hello, World!\")\n (println x \"Hello, World!\"))\n"
      {:parse-string? true, :style [:community :rod]}))

  ;;
  ;; Issue #221 -- fix :fn to handle multiple arities (and also to work with
  ;; #fn-force-nl).
  ;;

  (expect
    "(extend-type ZprintType\n  ZprintProtocol\n    (more [a b]\n      (and a b))\n    (and-more\n      ([a] (nil? a))\n      ([a b] (or a b))))"
    (zprint-str
      "(extend-type ZprintType\n  ZprintProtocol\n (more [a b] \n\t(and a b))\n  (and-more ([a] \n\t(nil? a)) ([a b] \n\t(or a b))))"
      {:parse-string? true, :fn-force-nl #{:fn}}))

  (expect
    "(extend-type ZprintType\n  ZprintProtocol\n    (more [a b] (and a b))\n    (and-more\n      ([a] (nil? a))\n      ([a b] (or a b))))"
    (zprint-str
      "(extend-type ZprintType\n  ZprintProtocol\n (more [a b] \n\t(and a b))\n  (and-more ([a] \n\t(nil? a)) ([a b] \n\t(or a b))))"
      {:parse-string? true, :width 30}))

  (expect
    "(letfn [(first-fn [arg1 arg2]\n          (-> (doing-stuff)\n              (and-more-stuff)))\n        (second-fn [arg1 arg2]\n          (-> (doing-stuff)\n              (and-more-stuff)))]\n  (other-stuff))"
    (zprint-str
      "(letfn [(first-fn [arg1 arg2]\n                  (-> (doing-stuff)\n                      (and-more-stuff)))\n        (second-fn [arg1 arg2]\n                   (-> (doing-stuff)\n                       (and-more-stuff)))]\n    (other-stuff))\n"
      {:parse-string? true}))

(expect
  "(defn print-balance\n  [xml] ;\n  (let [balance (parse xml)]\n    (letfn [(transform [acc item]\n              (assoc acc\n                (separate-words (clean-key item)) (format-decimals\n                                                    (item balance))))]\n      (reduce transform {} (keys balance)))))"
  (zprint-str
    "(defn print-balance [xml]                                 ;\n  (let [balance (parse xml)]\n    (letfn [(transform [acc item]\n              (assoc acc\n                     (separate-words (clean-key item))\n                     (format-decimals (item balance))))]\n      (reduce transform {} (keys balance)))))\n"
    {:parse-string? true}))

  (expect
    "(letfn [(first-fn [arg1 arg2]\n          (-> (doing-stuff)\n              (and-more-stuff)))]\n  (other-stuff))"
    (zprint-str
      "(letfn [(first-fn [arg1 arg2]\n                  (-> (doing-stuff)\n                      (and-more-stuff)))]\n    (other-stuff))\n"
      {:parse-string? true}))

  (expect
    "(letfn [(first-fn [arg1 arg2] (this (doing-stuff) (and-more-stuff)))\n        (second-fn [arg1 arg2] (test (doing-stuff) (and-more-stuff)))]\n  (other-stuff))"
    (zprint-str
      "(letfn [(first-fn [arg1 arg2]\n                  (this (doing-stuff)\n                      (and-more-stuff)))\n        (second-fn [arg1 arg2]\n                   (test (doing-stuff)\n                       (and-more-stuff)))]\n    (other-stuff))\n"
      {:parse-string? true}))

  ;;
  ;; Alternative way to format metadata.  Also, :meta :eplit? true tests.
  ;;
  ;; Issue #224
  ;;

  (expect
    "(def\n  ^{:doc\n      \"Json serde. This enables our default camel-case out kebab case in behaviour.\"}\n  default-json-serde\n  serde/json-camel-kebab-serde)"
    (zprint-str
      "(def ^{:doc \"Json serde. This enables our default camel-case out kebab case in behaviour.\"}\n     default-json-serde\n  serde/json-camel-kebab-serde)\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^{:database true ::test.hooks/system-init-keys system-keys}\n         websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")]\n    foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest \n         websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")]\n    foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest ^{:database true} websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^{:database true} websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")]\n    foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest ^:database websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^:database websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest ^:database-stuff-and-bother\n  websocket-diagnostic-and-a-bit-more-that-does-not-fit\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^:database-stuff-and-bother  websocket-diagnostic-and-a-bit-more-that-does-not-fit\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect "(def ^:private foo \"bar\")"
          (zprint-str "(def ^:private foo \"bar\")\n"
                      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event\n  (foo))"
    (zprint-str
      "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event (foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n  ^{:database true, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest\n  ^{:database-a-bit-longer :does-not-fit,\n    ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n  ^{:database-a-bit-longer :does-not-fit, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style :meta-alt}))

  (expect
    "(deftest\n\n  ^{:database true,\n\n    ::test.hooks/system-init-keys system-keys,\n\n    :another-map-key :another-map-value}\n\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n\n  ^{:database \n  true,\n\n    ::test.hooks/system-init-keys system-keys,\n\n    :another-map-key \n    :another-map-value}\n\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style [:meta-alt :respect-bl]}))

  (expect
    "(deftest\n\n  ^{:database\n      true,\n\n    ::test.hooks/system-init-keys system-keys,\n\n    :another-map-key\n      :another-map-value}\n\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n\n  ^{:database \n  true,\n\n    ::test.hooks/system-init-keys system-keys,\n\n    :another-map-key \n    :another-map-value}\n\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style [:meta-alt :respect-nl]}))

  ;;
  ;; With :style :community
  ;;

  ; i224

  (expect
    "(def\n  ^{:doc\n    \"Json serde. This enables our default camel-case out kebab case in behaviour.\"}\n  default-json-serde\n  serde/json-camel-kebab-serde)"
    (zprint-str
      "(def ^{:doc \"Json serde. This enables our default camel-case out kebab case in behaviour.\"}\n     default-json-serde\n  serde/json-camel-kebab-serde)\n"
      {:parse-string? true, :style [:meta-alt :community]}))

  ; i224a

  (expect
    "(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^{:database true ::test.hooks/system-init-keys system-keys}\n         websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")]\n    foo))\n"
      {:parse-string? true, :style [:meta-alt :community]}))

  ; i224b
  ;
  ; This does odd things, and hasn't any meta data anyway

  #_(expect
      "(deftest websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
      (zprint-str
        "(deftest \n         websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")]\n    foo))\n"
        {:parse-string? true, :style [:community :meta-alt]}))

  ; i224c

  (expect
    "(deftest ^{:database true} websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^{:database true} websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")]\n    foo))\n"
      {:parse-string? true, :style [:community :meta-alt]}))

  ; i224d

  (expect
    "(deftest ^:database websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^:database websocket-diagnostic-and-a-bit-more\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style [:community :meta-alt]}))

  ; i224e

  (expect
    "(deftest ^:database-stuff-and-bother\n  websocket-diagnostic-and-a-bit-more-that-does-not-fit\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest ^:database-stuff-and-bother  websocket-diagnostic-and-a-bit-more-that-does-not-fit\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style [:meta-alt :community]}))

  ; i224f

  (expect "(def ^:private foo \"bar\")"
          (zprint-str "(def ^:private foo \"bar\")\n"
                      {:parse-string? true, :style [:meta-alt :community]}))

  ; i224g

  (expect
    "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style [:community :meta-alt]}))

  ; i224h

  (expect
    "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event\n  (foo))"
    (zprint-str
      "(deftest\n  ^{:database true,\n    ::test.hooks/system-init-keys system-keys,\n    :another-map-key :another-map-value}\n  websocket-diagnostic-report-measurements-updated-event (foo))\n"
      {:parse-string? true, :style [:community :meta-alt]}))

  ; i224i

  (expect
    "(deftest ^{:database true, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n  ^{:database true, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style [:meta-alt :community]}))

  ; i224j

  (expect
    "(deftest\n  ^{:database-a-bit-longer :does-not-fit,\n    ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))"
    (zprint-str
      "(deftest\n  ^{:database-a-bit-longer :does-not-fit, ::test.hooks/system-init-keys system-keys}\n  websocket-diagnostic-report-measurements-updated-event\n  (let [foo (bar \"1\")] foo))\n"
      {:parse-string? true, :style [:community :meta-alt]}))

  ;;
  ;; Issue #230
  ;;

  (expect "(let)" (zprint-file-str "(let)" "stuff" {}))

  ;;
  ;; Multiple line capability in precede-w-nl.  i:list {:nl-count ...}}
  ;;

  (def pwnl2 "(this is\n\n a test \n\n this is \n\n only a test)")
  (def pwnl2a
    "(this is\n\n a test ;this is an inline comment \n\n ;this is a comemnt \n this is \n\n only a test)")

  (expect
    "(this\n  is\n  a\n  test\n  this\n  is\n  only\n  a\n  test)"
    (zprint-str
      pwnl2
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 1, :respect-nl? false}}))

  (expect
    "(this\n  is\n\n  a\n\n  test\n\n  this\n\n  is\n\n  only\n\n  a\n\n  test)"
    (zprint-str
      pwnl2
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 2, :respect-nl? false}}))

  (expect
    "(this\n  is\n\n  a\n  test\n\n  this\n  is\n\n  only\n  a\n  test)"
    (zprint-str
      pwnl2
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 1, :respect-nl? true}}))

  (expect
    "(this\n  is\n\n  a\n\n  test\n\n  this\n\n  is\n\n  only\n\n  a\n\n  test)"
    (zprint-str
      pwnl2
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 2, :respect-nl? true}}))

  (expect
    "(this\n  is\n  a\n  test ;this is an inline comment\n  ;this is a comemnt\n  this\n  is\n  only\n  a\n  test)"
    (zprint-str
      pwnl2a
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 1, :respect-nl? false}}))

  (expect
    "(this\n  is\n\n  a\n\n  test ;this is an inline comment\n\n  ;this is a comemnt\n  this\n\n  is\n\n  only\n\n  a\n\n  test)"
    (zprint-str
      pwnl2a
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 2, :respect-nl? false}}))

  (expect
    "(this\n  is\n\n  a\n  test ;this is an inline comment\n\n  ;this is a comemnt\n  this\n  is\n\n  only\n  a\n  test)"
    (zprint-str
      pwnl2a
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 1, :respect-nl? true}}))

  (expect
    "(this\n  is\n\n  a\n\n  test ;this is an inline comment\n\n  ;this is a comemnt\n  this\n\n  is\n\n  only\n\n  a\n\n  test)"
    (zprint-str
      pwnl2a
      {:parse-string? true,
       :list {:force-nl? true, :hang? false, :nl-count 2, :respect-nl? true}}))

  (expect
    "(this\n  is\n\n  a\n\n\n  test ;this is an inline comment\n\n\n\n  ;this is a comemnt\n  this\n\n\n\n\n  is\n\n\n\n\n\n  only\n\n\n\n\n\n\n  a\n\n\n\n\n\n\n\n  test)"
    (zprint-str pwnl2a
                {:parse-string? true,
                 :list {:force-nl? true,
                        :hang? false,
                        :nl-count [2 3 4 5 6 7 8 9],
                        :respect-nl? false}}))

  ;;
  ;; ## New capability -- strings as values in :fn-map, allows alias of
  ;; fn names.
  ;;

  (def are1b
    "(arex [x y z] (= x y z)\n   3 (stuff y) (bother z)\n   4 (foo y) (bar z))")

  (expect
    "(arex [x y z] (= x y z)\n  3 (stuff y) (bother z)\n  4 (foo y)   (bar z))"
    (zprint-str are1b
                {:parse-string? true,
                 :fn-map {"arex" "arey", "arey" "arez", "arez" "are"}}))

  #?(:clj
       (expect
         "java.lang.Exception: Circular :fn-map lookup! fn-str: 'arex' has already been used in this lookup. "
         (try (zprint-str are1b
                          {:parse-string? true,
                           :fn-map {"arex" "arey", "arey" "arex"}})
              (catch Exception e
                ; Set's don't print deterministically
                (clojure.string/replace (str e)
                                        (re-find #"fn-strs.*" (str e))
                                        ""))))
     :cljs
       (expect
         "Error: Circular :fn-map lookup! fn-str: 'arex' has already been used in this lookup. "
         (try (zprint-str are1b
                          {:parse-string? true,
                           :fn-map {"arex" "arey", "arey" "arex"}})
              (catch :default e
                ; Set's don't print deterministically
                (clojure.string/replace (str e)
                                        (re-find #"fn-strs.*" (str e))
                                        "")))))

  ;;
  ;; Add :max-gap to :justify for lots of things.  This tests pairs.
  ;;
  ;; Issue #239
  ;;

  (def pair1
    "
(defn pair1
  [a b c d]
  (cond (nil? a) a
        (a-very-long-function? b) b
        :else c))")

  (expect
    "(defn pair1\n  [a b c d]\n  (cond (nil? a) a\n        (a-very-long-function? b) b\n        :else c))"
    (zprint-str pair1
                {:parse-string? true,
                 :pair {:justify? true,
                        :justify {:max-variance 80, :max-gap 17}}}))

  (expect
    "(defn pair1\n  [a b c d]\n  (cond (nil? a)                  a\n        (a-very-long-function? b) b\n        :else                     c))"
    (zprint-str pair1
                {:parse-string? true,
                 :pair {:justify? true,
                        :justify {:max-variance 80, :max-gap 18}}}))

  (expect
    "(defn pair1\n  [a b c d]\n  (cond (nil? a)                  a\n        (a-very-long-function? b) b\n        :else                     c))"
    (zprint-str
      pair1
      {:parse-string? true,
       :pair {:justify? true,
              :justify
                {:max-variance 80, :max-gap 21, :ignore-for-variance nil}}}))

  (expect
    "(defn pair1\n  [a b c d]\n  (cond (nil? a) a\n        (a-very-long-function? b) b\n        :else c))"
    (zprint-str
      pair1
      {:parse-string? true,
       :pair {:justify? true,
              :justify
                {:max-variance 80, :max-gap 20, :ignore-for-variance nil}}}))

  ;;
  ;; tests for :flow-all-if-any?
  ;;
  ;; Binding:
  ;;

  (def pou12z
    "\n                                     (let [thread# (.thread e#)\n                                             ; the code below works as it slows down tracing\n\n\t\t\t\t\t\t ]\n\n\t\t\t\t\t\t (stuff)\n\n\n\t\t\t\t\t   )")

  (expect
    "(let [thread# (.thread e#)\n      ; the code below works as it slows down tracing\n     ]\n  (stuff))"
    (zprint-str pou12z
                {:parse-string? true, :binding {:flow-all-if-any? true}}))


  (def avgt
    "(defn avg-time\n  \"Take a function of no arguments, and run it n times, dropping the\n  first 5 runs every time.\"\n  ([f n return?]\n   (if (< n 10)\n     (println \"n must be at least 10\")\n     (let [results (doall (repeatedly n #(with-out-str (time (f)))))\n           good-results (drop 5 results)\n           time-strs (mapv #(re-find #\"d*.\" %) good-results)\n           time-seq (mapv read-string time-strs)\n           sorted-time-seq (sort time-seq)\n           _ (when-not return? (println \"sorted-time-seq\" sorted-time-seq))\n           no-outliers-time-seq\n             (drop 2 (take (- (count sorted-time-seq) 2) sorted-time-seq))\n           _ (when-not return?\n               (println \"no-outliers-time-seq\" no-outliers-time-seq))\n           avg (arith-mean no-outliers-time-seq)\n           med (local-median no-outliers-time-seq)]\n       (if return?\n         (int avg)\n         (do (println \"Average:\" (int avg) \"ms.\")\n             (println \"Median: \" (int med) \"ms.\"))))))\n  ([f n] (avg-time f n nil)))")

  (expect
    "(defn avg-time\n  \"Take a function of no arguments, and run it n times, dropping the\n  first 5 runs every time.\"\n  ([f n return?]\n   (if (< n 10)\n     (println \"n must be at least 10\")\n     (let [results\n             (doall (repeatedly n #(with-out-str (time (f)))))\n           good-results\n             (drop 5 results)\n           time-strs\n             (mapv #(re-find #\"d*.\" %) good-results)\n           time-seq\n             (mapv read-string time-strs)\n           sorted-time-seq\n             (sort time-seq)\n           _\n             (when-not return? (println \"sorted-time-seq\" sorted-time-seq))\n           no-outliers-time-seq\n             (drop 2 (take (- (count sorted-time-seq) 2) sorted-time-seq))\n           _\n             (when-not return?\n               (println \"no-outliers-time-seq\" no-outliers-time-seq))\n           avg\n             (arith-mean no-outliers-time-seq)\n           med\n             (local-median no-outliers-time-seq)]\n       (if return?\n         (int avg)\n         (do (println \"Average:\" (int avg) \"ms.\")\n             (println \"Median: \" (int med) \"ms.\"))))))\n  ([f n] (avg-time f n nil)))"
    (zprint-str avgt {:parse-string? true, :binding {:flow-all-if-any? true}}))

  ;;
  ;; map
  ;;

  (def i48l
    {:abcdefghijkl [{:abcd "bar", :abcdefg [:a :ab :abc :x :l]} :s :t :u :v],
     :abce {:abcd :abcd, :abcdef {:abcd "foo"}}})

  (expect
    "{:abcdefghijkl\n   [{:abcd \"bar\",\n     :abcdefg [:a :ab :abc :x :l]}\n    :s :t :u :v],\n :abce\n   {:abcd :abcd,\n    :abcdef {:abcd \"foo\"}}}"
    (zprint-str i48l {:width 35, :map {:flow-all-if-any? true}}))


  (def index-map
    {"mappings"
       {"lease4"
          {"_all" {"enabled" false},
           "properties"
             {"failover-pair-name" {"index" false, "type" "keyword"},
              "client-binary-client-id" {"type" "keyword"},
              "failover-role" {"index" false, "type" "keyword"},
              "class" {"index" false, "type" "keyword"},
              "giaddr" {"type" "ip"},
              "client-last-transaction-time" {"format" "yyyy-MM-dd HH:mm:ss",
                                              "type" "date"},
              "flags" {"index" false, "type" "keyword"},
              "failover-sequence-number" {"index" false, "type" "keyword"},
              "start-time-of-state"
                {"format" "yyyy-MM-dd HH:mm:ss", "index" false, "type" "date"},
              "write-time"
                {"format" "yyyy-MM-dd HH:mm:ss", "index" false, "type" "date"},
              "binding-start-time" {"format" "yyyy-MM-dd HH:mm:ss",
                                    "type" "date"},
              "state-serial" {"index" false, "type" "long"},
              "address" {"type" "ip"},
              "client-expiration-time"
                {"format" "yyyy-MM-dd HH:mm:ss", "index" false, "type" "date"},
              "partner-expiration-time"
                {"format" "yyyy-MM-dd HH:mm:ss", "index" false, "type" "date"},
              "data-source" {"index" false, "type" "keyword"},
              "lease-renewal-time"
                {"format" "yyyy-MM-dd HH:mm:ss", "index" false, "type" "date"},
              "state" {"type" "keyword"},
              "version" {"index" false, "type" "keyword"},
              "expiration" {"format" "yyyy-MM-dd HH:mm:ss", "type" "date"},
              "state-expiration-time" {"format" "yyyy-MM-dd HH:mm:ss",
                                       "type" "date"},
              "failover-lease-state" {"index" false, "type" "keyword"},
              "client-creation-time"
                {"format" "yyyy-MM-dd HH:mm:ss", "index" false, "type" "date"},
              "client-mac-addr" {"type" "keyword"},
              "binding-end-time" {"format" "yyyy-MM-dd HH:mm:ss",
                                  "type" "date"},
              "scope-name" {"type" "keyword"},
              "client-flags" {"index" false, "type" "keyword"}}}}})
  (expect
    "{\"mappings\"\n   {\"lease4\"\n      {\"_all\"\n         {\"enabled\" false},\n       \"properties\"\n         {\"address\"\n            {\"type\" \"ip\"},\n          \"binding-end-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\", \"type\" \"date\"},\n          \"binding-start-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\", \"type\" \"date\"},\n          \"class\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"client-binary-client-id\"\n            {\"type\" \"keyword\"},\n          \"client-creation-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\",\n             \"index\" false,\n             \"type\" \"date\"},\n          \"client-expiration-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\",\n             \"index\" false,\n             \"type\" \"date\"},\n          \"client-flags\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"client-last-transaction-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\", \"type\" \"date\"},\n          \"client-mac-addr\"\n            {\"type\" \"keyword\"},\n          \"data-source\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"expiration\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\", \"type\" \"date\"},\n          \"failover-lease-state\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"failover-pair-name\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"failover-role\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"failover-sequence-number\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"flags\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"giaddr\"\n            {\"type\" \"ip\"},\n          \"lease-renewal-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\",\n             \"index\" false,\n             \"type\" \"date\"},\n          \"partner-expiration-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\",\n             \"index\" false,\n             \"type\" \"date\"},\n          \"scope-name\"\n            {\"type\" \"keyword\"},\n          \"start-time-of-state\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\",\n             \"index\" false,\n             \"type\" \"date\"},\n          \"state\"\n            {\"type\" \"keyword\"},\n          \"state-expiration-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\", \"type\" \"date\"},\n          \"state-serial\"\n            {\"index\" false, \"type\" \"long\"},\n          \"version\"\n            {\"index\" false, \"type\" \"keyword\"},\n          \"write-time\"\n            {\"format\" \"yyyy-MM-dd HH:mm:ss\",\n             \"index\" false,\n             \"type\" \"date\"}}}}}"
    (zprint-str
      index-map
      {:parse-string? false, :map {:flow-all-if-any? true}, :width 60}))

  (def sort-demo
    [{:detail {:ident "64:c1:2f:34",
               :alternate "64:c1:2f:34",
               :string "datacenter",
               :interface "3.13.168.35"},
      :direction :received,
      :connection "2795",
      :reference 14873,
      :type "get",
      :time 1425704001,
      :code "58601"}
     {:detail {:ident "64:c1:2f:34",
               :ip4 "3.13.168.151",
               :time "30m",
               :code "0xe4e9"},
      :direction :transmitted,
      :connection "X13404",
      :reference 14133,
      :type "post",
      :time 1425704001,
      :code "0xe4e9"}
     {:direction :transmitted,
      :connection "X13404",
      :reference 14227,
      :type "post",
      :time 1425704001,
      :code "58601"}
     {:detail {:ident "50:56:a5:1d:61", :ip4 "3.13.171.81", :code "0x1344a676"},
      :direction :received,
      :connection "2796",
      :reference 14133,
      :type "error",
      :time 1425704003,
      :code "0x1344a676"}
     {:detail {:ident "50:56:a5:1d:61",
               :alternate "01:50:56:a5:1d:61",
               :string "datacenter",
               :interface "3.13.168.35"},
      :direction :transmitted,
      :connection "2796",
      :reference 14873,
      :type "error",
      :time 1425704003,
      :code "323266166"}])


  (expect
    "[{:code \"58601\",\n  :connection \"2795\",\n  :detail {:alternate \"64:c1:2f:34\",\n           :ident \"64:c1:2f:34\",\n           :interface \"3.13.168.35\",\n           :string \"datacenter\"},\n  :direction :received,\n  :reference 14873,\n  :time 1425704001,\n  :type \"get\"}\n {:code\n    \"0xe4e9\",\n  :connection\n    \"X13404\",\n  :detail\n    {:code \"0xe4e9\", :ident \"64:c1:2f:34\", :ip4 \"3.13.168.151\", :time \"30m\"},\n  :direction\n    :transmitted,\n  :reference\n    14133,\n  :time\n    1425704001,\n  :type\n    \"post\"}\n {:code \"58601\",\n  :connection \"X13404\",\n  :direction :transmitted,\n  :reference 14227,\n  :time 1425704001,\n  :type \"post\"}\n {:code \"0x1344a676\",\n  :connection \"2796\",\n  :detail {:code \"0x1344a676\", :ident \"50:56:a5:1d:61\", :ip4 \"3.13.171.81\"},\n  :direction :received,\n  :reference 14133,\n  :time 1425704003,\n  :type \"error\"}\n {:code \"323266166\",\n  :connection \"2796\",\n  :detail {:alternate \"01:50:56:a5:1d:61\",\n           :ident \"50:56:a5:1d:61\",\n           :interface \"3.13.168.35\",\n           :string \"datacenter\"},\n  :direction :transmitted,\n  :reference 14873,\n  :time 1425704003,\n  :type \"error\"}]"
    (zprint-str
      sort-demo
      {:parse-string? false, :map {:flow-all-if-any? true}, :width 80}))

  ;;
  ;; pairs
  ;;

  (def pair2
    "\n(defn pair1 \n  [a b c d]\n  (cond (nil? a) a\n        (a-very-long-function? b) b\n\t:else c))")

  (expect
    "(defn pair1\n  [a b c d]\n  (cond (nil? a) a\n        (a-very-long-function? b) b\n        :else c))"
    (zprint-str
      pair2
      {:parse-string? true,
       :dbg? false,
       :pair
         {:flow-all-if-any? true, :justify? true, :justify {:max-variance 20}},
       :width 40}))

  (expect
    "(defn pair1\n  [a b c d]\n  (cond (nil? a)\n          a\n        (a-very-long-function?\n          b)\n          b\n        :else\n          c))"
    (zprint-str
      pair2
      {:parse-string? true,
       :dbg? false,
       :pair
         {:flow-all-if-any? true, :justify? true, :justify {:max-variance 20}
	  :multi-lhs-hang? false},
       :width 30}))


  (def i235
    "(cond\n  (simple-check) (short-function-call)\n  (and (much-more-complicated-check) (an-even-longer-check-that-is-too-long))\n  (look-im-on-the-next-line)\n  :else (another-short-call))\n")

  (expect
    "(cond (simple-check)\n        (short-function-call)\n      (and (much-more-complicated-check)\n           (an-even-longer-check-that-is-too-long))\n        (look-im-on-the-next-line)\n      :else\n        (another-short-call))"
    (zprint-str
      i235
      {:parse-string? true,
       :dbg? false,
       :pair
         {:flow-all-if-any? true, :justify? true, :justify {:max-variance 30}
	  :multi-lhs-hang? false},
       :width 80}))

  ;;
  ;; :fn-type-map
  ;;

  (def i229u
    "(defrecord ADefrecord [f1 f2 f3]\n  AProtocol\n  (doit\n    ([this that]\n     (run! println [1 2 3])\n     (println this))\n    \n    ([this]\n     (doit [this nil])))\n  \n  (dothat [this that])\n\n  (domore [this that])\n  \n  AnotherProtocol\n  (xdoit [this])\n  \n  (xdothat [this that])\n  \n  (xdomore [this that]))\n")


  (expect
    "(defrecord ADefrecord [f1 f2 f3]\n  AProtocol\n  (doit\n    ([this that]\n     (run! println [1 2 3])\n     (println this))\n\n    ([this]\n     (doit [this nil])))\n\n  (dothat [this that])\n\n  (domore [this that])\n\n  AnotherProtocol\n  (xdoit [this])\n\n  (xdothat [this that])\n\n  (xdomore [this that]))"
    (zprint-str
      i229u
      {:parse-string? true,
       :extend {:nl-count 2, :nl-separator? true, :indent 0},
       :fn-type-map
         {:fn [:none
               {:list {:option-fn (partial rodfn {:multi-arity-nl? true})}}]}}))

  (expect
    "(defrecord ADefrecord [f1 f2 f3]\n  AProtocol\n    (doit\n      ([this that] (run! println [1 2 3]) (println this))\n      ([this] (doit [this nil])))\n    (dothat [this that])\n    (domore [this that])\n  AnotherProtocol\n    (xdoit [this])\n    (xdothat [this that])\n    (xdomore [this that]))"
    (zprint-str i229u {:parse-string? true}))

  ;;
  ;; Indirection
  ;;

  (expect
    "(defrecord ADefrecord [f1 f2 f3]\n  AProtocol\n  (doit\n    ([this that]\n     (run! println [1 2 3])\n     (println this))\n\n    ([this]\n     (doit [this nil])))\n\n  (dothat [this that])\n\n  (domore [this that])\n\n  AnotherProtocol\n  (xdoit [this])\n\n  (xdothat [this that])\n\n  (xdomore [this that]))"
    (zprint-str
      i229u
      {:parse-string? true,
       :extend {:nl-count 2, :nl-separator? true, :indent 0},
       :fn-type-map
         {:arg2 [:none
                 {:list {:option-fn (partial rodfn {:multi-arity-nl? true})}}],
          :fn :arg2}}))

  ;;
  ;; Can we get rid of it with :next-inner?
  ;;

  (def i229ua
    "(defrecord ADefrecord [f1 f2 f3]\n  AProtocol\n  (doit\n    ([this that]\n     (run! println [1 2 3])\n     (let [myfn (fn ([x] (println x)) ([x y] (+ x y)))] (more stuff))\n     (println this))\n    \n    ([this]\n     (doit [this nil])))\n  \n  (dothat [this that])\n\n  (domore [this that])\n  \n  AnotherProtocol\n  (xdoit [this])\n  \n  (xdothat [this that])\n  \n  (xdomore [this that]))\n")

  (expect
    "(defrecord ADefrecord [f1 f2 f3]\n  AProtocol\n  (doit\n    ([this that]\n     (run! println [1 2 3])\n     (let [myfn (fn ([x] (println x)) ([x y] (+ x y)))] (more stuff))\n     (println this))\n\n    ([this]\n     (doit [this nil])))\n\n  (dothat [this that])\n\n  (domore [this that])\n\n  AnotherProtocol\n  (xdoit [this])\n\n  (xdothat [this that])\n\n  (xdomore [this that]))"
    (zprint-str
      i229ua
      {:parse-string? true,
       :extend {:nl-count 2, :nl-separator? true, :indent 0},
       :fn-type-map {:fn [:none
                          {:list {:option-fn (partial rodfn
                                                      {:multi-arity-nl? true})},
                           :next-inner {:fn-type-map {:fn nil}}}]}}))

  ;;
  ;; Try multiple option maps in :fn-type-map, and see which has precedence
  ;;

  (def wchkds
    "(defn wchkd
  [x]
[:5 :8 11 14 17 20 23 26 29 32 35 38 41 44 47 50 53 56 59 62 65 68 71 74 77 80])
")

  (expect
    "(defn wchkd\n  [x]\n  [:5 :8 11 14 17 20 23 26 29 32 35 38 41 44 47 50 53 56 59 62 65 68\n   71 74 77 80])"
    (zprint-str
      wchkds
      {:parse-string? true,
       :width 50,
       :fn-map {"defn" [:arg2 {:width 70}]},
       :fn-type-map {:arg2 [:hang {:width 30}], :hang [:arg1 {:width 40}]}}))

  (expect
    "(defn wchkd\n  [x]\n  [:5 :8 11 14 17 20 23 26 29\n   32 35 38 41 44 47 50 53 56\n   59 62 65 68 71 74 77 80])"
    (zprint-str
      wchkds
      {:parse-string? true,
       :width 50,
       :fn-map {"defn" [:arg2 {}]},
       :fn-type-map {:arg2 [:hang {:width 30}], :hang [:arg1 {:width 40}]}}))

  (expect
    "(defn wchkd\n  [x]\n  [:5 :8 11 14 17 20 23 26 29 32 35 38\n   41 44 47 50 53 56 59 62 65 68 71 74\n   77 80])"
    (zprint-str wchkds
                {:parse-string? true,
                 :width 50,
                 :fn-map {"defn" [:arg2 {}]},
                 :fn-type-map {:arg2 [:hang {}], :hang [:arg1 {:width 40}]}}))

  (expect
    "(defn wchkd\n  [x]\n  [:5 :8 11 14 17 20 23 26 29 32 35 38 41 44 47 50\n   53 56 59 62 65 68 71 74 77 80])"
    (zprint-str wchkds
                {:parse-string? true,
                 :width 50,
                 :fn-map {"defn" [:arg2 {}]},
                 :fn-type-map {:arg2 [:hang {}], :hang [:arg1 {}]}}))

  ;;
  ;; Namespaced maps don't work with :indexed-only
  ;;

  (expect "#:a{:b 1}"
          (zprint-str "#:a{:b 1}\n" {:parse-string? true, :style :indent-only}))

  ;;
  ;; :indent-only issues with indent for anon-fns
  ;;
  ;; Issue #243
  ;;

  (expect
    "#(let [really-long-text-so-it-puts-on-two-lines %]\n   really-long-test-so-it-puts-on-two-lines)"
    (zprint-str
      "#(let [really-long-text-so-it-puts-on-two-lines %]\n         really-long-test-so-it-puts-on-two-lines)\n"
      {:parse-string? true, :style :indent-only}))

  ;;
  ;; Issue #245 -- :meta {:split? true} leaves out all but the first ^:stuff
  ;; metadata element
  ;;

  (expect
    "(def ^:const\n  ^:private\n  ^:test\n  ^:lots\n  ^:of\n  ^:meta\n  ^:stuff\n  port-file-name\n  \".nrepl-port\")"
    (zprint-str
      " (def ^:const ^:private ^:test ^:lots ^:of ^:meta ^:stuff port-file-name \".nrepl-port\")\n"
      {:parse-string? true, :meta {:split? true}}))

;;
;; Add no-wrap-after to :vector, Issue #252
;;

(expect
  "(defn example\n  [vvvvvveeeeeeeerrrrrrryyyyy looooooooooooooooooonnnnggggg paraaaaaaams &\n   body]\n  (+ 1 2 3))"
  (zprint-str
    "(defn example\n  [vvvvvveeeeeeeerrrrrrryyyyy looooooooooooooooooonnnnggggg paraaaaaaams & body]\n  (+ 1 2 3))\n"
    {:parse-string? true, :width 79}))


(expect
  "(defn example\n  [vvvvvveeeeeeeerrrrrrryyyyy looooooooooooooooooonnnnggggg paraaaaaaams\n   & body]\n  (+ 1 2 3))"
  (zprint-str
    "(defn example\n  [vvvvvveeeeeeeerrrrrrryyyyy looooooooooooooooooonnnnggggg paraaaaaaams & body]\n  (+ 1 2 3))\n"
    {:parse-string? true, :width 79, :vector {:no-wrap-after #{"&"}}}))

;; Does it work for :wrap in lists as well?

(expect
  "(stuff example vvvvvveeeeeeeerrrrrrryyyyy looooooooooooonnnnggggg paraaaaaaams &\n  body)"
  (zprint-str
    "(stuff example vvvvvveeeeeeeerrrrrrryyyyy looooooooooooonnnnggggg paraaaaaaams & body)\n"
    {:parse-string? true, :fn-map {"stuff" :wrap}}))

(expect
  "(stuff example vvvvvveeeeeeeerrrrrrryyyyy looooooooooooonnnnggggg paraaaaaaams\n  & body)"
  (zprint-str
    "(stuff example vvvvvveeeeeeeerrrrrrryyyyy looooooooooooonnnnggggg paraaaaaaams & body)\n"
    {:parse-string? true,
     :fn-map {"stuff" :wrap},
     :list {:no-wrap-after #{"&"}}}))

;;
;; Add :element-wrap-flow-* to guide
;;

(expect
  "(let [example (data.example/get-by-org-id-and-items\n                db-conn true [org-id] {:very-long-arg-here true})]\n  (some-body expressions)\n  (more-body expressions))"
  (zprint-str
    "     (let [example (data.example/get-by-org-id-and-items\n                      db-conn true [org-id] {:very-long-arg-here true})]\n       (some-body expressions)\n       (more-body expressions))\n"
    {:parse-string? true,
     :fn-map {"get-by-org-id-and-items"
                [:guided {:guide [:element :newline :element-wrap-flow-*]}]},
     :width 80}))


(expect
  "(let [example\n        (data.example/get-by-org-id-and-items\n          db-conn true [org-id] {:very-long-arg-here true})]\n  (some-body expressions)\n  (more-body expressions))"
  (zprint-str
    "     (let [example (data.example/get-by-org-id-and-items\n                      db-conn true [org-id] {:very-long-arg-here true})]\n       (some-body expressions)\n       (more-body expressions))\n"
    {:parse-string? true,
     :fn-map {"get-by-org-id-and-items"
                [:guided {:guide [:element :newline :element-wrap-flow-*]}]},
     :width 60}))

(expect
  "(let [example (data.example/get-by-org-id-and-items\n                db-conn\n                true\n                [org-id]\n                {:very-long-arg-here true})]\n  (some-body expressions)\n  (more-body expressions))"
  (zprint-str
    "     (let [example (data.example/get-by-org-id-and-items\n                      db-conn true [org-id] {:very-long-arg-here true})]\n       (some-body expressions)\n       (more-body expressions))\n"
    {:parse-string? true,
     :fn-map {"get-by-org-id-and-items"
                [:guided {:guide [:element :newline :element-wrap-flow-*]}]},
     :width 55}))


;;
;; The :fn-type return from an :option-fn can now be a string, and this
;; will be an alias.
;;

(expect
  "(defn regexa\n  \"This should format like when.\"\n  [this is a test]\n  (list this is a test)\n  (let [some stuff\n        more stuff\n        lots (when-tst stuff\n               this\n               needs\n               to\n               be\n               longer\n               than\n               one\n               lineeven\n               more\n               things)]\n    (stuff bother)))"
  (zprint-str
    "(defn regexa\n  \"This should format like when.\"\n  [this is a test]\n  (list this is a test)\n  (let [some stuff\n        more stuff\n        lots (when-tst stuff this needs to be longer than one lineeven more things)]\n    (stuff bother)))\n"
    {:parse-string? true,
     :fn-map {:default-not-none
                [:none
                 {:list {:option-fn (fn ([] "rulesfn")
                                        ([options len sexpr]
                                         (let [fn-str (str (first sexpr))]
                                           (cond (re-find #"^when" fn-str)
                                                   {:fn-style "when"}
                                                 :else nil))))}}]}}))
;;
;; Issue #268 -- when doing anon fns, turn (fn* ...) into #( ... ).
;;

(def i268a `#(list % :a))
(def i268b '#(+ %1 %2))
(def i268c '(map #(* 2 %) [1 2 3]))
(def i268d '#(println %1 %2 %&))
(def i268e
  '{:style-map {:m2 {:fn-map {"app"
                                [:force-nl-body
                                 {:list {:constant-pair-min 1,
                                         :constant-pair-fn #(or (keyword? %)
                                                                (string? %)
                                                                (number? %)
                                                                (= true %)
                                                                (= false %)
                                                                (vector? %))},
                                  :pair {:justify? true},
                                  :next-inner {:list {:constant-pair-min 4,
                                                      :constant-pair-fn nil},
                                               :pair {:justify? false}}}]}}}})

(def i268f
  '(is (= {1 :a, 2 :b} (reduce-kgv #(assoc %1 %3 %2) {} (seq {:a 1, :b 2})))))
(def i268g
  '([f g h & fs]
    (let [fs (list* f g h fs)]
      (fn ([] (reduce1 #(conj %1 (%2)) [] fs))
          ([x] (reduce1 #(conj %1 (%2 x)) [] fs))
          ([x y] (reduce1 #(conj %1 (%2 x y)) [] fs))
          ([x y z] (reduce1 #(conj %1 (%2 x y z)) [] fs))
          ([x y z & args] (reduce1 #(conj %1 (apply %2 x y z args)) [] fs))))))



(def i268i
'#(this % is a test this is only a test))

(def i268j
'#(or % is a test this is only a test))


#?(:clj (expect "#(clojure.core/list % :a)"
                (zprint-str i268a {:style :anon-fn}))
   :cljs (expect "#(cljs.core/list % :a)" (zprint-str i268a {:style :anon-fn})))
					

(expect
"#(+ %1 %2)"
(zprint-str i268b {:style :anon-fn}))

(expect
"(map #(* 2 %) [1 2 3])"
(zprint-str i268c {:style :anon-fn}))

(expect
"#(println %1 %2 %&)"
(zprint-str i268d {:style :anon-fn}))

(expect
"{:style-map\n   {:m2\n      {:fn-map {\"app\" [:force-nl-body\n                       {:list {:constant-pair-fn #(or (keyword? %)\n                                                      (string? %)\n                                                      (number? %)\n                                                      (= true %)\n                                                      (= false %)\n                                                      (vector? %)),\n                               :constant-pair-min 1},\n                        :next-inner\n                          {:list {:constant-pair-fn nil, :constant-pair-min 4},\n                           :pair {:justify? false}},\n                        :pair {:justify? true}}]}}}}"
(zprint-str i268e {:style :anon-fn}))

(expect
"(is (= {1 :a, 2 :b} (reduce-kgv #(assoc %1 %3 %2) {} (seq {:a 1, :b 2}))))"
(zprint-str i268f {:style :anon-fn}))

(expect
"([f g h & fs]\n (let [fs (list* f g h fs)]\n   (fn\n     ([] (reduce1 #(conj %1 (%2)) [] fs))\n     ([x] (reduce1 #(conj %1 (%2 x)) [] fs))\n     ([x y] (reduce1 #(conj %1 (%2 x y)) [] fs))\n     ([x y z] (reduce1 #(conj %1 (%2 x y z)) [] fs))\n     ([x y z & args] (reduce1 #(conj %1 (apply %2 x y z args)) [] fs)))))"
(zprint-str i268g {:style :anon-fn}))


(expect
"#(this %\n       is\n       a\n       test\n       this\n       is\n       only\n       a\n       test)"
(zprint-str i268i {:style :anon-fn :width 30}))

(expect
"#(or %\n     is\n     a\n     test\n     this\n     is\n     only\n     a\n     test)"
(zprint-str i268j {:style :anon-fn :width 30}))

(expect
"#(or % is a test this is only a test)"
(zprint-str "#(or % is a test this is only a test)" {:parse-string? true}))

(expect
"#(or % is a test this is only a test)"
(zprint-str "#(or % is a test this is only a test)" {:parse-string? true :style :anon-fn}))

(defn rev-vec
  "Test out structure modifications for vectors."
  ([] "rev-vec")
  ([options n sexpr]
   (when (= (:ztype options) :sexpr) {:new-zloc (reverse (:zloc options))})))

(expect "[:a :b :c]" (zprint-str [:a :b :c]))

(expect "[:c :b :a]"
        (zprint-str [:a :b :c]
                    {:vector {:option-fn zprint.zprint-test/rev-vec}}))

(expect "[:a :b :c]"
        (zprint-str "[:a :b :c]"
                    {:parse-string? true,
                     :vector {:option-fn zprint.zprint-test/rev-vec}}))

(defn remove-vec
  "Test out zipper (source) modifications for vectors."
  ([] "remove-vec")
  ([options n sexpr]
   (when (= (:ztype options) :zipper)
     (let [zloc (:zloc options)
           zloc (z/down zloc)
           #_(println "remove-vec" (z/string zloc))
           new-zloc (z/right zloc)
           new-zloc (z/remove new-zloc)
           new-zloc (z/up new-zloc)]
       {:new-zloc new-zloc}))))


(expect "[:a :c]"
        (zprint-str "[:a :b :c]"
                    {:parse-string? true,
                     :vector {:option-fn zprint.zprint-test/remove-vec}}))

;;
;; Issue #269 -- if you have a comment in a map, some of the advanced options
;; don't work.
;;

(expect
";;!zprint {:map {:sort? true :key-value-options {:tasks {:map {:sort? true :justify? true}}} :key-order [:min-bb-version :paths :deps :tasks] :respect-bl? false}}\n{:min-bb-version \"0.10.0\",\n :paths [\"src/bb\"],\n :deps {local/deps {:local/root \".\"}},\n :tasks {;; Check\n         fmt-check {:doc  \"Check code for formatting errors\",\n                    :task (shell \"make\" \"-C\" \"..\" \"clj-format-check\")},\n         fmt-fix   {:doc  \"Fix code formatting errors\",\n                    :task (shell \"make\" \"-C\" \"..\" \"clj-format-fix\")}},\n :pods {org.babashka/postgresql {:version \"0.1.1\"}}}\n"
(zprint-file-str
";;!zprint {:map {:sort? true :key-value-options {:tasks {:map {:sort? true :justify? true}}} :key-order [:min-bb-version :paths :deps :tasks] :respect-bl? false}}\n{:min-bb-version \"0.10.0\"\n :paths [\"src/bb\"]\n :deps {local/deps {:local/root \".\"}}\n :tasks {\n         ;; Check\n         fmt-check\n         {:doc     \"Check code for formatting errors\"\n          :task (shell \"make\" \"-C\" \"..\" \"clj-format-check\")}\n         fmt-fix\n         {:doc     \"Fix code formatting errors\"\n          :task (shell \"make\" \"-C\" \"..\" \"clj-format-fix\")}\n         }\n :pods {org.babashka/postgresql {:version \"0.1.1\"}}}\n"
"x"
{}))

(expect
";;!zprint {:map {:sort? true :key-color {:tasks :blue} :key-order [:min-bb-version :paths :deps :tasks] :respect-bl? false}}\n{:min-bb-version \"0.10.0\",\n :paths [\"src/bb\"],\n :deps {local/deps {:local/root \".\"}},\n :tasks {;; Check\n         fmt-check {:doc \"Check code for formatting errors\",\n                    :task (shell \"make\" \"-C\" \"..\" \"clj-format-check\")},\n         fmt-fix {:doc \"Fix code formatting errors\",\n                  :task (shell \"make\" \"-C\" \"..\" \"clj-format-fix\")}},\n :pods {org.babashka/postgresql {:version \"0.1.1\"}}}\n"
(zprint-file-str
";;!zprint {:map {:sort? true :key-color {:tasks :blue} :key-order [:min-bb-version :paths :deps :tasks] :respect-bl? false}}\n{:min-bb-version \"0.10.0\"\n :paths [\"src/bb\"]\n :deps {local/deps {:local/root \".\"}}\n :tasks {\n         ;; Check\n         fmt-check\n         {:doc     \"Check code for formatting errors\"\n          :task (shell \"make\" \"-C\" \"..\" \"clj-format-check\")}\n         fmt-fix\n         {:doc     \"Fix code formatting errors\"\n          :task (shell \"make\" \"-C\" \"..\" \"clj-format-fix\")}\n         }\n :pods {org.babashka/postgresql {:version \"0.1.1\"}}}\n"
"x"
{}))

(expect
";;!zprint {:map {:sort? true :key-value-color {:tasks {:keyword :blue}} :key-order [:min-bb-version :paths :deps :tasks] :respect-bl? false}}\n{:min-bb-version \"0.10.0\",\n :paths [\"src/bb\"],\n :deps {local/deps {:local/root \".\"}},\n :tasks {;; Check\n         fmt-check {:doc \"Check code for formatting errors\",\n                    :task (shell \"make\" \"-C\" \"..\" \"clj-format-check\")},\n         fmt-fix {:doc \"Fix code formatting errors\",\n                  :task (shell \"make\" \"-C\" \"..\" \"clj-format-fix\")}},\n :pods {org.babashka/postgresql {:version \"0.1.1\"}}}\n"
(zprint-file-str
";;!zprint {:map {:sort? true :key-value-color {:tasks {:keyword :blue}} :key-order [:min-bb-version :paths :deps :tasks] :respect-bl? false}}\n{:min-bb-version \"0.10.0\"\n :paths [\"src/bb\"]\n :deps {local/deps {:local/root \".\"}}\n :tasks {\n         ;; Check\n         fmt-check\n         {:doc     \"Check code for formatting errors\"\n          :task (shell \"make\" \"-C\" \"..\" \"clj-format-check\")}\n         fmt-fix\n         {:doc     \"Fix code formatting errors\"\n          :task (shell \"make\" \"-C\" \"..\" \"clj-format-fix\")}\n         }\n :pods {org.babashka/postgresql {:version \"0.1.1\"}}}\n"
"x"
{}))

;;
;; Issue #271 -- when formatted twice with :respect-nl and :justified, it 
;; changes the first time -- and then changes again with the second format.
;;

(def i271q
  ";; shadow-cljs configuration\n{:source-paths :abc\n\n :builds {:mobile\n          {:devtools {:autobuild [shadow/env [\"SHADOW_AUTOBUILD_ENABLED\" :default true]]}}}\n\t  \n :cache-blockers #{status-im.utils.js-resources}}\n")


(expect
  ";; shadow-cljs configuration\n{:source-paths :abc,\n\n :builds\n   {:mobile\n      {:devtools {:autobuild [shadow/env\n                              [\"SHADOW_AUTOBUILD_ENABLED\" :default true]]}}},\n\n :cache-blockers #{status-im.utils.js-resources}}\n"
  (zprint-file-str i271q "x" {:style [:respect-nl :justified]}))

;;
;; Shouldn't change this time
;;

(expect
  ";; shadow-cljs configuration\n{:source-paths :abc,\n\n :builds\n   {:mobile\n      {:devtools {:autobuild [shadow/env\n                              [\"SHADOW_AUTOBUILD_ENABLED\" :default true]]}}},\n\n :cache-blockers #{status-im.utils.js-resources}}\n"
  (zprint-file-str
    ";; shadow-cljs configuration\n{:source-paths :abc,\n\n :builds\n   {:mobile\n      {:devtools {:autobuild [shadow/env\n                              [\"SHADOW_AUTOBUILD_ENABLED\" :default true]]}}},\n\n :cache-blockers #{status-im.utils.js-resources}}\n"
    "x"
    {:style [:respect-nl :justified]}))

;;
;; More Issue #271 problems -- in fzprint-two-up, rightcnt was wrong for
;; things with 3 in (count pair), and a newline in there would cause different
;; output when using :respect-nl.
;;

(def i271z
" (:multiaccount {:chain mainnet_rpc\n })\n")

(expect
  "(:multiaccount {:chain\n                  mainnet_rpc\n               })\n"
  (zprint-file-str i271z "x" {:style [:respect-nl :justified], :width 30}))

(expect
  "(:multiaccount {:chain\n                  mainnet_rpc\n               })\n"
  (zprint-file-str
    "(:multiaccount {:chain\n                  mainnet_rpc\n               })\n"
    "x"
    {:style [:respect-nl :justified], :width 30}))

;;
;; Issue #271 -- problems with things changing for each format operation.
;;
;; This just checks the old tuning: :style :original-tuning
;;

(expect
"(defn print-balance\n  [xml] ;\n  (let [balance (parse xml)]\n    (letfn\n      [(transform [acc item]\n         (assoc acc\n           (separate-words (clean-key item)) (format-decimals (item balance))))]\n      (reduce transform {} (keys balance)))))"
 (zprint-str    "(defn print-balance [xml]                                 ;\n  (let [balance (parse xml)]\n    (letfn [(transform [acc item]\n              (assoc acc\n                  (separate-words (clean-key item))\n                     (format-decimals (item balance))))]\n      (reduce transform {} (keys balance)))))\n"    {:parse-string? true :style :original-tuning}))

;;
;; :inline-align-style and always moving inline comments to the left
;;

(expect
"(this is ; comment\n      a\n      test)"
 (zprint-str "(this is   ; comment\n a test)" {:parse-string? true, :comment {:inline-align-style :consecutive}}))

(expect
"(this is   ; comment\n      a\n      test)"
(zprint-str "(this is   ; comment\n a test)" {:parse-string? true, :comment {:inline-align-style :none}}))

;;
;; Issue #276 -- namespaced stuff doesn't alias right.  Missing code.
;;

(def i276 "(cat x y z)\n(my/cat x y z)\n(dog x y z)\n(my/dog x y z)\n")
(expect
"(cat x\n     y\n     z)\n(my/cat x\n        y\n        z)\n(dog x\n     y\n     z)\n(my/dog x\n        y\n        z)\n"
(zprint-file-str i276 "x" {:fn-map {"dog" "cat"}}))

;;
;; Issue #273 -- lots of changes to justification and pair handling
;;

;; :multi-lhs-hang?  - hang things after multi-line lhs things.
;;

;; :binding

 (expect
"(let [(aaaaaaaaa bbbbbbbbbb\n                 ccccccccc\n                 (ddddddddddd (eeeeeeeeeee (ffffffffffff))))\n        (stuff a b c)\n      (bother x y) (foo bar baz)])"
 (zprint-str "(let [(aaaaaaaaa bbbbbbbbbb ccccccccc (ddddddddddd (eeeeeeeeeee (ffffffffffff)))) (stuff a b c) (bother x y) (foo bar baz)])" {:parse-string? true :binding {:multi-lhs-hang? false}}))

(expect
"(let [(aaaaaaaaa bbbbbbbbbb\n                 ccccccccc\n                 (ddddddddddd (eeeeeeeeeee (ffffffffffff)))) (stuff a b c)\n      (bother x y) (foo bar baz)])"
(zprint-str "(let [(aaaaaaaaa bbbbbbbbbb ccccccccc (ddddddddddd (eeeeeeeeeee (ffffffffffff)))) (stuff a b c) (bother x y) (foo bar baz)])" {:parse-string? true :binding {:multi-lhs-hang? true}}))

;; :pair

  (expect
    "(cond (simple-check)\n        (short-function-call)\n      (and (much-more-complicated-check)\n           (an-even-longer-check-that-is-too-long))\n        (look-im-on-the-next-line)\n      :else\n        (another-short-call))"
    (zprint-str
      i235
      {:parse-string? true,
       :dbg? false,
       :pair
         {:flow-all-if-any? true, :justify? true, :justify {:max-variance 30}
	  :multi-lhs-hang? false},
       :width 80}))

;; :binding

(expect
"(defn pair1\n  [a b c d]\n  (cond (nil? a) a\n        (a-very-long-function?\n          b)     b\n        :else    c))"
(zprint-str pair2 {:parse-string? true, :dbg? false, :pair {:flow-all-if-any? true, :justify? true, :justify {:max-variance 20 :multi-lhs-overlap? true} :multi-lhs-hang? true}, :width 30}))

;; :map

(expect
"{:aaaa :bbbb,\n {:cccccccc :dddddddddd,\n  {:eeeeeeeeee :fffffffffffffffffffffff,\n   :ggggggggg :hhhhhhhhhhhhhh,\n   :iiiiiii :jjjjj} :kkkkkkkkkkkkkk} :lllllllll}"
(zprint-str "{:aaaa :bbbb {:cccccccc :dddddddddd {:eeeeeeeeee :fffffffffffffffffffffff :ggggggggg :hhhhhhhhhhhhhh :iiiiiii :jjjjj} :kkkkkkkkkkkkkk} :lllllllll}" {:parse-string? true :map {:multi-lhs-hang? true}}))
(expect
"{:aaaa :bbbb,\n {:cccccccc :dddddddddd,\n  {:eeeeeeeeee :fffffffffffffffffffffff,\n   :ggggggggg :hhhhhhhhhhhhhh,\n   :iiiiiii :jjjjj}\n    :kkkkkkkkkkkkkk}\n   :lllllllll}"
(zprint-str "{:aaaa :bbbb {:cccccccc :dddddddddd {:eeeeeeeeee :fffffffffffffffffffffff :ggggggggg :hhhhhhhhhhhhhh :iiiiiii :jjjjj} :kkkkkkkkkkkkkk} :lllllllll}" {:parse-string? true :map {:multi-lhs-hang? false}}))

;;
;; Start of tests for more complex justification, etc.
;;

;; Check to see that it doesn't narrow something that isn't getting justified


(def flowtst6
"
(cond (nil? a) (list d)
      (even? b) (list c d a b)
      (this (is (something that) can) be narrowed) (and the result)
      (whoknows? c) (this is d)
      (and (this needs to be pretty long to make it flow miser)
           (list? [lots of stuff])
           ((number? width)
             (nil? stuff)
             (list?
               (keyword?
                 (nil? (keyword?
                         (stuff bother
                                (foo bar (this is a test (type bother))))))))))
        (clojure.pprint/write sexpr
                              (pretty true)
                              (right-margin width)
                              (miser-width miser))
      (and (number? width) (nil? b))
        (clojure.pprint/write sexpr pretty true right-margin width)
      :else (clojure.pprint/write sexpr :pretty true))"
      )

 (expect

"(cond (nil? a)        (list d)\n      (even? b)       (list c d a b)\n      (this (is (something that) can)\n            be\n            narrowed) (and the result)\n      (whoknows? c)   (this is d)\n      (and (this needs to be pretty long to make it flow miser)\n           (list? [lots of stuff])\n           ((number? width)\n             (nil? stuff)\n             (list? (keyword?\n                      (nil? (keyword?\n                              (stuff bother\n                                     (foo bar\n                                          (this is a test (type bother))))))))))\n        (clojure.pprint/write sexpr\n                              (pretty true)\n                              (right-margin width)\n                              (miser-width miser))\n      (and (number? width) (nil? b))\n        (clojure.pprint/write sexpr pretty true right-margin width)\n      :else           (clojure.pprint/write sexpr :pretty true))"

 (zprint-str flowtst6 {:parse-string? true :style :justified :pair {:multi-lhs-hang? true :justify {:max-variance 20 :lhs-narrow 2.0}} #_#_:dbg-s #{:justify-result}}))

 ;;
 ;; See what happens with a bunch of lhs things that don't fit at all in the
 ;; narrow width
 ;;

(def flowtst11
"
(cond (nil? a) (list d)
      (even? b) (list c d a b)
      this-is-a-very-long-thing-designed-to-not-fit-in (and the result)
      this-is-another-very-long-thing-designed-to-not-fit (and the result)
      this-is-a-third-very-long-thing-designed-to-not-fit (and the result)
      this-is-a-fourth-very-long-thing-designed-to-not-fit (and the result)
      this-is-a-fifth-very-long-thing-designed-to-not-fit (and the result)
      (whoknows? c) (this is d)
      (and (number? width) (nil? b))
        (clojure.pprint/write sexpr pretty true right-margin width)
      :else (clojure.pprint/write sexpr :pretty true))
        ")

(expect
"(cond (nil? a) (list d)\n      (even? b) (list c d a b)\n      this-is-a-very-long-thing-designed-to-not-fit-in (and the result)\n      this-is-another-very-long-thing-designed-to-not-fit (and the result)\n      this-is-a-third-very-long-thing-designed-to-not-fit (and the result)\n      this-is-a-fourth-very-long-thing-designed-to-not-fit (and the result)\n      this-is-a-fifth-very-long-thing-designed-to-not-fit (and the result)\n      (whoknows? c) (this is d)\n      (and (number? width) (nil? b))\n        (clojure.pprint/write sexpr pretty true right-margin width)\n      :else (clojure.pprint/write sexpr :pretty true))"
(zprint-str flowtst11 {:parse-string? true :style :justified :pair {:multi-lhs-hang? true :justify {:max-variance 20 :lhs-narrow 2.0}}}))

;;
;; #282 -- :rod-no-ma-nl test
;;

(def
i282
"(rf/defn set-log-level\n  [{:keys [db]} log-level]\n  (let [log-level (or log-level config/log-level)]\n    {:db             (assoc-in db [:multiaccount :log-level] log-level)\n     :logs/set-level log-level}))\n")

(expect
"(rf/defn set-log-level [{:keys [db]} log-level]\n  (let [log-level (or log-level config/log-level)]\n    {:db (assoc-in db [:multiaccount :log-level] log-level),\n     :logs/set-level log-level}))"
(zprint-str i282 {:parse-string? true :style :rod-no-ma-nl}))

;;
;; Issue #273 -- multi-lhs-hang, etc. etc.
;;

(def
i273mc
"(defn pressable-hooks\n  [props]\n  (let [\n        long-press-ref (react/create-ref)\n        state (animated/use-value (:undetermined gesture-handler/states))\n        active (animated/eq state (:began gesture-handler/states))\n  {background-color    :bgColor\n         border-radius       :borderRadius\n         accessibility-label {:duration (animated/cond* active time-in time-out)\n\t                      :easing (:ease-in animated/easings)}\n         children            :children\n         :or                 {border-radius 0\n                              type          \"primary\"}}\n        (bean/bean props)\n        gesture-handler (animated/use-gesture state state)\n        animation (react/use-memo\n                   (fn []\n                     (animated/with-timing-transition active \n                                                      {:duration (animated/cond* active time-in time-out)\n                                                       :easing   (:ease-in animated/easings)}\n\t\t     )))\n        {:keys [background\n                foreground]}\n        (react/use-memo\n         (fn []\n           (type->animation {:type      (keyword type)\n                             :animation animation}))\n         [type])\n        handle-press (fn [] (when on-press (on-press)))\n        long-gesture-handler (react/callback\n                              (fn [^js evt]\n                                (let [gesture-state (-> evt\n                                                        .-nativeEvent\n                                                        .-state)]\n                                  (when (and on-press-start\n                                             (= gesture-state (:began gesture-handler/states)))\n                                    (on-press-start))\n                                  (when (and on-long-press\n                                             (= gesture-state (:active gesture-handler/states)))\n                                    (on-long-press)\n                                    (animated/set-value state (:undetermined gesture-handler/states)))))\n                              [on-long-press on-press-start])]\n    (animated/code!\n     (fn []\n       (when on-press\n         (animated/cond* (animated/eq state (:end gesture-handler/states))\n                         [(animated/set state (:undetermined gesture-handler/states))\n                          (animated/call* [] handle-press)])))\n     [on-press])\n    (reagent/as-element\n     [gesture-handler/long-press-gesture-handler\n      {:enabled                 (boolean (and on-long-press (not disabled)))\n       :on-handler-state-change long-gesture-handler\n       :min-duration-ms         long-press-duration\n       :max-dist                22\n       :ref                     long-press-ref}\n      [animated/view\n       {:accessible          true\n        :accessibility-label accessibility-label}\n       [gesture-handler/tap-gesture-handler\n        (merge gesture-handler\n               {:shouldCancelWhenOutside true\n                :wait-for                long-press-ref\n                :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                       (not disabled)))})\n        [animated/view\n         [animated/view\n          {:style (merge absolute-fill\n                         background\n                         {:background-color background-color\n                          :border-radius    border-radius\n                          :border-color     border-color\n                          :border-width     border-width})}]\n         (into [animated/view {:style foreground}]\n               (react/get-children children))]]]])\n    ))\n"
)



 (expect
"(defn pressable-hooks\n  [props]\n  (let [long-press-ref                              (react/create-ref)\n        state                                       (animated/use-value (:undetermined\n                                                                          gesture-handler/states))\n        active                                      (animated/eq state (:began gesture-handler/states))\n        {background-color :bgColor,\n         border-radius :borderRadius,\n         accessibility-label\n           {:duration (animated/cond* active\n                                      time-in\n                                      time-out),\n            :easing   (:ease-in animated/easings)},\n         children      :children,\n         :or           {border-radius 0,\n                        type \"primary\"}}            (bean/bean props)\n        gesture-handler                             (animated/use-gesture state state)\n        animation                                   (react/use-memo\n                                                      (fn []\n                                                        (animated/with-timing-transition\n                                                          active\n                                                          {:duration (animated/cond* active\n                                                                                     time-in\n                                                                                     time-out),\n                                                           :easing   (:ease-in animated/easings)})))\n        {:keys [background foreground]}             (react/use-memo (fn []\n                                                                      (type->animation\n                                                                        {:type      (keyword type),\n                                                                         :animation animation}))\n                                                                    [type])\n        handle-press                                (fn [] (when on-press (on-press)))\n        long-gesture-handler                        (react/callback\n                                                      (fn [^js evt]\n                                                        (let [gesture-state (-> evt\n                                                                                .-nativeEvent\n                                                                                .-state)]\n                                                          (when (and on-press-start\n                                                                     (= gesture-state\n                                                                        (:began gesture-handler/states)))\n                                                            (on-press-start))\n                                                          (when (and on-long-press\n                                                                     (= gesture-state\n                                                                        (:active\n                                                                          gesture-handler/states)))\n                                                            (on-long-press)\n                                                            (animated/set-value\n                                                              state\n                                                              (:undetermined gesture-handler/states)))))\n                                                      [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond* (animated/eq state (:end gesture-handler/states))\n                                        [(animated/set state (:undetermined gesture-handler/states))\n                                         (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled         (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms long-press-duration,\n        :max-dist        22,\n        :ref             long-press-ref}\n       [animated/view {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for long-press-ref,\n                 :enabled  (boolean (and (or on-press on-long-press on-press-start) (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}] (react/get-children children))]]]])))"
 (zprint-str i273mc {:parse-string? true :binding {:justify? true :justify {:max-variance 1000 :lhs-narrow 2.0 :multi-lhs-overlap? false} :justify-tuning {:binding {:tuning {:hang-flow 5}}} :multi-lhs-hang? true} :width 105 :map {:justify? true :justify {:max-variance 20 :lhs-narrow 2.0 :multi-lhs-overlap? false}  :multi-lhs-hang? true :flow? false}}))


(expect
"(defn pressable-hooks\n  [props]\n  (let [long-press-ref                   (react/create-ref)\n        state                            (animated/use-value (:undetermined gesture-handler/states))\n        active                           (animated/eq state (:began gesture-handler/states))\n        {background-color :bgColor,\n         border-radius :borderRadius,\n         accessibility-label\n           {:duration (animated/cond* active\n                                      time-in\n                                      time-out),\n            :easing   (:ease-in animated/easings)},\n         children      :children,\n         :or           {border-radius 0,\n                        type \"primary\"}} (bean/bean props)\n        gesture-handler                  (animated/use-gesture state state)\n        animation                        (react/use-memo\n                                           (fn []\n                                             (animated/with-timing-transition\n                                               active\n                                               {:duration (animated/cond* active time-in time-out),\n                                                :easing   (:ease-in animated/easings)})))\n        {:keys [background foreground]}  (react/use-memo (fn []\n                                                           (type->animation {:type      (keyword type),\n                                                                             :animation animation}))\n                                                         [type])\n        handle-press                     (fn [] (when on-press (on-press)))\n        long-gesture-handler             (react/callback\n                                           (fn [^js evt]\n                                             (let [gesture-state (-> evt\n                                                                     .-nativeEvent\n                                                                     .-state)]\n                                               (when (and on-press-start\n                                                          (= gesture-state\n                                                             (:began gesture-handler/states)))\n                                                 (on-press-start))\n                                               (when (and on-long-press\n                                                          (= gesture-state\n                                                             (:active gesture-handler/states)))\n                                                 (on-long-press)\n                                                 (animated/set-value state\n                                                                     (:undetermined\n                                                                       gesture-handler/states)))))\n                                           [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond* (animated/eq state (:end gesture-handler/states))\n                                        [(animated/set state (:undetermined gesture-handler/states))\n                                         (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled         (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms long-press-duration,\n        :max-dist        22,\n        :ref             long-press-ref}\n       [animated/view {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for long-press-ref,\n                 :enabled  (boolean (and (or on-press on-long-press on-press-start) (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}] (react/get-children children))]]]])))"

(zprint-str i273mc {:parse-string? true :binding {:justify? true :justify {:max-variance 1000 :lhs-narrow 2.0 :multi-lhs-overlap? true} :justify-tuning {:binding {:tuning {:hang-flow 5}}} :multi-lhs-hang? true} :width 105 :map {:justify? true :justify {:max-variance 20 :lhs-narrow 2.0 :multi-lhs-overlap? false}  :multi-lhs-hang? true :flow? false}}))

(expect

"(defn pressable-hooks\n  [props]\n  (let [long-press-ref                                  (react/create-ref)\n        state                                           (animated/use-value (:undetermined\n                                                                              gesture-handler/states))\n        active                                          (animated/eq state\n                                                                     (:began gesture-handler/states))\n        {background-color    :bgColor,\n         border-radius       :borderRadius,\n         accessibility-label {:duration (animated/cond*\n                                          active\n                                          time-in\n                                          time-out),\n                              :easing\n                                (:ease-in\n                                  animated/easings)},\n         children            :children,\n         :or                 {border-radius 0,\n                              type          \"primary\"}} (bean/bean props)\n        gesture-handler                                 (animated/use-gesture state state)\n        animation                                       (react/use-memo\n                                                          (fn []\n                                                            (animated/with-timing-transition\n                                                              active\n                                                              {:duration (animated/cond* active\n                                                                                         time-in\n                                                                                         time-out),\n                                                               :easing   (:ease-in animated/easings)})))\n        {:keys [background foreground]}                 (react/use-memo (fn []\n                                                                          (type->animation\n                                                                            {:type      (keyword type),\n                                                                             :animation animation}))\n                                                                        [type])\n        handle-press                                    (fn [] (when on-press (on-press)))\n        long-gesture-handler                            (react/callback\n                                                          (fn [^js evt]\n                                                            (let [gesture-state (-> evt\n                                                                                    .-nativeEvent\n                                                                                    .-state)]\n                                                              (when (and on-press-start\n                                                                         (= gesture-state\n                                                                            (:began\n                                                                              gesture-handler/states)))\n                                                                (on-press-start))\n                                                              (when (and on-long-press\n                                                                         (= gesture-state\n                                                                            (:active\n                                                                              gesture-handler/states)))\n                                                                (on-long-press)\n                                                                (animated/set-value\n                                                                  state\n                                                                  (:undetermined\n                                                                    gesture-handler/states)))))\n                                                          [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond* (animated/eq state (:end gesture-handler/states))\n                                        [(animated/set state (:undetermined gesture-handler/states))\n                                         (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}] (react/get-children children))]]]])))"


(zprint-str i273mc {:parse-string? true :binding {:justify? true :justify {:max-variance 1000 :lhs-narrow 2.0 :multi-lhs-overlap? true} :justify-tuning {:binding {:tuning {:hang-flow 5}}} :multi-lhs-hang? true} :width 105 :map {:justify? true :justify {:max-variance 1000 :lhs-narrow 2.0 :multi-lhs-overlap? false}  :multi-lhs-hang? true :flow? false}}))

 (expect
"(defn pressable-hooks\n  [props]\n  (let [long-press-ref                                  (react/create-ref)\n        state                                           (animated/use-value (:undetermined\n                                                                              gesture-handler/states))\n        active                                          (animated/eq state\n                                                                     (:began gesture-handler/states))\n        {background-color    :bgColor,\n         border-radius       :borderRadius,\n         accessibility-label {:duration (animated/cond*\n                                          active\n                                          time-in\n                                          time-out),\n                              :easing\n                                (:ease-in\n                                  animated/easings)},\n         children            :children,\n         :or                 {border-radius 0,\n                              type          \"primary\"}} (bean/bean props)\n        gesture-handler                                 (animated/use-gesture state state)\n        animation                                       (react/use-memo\n                                                          (fn []\n                                                            (animated/with-timing-transition\n                                                              active\n                                                              {:duration (animated/cond* active\n                                                                                         time-in\n                                                                                         time-out),\n                                                               :easing   (:ease-in animated/easings)})))\n        {:keys [background foreground]}                 (react/use-memo (fn []\n                                                                          (type->animation\n                                                                            {:type      (keyword type),\n                                                                             :animation animation}))\n                                                                        [type])\n        handle-press                                    (fn [] (when on-press (on-press)))\n        long-gesture-handler                            (react/callback\n                                                          (fn [^js evt]\n                                                            (let [gesture-state (-> evt\n                                                                                    .-nativeEvent\n                                                                                    .-state)]\n                                                              (when (and on-press-start\n                                                                         (= gesture-state\n                                                                            (:began\n                                                                              gesture-handler/states)))\n                                                                (on-press-start))\n                                                              (when (and on-long-press\n                                                                         (= gesture-state\n                                                                            (:active\n                                                                              gesture-handler/states)))\n                                                                (on-long-press)\n                                                                (animated/set-value\n                                                                  state\n                                                                  (:undetermined\n                                                                    gesture-handler/states)))))\n                                                          [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond* (animated/eq state (:end gesture-handler/states))\n                                        [(animated/set state (:undetermined gesture-handler/states))\n                                         (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}] (react/get-children children))]]]])))"
 (zprint-str i273mc {:parse-string? true :binding {:justify? true :justify {:max-variance 1000} :justify-tuning {:binding {:tuning {:hang-flow 5}}} :multi-lhs-hang? true} :width 105 :map {:justify? true :justify {:max-variance 1000}  :multi-lhs-hang? true :flow? false}}))

(expect
"(defn pressable-hooks\n  [props]\n  (let [long-press-ref (react/create-ref)\n        state (animated/use-value (:undetermined gesture-handler/states))\n        active (animated/eq state (:began gesture-handler/states))\n        {background-color    :bgColor,\n         border-radius       :borderRadius,\n         accessibility-label {:duration (animated/cond* active time-in time-out),\n                              :easing   (:ease-in animated/easings)},\n         children            :children,\n         :or                 {border-radius 0, type \"primary\"}} (bean/bean props)\n        gesture-handler (animated/use-gesture state state)\n        animation (react/use-memo (fn []\n                                    (animated/with-timing-transition\n                                      active\n                                      {:duration (animated/cond* active time-in time-out),\n                                       :easing   (:ease-in animated/easings)})))\n        {:keys [background foreground]}\n          (react/use-memo (fn [] (type->animation {:type (keyword type), :animation animation})) [type])\n        handle-press (fn [] (when on-press (on-press)))\n        long-gesture-handler\n          (react/callback\n            (fn [^js evt]\n              (let [gesture-state (-> evt\n                                      .-nativeEvent\n                                      .-state)]\n                (when (and on-press-start (= gesture-state (:began gesture-handler/states)))\n                  (on-press-start))\n                (when (and on-long-press (= gesture-state (:active gesture-handler/states)))\n                  (on-long-press)\n                  (animated/set-value state (:undetermined gesture-handler/states)))))\n            [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond* (animated/eq state (:end gesture-handler/states))\n                                        [(animated/set state (:undetermined gesture-handler/states))\n                                         (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}] (react/get-children children))]]]])))"
(zprint-str i273mc {:parse-string? true :binding {:justify? true :justify {:max-variance 1000} :justify-tuning {:binding {:tuning {:hang-flow 4}}} :multi-lhs-hang? true} :width 105 :map {:justify? true :justify {:max-variance 1000}  :multi-lhs-hang? true :flow? false}}))


(expect
"(defn pressable-hooks\n  [props]\n  (let [long-press-ref                  (react/create-ref)\n        state                           (animated/use-value\n                                          (:undetermined\n                                            gesture-handler/states))\n        active                          (animated/eq state\n                                                     (:began\n                                                       gesture-handler/states))\n        {background-color :bgColor,\n         border-radius :borderRadius,\n         accessibility-label\n           {:duration (animated/cond*\n                        active\n                        time-in\n                        time-out),\n            :easing   (:ease-in\n                        animated/easings)},\n         children :children,\n         :or {border-radius 0,\n              type          \"primary\"}} (bean/bean props)\n        gesture-handler                 (animated/use-gesture state state)\n        animation                       (react/use-memo\n                                          (fn []\n                                            (animated/with-timing-transition\n                                              active\n                                              {:duration (animated/cond*\n                                                           active\n                                                           time-in\n                                                           time-out),\n                                               :easing   (:ease-in\n                                                           animated/easings)})))\n        {:keys [background foreground]} (react/use-memo\n                                          (fn []\n                                            (type->animation\n                                              {:type      (keyword type),\n                                               :animation animation}))\n                                          [type])\n        handle-press                    (fn [] (when on-press (on-press)))\n        long-gesture-handler            (react/callback\n                                          (fn [^js evt]\n                                            (let [gesture-state (->\n                                                                  evt\n                                                                  .-nativeEvent\n                                                                  .-state)]\n                                              (when\n                                                (and\n                                                  on-press-start\n                                                  (= gesture-state\n                                                     (:began\n                                                       gesture-handler/states)))\n                                                (on-press-start))\n                                              (when\n                                                (and\n                                                  on-long-press\n                                                  (= gesture-state\n                                                     (:active\n                                                       gesture-handler/states)))\n                                                (on-long-press)\n                                                (animated/set-value\n                                                  state\n                                                  (:undetermined\n                                                    gesture-handler/states)))))\n                                          [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond*\n                          (animated/eq state (:end gesture-handler/states))\n                          [(animated/set state\n                                         (:undetermined gesture-handler/states))\n                           (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view\n        {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press\n                                                            on-long-press\n                                                            on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}]\n                (react/get-children children))]]]])))"
 (zprint-str i273mc {:parse-string? true :binding {:justify? true :justify {:max-variance 1000 :multi-lhs-overlap? true} :justify-tuning {:binding {:tuning {:hang-flow 4}}} :multi-lhs-hang? true} :width 80 :map {:justify? true :justify {:max-variance 1000}  :multi-lhs-hang? true :flow? false}}))


(expect
"(defn pressable-hooks\n  [props]\n  (let [long-press-ref (react/create-ref)\n        state (animated/use-value (:undetermined gesture-handler/states))\n        active (animated/eq state (:began gesture-handler/states))\n        {background-color    :bgColor,\n         border-radius       :borderRadius,\n         accessibility-label {:duration (animated/cond* active\n                                                        time-in\n                                                        time-out),\n                              :easing   (:ease-in animated/easings)},\n         children            :children,\n         :or                 {border-radius 0, type \"primary\"}} (bean/bean\n                                                                  props)\n        gesture-handler (animated/use-gesture state state)\n        animation (react/use-memo\n                    (fn []\n                      (animated/with-timing-transition\n                        active\n                        {:duration (animated/cond* active time-in time-out),\n                         :easing   (:ease-in animated/easings)})))\n        {:keys [background foreground]}\n          (react/use-memo (fn []\n                            (type->animation {:type      (keyword type),\n                                              :animation animation}))\n                          [type])\n        handle-press (fn [] (when on-press (on-press)))\n        long-gesture-handler\n          (react/callback\n            (fn [^js evt]\n              (let [gesture-state (-> evt\n                                      .-nativeEvent\n                                      .-state)]\n                (when (and on-press-start\n                           (= gesture-state (:began gesture-handler/states)))\n                  (on-press-start))\n                (when (and on-long-press\n                           (= gesture-state (:active gesture-handler/states)))\n                  (on-long-press)\n                  (animated/set-value state\n                                      (:undetermined gesture-handler/states)))))\n            [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond*\n                          (animated/eq state (:end gesture-handler/states))\n                          [(animated/set state\n                                         (:undetermined gesture-handler/states))\n                           (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view\n        {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press\n                                                            on-long-press\n                                                            on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}]\n                (react/get-children children))]]]])))"
(zprint-str i273mc {:parse-string? true :binding {:justify? true :justify {:max-variance 1000 :multi-lhs-overlap? false} :justify-tuning {:binding {:tuning {:hang-flow 4}}} :multi-lhs-hang? true} :width 80 :map {:justify? true :justify {:max-variance 1000}  :multi-lhs-hang? true :flow? false}}))

(def
i273zb
"(defn fzprint-two-up\n  [caller\n   {:keys [one-line? dbg? dbg-indent in-hang? do-in-hang? map-depth],\n    {:keys [hang? dbg-local? dbg-cnt? indent indent-arg flow? key-color\n            key-depth-color key-value-color key-value-options justify\n\t    #_lhs-narrow multi-lhs-hang?]}\n      caller,\n    :as options} ind commas? justify-width justify-options narrow-width \n    rightmost-pair? force-flow? [lloc rloc xloc :as pair]]\n  (let [flow? (or flow? force-flow?)\n        lhang? (or one-line? hang?)\n        indent (or indent indent-arg)\n        non-justify-options options\n        options justify-options\n        lcolor\n          (if (and key-color (> (count pair) 1) (zsexpr? lloc)) \n\t    (key-color (get-sexpr options lloc)) \n\t    local-color)\n        lcolor\n        (cond (and map-depth (= caller :map) (= map 2)) :green\n                (and map-depth (= caller :map) (= map-depth 1)) :blue\n                (and map-depth (= caller :map) (= map 3)) :yellow\n                (and map-depth (= caller :map) (= map-depth 4)) :red\n                :else nil)]\n\n\t(stuff bother)))\n"
)

 (expect
"(defn fzprint-two-up\n  [caller\n   {:keys [one-line? dbg? dbg-indent in-hang? do-in-hang? map-depth],\n    {:keys [hang? dbg-local? dbg-cnt? indent indent-arg flow? key-color\n            key-depth-color key-value-color key-value-options justify\n            #_lhs-narrow multi-lhs-hang?]} caller,\n    :as   options} ind commas? justify-width justify-options narrow-width\n   rightmost-pair? force-flow? [lloc rloc xloc :as pair]]\n  (let [flow?   (or flow? force-flow?)\n        lhang?  (or one-line? hang?)\n        indent  (or indent indent-arg)\n        non-justify-options options\n        options justify-options\n        lcolor  (if (and key-color (> (count pair) 1) (zsexpr? lloc))\n                  (key-color (get-sexpr options lloc))\n                  local-color)\n        lcolor  (cond (and map-depth (= caller :map) (= map 2))       :green\n                      (and map-depth (= caller :map) (= map-depth 1)) :blue\n                      (and map-depth (= caller :map) (= map 3))       :yellow\n                      (and map-depth (= caller :map) (= map-depth 4)) :red\n                      :else                                           nil)]\n    (stuff bother)))"
 (zprint-str i273zb {:parse-string? true :style [:justified :multi-lhs-hang]}))

 (def
 i273x
"       (cond (and (zero? line-length) (not previous-comment?)) out\n              previous-comment? (conj out line-length 0)\n              :else (conj out line-length))\n\n")

 (expect
"(cond (and (zero? line-length)\n           (not previous-comment?)) out\n      previous-comment?             (conj out line-length 0)\n      :else                         (conj out line-length))"
 (zprint-str i273x {:parse-string? true :style [:justified-original :multi-lhs-hang] :width 70}))

(expect
"(cond (and (zero? line-length) (not previous-comment?)) out\n      previous-comment? (conj out line-length 0)\n      :else (conj out line-length))"
(zprint-str i273x {:parse-string? true :style [:justified-original ] :width 70}))


(def
i273xa
"       (cond \n       (and (zero? line-length) (not previous-comment?)) out\n              previous-comment? (conj out line-length 0)\n       (and (zero? line-length) (not previous-comment?)) out\n              previous-comment? (conj out line-length 0)\n       (and (zero? line-length) (not previous-comment?)) out\n              previous-comment? (conj out line-length 0)\n       (and (zero? line-length) (not previous-comment?)) out\n              previous-comment? (conj out line-length 0)\n              :else (conj out line-length))\n\n")


 (expect
"(cond (and (zero? line-length)\n           (not previous-comment?)) out\n      previous-comment?             (conj out line-length 0)\n      (and (zero? line-length)\n           (not previous-comment?)) out\n      previous-comment?             (conj out line-length 0)\n      (and (zero? line-length)\n           (not previous-comment?)) out\n      previous-comment?             (conj out line-length 0)\n      (and (zero? line-length)\n           (not previous-comment?)) out\n      previous-comment?             (conj out line-length 0)\n      :else                         (conj out line-length))"
 (zprint-str i273xa {:parse-string? true :style [:justified-original :multi-lhs-hang] :width 70}))

(def
bnd1
"(let [[this is a test this is only a test] stuff\n        bother foo\n\tbar baz]\n    (result-that\n    is-pretty-long\n    so-it does not fit))")

(expect
"(let [[this is a test this is only a test] stuff\n      bother                               foo\n      bar                                  baz]\n  (result-that is-pretty-long so-it does not fit))"
(zprint-str bnd1 {:parse-string? true  :binding {:justify? true :justify {:max-variance 1000 :lhs-narrow 10} :multi-lhs-hang? true}}))


(expect
"(defn defprotocolguide\n  \"Handle defprotocol with options.\"\n  ([] \"defprotocolguide\")\n  ([options len sexpr]\n   (when (= (first sexpr) 'defprotocol)\n     (let [third                           (nth sexpr 2 nil)\n           fourth                          (nth sexpr 3 nil)\n           fifth                           (nth sexpr 4 nil)\n           [docstring option option-value] (cond (and (string?\n                                                        third)\n                                                      (keyword?\n                                                        fourth)) [third fourth\n                                                                  fifth]\n                                                 (string? third) [third nil nil]\n                                                 (keyword?\n                                                   third)        [nil third\n                                                                  fourth]\n                                                 :else           [nil nil nil])\n           guide                           (cond-> [:element :element-best\n                                                    :newline]\n                                             docstring (conj :element :newline)\n                                             option\n                                               (conj :element :element :newline)\n                                             :else (conj\n                                                     :element-newline-best-*))]\n       {:guide guide, :next-inner {:list {:option-fn nil}}}))))"
(zprint-str dpg {:parse-string? true, :pair {:justify {:max-variance 1000 :lhs-narrow 2} :multi-lhs-hang? true}, :binding {:justify {:max-variance 1000 :lhs-narrow 1} :multi-lhs-hang? true}, :style :justified}))


 (def
 i273m
"(defn pressable-hooks\n  [props]\n  (let [{background-color    :bgColor\n         border-radius       :borderRadius\n         border-color        :borderColor\n         border-width        :borderWidth\n         type                :type\n         disabled            :disabled\n         on-press            :onPress\n         on-long-press       :onLongPress\n         on-press-start      :onPressStart\n         accessibility-label :accessibilityLabel\n         children            :children\n         :or                 {border-radius 0\n                              type          \"primary\"}}\n        (bean/bean props)\n        long-press-ref (react/create-ref)\n        state (animated/use-value (:undetermined gesture-handler/states))\n        active (animated/eq state (:began gesture-handler/states))\n        gesture-handler (animated/use-gesture state state)\n        animation (react/use-memo\n                   (fn []\n                     (animated/with-timing-transition active \n                                                      {:duration (animated/cond* active time-in time-out)\n                                                       :easing   (:ease-in animated/easings)}\n\t\t     )))\n        {:keys [background\n                foreground]}\n        (react/use-memo\n         (fn []\n           (type->animation {:type      (keyword type)\n                             :animation animation}))\n         [type])\n        handle-press (fn [] (when on-press (on-press)))\n        long-gesture-handler (react/callback\n                              (fn [^js evt]\n                                (let [gesture-state (-> evt\n                                                        .-nativeEvent\n                                                        .-state)]\n                                  (when (and on-press-start\n                                             (= gesture-state (:began gesture-handler/states)))\n                                    (on-press-start))\n                                  (when (and on-long-press\n                                             (= gesture-state (:active gesture-handler/states)))\n                                    (on-long-press)\n                                    (animated/set-value state (:undetermined gesture-handler/states)))))\n                              [on-long-press on-press-start])]\n    (animated/code!\n     (fn []\n       (when on-press\n         (animated/cond* (animated/eq state (:end gesture-handler/states))\n                         [(animated/set state (:undetermined gesture-handler/states))\n                          (animated/call* [] handle-press)])))\n     [on-press])\n    (reagent/as-element\n     [gesture-handler/long-press-gesture-handler\n      {:enabled                 (boolean (and on-long-press (not disabled)))\n       :on-handler-state-change long-gesture-handler\n       :min-duration-ms         long-press-duration\n       :max-dist                22\n       :ref                     long-press-ref}\n      [animated/view\n       {:accessible          true\n        :accessibility-label accessibility-label}\n       [gesture-handler/tap-gesture-handler\n        (merge gesture-handler\n               {:shouldCancelWhenOutside true\n                :wait-for                long-press-ref\n                :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                       (not disabled)))})\n        [animated/view\n         [animated/view\n          {:style (merge absolute-fill\n                         background\n                         {:background-color background-color\n                          :border-radius    border-radius\n                          :border-color     border-color\n                          :border-width     border-width})}]\n         (into [animated/view {:style foreground}]\n               (react/get-children children))]]]])\n    ))\n")

(expect
"(defn pressable-hooks\n  [props]\n  (let [{background-color :bgColor,\n         border-radius :borderRadius,\n         border-color :borderColor,\n         border-width :borderWidth,\n         type :type,\n         disabled :disabled,\n         on-press :onPress,\n         on-long-press :onLongPress,\n         on-press-start :onPressStart,\n         accessibility-label\n           :accessibilityLabel,\n         children :children,\n         :or {border-radius 0,\n              type          \"primary\"}} (bean/bean props)\n        long-press-ref                  (react/create-ref)\n        state                           (animated/use-value\n                                          (:undetermined\n                                            gesture-handler/states))\n        active                          (animated/eq state\n                                                     (:began\n                                                       gesture-handler/states))\n        gesture-handler                 (animated/use-gesture state state)\n        animation                       (react/use-memo\n                                          (fn []\n                                            (animated/with-timing-transition\n                                              active\n                                              {:duration (animated/cond*\n                                                           active\n                                                           time-in\n                                                           time-out),\n                                               :easing   (:ease-in\n                                                           animated/easings)})))\n        {:keys [background foreground]} (react/use-memo\n                                          (fn []\n                                            (type->animation\n                                              {:type      (keyword type),\n                                               :animation animation}))\n                                          [type])\n        handle-press                    (fn [] (when on-press (on-press)))\n        long-gesture-handler            (react/callback\n                                          (fn [^js evt]\n                                            (let [gesture-state (->\n                                                                  evt\n                                                                  .-nativeEvent\n                                                                  .-state)]\n                                              (when\n                                                (and\n                                                  on-press-start\n                                                  (= gesture-state\n                                                     (:began\n                                                       gesture-handler/states)))\n                                                (on-press-start))\n                                              (when\n                                                (and\n                                                  on-long-press\n                                                  (= gesture-state\n                                                     (:active\n                                                       gesture-handler/states)))\n                                                (on-long-press)\n                                                (animated/set-value\n                                                  state\n                                                  (:undetermined\n                                                    gesture-handler/states)))))\n                                          [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond*\n                          (animated/eq state (:end gesture-handler/states))\n                          [(animated/set state\n                                         (:undetermined gesture-handler/states))\n                           (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view\n        {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press\n                                                            on-long-press\n                                                            on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}]\n                (react/get-children children))]]]])))"
(zprint-str i273m {:parse-string? true :style [:justified-original :multi-lhs-hang]}))


 (def
 i273g
"(defn pressable-hooks\n  [props]\n  (let [{background-color    :bgColor\n         border-radius       :borderRadius\n         border-color        :borderColor\n         border-width        :borderWidth\n         type                :type\n         disabled            :disabled\n         on-press            :onPress\n         on-long-press       :onLongPress\n         on-press-start      :onPressStart\n         accessibility-label :accessibilityLabel\n         children            :children\n         :or                 {border-radius 0\n                              type          \"primary\"}}\n        (bean/bean props)\n        long-press-ref (react/create-ref)\n        state (animated/use-value (:undetermined gesture-handler/states))\n        active (animated/eq state (:began gesture-handler/states))\n        gesture-handler (animated/use-gesture {:state state})\n        animation (react/use-memo\n                   (fn []\n                     (animated/with-timing-transition active\n                                                      {:duration (animated/cond* active time-in time-out)\n                                                       :easing   (:ease-in animated/easings)}\n\t\t\t\t\t\t       ))\n                   [])\n        {:keys [background\n                foreground]}\n        (react/use-memo\n         (fn []\n           (type->animation {:type      (keyword type)\n                             :animation animation}))\n         [type])\n        handle-press (fn [] (when on-press (on-press)))\n        long-gesture-handler (react/callback\n                              (fn [^js evt]\n                                (let [gesture-state (-> evt\n                                                        .-nativeEvent\n                                                        .-state)]\n                                  (when (and on-press-start\n                                             (= gesture-state (:began gesture-handler/states)))\n                                    (on-press-start))\n                                  (when (and on-long-press\n                                             (= gesture-state (:active gesture-handler/states)))\n                                    (on-long-press)\n                                    (animated/set-value state (:undetermined gesture-handler/states)))))\n                              [on-long-press on-press-start])]\n    (animated/code!\n     (fn []\n       (when on-press\n         (animated/cond* (animated/eq state (:end gesture-handler/states))\n                         [(animated/set state (:undetermined gesture-handler/states))\n                          (animated/call* [] handle-press)])))\n     [on-press])\n    (reagent/as-element\n     [gesture-handler/long-press-gesture-handler\n      {:enabled                 (boolean (and on-long-press (not disabled)))\n       :on-handler-state-change long-gesture-handler\n       :min-duration-ms         long-press-duration\n       :max-dist                22\n       :ref                     long-press-ref}\n      [animated/view\n       {:accessible          true\n        :accessibility-label accessibility-label}\n       [gesture-handler/tap-gesture-handler\n        (merge gesture-handler\n               {:shouldCancelWhenOutside true\n                :wait-for                long-press-ref\n                :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                       (not disabled)))})\n        [animated/view\n         [animated/view\n          {:style (merge absolute-fill\n                         background\n                         {:background-color background-color\n                          :border-radius    border-radius\n                          :border-color     border-color\n                          :border-width     border-width})}]\n         (into [animated/view {:style foreground}]\n               (react/get-children children))]]]])\n\t       ))\n")
(expect
"(defn pressable-hooks\n  [props]\n  (let [{background-color :bgColor,\n         border-radius :borderRadius,\n         border-color :borderColor,\n         border-width :borderWidth,\n         type :type,\n         disabled :disabled,\n         on-press :onPress,\n         on-long-press :onLongPress,\n         on-press-start :onPressStart,\n         accessibility-label\n           :accessibilityLabel,\n         children :children,\n         :or {border-radius 0,\n              type          \"primary\"}} (bean/bean props)\n        long-press-ref                  (react/create-ref)\n        state                           (animated/use-value\n                                          (:undetermined\n                                            gesture-handler/states))\n        active                          (animated/eq state\n                                                     (:began\n                                                       gesture-handler/states))\n        gesture-handler                 (animated/use-gesture {:state state})\n        animation                       (react/use-memo\n                                          (fn []\n                                            (animated/with-timing-transition\n                                              active\n                                              {:duration (animated/cond*\n                                                           active\n                                                           time-in\n                                                           time-out),\n                                               :easing   (:ease-in\n                                                           animated/easings)}))\n                                          [])\n        {:keys [background foreground]} (react/use-memo\n                                          (fn []\n                                            (type->animation\n                                              {:type      (keyword type),\n                                               :animation animation}))\n                                          [type])\n        handle-press                    (fn [] (when on-press (on-press)))\n        long-gesture-handler            (react/callback\n                                          (fn [^js evt]\n                                            (let [gesture-state (->\n                                                                  evt\n                                                                  .-nativeEvent\n                                                                  .-state)]\n                                              (when\n                                                (and\n                                                  on-press-start\n                                                  (= gesture-state\n                                                     (:began\n                                                       gesture-handler/states)))\n                                                (on-press-start))\n                                              (when\n                                                (and\n                                                  on-long-press\n                                                  (= gesture-state\n                                                     (:active\n                                                       gesture-handler/states)))\n                                                (on-long-press)\n                                                (animated/set-value\n                                                  state\n                                                  (:undetermined\n                                                    gesture-handler/states)))))\n                                          [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond*\n                          (animated/eq state (:end gesture-handler/states))\n                          [(animated/set state\n                                         (:undetermined gesture-handler/states))\n                           (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view\n        {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press\n                                                            on-long-press\n                                                            on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}]\n                (react/get-children children))]]]])))"
(zprint-str i273g {:parse-string? true :style [:justified-original :multi-lhs-hang]}))

(def
alc
"(this is a test)\n(s/def ::cache-path (s/nilable string?))   ; debugging only\n(this is still a test)")
(expect
"(this is a test)\n(s/def ::cache-path (s/nilable string?))   ; debugging only\n(this is still a test)"
(zprint-file-str alc "x" {}))

 (def
 i273g
"(defn pressable-hooks\n  [props]\n  (let [{background-color    :bgColor\n         border-radius       :borderRadius\n         border-color        :borderColor\n         border-width        :borderWidth\n         type                :type\n         disabled            :disabled\n         on-press            :onPress\n         on-long-press       :onLongPress\n         on-press-start      :onPressStart\n         accessibility-label :accessibilityLabel\n         children            :children\n         :or                 {border-radius 0\n                              type          \"primary\"}}\n        (bean/bean props)\n        long-press-ref (react/create-ref)\n        state (animated/use-value (:undetermined gesture-handler/states))\n        active (animated/eq state (:began gesture-handler/states))\n        gesture-handler (animated/use-gesture {:state state})\n        animation (react/use-memo\n                   (fn []\n                     (animated/with-timing-transition active\n                                                      {:duration (animated/cond* active time-in time-out)\n                                                       :easing   (:ease-in animated/easings)}\n\t\t\t\t\t\t       ))\n                   [])\n        {:keys [background\n                foreground]}\n        (react/use-memo\n         (fn []\n           (type->animation {:type      (keyword type)\n                             :animation animation}))\n         [type])\n        handle-press (fn [] (when on-press (on-press)))\n        long-gesture-handler (react/callback\n                              (fn [^js evt]\n                                (let [gesture-state (-> evt\n                                                        .-nativeEvent\n                                                        .-state)]\n                                  (when (and on-press-start\n                                             (= gesture-state (:began gesture-handler/states)))\n                                    (on-press-start))\n                                  (when (and on-long-press\n                                             (= gesture-state (:active gesture-handler/states)))\n                                    (on-long-press)\n                                    (animated/set-value state (:undetermined gesture-handler/states)))))\n                              [on-long-press on-press-start])]\n    (animated/code!\n     (fn []\n       (when on-press\n         (animated/cond* (animated/eq state (:end gesture-handler/states))\n                         [(animated/set state (:undetermined gesture-handler/states))\n                          (animated/call* [] handle-press)])))\n     [on-press])\n    (reagent/as-element\n     [gesture-handler/long-press-gesture-handler\n      {:enabled                 (boolean (and on-long-press (not disabled)))\n       :on-handler-state-change long-gesture-handler\n       :min-duration-ms         long-press-duration\n       :max-dist                22\n       :ref                     long-press-ref}\n      [animated/view\n       {:accessible          true\n        :accessibility-label accessibility-label}\n       [gesture-handler/tap-gesture-handler\n        (merge gesture-handler\n               {:shouldCancelWhenOutside true\n                :wait-for                long-press-ref\n                :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                       (not disabled)))})\n        [animated/view\n         [animated/view\n          {:style (merge absolute-fill\n                         background\n                         {:background-color background-color\n                          :border-radius    border-radius\n                          :border-color     border-color\n                          :border-width     border-width})}]\n         (into [animated/view {:style foreground}]\n               (react/get-children children))]]]])\n\t       ))\n")

(expect
"(defn pressable-hooks\n  [props]\n  (let [{background-color    :bgColor,\n         border-radius       :borderRadius,\n         border-color        :borderColor,\n         border-width        :borderWidth,\n         type                :type,\n         disabled            :disabled,\n         on-press            :onPress,\n         on-long-press       :onLongPress,\n         on-press-start      :onPressStart,\n         accessibility-label :accessibilityLabel,\n         children            :children,\n         :or                 {border-radius 0,\n                              type          \"primary\"}} (bean/bean props)\n        long-press-ref                                  (react/create-ref)\n        state                                           (animated/use-value (:undetermined\n                                                                              gesture-handler/states))\n        active                                          (animated/eq state\n                                                                     (:began gesture-handler/states))\n        gesture-handler                                 (animated/use-gesture {:state state})\n        animation                                       (react/use-memo\n                                                          (fn []\n                                                            (animated/with-timing-transition\n                                                              active\n                                                              {:duration (animated/cond* active\n                                                                                         time-in\n                                                                                         time-out),\n                                                               :easing   (:ease-in animated/easings)}))\n                                                          [])\n        {:keys [background foreground]}                 (react/use-memo (fn []\n                                                                          (type->animation\n                                                                            {:type      (keyword type),\n                                                                             :animation animation}))\n                                                                        [type])\n        handle-press                                    (fn [] (when on-press (on-press)))\n        long-gesture-handler                            (react/callback\n                                                          (fn [^js evt]\n                                                            (let [gesture-state (-> evt\n                                                                                    .-nativeEvent\n                                                                                    .-state)]\n                                                              (when (and on-press-start\n                                                                         (= gesture-state\n                                                                            (:began\n                                                                              gesture-handler/states)))\n                                                                (on-press-start))\n                                                              (when (and on-long-press\n                                                                         (= gesture-state\n                                                                            (:active\n                                                                              gesture-handler/states)))\n                                                                (on-long-press)\n                                                                (animated/set-value\n                                                                  state\n                                                                  (:undetermined\n                                                                    gesture-handler/states)))))\n                                                          [on-long-press on-press-start])]\n    (animated/code! (fn []\n                      (when on-press\n                        (animated/cond* (animated/eq state (:end gesture-handler/states))\n                                        [(animated/set state (:undetermined gesture-handler/states))\n                                         (animated/call* [] handle-press)])))\n                    [on-press])\n    (reagent/as-element\n      [gesture-handler/long-press-gesture-handler\n       {:enabled                 (boolean (and on-long-press (not disabled))),\n        :on-handler-state-change long-gesture-handler,\n        :min-duration-ms         long-press-duration,\n        :max-dist                22,\n        :ref                     long-press-ref}\n       [animated/view {:accessible true, :accessibility-label accessibility-label}\n        [gesture-handler/tap-gesture-handler\n         (merge gesture-handler\n                {:shouldCancelWhenOutside true,\n                 :wait-for                long-press-ref,\n                 :enabled                 (boolean (and (or on-press on-long-press on-press-start)\n                                                        (not disabled)))})\n         [animated/view\n          [animated/view\n           {:style (merge absolute-fill\n                          background\n                          {:background-color background-color,\n                           :border-radius    border-radius,\n                           :border-color     border-color,\n                           :border-width     border-width})}]\n          (into [animated/view {:style foreground}] (react/get-children children))]]]])))"
(zprint-str i273g {:parse-string? true :style [:justified-original :multi-lhs-hang] :width 105 :binding {:justify-tuning {:binding {:tuning {:hang-flow 5}}}}}))


;;
;; Issue #261 and others -- regex recognition for fn-map
;;

 (def
 are12a
"\n(deftest t-parsing-auto-resolve-keywords\n  (are-mine [?s ?sexpr-default ?sexpr-custom]\n       (let [n (p/parse-string ?s)]\n         (is (= :token (node/tag n)))\n         (is (= ?s (node/string n)))\n         (is (= ?sexpr-default (node/sexpr n)))\n         (is (= ?sexpr-custom (node/sexpr n {:auto-resolve #(if (= :current %)\n                                                              'my.current.ns\n                                                              (get {'xyz 'my.aliased.ns} % 'alias-unresolved))}))))\n    \"::key\"        :?_current-ns_?/key    :my.current.ns/key\n    \"::xyz/key\"    :??_xyz_??/key         :my.aliased.ns/key))\n    ")

(expect
"(deftest t-parsing-auto-resolve-keywords\n  (are-mine [?s ?sexpr-default ?sexpr-custom]\n    (let [n (p/parse-string ?s)]\n      (is (= :token (node/tag n)))\n      (is (= ?s (node/string n)))\n      (is (= ?sexpr-default (node/sexpr n)))\n      (is (= ?sexpr-custom\n             (node/sexpr n\n                         {:auto-resolve #(if (= :current %)\n                                           'my.current.ns\n                                           (get {'xyz 'my.aliased.ns}\n                                                %\n                                                'alias-unresolved))}))))\n    \"::key\"     :?_current-ns_?/key :my.current.ns/key\n    \"::xyz/key\" :??_xyz_??/key      :my.aliased.ns/key))"
(zprint-str are12a {:parse-string? true :style :regex-example}))


(def sooox
"(defn ooox
  \"A variety of sorting and ordering options for the output
  of partition-all-2-nc.  It can sort, which is the default,
  but if the caller has a key-order vector, it will extract
  any keys in that vector and place them first (in order) before
  sorting the other keys.\"
  [caller
   {:keys [dbg?],
    {:keys [zsexpr zdotdotdot compare-ordered-keys compare-keys a b]} :zf,
    {:keys [sort? key-order key-value]} caller,
    :as options} out]
  (cond (or sort? key-order)
          (sort #((partial compare-ordered-keys (or key-value {}) (zdotdotdot))
                   (zsexpr (first a))
                   (zsexpr (first b)))
                out)
        sort? (sort #(compare-keys (zsexpr (first a)) (zsexpr (first b))) out)
        :else (throw (Exception. \"Unknown options
       to order-out\"))))")

(expect
"(defn ooox\n  \"A variety of sorting and ordering options for the output\n  of partition-all-2-nc.  It can sort, which is the default,\n  but if the caller has a key-order vector, it will extract\n  any keys in that vector and place them first (in order) before\n  sorting the other keys.\"\n  [caller\n   {:keys [dbg?],\n    {:keys [zsexpr zdotdotdot compare-ordered-keys compare-keys a b]}\n      :zf,\n    {:keys [sort? key-order key-value]} caller,\n    :as options} out]\n  (cond (or sort? key-order) (sort #((partial compare-ordered-keys\n                                       (or key-value {})\n                                       (zdotdotdot))\n                                       (zsexpr (first a))\n                                       (zsexpr (first b)))\n                                   out)\n        sort? (sort #(compare-keys (zsexpr (first a))\n                                   (zsexpr (first b)))\n                    out)\n        :else (throw (Exception.\n                       \"Unknown options\n       to order-out\"))))"
(zprint-str sooox {:parse-string? true :fn-map {:default-not-none [:none {:list {:option-fn (partial regexfn [#"^partial$" {:fn-style :arg1}])}}]} :width 70}))


;;
;; We don't allow {:fn-style [:arg1 {:list ...}]}
;;

#?(:clj
     (expect
       "java.lang.Exception: Options resulting from :list :option-fn named 'regexfn' called with an sexpr of length 4 had these errors: The value of the key-sequence [:fn-style] -> [:arg1 {:list {:nl-count 3}}] was not a clojure.core/string?"
       (try (zprint-str
              sooox
              {:parse-string? true,
               :fn-map {:default-not-none
                          [:none
                           {:list {:option-fn
                                     (partial
                                       regexfn
                                       [#"^partial$"
                                        {:fn-style
                                           [:arg1 {:list {:nl-count 3}}]}])}}]},
               :width 70})
            (catch Exception e (str e))))
   :cljs
     (expect
       "Error: Options resulting from :list :option-fn named 'regexfn' called with an sexpr of length 4 had these errors: The value of the key-sequence [:fn-style] -> [:arg1 {:list {:nl-count 3}}] was not a cljs.core/string?"
       (try (zprint-str
              sooox
              {:parse-string? true,
               :fn-map {:default-not-none
                          [:none
                           {:list {:option-fn
                                     (partial
                                       regexfn
                                       [#"^partial$"
                                        {:fn-style
                                           [:arg1 {:list {:nl-count 3}}]}])}}]},
               :width 70})
            (catch :default e (str e)))))


; Instead of [:fn-type {options-map}], just do 
; {:fn-type :keyword rest-of-options-map}

(expect
"(defn ooox\n  \"A variety of sorting and ordering options for the output\n  of partition-all-2-nc.  It can sort, which is the default,\n  but if the caller has a key-order vector, it will extract\n  any keys in that vector and place them first (in order) before\n  sorting the other keys.\"\n  [caller\n   {:keys [dbg?],\n    {:keys [zsexpr zdotdotdot compare-ordered-keys compare-keys a b]}\n      :zf,\n    {:keys [sort? key-order key-value]} caller,\n    :as options} out]\n  (cond (or sort? key-order) (sort #((partial compare-ordered-keys\n                                       (or key-value {})\n\n\n                                       (zdotdotdot))\n                                       (zsexpr (first a))\n                                       (zsexpr (first b)))\n                                   out)\n        sort? (sort #(compare-keys (zsexpr (first a))\n                                   (zsexpr (first b)))\n                    out)\n        :else (throw (Exception.\n                       \"Unknown options\n       to order-out\"))))"
(zprint-str sooox {:parse-string? true :fn-map {:default-not-none [:none {:list {:option-fn (partial regexfn [#"^partial$" {:fn-style :arg1 :list {:nl-count 3}}])}}]} :width 70}))

;;
;; Issue #261 -- longer than 20 chars, do something different
;;

(def
i261
"     (let [example (data.example/get-by-org-id-and-items\n                      db-conn true [org-id] {:very-long-arg-here true})]\n       (some-body expressions)\n       (more-body expressions))\n")


(expect
"(let [example (data.example/get-by-org-id-and-items\n                db-conn true [org-id] {:very-long-arg-here true})]\n  (some-body expressions)\n  (more-body expressions))"
(zprint-str i261 {:parse-string? true :style :rules-example}))

;;
;; Test loop detection
;;

(defn loopfn
  "Force a loop in option-fns."
  ([] "loopfn")
  ([options len sexpr]
   (let [dnn (:default-not-none (:fn-map options))
         option-fn (:option-fn (:list (second dnn)))]
     {:list {:option-fn option-fn}})))

(expect
  "Circular :option-fn lookup!"
  (try (zprint-str
         i261
         {:parse-string? true,
          :fn-map {:default-not-none
                     [:none
                      {:list {:option-fn
                                (partial
                                  rulesfn
                                  [#(> (count %) 20)
                                   {:list {:option-fn
                                             zprint.zprint-test/loopfn}}])}}]}})
       (catch #?(:clj Exception
                 :cljs :default)
         e
         (re-find #"Circular :option-fn lookup!" (str e)))))


 (def
 prj2
"(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[expectations \"2.2.0-rc1\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  ;[rum \"0.10.8\"];\n                                  [better-cond \"1.0.1\"]\n\t\t\t\t  [zpst \"0.1.6\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\" \"-Xms2048m\" \"-Xmx2048m\" \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[org.clojure/clojure \"1.10.0-beta3\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [org.clojure/clojure \"1.8.0\"]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]\n     #_[cprop \"0.1.6\"]])\n")


(expect
"(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[better-cond \"1.0.1\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  ;[rum \"0.10.8\"];\n                                  [expectations \"2.2.0-rc1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [zpst \"0.1.6\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\"\n             \"-Xms2048m\"\n             \"-Xmx2048m\"\n             \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[cprop \"0.1.6\"]\n     #_[org.clojure/clojure \"1.10.0-beta3\"]\n     [org.clojure/clojure \"1.8.0\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]])"
(zprint-str prj2 {:parse-string? true :style :sort-dependencies :comment {:smart-wrap {:border 0}}}))

;;
;; Put blank lines in vectors Issue #283
;;

(def
i283i
"{:foo/bar\n [{:foo/bar :activities\n   :foo/bar []}\n  {:foo/bar :programs\n   :foo/bar [{:foo/bar :foo/baz\n              :name \"bardel\"\n              :activities [{:foo/bar :foo/bar.cosmic\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.objectivism\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.nonperforming\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.splenodynia\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.Cashmere\n                            :start-offset-days 5}\n\n                           {:foo/bar :foo/bar.undelectably\n                            :start-offset-days 5}\n\n                           {:foo/bar :foo/bar.theosopheme\n                            :start-offset-days 20}]}\n             {:foo/bar :foo/baz\n              :name \"irresonant\"\n              :activities [{:foo/bar :foo/bar.phytogenetical\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.oriental\n                            :start-offset-days 5}]}]}]}\n")

(expect
"{:foo/bar\n [{:foo/bar :activities\n   :foo/bar []}\n\n  {:foo/bar :programs\n   :foo/bar [{:activities [{:foo/bar :foo/bar.cosmic\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.objectivism\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.nonperforming\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.splenodynia\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.Cashmere\n                            :start-offset-days 5}\n\n                           {:foo/bar :foo/bar.undelectably\n                            :start-offset-days 5}\n\n                           {:foo/bar :foo/bar.theosopheme\n                            :start-offset-days 20}]\n              :foo/bar :foo/baz\n              :name \"bardel\"}\n\n             {:activities [{:foo/bar :foo/bar.phytogenetical\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.oriental\n                            :start-offset-days 5}]\n              :foo/bar :foo/baz\n              :name \"irresonant\"}]}]}"
(zprint-str i283i {:parse-string? true :vector {:option-fn (fn ([] "vector-lines") ([options len sexpr] (when (not (empty? sexpr)) {:guide (into [] (->> (repeat (count sexpr) :element) (interpose [:newline :newline]) flatten))})))} :style :community :map {:comma? false :force-nl? true} :width 120 :output {:real-le? true}}))

;;
;; Issue #274 -- fix indentation in namespaced maps
;;

(def
i274
"#:foo{:aaaaaaaaaaaaaaaaaaaaaaaa 1,\n :bbbbbbbbbbbbbbbbbbbbbbbb {:aaaaaaaaaaaaaaaaaaaaaaaa 1,\n                                 :bbbbbbbbbbbbbbbbbbbbbbbb 2,\n                                 :cccccccccccccccccccccccc 3},\n :cccccccccccccccccccccccc 3}\n")

(expect
"#:foo{:aaaaaaaaaaaaaaaaaaaaaaaa 1,\n      :bbbbbbbbbbbbbbbbbbbbbbbb {:aaaaaaaaaaaaaaaaaaaaaaaa 1,\n                                 :bbbbbbbbbbbbbbbbbbbbbbbb 2,\n                                 :cccccccccccccccccccccccc 3},\n      :cccccccccccccccccccccccc 3}"
(zprint-str i274 {:parse-string? true}))

;;
;; Issue #275 -- namespaced functions are lost when doing noformat
;;               that is, ;!zprint {:format :off} or {:format :skip}
;;

(def
i275
";!zprint {:format :off}\n#::foo{:bar :baz}\n")

(expect
";!zprint {:format :off}\n#::foo{:bar :baz}\n"
(zprint-file-str i275 "x" {}))

;;
;; Issue #273 -- ensure that we will still justify stuff even when
;; there is a comment in a set of pairs.
;;

 (def
 i273clve
"(defn cleave-end\n  \"Take a seq, and if it is contains a single symbol, simply return\n  it in another seq.  If it contains something else, remove any non\n  collections off of the end and return them in their own double seqs,\n  as well as return the remainder (the beginning) as a double seq.\"\n  [coll]\n  (if (or (zsymbol? (first coll)) (zreader-cond-w-symbol? (first coll)))\n    ;(symbol? (first coll))\n    (list coll)\n    (let [rev-seq (reverse coll)\n          [split-non-coll _]\n            ;(split-with (comp not zcoll?) rev-seq)\n            (split-with #(not (or (zcoll? %) (zreader-cond-w-coll? %))) rev-seq)\n          #_(def sncce split-non-coll)\n          split-non-coll (map list (reverse split-non-coll))\n          remainder (take (- (count coll) (count split-non-coll)) coll)]\n      (if (empty? remainder)\n        split-non-coll\n        (concat (list remainder) split-non-coll)))))\n")

(expect
"(defn cleave-end\n  \"Take a seq, and if it is contains a single symbol, simply return\n  it in another seq.  If it contains something else, remove any non\n  collections off of the end and return them in their own double seqs,\n  as well as return the remainder (the beginning) as a double seq.\"\n  [coll]\n  (if (or (zsymbol? (first coll)) (zreader-cond-w-symbol? (first coll)))\n    ;(symbol? (first coll))\n    (list coll)\n    (let [rev-seq            (reverse coll)\n          [split-non-coll _]\n            ;(split-with (comp not zcoll?) rev-seq)\n            (split-with #(not (or (zcoll? %) (zreader-cond-w-coll? %))) rev-seq)\n          #_(def sncce split-non-coll)\n          split-non-coll     (map list (reverse split-non-coll))\n          remainder          (take (- (count coll) (count split-non-coll))\n                                   coll)]\n      (if (empty? remainder)\n        split-non-coll\n        (concat (list remainder) split-non-coll)))))"

(zprint-str i273clve {:parse-string? true :style :justified}))

;;
;; Issue #283 -- blank lines only after elements in a vector that take more
;; than one line.
;;

 (def
 i283j
"{:foo/bar\n [{:foo/bar :activities\n   :foo/bar []}\n  {:foo/bar :programs\n   :foo/bar [{:foo/bar :foo/baz\n              :name \"bardel\"\n              :activities [{:foo/bar :foo/bar.cosmic\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.objectivism\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.nonperforming}\n\n                           {:foo/bar :foo/bar.splenodynia\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.Cashmere}\n\n                           {:foo/bar :foo/bar.undelectably\n                            :start-offset-days 5}\n\n                           {:foo/bar :foo/bar.theosopheme\n                            :start-offset-days 20}]}\n             {:foo/bar :foo/baz\n              :name \"irresonant\"\n              :activities [{:foo/bar :foo/bar.phytogenetical\n                            :start-offset-days 0}\n\n                           {:foo/bar :foo/bar.oriental\n                            :start-offset-days 5}]}]}]}\n")
(expect

"{:foo/bar\n   [{:foo/bar :activities,\n     :foo/bar []}\n\n    {:foo/bar :programs,\n     :foo/bar [{:activities [{:foo/bar :foo/bar.cosmic,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.objectivism,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.nonperforming}\n                             {:foo/bar :foo/bar.splenodynia,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.Cashmere}\n                             {:foo/bar :foo/bar.undelectably,\n                              :start-offset-days 5}\n\n                             {:foo/bar :foo/bar.theosopheme,\n                              :start-offset-days 20}],\n                :foo/bar :foo/baz,\n                :name \"bardel\"}\n\n               {:activities [{:foo/bar :foo/bar.phytogenetical,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.oriental,\n                              :start-offset-days 5}],\n                :foo/bar :foo/baz,\n                :name \"irresonant\"}]}]}"

(zprint-str i283j {:parse-string? true :vector {:fn-format :list} :vector-fn {:nl-count 2 :indent 1 :hang? false :nl-separator? true} :map {:force-nl? true}}))



 (def
 i283m
"{:foo/bar\n   [{:foo/bar :activities, :foo/bar []}\n    {:foo/bar :programs,\n     :foo/bar\n       [{:activities [{:foo/bar :foo/bar.cosmic, :start-offset-days 0}\n                      {:foo/bar :foo/bar.objectivism, :start-offset-days 0}\n                      {:foo/bar :foo/bar.nonperforming}\n                      {:foo/bar :foo/bar.splenodynia, :start-offset-days 0}\n                      {:foo/bar :foo/bar.Cashmere}\n                      {:foo/bar :foo/bar.undelectably, :start-offset-days 5}\n                      {:foo/bar :foo/bar.theosopheme, :start-offset-days 20}],\n         :foo/bar :foo/baz,\n         :name \"bardel\"}\n        {:activities [{:foo/bar :foo/bar.phytogenetical, :start-offset-days 0}\n                      {:foo/bar :foo/bar.oriental, :start-offset-days 5}],\n         :foo/bar :foo/baz,\n         :name \"irresonant\"}]} {:foo/bar :stuff} {:bother [], :foo/bar :stuff}]}\n")


(expect
"{:foo/bar\n   [{:foo/bar :activities,\n     :foo/bar []}\n\n    {:foo/bar :programs,\n     :foo/bar [{:activities [{:foo/bar :foo/bar.cosmic,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.objectivism,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.nonperforming}\n                             {:foo/bar :foo/bar.splenodynia,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.Cashmere}\n                             {:foo/bar :foo/bar.undelectably,\n                              :start-offset-days 5}\n\n                             {:foo/bar :foo/bar.theosopheme,\n                              :start-offset-days 20}],\n                :foo/bar :foo/baz,\n                :name \"bardel\"}\n\n               {:activities [{:foo/bar :foo/bar.phytogenetical,\n                              :start-offset-days 0}\n\n                             {:foo/bar :foo/bar.oriental,\n                              :start-offset-days 5}],\n                :foo/bar :foo/baz,\n                :name \"irresonant\"}]}\n\n    {:foo/bar :stuff}\n    {:bother [],\n     :foo/bar :stuff}]}"
(zprint-str i283m {:parse-string? true :vector {:fn-format :list} :vector-fn {:nl-count 2 :indent 1 :hang? false :nl-separator? true} :map {:force-nl? true}}))



(def
i283l
"[{:a :b}\n {:c :d :e :f}\n {:t [{:u :v}\n      {:w :x}\n      {:y :z :aa :bb}\n      {:cc :dd}\n      {:ee :ff :gg :hh}]}\n {:h :i}\n {:j :k :l :m}\n {:n :o :p :q}\n {:r :s}\n ]\n")

(expect
"[{:a :b}\n {:c :d,\n  :e :f}\n\n {:t [{:u :v}\n      {:w :x}\n      {:aa :bb,\n       :y :z}\n\n      {:cc :dd}\n      {:ee :ff,\n       :gg :hh}]}\n\n {:h :i}\n {:j :k,\n  :l :m}\n\n {:n :o,\n  :p :q}\n\n {:r :s}]"

(zprint-str i283l {:parse-string? true :vector {:fn-format :list} :vector-fn {:nl-count 2 :indent 1 :hang? false :nl-separator? true} :map {:force-nl? true}}))


(def
 i283k
"[{:a :b}\n {:c :d :e :f}\n {:h :i}\n {:j :k :l :m}\n {:n :o :p :q}\n {:r :s}\n ]\n")

(expect
"[{:a :b}\n {:c :d,\n  :e :f}\n\n {:h :i}\n {:j :k,\n  :l :m}\n\n {:n :o,\n  :p :q}\n\n {:r :s}]"
(zprint-str i283k {:parse-string? true :vector {:fn-format :list} :vector-fn {:nl-count 2 :indent 1 :hang? false :nl-separator? true} :map {:force-nl? true}}))

(expect
"[{:a :b}\n\n {:c :d,\n  :e :f}\n\n {:t [{:u :v}\n\n      {:w :x}\n\n      {:aa :bb,\n       :y :z}\n\n      {:cc :dd}\n\n      {:ee :ff,\n       :gg :hh}]}\n\n {:h :i}\n\n {:j :k,\n  :l :m}\n\n {:n :o,\n  :p :q}\n\n {:r :s}]"

(zprint-str i283l {:parse-string? true :vector {:fn-format :list} :vector-fn {:indent 1 :hang? false :nl-separator? false :nl-count 2} :map {:force-nl? true}}))

(expect
"[{:a :b}\n {:c :d,\n  :e :f}\n {:t [{:u :v}\n      {:w :x}\n      {:aa :bb,\n       :y :z}\n      {:cc :dd}\n      {:ee :ff,\n       :gg :hh}]}\n {:h :i}\n {:j :k,\n  :l :m}\n {:n :o,\n  :p :q}\n {:r :s}]"
(zprint-str i283l {:parse-string? true :vector {:fn-format :list} :vector-fn {:indent 1 :hang? false :nl-separator? false} :map {:force-nl? true}}))

(expect
"[{:a :b}\n\n\n {:c :d,\n  :e :f}\n\n\n\n {:t [{:u :v}\n\n\n      {:w :x}\n\n\n\n      {:aa :bb,\n       :y :z}\n\n\n\n\n      {:cc :dd}\n\n\n\n\n      {:ee :ff,\n       :gg :hh}]}\n\n\n\n\n {:h :i}\n\n\n\n\n {:j :k,\n  :l :m}\n\n\n\n\n {:n :o,\n  :p :q}\n\n\n\n\n {:r :s}]"
(zprint-str i283l {:parse-string? true :vector {:fn-format :list} :vector-fn {:indent 1 :hang? false :nl-separator? false :nl-count [3 4 5]} :map {:force-nl? true}}))

(expect
"[{:a :b}\n {:c :d,\n  :e :f}\n\n {:t [{:u :v}\n      {:w :x}\n      {:aa :bb,\n       :y :z}\n\n      {:cc :dd}\n      {:ee :ff,\n       :gg :hh}]}\n\n {:h :i}\n {:j :k,\n  :l :m}\n\n {:n :o,\n  :p :q}\n\n {:r :s}]"
(zprint-str i283l {:parse-string? true :vector {:fn-format :list} :vector-fn {:indent 1 :hang? false :nl-separator? true :nl-count [3 4 5]} :map {:force-nl? true}}))

;;
;; Issue #294 -- :hiccup style getting triggered by destructuring map in
;; argument vector.
;;

(def
i294b
"(defn my-fn \n  ([a] (my-fn a {}))\n  ([a {:keys [b c]} a longer argument vector]\n   (do-stuff a b c)))\n")


(expect
"(defn my-fn\n  ([a] (my-fn a {}))\n  ([a {:keys [b c]} a longer argument vector] (do-stuff a b c)))"
(zprint-str i294b {:parse-string? true :style :hiccup}))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
  ;; End of defexpect
  ;;
  ;; All tests MUST come before this!!!
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
)

