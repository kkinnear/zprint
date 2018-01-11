(ns zprint.spec
  #?@(:cljs [[:require-macros [zprint.smacros :refer [only-keys]]]])
  (:require #?@(:clj [[zprint.smacros :refer [only-keys]]
                      [clojure.spec.alpha :as s]]
                :cljs [[cljs.spec.alpha :as s]])))

;;
;; # Compatibility
;;
;; Try to avoid loading any namespaces we don't need all the
;; time.  These can go away when we get to just 1.9
;;

(defn zany? [x] true)
#?(:clj (defn zboolean? [x] (instance? Boolean x))
   :cljs (defn ^boolean zboolean?
           [x]
           (or (cljs.core/true? x) (cljs.core/false? x))))

;!zprint {:list {:constant-pair-min 2}}

;;
;; # Specs for the options map
;;

;;
;; ## Color keys
;;

(s/def ::color #{:red :purple :green :blue :magenta :yellow :black :cyan})

(s/def ::brace ::color)
(s/def ::bracket ::color)
(s/def ::comment ::color)
(s/def ::deref ::color)
(s/def ::fn ::color)
(s/def ::hash-brace ::color)
(s/def ::hash-paren ::color)
(s/def ::keyword ::color)
(s/def ::nil ::color)
(s/def ::none ::color)
(s/def ::number ::color)
(s/def ::paren ::color)
(s/def ::syntax-quote-paren ::color)
(s/def ::quote ::color)
(s/def ::string ::color)
(s/def ::uneval ::color)
(s/def ::user-fn ::color)

;;
;; # Fundamental values
;;

(s/def ::boolean (s/nilable zboolean?))
(s/def ::fn-type
  #{:binding :arg1 :arg1-body :arg1-pair-body :arg1-pair :pair :hang :extend
    :arg1-extend :fn :arg1-> :noarg1-body :noarg1 :arg2 :arg2-extend :arg2-pair
    :arg2-fn :none :none-body :arg1-force-nl :gt2-force-nl :gt3-force-nl :flow
    :flow-body :force-nl-body :force-nl :pair-fn :arg2-mixin})
(s/def ::format-value #{:on :off :next :skip})
(s/def ::nilable-number (s/nilable number?))
(s/def ::vec-or-list-of-keyword (s/coll-of keyword? :kind sequential?))
(s/def ::style-value
  (s/or :multiple-styles ::vec-or-list-of-keyword
        :single-style (s/nilable keyword?)))
(s/def ::constant
  (s/or :string string?
        :number number?
        :keyword keyword?))
(s/def ::constant-seq (s/coll-of ::constant :kind sequential?))
(s/def ::line-seq
  (s/nilable (s/coll-of (s/or :number number?
                              :range (s/coll-of number? :kind sequential?))
                        :kind sequential?)))
(s/def ::path-seq
  (s/nilable (s/coll-of (s/coll-of number? :kind sequential?)
                        :kind sequential?)))
(s/def ::key-or-ks-seq
  (s/coll-of (s/or :constant ::constant
                   :constant-seq ::constant-seq)
             :kind sequential?))
(s/def ::key-value (s/nilable (s/coll-of ::constant :kind sequential?)))
(s/def ::key-color-value
  (s/nilable (s/coll-of (s/nilable ::color) :kind sequential?)))
;(s/def ::key-color-seq (s/coll-of (s/nilable
(s/def ::boolean-or-string
  (s/or :boolean ::boolean
        :string string?))
(s/def ::keep-or-drop #{:keep :drop})
(s/def ::fn-map-value (s/nilable (s/map-of string? ::fn-type)))

;;
;; # Leaf map keys
;;

(s/def ::comma? ::boolean)
(s/def ::constant-pair? ::boolean)
(s/def ::constant-pair-min number?)
(s/def ::count? ::boolean)
(s/def ::binding? ::boolean)
(s/def ::docstring? ::boolean)
(s/def ::elide (s/nilable string?))
(s/def ::expand? ::boolean)
(s/def ::flow? ::boolean)
(s/def ::focus (only-keys :opt-un [::zloc? ::path ::surround]))
(s/def ::force-nl? ::boolean)
(s/def ::general-hang-adjust number?)
(s/def ::hang? ::boolean)
(s/def ::hang-diff number?)
(s/def ::hang-avoid ::nilable-number)
(s/def ::hang-expand number?)
(s/def ::hang-flow number?)
(s/def ::hang-flow-limit number?)
(s/def ::hang-if-equal-flow? ::boolean)
(s/def ::hang-type-flow number?)
(s/def ::hex? ::boolean)
(s/def ::indent number?)
(s/def ::indent-arg ::nilable-number)
(s/def ::inline? ::boolean)
(s/def ::interpose ::boolean-or-string)
(s/def ::justify? ::boolean)
(s/def ::justify-hang (only-keys :opt-un [::hang? ::hang-expand ::hang-diff]))
(s/def ::justify-tuning
  (only-keys :opt-un [::hang-flow ::hang-type-flow ::hang-flow-limit
                      ::general-hang-adjust]))
(s/def ::key-color (s/nilable (s/map-of zany? ::color)))
(s/def ::key-value-color (s/nilable (s/map-of zany? ::color-map)))
(s/def ::key-depth-color ::key-color-value)
(s/def ::key-ignore (s/nilable ::key-or-ks-seq))
(s/def ::key-ignore-silent (s/nilable ::key-or-ks-seq))
(s/def ::key-order (s/nilable ::key-value))
(s/def ::left-space ::keep-or-drop)
(s/def ::lines ::line-seq)
(s/def ::modifiers (s/nilable (s/coll-of string? :kind set?)))
(s/def ::nl-separator? ::boolean)
(s/def ::object? ::boolean)
(s/def ::pair-hang? ::boolean)
(s/def ::parallel?
  #?(:clj ::boolean
     :cljs false?))
(s/def ::path (s/coll-of number? :kind sequential?))
(s/def ::paths ::path-seq)
(s/def ::surround (s/nilable (s/coll-of number? :kind sequential?)))
(s/def ::additional-libraries? ::boolean)
(s/def ::option-fn-first fn?)
(s/def ::record-type? ::boolean)
(s/def ::respect-nl? ::boolean)
(s/def ::size number?)
(s/def ::sort? ::boolean)
(s/def ::sort-in-code? ::boolean)
(s/def ::lift-ns? ::boolean)
(s/def ::lift-ns-in-code? ::boolean)
(s/def ::to-string? ::boolean)
(s/def ::value zany?)
(s/def ::wrap? ::boolean)
(s/def ::wrap-after-multi? ::boolean)
(s/def ::wrap-coll? ::boolean)
(s/def ::zloc? ::boolean)


;;
;; # Elements of the top level options map
;;

(s/def ::agent (only-keys :opt-un [::object?]))
(s/def ::array (only-keys :opt-un [::hex? ::indent ::object? ::wrap?]))
(s/def ::atom (only-keys :opt-un [::object?]))
(s/def ::auto-width? ::boolean)
(s/def ::binding
  (only-keys :opt-un [::flow? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::indent ::justify? ::justify-hang ::justify-tuning
                      ::nl-separator?]))
(s/def ::color-map
  (only-keys :opt-un [::brace ::bracket ::comment ::deref ::fn ::hash-brace
                      ::hash-paren ::keyword ::nil ::none ::number ::paren
                      ::quote ::string ::syntax-quote-paren ::uneval
                      ::user-fn]))
(s/def :alt/comment (only-keys :opt-un [::count? ::wrap? ::inline?]))
(s/def ::color? ::boolean)
(s/def ::configured? ::boolean)
(s/def ::dbg? ::boolean)
(s/def ::dbg-print? ::boolean)
(s/def ::dbg-ge zany?)

(s/def ::dbg-bug? ::boolean)


(s/def ::delay (only-keys :opt-un [::object?]))
(s/def ::drop? ::boolean)
(s/def ::do-in-hang? ::boolean)
(s/def ::extend
  (only-keys :opt-un [::flow? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::indent ::modifiers ::nl-separator?]))
(s/def :alt/extend (only-keys :opt-un [::modifiers]))
(s/def ::file? ::boolean)
(s/def ::fn-force-nl (s/nilable (s/coll-of ::fn-type :kind set?)))
(s/def ::fn-gt2-force-nl (s/nilable (s/coll-of ::fn-type :kind set?)))
(s/def ::fn-gt3-force-nl (s/nilable (s/coll-of ::fn-type :kind set?)))
(s/def ::fn-map ::fn-map-value)
(s/def ::fn-name zany?)
(s/def ::fn-obj (only-keys :opt-un [::object?]))
(s/def ::format ::format-value)
(s/def ::future (only-keys :opt-un [::object?]))
(s/def ::indent number?)
(s/def ::list
  (only-keys :opt-un [::constant-pair-min ::constant-pair? ::hang-diff
                      ::hang-avoid ::hang-expand ::hang-size ::hang? ::indent
                      ::indent-arg ::pair-hang?]))
(s/def ::map
  (only-keys
    :opt-un [::comma? ::flow? ::force-nl? ::hang-adjust ::hang-diff
             ::hang-expand ::hang? ::indent ::justify? ::justify-hang
             ::justify-tuning ::key-color ::key-value-color ::key-depth-color
             ::key-ignore ::key-ignore-silent ::key-order ::lift-ns?
             ::lift-ns-in-code? ::nl-separator? ::sort-in-code? ::sort?]))
(s/def ::max-depth number?)
(s/def ::max-hang-count number?)
(s/def ::max-hang-dept number?)
(s/def ::max-hang-span number?)
(s/def ::max-length number?)
(s/def ::object (only-keys :opt-un [::indent ::wrap-coll? ::wrap-after-multi?]))
(s/def ::old? ::boolean)
(s/def ::output (only-keys :opt-un [::focus ::lines ::elide ::paths]))
(s/def ::pair
  (only-keys :opt-un [::flow? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::indent ::justify? ::justify-hang ::justify-tuning
                      ::nl-separator?]))
(s/def ::pair-fn
  (only-keys :opt-un [::hang-diff ::hang-expand ::hang-size ::hang?]))
(s/def ::parse (only-keys :opt-un [::interpose ::left-space]))
(s/def ::parse-string-all? ::boolean)
(s/def ::parse-string? ::boolean)
(s/def ::perf-vs-format ::nilable-number)
(s/def ::process-bang-zprint? ::boolean)
(s/def ::promise (only-keys :opt-un [::object?]))
(s/def ::reader-cond
  (only-keys :opt-un [::comma? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::indent ::key-order ::sort-in-code? ::sort?]))
(s/def ::record (only-keys :opt-un [::hang? ::record-type? ::to-string?]))
(s/def ::remove
  (only-keys :opt-un [::fn-force-nl ::fn-gt2-force-nl ::fn-gt3-force-nl
                      :alt/extend]))
(s/def ::return-cvec? ::boolean)
(s/def ::set
  (only-keys :opt-un [::indent ::sort? ::sort-in-code? ::wrap-after-multi?
                      ::wrap-coll? ::wrap?]))
(s/def ::spaces? ::boolean)
(s/def ::spec (only-keys :opt-un [::docstring? ::value]))
(s/def ::style ::style-value)
(s/def ::style-map map?)
(s/def ::tab (only-keys :opt-un [::expand? ::size]))
(s/def ::trim-comments? ::boolean)
(s/def ::tuning
  (only-keys :opt-un [::hang-flow ::hang-type-flow ::hang-flow-limit
                      ::general-hang-adjust ::hang-if-equal-flow?]))
(s/def :alt/uneval (only-keys :opt-un [::color-map]))
(s/def ::user-fn-map ::fn-map-value)
(s/def ::vector
  (only-keys :opt-un [::indent ::binding? ::respect-nl? ::option-fn-first
                      ::wrap-after-multi? ::wrap-coll? ::wrap?]))
(s/def ::version string?)
(s/def ::width number?)
(s/def ::zipper? ::boolean)

;;
;; # Top level options map
;;

(s/def ::options
  (only-keys
    :opt-un [::additional-libraries? ::agent ::array ::atom ::auto-width?
             ::binding ::color? ::color-map :alt/comment ::configured? ::dbg?
             ::dbg-bug? ::dbg-print? ::dbg-ge ::delay ::do-in-hang? ::drop?
             ::extend ::file? ::fn-force-nl ::fn-gt2-force-nl ::fn-gt3-force-nl
             ::fn-map ::fn-name ::fn-obj ::format ::future ::indent ::list ::map
             ::max-depth ::max-hang-count ::max-hang-depth ::max-hang-span
             ::max-length ::object ::old? ::output ::pair ::pair-fn ::parallel?
             ::parse ::parse-string-all? ::parse-string? ::perf-vs-format
             ::process-bang-zprint? ::promise ::reader-cond ::record ::remove
             ::return-cvec? ::set ::spaces? ::spec ::style ::style-map ::tab
             ::trim-comments? ::tuning :alt/uneval ::user-fn-map ::vector
             ::version ::width ::zipper?]))

(defn numbers-or-number-pred?
  "If they are both numbers and are equal, or the first is a number 
  and the second one is a pred."
  [x y]
  (and (number? x)
       (or (= x y)
           (= y
              #?(:clj :clojure.spec.alpha/pred
                 :cljs :cljs.spec.alpha/pred)))))

(defn problem-ks
  "Return the key sequence for this problem.  This is totally empiric, and
  not based on any real understanding of what explain-data is returning as
  the problem.  It seems to stick integers into the :in for no obvious reason.
  This version has three heuristics, described in the comments in the code."
  [problem]
  (let [path (:path problem)
        last-path (last path)
        last-num (and (number? last-path) last-path)
        ks (:in problem)
        #_(println ":in" ks)
        #_(println ":path" path)
        ; First heuristic: trim ks to be no longer than path
        ks (into [] (take (count path) ks))
        ; Second heuristic: If the last thing in ks is a number and
        ; the last thing in the path is a pred, then trim the number
        last-ks (last ks)
        #_(println "ks na:" ks)
        ks (if (and (number? last-ks)
                    (= last-path
                       #?(:clj :clojure.spec.alpha/pred
                          :cljs :cljs.spec.alpha/pred)))
             (into [] (butlast ks))
             ks)
        ; Third heuristic: Remove the first number in ks that is at
        ; the same index as a matching number in the path, if it is not
        ; equal to the val.
        ks-equal (map #(when (numbers-or-number-pred? %1 %2) %1) ks path)
        matching-index (reduce
                         #(if (number? %2) (reduced %1) (inc %1) #_(dec %1))
                         0 ks-equal)
        matching-index (when (< matching-index (count ks)) matching-index)
        #_(println "ks mi:" ks "matching-index:" matching-index)
        ks (if (and matching-index
                    (not= (nth ks matching-index) (:val problem)))
             (let [[begin end] (split-at matching-index ks)]
               (into [] (concat begin (drop 1 end))))
             ks)]
    ks))

(defn ks-phrase
  "Take a key-sequence and a value, and decide if we want to 
  call it a value or a key."
  [problem]
  (let [val (:val problem)
        ks (problem-ks problem)]
    (if ((set ks) val)
      (str "In the key-sequence " ks " the key " (pr-str val))
      (str "The value of the key-sequence " ks " -> " (pr-str val)))))

(defn map-pred
  "Turn some predicates into something more understandable."
  [pred]
  (case pred
    "zboolean?" "boolean"
    "zprint.spec/zboolean?" "boolean"
    "clojure.core/set?" "set"
    "clojure.core/sequential?" "sequential"
    "clojure.core/number?" "number"
    "clojure.core/map?" "map"
    "map?" "map"
    "string?" "string"
    pred))

(defn map-pred-alt
  "Turn some predicates into something more understandable."
  [pred]
  (case pred
    'zboolean? 'boolean?
    pred))

(defn explain-more
  "Try to do a better job of explaining spec problems."
  [explain-data-return]
  (when explain-data-return
    (let [problem-list (#?(:clj :clojure.spec.alpha/problems
                           :cljs :cljs.spec.alpha/problems)
                        explain-data-return)
          problem-list (remove #(= "nil?" (str (:pred %))) problem-list)
          val-map (group-by :val problem-list)
          key-via-len-seq
            (map (fn [[k v]] [k (apply min (map (comp count :via) v))]) val-map)
          [key-choice min-via] (first (sort-by second key-via-len-seq))
          problem (first (filter (comp (partial = min-via) count :via)
                           (val-map key-choice)))]
      (cond (clojure.string/ends-with? (str (:pred problem)) "?")
              (str (ks-phrase problem)
                   " was not a " (map-pred (str (:pred problem))))
            (set? (:pred problem)) (str (ks-phrase problem)
                                        " was not recognized as valid!")
            :else (str "what?")))))

(defn validate-basic
  "Using spec defined above, validate the given options map.  Return
  nil if no errors, or a string containing errors if any."
  ([options source-str]
   #_(println "Options:" options)
   (try (if (s/valid? ::options options)
          nil
          (if source-str
            (str "In " source-str
                 ", " (explain-more (s/explain-data ::options options)))
            (explain-more (s/explain-data ::options options))))
        (catch #?(:clj Exception
                  :cljs :default) e
          #_(println "Exception:" (str e))
          #_(println "type of exception:" (type e))
          #_(println ":cause" (:cause e))
          (if source-str
            (str "In "
                 source-str
                 ", validation failed completely because: "
                 (str e)
                 #_(.-message e))
            (str "Validation failed completely because: "
                 (str e)
                 #_(.-message e))))))
  ([options] (validate-basic options nil)))

#_(defn explain
    "Take an options map and explain the result of the spec.  This is
  really here for testing purposes."
    ([options show-problems?]
     (let [problems (s/explain-data ::options options)]
       (when show-problems? (zprint.core/czprint problems))
       (explain-more problems)))
    ([options] (explain options nil)))
