(ns ^:no-doc zprint.optionfn
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require #?@(:clj [[zprint.macros :refer
                       [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
            [zprint.util :refer [column-alignment cumulative-alignment]]))

;;
;; Contains functions which can be called with {:option-fn <fn>} to produce
;; a new options map.  Option-fns which produce a "guide" are in guide.cljc.
;; The optionfns here are called the same way, but just produce a basic
;; option map.
;;

(defn rodfn
  "Given a structure which starts with defn, do what rodguide does without
  a guide."
  ([] "rodfn")
  ; If you call an option-fn  with partial because it has its own options map,
  ; the "no-argument" arity must include the options map!
  ([rod-options] "rodfn")
  ; Since we have released this before, we will also allow it to be called
  ; without rod-options (since this is a replacement for rodguide).
  ([options len sexpr] (rodfn {} options len sexpr))
  ([rod-options options len sexpr]
   (let [multi-arity-nl? (get rod-options :multi-arity-nl? true)
         docstring? (string? (nth sexpr 2))
         rest (nthnext sexpr (if docstring? 3 2))
         multi-arity? (not (vector? (first rest)))
         rest (if multi-arity? rest (next rest))
         nl-count (cond (and multi-arity? multi-arity-nl? docstring?) [1 2]
	                (and multi-arity? multi-arity-nl?) [2]
			:else [1])
         option-map {:list {:nl-count nl-count},
                     :next-inner {:list {:option-fn nil}}
		     :next-inner-restore [[:list :nl-count]]}
         option-map (if (or docstring? multi-arity?)
                      (assoc option-map :fn-style :arg1-body)
                      (assoc option-map :fn-style :arg2))]
     (if multi-arity?
       (assoc option-map
         :next-inner {:list {:option-fn nil},
                      :fn-map {:vector :force-nl},
                      :next-inner-restore [[:fn-map :vector]]})
       option-map))))

; Use this to use the above:
;
; (czprint rod4
;    {:parse-string? true
;     :fn-map {"defn" [:none {:list {:option-fn rodfn}}]}})

