;!zprint {:style :require-justify}
(ns ^:no-doc zprint.config
  #?(:clj [:refer-clojure :exclude [read-string]])
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer
               [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
    clojure.string
    [clojure.set    :refer [difference]]
    [clojure.data   :as d]
    [zprint.spec    :refer [validate-basic coerce-to-boolean]]
    [zprint.rewrite :refer [sort-dependencies]]
    [zprint.util :refer [dissoc-two]]
    [zprint.guide   :refer [jrequireguide defprotocolguide signatureguide1
                            odrguide guideguide rodguide areguide]]
    [zprint.optionfn   :refer [rodfn]]
    #?@(:bb []
        ; To completely remove sci, comment out the following line.
        :clj [[sci.core :as sci]]
        :cljs [[sci.core :as sci]])
    #?(:clj [clojure.edn :refer [read-string]]
       :cljs [cljs.reader :refer [read-string]]))
  #?@(:clj [(:import (java.io InputStreamReader FileReader BufferedReader))]))

;;
;; # Configuration
;;
;; Handles incoming configuration, validation of option maps,
;; contains atoms holding the current options map.
;;


;;
;; # Program Version
;;

(defn about "Return version of this program." [] (str "zprint-1.2.4"))

;;
;; # External Configuration
;;
;; Will read this when run standalone, or first time a zprint
;; function is used when used as a library.
;;

(def zprintrc ".zprintrc")
(def zprintedn ".zprint.edn")

;;
;; # Internal Storage
;;
;; Keys that should be used from the cli options map to pass
;; directly on to zprint (and to get validated as well)
;;

(def zprint-keys [:width])

(def operational-options
  [:cache :cwd-zprintrc? :parallel? :search-config? :url])

(def explain-hide-keys
  [:configured? :dbg-print? :dbg? :dbg-s :force-eol-blanks? :do-in-hang? :drop?
   :dbg-ge :file? :spaces? :process-bang-zprint? :trim-comments? :zipper?
   :indent :remove :return-cvec? :test-for-eol-blanks? :!zprint-elide-skip-next?
   :fn-str
   [:object :wrap-after-multi? :wrap-coll?] [:reader-cond :comma? :key-value]
   [:output :elide :lines] [:pair :justify-hang :justify-tuning]
   [:binding :justify-hang :justify-tuning] [:spec :value]
   [:map :dbg-local? :hang-adjust :justify-hang :justify-tuning :key-value]
   :tuning :perf-vs-format [:url :cache-path]
   [:binding :hang-accept :ha-depth-factor :ha-width-factor]
   [:extend :hang-accept :ha-depth-factor :ha-width-factor]
   [:list :hang-accept :ha-depth-factor :ha-width-factor]
   [:map :hang-accept :ha-depth-factor :ha-width-factor]
   [:pair :hang-accept :ha-depth-factor :ha-width-factor]
   [:vector-fn :hang-accept :ha-depth-factor :ha-width-factor]])

;;
;; ## Function style database
;;
;; In all of these cases, if the fn fits one one line, it will
;; go on one line.
;;
;; Choices:
;;
;; :arg1
;;
;; (map #(if (= % :big)
;;         (takes 2 "lines")
;;         (just 1 "line"))
;;   (list 'stuff 'bother))
;;
;; Print the first argument on the same line as
;; the function, if possible.  Later arguments go
;; indented.
;;
;; :arg1-body
;;
;; Print the first argument on the same line as
;; the function, if possible.  Later body arguments go
;; indented.
;;
;; (if (= a 1)
;;   (map inc coll)
;;   (map dec coll))
;;
;; :arg1-mixin
;;
;; Print Rum defc, defcc, and defcs macros in a standard
;; way.  Puts the mixins under the first line, and above the
;; argument vector.  Also allows a docstring.
;;
;; (rum/defcs component
;;   < rum/static
;;     rum/reactive
;;     (rum/local 0 ::count)
;;     (rum/local "" ::text)
;;   [state label]
;;   (let [count-atom (::count state) text-atom (::text state)] [:div]))
;;
;; :binding
;;
;; The function has a binding clause as its first argument.
;; Print the binding clause two-up.
;;
;; (let [a b
;;       c d]
;;   (+ a c))
;;
;; :arg1-pair
;;
;; The function has an important first argument, then the
;; rest of the arguments are paired up.
;;
;; (assoc my-map
;;   :key1 :val1
;;   :key2 :val2)
;;
;; :pair-fn
;;
;; The function has a series of clauses which are paired.
;;
;; (cond
;;   (and (= a 1) (> b 3))
;;     (vector c d e)
;;   (= d 4)
;;     (inc a))
;;
;; :hang
;;
;; The function has a series of arguments where it would be nice
;; to put the first on the same line as the function and then
;; indent the rest to that level.  This would usually always be nice,
;; but we try extra hard for these.
;;
;; (and (= i 1)
;;      (> (inc j) (stuff k)))
;;
;; :extend
;;
;; The sexpression has a series of symbols with one or more forms
;; following each.
;;
;;  (reify
;;    stuff
;;      (bother [] (println))
;;    morestuff
;;      (really [] (print x))
;;      (sure [] (print y))
;;      (more-even [] (print z)))
;;
;; :arg1-extend
;;
;; For the several functions which have a single argument
;; prior to the :extend syntax.  They must have one argument,
;; and if the second argument is a vector, it is also handled
;; separately from the :extend syntax.
;;
;;  (extend-protocol ZprintProtocol
;;    ZprintType
;;      (more-stuff [x] (str x))
;;      (more-bother [y] (list y))
;;      (more-foo [z] (nil? z))))
;;
;;  (deftype ZprintType
;;    [a b c]
;;    ZprintProtocol
;;      (stuff [this x y] a)
;;      (bother [this] b)
;;      (bother [this x] (list x c))
;;      (bother [this x y] (list x y a b)))
;;
;;  (extend ZprintType
;;    ZprintProtocol
;;      {:bar (fn [x y] (list x y)),
;;       :baz (fn ([x] (str x)) ([x y] (list x y)))})
;;
;; :arg1->
;;
;; Print the first argument on the same line as
;; the function, if possible.  Later arguments go
;; indented and :arg1 and :arg-1-pair top level fns
;; are become :none and :pair, respectively.
;;
;; Currently -> is :none-body, however, and there
;; are no :arg1-> functions.
;;
;; (-> opts
;;   (assoc
;;     :stuff (list "and" "bother"))
;;   (dissoc :things))
;;
;; :noarg1-body
;;
;; Print the fn in whatever way is possible without
;; special handling.  However, top level fns become
;; different based on the lack of their first argument.
;; Thus, :arg1 becomes :none, :arg1-pair becomes :pair,
;; etc.
;;
;; (-> opts
;;     (assoc
;;       :stuff (list "and" "bother"))
;;     (dissoc :things))
;;
;; :force-nl-body
;;
;; Ensure that even if it fits on one line, it always does
;; a hang or a flow but doesn't end up on one line..
;;
;; (->> opts
;;      foo
;;      bar
;;      baz)
;;
;; :fn
;;
;; Print the first argument on the same line as the (fn ...) if possible,
;; and if it does, and the second argument is a vector, print it on
;; the same line as the first argument if it fits on one line.
;;
;; (fn [a b c]
;;   (let [d c]
;;     (inc d)))
;;
;; (fn myfunc [a b c]
;;   (let [d c]
;;     (inc d)))
;;
;; :arg2
;;
;; NOTE: This is implemented as a body function, i.e., like it was
;; :arg2-body.
;;
;; Print the first argument on the same line as the function name, and
;; if it fits then print the second argument on the same line as the function
;; name if it fits.
;;
;; (as-> initial-value tag
;;   (process stuff tag bother)
;;   (more-process tag foo bar))
;;
;; :arg2-pair
;;
;; Just like :arg2, but prints the third through last arguments as pairs.
;;
;; (condp = stuff
;;   :bother "bother"
;;   :foo "foo"
;;   :bar "bar"
;;   "baz")
;;
;; :arg2-fn
;;
;; Just like :arg2, but prints the third through last arguments as fn's.
;;
;; (proxy [Classname] []
;;   (stuff [] bother)
;;   (foo [bar] baz))
;;
;; :flow
;;
;; Don't hang under any circumstances.  :flow assume arguments,
;; :flow-body assumes executable things.
;;
;; (foo
;;   (bar a b c)
;;   (baz d e f))
;;
;; :flow-body
;;
;; Don't hang under any circumstances.  :flow assume arguments,
;; :flow-body assumes executable things.
;;
;; (foo
;;   (bar a b c)
;;   (baz d e f))
;;
;; :none
;;
;; This is for things like special forms that need to be in this
;; map to show up as "functions", but don't actually trigger showfn
;; to represent them as such.
;;
;; :wrap
;;
;; Do what we do for vectors for a list -- just wrap to the right margin.
;; This also looks at :wrap? and :wrap-coll? in :list to decide how to
;; process things.
;;
;; (foo (bar a b c) (baz d e f))
;;
;;

(def zfnstyle
  {"->" [:noarg1-body
         {:list {:constant-pair? false},
          :next-inner-restore [[:list :constant-pair?]]}],
   "->>" [:force-nl-body
          {:list {:constant-pair? false},
           :next-inner-restore [[:list :constant-pair?]]}],
   ":import" :force-nl-body,
   ":require" :force-nl-body,
   "=" :hang,
   "alt" :pair-fn,
   "and" :hang,
   "apply" :arg1,
   "are" [:guided {:style :areguide}],
   "as->" :arg2,
   "assert-args" :pair-fn,
   "assoc" :arg1-pair,
   "assoc-in" :arg1,
   "binding" :binding,
   "case" :arg1-pair-body,
   "cat" :force-nl,
   "catch" :arg2,
   "comment" :flow-body,
   "cond" :pair-fn,
   "cond-let" :pair-fn,
   "cond->" :arg1-pair-body,
   "condp" :arg2-pair,
   "def" :arg1-body,
   "defc" :arg1-mixin,
   "defcc" :arg1-mixin,
   "defcs" :arg1-mixin,
   "defmacro" :arg1-body,
   "defexpect" [:arg1-body
                {; This replicates :style :respect-nl, but
                 ; is done explicitly here in case that style
                 ; changes, so that the :next-inner-restore will
                 ; still be accurate
                 :list {:respect-nl? true},
                 :map {:respect-nl? true},
                 :vector {:respect-nl? true},
                 :set {:respect-nl? true},
                 :next-inner-restore [[:list :respect-nl?] [:map :respect-nl?]
                                      [:vector :respect-nl?]
                                      [:set :respect-nl?]]}],
   "defmethod" :arg2,
   "defmulti" :arg1-body,
   "defn" :arg1-body,
   "defn-" :arg1-body,
   "defproject" [:arg2-pair {:vector {:wrap? false}}],
   "defprotocol" [:guided-body
                  {:style :defprotocolguide,
                   :fn-map {:default-not-none [:none {:style :signature1}]}}],
   "defrecord" :arg2-extend-body,
   "deftest" :arg1-body,
   "deftype" :arg2-extend-body,
   "defui" :arg1-extend,
   "do" :none-body,
   "doseq" :binding,
   "dotimes" :binding,
   "doto" :arg1,
   "extend" :arg1-extend,
   "extend-protocol" :arg1-extend,
   "extend-type" :arg1-extend,
   "fdef" :arg1-force-nl,
   "filter" :arg1,
   "filterv" :arg1,
   "fn" :fn,
   "fn*" :fn,
   "for" :binding,
   "if" :arg1-body,
   "if-let" :binding,
   "if-not" :arg1-body,
   "if-some" :binding,
   "interpose" :arg1,
   "let" :binding,
   "letfn" [:guided-body
            {:guide [:element :options {:next-inner {:fn-style :fn}}
                     :element-best :options-reset :newline :element-best-*]}],
   "loop" :binding,
   "map" :arg1,
   "mapcat" :arg1,
   "match" :arg1-pair-body,
   "matchm" :arg1-pair-body,
   "mapv" :arg1,
   "not=" :hang,
   "ns" :arg1-body,
   "or" :hang,
   "proxy" :arg2-fn,
   "reduce" :arg1,
   "reify" :extend,
   "remove" :arg1,
   "reset!" :arg2,
   "s/def" [:arg1-body {:list {:constant-pair-min 2}}],
   "s/fdef" [:arg1-body {:list {:constant-pair-min 2}}],
   "s/and" :gt2-force-nl,
   "s/or" :gt2-force-nl,
   "some->" :force-nl-body,
   "some->>" :force-nl-body,
   "swap!" :arg2,
   "try" :none-body,
   "when" :arg1-body,
   "when-first" :binding,
   "when-let" :binding,
   "when-not" :arg1-body,
   "when-some" :binding,
   "with-bindings" :arg1,
   "with-bindings*" :arg1,
   "with-local-vars" :binding,
   "with-meta" :arg1-body,
   "with-open" :binding,
   "with-out-str" :none-body,
   "with-redefs" :binding,
   "with-redefs-fn" :arg1-body,
   :quote [:none
           {:list {:hang? false, :indent 1},
            ; This probably isn't going to make any difference, as
            ; quote? sticks around for a good long time.
            :next-inner-restore [[:list :hang?] [:list :indent]]}]})

;;
;; ## The global defaults
;;

(def default-zprint-options
  {:agent {:object? false},
   :array {:hex? false, :indent 1, :object? false, :wrap? true},
   :atom {:object? false},
   :binding {:flow? false,
	  :flow-all-if-any? false
             :force-nl? false,
             :hang-diff 1,
             :hang-expand 2.0,
             :hang-accept nil,
             :ha-depth-factor 0,
             :ha-width-factor 0,
             :hang? true,
             :indent 2,
             :justify-hang {:hang-expand 5},
             :justify-tuning {:hang-flow 4, :hang-flow-limit 30},
             :justify
               {:max-variance 20, :no-justify #{"_"}, :ignore-for-variance nil
	        :max-gap nil},
             :justify? false,
             :nl-separator? false,
             :nl-separator-all? false},
   :cache {:directory ".zprint", :location "HOME"},
   :color? false,
   :color-map {:brace :red,
               :bracket :purple,
               :char :black,
               :comma :none,
               :comment :green,
               :deref :red,
               :false :black,
               :fn :blue,
               :hash-brace :red,
               :hash-paren :green,
               :keyword :magenta,
               :nil :yellow,
               :none :black,
               :number :purple,
               :paren :green,
               :regex :black,
               :symbol :black,
               :syntax-quote-paren :red,
               :quote :red,
               :string :red,
               :true :black,
               :uneval :magenta,
               :user-fn :black},
   :comment
     {:count? false, :wrap? true, :inline? true, :inline-align-style :aligned},
   :configured? false,
   :cwd-zprintrc? false,
   :dbg-ge nil,
   :dbg-print? nil,
   :dbg? nil,
   :dbg-s nil,
   :delay {:object? false},
   :do-in-hang? true,
   :drop? nil,
   :extend {:flow? true,
            :force-nl? true,
            :hang-diff 1,
            :hang-expand 1000.0,
            :hang-accept nil,
            :ha-depth-factor 0,
            :ha-width-factor 0,
            :hang? true,
            :indent 2,
            :modifiers #{"static"},
            :nl-separator? false},
   :file? false,
   :fn-force-nl #{:noarg1-body :noarg1 :force-nl-body :force-nl :flow
                  :arg1-force-nl :arg1-force-nl-body :flow-body
		  :arg2-force-nl-body :arg2-force-nl},
   :fn-gt2-force-nl #{:gt2-force-nl :binding #_:binding-vector :pair-fn},
   :fn-gt3-force-nl #{:gt3-force-nl :arg1-pair :arg1-pair-body},
   :fn-map zfnstyle,
   :fn-type-map {}
   :fn-name nil,
   :fn-obj {:object? false},
   :force-eol-blanks? false,
   :format :on,
   :future {:object? false},
   ; This is used for {:parse {:left-space :keep}}
   :indent 0,
   :input {:range {:start nil,
                   :end nil,
                   :use-previous-!zprint? false,
                   :continue-after-!zprint-error? false}},
   ; When you change :list, you should also change :vector-fn, since it
   ; becomes the current :list when
   :list {:constant-pair-fn nil,
          :constant-pair-min 4,
          :constant-pair? true,
          :force-nl? false,
          :hang-avoid 0.5,
          :hang-diff 1,
          :hang-expand 2.0,
          :hang-accept nil,
          :ha-depth-factor 0,
          :ha-width-factor 0,
          :hang-size 100,
          :hang? true,
          :indent 2,
          :indent-arg nil,
          :indent-only? false,
          :indent-only-style :input-hang,
	  :nl-count nil
          :option-fn nil,
          :pair-hang? true,
          :respect-bl? false,
          :respect-nl? false,
          :replacement-string nil,
          :wrap? false,
          :wrap-coll? true,
          :wrap-after-multi? true,
          :wrap-multi? true},
   :map {:indent 2,
         :sort? true,
         :sort-in-code? nil,
         :comma? true,
         :hang? true,
         :hang-expand 1000.0,
         :hang-diff 1,
         ; See zprint_test.clj, def r, (czprint r 28) to see why this
         ; was created and set to 0.  That certainly looks better, but
         ; wider stuff seems better with -1, so for now, we will go with that.
         :hang-adjust -1,
         :hang-accept nil,
         :ha-depth-factor 0,
         :ha-width-factor 0,
         :indent-only? false,
         :key-order nil,
         :key-ignore nil,
         :key-ignore-silent nil,
         :key-color nil,
         :key-depth-color nil,
         :key-value-color nil,
         :key-value-options nil,
         :key-no-sort #{"..."},
         :lift-ns? false,
         :lift-ns-in-code? false,
         :force-nl? nil,
         :nl-separator? false,
         :nl-separator-all? false,
         :flow? false,
	  :flow-all-if-any? false
         :justify? false,
         :justify {:max-variance 20, :ignore-for-variance nil, :no-justify nil
	           :max-gap nil},
         :justify-hang {:hang-expand 1000.0},
         :justify-tuning {:hang-flow 4, :hang-flow-limit 30},
         :respect-bl? false,
         :respect-nl? false,
         :unlift-ns? false},
   :max-depth 1000000,
   :max-depth-string "##",
   :parallel? false,
   :max-hang-count 4,
   ; :max-hang-depth used to be 3, but while it helped a bit, there was
   ; at least one bug filed where this caused a problem.  Since we now
   ; support :parallel? true at the repl automatically, we will disable
   ; this performance speedup as less necessary and with unpleasanet
   ; unintended consequences.
   :max-hang-depth 300,
   :max-hang-span 4,
   :max-length 1000000,
   :meta {:split? false},
   :object {:indent 1, :wrap-after-multi? true, :wrap-coll? true},
   :old? true,
   :output 
     {:format :string
      :focus {:zloc? false, :surround nil},
      :lines nil,
      :elide nil,
      :paragraph 
	{:style 
	  "font-size:20px;font-family: Lucidia Concole, Courier, monospace"}
      :range? nil,
      :real-le? false,
      :real-le-length 20},
   :pair {:flow? false,
	  :flow-all-if-any? false
          :force-nl? nil,
          :hang-diff 1,
          :hang-expand 2.0,
          :hang-accept nil,
          :ha-depth-factor 0,
          :ha-width-factor 0,
          :hang? true,
          :indent 2,
          :justify-hang {:hang-expand 5},
          :justify-tuning {:hang-flow 4, :hang-flow-limit 30},
          :justify {:max-variance 20,
                    :ignore-for-variance #{":else"},
                    :no-justify nil
		    :max-gap nil},
          :justify? false,
          :nl-separator? false,
          :nl-separator-all? false},
   :pair-fn {:hang-diff 1, :hang-expand 2.0, :hang-size 10, :hang? true},
   :parse {:interpose nil, :left-space :drop, :ignore-if-parse-fails #{"..."}},
   :parse-string-all? false,
   :parse-string? false,
   :perf-vs-format nil,
   :process-bang-zprint? nil,
   :promise {:object? false},
   :reader-cond {:comma? nil,
                 :force-nl? true,
                 :hang-diff 1,
                 :hang-expand 1000.0,
                 :hang? true,
                 :indent 2,
                 :key-order nil,
                 :sort-in-code? nil,
                 :sort? nil},
   :record {:hang? true, :record-type? true, :to-string? false},
   :remove {:fn-force-nl nil,
            :fn-gt2-force-nl nil,
            :fn-gt3-force-nl nil,
            :extend {:modifiers nil},
            :pair {:justify {:no-justify nil, :ignore-for-variance nil}},
            :binding {:justify {:no-justify nil, :ignore-for-variance nil}},
            :map {:key-no-sort nil,
                  :justify {:no-justify nil, :ignore-for-variance nil}},
            :parse {:ignore-if-parse-fails nil}},
   :return-cvec? false,
   :script {:more-options nil},
   :search-config? false,
   :set {:indent 2,
         :indent-only? false,
         :respect-bl? false,
         :respect-nl? false,
         :sort? true,
         :sort-in-code? false,
         :wrap-after-multi? true,
         :wrap-coll? true,
         :wrap? true},
   :spaces? nil,
   :spec {:docstring? true, :value nil},
   :style nil,
   :style-map
     {:all-hang {:doc "Enable hangs everywhere (which they are by default)",
                 :map {:hang? true},
                 :list {:hang? true},
                 :extend {:hang? true},
                 :pair {:hang? true},
                 :pair-fn {:hang? true},
                 :reader-cond {:hang? true},
                 :record {:hang? true}},
      :areguide {:doc "Allow modification of areguide in :fn-map",
                 :list {:option-fn (partial areguide {:justify? true})}},
      :areguide-nj
        {:doc "Do nice are formatting, but don't justify, use only in :fn-map",
         :list {:option-fn (partial areguide {:justify? false})}},
      :backtranslate
        {:doc "Turn quote, deref, var, unquote into reader macros",
         :fn-map
           {"quote" [:replace-w-string {} {:list {:replacement-string "'"}}],
            "clojure.core/deref" [:replace-w-string {}
                                  {:list {:replacement-string "@"}}],
            "var" [:replace-w-string {} {:list {:replacement-string "#'"}}],
            "clojure.core/unquote" [:replace-w-string {}
                                    {:list {:replacement-string "~"}}]}},
      :binding-nl {:doc "Add a blank line after every value that flowed",
                   :binding {:indent 0, :nl-separator? true}},
      :binding-nl-all {:doc "Add a blank line between every pair",
                       :binding {:indent 0, :nl-separator-all? true}},
      :community {:doc "Modify defaults to format to 'community' approach",
                  :binding {:indent 0},
                  :fn-map {"apply" :none,
                           "assoc" :none,
                           "filter" :none,
                           "filterv" :none,
                           "map" :none,
                           "mapv" :none,
                           "reduce" :none,
                           "remove" :none,
                           "with-meta" :none-body},
                  :list {:indent-arg 1},
                  :map {:indent 0},
                  :pair {:indent 0}},
      :dark-color-map {:doc
                         "A color map that is pretty good for dark backgrounds",
                       :color-map {:brace :white,
                                   :bracket :white,
                                   :char :bright-cyan,
                                   :comma :bright-white,
                                   :comment :bright-black,
                                   :deref :red,
                                   :false :bright-magenta,
                                   :fn :bright-red,
                                   :hash-brace :white,
                                   :hash-paren :white,
                                   :keyword :bright-blue,
                                   :nil :bright-magenta,
                                   :none :white,
                                   :number :bright-magenta,
                                   :paren :white,
                                   :quote :bright-yellow,
                                   :regex :bright-cyan,
                                   :string :bright-green,
                                   :symbol :bright-white,
                                   :syntax-quote :bright-yellow,
                                   :syntax-quote-paren :white,
                                   :true :bright-magenta,
                                   :uneval :bright-red,
                                   :unquote :bright-yellow,
                                   :unquote-splicing :bright-yellow,
                                   :user-fn :bright-yellow},
                       :uneval {:color-map {:brace :white,
                                            :bracket :white,
                                            :char :bright-cyan,
                                            :comma :bright-white,
                                            :comment :bright-black,
                                            :deref :red,
                                            :false :bright-magenta,
                                            :fn :bright-red,
                                            :hash-brace :white,
                                            :hash-paren :white,
                                            :keyword :bright-blue,
                                            :nil :bright-magenta,
                                            :none :white,
                                            :number :bright-magenta,
                                            :paren :white,
                                            :quote :bright-yellow,
                                            :regex :bright-cyan,
                                            :string :bright-green,
                                            :symbol :bright-white,
                                            :syntax-quote :bright-yellow,
                                            :syntax-quote-paren :white,
                                            :true :bright-magenta,
                                            :uneval :bright-red,
                                            :unquote :bright-yellow,
                                            :unquote-splicing :bright-yellow,
                                            :user-fn :bright-yellow}}},
      :defprotocolguide {:doc "Allow alteration of defprotocol in :fn-map",
                         :list {:option-fn defprotocolguide}},
      :extend-nl {:doc "Add a blank line between protocols",
                  :extend {:flow? true, :indent 0, :nl-separator? true}},
      :how-to-ns {:doc "Make newlines and indentation match 'how to ns'",
                  :fn-map {"ns" [:arg1-body
                                 {:fn-map {":import" [:flow
                                                      {:list {:hang? true}}],
                                           ":require" :flow},
                                  :list {:hang? false, :indent-arg 1}}]}},
      :guideguide {:doc "output guides themselves, experimental",
                   :vector {:option-fn guideguide}},
      :hiccup {:doc "Format vectors containing hiccup information better",
               :vector
                 {:option-fn
                    (fn
                      ([] "hiccup-option-fn")
                      ([opts n exprs]
                       (let [hiccup? (and (>= n 2)
                                          (or (keyword? (first exprs))
                                              (symbol? (first exprs)))
                                          (map? (second exprs)))]
                         (cond (and hiccup? (not (:fn-format (:vector opts))))
                                 {:vector {:fn-format :arg1-force-nl}}
                               (and (not hiccup?) (:fn-format (:vector opts)))
                                 {:vector {:fn-format nil}}
                               :else nil)))),
                  :wrap? false},
               :vector-fn {:indent 1, :indent-arg 1}},
      :indent-only {:doc "Enable indent only for every type of structure",
                    :comment {:wrap? false},
                    :list {:indent-only? true},
                    :map {:indent-only? true},
                    :set {:indent-only? true},
                    ; Should we also set :vector-fn to :indent-only?  That
                    ; is only used by :fn-format, so it might confuse people
                    ; if we did that.
                    :vector {:indent-only? true}},
      :justified {:doc "Justify everything possible",
                  :binding {:justify? true},
                  :map {:justify? true},
                  :pair {:justify? true}},
      :justified-original
        {:doc "Justify everything using pre-1.1.2 approach",
         :binding {:justify? true, :justify {:max-variance 1000}},
         :map {:justify? true, :justify {:max-variance 1000}},
         :pair {:justify? true, :justify {:max-variance 1000}}},
      :keyword-respect-nl
        {:doc "When a vector starts with a :keyword, :respect-nl in it",
         :vector {:option-fn-first
                    (fn
                      ([] "keyword-respect-nl-option-fn-first")
                      ([options element]
                       (let [k? (keyword? element)]
                         (when (not= k? (:respect-nl? (:vector options)))
                           {:vector {:respect-nl? k?}}))))}},
      :map-nl {:doc "Add newline after every value that flows",
               :map {:indent 0, :nl-separator? true}},
      :map-nl-all {:doc "Add newline between all map pairs",
                   :map {::indent 0, :nl-separator-all? true}},
      :meta-base {:doc "Alternative format for metadata. Experimental.",
                  :list {:option-fn (fn
                                      ([] "meta-base-option-fn")
                                      ([opts n exprs]
                                       (when (meta (second exprs))
                                         {:meta {:split? true},
                                          :list {:hang-expand 0},
                                          :fn-style :arg2,
                                          :next-inner-restore
                                            [[:list :option-fn]
                                             [:list :hang-expand]]})))}},
      :meta-alt {:doc "Alternative for metadata. Experimental.",
                 :fn-map {"def" [:arg2 {:style :meta-base}],
                          "deftest" [:arg2 {:style :meta-base}]}},
      :moustache {:doc "Format moustache elements nicely",
                  :fn-map {"app" [:flow {:style :vector-pairs}]}},
      :vector-pairs {:doc "Consider vectors 'constants' for constant pairing",
                     :list {:constant-pair-min 1,
                            :constant-pair-fn #(or (keyword? %)
                                                   (string? %)
                                                   (number? %)
                                                   (= true %)
                                                   (= false %)
                                                   (vector? %))},
                     :pair {:justify? true},
                     :next-inner {:list {:constant-pair-min 4,
                                         :constant-pair-fn nil},
                                  :pair {:justify? false}}},
      :no-hang {:doc "Turn off hang for every structure",
                :map {:hang? false},
                :list {:hang? false},
                :extend {:hang? false},
                :pair {:hang? false},
                :pair-fn {:hang? false},
                :reader-cond {:hang? false},
                :record {:hang? false}},
      :pair-nl {:doc "Add a blank line after every value that flowed",
                :pair {:indent 0, :nl-separator? true}},
      :pair-nl-all {:doc "Add a blank line between every pair",
                    :pair {:indent 0, :nl-separator-all? true}},
      :fast-hang {:doc "Speed up formatting of very deeply nested structures",
                  :binding {:hang-accept 100, :ha-width-factor -600},
                  :extend {:hang-accept 100, :ha-width-factor -600},
                  :list {:hang-accept 100, :ha-width-factor -300},
                  :map {:hang-accept 0, :ha-depth-factor 15},
                  :pair {:hang-accept 20, :ha-width-factor -150},
                  :vector-fn {:hang-accept 100, :ha-width-factor -300}},
      :odr {:doc "justify columns of rules, experimental",
            :vector {:option-fn odrguide}},
      :quote-wrap {:doc "Wrap quoted lists to right margin, like vectors",
                   :fn-map {:quote [:wrap
                                    {:list {:indent 1},
                                     :next-inner-restore [[:list :indent]]}]}},
      :jrequireguide {:list {:option-fn (partial jrequireguide :require)}},
      :jrequiremacrosguide {:list {:option-fn (partial jrequireguide
                                                       :require-macros)}},
      :jimportguide {:list {:option-fn (partial jrequireguide :import)}},
      :rj-var {:doc "Set max-variance for :require-justify",
               :pair {:justify {:max-variance 20}}},
      :rjm-var {:doc "Set max-variance for :require-justify-macros",
                :pair {:justify {:max-variance 20}}},
      :ij-var {:doc "Set max-variance for :import-justify",
               :pair {:justify {:max-variance 1000}}},
      :require-justify {:doc "Justify namespaces in :require",
                        :fn-map {":require"
                                   [:flow {:style [:jrequireguide :rj-var]}]}},
      :require-macros-justify
        {:doc "Justify namespaces in :require-macros",
         :fn-map {":require-macros"
                    [:flow {:style [:jrequiremacrosguide :rjm-var]}]}},
      :import-justify {:doc "Justify :import",
                       :fn-map {":import" [:flow
                                           {:style [:jimportguide :ij-var]}]}},
      :ns-justify {:style [:require-justify :require-macros-justify
                           :import-justify]},
      :require-pair
        {:doc "Clarify namespaces in :require",
         :fn-map {":require" [:none
                              {:vector {:option-fn
                                          (fn
                                            ([opts n exprs]
                                             (if-not (clojure.string/includes?
                                                       (str (first exprs))
                                                       ".")
                                               {:vector {:fn-format nil}}
                                               {:vector {:fn-format :none},
                                                :vector-fn {:constant-pair-min
                                                              1}}))
                                            ([] "require-pair-option-fn"))}}]}},
      :respect-bl {:doc "Enable respect blank lines for every type",
                   :list {:respect-bl? true},
                   :map {:respect-bl? true},
                   :vector {:respect-bl? true},
                   :set {:respect-bl? true}},
      :respect-bl-off {:doc "Disable respect blank lines for every type",
                       :list {:respect-bl? false},
                       :map {:respect-bl? false},
                       :vector {:respect-bl? false},
                       :set {:respect-bl? false}},
      :respect-nl {:doc "Enable respect newlines for every type",
                   :list {:respect-nl? true},
                   :map {:respect-nl? true},
                   :vector {:respect-nl? true},
                   :set {:respect-nl? true}},
      :respect-nl-off {:doc "Disable respect newline for every type",
                       :list {:respect-nl? false},
                       :map {:respect-nl? false},
                       :vector {:respect-nl? false},
                       :set {:respect-nl? false}},
      :rod-base {:list {:option-fn (partial rodfn {:multi-arity-nl? true})}}
      :rod {:doc "Rules of defn, experimental.  Very likely to change.",
            :fn-map {"defn" [:none #_:guided-body
                             {:list {:option-fn (partial rodfn #_rodguide
                                                         {:multi-arity-nl?
                                                            true})}}],
                     "defn-" [:none #_:guided-body
                              {:list {:option-fn (partial rodfn #_rodguide
                                                          {:multi-arity-nl?
                                                             true})}}]}},
      :rod-no-ma-nl
        {:doc
           "Rules of defn, experimental. No newlines between arities.  Very likely to change.",
         :fn-map
           {"defn" [:guided-body
                    {:list {:option-fn (partial rodguide
                                                {:multi-arity-nl? false})}}],
            "defn-" [:guided-body
                     {:list {:option-fn (partial rodguide
                                                 {:multi-arity-nl? false})}}]}},
      :signature1 {:doc
                     "defprotocol signatures with doc on newline, experimental",
                   :list {:option-fn signatureguide1}},
      :sort-dependencies {:doc "sort dependencies in lein defproject files",
                          :list {:return-altered-zipper [1 'defproject
                                                         sort-dependencies]}}},
   :tab {:expand? true, :size 8},
   :test-for-eol-blanks? false,
   :trim-comments? nil,
   :tuning {; do hang if (< (/ hang-count flow-count) :hang-flow)
            :hang-flow 1.1,
            ; if the :fn-style is hang, then this become the :hang-flow above
            :hang-type-flow 1.5,
            ; when (> hang-count :hang-flow-limit),
            ;  hang if (<= (dec hang-count) flow-count)
            :hang-flow-limit 10,
            ; this is added to the count of hanging lines before the comparison
            ; when doing the one with :hang-flow or :hang-type-flow
            ; Note that :map has its own :hang-adjust which overides this
            ; general
            ; one.
            :general-hang-adjust -1,
            :hang-if-equal-flow? true},
   :uneval {:color-map {:brace :yellow,
                        :bracket :yellow,
                        :char :magenta,
                        :comma :none,
                        :comment :green,
                        :deref :yellow,
                        :false :yellow,
                        :fn :cyan,
                        :hash-brace :yellow,
                        :hash-paren :yellow,
                        :keyword :yellow,
                        :nil :yellow,
                        :none :yellow,
                        :number :yellow,
                        :paren :yellow,
                        :quote :yellow,
                        :regex :yellow,
                        :string :yellow,
                        :symbol :cyan,
                        :syntax-quote-paren :yellow,
                        :true :yellow,
                        :uneval :magenta,
                        :user-fn :cyan}},
   :user-fn-map {},
   :vector {:indent 1,
            :binding? false,
            :option-fn-first nil,
            :option-fn nil,
            :fn-format nil,
            :force-nl? false,
            :hang? nil,
            :respect-bl? false,
            :respect-nl? false,
            :wrap-after-multi? true,
            :wrap-coll? true,
            :wrap? true,
            :wrap-multi? false,
            :indent-only? false},
   ; Needs to have same keys as :list, since this replaces :list when
   ; vectors are formatted as functions.
   :vector-fn {:constant-pair-fn nil,
               :constant-pair-min 4,
               :constant-pair? true,
               :hang-avoid 0.5,
               :hang-diff 1,
               :hang-expand 2.0,
               :hang-accept nil,
               :ha-depth-factor 0,
               :ha-width-factor 0,
               :hang-size 100,
               :hang? true,
               :indent 2,
               :indent-arg nil,
               :indent-only? false,
               :indent-only-style :input-hang,
               :pair-hang? true,
               :respect-bl? false,
               :respect-nl? false,
               :wrap-coll? true,
               :wrap-after-multi? true,
               :wrap-multi? true},
   :width 80,
   :url {:cache-dir "urlcache", :cache-secs 300},
   :zipper? false,
   :!zprint-elide-skip-next? false})

;; Returns nil for all of the colors
(def no-color-map
  {:brace :none,
   :bracket :none,
   :comment :none,
   :deref :none,
   :fn :none,
   :hash-brace :none,
   :hash-paren :none,
   :keyword :none,
   :nil :none,
   :none :none,
   :number :none,
   :paren :none,
   :syntax-quote-paren :none,
   :quote :none,
   :string :none,
   :uneval :none,
   :user-fn :none})


;;
;; # Mutable Options storage
;;

(def configured-options (atom default-zprint-options))

(def explained-options (atom default-zprint-options))

(def explained-sequence (atom 1))

(def write-options? (atom nil))

;;
;; # Utility functions for manipulating option maps
;;

(defn merge-with-fn
  "Take two arguments of things to merge and figure it out.
  Works for sets too."
  [val-in-result val-in-latter]
  (cond (and (map? val-in-result) (map? val-in-latter))
          (merge-with merge-with-fn val-in-result val-in-latter)
        (and (set? val-in-result) (set? val-in-latter))
          (apply conj val-in-result (seq val-in-latter))
        :else val-in-latter))

(defn merge-deep
  "Do a merge of maps all the way down."
  [& maps]
  (apply merge-with merge-with-fn maps))

(defn merge-with-fn-doc
  "Take two arguments of things to merge and figure it out."
  [doc-string val-in-result val-in-latter]
  (if (and (map? val-in-result) (map? val-in-latter))
    (merge-with (partial merge-with-fn-doc doc-string)
                val-in-result
                val-in-latter)
    {:from doc-string, :value val-in-latter}))

(defn merge-deep-doc
  "Do a merge of maps all the way down, keeping track of where every
  value came from."
  [doc-string & maps]
  (apply merge-with (partial merge-with-fn-doc doc-string) maps))

(declare remove-keys)

(defn remove-key
  "Remove a single key from a map or remove a series of
  keys from an internal map."
  [m k]
  (if (coll? k)
    (let [map-key (first k)
          keys-to-remove (next k)]
      (assoc m map-key (remove-keys (m map-key) keys-to-remove)))
    (dissoc m k)))

(defn remove-keys
  "Remove keys from a map at multiple levels."
  [m ks]
  (reduce #(remove-key %1 %2) m ks))

;;
;; ## Remove set elements
;;

(defn key-seq
  "Get the key seq for every terminal element in a map."
  [m]
  (when (map? m)
    (mapcat (fn [[k v]]
              (let [ks (key-seq v)]
                (if ks
                  (map #(cons k (if (coll? %) % [%])) ks)
                  ;(map (partial list k) ks)
                  [[k]])))
      m)))

(defn remove-elements
  "Given a key sequence and two maps, remove the elements of the set at
  the key sequence in the second map from set in the first map."
  [map-remove map-orig ks]
  (update-in map-orig ks clojure.set/difference (get-in map-remove ks)))

(defn remove-set-elements
  "Take two maps, and remove all of the elemnts in the second maps sets
  from equivalent places in the first map."
  [map-orig map-remove]
  (reduce (partial remove-elements map-remove) map-orig (key-seq map-remove)))

(declare diff-deep-ks)

(defn perform-remove
  "Take an options map, and remove the set elements that are at the :remove
  key, and also remove the :remove key."
  [doc-string doc-map options new-options]
  (let [map-remove (:remove new-options)
        options-out (remove-set-elements options map-remove)
        remove-ks-seq (key-seq map-remove)
        new-options-out (dissoc new-options :remove)]
    [options-out new-options-out
     (diff-deep-ks doc-string doc-map remove-ks-seq options-out)]))

;;
;; The best way is to just label all of the nodes.
;;

(declare map-leaves)

(defn map-leaf
  "Given a function and map and a key, replace the value with 
  (f leaf) or (map-leaves f m)."
  [f m k]
  (let [v (m k)]
    (if-not (map? v) (assoc m k (f v)) (assoc m k (map-leaves f v)))))

(defn map-leaves
  "Return a map of the same shape but where every leaf value
  (i.e., not map value) has been replaced by the (f leaf-value)."
  [f m]
  (reduce (partial map-leaf f) m (keys m)))

(defn value-set-by
  "Create a map with a :value and :set-by elements."
  [set-by _ value]
  {:set-by set-by, :value value})

(defn diff-deep-ks
  "Update an existing doc-map with labels of everything that shows up
  in the ks-seq."
  [doc-string doc-map changed-key-seq existing]
  (reduce
    #(update-in %1 %2 (partial value-set-by doc-string) (get-in existing %2))
    doc-map
    changed-key-seq))

(defn diff-map "Diff two maps." [before after] (second (d/diff before after)))

;;
;; # Functions manipulating mutable options
;;
;; ## Overall Options
;;

(defn inc-explained-sequence
  "Return current explained-seqence and add one to it."
  []
  (swap! explained-sequence inc))

(defn add-calculated-options
  "Take an updated-map and generate calculated options
  from it.  Takes the updated-map and further updates
  it, being smart about things that were set to nil.
  Presently updates [:map :key-value] and [:reader-cond :key-value]"
  [updated-map]
  (cond-> updated-map
    (:key-order (:map updated-map)) (assoc-in [:map :key-value]
                                      (zipmap (:key-order (:map updated-map))
                                              (range)))
    ; is :key-order now nil and :key-value is not?
    (and (nil? (:key-order (:map updated-map)))
         (not (nil? (:key-value (:map updated-map)))))
      (dissoc-two [:map :key-value])
    (:key-order (:reader-cond updated-map))
      (assoc-in [:reader-cond :key-value]
        (zipmap (:key-order (:reader-cond updated-map)) (range)))
    ; is :key-order now nil and :key-value is not?
    (and (nil? (:key-order (:reader-cond updated-map)))
         (not (nil? (:key-value (:reader-cond updated-map)))))
      (dissoc-two [:reader-cond :key-value])))

(defn reset-options!
  "Replace options to be used on every call.  You must have validated
  these options already!"
  ([updated-map doc-map]
   (reset! configured-options (add-calculated-options updated-map))
   (when doc-map (reset! explained-options doc-map)))
  ([updated-map] (reset-options! updated-map nil)))

(defn reset-default-options!
  "Remove any previously set options."
  []
  (reset! configured-options default-zprint-options)
  (reset! explained-options default-zprint-options))

(defn get-options
  "Return any previously set options."
  []
  (assoc @configured-options :version (about)))

(declare configure-if-needed!)

(defn get-configured-options
  "Do a (get-options), but make sure that they are configured first."
  []
  (configure-if-needed! {} :report-errors)
  (get-options))

(defn get-default-options
  "Return the base default options."
  []
  default-zprint-options)

;;
;; ## Utilities for explained-set options
;;

(defn find-set
  "If the mapentry was explicitly set, return it as {:k {:set-by x
  :value y}}.  If the key has information below it that was explicitly
  set, return that as [k stuff] where stuff is a seq of maps whose
  ultimate value is {:set-by x :value y}."
  [[k v :as mapentry]]
  #_(println "find-set: mapentry:" mapentry)
  (when (map? v)
    (if (= #{:set-by :value} (into #{} (keys v)))
      (do #_(println "found one:" {k v}) {k v})
      (let [result (mapv find-set v)
            result (remove nil? result)]
        (if (not (empty? result))
          (do #_(println "find-set: result:" [k result]) [k result]))))))

(defn map-set
  "Call find-set on all the elements of a map."
  [options]
  (mapv find-set options))

(declare form-map)

(defn extract-map
  "Handle the three things returned from find-set, a regular map, nil,
  or a key with a seq of things that ultimately had a set map underneath,
  and build a map from each."
  [m]
  (cond (map? m) m
        (nil? m) m
        (seq? (first (next m)))
          (do #_(println "extract-map: m:" m "\n(next m)" (next m))
              {(first m) (apply merge (form-map (first (next m))))})
        :else nil))

(defn form-map
  "Given the vector of things from map-set, reconstruct a map out of
  the information."
  [map-set-result]
  #_(println map-set-result)
  (map extract-map map-set-result))

(defn only-set
  "Take an option map, and return a new options map with only the
  set values shows."
  [options]
  (apply merge (form-map (map-set options))))

;;
;; ## Explained options, also known as the doc-map
;;

(defn set-explained-options!
  "Set options to be used on every call."
  [doc-map]
  (reset! explained-options doc-map))

(defn get-explained-options
  "Return any previously set doc-map."
  []
  (assoc (remove-keys @explained-options explain-hide-keys) :version (about)))

(defn get-explained-set-options
  "Return any previously set doc-map."
  []
  (only-set (get-explained-options)))

(defn get-explained-all-options
  "Return any previously set doc-map complete."
  []
  (assoc @explained-options :version (about)))

(defn get-default-explained-all-options
  "Return the base explained options, matches get-default-options"
  []
  default-zprint-options)

(declare config-and-validate)
(declare config-and-validate-all)
(declare config-set-options!)

;;
;; # Configure Everything
;;

(defn ensure-option-access
  "Throw an exception if write-options? is not true."
  [s]
  (when-not @write-options?
    (throw
      (#?(:clj Exception.
          :cljs js/Error.)
       (str "Routine: '" s "' requires option access and does not have it!")))))

(defn internal-set-options!
  "Validate the new options, and update both the saved options
  and the doc-map as well.  Will throw an exceptino for errors."
  [doc-string doc-map existing-options new-options]
  (ensure-option-access "internal-set-options!")
  (let [[updated-map new-doc-map error-vec]
          (config-and-validate doc-string doc-map existing-options new-options)]
    (if error-vec
      (throw (#?(:clj Exception.
                 :cljs js/Error.)
              (str "set-options! for " doc-string
                   " found these errors: " error-vec)))
      (do (reset-options! updated-map new-doc-map) nil))))

;;
;; Stack Overflow code to detect if running in a REPL
;; Alfred Xiao 5/23/15
;;

#?(:bb []
   :clj (defn current-stack-trace [] (.getStackTrace (Thread/currentThread))))
#?(:bb []
   :clj (defn is-repl-stack-element
          [^java.lang.StackTraceElement stack-element]
          (and (= "clojure.main$repl" (.getClassName stack-element))
               (= "doInvoke" (.getMethodName stack-element)))))

(defn is-in-repl?
  []
  #?(:bb true
     :clj (some is-repl-stack-element (current-stack-trace))
     :cljs nil))

(defn select-op-options
  "Given an options map, return an options map with only the operational
  options remaining."
  [options]
  (select-keys options operational-options))

;;
;; End "detect in a REPL" code
;;

(defn config-configure-all!
  "Do external configuration regardless of whether or not it has
  already been done, replacing any internal configuration.  Returns
  nil if successful, a vector of errors if not. "
  ([op-options]
   ; Any config changes prior to this will be lost, as
   ; config-and-validate-all works from the default options!
   (ensure-option-access "config-configure-all!")
   #_(println "config-configure-all!")
   (let [[zprint-options doc-map errors] (config-and-validate-all op-options)]
     (if errors
       errors
       (do (reset-options! zprint-options doc-map)
           ; We used to call
           ; (config-set-options! {:configured? true} "internal")
           ; but if you do that, configure-if-needed! is called after
           ; someone already has the write-options? atom set to true,
           ; and the processing for that is not reentrant. So it will
           ; time out.
           (internal-set-options! "internal"
                                  (get-explained-all-options)
                                  (get-options)
                                  {:configured? true})
           ; If we are running in a repl, then turn on :parallel?
           ; the first time we run unless it has been explicitly
           ; set by some external configuration
           (when (and (is-in-repl?)
                      (not (:set-by (:parallel? (get-explained-all-options)))))
             (internal-set-options! "REPL execution default"
                                    (get-explained-all-options)
                                    (get-options)
                                    {:parallel? true}))
           nil))))
  ([] (config-configure-all! nil)))

(defn lock-options
  "Attempt to lock the options using the write-options? atom.
  Tries 1000 times, waiting 1ms between tries. Always returns nil."
  []
  (loop [n 0]
    (if (or (compare-and-set! write-options? nil true) (> n 1000))
      nil
      (do #_(println "sleeping for 1 ms")
          #?(:bb nil
             :clj (Thread/sleep 1))
          (recur (inc n))))))

(defn unlock-options
  "Unlock the options using write-options?"
  []
  (when-not (compare-and-set! write-options? true nil)
    (throw
      (#?(:clj Exception.
          :cljs js/Error.)
       (str
         "When unlocking the options, we found we didn't have them locked!")))))

(defn protected-configure-all!
  "Call config-configure-all!, but protect the call by gaining
  access to the options using lock-options and unlock-options."
  ([]
   (lock-options)
   (let [error-vec (config-configure-all!)]
     (unlock-options)
     error-vec))
  ([op-options]
   (lock-options)
   (let [error-vec (config-configure-all! op-options)]
     (unlock-options)
     error-vec)))

(defn configure-if-needed!
  "Test to see if we are already configured, and if we are not,
  then perform a configuration and return any errors detected.  If
  no-configure? is non-nil then don't read in any files.  This does
  not set :configured? true, so if you call with no-configure? true
  you better set :configured? true yourself!  Handle multithreaded
  case of several executions of zprint-str-internal at once, each
  trying to figure out if we are already configured.  If no-unlock?
  is true, then don't (unlock-options) when finished."
  ([op-options report-errors? no-configure-all? no-unlock?]
   #_(println "configure-if-needed! write-options?" @write-options?
              "no-configure-all?" no-configure-all?
              "no-unlock?" no-unlock?)
   (if (:configured? (get-options))
     ; We are already configured
     (if no-unlock?
       ; we need to lock, even though we are configured
       (lock-options)
       nil)
     ; We are not, presently, configured - gain access to the options
     (do (lock-options)
         ; Configure from external files, unless someone configured them
         ; already while we were waiting for access, or we were told to
         ; not configure them from external files.
         (let [error-vec (when-not (or (:configured? (get-options))
                                       no-configure-all?)
                           #_(println "calling configure-configure-all!")
                           (config-configure-all! op-options))]
           (when (and report-errors? (not (empty? error-vec)))
             ; If we are exiting, unlock regardless of whether we were
             ; requested to do so.
             (unlock-options)
             (throw
               (#?(:clj Exception.
                   :cljs js/Error.)
                (str "When configuring these errors were found: " error-vec))))
           ; Unlock options unless requested not to
           (when-not no-unlock? (unlock-options))
           error-vec))))
  ([] (configure-if-needed! {} nil nil nil))
  ([op-options report-errors?]
   (configure-if-needed! op-options report-errors? nil nil)))

(defn config-set-options!
  "Add some options to the current options, checking to make sure
  that they are correct. op-options are operational options that
  affect where to get options or how to do the processing, but do
  not affect the format of the output directly."
  ([new-options doc-str op-options]
   ; avoid infinite recursion, while still getting the doc-map updated
   (let [error-vec (configure-if-needed! op-options
                                         nil ; report-errors?
                                         (:configured? new-options)
                                         :no-unlock)]
     (when error-vec
       ; Unlock before we exit with throw
       (unlock-options)
       (throw
         (#?(:clj Exception.
             :cljs js/Error.)
          (str "set-options! for " doc-str " found these errors: " error-vec))))
     (internal-set-options! doc-str
                            (get-explained-all-options)
                            (get-options)
                            new-options)
     ; Unlock the options now
     (unlock-options)))
  ([new-options]
   (config-set-options! new-options
                        (str "repl or api call " (inc-explained-sequence))
                        (select-op-options new-options)))
  ([new-options doc-str]
   (config-set-options! new-options doc-str (select-op-options new-options))))

;;
;; # Options Validation Functions
;;


(defn empty-to-nil
  "If the sequence is empty, then return nil, else return the sequence."
  [empty-seq]
  (when-not (empty? empty-seq) empty-seq))

(defn validate-options
  "Validate an options map, source-str is a descriptive phrase 
  which will be included in the errors (if any). Returns nil 
  for success, a string with error(s) if not."
  ([options source-str]
   #_(println "validate-options:" options)
   (when options
     (empty-to-nil (apply str
                     (interpose ", "
                       (remove #(or (nil? %) (empty? %))
                         (conj [] (validate-basic options source-str))))))))
  ([options] (validate-options options nil)))

;;
;; # Style
;;

(declare apply-style)
(declare internal-config)

(defn apply-one-style
  "Take a [doc-string new-map styles-applied [existing-map doc-map
  error-str] style-name] and produce a new [existing-map doc-map
  error-str] from the style defined in the new-map if it exists,
  or the existing-map if it doesn't."
  [doc-string new-map [existing-map doc-map error-str] style-name]
  (if (or (= style-name :not-specified) (nil? style-name))
    [existing-map doc-map nil]
    (let [style-map (if (= style-name :default)
                      (get-default-options)
                      (or (get-in new-map [:style-map style-name])
                          (get-in existing-map [:style-map style-name])))
          ; Remove the :doc key from the style, or it will end up in
          ; the top level options map.
          style-map (dissoc style-map :doc)
          ; Note that styles-applied is initialized in config-and-validate
          ; to be [].
          existing-map (assoc existing-map
                         :styles-applied (conj (:styles-applied existing-map)
                                               style-name))]
      (if style-map
        (let [[updated-map new-doc-map error-vec]
                (internal-config
                  (str doc-string " specified :style " style-name)
                  doc-map
                  existing-map
                  style-map)
              new-doc-map (when new-doc-map
                            (assoc new-doc-map
                              :styles-applied (:styles-applied updated-map)))]
          [updated-map new-doc-map
           (when (or error-str error-vec)
             (str error-str (when error-str ",") error-vec))])
        [existing-map doc-map
         (str error-str
              (when error-str ",")
              (str "Style '" style-name "' not found!"))]))))

(defn apply-style
  "Given an existing-map and a new-map, if the new-map specifies a
  style, apply it if it exists, looking first in the new-map for the style
  and then in the existing-map for the style.  Otherwise do nothing. 
  Use config-and-validate to actually apply the style, which in turn
  means that we need to pass down styles-applied through that routine.
  Return [updated-map new-doc-map error-string]"
  [doc-string doc-map existing-map new-map]
  (let [style-name (get new-map :style :not-specified)]
    (if (or (= style-name :not-specified) (nil? style-name))
      [existing-map doc-map nil]
      (if (some #(= % style-name)
                (:styles-applied existing-map)
                #_styles-applied)
        ; We have already done this style
        [existing-map doc-map
         (str "Circular style error: style '"
              style-name
              "' has already been applied in this call."
              "  Styles already applied: "
              (:styles-applied existing-map))]
        (let [[updated-map new-doc-map error-string]
                (if (not (coll? style-name))
                  (apply-one-style doc-string
                                   new-map
                                   [existing-map doc-map nil]
                                   style-name)
                  (reduce (partial apply-one-style doc-string new-map)
                    [existing-map doc-map nil]
                    style-name))
              another-style-name (get updated-map :style :not-specified)
              another-style? (not (or (= another-style-name :not-specified)
                                      (nil? another-style-name)))
              another-style-error
                (when another-style?
                  (str "Internal Error: While processing style: '"
                       style-name
                       "' found that"
                       " this style: '"
                       another-style-name
                       "' showed up in :style,"
                       " Styles already applied: " (:styles-already-applied
                                                     existing-map)
                       " Please submit an issue on GitHub regarding"
                         " this problem!"))]
          (if another-style?
            ; We found another style in the :style of the updated map!
            [updated-map new-doc-map
             (if error-string
               (str error-string "," another-style-error)
               another-style-error)]
            [updated-map new-doc-map error-string]))))))

;;
;; # File Access
;;

#?(:clj
     (defn file-line-seq-file
       "Turn the lines in a file from the filesystem    
   into a seq of lines."
       [filename]
       (line-seq (BufferedReader. (FileReader. ^String filename)))))

;;
;; # Configuration Utilities
;;

;;
;; Allow user defined option maps to access to the internally defined
;; guide functions.
;;

(def opts
  {:namespaces {'clojure.core {'jrequireguide zprint.guide/jrequireguide,
                               'defprotocolguide zprint.guide/defprotocolguide,
                               'signatureguide1 zprint.guide/signatureguide1,
                               'guideguide zprint.guide/guideguide,
                               'rodguide zprint.guide/rodguide,
                               'areguide zprint.guide/areguide,
                               'odrguide zprint.guide/odrguide
			       'rodfn zprint.optionfn/rodfn}}})
(def sci-ctx
  #?(:bb nil
     ;:clj nil
     ; To completely remove sci, include the previous line and comment
     ; out the following line.
     :clj (sci/init opts)
     :cljs (sci/init opts)))

(defn sci-load-string
  "Read an options map from a string using sci/eval-string to read
  in the structure, and to create sandboxed function for any functions
  defined in the options-map (i.e. in the string).  Any failures
  from eval-string are not caught and propagate back up the call
  stack."
  [s]
  #?(:bb (read-string s)
     ; :clj (read-string s)
     ; To completely remove sci, include the previous line and comment out
     ; the following line.
     :clj (sci/eval-string* sci-ctx s)
     :cljs (sci/eval-string* sci-ctx s)))

;; Remove two files from this, make it one file at a time.`
;; Do the whole file here.

(defn get-config-from-file
  "Read in an options map from one file or another file. Possibly neither of
  them exist, which is fine if optional? is truthy. Return
  [options-from-file error-string full-path-of-file].  It is acceptable to
  not have a file if optional? is truthy, but if the file exists, then 
  regardless of optional?, errors are detected and reported."
  ([filename optional?]
   #?(:clj
        (when filename
          (let [filestr (str filename)
                the-file (java.io.File. filestr)
                full-path (.getCanonicalPath the-file)
                #_(println "get-config-from-file: filename:" filename
                           "full-path:" full-path)
                [lines file-error]
                  (try (let [lines (file-line-seq-file filename)] [lines nil])
                       (catch Exception e
                         [nil
                          (str "Unable to open configuration file " full-path
                               " because " e)]))]
            (if file-error
              (if optional? nil [nil file-error full-path])
              (try (let [opts-file (sci-load-string
                                     (clojure.string/join "\n" lines))]
                     [opts-file nil full-path])
                   (catch Exception e
                     [nil
                      (str "Unable to read configuration from file " full-path
                           " because " e) full-path])))))
      :cljs nil))
  ([filename] (get-config-from-file filename nil)))

(defn get-config-from-path
  "Take a vector of filenames, and look in exactly one directory for
  all of the filenames.  Return the [option-map error-str full-file-path]
  from get-config-from-file for the first one found, or nil if none found."
  [filename-vec file-sep dir-vec]
  (let [dirspec (apply str (interpose file-sep dir-vec))
        config-vec (some #(get-config-from-file % :optional)
                         (map (partial str dirspec file-sep) filename-vec))]
    config-vec))

(defn get-config-from-dirs
  "Take a vector of directories dir-vec and check for all of the
  filenames in filename-vec in the directory specified by dir-vec.
  When one is found, return (using reduced) the [option-map error-str
  full-file-path] from get-config-from-file, or nil if none are
  found.  Will now accept fns from any of the files since using
  sci/eval-string."
  [filename-vec file-sep dir-vec _]
  (let [config-vec (get-config-from-path filename-vec file-sep dir-vec)]
    (if config-vec
      (reduced config-vec)
      (if (= ["."] dir-vec) [".."] (concat [".."] dir-vec)))))

(defn scan-up-dir-tree
  "Take a vector of filenames and scan up the directory tree from
  the current directory to the root, looking for any of the files
  in each directory.  Look for them in the order given in the vector.
  Return nil or a vector from get-config-from-file: [option-map
  error-str full-file-path]."
  [filename-vec file-sep]
  #?(:clj (let [; Fix file-sep for Windows file separator regex-file-sep
                regex-file-sep (if (= file-sep "\\") "\\\\" file-sep)
                file-sep-pattern (re-pattern regex-file-sep)
                cwd (java.io.File. ".")
                path-to-root (.getCanonicalPath cwd)
                dirs-to-root (clojure.string/split path-to-root
                                                   file-sep-pattern)]
            (reduce (partial get-config-from-dirs filename-vec file-sep)
              ["."]
              dirs-to-root))
     :cljs nil))

(defn get-config-from-map
  "Read in an options map from a string."
  [map-string]
  (when map-string
    (try (let [opts-map (sci-load-string map-string)] [opts-map nil])
         (catch #?(:clj Exception
                   :cljs :default)
           e
           [nil
            (str "Unable to read configuration from map" map-string
                 " because " e)]))))

(defn strtf->boolean
  "If it is a string, and it is true or false (any case), turn it
  into true or false, else leave it the way it is."
  [sexpr]
  (if-not (string? sexpr)
    sexpr
    (let [lc-sexpr (clojure.string/lower-case (clojure.string/trim sexpr))]
      (if-not (or (= lc-sexpr "true") (= lc-sexpr "false"))
        sexpr
        (if (= lc-sexpr "true") true false)))))

(defn starts-with?
  "Return true if a sequence starts with another sequence."
  [coll seq]
  (reduce #(and %1 %2) (map = coll seq)))

(defn build-fn-map-update
  "Given a map of environment variable entries from cprop, which contain
  a sequence key and a keyword value, build a map to merge with the
  fn-map."
  [m]
  (let [mapseq (seq m)
        fn-map-entries (filter #(starts-with? [:zprint :fn-map] (first %))
                         mapseq)
        fn-map-keys (map #(name (nth (first %) 2)) fn-map-entries)
        fn-map-vals (map #(keyword (second %)) fn-map-entries)]
    (zipmap fn-map-keys fn-map-vals)))

(defn update-fn-map
  "Given the current options map and a map of environment variables
  from cprop, update the fn-map as described by the environment variable
  map."
  [options env-map]
  (let [fn-map-update (build-fn-map-update env-map)]
    (if (empty? fn-map-update)
      options
      (assoc options :fn-map (merge (:fn-map options) fn-map-update)))))

;;
;; Special merge only if existing value already exists.  Works even if
;; existing value is nil!
;;

(defn replace-existing
  "If a particular key-path exists in an existing map, replace it
  with a new key-path.  The existing key-path can have a nil value."
  [existing [k-path v]]
  (if (and (seq k-path)
           (not= (get-in existing k-path :not-present) :not-present))
    (assoc-in existing k-path v)
    existing))

(defn merge-existing
  "Takes a map with multiple levels, and merge only the value from
  the second map where the path of the keys already exists in the first
  map.  Will not add any new keys to the first map."
  [existing new]
  (reduce replace-existing existing new))

;;
;; # Configure one map
;;


#?(:clj (do #_(defn get-stack-trace
                "Get the current stack trace as a string."
                []
                (with-out-str (clojure.stacktrace/print-stack-trace
                                (Exception. "get*stack*trace"))))))

(defn update-next-inner
  "Update the current :next-inner map in the options map.  If the
  :next-inner doesn't exist or the value is map, just add to it.  If the
  value is a vector of maps, then update the first map in the vector."
  [options ks ks-value]
  (dbg-s options
         :next-inner-restore
         "update-next-inner: ks:" ks
         "ks-value:" ks-value)
  (let [next-inner (:next-inner options :unset)]
    (cond (or (map? next-inner) (= :unset next-inner))
            (assoc-in options (concat [:next-inner] ks) ks-value)
          (vector? next-inner)
            (let [next-inner-map (assoc-in (first next-inner) ks ks-value)
                  next-inner-vector (assoc next-inner 0 next-inner-map)]
              (assoc options :next-inner next-inner-vector))
          :else options)))

(defn restore-vector
  "Process one subvector from the :next-inner-restore key in an options map.
  If the subvector is a sequence of keywords, save the value
  of that element in the :next-inner map.  If the first
  element in the vector is itself a vector of keywords, then that vector
  is a key-sequence to a set, and the second element of the vector is
  an element in the set.  In that case, modify the :next-inner map to
  recreate the current state of that element in the set when it is 
  executed (which might be to add it, or to ensure that it doesn't
  exist).  Return the modified options map with :next-inner populated
  appropriately."
  [existing-map new-updated-map restore-vector]
  (cond
    (keyword? (first restore-vector))
      ; Handle basic key sequences, and ensure their current value
      ; shows up in :next-inner
      (let [ks-value (get-in existing-map restore-vector :unset)]
        (if (= ks-value :unset)
          (if (= (first restore-vector) :fn-map)
            ; We are operating on the :fn-map, where if something isn't
            ; set, when restoring things, it should be :none
            (update-next-inner new-updated-map restore-vector :none)
            new-updated-map)
          (update-next-inner new-updated-map restore-vector ks-value)
          #_(assoc-in options
              (concat [:next-inner] restore-vector)
              ks-value)))
    (sequential? (first restore-vector))
      ; Handle sets, and ensure that the set element given in
      ; (second restore-vector) is replicated (or not) in the set
      ; in :next-inner
      (let [ks (first restore-vector)
            set-element (second restore-vector)
            the-set (get-in existing-map ks :unset)]
        (if (or (= the-set :unset) (not (set? the-set)))
          ; We don't have a set, one way or another
          new-updated-map
          (let [element-exists? (the-set set-element)]
            (if element-exists?
              (update-next-inner new-updated-map ks #{set-element})
              #_(assoc-in options (concat [:next-inner] ks) #{set-element})
              (update-next-inner new-updated-map
                                 (concat [:remove] ks)
                                 #{set-element})
              #_(assoc-in options
                  (concat [:next-inner :remove] ks)
                  #{set-element})))))
    ; We don't have another other possibilities
    :else new-updated-map))

(defn process-restore
  "Given a map with a top-level :next-inner-restore key, the value of this key
  is a vector of vectors.  The vectors are either a sequence of keywords,
  which are a key-sequence of an element.  In this case, save the value
  of that element in the :next-inner map.  In the case where the first
  element in the vector is itself a vector of keywords, they that vector
  is a key-sequence to a set, and the second element of the vector is
  an element in the set.  In this case, modify the :next-inner map to
  recreate the current state of that element in the set when it is 
  executed (which might be to add it, or to ensure that it doesn't
  exist).  Return the modified options map with :next-inner populated
  accordingly"
  [new-updated-map existing-map]
  (dbg-s new-updated-map
         :next-inner-restore
         "process-restore: next-inner:" (:next-inner new-updated-map)
         "next-inner-restore:" (:next-inner-restore new-updated-map))
  (let [restore-vec (:next-inner-restore new-updated-map)
        new-updated-map
          (let [next-inner (:next-inner new-updated-map :unset)]
            ; Deal with existing :next-inner (or not)
            (cond
              ; Nothing there now, so that will be handled later
              (= next-inner :unset) new-updated-map
              ; We already have one, so create a vector
              ; and add it to the vector.
              (map? next-inner) (assoc new-updated-map
                                  :next-inner [{} next-inner])
              ; We already have a vector, so create an
              ; empty map for our new next-inner information
              (vector? next-inner) (assoc new-updated-map
                                     :next-inner (concat [{}] next-inner))
              ; This should be unreachable, one hopes
              :else new-updated-map))
        result (dissoc (reduce (partial restore-vector existing-map)
                         new-updated-map
                         restore-vec)
                       :next-inner-restore)]
    (dbg-s new-updated-map
           :next-inner-restore
           "process-restore: (:next-inner result)"
           (:next-inner result))
    result))

(defn internal-config
  "Do the internals of config-and-validate.  This is also what is
  called when styles are being processed.  Returns [updated-map
  new-doc-map error-str]"
  [doc-string doc-map existing-map new-map]
  (if (not (empty? new-map))
    (let [; remove set elements, and then remove the :remove key too
          [existing-map new-map new-doc-map]
            (perform-remove doc-string doc-map existing-map new-map)
          ; Before we do styles, if any, we need to make sure that the
          ; style map has all of the data, including the new style-map
          ; entries.
          existing-map (let [new-style-map (:style-map new-map)]
                         (if (nil? new-style-map)
                           existing-map
                           (merge-deep existing-map
                                       {:style-map (:style-map new-map)})))
          ; do style early, so other things in new-map can override it
          [updated-map new-doc-map style-errors]
            (apply-style doc-string new-doc-map existing-map new-map)
          ; Now that we've done the style, remove it so that the doc-map
          ; doesn't get overridden
          new-map (dissoc new-map :style)
          ; Do the actual merge of the majority of the data
          new-updated-map (merge-deep updated-map new-map)
          ; before we update the map, see if we have anything to remember
          ; in :next-inner-restore
          new-updated-map (if (:next-inner-restore new-updated-map)
                            (process-restore new-updated-map existing-map)
                            new-updated-map)
          new-doc-map (diff-deep-ks doc-string
                                    new-doc-map
                                    (key-seq (dissoc new-map
                                                     :next-inner-restore))
                                    new-updated-map)]
      [(add-calculated-options new-updated-map) new-doc-map style-errors])
    ; if we didn't get a map, just return something with no changes
    [existing-map doc-map nil]))

(defn config-and-validate
  "Validate a new map and merge it correctly into the existing
  options map.  You MUST do this whenever you have an options map
  which is to be merged into an existing options map, since a simple
  merge-deep will miss things like styles. Returns [updated-map
  new-doc-map error-str] It is important that this routine not throw
  exceptions on errors, but rather return them in the error-str.
  Various routines that call this one will wrap any errors in a
  larger context to make the response to the user more useful.
  Depends on existing-map to be the full, current options map!"
  ([doc-string doc-map existing-map new-map validate?]
   #?(:clj (do #_(println "\n\nconfig-and-validate: "
                          doc-string
                          new-map
                          "\n\n"
                          (get-stack-trace))))
   (if (not (empty? new-map))
     (let [_ #?(:clj (do #_(println "\n\nconfig-and-validate: "
                                    doc-string
                                    new-map
                                    "\n\n"
                                    #_(get-stack-trace)))
                :cljs nil)
           new-map (coerce-to-boolean new-map)
           errors (when validate? (validate-options new-map doc-string))
           ; The meat of this routine is internal-config
           [updated-map new-doc-map internal-errors]
             (internal-config doc-string
                              doc-map
                              ; Remove previous :styles-applied, if any
                              ; and start fresh.
                              (assoc existing-map :styles-applied [])
                              new-map)]
       [updated-map new-doc-map
        (if internal-errors (str errors " " internal-errors) errors)])
     ; if we didn't get a map, just return something with no changes
     [existing-map doc-map nil]))
  ([doc-string doc-map existing-map new-map]
   (config-and-validate doc-string doc-map existing-map new-map :validate)))

;;
;; # Configure all maps
;;

(defn config-and-validate-all
  "Do configuration and validation of options.
  op-options are options that control where to look for information.
  Returns [new-map doc-map errors]"
  [op-options]
  #_(println "config-and-validate-all!")
  ;
  ; NOTE WELL: When adding sections to the validation below, all of the
  ; error strings *must* appear in the list at the end.
  ;
  (let [default-map (get-default-options)
        default-doc-map (get-default-explained-all-options)
        ;
        ; Validate op-options
        ;
        [op-options _ op-option-errors] (config-and-validate
                                          "Operational options"
                                          default-doc-map
                                          default-map
                                          op-options)
        op-options (select-op-options op-options)
        #_(println "op-options:" op-options)
        ;
        ; $HOME/.zprintrc
        ;
        home #?(:clj (System/getenv "HOME")
                :cljs nil)
        file-separator #?(:clj (System/getProperty "file.separator")
                          :cljs nil)
        zprintrc-file (str home file-separator zprintrc)
        [opts-rcfile errors-rcfile rc-filename :as home-config]
          (when (and home file-separator)
            (get-config-from-path [zprintrc zprintedn] file-separator [home]))
        [updated-map new-doc-map rc-errors]
          (config-and-validate (str "Home directory file: " rc-filename)
                               default-doc-map
                               default-map
                               opts-rcfile)
        ;
        ; Search for .zprintrc or .zprint.edn in the current working
        ; directory, and all directories above that, if enabled by:
        ; {:search-config? true}.  If we ended up with the same info
        ; as we got above for the HOME config, ignore it.
        ;
        [search-rcfile search-errors-rcfile search-filename :as search-config]
          (when (and (or (:search-config? updated-map)
                         (:search-config? op-options))
                     file-separator)
            (scan-up-dir-tree [zprintrc zprintedn] file-separator))
        [search-rcfile search-errors-rcfile search-filename]
          (when (not= home-config search-config)
            [search-rcfile search-errors-rcfile search-filename])
        [search-map search-doc-map search-rc-errors]
          (config-and-validate (str ":search-config? file: " search-filename)
                               new-doc-map
                               updated-map
                               search-rcfile)
        ;
        ; Look for .zprintrc in current working directory if:
        ;   o we didn't search this directory already, above
        ;   o {:cwd-zprintrc? true}
        ;   o we have a file-separator
        ;
        [cwd-rcfile cwd-errors-rcfile cwd-filename]
          (when (and (not (:search-config? updated-map))
                     (or (:cwd-zprintrc? search-map)
                         (:cwd-zprintrc? op-options))
                     file-separator)
            (get-config-from-path [zprintrc zprintedn] file-separator ["."]))
        [updated-map new-doc-map cwd-rc-errors]
          (config-and-validate (str ":cwd-zprintrc? file: " cwd-filename)
                               search-doc-map
                               search-map
                               cwd-rcfile)
        ;
        ; Process errors together
        ;
        ; NOTE WELL: All of the errors above must appear in this list!
        all-errors (apply str
                     (interpose "\n"
                       (filter identity
                         (list op-option-errors
                               errors-rcfile
                               rc-errors
                               search-errors-rcfile
                               search-rc-errors
                               cwd-errors-rcfile
                               cwd-rc-errors))))
        all-errors (if (empty? all-errors) nil all-errors)]
    #_(println "finished config-and-validate-all!")
    [updated-map new-doc-map all-errors]))


;;
;; ## Parse a comment to see if it has an options map in it
;;

(defn get-options-from-comment
  "s is string containing a comment.  See if it starts out ;!zprint
  (with any number of ';' allowed), and if it does, attempt to parse
  it as an options-map.  Return [options error-str] with only options
  populated if it works, and only error-str if it doesn't.  Can't use
  config-and-validate here as we don't have anything to merge into.
  Use sci/eval-string to create sandboxed functions if any exist in
  the options map."
  [zprint-num s]
  (let [s-onesemi (clojure.string/replace s #"^;+" ";")
        comment-split (clojure.string/split s-onesemi #"^;!zprint ")]
    (when-let [possible-options (second comment-split)]
      (try
        [(sci-load-string possible-options) nil]
        (catch #?(:clj Exception
                  :cljs :default)
          e
          ; If it doesn't work, an error-str!
          [nil
           (str "Unable to create zprint options-map from: '" possible-options
                "' found in !zprint directive number: " zprint-num
                " because: " e)])))))

;;
;; # Help
;;

(defn vec-str-to-str
  "Take a vector of strings and concatenate them into one string with
  newlines between them."
  [vec-str]
  (apply str (interpose "\n" vec-str)))

;!zprint {:vector {:wrap? false}}

(def help-str
  (vec-str-to-str
    [(about)
     ""
     " The basic call uses defaults, prints to stdout"
     ""
     "   (zprint x)"
     ""
     " All zprint functions also allow the following arguments:"
     ""
     "   (zprint x <width>)"
     "   (zprint x <width> <options>)"
     "   (zprint x <options>)"
     ""
     " Format a function to stdout (accepts arguments as above)"
     ""
     "   (zprint-fn <fn-name>)"
     ""
     " Output to a string instead of stdout:"
     ""
     "   (zprint-str x)"
     "   (zprint-fn-str <fn-name>)"
     ""
     " Syntax color output for an ANSI terminal:"
     ""
     "   (czprint x)"
     "   (czprint-fn <fn-name>)"
     "   (czprint-str x)"
     "   (czprint-fn-str <fn-name>)"
     ""
     " The first time you call a zprint printing function, it configures"
     " itself from $HOME/.zprintrc."
     ""
     " Explain current configuration, shows all possible configurable"
     " values as well as source of non-default values:"
     ""
     "   (zprint nil :explain)"
     ""
     " Change current configuration from running code:"
     ""
     "   (set-options! <options>)"
     ""
     " Format a complete file (recognizing ;!zprint directives):"
     ""
     "   (zprint-file infile file-name outfile <options>)"
     ""
     " Format a string containing multiple \"top level\" forms, essentially"
     " a file contained in a string, (recognizing ;!zprint directives):"
     ""
     "   (zprint-file-str file-str zprint-specifier <options> <doc-str>)"
     ""
     " Output information to include when submitting an issue:"
     ""
     "   (zprint nil :support)"
     ""]))
