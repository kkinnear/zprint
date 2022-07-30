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
  "Given a structure which starts with defn or fn format it using the
  'rules of defn'."
  ([] "rodfn")
  ; If you call an option-fn with partial because it has its own options map,
  ; the "no-argument" arity must include the options map!
  ([rod-options] "rodfn")
  ; Since we have released this before, we will also allow it to be called
  ; without rod-options (since this is a drop-in replacement for rodguide).
  ([options len sexpr] (rodfn {} options len sexpr))
  ([rod-options options len sexpr]
   (let [multi-arity-nl? (get rod-options :multi-arity-nl? true)
         fn-name? (symbol? (second sexpr))
         docstring? (string? (nth sexpr (if fn-name? 2 1)))
         multi-arity? (not (vector? (nth sexpr
                                         (cond (and fn-name? docstring?) 3
                                               (or fn-name? docstring?) 2
                                               :else 1))))
         nl-count (cond (and multi-arity? multi-arity-nl? docstring?) [1 2]
                        (and multi-arity? multi-arity-nl?) [2]
                        :else [1])
         option-map {:list {:nl-count nl-count},
                     :next-inner {:list {:option-fn nil}},
                     :next-inner-restore [[:list :nl-count]]}
         option-map (cond (and fn-name? docstring?)
                            (assoc option-map :fn-style :arg1-force-nl-body)
                          (and fn-name? (not multi-arity?))
                            (assoc option-map :fn-style :arg2-force-nl-body)
                          fn-name? (assoc option-map
                                     :fn-style :arg1-force-nl-body)
                          (not multi-arity?) (assoc option-map
                                               :fn-style :arg1-force-nl-body)
                          :else (assoc option-map :fn-style :flow-body))]
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

(defn meta-base-fn
  "Look at a list, and if it has metadata, then based on the kind of
  metadata, try to do it differently than the normal metadata output."
  ([] "meta-base-fn")
  ([opts n exprs]
   (when (meta (second exprs))
     #_(println (meta (second exprs)))
     (let [zfn-map (:zfn-map opts)
           zloc-seq-nc ((:zmap-no-comment zfn-map) identity (:zloc opts))
           meta-zloc (second zloc-seq-nc)
           #_(println "tag:" ((:ztag zfn-map) meta))
           meta-seq ((:zmap-no-comment zfn-map) identity meta-zloc)
           #_(println "count meta-seq:" (count meta-seq)
                      "meta-seq:" (map (:zstring zfn-map) meta-seq)
                      "meta-seq-tag:" (map (:ztag zfn-map) meta-seq))]
       (if (= :meta ((:ztag zfn-map) (second meta-seq)))
         ; Figure out next-inner restore
         nil
         {:meta {:split? true},
          :list {:hang-expand 0},
          :fn-style (if (and (map? (meta (second exprs)))
                             (> (count (keys (meta (second exprs)))) 1))
                      :arg1-body
                      :arg2),
          :next-inner-restore [[:list :hang-expand]]})))))

