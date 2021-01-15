(ns ^:no-doc zprint.spec
  #?@(:cljs [[:require-macros [zprint.smacros :refer [only-keys]]]])
  (:require clojure.string
            [clojure.walk :refer [prewalk]]
            [zprint.ansi :refer [ansi-codes]]
            #?@(:clj [[zprint.smacros :refer [only-keys]]
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

(defn booleanable?
  "Can this value be coerced into a boolean?"
  [x]
  (try (boolean x)
       true
       (catch #?(:clj Exception
                 :cljs :default)
         e
         false)))

;!zprint {:list {:constant-pair-min 2}}

;;
;; # Specs for the options map
;;

;;
;; ## Color keys
;;

(def ansi-code
  (-> ansi-codes
      keys
      set))

(defn ansi-codes? [x] (if (sequential? x) (every? ansi-code x) (ansi-code x)))

(s/def ::color ansi-codes?)

(s/def ::brace ::color)
(s/def ::bracket ::color)
(s/def ::char ::color)
(s/def ::comma ::color)
(s/def ::comment ::color)
(s/def ::deref ::color)
(s/def ::false ::color)
(s/def ::fn ::color)
(s/def ::hash-brace ::color)
(s/def ::hash-paren ::color)
(s/def ::keyword ::color)
(s/def ::nil ::color)
(s/def ::none ::color)
(s/def ::number ::color)
(s/def ::paren ::color)
(s/def ::symbol ::color)
(s/def ::syntax-quote ::color)
(s/def ::syntax-quote-paren ::color)
(s/def ::quote ::color)
(s/def ::regex ::color)
(s/def ::string ::color)
(s/def ::true ::color)
(s/def ::uneval ::color)
(s/def ::unquote ::color)
(s/def ::unquote-splicing ::color)
(s/def ::user-fn ::color)

;;
;; # Fundamental values
;;

(s/def ::boolean (s/nilable zboolean?))
;(s/def ::boolean booleanable?)

; Note that actual fn-types can be [:arg1 {:style :respect-nl}] in addition
; to simple keywords.  It used to be that these things were ripped apart
; during option map validation and done separately. Now we get spec to do
; them for us!

(s/def ::fn-type
  #{:binding :arg1 :arg1-body :arg1-pair-body :arg1-pair :pair :hang :extend
    :arg1-extend :fn :arg1-> :noarg1-body :noarg1 :arg2 :arg2-extend :arg2-pair
    :arg2-fn :none :none-body :arg1-force-nl :gt2-force-nl :gt3-force-nl :flow
    :flow-body :force-nl-body :force-nl :pair-fn :arg1-mixin :arg2-mixin :indent
    :replace-w-string})
(s/def ::fn-type-w-map
  (s/or :general-options (s/tuple ::fn-type ::options)
        :string-w-structure-options (s/tuple ::fn-type ::options ::options)))
(s/def ::fn-specifier
  (s/or :simple-type ::fn-type
        :complex-type ::fn-type-w-map))
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
(s/def ::fn-map-keys #{:default})
(s/def ::fn-map-value
  (s/nilable (s/map-of (s/or :specific-function-name string?
                             :generic-function-configuration ::fn-map-keys)
                       ::fn-specifier)))
(s/def ::number-or-vector-of-numbers
  (s/or :length number?
        :length-by-depth (s/coll-of number? :kind vector?)))
(s/def ::indent-only-style-value #{:input-hang :none})
(s/def ::inline-align-style-value #{:consecutive :aligned :none})

;;
;; # Leaf map keys
;;

(s/def ::binding? ::boolean)
(s/def ::cache-dir (s/nilable string?))
(s/def ::cache-path (s/nilable string?)); debugging only
(s/def ::cache-secs ::nilable-number)
(s/def ::comma? ::boolean)
(s/def ::constant-pair? ::boolean)
(s/def ::constant-pair-min number?)
(s/def ::constant-pair-fn (s/nilable fn?))
(s/def ::count? ::boolean)
(s/def ::directory (s/nilable string?))
(s/def ::docstring? ::boolean)
(s/def ::elide (s/nilable string?))
(s/def ::end (s/nilable number?))
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
(s/def ::hang-accept? ::nilable-number)
(s/def ::ha-depth-factor number?)
(s/def ::ha-width-factor number?)
(s/def ::hang-size number?)
(s/def ::hang-type-flow number?)
(s/def ::hex? ::boolean)
(s/def ::indent number?)
(s/def ::indent-arg ::nilable-number)
(s/def ::indent-only? ::boolean)
(s/def ::indent-only-style ::indent-only-style-value)
(s/def ::inline-align-style ::inline-align-style-value)
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
(s/def ::location (s/nilable string?))
(s/def ::modifiers (s/nilable (s/coll-of string? :kind set?)))
(s/def ::nl-separator? ::boolean)
(s/def ::nl-separator-all? ::boolean)
(s/def ::object? ::boolean)
(s/def ::pair-hang? ::boolean)
(s/def ::parallel?
  #?(:clj ::boolean
     :cljs false?))
(s/def ::path (s/coll-of number? :kind sequential?))
(s/def ::paths ::path-seq)
(s/def ::range (only-keys :opt-un [::start ::end]))
(s/def ::replacement-string (s/nilable string?))
(s/def ::return-altered-zipper vector?)
(s/def ::surround (s/nilable (s/coll-of number? :kind sequential?)))
(s/def ::option-fn-first (s/nilable fn?))
(s/def ::option-fn (s/nilable fn?))
(s/def ::fn-format (s/nilable ::fn-type))
(s/def ::record-type? ::boolean)
(s/def ::respect-nl? ::boolean)
(s/def ::respect-bl? ::boolean)
(s/def ::size number?)
(s/def ::sort? ::boolean)
(s/def ::sort-in-code? ::boolean)
(s/def ::start (s/nilable number?))
(s/def ::lift-ns? ::boolean)
(s/def ::unlift-ns? ::boolean)
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
(s/def ::binding
  (only-keys :opt-un [::flow? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::hang-accept ::ha-depth-factor ::ha-width-factor ::indent
                      ::justify? ::justify-hang ::justify-tuning ::nl-separator?
                      ::nl-separator-all?]))
(s/def ::cache (only-keys :opt-un [::directory ::location]))
(s/def ::color-map
  (only-keys :opt-un [::brace ::bracket ::char ::comma ::comment ::deref ::false
                      ::fn ::hash-brace ::hash-paren ::keyword ::nil ::none
                      ::number ::paren ::quote ::regex ::string ::symbol
                      ::syntax-quote ::syntax-quote-paren ::true ::uneval
                      ::unquote ::unquote-splicing ::user-fn]))
(s/def :alt/comment
  (only-keys :opt-un [::count? ::wrap? ::inline? ::inline-align-style]))
(s/def ::color? ::boolean)
(s/def ::configured? ::boolean)
(s/def ::cwd-zprintrc? ::boolean)
(s/def ::search-config? ::boolean)
(s/def ::dbg? ::boolean)
(s/def ::force-eol-blanks? ::boolean)
(s/def ::test-for-eol-blanks? ::boolean)
(s/def ::dbg-local? ::boolean)
(s/def ::dbg-print? ::boolean)
(s/def ::dbg-ge zany?)

(s/def ::dbg-bug? ::boolean)


(s/def ::delay (only-keys :opt-un [::object?]))
(s/def ::drop? ::boolean)
(s/def ::do-in-hang? ::boolean)
(s/def ::extend
  (only-keys :opt-un [::flow? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::hang-accept ::ha-depth-factor ::ha-width-factor ::indent
                      ::modifiers ::nl-separator?]))
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
(s/def ::input (only-keys :opt-un [::range]))
; When you modify list, you are also modifying vector-fn (see below)
(s/def ::list
  (only-keys
    :opt-un [::constant-pair-fn ::constant-pair-min ::constant-pair? ::hang-diff
             ::hang-avoid ::hang-expand ::hang-size ::hang? ::indent
             ::hang-accept ::ha-depth-factor ::ha-width-factor ::indent-arg
             ::pair-hang? ::return-altered-zipper ::respect-bl? ::respect-nl?
             ::indent-only? ::indent-only-style ::replacement-string]))
; vector-fn needs to accept exactly the same things as list
(s/def ::vector-fn ::list)
(s/def ::map
  (only-keys
    :opt-un [::comma? ::flow? ::force-nl? ::hang-adjust ::hang-diff
             ::hang-accept ::ha-depth-factor ::ha-width-factor ::hang-expand
             ::hang? ::indent ::indent-only? ::justify? ::justify-hang
             ::justify-tuning ::key-color ::key-value-color ::key-depth-color
             ::key-ignore ::key-ignore-silent ::key-order ::lift-ns?
             ::lift-ns-in-code? ::nl-separator? ::nl-separator-all?
             ::respect-bl? ::respect-nl? ::sort-in-code? ::sort? ::unlift-ns?]))
(s/def ::max-depth number?)
(s/def ::max-depth-string string?)
(s/def ::max-hang-count number?)
(s/def ::max-hang-depth number?)
(s/def ::max-hang-span number?)
(s/def ::max-length ::number-or-vector-of-numbers)
(s/def ::object (only-keys :opt-un [::indent ::wrap-coll? ::wrap-after-multi?]))
(s/def ::old? ::boolean)
(s/def ::more-options (s/nilable ::options))
(s/def ::output (only-keys :opt-un [::focus ::lines ::elide ::paths]))
(s/def ::pair
  (only-keys :opt-un [::flow? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::hang-accept ::ha-depth-factor ::ha-width-factor ::indent
                      ::justify? ::justify-hang ::justify-tuning ::nl-separator?
                      ::nl-separator-all?]))
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
(s/def ::next-inner (s/nilable ::options))
(s/def ::return-cvec? ::boolean)
(s/def ::script (only-keys :opt-un [::more-options]))
(s/def ::set
  (only-keys :opt-un [::indent ::indent-only? ::respect-bl? ::respect-nl?
                      ::sort? ::sort-in-code? ::wrap-after-multi? ::wrap-coll?
                      ::wrap?]))
(s/def ::spaces? ::boolean)
(s/def ::spec (only-keys :opt-un [::docstring? ::value]))
(s/def ::style ::style-value)
(s/def ::styles-applied (s/nilable ::vec-or-list-of-keyword))
(s/def ::style-map (s/nilable (s/map-of keyword? ::options)))
(s/def ::tab (only-keys :opt-un [::expand? ::size]))
(s/def ::trim-comments? ::boolean)
(s/def ::tuning
  (only-keys :opt-un [::hang-flow ::hang-type-flow ::hang-flow-limit
                      ::general-hang-adjust ::hang-if-equal-flow?]))
(s/def :alt/uneval (only-keys :opt-un [::color-map]))
(s/def ::user-fn-map ::fn-map-value)
(s/def ::vector
  (only-keys :opt-un [::indent ::binding? ::respect-bl? ::respect-nl?
                      ::option-fn-first ::option-fn ::fn-format
                      ::wrap-after-multi? ::wrap-coll? ::wrap? ::indent-only?]))
(s/def ::version string?)
(s/def ::width number?)
(s/def ::url (only-keys :opt-un [::cache-dir ::cache-path ::cache-secs]))
(s/def ::zipper? ::boolean)

;;
;; # Top level options map
;;

(s/def ::options
  (only-keys
    :opt-un
      [::agent ::array ::atom ::binding ::cache ::color? ::color-map
       :alt/comment ::configured? ::dbg? ::dbg-local? ::cwd-zprintrc? ::dbg-bug?
       ::dbg-print? ::dbg-ge ::delay ::do-in-hang? ::drop? ::extend ::file?
       ::fn-force-nl ::fn-gt2-force-nl ::fn-gt3-force-nl ::fn-map ::fn-name
       ::fn-obj ::force-eol-blanks? ::format ::future ::indent ::input ::list
       ::map ::max-depth ::max-depth-string ::max-hang-count ::max-hang-depth
       ::max-hang-span ::max-length ::object ::old? ::output ::pair ::pair-fn
       ::parallel? ::parse ::parse-string-all? ::parse-string? ::perf-vs-format
       ::process-bang-zprint? ::promise ::reader-cond ::record ::remove
       ::next-inner ::return-cvec? ::search-config? ::set ::spaces? ::script
       ::spec ::style ::styles-applied ::style-map ::tab ::test-for-eol-blanks?
       ::trim-comments? ::tuning :alt/uneval ::user-fn-map ::vector ::vector-fn
       ::version ::width ::url ::zipper?]))

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
    "zprint.spec/ansi-codes?" "ansi-codes"
    "zprint.spec/zboolean?" "boolean"
    "clojure.core/set?" "set"
    "clojure.core/sequential?" "sequential"
    "clojure.core/number?" "number"
    "clojure.core/map?" "map"
    "clojure.core/keyword?" "keyword"
    "cljs.core/set?" "set"
    "cljs.core/sequential?" "sequential"
    "cljs.core/number?" "number"
    "cljs.core/map?" "map"
    "cljs.core/keyword?" "keyword"
    "map?" "map"
    "string?" "string"
    pred))

(defn phrase-problem-str
  "Take a single problem and turn it into a phrase."
  [problem last?]
  (cond (clojure.string/ends-with? (str (:pred problem)) "?")
          (str (ks-phrase
                 (if last? (assoc problem :in [(last (:in problem))]) problem))
               " was not a " (map-pred (str (:pred problem))))
        (set? (:pred problem))
          (if (< (count (:pred problem)) 10)
            (str (ks-phrase problem) " was not one of " (:pred problem))
            (str (ks-phrase problem) " was not recognized as valid!"))
        :else (str "what?")))

(defn lower-first
  "Lowercase the first character of a string."
  [s]
  (when s (str (clojure.string/lower-case (subs s 0 1)) (subs s 1))))

(defn explain-more
  "Try to do a better job of explaining spec problems. This is a totally
  heuristic hack to try to extract useful information from spec problems."
  [explain-data-return]
  (when explain-data-return
    (let [problem-list (#?(:clj :clojure.spec.alpha/problems
                           :cljs :cljs.spec.alpha/problems)
                        explain-data-return)
          problem-list (remove #(= "nil?" (str (:pred %))) problem-list)
          val-map (group-by :val problem-list)
          #_(println "val-map:\n" (zprint.core/czprint-str val-map))
          key-via-len-seq
            (map (fn [[k v]] [k (apply min (map (comp count :via) v))]) val-map)
          #_(println "key-via-len-seq:\n" (zprint.core/czprint-str
                                            key-via-len-seq))
          [key-choice min-via] (first (sort-by second key-via-len-seq))
          #_(println "key-choice:\n" (zprint.core/czprint-str key-choice))
          #_(println "min-via:\n" (zprint.core/czprint-str min-via))
          problem (first (filter (comp (partial = min-via) count :via)
                           (val-map key-choice)))
          #_(println "problem1:\n" (zprint.core/czprint-str problem))
          [key-choice2 min-via2] (second (sort-by second key-via-len-seq))
          problem2 (first (filter (comp (partial = min-via2) count :via)
                            (val-map key-choice2)))
          #_(println "problem2:\n" (zprint.core/czprint-str problem2))
          problem-str (phrase-problem-str problem nil)
          problem-str (if (re-find #"valid" problem-str)
                        (let [problem-str-2 (phrase-problem-str problem2 :last)]
                          (if (re-find #"was not a" problem-str-2)
                            (str problem-str
                                 " because " (lower-first (phrase-problem-str
                                                            problem2
                                                            :last)))
                            problem-str))
                        problem-str)]
      problem-str)))

(defn coerce-to-boolean
  "Examine an options map prior to validation and if :coerce-to-false
  appears as a key, scan the map for keys which are a keyword with
  zprint.spec/:boolean as their spec, and if any are found, if their
  values are boolean, do not change them.  If their values are not
  boolean, replace those whose values are equal to the value of
  :coerce-to-false with false, and all others (that are found) with
  true.  Return the modified map without :coerce-to-false.  If there
  are any problems with this transformation, return the unmodified
  map."
  [options]
  (let [coerce-to-false (when (map? options) (:coerce-to-false options))]
    (if-not coerce-to-false
      options
      (try
        (dissoc (prewalk #(if (and (map-entry? %)
                                   (keyword? (first %))
                                   (= (s/get-spec (keyword "zprint.spec"
                                                             (name (first %))))
                                      :zprint.spec/boolean))
                            ; This is a keyword whose spec is
                            ; boolean.  If it is boolean, we're good.
                            ; If it isn't, then figure out if it is the
                            ; same as coerce-to-false, in which case it
                            ; will be false, otherwise change it to true.
                            (if (zboolean? (second %))
                              ; Don't change anything
                              (first {(first %) (second %)})
                              ; Is it equal to coerce-to-false?
                              (if (= (second %) coerce-to-false)
                                ; Make it false
                                (first {(first %) false})
                                ; Make it true
                                (first {(first %) true})))
                            %)
                         options)
                :coerce-to-false)
        (catch #?(:clj Exception
                  :cljs :default)
          e
          options)))))

(defn validate-basic
  "Using spec defined above, validate the given options map.  Return
  nil if no errors, or a string containing errors if any. If :coerce-to-false
  appears as a key, scan the map for keys which are keyword with 
  zprint.spec/:boolean as their spec, and if any are found replace their
  values with the value of :coerce-to-false."
  ([options source-str]
   #_(println "Options:" options)
   (try (if (s/valid? ::options options)
          nil
          (if source-str
            (str "In " source-str
                 ", " (explain-more (s/explain-data ::options options)))
            (explain-more (s/explain-data ::options options))))
        (catch #?(:clj Exception
                  :cljs :default)
          e
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

; Useful for debugging, tests will not run with this defined
#_(defn explain
    "Take an options map and explain the result of the spec.  This is
  really only here for testing purposes."
    ([options show-problems?]
     (let [problems (s/explain-data ::options options)]
       (when show-problems? (zprint.core/czprint problems))
       (explain-more problems)))
    ([options] (explain options nil)))
