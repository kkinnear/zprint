(ns ^:no-doc zprint.optionfn
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require #?@(:clj [[zprint.macros :refer
                       [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
            [zprint.rewrite :refer [sort-dependencies]]
            [zprint.util :refer [column-alignment cumulative-alignment]]))

;;
;; Contains functions which can be called with {:option-fn <fn>} to
;; produce
;; a new options map.  Option-fns which produce a "guide" are in
;; guide.cljc.
;; The optionfns here are called the same way, but just produce a basic
;; option map.
;;

(defn rodfn
  "Given a structure which starts with defn or fn format it using the
  'rules of defn'."
  ([] "rodfn")
  ; If you call an option-fn with partial because it has its own options
  ; map, the "no-argument" arity must include the options map!
  ([rod-options] "rodfn")
  ; Since we have released this before, we will also allow it to be called
  ; without rod-options (since this is a drop-in replacement for rodguide).
  ([options len sexpr] (rodfn {} options len sexpr))
  ([rod-options options len sexpr]
   (let [multi-arity-nl? (get rod-options :multi-arity-nl? true)
         one-line-ok? (:one-line-ok? rod-options)
         fn-name (if (symbol? (second sexpr)) 1 0)
         fn-name? (= fn-name 1)
         docstring (if (string? (nth sexpr (inc fn-name))) 1 0)
         docstring? (= docstring 1)
         attr-map (if (map? (nth sexpr (inc (+ fn-name docstring)))) 1 0)
         attr-map? (= attr-map 1)
         multi-arity? (not (vector? (nth sexpr
                                         (inc (+ fn-name docstring attr-map)))))
         nl-count (cond (and multi-arity? multi-arity-nl? docstring?) [1 2]
                        (and multi-arity? multi-arity-nl?) [2]
                        :else [1])
         option-map {:list {:nl-count nl-count},
                     :next-inner {:list {:option-fn nil}},
                     :next-inner-restore [[:list :nl-count]]}
         option-map
           (if one-line-ok? (assoc option-map :one-line-ok? true) option-map)
         option-map
           (cond (and fn-name?
                      (and (not multi-arity?) (not attr-map?) (not docstring?)))
                   (assoc option-map :fn-style :arg2-force-nl-body)
                 fn-name? (assoc option-map :fn-style :arg1-force-nl-body)
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


;;
;; When given (fn* ...), turn it back into #(...%...).
;;

(defn fn*->%
  "Given a structure starting with fn*, turn it back into a #(...) anon fn."
  ([] "fn*->%")
  ([options n exprs]
   #_(println zloc)
   (when (= (:ztype options) :sexpr)
     ; We know we've got a struct
     (let [caller (:caller options)
           zloc (:zloc options)
           l-str (:l-str options)
           arg-vec (second zloc)
           arg-count (count arg-vec)
           [arg-vec final-value]
             (if (and (>= arg-count 2) (= (nth arg-vec (- arg-count 2)) '&))
               [(conj (into [] (take (- arg-count 2) arg-vec))
                      (nth arg-vec (dec arg-count))) "&"]
               [arg-vec nil])
           arg-count (count arg-vec)
           replace-map (zipmap arg-vec
                               (mapv (comp symbol (partial str "%"))
                                 (if (= arg-count 1)
                                   [""]
                                   (if final-value
                                     (conj (mapv inc (range (dec arg-count)))
                                           final-value)
                                     (mapv inc (range arg-count))))))
           new-zloc (clojure.walk/prewalk-replace replace-map (nth zloc 2))]
       {:list {:option-fn nil},
        :new-zloc new-zloc,
        :new-l-str (str "#" l-str)}))))

(defn sort-deps
  "option-fn interface to sort-dependencies"
  ([] "sort-deps")
  ([options n exprs]
   (when (= (:ztype options) :zipper)
     (let [caller (:caller options)
           zloc (:zloc options)
           new-zloc (sort-dependencies caller options zloc)]
       {:new-zloc new-zloc, :list {:option-fn nil}}))))

(defn regexfn
  "Match functions that are not found in the :fn-map against a
  series of regular expression rules.  These rules are supplied as
  a set of pairs in a vector as the first argument.  Each pair
  should be a regular expression paired with an options map.  If
  the regex matches, will return the associated options map. 
  Process the pairs in the order they appear in the vector.  If
  none of the regex expressions match, return nil."
  ([rules-vec] "regexfn")
  ([rules-vec options len sexpr]
   (let [fn-name (first sexpr)
         fn-str (str fn-name)
         rule-pairs (partition 2 2 (repeat nil) rules-vec)
         result (reduce #(when (re-find (first %2) fn-str)
                           (reduced (second %2)))
                  nil
                  rule-pairs)]
     result)))

(defn rulesfn
  "Match functions that are not found in the :fn-map against a
  series of rules.  These rules are supplied as a set of pairs in
  a vector as the first argument to rulesfn.  Each pair could be a
  regular expression paired with an options map or a function paired
  with an options map.  If the left-hand-side of the pair is a
  regex, and the regex matches the string representation of the
  first element in the list, return the associated options map.  If
  the left-hand-side of the pair is a function, supply the string
  representation of the first element of the list as the single
  argument to the function.  If the function returns a non-nil
  result, return the options map from that pair.  Process the pairs
  in the order they appear in the vector. If none of the regex
  expressions match or functions return non-nil, return nil."
  ([rules-vec] "rulesfn")
  ([rules-vec options len sexpr]
   (let [fn-name (first sexpr)
         fn-str (str fn-name)
         rule-pairs (partition 2 2 (repeat nil) rules-vec)
         result (reduce #(let [lhs (first %2)]
                           (cond (fn? lhs) (when (lhs fn-str)
                                             (reduced (second %2)))
                                 :else (when (re-find (first %2) fn-str)
                                         (reduced (second %2)))))
                  nil
                  rule-pairs)]
     result)))

