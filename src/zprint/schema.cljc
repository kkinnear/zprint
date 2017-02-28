(ns zprint.schema
  (:require [schema.core :as s :refer [defschema]]))

;;
;; # Schema for Validation
;;

;!zprint {:fn-map {"defschema" :arg1}}

(defschema color-schema
  "An enum of the possible colors"
  (s/enum :green :purple :magenta :yellow :red :cyan :black :blue))

(defschema color-or-nil-schema (s/conditional nil? s/Any :else color-schema))

(defschema format-schema
  "An enum of the possible format options"
  (s/enum :on :off :next :skip))

(defschema keep-drop-schema (s/enum :keep :drop))

(defschema fn-type
  "An enum of the possible function types"
  (s/enum :binding
          :arg1 :arg1-body
          :arg1-pair-body :arg1-pair
          :pair :hang
          :extend :arg1-extend-body
          :arg1-extend :fn
          :arg1-> :noarg1-body
          :noarg1 :arg2
          :arg2-pair :arg2-fn
          :none :none-body
          :arg1-force-nl :gt2-force-nl
          :gt3-force-nl :flow
          :flow-body :force-nl-body
          :force-nl :pair-fn))

;;
;; There is no schema for possible styles, because people
;; can define their own.  There is special code to check to
;; see if a style specification matches one of the defined
;; styles, and if it doesn't, it is dealt with there.
;;

(defschema boolean-schema
  "Our version of boolen, which is true, false, and nil"
  (s/conditional nil? s/Any :else s/Bool))

(defschema boolean-schema-or-string
  "Our version of boolen, which is true, false, and nil or string"
  (s/conditional string? s/Any :else boolean-schema))

(defschema num-or-nil-schema (s/conditional nil? s/Any :else s/Num))

(defschema keyword-or-nil-schema (s/conditional nil? s/Any :else s/Keyword))

(defschema keyword-nil-or-keyword-list-schema
  (s/conditional coll? [s/Keyword] :else keyword-or-nil-schema))

(defschema color-map
  {(s/optional-key :paren) color-schema,
   (s/optional-key :bracket) color-schema,
   (s/optional-key :brace) color-schema,
   (s/optional-key :deref) color-schema,
   (s/optional-key :hash-brace) color-schema,
   (s/optional-key :hash-paren) color-schema,
   (s/optional-key :comment) color-schema,
   (s/optional-key :fn) color-schema,
   (s/optional-key :user-fn) color-schema,
   (s/optional-key :string) color-schema,
   (s/optional-key :keyword) color-schema,
   (s/optional-key :number) color-schema,
   (s/optional-key :uneval) color-schema,
   (s/optional-key :nil) color-schema,
   (s/optional-key :quote) color-schema,
   (s/optional-key :none) color-schema})

(defschema zprint-options-schema
  "Use this to validate input, so ensure that people don't forget
  things like the ? on the end of booleans and such."
  {(s/optional-key :configured?) boolean-schema,
   (s/optional-key :style) keyword-nil-or-keyword-list-schema,
   (s/optional-key :width) s/Num,
   (s/optional-key :indent) s/Num,
   (s/optional-key :trim-comments?) boolean-schema,
   (s/optional-key :process-bang-zprint?) boolean-schema,
   (s/optional-key :max-depth) s/Num,
   (s/optional-key :max-length) s/Num,
   (s/optional-key :max-hang-depth) s/Num,
   (s/optional-key :max-hang-count) s/Num,
   (s/optional-key :max-hang-span) s/Num,
   (s/optional-key :parse-string?) boolean-schema,
   (s/optional-key :parse-string-all?) boolean-schema,
   (s/optional-key :zipper?) boolean-schema,
   (s/optional-key :file?) boolean-schema,
   (s/optional-key :spaces?) boolean-schema,
   (s/optional-key :old?) boolean-schema,
   (s/optional-key :parallel?) boolean-schema,
   (s/optional-key :format) format-schema,
   (s/optional-key :return-cvec?) boolean-schema,
   (s/optional-key :fn-name) s/Any,
   (s/optional-key :auto-width?) boolean-schema,
   (s/optional-key :force-sexpr?) boolean-schema,
   (s/optional-key :spec) {(s/optional-key :value) s/Any,
                           (s/optional-key :docstring?) boolean-schema},
   (s/optional-key :tuning) {(s/optional-key :hang-flow) s/Num,
                             (s/optional-key :hang-type-flow) s/Num,
                             (s/optional-key :hang-flow-limit) s/Num,
                             (s/optional-key :general-hang-adjust) s/Num,
                             (s/optional-key :hang-if-equal-flow?)
                               boolean-schema},
   (s/optional-key :color-map) color-map,
   (s/optional-key :uneval) {(s/optional-key :color-map) color-map},
   (s/optional-key :fn-map) {s/Str fn-type},
   (s/optional-key :fn-force-nl) #{fn-type},
   (s/optional-key :fn-gt2-force-nl) #{fn-type},
   (s/optional-key :fn-gt3-force-nl) #{fn-type},
   (s/optional-key :remove) {(s/optional-key :fn-force-nl) #{fn-type},
                             (s/optional-key :fn-gt2-force-nl) #{fn-type},
                             (s/optional-key :fn-gt3-force-nl) #{fn-type},
                             (s/optional-key :extend)
                               {(s/optional-key :modifiers) #{s/Str}}},
   (s/optional-key :user-fn-map) {s/Str fn-type},
   (s/optional-key :vector) {(s/optional-key :indent) s/Num,
                             (s/optional-key :wrap?) boolean-schema,
                             (s/optional-key :wrap-coll?) boolean-schema,
                             (s/optional-key :wrap-after-multi?)
                               boolean-schema},
   (s/optional-key :set) {(s/optional-key :indent) s/Num,
                          (s/optional-key :wrap?) boolean-schema,
                          (s/optional-key :wrap-coll?) boolean-schema,
                          (s/optional-key :wrap-after-multi?) boolean-schema},
   (s/optional-key :object) {(s/optional-key :indent) s/Num,
                             (s/optional-key :wrap-coll?) boolean-schema,
                             (s/optional-key :wrap-after-multi?)
                               boolean-schema},
   (s/optional-key :list) {(s/optional-key :indent-arg) num-or-nil-schema,
                           (s/optional-key :indent) s/Num,
                           (s/optional-key :hang?) boolean-schema,
                           (s/optional-key :pair-hang?) boolean-schema,
                           (s/optional-key :hang-expand) s/Num,
                           (s/optional-key :hang-diff) s/Num,
                           (s/optional-key :hang-size) s/Num,
                           (s/optional-key :constant-pair?) boolean-schema,
                           (s/optional-key :constant-pair-min) s/Num},
   (s/optional-key :pair-fn) {(s/optional-key :hang?) boolean-schema,
                              (s/optional-key :hang-expand) s/Num,
                              (s/optional-key :hang-size) s/Num,
                              (s/optional-key :hang-diff) s/Num},
   (s/optional-key :map)
     {(s/optional-key :indent) s/Num,
      (s/optional-key :hang?) boolean-schema,
      (s/optional-key :hang-expand) s/Num,
      (s/optional-key :hang-diff) s/Num,
      (s/optional-key :hang-adjust) s/Num,
      (s/optional-key :sort?) boolean-schema,
      (s/optional-key :sort-in-code?) boolean-schema,
      (s/optional-key :comma?) boolean-schema,
      (s/optional-key :dbg-local?) boolean-schema,
      (s/optional-key :key-order) [s/Any],
      (s/optional-key :key-ignore) [s/Any],
      (s/optional-key :key-ignore-silent) [s/Any],
      (s/optional-key :key-color) {s/Any color-schema},
      (s/optional-key :key-depth-color) [color-or-nil-schema],
      (s/optional-key :flow?) boolean-schema,
      (s/optional-key :nl-separator?) boolean-schema,
      (s/optional-key :force-nl?) boolean-schema,
      (s/optional-key :justify?) boolean-schema,
      (s/optional-key :justify-hang) {(s/optional-key :hang?) boolean-schema,
                                      (s/optional-key :hang-expand) s/Num,
                                      (s/optional-key :hang-diff) s/Num},
      (s/optional-key :justify-tuning) {(s/optional-key :hang-flow) s/Num,
                                        (s/optional-key :hang-type-flow) s/Num,
                                        (s/optional-key :hang-flow-limit) s/Num,
                                        (s/optional-key :general-hang-adjust)
                                          s/Num}},
   (s/optional-key :extend) {(s/optional-key :indent) s/Num,
                             (s/optional-key :hang?) boolean-schema,
                             (s/optional-key :hang-expand) s/Num,
                             (s/optional-key :hang-diff) s/Num,
                             (s/optional-key :nl-separator?) boolean-schema,
                             (s/optional-key :flow?) boolean-schema,
                             (s/optional-key :force-nl?) boolean-schema,
                             (s/optional-key :modifiers) #{s/Str}},
   (s/optional-key :reader-cond) {(s/optional-key :indent) s/Num,
                                  (s/optional-key :hang?) boolean-schema,
                                  (s/optional-key :hang-expand) s/Num,
                                  (s/optional-key :hang-diff) s/Num,
                                  (s/optional-key :sort?) boolean-schema,
                                  (s/optional-key :sort-in-code?)
                                    boolean-schema,
                                  (s/optional-key :comma?) boolean-schema,
                                  (s/optional-key :force-nl?) boolean-schema,
                                  (s/optional-key :key-order) [s/Any]},
   (s/optional-key :binding) {(s/optional-key :indent) s/Num,
                              (s/optional-key :hang?) boolean-schema,
                              (s/optional-key :hang-expand) s/Num,
                              (s/optional-key :hang-diff) s/Num,
                              (s/optional-key :flow?) boolean-schema,
                              (s/optional-key :force-nl?) boolean-schema,
                              (s/optional-key :nl-separator?) boolean-schema,
                              (s/optional-key :justify?) boolean-schema,
                              (s/optional-key :justify-hang)
                                {(s/optional-key :hang?) boolean-schema,
                                 (s/optional-key :hang-expand) s/Num,
                                 (s/optional-key :hang-diff) s/Num},
                              (s/optional-key :justify-tuning)
                                {(s/optional-key :hang-flow) s/Num,
                                 (s/optional-key :hang-type-flow) s/Num,
                                 (s/optional-key :hang-flow-limit) s/Num,
                                 (s/optional-key :general-hang-adjust) s/Num}},
   (s/optional-key :pair) {(s/optional-key :indent) s/Num,
                           (s/optional-key :hang?) boolean-schema,
                           (s/optional-key :hang-expand) s/Num,
                           (s/optional-key :hang-diff) s/Num,
                           (s/optional-key :flow?) boolean-schema,
                           (s/optional-key :force-nl?) boolean-schema,
                           (s/optional-key :nl-separator?) boolean-schema,
                           (s/optional-key :justify?) boolean-schema,
                           (s/optional-key :justify-hang)
                             {(s/optional-key :hang?) boolean-schema,
                              (s/optional-key :hang-expand) s/Num,
                              (s/optional-key :hang-diff) s/Num},
                           (s/optional-key :justify-tuning)
                             {(s/optional-key :hang-flow) s/Num,
                              (s/optional-key :hang-type-flow) s/Num,
                              (s/optional-key :hang-flow-limit) s/Num,
                              (s/optional-key :general-hang-adjust) s/Num}},
   (s/optional-key :record) {(s/optional-key :record-type?) boolean-schema,
                             (s/optional-key :hang?) boolean-schema,
                             (s/optional-key :to-string?) boolean-schema},
   (s/optional-key :array) {(s/optional-key :hex?) boolean-schema-or-string,
                            (s/optional-key :object?) boolean-schema,
                            (s/optional-key :indent) s/Num,
                            (s/optional-key :wrap?) boolean-schema},
   (s/optional-key :atom) {(s/optional-key :object?) boolean-schema},
   (s/optional-key :fn-obj) {(s/optional-key :object?) boolean-schema},
   (s/optional-key :future) {(s/optional-key :object?) boolean-schema},
   (s/optional-key :promise) {(s/optional-key :object?) boolean-schema},
   (s/optional-key :delay) {(s/optional-key :object?) boolean-schema},
   (s/optional-key :agent) {(s/optional-key :object?) boolean-schema},
   (s/optional-key :parse) {(s/optional-key :left-space) keep-drop-schema,
                            (s/optional-key :interpose)
                              boolean-schema-or-string},
   (s/optional-key :tab) {(s/optional-key :expand?) boolean-schema,
                          (s/optional-key :size) s/Num},
   (s/optional-key :comment) {(s/optional-key :count?) boolean-schema,
                              (s/optional-key :wrap?) boolean-schema},
   (s/optional-key :dbg?) boolean-schema,
   (s/optional-key :dbg-print?) boolean-schema,
   (s/optional-key :dbg-ge) s/Any,
   (s/optional-key :drop?) boolean-schema,
   (s/optional-key :do-in-hang?) boolean-schema})



;;
;; # Schema Validation Functions
;;

; We could have called zprint.sutil/sconstant?, but the idea is to
; keep zprint.schema and zprint.spec namespaces as leaves with
; minimal dependencies.

(defn constants
  "Ensure that all of the elements of this collection are constants."
  [coll]
  (reduce #(and %1 (or (keyword? %2) (string? %2) (number? %2))) true coll))

(defn empty-2-nil
  "If the sequence is empty, then return nil, else return the sequence."
  [empty-seq]
  (when-not (empty? empty-seq) empty-seq))

;;
;;  # Use schema
;;

(defn validate-basic
  "Using the schema defined above, validate the options being given us.
  source-str is a descriptive phrase which will be included in the errors
  (if any). Returns nil for success, a string with error(s) if not."
  ([options source-str]
   #_(println "validate-options:" options)
   (when options
     (empty-2-nil
       (apply str
         (interpose ", "
           (remove #(or (nil? %) (empty? %))
             (conj
               []
               (try (s/validate zprint-options-schema options)
                    nil
                    (catch Exception e
                      (if source-str
                        (str "In " source-str ", " (:cause (Throwable->map e)))
                        (:cause (Throwable->map e)))))
               (when (not (constants (get-in options [:map :key-order])))
                 (str "In " source-str
                      " :map :key-order were not all constants:"
                        (get-in options [:map :key-order])))
               (when (not (constants (keys (get-in options [:map :key-color]))))
                 (str "In " source-str
                      " :map :key-color were not all constants:"
                        (get-in options [:map :key-color])))
               (when (not (constants (get-in options
                                             [:reader-cond :key-order])))
                 (str " In " source-str
                      " :reader-cond :key-order were not all constants:"
                        (get-in options [:reader-cond :key-order]))))))))))
  ([options] (validate-basic options nil)))
