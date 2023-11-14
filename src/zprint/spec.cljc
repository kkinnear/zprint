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
(s/def ::left ::color)
(s/def ::right ::color)

;;
;; # Fundamental values
;;

(s/def ::boolean (s/nilable zboolean?))
;(s/def ::boolean booleanable?)

; Note that actual fn-types can be [:arg1 {:style :respect-nl}] in
; addition
; to simple keywords.  It used to be that these things were ripped apart
; during option map validation and done separately. Now we get spec to do
; them for us!

(s/def ::fn-type
  #{:binding :binding-vector :arg1 :arg1-body :arg1-pair-body :arg1-pair :pair
    :hang :extend :arg1-extend :fn :arg1-> :noarg1-body :noarg1 :arg2
    :arg2-extend :arg2-pair :arg2-fn :none :none-body :arg1-force-nl
    :gt2-force-nl :gt3-force-nl :flow :flow-body :force-nl-body :force-nl
    :pair-fn :arg1-mixin :arg2-mixin :indent :replace-w-string :guided
    :arg1-force-nl-body :arg2-extend-body :wrap :guided-body :arg2-force-nl-body
    :arg2-force-nl :arg1-extend-body :list})
(s/def ::fn-type-w-map
  (s/or :general-options (s/tuple ::fn-type ::options)
        :string-w-structure-options (s/tuple ::fn-type ::options ::options)))
; This dance with making the :fn-alias really "deep" is because of the
; heuristics in explain-more, which tends to use the "simplest" problem
; that is found.  And the string tended to be the simplest problem, so
; that
; more important things were obscured.
(s/def ::fn-deep-alias string?)
(s/def ::fn-alias
  (s/or :string ::fn-deep-alias
        :also-string ::fn-deep-alias))
(s/def ::fn-specifier
  (s/or :simple-type ::fn-type
        :alias-type ::fn-alias
        :complex-type ::fn-type-w-map))
(s/def ::fn-type-specifier
  (s/nilable (s/or :simple-type ::fn-type
                   :complex-type ::fn-type-w-map)))
(s/def ::format-value #{:on :off :next :skip})
(s/def :alt/format-value #{:string :hiccup :html})
(s/def ::nilable-number (s/nilable number?))

(s/def ::individual-style
  (s/or :style-name keyword?
        :configured-style map?))

(s/def ::vec-or-list-of-keyword-or-map
  (s/coll-of ::individual-style :kind vector?))

(s/def ::style-value
  (s/or :single-style ::individual-style
        :multiple-styles ::vec-or-list-of-keyword-or-map))
; Also used for :map {:no-sort ::ignore-args}
(s/def ::ignore-args
  (s/or :string string?
        :regex s/regex?))
(s/def ::constant
  (s/or :string string?
        :number number?
        :keyword keyword?))
(s/def ::constant-seq (s/coll-of ::constant :kind sequential?))
(s/def ::call-stack-frame
  map?
  #_(s/or :basic-frame (s/tuple string? ::fn-specifier)
          :frame-w-data (s/tuple string? ::fn-specifier map?)))
(s/def ::line-seq
  (s/nilable (s/coll-of (s/or :number number?
                              :range (s/coll-of number? :kind sequential?))
                        :kind sequential?)))
(s/def ::guide-seq
  (s/nilable (s/coll-of (s/or :number number?
                              :keyword keyword?
                              :options ::options
                              :embedded-guide ::guide-seq))))
(s/def ::guide-debug-seq (s/tuple keyword? number? ::guide-seq))
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
; A :keyword is a fn, so it is already allowed
(s/def ::fn-map-keys
  #{:default :default-not-none :list :map :vector :set :quote})
(s/def ::fn-map-value
  (s/nilable (s/map-of (s/or :specific-function-name string?
                             :generic-function-configuration ::fn-map-keys)
                       ::fn-specifier)))
; This will not let people define their own fn-types, just modify the
; built-in ones.
(s/def ::fn-type-map-value (s/nilable (s/map-of ::fn-type ::fn-type-specifier)))
(s/def ::number-or-vector-of-numbers
  (s/or :length number?
        :length-by-depth (s/coll-of number? :kind vector?)))
(s/def ::vector-of-keywords (s/coll-of keyword? :kind sequential?))
(s/def ::vector-of-vector-of-keywords
  (s/coll-of ::vector-of-keywords :kind vector?))
(s/def ::indent-only-style-value #{:input-hang :none})
(s/def ::inline-align-style-value #{:consecutive :aligned :none})

;;
;; # Leaf map keys
;;

(s/def ::alt? ::boolean)
(s/def ::binding? ::boolean)
(s/def ::border number?)
(s/def ::cache-dir (s/nilable string?))
(s/def ::cache-path (s/nilable string?)); debugging only
(s/def ::cache-secs ::nilable-number)
(s/def ::comma? ::boolean)
(s/def ::constant-pair? ::boolean)
(s/def ::constant-pair-min number?)
(s/def ::constant-pair-fn (s/nilable fn?))
(s/def ::continue-after-!zprint-error? ::boolean)
(s/def ::count? ::boolean)
(s/def ::directory (s/nilable string?))
(s/def ::docstring? ::boolean)
(s/def ::dbg-s-set (s/nilable (s/coll-of keyword? :kind set?)))
(s/def ::elide (s/nilable string?))
(s/def ::end (s/nilable number?))
(s/def ::end+start-cg (s/nilable (s/coll-of zany? :kind vector?)))
(s/def ::end+skip-cg (s/nilable (s/coll-of zany? :kind vector?)))
(s/def ::end-cg (s/nilable (s/coll-of zany? :kind vector?)))
(s/def ::expand? ::boolean)
(s/def ::flow? ::boolean)
(s/def ::flow-all-if-any? ::boolean)
(s/def ::focus (only-keys :opt-un [::zloc? ::path ::surround]))
(s/def ::force-validate? ::boolean)
(s/def ::force-nl? ::boolean)
(s/def ::one-line-ok? ::boolean)
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
(s/def ::justify
  (only-keys :opt-un [::max-variance ::ignore-for-variance ::no-justify
                      ::max-gap ::lhs-narrow ::multi-lhs-overlap? ::max-depth]))
(s/def ::justify-hang (only-keys :opt-un [::hang? ::hang-expand ::hang-diff]))
; You can put any options map you want into justify-tuning.  Typically,
; you would only do some kind of tuning, but that isn't a limitation
; on the silly things you might try to do.
(s/def ::justify-tuning (s/nilable ::options))

(s/def ::key-color (s/nilable (s/map-of zany? ::color)))
(s/def ::key-value-color (s/nilable (s/map-of zany? ::color-map)))
(s/def ::key-value-options (s/nilable (s/map-of zany? ::options)))
(s/def ::key-depth-color ::key-color-value)
(s/def ::key-ignore (s/nilable ::key-or-ks-seq))
(s/def ::key-ignore-silent (s/nilable ::key-or-ks-seq))
(s/def ::key-order (s/nilable ::key-value))
(s/def ::last-max number?)
(s/def ::space-factor number?)
(s/def ::left-space ::keep-or-drop)
(s/def ::lhs-narrow (s/nilable number?))
(s/def ::lines ::line-seq)
(s/def ::location (s/nilable string?))
(s/def ::max-variance number?)
(s/def ::max-gap (s/nilable number?))
#_(s/def ::memoize? (s/nilable ::boolean))
(s/def ::min-space-after-semi number?)
(s/def ::modifiers (s/nilable (s/coll-of string? :kind set?)))
(s/def ::multi-lhs-hang? ::boolean)
(s/def ::multi-lhs-overlap? ::boolean)
(s/def ::no-validate? ::boolean)
(s/def ::nl-separator? ::boolean)
(s/def ::nl-separator-all? ::boolean)
(s/def ::nl-count (s/nilable ::number-or-vector-of-numbers))
(s/def ::object? ::boolean)
(s/def ::pair-hang? ::boolean)
(s/def ::parallel?
  #?(:clj ::boolean
     :cljs false?))
(s/def ::path (s/coll-of number? :kind sequential?))
(s/def ::paths ::path-seq)
(s/def ::range
  (only-keys :opt-un [::start ::end ::use-previous-!zprint?
                      ::continue-after-!zprint-error?]))
(s/def ::range? ::boolean)
(s/def ::ignore-if-parse-fails (s/nilable (s/coll-of ::ignore-args :kind set?)))
(s/def ::key-no-sort (s/nilable (s/coll-of ::ignore-args :kind set?)))
(s/def ::replacement-string (s/nilable string?))
(s/def ::return-altered-zipper (s/nilable vector?))
(s/def ::new-zloc zany?)
(s/def ::new-l-str string?)
(s/def ::new-r-str string?)
(s/def ::surround (s/nilable (s/coll-of number? :kind sequential?)))
(s/def ::smart-wrap
  (only-keys :opt-un [::border ::end+start-cg ::end+skip-cg ::max-variance
                      ::last-max ::space-factor ::end-cg]))
(s/def ::option-fn-first (s/nilable fn?))
(s/def ::option-fn (s/nilable fn?))
(s/def ::fn-format (s/nilable ::fn-type))
(s/def ::fn-style
  (s/nilable (s/or :fn-type ::fn-type
                   :string string?)))
(s/def ::fn-str (s/nilable string?))
(s/def ::real-le? ::boolean)
(s/def ::real-le-length number?)
(s/def ::record-type? ::boolean)
(s/def ::respect-nl? ::boolean)
(s/def ::respect-bl? ::boolean)
(s/def ::size number?)
(s/def ::sort? ::boolean)
(s/def ::sort-in-code? ::boolean)
(s/def ::start (s/nilable number?))
(s/def :alt/style (s/nilable string?))
(s/def ::lift-ns? ::boolean)
(s/def ::unlift-ns? ::boolean)
(s/def ::lift-ns-in-code? ::boolean)
(s/def ::to-string? ::boolean)
(s/def ::? ::boolean)
(s/def ::use-previous-!zprint? ::boolean)
(s/def ::value zany?)
(s/def ::wrap? ::boolean)
(s/def ::smart-wrap? ::boolean)
(s/def ::wrap-after-multi? ::boolean)
(s/def ::wrap-coll? ::boolean)
(s/def ::wrap-multi? ::boolean)
(s/def ::zloc? ::boolean)
(s/def ::!zprint-elide-skip-next? ::boolean)

;;
;; # Elements of the top level options map
;;

(s/def ::agent (only-keys :opt-un [::object?]))
(s/def ::array (only-keys :opt-un [::hex? ::indent ::object? ::wrap?]))
(s/def ::atom (only-keys :opt-un [::object?]))
(s/def ::binding
  (only-keys :opt-un [::flow? ::flow-all-if-any? ::force-nl? ::hang-diff
                      ::hang-expand ::hang? ::hang-accept ::ha-depth-factor
                      ::ha-width-factor ::indent ::justify? ::justify
                      ::justify-hang ::justify-tuning ::multi-lhs-hang?
                      ::nl-separator? ::nl-separator-all? :alt/tuning]))
(s/def ::cache (only-keys :opt-un [::directory ::location]))
(s/def ::call-stack (s/nilable (s/coll-of ::call-stack-frame :kind list?)))
(s/def ::color-map
  (only-keys :opt-un [::brace ::bracket ::char ::comma ::comment ::deref ::false
                      ::fn ::hash-brace ::hash-paren ::keyword ::nil ::none
                      ::number ::paren ::quote ::regex ::string ::symbol
                      ::syntax-quote ::syntax-quote-paren ::true ::uneval
                      ::unquote ::unquote-splicing ::user-fn ::left ::right]))
(s/def :alt/comment
  (only-keys :opt-un [::count? ::wrap? ::inline? ::inline-align-style
                      ::smart-wrap? ::smart-wrap ::border
		      ::min-space-after-semi]))
(s/def ::color? ::boolean)
(s/def ::configured? ::boolean)
(s/def ::cwd-zprintrc? ::boolean)
(s/def ::doc string?)
(s/def ::search-config? ::boolean)

(s/def ::dbg?
  (s/or :boolean ::boolean
        :set set?
        :keyword keyword?))

(s/def ::dbg-s ::dbg-s-set)
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
                      ::modifiers ::nl-separator? ::nl-count :alt/tuning]))
(s/def :alt/extend (only-keys :opt-un [::modifiers]))
(s/def ::file? ::boolean)
(s/def ::fn-force-nl (s/nilable (s/coll-of ::fn-type :kind set?)))
(s/def ::fn-gt2-force-nl (s/nilable (s/coll-of ::fn-type :kind set?)))
(s/def ::fn-gt3-force-nl (s/nilable (s/coll-of ::fn-type :kind set?)))
(s/def ::fn-map ::fn-map-value)
(s/def ::fn-type-map ::fn-type-map-value)
(s/def ::fn-name zany?)
(s/def ::fn-obj (only-keys :opt-un [::object?]))
(s/def ::format ::format-value)
(s/def :alt/format :alt/format-value)
(s/def ::future (only-keys :opt-un [::object?]))
(s/def ::ignore-for-variance (s/nilable (s/coll-of string? :kind set?)))
(s/def ::indent number?)
(s/def ::input (only-keys :opt-un [::range]))
; When you modify list, you are also modifying vector-fn (see below)
(s/def ::list
  (only-keys :opt-un [::constant-pair-fn ::constant-pair-min ::constant-pair?
                      ::hang-diff ::hang-avoid ::hang-expand ::hang-size ::hang?
                      ::indent ::hang-accept ::ha-depth-factor ::ha-width-factor
                      ::indent-arg ::option-fn ::pair-hang?
                      ::return-altered-zipper ::respect-bl? ::respect-nl?
                      ::indent-only? ::indent-only-style ::replacement-string
                      ::wrap-coll? ::wrap-after-multi? ::wrap-multi? ::force-nl?
                      ::wrap? ::nl-count ::no-wrap-after :alt/tuning
                      ::nl-separator?]))
; vector-fn needs to accept exactly the same things as list
(s/def ::vector-fn ::list)
(s/def ::map
  (only-keys :opt-un [::comma? ::flow? ::flow-all-if-any? ::force-nl?
                      ::hang-adjust ::hang-diff ::hang-accept ::ha-depth-factor
                      ::ha-width-factor ::hang-expand ::hang? ::indent
                      ::indent-only? ::justify? ::justify-hang ::justify
                      ::justify-tuning ::key-color ::key-value-color
                      ::key-depth-color ::key-ignore ::key-ignore-silent
                      ::key-order ::lift-ns? ::lift-ns-in-code? ::key-no-sort
                      ::multi-lhs-hang? ::nl-separator? ::nl-separator-all?
                      ::respect-bl? ::respect-nl? ::sort-in-code? ::sort?
                      ::unlift-ns? ::key-value-options :alt/tuning]))
(s/def ::max-depth number?)
(s/def ::max-depth-string string?)
(s/def ::max-hang-count number?)
(s/def ::max-hang-depth number?)
(s/def ::max-hang-span number?)
(s/def ::max-length ::number-or-vector-of-numbers)
(s/def ::meta (only-keys :opt-un [::split?]))

(s/def ::modify-sexpr-value (s/tuple (s/nilable fn?) (s/nilable ::options)))
(s/def ::modify-sexpr-by-type 
  (s/nilable (s/map-of string? ::modify-sexpr-value)))

#_(s/def ::modify-sexpr-by-type (s/nilable map?))

(s/def ::no-justify (s/nilable (s/coll-of string? :kind set?)))
(s/def ::no-wrap-after (s/nilable (s/coll-of string? :kind set?)))
(s/def ::object (only-keys :opt-un [::indent ::wrap-coll? ::wrap-after-multi?]))
(s/def ::option-fn-map map?)
(s/def ::old? ::boolean)
(s/def ::guide ::guide-seq)
(s/def ::guide-debug ::guide-debug-seq)
(s/def ::more-options (s/nilable ::options))
(s/def ::output
  (only-keys :opt-un [::focus ::lines ::elide ::paths ::real-le?
                      ::real-le-length ::range? :alt/format ::paragraph]))
(s/def ::pair
  (only-keys :opt-un [::flow? ::flow-all-if-any? ::force-nl? ::hang-diff
                      ::hang-expand ::hang? ::hang-accept ::ha-depth-factor
                      ::ha-width-factor ::indent ::justify? ::justify
                      ::justify-hang ::justify-tuning ::multi-lhs-hang?
                      ::nl-separator? ::nl-separator-all? :alt/tuning]))
(s/def ::paragraph (only-keys :opt-un [:alt/style]))
(s/def ::pair-fn
  (only-keys :opt-un [::hang-diff ::hang-expand ::hang-size ::hang?
                      :alt/tuning]))
(s/def ::parse
  (only-keys :opt-un [::interpose ::left-space ::ignore-if-parse-fails]))
(s/def ::parse-string-all? ::boolean)
(s/def ::parse-string? ::boolean)
(s/def ::perf-vs-format ::nilable-number)
(s/def ::process-bang-zprint? ::boolean)
(s/def ::promise (only-keys :opt-un [::object?]))
(s/def ::reader-cond
  (only-keys :opt-un [::comma? ::force-nl? ::hang-diff ::hang-expand ::hang?
                      ::indent ::key-order ::sort-in-code? ::sort?
                      :alt/tuning]))
(s/def ::tagged-literal
  (only-keys :opt-un [::hang-diff ::hang-expand ::hang? ::indent :alt/tuning]))
(s/def ::record (only-keys :opt-un [::hang? ::record-type? ::to-string?]))
(s/def ::remove
  (only-keys :opt-un [::fn-force-nl ::fn-gt2-force-nl ::fn-gt3-force-nl
                      :alt/extend ::binding ::pair ::map ::parse ::vector]))

(s/def ::remove-final-keys (s/nilable ::vector-of-vector-of-keywords))

(s/def ::next-inner (s/nilable ::options))

(s/def ::set-elements
  (s/or :string string?
        :fn-type ::fn-type
        :ignore-args ::ignore-args))

(s/def ::next-inner-restore
  (s/coll-of (s/or :set-value (s/tuple ::vector-of-keywords ::set-elements)
                   :key-sequence ::vector-of-keywords)
             :kind sequential?))

(s/def ::return-cvec? ::boolean)
(s/def ::script (only-keys :opt-un [::more-options]))
(s/def ::set
  (only-keys :opt-un [::indent ::indent-only? ::respect-bl? ::respect-nl?
                      ::sort? ::sort-in-code? ::wrap-after-multi? ::wrap-coll?
                      ::wrap? ::no-wrap-after]))
(s/def ::spaces? ::boolean)
(s/def ::spec (only-keys :opt-un [::docstring? ::value]))
(s/def ::split? ::boolean)
(s/def ::style (s/nilable ::style-value))
(s/def ::styles-applied (s/nilable ::vec-or-list-of-keyword-or-map))
(s/def ::style-map (s/nilable (s/map-of keyword? map?)))
(s/def ::tab (only-keys :opt-un [::expand? ::size]))
(s/def ::trim-comments? ::boolean)
(s/def ::tuning
  (only-keys :opt-un [::hang-flow ::hang-type-flow ::hang-flow-limit
                      ::general-hang-adjust ::hang-if-equal-flow?]))
(s/def :alt/tuning
  (only-keys :opt-un [::hang-flow ::hang-type-flow ::hang-flow-limit
                      ::hang-if-equal-flow?]))
(s/def :alt/uneval (only-keys :opt-un [::color-map]))
(s/def ::underscore? ::boolean)
(s/def ::user-fn-map ::fn-map-value)
(s/def ::vector
  (only-keys :opt-un [::indent ::binding? ::respect-bl? ::respect-nl?
                      ::option-fn-first ::option-fn ::fn-format
                      ::wrap-after-multi? ::wrap-multi? ::wrap-coll? ::wrap?
                      ::indent-only? ::hang? ::force-nl? ::no-wrap-after]))
(s/def ::version string?)
(s/def ::width number?)
(s/def ::url (only-keys :opt-un [::cache-dir ::cache-path ::cache-secs]))
(s/def ::zipper? ::boolean)

;;
;; # Top level options map
;;

(s/def ::options
  (only-keys
    :opt-un [::agent ::array ::atom ::binding ::cache ::call-stack ::color?
             ::color-map :alt/comment ::configured? ::dbg? ::dbg-s ::dbg-local?
             ::cwd-zprintrc? ::dbg-bug? ::dbg-print? ::dbg-ge ::delay
             ::do-in-hang? ::drop? ::extend ::file? ::fn-force-nl
             ::fn-gt2-force-nl ::fn-gt3-force-nl ::fn-map ::fn-name ::fn-obj
             ::force-eol-blanks? ::format ::future ::indent ::input ::list ::map
             ::max-depth ::max-depth-string ::max-hang-count ::max-hang-depth
             ::max-hang-span ::max-length ::object ::old? ::output ::pair
             ::pair-fn ::parallel? ::parse ::parse-string-all? ::parse-string?
             ::perf-vs-format ::process-bang-zprint? ::promise ::reader-cond
             ::record ::remove ::next-inner ::return-cvec? ::search-config?
             ::set ::spaces? ::script ::spec ::style ::styles-applied
             ::style-map ::tab ::test-for-eol-blanks? ::trim-comments? ::tuning
             :alt/uneval ::user-fn-map ::vector ::vector-fn ::version ::width
             ::url ::zipper? ::guide ::guide-debug ::no-validate?
             ::force-validate? ::doc ::next-inner-restore ::fn-style
             ::!zprint-elide-skip-next? ::meta ::fn-str ::fn-type-map ::new-zloc
             ::new-l-str ::new-r-str ::option-fn-map ::alt? ::one-line-ok?
             ::tagged-literal #_::memoize? ::remove-final-keys
	     ::modify-sexpr-by-type]))

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
      (try (dissoc (prewalk #(if (and (map-entry? %)
                                      (keyword? (first %))
                                      (= (s/get-spec (keyword "zprint.spec"
                                                                (name (first
                                                                        %))))
                                         :zprint.spec/boolean))
                               ; This is a keyword whose spec is boolean.
                               ; If it is boolean, we're good. If it isn't,
                               ; then figure out if it is the same as
                               ; coerce-to-false, in which case it will be
                               ; false, otherwise change it to true.
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
