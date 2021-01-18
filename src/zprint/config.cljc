(ns ^:no-doc zprint.config
  #?(:clj [:refer-clojure :exclude [read-string]])
  (:require clojure.string
            [clojure.set :refer [difference]]
            [clojure.data :as d]
            [zprint.spec :refer [validate-basic coerce-to-boolean]]
            [zprint.rewrite :refer [sort-dependencies]]
            [sci.core :as sci]
            #?(:clj [clojure.edn :refer [read-string]]
               :cljs [cljs.reader :refer [read-string]]))
  #?@(:clj [(:import (java.io InputStreamReader FileReader BufferedReader)
                     (java.util.concurrent.locks ReentrantLock))]))

;;
;; # Configuration
;;
;; Handles incoming configuration, validation of option maps,
;; contains atoms holding the current options map.
;;

;;
;; # Program Version
;;

(defn about "Return version of this program." [] (str "zprint-1.1.1"))

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
  [:configured? :dbg-print? :dbg? :force-eol-blanks? :do-in-hang? :drop? :dbg-ge
   :file? :spaces? :process-bang-zprint? :trim-comments? :zipper? :indent
   :remove :return-cvec? :test-for-eol-blanks?
   [:object :wrap-after-multi? :wrap-coll?] [:reader-cond :comma?]
   [:pair :justify-hang :justify-tuning]
   [:binding :justify-hang :justify-tuning] [:spec :value]
   [:map :dbg-local? :hang-adjust :justify-hang :justify-tuning] :tuning
   :perf-vs-format [:url :cache-path]
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

(def zfnstyle
  {"->" [:noarg1-body
         {:list {:constant-pair? false},
          :next-inner {:list {:constant-pair? true}}}],
   "->>" [:force-nl-body
          {:list {:constant-pair? false},
           :next-inner {:list {:constant-pair? true}}}],
   ":import" :force-nl-body,
   ":require" :force-nl-body,
   "=" :hang,
   "alt" :pair-fn,
   "and" :hang,
   "apply" :arg1,
   "as->" :arg2,
   "assert-args" :pair-fn,
   "assoc" :arg1-pair,
   "assoc-in" :arg1,
   "binding" :binding,
   "case" :arg1-pair-body,
   "cat" :force-nl,
   "catch" :arg2,
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
                {:style :respect-nl, :next-inner {:style :respect-nl-off}}],
   "defmethod" :arg2,
   "defmulti" :arg1-body,
   "defn" :arg1-body,
   "defn-" :arg1-body,
   "defproject" [:arg2-pair {:vector {:wrap? false}}],
   "defprotocol" :arg1-force-nl,
   "defrecord" :arg2-extend,
   "deftest" :arg1-body,
   "deftype" :arg2-extend,
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
   "letfn" :binding,
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
   "with-redefs-fn" :arg1-body})

;;
;; ## The global defaults
;;

(def default-zprint-options
  {:agent {:object? false},
   :array {:hex? false, :indent 1, :object? false, :wrap? true},
   :atom {:object? false},
   :binding {:flow? false,
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
                  :arg1-force-nl :flow-body},
   :fn-gt2-force-nl #{:gt2-force-nl :binding :pair-fn},
   :fn-gt3-force-nl #{:gt3-force-nl :arg1-pair :arg1-pair-body},
   :fn-map zfnstyle,
   :fn-name nil,
   :fn-obj {:object? false},
   :force-eol-blanks? false,
   :format :on,
   :future {:object? false},
   ; This is used for {:parse {:left-space :keep}}
   :indent 0,
   :input {:range {:start nil, :end nil}},
   ; When you change :list, you should also change :vector-fn, since it
   ; becomes the current :list when
   :list {:constant-pair-fn nil,
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
          :replacement-string nil},
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
         :lift-ns? false,
         :lift-ns-in-code? false,
         :force-nl? nil,
         :nl-separator? false,
         :nl-separator-all? false,
         :flow? false,
         :justify? false,
         :justify-hang {:hang-expand 5},
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
   :object {:indent 1, :wrap-after-multi? true, :wrap-coll? true},
   :old? true,
   :output {:focus {:zloc? false, :surround nil}, :lines nil, :elide nil},
   :pair {:flow? false,
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
          :justify? false,
          :nl-separator? false,
          :nl-separator-all? false},
   :pair-fn {:hang-diff 1, :hang-expand 2.0, :hang-size 10, :hang? true},
   :parse {:interpose nil, :left-space :drop},
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
            :extend {:modifiers nil}},
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
     {:all-hang {:map {:hang? true},
                 :list {:hang? true},
                 :extend {:hang? true},
                 :pair {:hang? true},
                 :pair-fn {:hang? true},
                 :reader-cond {:hang? true},
                 :record {:hang? true}},
      :backtranslate
        {:fn-map
           {"quote" [:replace-w-string {} {:list {:replacement-string "'"}}],
            "clojure.core/deref" [:replace-w-string {}
                                  {:list {:replacement-string "@"}}],
            "var" [:replace-w-string {} {:list {:replacement-string "#'"}}],
            "clojure.core/unquote" [:replace-w-string {}
                                    {:list {:replacement-string "~"}}]}},
      :binding-nl {:binding {:indent 0, :nl-separator? true}},
      :binding-nl-all {:binding {:indent 0, :nl-separator-all? true}},
      :community {:binding {:indent 0},
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
      :dark-color-map {:color-map {:brace :white,
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
      :extend-nl {:extend {:flow? true, :indent 0, :nl-separator? true}},
      :how-to-ns {:fn-map {"ns" [:arg1-body
                                 {:fn-map {":import" [:flow
                                                      {:list {:hang? true}}],
                                           ":require" :flow},
                                  :list {:hang? false, :indent-arg 1}}]}},
      :hiccup {:vector
                 {:option-fn
                    (fn [opts n exprs]
                      (let [hiccup? (and (>= n 2)
                                         (or (keyword? (first exprs))
                                             (symbol? (first exprs)))
                                         (map? (second exprs)))]
                        (cond (and hiccup? (not (:fn-format (:vector opts))))
                                {:vector {:fn-format :arg1-force-nl}}
                              (and (not hiccup?) (:fn-format (:vector opts)))
                                {:vector {:fn-format nil}}
                              :else nil))),
                  :wrap? false},
               :vector-fn {:indent 1, :indent-arg 1}},
      :indent-only {:comment {:wrap? false},
                    :list {:indent-only? true},
                    :map {:indent-only? true},
                    :set {:indent-only? true},
                    ; Should we also set :vector-fn to :indent-only?  That
                    ; is only used by :fn-format, so it might confuse people
                    ; if we did that.
                    :vector {:indent-only? true}},
      :justified {:binding {:justify? true},
                  :map {:justify? true},
                  :pair {:justify? true}},
      :keyword-respect-nl {:vector
                             {:option-fn-first
                                #(let [k? (keyword? %2)]
                                   (when (not= k? (:respect-nl? (:vector %1)))
                                     {:vector {:respect-nl? k?}}))}},
      :map-nl {:map {:indent 0, :nl-separator? true}},
      :map-nl-all {:map {:indent 0, :nl-separator-all? true}},
      :moustache {:fn-map {"app" [:flow {:style :vector-pairs}]}},
      :vector-pairs {:list {:constant-pair-min 1,
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
      :no-hang {:map {:hang? false},
                :list {:hang? false},
                :extend {:hang? false},
                :pair {:hang? false},
                :pair-fn {:hang? false},
                :reader-cond {:hang? false},
                :record {:hang? false}},
      :pair-nl {:pair {:indent 0, :nl-separator? true}},
      :pair-nl-all {:pair {:indent 0, :nl-separator-all? true}},
      :fast-hang {:binding {:hang-accept 100, :ha-width-factor -600},
                  :extend {:hang-accept 100, :ha-width-factor -600},
                  :list {:hang-accept 100, :ha-width-factor -300},
                  :map {:hang-accept 0, :ha-depth-factor 15},
                  :pair {:hang-accept 20, :ha-width-factor -150},
                  :vector-fn {:hang-accept 100, :ha-width-factor -300}},
      :respect-bl {:list {:respect-bl? true},
                   :map {:respect-bl? true},
                   :vector {:respect-bl? true},
                   :set {:respect-bl? true}},
      :respect-bl-off {:list {:respect-bl? false},
                       :map {:respect-bl? false},
                       :vector {:respect-bl? false},
                       :set {:respect-bl? false}},
      :respect-nl {:list {:respect-nl? true},
                   :map {:respect-nl? true},
                   :vector {:respect-nl? true},
                   :set {:respect-nl? true}},
      :respect-nl-off {:list {:respect-nl? false},
                       :map {:respect-nl? false},
                       :vector {:respect-nl? false},
                       :set {:respect-nl? false}},
      :sort-dependencies {:list {:return-altered-zipper [1 'defproject
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
            :respect-bl? false,
            :respect-nl? false,
            :wrap-after-multi? true,
            :wrap-coll? true,
            :wrap? true,
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
               :respect-nl? false},
   :width 80,
   :url {:cache-dir "urlcache", :cache-secs 300},
   :zipper? false})

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

(defn dissoc-two
  "Do a simple dissoc-in for two levels.  Does not remove the
  second map if it is empty."
  [m [k1 k2]]
  (assoc m k1 (dissoc (get m k1) k2)))

(defn add-calculated-options
  "Take an updated-map and generate calculated options
  from it.  Takes the updated-map and further updates
  it, being smart about things that were set to nil."
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
  #_@configured-options
  (assoc @configured-options :version (about)))

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

(defn internal-set-options!
  "Validate the new options, and update both the saved options
  and the doc-map as well.  Will throw an exceptino for errors."
  [doc-string doc-map existing-options new-options]
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

#?(:clj (defn current-stack-trace [] (.getStackTrace (Thread/currentThread))))
#?(:clj (defn is-repl-stack-element
          [^java.lang.StackTraceElement stack-element]
          (and (= "clojure.main$repl" (.getClassName stack-element))
               (= "doInvoke" (.getMethodName stack-element)))))

(defn is-in-repl?
  []
  #?(:clj (some is-repl-stack-element (current-stack-trace))
     :cljs nil))

(defn select-op-options
  "Given an options map, return an options map with only the operational
  options remaining."
  [options]
  (select-keys options operational-options))

;;
;; End "detect in a REPL" code

(defn config-configure-all!
  "Do external configuration regardless of whether or not it has
  already been done, replacing any internal configuration.  Returns
  nil if successful, a vector of errors if not. "
  ([op-options]
   ; Any config changes prior to this will be lost, as
   ; config-and-validate-all works from the default options!
   (let [[zprint-options doc-map errors]
           (config-and-validate-all nil nil op-options)]
     (if errors
       errors
       (do (reset-options! zprint-options doc-map)
           (config-set-options! {:configured? true} "internal")
           ; If we are running in a repl,
           ; then turn on :parallel?
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

(defn config-set-options!
  "Add some options to the current options, checking to make sure
  that they are correct. op-options are operational options that
  affect where to get options or how to do the processing, but do
  not affect the format of the output directly."
  ([new-options doc-str op-options]
   ; avoid infinite recursion, while still getting the doc-map updated
   (let [error-vec (when (and (not (:configured? (get-options)))
                              (not (:configured? new-options)))
                     (config-configure-all! op-options))]
     (when error-vec
       (throw
         (#?(:clj Exception.
             :cljs js/Error.)
          (str "set-options! for " doc-str " found these errors: " error-vec))))
     (internal-set-options! doc-str
                            (get-explained-all-options)
                            (get-options)
                            new-options)))
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

(defn apply-one-style-alt
  "Take a [doc-string new-map [existing-map doc-map error-str] style-name]
  and produce a new [existing-map doc-map error-str] from the style defined
  in the new-map if it exists, or the existing-map if it doesn't."
  [doc-string new-map [existing-map doc-map error-str] style-name]
  (if (or (= style-name :not-specified) (nil? style-name))
    [existing-map doc-map nil]
    (let [style-map (if (= style-name :default)
                      (get-default-options)
                      (or (get-in new-map [:style-map style-name])
                          (get-in existing-map [:style-map style-name])))]
      (if style-map
        (let [updated-map (merge-deep existing-map style-map)]
          [updated-map
           (when doc-map
             (diff-deep-ks (str doc-string " specified :style " style-name)
                           doc-map
                           (key-seq style-map)
                           updated-map)) nil])
        [existing-map doc-map (str "Style '" style-name "' not found!")]))))

(declare apply-style)
(defn apply-one-style-alt1
  "Take a [doc-string new-map [existing-map doc-map error-str] style-name]
  and produce a new [updated-map doc-map error-str] from the style defined
  in the new-map if it exists, or the existing-map if it doesn't."
  [doc-string new-map [existing-map doc-map error-str] style-name]
  (if (or (= style-name :not-specified) (nil? style-name))
    [existing-map doc-map nil]
    (let [style-map (if (= style-name :default)
                      (get-default-options)
                      (or (get-in new-map [:style-map style-name])
                          (get-in existing-map [:style-map style-name])))]
      (if style-map
        (let [updated-map (merge-deep existing-map style-map)
              updated-doc-map
                (when doc-map
                  (diff-deep-ks (str doc-string " specified :style " style-name)
                                doc-map
                                (key-seq style-map)
                                updated-map))]
          ; Apply any recursive styles
          (apply-style doc-string updated-doc-map updated-map new-map))
        [existing-map doc-map (str "Style '" style-name "' not found!")]))))

(defn apply-one-style
  "Take a [doc-string new-map [existing-map doc-map error-str] style-name]
  and produce a new [existing-map doc-map error-str] from the style defined
  in the new-map if it exists, or the existing-map if it doesn't."
  [doc-string new-map [existing-map doc-map error-str] style-name]
  (if (or (= style-name :not-specified) (nil? style-name))
    [existing-map doc-map nil]
    (let [style-map (if (= style-name :default)
                      (get-default-options)
                      (or (get-in new-map [:style-map style-name])
                          (get-in existing-map [:style-map style-name])))]
      (if style-map
        (let [updated-map (merge-deep existing-map style-map)
              updated-map (assoc updated-map
                            :styles-applied (conj
                                              (if (:styles-applied updated-map)
                                                (:styles-applied updated-map)
                                                [])
                                              style-name))
              doc-map (when doc-map
                        (assoc doc-map
                          :styles-applied (:styles-applied updated-map)))]
          [updated-map
           (when doc-map
             (diff-deep-ks (str doc-string " specified :style " style-name)
                           doc-map
                           (key-seq style-map)
                           updated-map)) nil])
        [existing-map doc-map (str "Style '" style-name "' not found!")]))))

(defn apply-style
  "Given an existing-map and a new-map, if the new-map specifies a
  style, apply it if it exists, looking first in the new-map for the style
  and then in the existing-map for the style.  Otherwise do nothing. 
  Return [updated-map new-doc-map error-string]"
  ([doc-string doc-map existing-map new-map styles-applied]
   (let [style-name (get new-map :style :not-specified)]
     (if (or (= style-name :not-specified) (nil? style-name))
       [existing-map doc-map nil]
       (if (some #(= % style-name) styles-applied)
         ; We have already done this style
         (throw (#?(:clj Exception.
                    :cljs js/Error.)
                 (str "Circular style error: style " style-name
                      " has already been applied: " styles-applied)))
         (let [[updated-map new-doc-map error-string]
                 (if (not (coll? style-name))
                   (apply-one-style doc-string
                                    new-map
                                    [existing-map doc-map nil]
                                    style-name)
                   (reduce (partial apply-one-style doc-string new-map)
                     [existing-map doc-map nil]
                     style-name))
               another-style-name (get updated-map :style :not-specified)]
           ; Do we have a style invocation in the updated-map?
           (if (or (= another-style-name :not-specified)
                   (nil? another-style-name))
             [updated-map new-doc-map error-string]
             ; Yes, we have a style invocation in the updated-map, so Move
             ; the style invocation from the updated-map to the new-map, and
             ; keep at it until we don't have a style in the updated-map
             (recur doc-string
                    new-doc-map
                    (dissoc updated-map :style)
                    (assoc new-map :style another-style-name)
                    (reduce conj
                      styles-applied
                      (if (keyword? style-name) [style-name] style-name)))))))))
  ([doc-string doc-map existing-map new-map]
   (apply-style doc-string doc-map existing-map new-map [])))

(defn apply-style-alt
  "Given an existing-map and a new-map, if the new-map specifies a
  style, apply it if it exists, looking first in the new-map for the style
  and then in the existing-map for the style.  Otherwise do nothing. 
  Return [updated-map new-doc-map error-string]"
  [doc-string doc-map existing-map new-map]
  (let [style-name (get new-map :style :not-specified)]
    (if (or (= style-name :not-specified) (nil? style-name))
      [existing-map doc-map nil]
      (if (not (coll? style-name))
        (apply-one-style doc-string
                         new-map
                         [existing-map doc-map nil]
                         style-name)
        (reduce (partial apply-one-style doc-string new-map)
          [existing-map doc-map nil]
          style-name)))))


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

(defn try-to-load-string
  "Read an options map from a string using sci/eval-string to read
  in the structure, and to create sandboxed function for any functions
  defined in the options-map (i.e. in the string).  Any failures
  from eval-string are not caught and propagate back up the call
  stack."
  [s]
  #?(:clj (sci/eval-string s)
     :cljs nil))

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
              (try (let [opts-file (try-to-load-string
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
    (try (let [opts-map (sci/eval-string map-string)] [opts-map nil])
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

(defn config-and-validate
  "Do a single new map. Returns [updated-map new-doc-map error-vec]
  Depends on existing-map to be the full, current options map!"
  [doc-string doc-map existing-map new-map]
  #_(println "config-and-validate:" new-map)
  (if new-map
    (let [new-map (coerce-to-boolean new-map)
          errors (validate-options new-map doc-string)
          ; remove set elements, and then remove the :remove key too
          [existing-map new-map new-doc-map]
            (perform-remove doc-string doc-map existing-map new-map)
          ; do style early, so other things in new-map can override it
          [updated-map new-doc-map style-errors]
            (apply-style doc-string new-doc-map existing-map new-map)
          ; Now that we've done the style, remove it so that the doc-map
          ; doesn't get overridden
          new-map (dissoc new-map :style)
          errors (if style-errors (str errors " " style-errors) errors)
          new-updated-map (merge-deep updated-map new-map)
          new-doc-map (diff-deep-ks doc-string
                                    new-doc-map
                                    (key-seq new-map)
                                    new-updated-map)]
      [new-updated-map new-doc-map errors])
    ; if we didn't get a map, just return something with no changes
    [existing-map doc-map nil]))

;;
;; # Configure all maps
;;

(defn config-and-validate-all
  "Take the opts and errors from the command line arguments, if any,
  and do the rest of the configuration and validation along the way.  
  op-options are options that control where to look for information.
  Left the config map, config file, and cli processing in place in case
  we go replace the uberjar capability soon.  
  Returns [new-map doc-map errors]"
  [cli-opts cli-errors op-options]
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
            (get-config-from-path [zprintrc zprintedn]
                                  file-separator
                                  [home]
                                  ; Do accept fns from this file
                                  #_:acceptfns))
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
            (get-config-from-path [zprintrc zprintedn]
                                  file-separator
                                  ["."]
                                  ; Do not accept fns from this file
                                  #_nil))
        [cwd-updated-map cwd-new-doc-map cwd-rc-errors]
          (config-and-validate (str ":cwd-zprintrc? file: " cwd-filename)
                               search-doc-map
                               search-map
                               cwd-rcfile)
        ;
        ; environment variables -- requires zprint on front
        ;
        read-system-env-fn #?(:clj (resolve 'cprop.source/read-system-env)
                              :cljs nil)
        env-map (when read-system-env-fn (read-system-env-fn))
        env-and-default-map (merge-existing {:zprint default-map} env-map)
        new-env-map (diff-map default-map (:zprint env-and-default-map))
        new-env-map (update-fn-map new-env-map env-map)
        new-env-map (map-leaves strtf->boolean new-env-map)
        [updated-map new-doc-map env-errors] (config-and-validate
                                               (str "Environment variable")
                                               cwd-new-doc-map
                                               cwd-updated-map
                                               new-env-map)
        ;
        ; System properties -- requires zprint on front
        ;
        read-system-props-fn #?(:clj (resolve 'cprop.source/read-system-props)
                                :cljs nil)
        prop-map (when read-system-props-fn (read-system-props-fn))
        prop-and-default-map (merge-existing {:zprint default-map} prop-map)
        new-prop-map (diff-map default-map (:zprint prop-and-default-map))
        new-prop-map (update-fn-map new-prop-map prop-map)
        new-prop-map (map-leaves strtf->boolean new-prop-map)
        [updated-map new-doc-map prop-errors] (config-and-validate
                                                (str "System property")
                                                new-doc-map
                                                updated-map
                                                new-prop-map)
        ;
        ; --config FILE
        ;
        config-filename #?(:clj (:config cli-opts)
                           :cljs nil)
        [opts-configfile errors-configfile config-filename]
          (when config-filename (get-config-from-file config-filename))
        [updated-map new-doc-map config-file-errors]
          (config-and-validate (str "Config file: " config-filename)
                               new-doc-map
                               updated-map
                               opts-configfile)
        ;
        ; --config-map STRING
        ;
        [opts-configmap errors-configmap] (get-config-from-map (:config-map
                                                                 cli-opts))
        [updated-map new-doc-map config-map-errors]
          (config-and-validate (str "Config map:" (:config-map cli-opts))
                               new-doc-map
                               updated-map
                               opts-configmap)
        ;
        ; command line options
        ;
        opts-cli (select-keys cli-opts zprint-keys)
        [updated-map new-doc-map cli-errors] (config-and-validate
                                               (str "CLI options")
                                               new-doc-map
                                               updated-map
                                               opts-cli)
        ;
        ; Process errors together
        ;
        ; NOTE WELL: All of the errors above must appear in this list!
        all-errors (apply str
                     (interpose "\n"
                       (filter identity
                         (list errors-rcfile
                               rc-errors
                               cwd-errors-rcfile
                               cwd-rc-errors
                               search-errors-rcfile
                               search-rc-errors
                               env-errors
                               prop-errors
                               errors-configfile
                               config-file-errors
                               config-map-errors
                               cli-errors
                               op-option-errors))))
        all-errors (if (empty? all-errors) nil all-errors)]
    [updated-map new-doc-map all-errors]))

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
