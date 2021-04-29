(ns ^:no-doc zprint.guide
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require #?@(:clj [[zprint.macros :refer
                       [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
            [clojure.string :as s]
            [zprint.util :refer [abs column-alignment cumulative-alignment]]))

;;
;; Contains functions which can be called with {:option-fn <fn>} to produce
;; a "guide", which is, roughtly, a sequence comprised of keywords
;; which describe how to format an expression.  A guide must be created 
;; explicitly for the expression to be formatted.  
;;
;; For instance, this expression: (a b c d e f g) could be formatted 
;; for this output:
;;
;; (a b c
;;  d e f
;;  g)
;;
;; by this guide: 
;;
;; [:element :element :element :newline :element :element :element :newline
;;  :element]
;;          
;; There are a lot more keywords and other things which can be in a guide 
;; than demonstrated above.

;;
;; # Guide for "rules of defn", an alternative way to format defn expressions.
;;

(defn rodguide
  "Given a structure which starts with defn, create a guide for the
  'rules of defn', an alternative approach to formatting a defn."
  ([] "rodguide")
  ([options len sexpr]
   (when (= (str (first sexpr)) "defn")
     (let [docstring? (string? (nth sexpr 2))
           rest (nthnext sexpr (if docstring? 3 2))
           multi-arity? (not (vector? (first rest)))
           rest (if multi-arity? rest (next rest))
           rest-guide (repeat (dec (count rest)) :element)
           rest-guide
             (into []
                   (if multi-arity?
                     (interleave rest-guide (repeat :newline) (repeat :newline))
                     (interleave rest-guide (repeat :newline))))
           ; Make interleave into interpose
           rest-guide (conj rest-guide :element)
           guide (cond-> [:element :element]
                   docstring? (conj :newline :element :newline)
                   (not multi-arity?) (conj :element :newline)
                   (and multi-arity? (not docstring?)) (conj :newline)
                   :rest (into rest-guide))
           option-map {:guide guide, :next-inner {:list {:option-fn nil}}}]
       (if multi-arity?
         (assoc option-map
           :fn-map {:vector [:force-nl
                             {:next-inner {:fn-map {:vector :none}}}]})
         option-map)))))

; Use this to use the above:
;
; (czprint rod4 
;    {:parse-string? true 
;     :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}})

;;
;; # Guide to replicate the existing output for {:style :moustache}
;;

(defn constant-or-vector?
  "Return true if a constant or vector."
  [element]
  #_(println "c-or-v?" element)
  (or (number? element)
      (string? element)
      (vector? element)
      (keyword? element)
      (= element true)
      (= element false)))

(defn count-constants
  [[constant-count possible-constant?] element]
  (if possible-constant?
    (if (constant-or-vector? element)
      [(inc constant-count) (not possible-constant?)]
      (reduced [constant-count possible-constant?]))
    [constant-count (not possible-constant?)]))

(defn moustacheguide
  "Reimplement :style :moustache with guides."
  ([] "moustacheguide")
  ([options len sexpr]
   ; First, find the pairs.
   (let [rev-sexpr (reverse sexpr)
         [constant-count _] (reduce count-constants [0 false] rev-sexpr)
         pair-count (* constant-count 2)
         pair-guide (into [] (repeat pair-count :element))
         pair-guide (conj pair-guide :group-end)
         pair-guide (conj pair-guide :element-pair-group)
         non-pair-count (- (count sexpr) pair-count)
         non-pair-guide (repeat non-pair-count :element)
         non-pair-guide (into [] (interpose :newline non-pair-guide))
         guide (conj non-pair-guide :newline :group-begin)
         guide (concat guide pair-guide)]
     (dbg-s options
            :guide
            "moustacheguide: sexpr" sexpr
            "pair-count:" pair-count
            "output:" guide)
     {:guide guide,
      :pair {:justify? true},
      :next-inner {:pair {:justify? false}, :list {:option-fn nil}}})))

; Use this to use the above:
;
;(czprint mapp6g 
;   {:parse-string? true 
;    :fn-map {"m/app" [:guided {:list {:option-fn moustacheguide}}]}})


;;
;; # Guide for the "are" function
;;

(defn areguide
  "Format are test functions."
  ([] "areguide")
  ([options len sexpr]
   (let [arg-vec-len (count (second sexpr))
         beginning (take 3 sexpr)
         test-len (- (count sexpr) 3)
         rows (/ test-len arg-vec-len)
         excess-tests (- test-len (* rows arg-vec-len))
         single-row (into [:newline] (repeat arg-vec-len :element))
         row-guide (apply concat (repeat rows single-row))
         guide (cond-> (-> [:element :element :element]
                           (into row-guide))
                 (pos? excess-tests) (conj :newline :element-*))]
     {:guide guide, :next-inner {:list {:option-fn nil}}})))

; Do this to use the above:
;
; (czprint are3 
;   {:parse-string? true 
;    :fn-map {"are" [:guided {:list {:option-fn areguide}}]}})
;

;;
;; # Guide to justify the content of the vectors in a (:require ...)
;;

;
; A much simpler version of the require guide.  This version doesn't require
; use of the call-stack, and has only one option-fn instead of two.  It also
; uses the new variance-based justification capabilities.
;
(defn jrequireguide
  "Justify the first things in a series of require vectors."
  ([] "jrequireguide")
  ([options len sexpr]
   (when (= (first sexpr) :require)
     (let [vectors (filter vector? sexpr)
           max-width-vec (column-alignment (:max-variance (:justify (:pair
                                                                      options)))
                                           vectors
					   ; only do the first column
					   1)
	   _ (dbg-s options :guide "jrequireguide max-width-vec:"
	                           max-width-vec)
           max-first (first max-width-vec)
           vector-guide (if max-first
                          [:mark-at 0 (inc max-first) :element :align 0
                           :element-pair-*]
			  ; We can't justify things, fall back to this.
                          [:element :element-pair-*])]
       ; Do this for all of the first level vectors below the :require, but
       ; no other vectors more deeply nested.
       {:next-inner {:vector {:option-fn (fn [_ _ _] {:guide vector-guide}),
                              :wrap-multi? true,
                              :hang? true},
                     :pair {:justify? true},
                     :next-inner {:vector {:option-fn nil,
                                           :wrap-multi? false,
                                           :hang? false}}}}))))

; Do this to use the above:
;
; (czprint jr1 
;    {:parse-string? true 
;    :fn-map {":require" [:none {:list {:option-fn jrequireguide}}]}})

;;
;; # Guide to replicate the output of :arg1-mixin
;; 

(defn rumguide
  "Assumes that this is rum/defcs or something similar. Implement :arg1-mixin
  with guides using :spaces.  For guide testing, do not use this as a model
  for how to write a guide."
  ([] "rumguide")
  ([options len sexpr]
   (let [docstring? (string? (nth sexpr 2))
         [up-to-arguments args-and-after]
           (split-with #(not (or (vector? %)
                                 (and (list? %) (vector? (first %)))))
                       sexpr)
         #_(println "rumguide: up-to-arguments:" up-to-arguments
                    "\nargs-and-after:" args-and-after)]
     (if (empty? args-and-after)
       {:list {:option-fn nil}}
       (let [lt (nth sexpr (if docstring? 3 2))
             lt? (= (str lt) "<")
             mixin-indent (if lt? 2 1)
             beginning-guide [:element :element :newline]
             beginning-guide (if docstring?
                               (concat beginning-guide [:element :newline])
                               beginning-guide)
             middle-element-count
               (- (count up-to-arguments) 2 (if docstring? 1 0) (if lt? 1 0))
             middle-guide
               (if (pos? middle-element-count)
                 (if lt? [:element :element :newline] [:element :newline])
                 [])
             #_(println "middle-element-count:" middle-element-count)
             middle-guide (concat middle-guide
                                  (repeat (dec middle-element-count)
                                          [:spaces mixin-indent :element
                                           :newline]))
             end-element-count (count args-and-after)
             end-guide [:element
                        (repeat (dec end-element-count) [:newline :element])]
             guide (concat beginning-guide middle-guide end-guide)
	     ; This could have been done so flatten wasn't necessary
	     ; but it for testing it wasn't worth the re-work.
             guide (flatten guide)
             #_(println "rumguide: guide:" guide)]
         {:guide guide, :next-inner {:list {:option-fn nil}}})))))

(defn rumguide-1
  "Assumes that this is rum/defcs or something similar. Implement :arg1-mixin
  with guides using :align.  For guide testing, do not use this as a model
  for how to write a guide."
  ([] "rumguide")
  ([options len sexpr]
   (let [docstring? (string? (nth sexpr 2))
         [up-to-arguments args-and-after]
           (split-with #(not (or (vector? %)
                                 (and (list? %) (vector? (first %)))))
                       sexpr)
         #_(println "rumguide: up-to-arguments:" up-to-arguments
                    "\nargs-and-after:" args-and-after)]
     (if (empty? args-and-after)
       {:list {:option-fn nil}}
       (let [lt (nth sexpr (if docstring? 3 2))
             lt? (= (str lt) "<")
             beginning-guide [:element :element :newline]
             beginning-guide (if docstring?
                               (concat beginning-guide [:element :newline])
                               beginning-guide)
             middle-element-count
               (- (count up-to-arguments) 2 (if docstring? 1 0) (if lt? 1 0))
             middle-guide (if (pos? middle-element-count)
                            (if lt?
                              [:element :mark 1 :align 1 :element :newline]
                              [:mark 1 :align 1 :element :newline])
                            [])
             #_(println "middle-element-count:" middle-element-count)
             middle-guide (concat middle-guide
                                  (repeat (dec middle-element-count)
                                          [:align 1 :element :newline]))
             end-element-count (count args-and-after)
             end-guide [:element
                        (repeat (dec end-element-count) [:newline :element])]
             guide (concat beginning-guide middle-guide end-guide)
	     ; This could have been done so flatten wasn't necessary
	     ; but it for testing it wasn't worth the re-work.
             guide (flatten guide)
             #_(println "rumguide: guide:" guide)]
         {:guide guide, :next-inner {:list {:option-fn nil}}})))))

(defn rumguide-2
  "Assumes that this is rum/defcs or something similar. Implement :arg1-mixin
  with guides using :indent.  This is probably the simplest and therefore the
  best of them all.  For guide testing, do not use this as a model for how
  to write a guide."
  ([] "rumguide")
  ([options len sexpr]
   (let [docstring? (string? (nth sexpr 2))
         [up-to-arguments args-and-after]
           (split-with #(not (or (vector? %)
                                 (and (list? %) (vector? (first %)))))
                       sexpr)
         #_(println "rumguide: up-to-arguments:" up-to-arguments
                    "\nargs-and-after:" args-and-after)]
     (if (empty? args-and-after)
       {:list {:option-fn nil}}
       (let [lt (nth sexpr (if docstring? 3 2))
             lt? (= (str lt) "<")
             beginning-guide [:element :element :newline]
             beginning-guide (if docstring?
                               (concat beginning-guide [:element :newline])
                               beginning-guide)
             middle-element-count
               (- (count up-to-arguments) 2 (if docstring? 1 0) (if lt? 1 0))
             middle-guide (if (pos? middle-element-count)
                            (if lt?
                              [:element :indent 4 :element :newline]
                              [:indent 4 :element :newline])
                            [])
             #_(println "middle-element-count:" middle-element-count)
             middle-guide (concat middle-guide
                                  (repeat (dec middle-element-count)
                                          [:element :newline]))
             end-element-count (count args-and-after)
             end-guide [:indent-reset :element
                        (repeat (dec end-element-count) [:newline :element])]
             guide (concat beginning-guide middle-guide end-guide)
	     ; This could have been done so flatten wasn't necessary
	     ; but it for testing it wasn't worth the re-work.
             guide (flatten guide)
             #_(println "rumguide: guide:" guide)]
         {:guide guide, :next-inner {:list {:option-fn nil}}})))))


; Do this to use the above:
;
; (czprint cz8x1 
;     {:parse-string? true 
;     :fn-map {"rum/defcs" [:guided {:list {:option-fn rumguide}}]}})

(defn odrguide
  "Justify O'Doyles Rules"
  ([] "odrguide")
  ([options len sexpr]
   (when (= (first sexpr) :what)
     (let [[vectors beyond] (split-with vector? (next sexpr))
           max-width-vec (column-alignment (:max-variance (:justify (:pair
                                                                      options)))
                                           vectors)
           alignment-vec (cumulative-alignment max-width-vec)
           mark-guide
             (vec (flatten
                    (mapv vector (repeat :mark-at) (range) alignment-vec)))
           alignment-guide
             (mapv vector (repeat :align) (range (count alignment-vec)))
           vector-guide (into mark-guide
                              (flatten [(interleave (repeat :element)
                                                    alignment-guide)
                                        :element-*]))
           keyword-1 (first beyond)
           [keyword-1-lists beyond] (split-with list? (next beyond))
           keyword-2 (first beyond)
           [keyword-2-lists beyond] (split-with list? (next beyond))
           _ (dbg-s options
                    :guide
                    "odrguide alignment-vec:" alignment-vec
                    "mark-guide:" mark-guide
                    "alignment-guide:" alignment-guide
                    "vector-guide:" vector-guide
                    "keyword-1:" keyword-1
                    "keyword-1-lists:" keyword-1-lists
                    "keyword-2:" keyword-2
                    "keyword-2-lists:" keyword-2-lists)
           guide (cond->
                   (-> [:element :indent 2 :options
                        {:guide vector-guide,
                         :vector {:wrap-multi? true, :hang? true}} :group-begin]
                       (into (repeat (count vectors) :element))
                       (conj :group-end
                             :element-newline-best-group :options-reset
                             :options {:vector {:wrap-multi? true, :hang? true}}
                             :indent 1))
                   keyword-1 (conj :newline :element)
                   (not (empty? keyword-1-lists))
                     (-> (conj :indent 2 :group-begin)
                         (into (repeat (count keyword-1-lists) :element))
                         (conj :group-end :element-newline-best-group
                               :indent 1))
                   keyword-2 (conj :newline :element)
                   (not (empty? keyword-2-lists))
                     (-> (conj :indent 2 :group-begin)
                         (into (repeat (count keyword-2-lists) :element))
                         (conj :group-end :element-newline-best-group)))]
       (dbg-s options :guide "odrguide:" guide)
       {:guide guide}))))

;;
;; Guide guide
;;

(def guide-arg-count
  {:element 0,
   :element-* 0,
   :element-best 0,
   :element-best-* 0,
   :element-pair-group 0,
   :element-pair-* 0,
   :element-newline-best-group 0,
   :element-newline-best-* 0,
   :element-binding-group 0,
   :element-binding-* 0,
   :element-guide 1,
   :element-binding-vec 0,
   :newline 0,
   :options 1,
   :options-reset 0,
   :indent 1,
   :indent-reset 0,
   :spaces 1,
   :mark-at 2,
   :mark 1,
   :align 1,
   :group-begin 0,
   :group-end 0})

(def guide-insert
  {:group-begin {:after [:indent 3]}, :group-end {:before [:indent 1]}})

(defn handle-args
  "Figure out the arg-count for a guide."
  [[guide running-arg-count] command]
  (if (zero? running-arg-count)
    (let [command-arg-count (guide-arg-count command)
          before (:before (guide-insert command))
          after (:after (guide-insert command))]
      [(cond-> guide
         before (into before)
         (empty? guide) (conj :element)
         (not (empty? guide)) (conj :newline :element)
         after (into after)) command-arg-count])
    [(conj guide :element) (dec running-arg-count)]))

(defn guideguide
  "Print out a guide"
  ([] "guideguide")
  ([options len sexpr]
   (when (guide-arg-count (first sexpr))
     {:guide (first (reduce handle-args [[] 0] sexpr))})))

;;
;; Real guide for defprotocol
;;

(defn defprotocolguide
  "Handle defprotocol with options."
  ([] "defprotocolguide")
  ([options len sexpr]
   (when (= (first sexpr) 'defprotocol)
     (let [third (nth sexpr 2 nil)
           fourth (nth sexpr 3 nil)
           fifth (nth sexpr 4 nil)
           [docstring option option-value]
             (cond (and (string? third) (keyword? fourth)) [third fourth fifth]
                   (string? third) [third nil nil]
                   (keyword? third) [nil third fourth]
                   :else [nil nil nil])
           guide (cond-> [:element :element-best :newline]
                   docstring (conj :element :newline)
                   option (conj :element :element :newline)
                   :else (conj :element-newline-best-*))]
       {:guide guide, :next-inner {:list {:option-fn nil}}}))))

(defn signatureguide1
  "Handle defprotocol signatures with arities and  doc string on their 
  own lines."
  ([] "signatureguide1")
  ([options len sexpr]
    (let [vectors (filter vector? sexpr)
          guide [:element :group-begin]
	  guide (apply conj guide (repeat (count vectors) :element))
	  guide (conj guide :group-end :element-newline-best-group 
	                    :newline :element-*)]
       {:guide guide})))

