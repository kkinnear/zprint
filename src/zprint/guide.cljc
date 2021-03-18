(ns ^:no-doc zprint.guide
  (:require [clojure.string :as s]))

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

(defn rodvecguide
  "If a list where the first things is a vector was one level down from
  a defn, then make sure the vector is on a line by itself, as is everything
  else."
  ([] "rodvecguide")
  ([options len sexpr]
   #_(println "================= rodvecguide")
   (let [call-stack (:call-stack options)
         callers-frame (second call-stack)
         fn-str (:zfirst-info callers-frame)]
     ; We know that we are in a list that starts with a vector, as we
     ; wouldn't be here if we weren't.  Is the next one up the stack defn?
     (when (= fn-str "defn")
       ; Yes!
       #_(println "rodvecguide:" (first sexpr))
       (let [basic-guide (repeat (count sexpr) :element)
             final-guide (interpose :newline basic-guide)]
         {:guide final-guide})))))

(defn rodguide
  "Given a structure which starts with defn, create a guide."
  ([] "rodguide")
  ([options len sexpr]
   (when (= (:zfirst-info (first (:call-stack options))) "defn")
     (let [docstring? (string? (nth sexpr 2))
           beginning-guide (if docstring?
                             [:element :element :newline :element :newline]
                             [:element :element])
           rest (nthnext sexpr (if docstring? 3 2))
           #_(println "first rest:" rest)
           multi-arity? (not (vector? (first rest)))
           #_(println "multi-arity?" multi-arity?)
           rest (if multi-arity? rest (next rest))
           beginning-guide (if multi-arity?
                             (if docstring?
                               beginning-guide
                               (concat beginning-guide [:newline]))
                             (concat beginning-guide [:element :newline]))
           #_(println "beginning-guide:" beginning-guide)
           #_(println "rest:" rest)
           rest-element (into [] (repeat (count rest) :element))
           rest-guide (interpose (if multi-arity? [:newline :newline] :newline)
                        rest-element)
           rest-guide (flatten rest-guide)
           guide (concat beginning-guide rest-guide)]
       {:guide guide,
        :fn-map {:vector [:guided {:list {:option-fn rodvecguide}}]},
        :next-inner {:list {:option-fn nil}}}))))

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
   #_(println "moustacheguide:" sexpr)
   (let [rev-sexpr (reverse sexpr)
         [constant-count _] (reduce count-constants [0 false] rev-sexpr)
         pair-count (* constant-count 2)
         #_(println "moustacheguide: pair-count:" pair-count)
         pair-guide (into [] (repeat pair-count :element))
         pair-guide (conj pair-guide :pair-end)
         non-pair-count (- (count sexpr) pair-count)
         non-pair-guide (repeat non-pair-count :element)
         non-pair-guide (into [] (interpose :newline non-pair-guide))
         guide (conj non-pair-guide :newline :pair-begin)
         guide (concat guide pair-guide)]
     #_(println "moustacheguide: output:" guide)
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
         beginning-guide [:element :element :element]
         test-len (- (count sexpr) 3)
         rows (/ test-len arg-vec-len)
         excess-tests (- test-len (* rows arg-vec-len))
         row-guide (repeat rows [:newline (repeat arg-vec-len :element)])
         excess-guide (when (pos? excess-tests)
                        [:newline (repeat excess-tests :element)])
         guide (flatten (concat beginning-guide row-guide excess-guide))]
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

(defn jvectorguide
  "Look at the information in the call stack, and if we see jrequire one
  level up, try to outut a guide to justify the first thing in the vector."
  ([] "jvectorguide")
  ([options len sexpr]
   #_(println "\n+++++>" (first sexpr)
              "\nguide:" (:guide options)
              "\nfn-style:" (:fn-style options))
   (let [call-stack (:call-stack options)
         caller-frame (second call-stack)
         ; Retrieve information left by jrequireguide
         max-first (:jrequireguide caller-frame)]
     (when (and max-first (symbol? (first sexpr)))
       (let [first-len (count (str (first sexpr)))
             spaces-needed (- (inc max-first) first-len)
             guide [:element :spaces spaces-needed :pair-begin
                    (repeat (dec len) :element) :pair-end]
             guide (into [] (flatten guide))]
         #_(println "=====>" (str (first sexpr)))
         #_(println "top-frame:" caller-frame)
         #_(println "max-first:" max-first)
         #_(println "first-len:" first-len)
         #_(println "spaces-needed:" spaces-needed)
         #_(println "guide:" guide)
         {:pair {:justify? true},
          :guide guide,
          :next-inner {:vector {:option-fn nil}, :pair {:justify? false}}})))))

(defn jrequireguide
  "Justify the first things in a series of require vectors."
  ([] "jrequireguide")
  ([options len sexpr]
   #_(doseq [x sexpr] (prn "jrequire: " x))
   (when (= (first sexpr) :require)
     #_(println "Got it!")
     (let [vectors (filter vector? sexpr)
           max-first (apply max (map #(count (str (first %))) vectors))
           call-stack (:call-stack options)
           #_(println "first call-stack:" (first call-stack))
           #_(println "type call-stack:" (type call-stack))
           top-frame (first call-stack)
           ; Leave information around for jvectorguide to use for justify
           new-top-frame (assoc top-frame :jrequireguide max-first)
           new-call-stack (conj (rest call-stack) new-top-frame)]
       #_(println "max-first: " max-first)
       ;(println "new-call-stack:" new-call-stack)
       {:list {:option-fn nil},
        ; Actually add a map to the current frame on the call-stack
        :call-stack (conj (rest call-stack) new-top-frame),
        :vector {:option-fn jvectorguide :wrap-multi? true}}))))

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
  with guides using :spaces."
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
             guide (flatten guide)
             #_(println "rumguide: guide:" guide)]
         {:guide guide, :next-inner {:list {:option-fn nil}}})))))

(defn rumguide-1
  "Assumes that this is rum/defcs or something similar. Implement :arg1-mixin
  with guides using :align"
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
             guide (flatten guide)
             #_(println "rumguide: guide:" guide)]
         {:guide guide, :next-inner {:list {:option-fn nil}}})))))

(defn rumguide-2
  "Assumes that this is rum/defcs or something similar. Implement :arg1-mixin
  with guides using :indent.  This is probably the simplest and therefore the
  best of them all."
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
           max-first (apply max (map #(count (str (first %))) vectors))
           max-second (apply max (map #(count (str (second %))) vectors))
           call-stack (:call-stack options)
           align-setup [:align 1 (inc max-first) :align 2
                        (+ (inc max-first) (inc max-second))]
           vector-guide [:mark-at 1 (inc max-first) :mark-at 2
                         (+ (inc max-first) (inc max-second)) :element :align 1
                         :element :align 2 :element]
           guide [:element :options {:guide vector-guide}
                  (repeat (count vectors) [:newline :element]) :options-reset
                  (repeat (count beyond) [:newline :element])]
           guide (into [] (flatten guide))]
       (println "odrguide:" guide)
       {:guide guide}))))

(defn size
  "Return the size of an sexpr, accounting for things that have been
  made zprint.core."
  [sexpr]
  (let [s (str sexpr)
        s (cond (clojure.string/starts-with? s ":zprint.core/")
                  (clojure.string/replace s ":zprint.core/" "::")
                (clojure.string/starts-with? s ":clojure.core/")
                  (clojure.string/replace s ":clojure.core/" "::")
                :else s)]
    (count s)))

(defn odrguide2
  "Justify O'Doyles Rules"
  ([] "odrguide2")
  ([options len sexpr]
   (when (= (first sexpr) :what)
     (let [[vectors beyond] (split-with vector? (next sexpr))
           max-first (apply max (map #(size (first %)) vectors))
	   #_ (println "max-first:" max-first)
           max-second (apply max (map #(size (second %)) vectors))
	   #_ (println "max-second:" max-second)
	   #_ (println "actual first:" (ffirst vectors))
           align-setup [:align 1 (inc max-first) :align 2
                        (+ (inc max-first) (inc max-second))]
           vector-guide [:mark-at 1 (inc max-first) :mark-at 2
                         (+ (inc max-first) (inc max-second)) :element :align 1
                         :element :align 2 :element :element :element]
           keyword-1 (first beyond)
           [keyword-1-lists beyond] (split-with list? (next beyond))
           keyword-2 (first beyond)
           [keyword-2-lists beyond] (split-with list? (next beyond))
           #_(println "keyword-1:" keyword-1
                      "keyword-1-lists:" keyword-1-lists
                      "keyword-2:" keyword-2
                      "keyword-2-lists:" keyword-2-lists)
           guide
             [:element :indent (+ (count (str (first sexpr))) 2) :options
              {:guide vector-guide}
              (interpose :newline (repeat (count vectors) :element))
              :options-reset :options {:vector {:wrap-multi? true}} :newline
              #_:newline :indent 1 :element :indent (+ (count (str keyword-1)) 2)
              (repeat (count keyword-1-lists) [:element :newline]) :indent 1
              #_:newline :element :indent (+ (count (str keyword-2)) 2)
              (interpose :newline (repeat (count keyword-2-lists) :element))]
           guide (into [] (flatten guide))]
       #_(println "odrguide:" guide)
       {:guide guide}))))
