;!zprint {:style :require-justify}
(ns ^:no-doc zprint.zprint
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-s dbg-pr dbg-s-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer
               [dbg-pr dbg-s-pr dbg dbg-s dbg-form dbg-print zfuture]]])
    [clojure.string     :as s]
    [zprint.finish      :refer [newline-vec]]
    [zprint.zfns        :refer [zstring znumstr zbyte-array? zcomment? zsexpr
                                zseqnws zseqnws-w-nl zfocus-style zstart zfirst
                                zfirst-sexpr zsecond znthnext zcount zmap
                                zanonfn? zfn-obj? zfocus zfind-path zwhitespace?
                                zlist? zcount-zloc-seq-nc-nws zvector? zmap?
                                zset? zcoll? zuneval? zmeta? ztag zlast zarray?
                                zatom? zderef zrecord? zns? zobj-to-vec
                                zexpandarray znewline? zwhitespaceorcomment?
                                zmap-all zmap-all-nl-comment zpromise? zfuture?
                                zdelay? zkeyword? zconstant? zagent?
                                zreader-macro? zarray-to-shift-seq zdotdotdot
                                zsymbol? znil? zreader-cond-w-symbol?
                                zreader-cond-w-coll? zlift-ns zfind zmap-w-nl
                                zmap-w-nl-comma ztake-append znextnws-w-nl
                                znextnws znamespacedmap? zmap-w-bl zseqnws-w-bl
                                zsexpr? zmap-no-comment zfn-map]]
    [zprint.comment     :refer [blanks inlinecomment? length-before]]
    [zprint.ansi        :refer [color-str]]
    [zprint.config      :refer [validate-options merge-deep]]
    [zprint.zutil       :refer [add-spec-to-docstring]]
    [zprint.util        :refer [column-width-variance median mean percent-gt-n]]
    [zprint.optionfn    :refer [rodfn]]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip    :as    z
                        :refer [of-node* tag right* down*]]
    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))

#_(tufte/add-basic-println-handler! {})

;;
;; # Utility Functions
;;

(defn dots
  "Produce a dot string of desired size."
  [n]
  (apply str (repeat n ".")))

; This is about 10% faster than:
;
;(defn conj-it!-orig
;  "Make a version of conj! that take multiple arguments."
;  [& rest]
;  (loop [out (first rest)
;         more (next rest)]
;    (if more (recur (conj! out (first more)) (next more)) out)))

(defn conj-it!
  "Make a version of conj! that takes multiple arguments."
  [to & rest]
  (reduce conj! to rest))

(defn split-lf-2
  "Do split for newlines, instead of using regular expressions.
  Maximum split is 2."
  [s]
  (if-let [next-lf (clojure.string/index-of s "\n")]
    [(subs s 0 next-lf) (subs s (inc next-lf))]
    [s]))

(defn internal-config-and-validate
  "Possibly validate an options map and merge it correctly with the existing
  options map.  Validation only happens when the caller says to validate
  and the new-map doesn't have :no-validate? true (unless the existing
  options map has :force-validate? true).
  This is necessary instead of just doing a merge-deep, since
  that doesn't get styles and removal done correctly.  Returns
  [merged-options-map new-options] or throws an error."
  ([options new-map error-str validate?]
   (let [validate? (when validate?
                     (if (:no-validate? new-map)
                       (when (:force-validate? options) :validate)
                       :validate))
         [updated-map _ errors] (zprint.config/config-and-validate nil
                                                                   #_error-str
                                                                   nil
                                                                   options
                                                                   new-map
                                                                   validate?)
         errors (when errors
                  (str "Options resulting from " error-str
                       " had these errors: " errors))]
     (if (not (empty? errors))
       (throw (#?(:clj Exception.
                  :cljs js/Error.)
               errors))
       [updated-map new-map])))
  ([options new-map error-str]
   (internal-config-and-validate options new-map error-str :validate)))

(defn option-fn-name
  "Given an option-fn, call it with no arguments to see if it returns its
  name.  To be used only in exceptions or other times when performance is
  not important, because historically many option-fn's didn't know to do this."
  [option-fn]
  (try (let [option-fn-name (option-fn)]
         (when (string? option-fn-name) (str " named '" option-fn-name "'")))
       (catch #?(:clj Exception
                 :cljs :default)
         e
         nil)))


(defn zsexpr-token?
  "Call zsexpr?, but only if zloc is a :token"
  [zloc]
  (when (= (ztag zloc) :token) (zsexpr? zloc)))

(defn empty-coll
  "For the major collections, returns a empty one. Essentially an
  implementation of empty for zlocs."
  [zloc]
  (cond (= (ztag zloc) :list) '()
        (= (ztag zloc) :vector) []
        (= (ztag zloc) :set) #{}
        (= (ztag zloc) :map) {}))

(defn get-sexpr
  "Given a zloc, do the best we can to get an sexpr out of it."
  [options zloc]
  (try
    (zsexpr zloc)
    (catch #?(:clj Exception
              :cljs :default)
      e
      (let [#_(prn "tag:" (ztag zloc) "zloc:" zloc "zstring:" (zstring zloc))
            s (zstring zloc)
            new-s (reduce #(clojure.string/replace %1 %2 "")
                    s
                    (:ignore-if-parse-fails (:parse options)))
            #_(clojure.string/replace s "..." "")
            #_(prn "new-s:" new-s)
            ; So, let's try parsing it again and see if this works.  If
            ; the zsexpr failed, we know it is a string because the structure
            ; version of zsexpr is identity, which is unlikely to fail.
            sexpr
              (try
                (let [n (p/parse-string (clojure.string/trim new-s))
                      new-zloc (of-node* n)
                      sexpr (zsexpr new-zloc)]
                  sexpr)
                (catch #?(:clj Exception
                          :cljs :default)
                  e
                  (throw
                    (#?(:clj Exception.
                        :cljs js/Error.)
                     (str
                       "Unable to parse the string '" s
                       "' because of '" e
                       "'.  Consider adding any unallowed elements to"
                         " {:parse {:ignore-if-parse-fails #{ <string> }}}")))))
            #_(prn "sexpr:" sexpr)]
        sexpr))))

(defn get-sexpr-or-nil
  "Try to get an sexpr of something, and return nil if we can't."
  [options zloc]
  (try (get-sexpr options zloc)
       (catch #?(:clj Exception
                 :cljs :default)
         e
         nil)))

;!zprint {:format :next :vector {:wrap? false}}
(defn call-option-fn
  "Call an option-fn and return a validated map of the merged options. 
  Returns [merged-options-map new-options zloc l-str r-str changed?] where
  changed? refers only to changes in any of zloc, l-str, or r-str."
  [caller options option-fn zloc l-str r-str]
  #_(prn "call-option-fn caller:" caller)
  (let [sexpr-seq (get-sexpr options zloc)
        ; Add the current zloc and l-str, r-str to the options
        ; for the option-fn
        options (assoc options
                  :zloc zloc
                  :l-str l-str
                  :r-str r-str
                  :caller caller)
        _ (dbg-s options
                 #{:call-option-fn}
                 "call-option-fn: caller:" caller
                 "option-fn:" option-fn
                 "sexpr-seq:" sexpr-seq)
        [merged-options new-options]
          (internal-config-and-validate
            options
            (try (dbg-pr options "call-option-fn sexpr-seq:" sexpr-seq)
                 (let [result (option-fn options (count sexpr-seq) sexpr-seq)]
                   (dbg-s options
                          #{:call-option-fn}
                          "call-option-fn result:"
                          result)
                   result)
                 (catch #?(:clj Exception
                           :cljs :default)
                   e
                   (do (dbg-s options
                              :guide-exception
                              "Failure in option-fn:"
                              (throw e))
                       (throw (#?(:clj Exception.
                                  :cljs js/Error.)
                               (str " When " caller
                                    " called an option-fn" (option-fn-name
                                                             option-fn)
                                    " it failed because: " e))))))
            (str caller
                 " :option-fn" (option-fn-name option-fn)
                 " called with an sexpr of length " (count sexpr-seq))
            :validate)
        new-zloc (:new-zloc merged-options)
        new-l-str (:new-l-str merged-options)
        new-r-str (:new-r-str merged-options)]
    (dbg-pr options
            "call-option-fn: caller:"
            caller
            "
            option-fn '"
            (option-fn-name option-fn)
            "' returned new options:"
            new-options)
    [(dissoc merged-options
       :caller
       :zloc
       :new-zloc
       :l-str
       :new-l-str
       :r-str
       :new-r-str)
     #_new-options
     (or new-zloc zloc)
     (or new-l-str l-str)
     (or new-r-str r-str)
     ; Don't return more than we need to here, we might log it!
     (when (or new-zloc new-l-str new-r-str) true)]))

(defn call-option-fn-first
  "Call an option-fn-first with just the first thing in the zloc, and
  then return a validated map of just the new options.
  Returns [merge-options-map new-options]"
  [caller options option-fn-first zloc]
  (let [first-sexpr (get-sexpr options (zfirst-sexpr zloc))]
    (internal-config-and-validate
      options
      (try (option-fn-first options first-sexpr)
           (catch #?(:clj Exception
                     :cljs :default)
             e
             (throw (#?(:clj Exception.
                        :cljs js/Error.)
                     (str "When " caller
                          " called an option-fn-first" (option-fn-name
                                                         option-fn-first)
                          " with '" first-sexpr
                          "' failed because: " e)))))
      (str caller
           " :option-fn-first" (option-fn-name option-fn-first)
           " called with " first-sexpr)
      :validate)))

(defn guide-debug
  "Given the options map and a caller, look for :guide-debug in the options
  map.  It looks like [:caller :depth [:element ...]]  If the caller and 
  depth match, return the guide, else nil."
  [caller options]
  (let [debug-vector (:guide-debug options)]
    (when debug-vector
      (dbg-s options
             :guide
             "guide-debug: caller:" caller
             "depth:" (:depth options)
             "guide:" debug-vector)
      (if (and (= caller (first debug-vector))
               (= (:depth options) (second debug-vector)))
        (nth debug-vector 2)))))

(defn condense
  [depth [out accumulated-string current-depth] [s _ what :as element]]
  (let [new-depth (cond (= what :left) (inc current-depth)
                        (= what :right) (dec current-depth)
                        :else current-depth)
        accumulating? (> current-depth depth)
        start-accumulating? (> new-depth depth)
        new-accumulated-string (if (or accumulating? start-accumulating?)
                                 (str accumulated-string s)
                                 accumulated-string)
        next-accumulated-string
          (if start-accumulating? new-accumulated-string "")]
    [(cond (and accumulating? (not start-accumulating?))
             (conj out new-accumulated-string)
           (and accumulating? start-accumulating?) out
           (and (not accumulating?) (not start-accumulating?)) (conj out
                                                                     element)
           (and (not accumulating?) start-accumulating?) out
           :else (println "shouldn't be an else")) next-accumulated-string
     new-depth]))

(defn condense-depth
  "Take a style vec, and condense everything above the given depth."
  [depth coll]
  (first (reduce (partial condense depth) [[] "" 1] coll)))

;;
;; # Use pmap when we have it
;;

#?(:bb (defn zpmap
         ([options f coll] (map f coll))
         ([options f coll1 coll2] (map f coll1 coll2)))
   :clj (defn zpmap
          ([options f coll]
           (if (:parallel? options) (pmap f coll) (map f coll)))
          ([options f coll1 coll2]
           (if (:parallel? options) (pmap f coll1 coll2) (map f coll1 coll2))))
   :cljs (defn zpmap
           ([options f coll] (map f coll))
           ([options f coll1 coll2] (map f coll1 coll2))))

;;
;; # More parallelism issues -- zderef to go with zfuture macro
;;

(defn zat
  "Takes an option map and the return from zfuture.  If the
  options map has (:parallel? options) as true, then deref
  the value, otherwise just pass it through."
  [options value]
  #?(:bb value
     :clj (if (:parallel? options) (deref value) value)
     :cljs value))

;;
;; # Debugging Assistance
;;

(def fzprint-dbg (atom nil))

(defn log-lines
  "Accept a style-vec that we are about to hand to style-lines, and
  output it if called for, to aid in debugging."
  [{:keys [dbg-print? dbg-indent in-hang?], :as options} dbg-output ind
   style-vec]
  (when dbg-print?
    (if style-vec
      (do (println dbg-indent dbg-output "--------------" "in-hang?" in-hang?)
          (prn style-vec)
          #_(println (apply str (blanks ind) (map first style-vec))))
      (println dbg-indent dbg-output "--------------- no style-vec"))))

;;
;; # What is a function?
;;

(defn showfn?
  "Show this thing as a function?"
  [{:keys [fn-map color?], :as options} f]
  (when (and color? (not (string? f)))
    (let [f-str (str f)
          fn-map (:fn-map options)]
      (or (fn-map f-str)
          (re-find #"clojure" f-str)
          (if (symbol? f)
            ; This is necessary because f can be a symbol that
            ; resolve will have a problem with.  The obvious ones
            ; were (ns-name <some-namespace>), but there are almost
            ; certainly others.
            (try (or (re-find #"clojure"
                              (str (:ns (meta #?(:clj (resolve f)
                                                 :cljs f)))))
                     (fn-map (name f)))
                 (catch #?(:clj Exception
                           :cljs :default)
                   e
                   nil)))))))

(defn show-user-fn?
  "Show this thing as a user defined function?  Assumes that we
  have already handled any clojure defined functions!"
  [{:keys [user-fn-map color?], :as options} f]
  (when (and color? (not (string? f)))
    (let [f-str (str f)]
      (or (get user-fn-map f-str)
          (if (symbol? f)
            ; This is necessary because f can be a symbol that
            ; resolve will have a problem with.  The obvious ones
            ; were (ns-name <some-namespace>), but there are almost
            ; certainly others.
            (try (or (not (empty? (str (:ns (meta #?(:clj (resolve f)
                                                     :cljs f))))))
                     (get user-fn-map (name f)))
                 (catch #?(:clj Exception
                           :cljs :default)
                   e
                   nil)))))))

(def right-separator-map {")" 1, "]" 1, "}" 1})

;;
;; # Functions to compare alternative printing approaches
;;

(declare fix-rightcnt)
(declare contains-nil?)

(defn good-enough?
  "Given the fn-style, is the first output good enough to be worth
  doing. p is pretty, which is typically hanging, and b is basic, which
  is typically flow. p-count is the number of elements in the hang."
  [caller
   {:keys [width rightcnt dbg?],
    {:keys [general-hang-adjust]} :tuning,
    {:keys [hang-expand hang-diff hang-size hang-adjust],
     {:keys [hang-flow hang-type-flow hang-flow-limit hang-if-equal-flow?]}
       :tuning}
      caller,
    :as options} fn-style p-count indent-diff
   [p-lines p-maxwidth p-length-seq p-what] [b-lines b-maxwidth _ b-what]]
  (let [p-last-maxwidth (last p-length-seq)
        hang-diff (or hang-diff 0)
        hang-expand (or hang-expand 1000.)
        hang-adjust (or hang-adjust general-hang-adjust)
        ; Get solid versions of key local tuning parameters
        tuning (:tuning options)
        #_(when (= caller :pair)
            (println "good-enough:" (:tuning (caller options)))
            (println "good-enough caller hang-flow:" caller hang-flow))
        hang-flow (or hang-flow (:hang-flow tuning))
        hang-type-flow (or hang-type-flow (:hang-type-flow tuning))
        hang-flow-limit (or hang-flow-limit (:hang-flow-limit tuning))
        hang-if-equal-flow? (or hang-if-equal-flow?
                                (:hang-if-equal-flow? tuning))
        #_(options (if (and p-lines
                            p-count
                            (pos? p-count)
                            (not (<= indent-diff hang-diff))
                            (not (<= (/ (dec p-lines) p-count) hang-expand)))
                     (assoc options :dbg? true)
                     options))
        options (if (or p-what b-what) (assoc options :dbg? true) options)
        result (if (not b-lines)
                 true
                 (and p-lines
                      ; Does the last line fit, including the collection ending
                      ; stuff?
                      ; Do we really need this anymore?
                      (<= p-last-maxwidth (- width (fix-rightcnt rightcnt)))
                      ; Does it widest line fit?
                      ; Do we have a problem if the widest line has a rightcnt?
                      (<= p-maxwidth width)
                      ;      (<= p-maxwidth (- width (fix-rightcnt rightcnt)))
                      (or
                        (zero? p-lines)
                        (and ; do we have lines to operate on?
                          (> b-lines 0)
                          (> p-count 0)
                          ; if the hang and the flow are the same size, why
                          ; not hang?
                          (if (and (= p-lines b-lines) hang-if-equal-flow?)
                            true
                            ; is the difference between the indents so small
                            ; that we don't care?
                            (and (if (<= indent-diff hang-diff)
                                   true
                                   ; Do the number of lines in the hang exceed
                                   ; the number of elements in the hang?
                                   (<= (/ (dec p-lines) p-count) hang-expand))
                                 (if hang-size (< p-lines hang-size) true)
                                 (let [factor (if (= fn-style :hang)
                                                hang-type-flow
                                                hang-flow)]
                                   ; if we have more than n lines, take the
                                   ; shortest
                                   (if (> p-lines hang-flow-limit)
                                     (<= (dec p-lines) b-lines)
                                     ; if we have less then n lines, we don't
                                     ; necessarily take the shortest
                                     ; once we did (dec p-lines) here, fwiw
                                     ; then we tried it w/out the dec, now we
                                     ; let you set it in :tuning.
                                     ; The whole point of having a
                                     ; hang-adjust of -1 is to allow hangs
                                     ; when the number of lines in a hang
                                     ; is the same as the number of lines
                                     ; in a flow.
                                     ;(< (/ p-lines b-lines) factor)))))))]
                                     (< (/ (+ p-lines hang-adjust) b-lines)
                                        factor)))))))))]
    (dbg
      options
      (if result "++++++" "XXXXXX")
      "p-what" p-what
      "good-enough? caller:" caller
      "fn-style:" fn-style
      "width:" width
      "rightcnt:" rightcnt
      "hang-expand:" hang-expand
      "hang-flow-limit:" hang-flow-limit
      "hang-adjust:" hang-adjust
      "(/ (+ p-lines hang-adjust) b-lines)"
        (when (and p-lines b-lines hang-adjust)
          (/ (+ p-lines hang-adjust) b-lines))
      "factor:" (if (= fn-style :hang) hang-type-flow hang-flow)
      "p-count:" p-count
      "p-lines:" p-lines
      "p-maxwidth:" p-maxwidth
      "indent-diff:" indent-diff
      "hang-diff:" hang-diff
      "p-last-maxwidth:" p-last-maxwidth
      "b-lines:" b-lines
      "b-maxwidth:" b-maxwidth)
    result))

;;
;; # Utility Functions
;;

(defn in-hang
  "Add :in-hang? true to the options map."
  [options]
  (if (:in-hang? options)
    options
    (if (:do-in-hang? options)
      (assoc options :in-hang? (or (:depth options) true))
      options)))

(defn contains-nil?
  "Scan a collection, and return true if it contains any nils or empty
  collections."
  [coll]
  (some #(if (coll? %) (empty? %) (nil? %)) coll))

#_(defn concat-no-nil-alt
    "Concatentate multiple sequences, but if any of them are nil, return nil.
  This version is 15-20% slower than the version below. Keeping it around
  just for illustrative purposes."
    [& rest]
    (loop [coll rest
           out (transient [])]
      (let [c (first coll)]
        (if-not c
          (persistent! out)
          (when (or (and (coll? c) (not (empty? c))) (not (nil? c)))
            (recur (next coll) (conj! out c)))))))

(defn concat-no-nil-pre-noseq
  "Concatentate multiple sequences, but if any of them are nil or empty
  collections, return nil."
  [& rest]
  (let [result (reduce (fn [v o]
                         (if (coll? o)
                           (if (empty? o) (reduced nil) (reduce conj! v o))
                           (if (nil? o) (reduced nil) (conj! v o))))
                 (transient [])
                 rest)]
    (when result (persistent! result))))

(declare count-right-blanks)
(declare trimr-blanks)

(defn concat-no-nil
  "Concatentate multiple sequences, but if any of them are nil or empty
  collections, return nil. If any of them are :noseq, just skip them.
  When complete, check the last element-- if it is a :right, and if it
  the previous element is a :newline or :indent, then ensure that the
  number of spaces in that previous element matches the number to the
  right of the :right."
  [& rest]
  (let [result (reduce (fn [v o]
                         (if (coll? o)
                           (if (empty? o) (reduced nil) (reduce conj! v o))
                           (if (= :noseq o)
                             ; if the supposed sequence is :noseq, skip it
                             v
                             (if (nil? o) (reduced nil) (conj! v o)))))
                 (transient [])
                 rest)]
    (when result
      (let [result (persistent! result)]
        (if (< (count result) 2)
          result
          (let [[_ _ what right-ind :as last-element] (peek result)]
            (if (= what :right)
              ; we have a right paren, bracket, brace as the last thing
              (let [previous-index (- (count result) 2)
                    [s color previous-what] (nth result previous-index)]
                (if (or (= previous-what :newline) (= previous-what :indent))
                  ; we have a newline or equivalent before the last thing
                  (if (= (count-right-blanks s) right-ind)
                    ; we already have the right number of blanks!
                    result
                    (let [new-previous [(str (trimr-blanks s)
                                             (blanks right-ind)) color
                                        previous-what]]
                      (assoc result previous-index new-previous)))
                  result))
              result)))))))


(defn concat-no-nil-pre-right
  "Concatentate multiple sequences, but if any of them are nil or empty
  collections, return nil. If any of them are :noseq, just skip them."
  [& rest]
  (let [result (reduce (fn [v o]
                         (if (coll? o)
                           (if (empty? o) (reduced nil) (reduce conj! v o))
                           (if (= :noseq o)
                             ; if the supposed sequence is :noseq, skip it
                             v
                             (if (nil? o) (reduced nil) (conj! v o)))))
                 (transient [])
                 rest)]
    (when result (persistent! result))))

(defn remove-one
  "Remove a single thing from a sequence."
  [s index]
  (concat (take index s) (drop (inc index) s)))

(defn force-vector
  "Ensure that whatever we have is a vector."
  [coll]
  (if (vector? coll) coll (into [] coll)))

(defn keyword-fn?
  "Takes a string, and returns the fn-style if it is a keyword and
  without the : it can be found in the fn-map."
  [options s]
  (let [[left right] (clojure.string/split s #"^:")]
    (when right ((:fn-map options) right))))

(defn get-max-length
  "Given the options map, return the max length.  This might be
  a constant number, but it might be based on the depth as well.
  Returns nil of there is no max-length set."
  [{:as options, :keys [max-length depth]}]
  (when max-length
    (if (vector? max-length)
      (nth max-length (min (dec depth) (dec (count max-length))))
      max-length)))

(defn no-max-length
  "Given an options map, return another options map with no
  :max-length key.  This is to that you can call a routine that
  normally deals with :max-length and get it to do the normal
  thing."
  [options]
  (assoc options :max-length 10000))

;;
;; # Work with style-vecs and analyze results
;;

;; Transients don't help here, though they don't hurt much either.

(defn accumulate-ll
  "Take the vector carrying the intermediate results, and
  do the right thing with a new string. Vector is
  [ 0 out - vector accumulating line lengths 
    1 cur-len - length of current line
    just-eol? - did we just do an eol?
    ]
  s - string to add to current line
  tag - element type of string (comment's don't count in length)
  eol? - should we terminate line after adding count of s"
  [count-comment? [out cur-len just-eol? just-comment? :as in] s tag eol?]
  (let [comment? (or (= tag :comment) (= tag :comment-inline))
        count-s (if (and comment? (not count-comment?)) 0 (count s))]
    (cond
      ; if we are told to terminate the line or it
      ; is a comment, we terminate the line with the
      ; size of the string added to it
      (or (and eol? (not (and just-eol? (zero? count-s)))) comment?)
        [(conj out (+ cur-len count-s)) 0 true comment?]
      ;(assoc in 0 (conj out (+ cur-len count-s)) 1 0 2 true 3 comment?)
      ; no reason to terminate the line, just accumulate
      ; the size in cur-len
      :else [out (+ cur-len count-s) nil comment?])))

(defn generate-ll
  [count-comment? [out cur-len just-eol? just-comment? :as in]
   [s _ tag :as element]]
  (let [[l r] (if (or (= tag :whitespace) (= tag :indent) (= tag :newline))
                (split-lf-2 s)
                (list s))
        ; if tag = :comment, shouldn't have \n and
        ; therefore shouldn't have r
        ; if r is non-nil, then we had a newline, so we want to
        ; terminate the current line
        ; if we are already in a comment and we have something
        ; that is not whitespace, then we want to terminate the
        ; current line
        in (accumulate-ll count-comment? in l tag (not (nil? r)))
        in (if (empty? r) in (accumulate-ll count-comment? in r tag nil))]
    in))


(defn line-lengths-iter
  "Take a style-vec, and output a sequence of numbers, one for each
  line, which contains the actual length. Must take the current
  indent to have a prayer of getting this right, but it is used
  only for the first line.  The ind can be an integer or a seq of
  integers, in which case only the first integer is used. Newlines
  can come anywhere in an element in a style-vec, it will account
  for both sides.  Will break lines on comments even if no newlines
  in them.  This doesn't count the length of comment lines unless
  [:comment :count?] is true, so that we don't format based on
  comment size -- that is handled with the wrap-comments elsewhere.
  Note that only vectors with :whitespace, :indent, or :newline are scanned
  for newlines, and if consecutive newlines appear, only the first
  is counted as a newline -- the second is counted as a regular 
  character. A single comment is counted as two lines. Lots of edge
  conditions that are really quite important."
  [options ind style-vec]
  (let [count-comment? (:count? (:comment options))
        ind (if (coll? ind) (first ind) ind)]
    (dbg-pr options "line-lengths-iter: style-vec:" style-vec)
    (loop [next-vec style-vec
           current-string nil
           line-length ind
           previous-comment? nil
           out []]
      (if (or (and (empty? next-vec) (empty? current-string)))
        ; A trailing newline isn't counted.
        (cond (and (zero? line-length) (not previous-comment?)) out
              previous-comment? (conj out line-length 0)
              :else (conj out line-length))
        (let [advance? (empty? current-string)
              [next-string _ tag] (when advance? (first next-vec))
              comment? (or (= tag :comment) (= tag :comment-inline))
              s (if advance? next-string current-string)
              [l r] (when s
                      ; if we have a current-string, then we are looking for
                      ; newlines
                      (cond (and comment? (not count-comment?)) [""]
                            (or (and advance?
                                     (or (= tag :whitespace)
                                         (= tag :newline)
                                         (= tag :indent)))
                                current-string)
                              (split-lf-2 s)
                            :else [s]))
              ; If r non-nil, we had a newline at end of l.
              ; If we had a previous-comment, then we want to
              ; imply a newline unless we have a newline at the
              ; start of s.
              ; If r is non-nil, and l is empty, then the newline
              ; was at the front of r, in which case we don't need to
              ; do an implied newline for the comment (if any).
              ; Choices:
              ;  leave l and r alone
              ;  l becomes nil and r is (str l r)
              force-newline? (and previous-comment? (not (empty? l)))
              #_(prn "l:" l
                     "r:" r
                     "force-newline?" force-newline?
                     "comment?" comment?)
              r (if force-newline? (str l r) r)
              l (if force-newline? nil l)
              new-line-length (+ line-length (count l))]
          #_(prn "current-string:" current-string
                 "line-length:" line-length
                 "advance?" advance?
                 "s:" s
                 "l:" l
                 "r:" r
                 "new-line-length:" new-line-length)
          (recur (if advance? (next next-vec) next-vec)
                 r
                 (if r 0 new-line-length)
                 comment?
                 (if r (conj out new-line-length) out)))))))

(defn line-lengths
  "Take a style-vec, and output a sequence of numbers, one for each
  line, which contains the actual length. Must take the current
  indent to have a prayer of getting this right, but it is used
  only for the first line.  The ind can be an integer or a seq of
  integers, in which case only the first integer is used. Newlines
  can come anywhere in an element in a style-vec, it will account
  for both sides.  Will break lines on comments even if no newlines
  in them.  This doesn't count the length of comment lines unless
  [:comment :count?] is true, so that we don't format based on
  comment size -- that is handled with the wrap-comments at the
  end. Note that only vectors with :whitespace or :indent are scanned
  for newlines, and if consecutive newlines appear, only the first
  is counted as a newline -- the second is counted as a regular 
  character."
  [options ind style-vec]
  (let [length-vec (first ; this final accumulate-ll is to terminate the last
                          ; line, the one in progress
                     (let [count-comment? (:count? (:comment options))
                           [_ _ just-eol? just-comment? :as result]
                             (reduce (partial generate-ll count-comment?)
                               [[] (if (coll? ind) (first ind) ind) nil nil]
                               style-vec)]
                       (if (and just-eol? (not just-comment?))
                         result
                         (accumulate-ll count-comment?
                                        (assoc result 2 nil)
                                        ""
                                        nil
                                        true))))]
    (dbg-pr options
            "line-lengths: style-vec:" style-vec
            "ind:" ind
            "length-vec:" length-vec)
    length-vec))

(defn single-line?
  "This looks at a style vec and doesn't do all that style-lines does.
  It just looks for a new-line in the strings, and returns true if it
  doesn't find one."
  [style-vec]
  #_(prn "style-vec:" style-vec)
  (not (reduce #(or %1 %2)
         false
         (map #(clojure.string/includes? (first %) "\n") style-vec))))

(defn find-what
  "Given a style-vec, come up with a string that gives some hint of 
  where this style-vec came from."
  [style-vec]
  (loop [s-vec style-vec]
    (when s-vec
      (let [[what _ this] (first s-vec)]
        (if (= this :element) what (recur (next s-vec)))))))

(defn first-nl?
  "Look at a style vec ready to be given to concat-no-nil, and see if
  the first thing in there is a newline of some sort."
  [style-vec]
  (let [[s color what] (first style-vec)]
    (or (= what :newline) (= what :indent))))

(defn prepend-nl
  "Given an indent ind and a style-vec coll, place a newline (actually an
  indent) at the front of coll.  If the first thing in coll is a newline,
  then don't add any spaces after the newline that we prepend."
  [options ind coll]
  (concat-no-nil [[(str "\n" (blanks (if (first-nl? coll) 0 ind))) :none :indent
                   1]]
                 coll))

; Debugging help to find differences between line-lengths and
; line-lengths-iter.  Surprisingly helpful!
#_(defonce lldiff (atom []))

(defn style-lines
  "Take a style output, and tell us how many lines it takes to print it
  and the maximum width that it reaches. Returns 
  [<line-count> <max-width> [line-lengths]].
  Doesn't require any max-width inside the style-vec. Also returns the
  line lengths in case that is helpful (since we have them anyway).
  If (:dbg-ge options) has value, then uses find-what to see if what it
  finds matches the value, and if it does, place the value in the
  resulting vector."
  [options ind style-vec]
  (when (and style-vec (not (empty? style-vec)) (not (contains-nil? style-vec)))
    (let [;lengths (line-lengths options ind style-vec)
          lengths (line-lengths-iter options ind style-vec)
          count-lengths (count lengths)
          result [count-lengths (if (zero? count-lengths) 0 (apply max lengths))
                  lengths]
          dbg-ge (:dbg-ge options)
          what (when (and dbg-ge (= (find-what style-vec) dbg-ge)) dbg-ge)]
      #_(when (not= lengths lengths-iter) (swap! lldiff conj style-vec))
      (if what (conj result what) result))))

(defn style-lines-hangflow
  "Does style-lines on something formatted like [[:hang ...] [:flow ...]],
  which is the output from fzprint-map-two-up. Assumes each :hang or :flow
  thing is on a separate line, which may not be true when the 
  hangflow-style-vec is ultimately given to interpose-...."
  [options ind hangflow-style-vec]
  (when (and hangflow-style-vec
             (not (empty? hangflow-style-vec))
             (not (contains-nil? hangflow-style-vec)))
    (let [style-vecs (mapv second hangflow-style-vec)
          style-lines-map (mapv (partial style-lines options ind) style-vecs)
          lengths (into [] (flatten (mapv #(nth % 2) style-lines-map)))
          max-length (apply max lengths)]
      [(count lengths) max-length lengths])))

(defn fzfit
  "Given output from style-lines and options, see if it fits the width.  
  Return the number of lines it takes if it fits, nil otherwise."
  [{:keys [width rightcnt dbg?], :as options}
   [line-count max-width :as style-lines-return]]
  (dbg options
       "fzfit: fixed-rightcnt:" (fix-rightcnt rightcnt)
       "line-count:" line-count
       "max-width:" max-width
       "width:" width)
  (when style-lines-return
    (if (<= max-width (- width (fix-rightcnt rightcnt))) line-count nil)))

(defn fzfit-one-line
  "Given the return from style-lines  and options, 
  return true if it fits on a single line."
  [options style-lines-return]
  (let [lines (fzfit options style-lines-return)]
    (and (number? lines) (= lines 1))))

;;
;; # Handle Rightmost Size
;;

(defn rightmost
  "Increase the rightmost count, if any, and return one if not."
  [options]
  (assoc options :rightcnt (inc (:rightcnt options 0))))

(defn not-rightmost
  "Remove the rightmost count."
  [options]
  (dissoc options :rightcnt))

(defn c-r-pair
  "Handle the complexity of commas and rightmost-pair with options.
  If it isn't a rightmost, it loses rightmost status.
  If it is a rightmost, and in the rightmost pair, it gain one rightmost
  since it has the right end thing (and we don't care about the comma).
  If it is the rightmost of the non-rightmost-pair, then the comma
  matters, and we handle that appropriately.  Whew!"
  [commas? rightmost-pair? rightmost? options]
  (if-not rightmost?
    (not-rightmost options)
    (if rightmost-pair?
      options
      (if commas?
        (rightmost (not-rightmost options))
        (not-rightmost options)))))

(defn fix-rightcnt
  "Handle issue with rightcnt."
  [rightcnt]
  (if (number? rightcnt) rightcnt 0))

;;
;; # First pass at color -- turn string or type into keyword color
;;

;;
;; ## Translate from a string to a keyword as needed.
;;

(def str->key
  {"(" :paren,
   ")" :paren,
   "[" :bracket,
   "]" :bracket,
   "{" :brace,
   "}" :brace,
   "#{" :hash-brace,
   "#(" :hash-paren,
   "#_" :uneval,
   "'" :quote,
   "`" :syntax-quote,
   "~" :unquote,
   "~@" :unquote-splicing,
   "@" :deref})


(defn zcolor-map
  "Look up the thing in the zprint-color-map.  Accepts keywords or
  strings."
  [{:keys [color-map color?], :as options} key-or-str]
  ; If we aren't doing color, don't even bother to do the lookup
  (if color?
    (color-map (if (keyword? key-or-str) key-or-str (str->key key-or-str)))
    :none))


;;
;; ## Pretty Printer Code
;;

(declare fzprint*)
(declare fzprint-flow-seq)

(defn hangflow
  "Take a style-vec, and if hangflow? is true, return a
  vector [hang-or-flow style-vec], else return style-vec.
  But a nil style-vec returns nil."
  [hangflow? hang-or-flow style-vec]
  (when style-vec (if hangflow? [hang-or-flow style-vec] style-vec)))

(defn fzprint-hang-unless-fail
  "Try to hang something and if it doesn't hang at all, then flow it,
  but strongly prefer hang.  Has hang and flow indents, and fzfn is the
  fzprint-? function to use with zloc.  Callers need to know whether this
  was hang or flow, so it returns [{:hang | :flow} style-vec] all the time."
  [options hindent findent fzfn zloc]
  (dbg options
       "fzprint-hang-unless-fail: hindent:" hindent
       "findent:" findent
       "zloc:" (zstring (zfirst zloc)))
  ; If the hindent is different than the findent, we'll try hang, otherwise
  ; we will just do the flow
  (let [hanging (when (not= hindent findent)
                  (fzfn (in-hang options) hindent zloc))]
    (dbg-form
      options
      "fzprint-hang-unless-fail: exit:"
      (if (and hanging (fzfit options (style-lines options hindent hanging)))
        [:hang hanging]
        ; hang didn't work, do flow
        (do (dbg options "fzprint-hang-unless-fail: hang failed, doing flow")
            [:flow
             (prepend-nl options findent (fzfn options findent zloc))])))))

(defn replace-color
  "Given a style-vec with exactly one thing in it, replace the color
  with whatever local color we have determined is correct."
  [local-color style-vec]
  (if (= (count style-vec) 1)
    (let [[[string color element]] style-vec] [[string local-color element]])
    style-vec))

(declare fzprint-binding-vec)
(declare middle-element?)

(defn use-hang?
  "This routine tries to figure out if existing hang should be used without
  even bothering to do a flow and compare them with good-enough?."
  [caller
   {:keys [depth width],
    {:keys [hang-accept ha-depth-factor ha-width-factor]} caller,
    :as options} ind hang-count hanging-line-count]
  (when (and hanging-line-count hang-accept (pos? hang-count))
    #_(prn "use-hang? caller:" caller "(/ ind width):" (double (/ ind width)))
    (let [hang-accept (+ hang-accept
                         (* depth ha-depth-factor)
                         (* (/ ind width) ha-width-factor))
          result (<= (/ (dec hanging-line-count) hang-count) hang-accept)]
      #_(prn "use-hang? result:" result)
    #_(prn "use-hang? caller:" caller "(/ ind width):" (double (/ ind width))
          "hang-count:" hang-count "hanging-line-count:" hanging-line-count
	  "depth:" depth "hang-accept:" hang-accept "result:" result)
      result)))

;;
;; Performance Debugging
;;

#_(def pass-count (atom 0))
#_(defn reset-pass-count! [] (reset! pass-count 0))
#_(defn inc-pass-count [] (swap! pass-count inc))
#_(defn print-pass-count [] (println "pass-count:" @pass-count))

(defn fzprint-two-up
  "Print a single pair of things (though it might not be exactly a
  pair, given comments and :extend and the like), like bindings in
  a let, clauses in a cond, keys and values in a map.  Controlled
  by various maps, the key of which is caller.  Returns 
  [:hang <style-vec>] or [:flow <style-vec>] so that the upstream folks
  know whether this was a hang or flow and can do the right thing
  based on that. If narrow-width is non-nil, that is the width to
  harrow to, else don't narrow. Note that justify-width tells us we
  are justifying."
  [caller
   {:keys [one-line? dbg? dbg-indent in-hang? do-in-hang? map-depth],
    {:keys [hang? dbg-local? dbg-cnt? indent indent-arg flow? key-color
            key-depth-color key-value-color key-value-options justify
            multi-lhs-hang?]}
      caller,
    :as options} ind commas? justify-width justify-options narrow-width
   rightmost-pair? force-flow? [lloc rloc xloc :as pair]]
  (if dbg-cnt? (println "two-up: caller:" caller "hang?" hang? "dbg?" dbg?))
  (dbg-s options
         #{:narrow :justify :justify-result}
         "fzprint-two-up:" (pr-str (zstring lloc))
         "justify-width:" justify-width
         "width:" (:width options)
         "justify-options-width:" (:width justify-options)
         "(count pair):" (count pair))
  (if (or dbg? dbg-local?)
    (println (or dbg-indent "")
             "=========================="
             (str "\n" (or dbg-indent ""))
             (pr-str
               "fzprint-two-up:" (zstring lloc)
               "tag:" (ztag lloc)
               "caller:" caller
               "count:" (count pair)
               "ind:" ind
               "indent:" indent
               "indent-arg:" indent-arg
               "justify-width:" justify-width
               "one-line?:" one-line?
               "hang?:" hang?
               "in-hang?" in-hang?
               "do-in-hang?" do-in-hang?
               "flow?" flow?
               "force-flow?" force-flow?
               "commas?" commas?
               "rightmost-pair?" rightmost-pair?)))
  (let [flow? (or flow? force-flow?)
        local-hang? (or one-line? hang?)
        indent (or indent indent-arg)
        ; We get options and justify-options.  Generally we want to use
        ; the justify-options, unless we are not justifying, in which
        ; case we want to use the regular options.  We can't tell if
        ; we are justifying until after we figure the arg-1-width,
        ; so we will use the justify-options for everything and
        ; calculate one set of r-non-justify-options for use with
        ; good-enough if we didn't justify.  This allows us to put
        ; weird stuff in :justify-hang to change things other then
        ; :hang-expand when we are justifying.
        non-justify-options options
        options justify-options
        local-options
          (if (not local-hang?) (assoc options :one-line? true) options)
        _ (dbg-s options
                 :rightmost-pair
                 "fzprint-two-up rightmost-pair:" rightmost-pair?
                 "lloc:" (pr-str (zstring lloc))
                 "width:" (:width options)
                 "original-width:" (:original-width options))
        loptions (c-r-pair commas?
                           rightmost-pair?
                           nil
                           (if narrow-width
                             (assoc options :width narrow-width)
                             options))
        loptions-non-narrow (c-r-pair commas? rightmost-pair? nil options)
        width (:width options)
        roptions (c-r-pair commas? rightmost-pair? :rightmost options)
        ; These are only really important for good-enough
        non-justify-roptions
          (c-r-pair commas? rightmost-pair? :rightmost non-justify-options)
        local-roptions
          (c-r-pair commas? rightmost-pair? :rightmost local-options)
        ; If we have a key-value-color map, and the key we have matches any
        ; of the keys in the map, then merge the resulting color-map elements
        ; into the current color-map.  Could be problematic if lloc is a
        ; modifier, but at present modifiers are only for extend and
        ; key-value-color is only for maps, so they can't both show up
        ; at once.
        value-color-map (and key-value-color
                             (> (count pair) 1)
                             (zsexpr? lloc)
                             (key-value-color (get-sexpr options lloc)))
        local-roptions (if value-color-map
                         (merge-deep local-roptions
                                     {:color-map value-color-map})
                         local-roptions)
        roptions (if value-color-map
                   (merge-deep roptions {:color-map value-color-map})
                   roptions)
        ; If we have a key-value-options map, and the key we have matches
        ; any of the keys in the map, then merge the resulting options map
        ; into the current options for the value.
        value-options-map (and key-value-options
                               (> (count pair) 1)
                               (zsexpr? lloc)
                               (key-value-options (get-sexpr options lloc)))
        local-roptions (if value-options-map
                         (merge-deep local-roptions value-options-map)
                         local-roptions)
        roptions (if value-options-map
                   (merge-deep roptions value-options-map)
                   roptions)
        ; It is possible that lloc is a modifier, and if we have exactly
        ; three things, we will pull rloc in with it, and move xloc to rloc.
        ; If it is just two, we'll leave it to be handled normally.
        ; Which might need to be re-thought due to justification, but since
        ; we are really only talking :extend here, maybe not.
        modifier-set (:modifiers (options caller))
        modifier? (or (and modifier-set
                           (modifier-set (zstring lloc))
                           (> (count pair) 2))
                      (middle-element? options rloc))
        ; Figure out if we want to color keys based on their depth, and if so,
        ; figure out the color for this one.
        local-color (get key-depth-color (dec map-depth))
        ; Doesn't work if we have a modifier, but at this point, key-color
        ; is only for maps and modifiers are only for extend.
        local-color (if (and key-color (> (count pair) 1) (zsexpr? lloc))
                      (key-color (get-sexpr options lloc))
                      local-color)
        #_local-color
        #_(cond (and map-depth (= caller :map) (= map-depth 2)) :green
                (and map-depth (= caller :map) (= map-depth 1)) :blue
                (and map-depth (= caller :map) (= map-depth 3)) :yellow
                (and map-depth (= caller :map) (= map-depth 4)) :red
                :else nil)
        #_(println "fzprint-two-up: :fn" (:fn-type-map options))
        #_(println "fzprint-two-up: :next-inner" (:next-inner options))
        _ (dbg-s options
                 :justify-result-deep
                 "fzprint-two-up:" (pr-str (zstring lloc))
                 "one-line?" one-line?
                 "loptions width:" (:width loptions))
        arg-1 (fzprint* loptions ind lloc)
        ; If we were narrowing and justifying, and if this doesn't fit
        ; the narrowing at all (i.e., returns nil), let's re-do it with
        ; the regular width since this must be one that shouln't be
        ; justified.
        _ (dbg-s options
                 #{:justify-width}
                 "fzprint-two-up: pre-a:" (pr-str (zstring lloc))
                 "one-line?" one-line?
                 "ind:" ind
                 "loptions width:" (:width loptions)
                 "lines:" (style-lines options ind arg-1))
        arg-1 (if (and (nil? arg-1) justify-width narrow-width)
                (do (dbg-s options
                           #{:justify-width}
                           "fzprint-two-up: DID NOT FIT:" (pr-str (zstring
                                                                    lloc))
                           "one-line?" one-line?
                           "loptions width:" (:width loptions))
                    ; These need to be the regular loptions (which include
                    ; justify-options) but just not be narrow, to match
                    ; what fzprint-justify-width does when it redoes the
                    ; fzprint* when it fails for narrowing.
                    (fzprint* loptions-non-narrow ind lloc))
                arg-1)
        no-justify (:no-justify justify)
        ; If we have a newline, make it one shorter since we did a newline
        ; after the previous pair.  Unless this is the first pair, but we
        ; should have done one before that pair too, maybe?
        arg-1-newline? (and (= (count pair) 1) (znewline? lloc))
        arg-1 (if local-color (replace-color local-color arg-1) arg-1)
        ; If we are going to print the second thing on the line, we need
        ; to know how big the first thing is, so we can see if the second
        ; thing fits on the line.
        [arg-1-line-count arg-1-max-width :as arg-1-lines]
          (style-lines options ind arg-1)
        ; Get the correct arg-1-max-width in the multi-line case.
        ; Note that this get the width of the last line of a multi-line
        ; arg-1!
        arg-1-max-width (peek (nth arg-1-lines 2))
        ; If arg-1 already takes multiple lines, we aren't going to do
        ; anything interesting with a modifier.
        _ (dbg options
               "fzprint-two-up before modifier: arg-1-line-count:"
                 arg-1-line-count
               "arg-1-max-width:" arg-1-max-width)
        modifier? (if (or (and arg-1-line-count (> arg-1-line-count 1))
                          arg-1-newline?)
                    nil
                    modifier?)
        ; See if we can merge the first and second things and have them
        ; stay on the same line?
        combined-arg-1 (if modifier?
                         (concat-no-nil arg-1
                                        [[(str " ") :none :whitespace 1]]
                                        (fzprint* (in-hang loptions)
                                                  (+ ind arg-1-max-width)
                                                  rloc))
                         arg-1)
        ; If they fit, then they are the new arg-1
        arg-1 (if combined-arg-1 combined-arg-1 arg-1)
        ; If they fit, then we are still doing modifier if we are already
        modifier? (if combined-arg-1 modifier? nil)
        ; If they fit, we need to recalculate the size of arg-1
        [arg-1-line-count arg-1-max-width :as arg-1-lines]
          (if combined-arg-1 (style-lines options ind arg-1) arg-1-lines)
        ; Use the last line, not the widest
        arg-1-max-width (peek (nth arg-1-lines 2))
        _ (dbg options
               "fzprint-two-up after modifier: arg-1-line-count:"
                 arg-1-line-count
               "arg-1-max-width:" arg-1-max-width)
        lloc (if modifier? rloc lloc)
        rloc (if modifier? xloc rloc)
        ;     arg-1-fit-oneline? (and (not force-nl?)
        ;                             (fzfit-one-line loptions arg-1-lines))
        arg-1-fit-oneline? (and (not flow?)
                                (if multi-lhs-hang?
                                  ; We don't care if it fits in a
                                  ; single line so we fake it here.
                                  true
                                  (fzfit-one-line loptions arg-1-lines)))
        arg-1-fit? (fzfit loptions arg-1-lines)
        ; sometimes arg-1-max-width is nil because fzprint* returned nil,
        ; but we need to have something for later code to use as a number
        arg-1-width (- (or arg-1-max-width 0) ind)
        ; Remember if we were supposed to be justifying.
        justifying? justify-width
        ; Should we justify the lhs of this particular pair?
        ; We use justify-width as a signal of whether or not to justify
        ; later in this routine.
        justify-width (when justify-width
                        (if (or (> arg-1-width justify-width)
                                (when no-justify (no-justify (ffirst arg-1))))
                          nil
                          justify-width))
        ; If we were doing narrow, and we were trying to justify, but this
        ; lhs didn't meet the qualifcations for justifying, then let's not
        ; narrow this lhs if it is a collection over one line
        [narrow-width arg-1 arg-1-lines arg-1-width]
          (if (and justifying?
                   (not justify-width)
                   narrow-width
                   (not (fzfit-one-line loptions arg-1-lines))
                   (= (nth (first arg-1) 2) :left))
            ; Note that we need to use the loptions as they also have
            ; the justify-options in them -- which needs to match what
            ; we did in fzprint-justify-width.
            (let [arg-1 (fzprint* (assoc loptions :width width) ind lloc)
                  arg-1-lines (style-lines options ind arg-1)
                  arg-1-max-width (peek (nth arg-1-lines 2))
                  arg-1-width (- (or arg-1-max-width 0) ind)]
              (dbg-s options
                     #{:justify-opt :justify-width}
                     "fzprint-two-up: redoing lhs arg-1" (pr-str (zstring lloc))
                     "arg-1-lines:" arg-1-lines)
              [nil arg-1 arg-1-lines arg-1-width])
            [narrow-width arg-1 arg-1-lines arg-1-width])]
    (dbg-s options
           #{:justify-result :justify-width}
           "fzprint-two-up a:" (zstring lloc)
           "justify-width:" justify-width
           "narrow-width:" narrow-width
           "arg-1-width" arg-1-width
           "arg-1-max-width" arg-1-max-width
           "ind" ind
           "arg-1-lines" arg-1-lines
           "arg-1-fit?" arg-1-fit?
           "(not in-hang?)" (not in-hang?)
           "(:width options)" (:width options))
    ; If we don't *have* an arg-1, no point in continuing...
    ;  If arg-1 doesn't fit, maybe that's just how it is!
    ;  If we are in-hang, then we can bail, but otherwise, not.
    (dbg-pr options "fzprint-two-up: arg-1:" arg-1)
    (when arg-1
      #_(and arg-1 (or arg-1-fit? (not in-hang?)))
      (cond
        ; This used to always :flow
        arg-1-newline? [(if force-flow? :flow :hang) arg-1]
        ; This is what does comments, and will cause an infinite loop
        ; in fzprint-map-two-up with flow-all-if-any? true,
        ; since it used to always hang even if force-flow? was true.
        (= (count pair) 1) [(if force-flow? :flow :hang)
                            (fzprint* roptions ind lloc)]
        (or (= (count pair) 2) (and modifier? (= (count pair) 3)))
          ;concat-no-nil
          ;  arg-1
          ; We used to think:
          ; We will always do hanging, either fully or with one-line? true,
          ; we will then do flow if hanging didn't do anything or if it did,
          ; we will try to see if flow is better.
          ;
          ; But now, we don't do hang if arg-1-fit-oneline? is false, since
          ; we won't use it.
          (let [hanging-width (if justify-width justify-width arg-1-width)
                ; These next two -n things are to keep the cljs compiler
                ; happy, since it complains that - need numbers and these
                ; might not be numbers if they are used below, despite the
                ; if justify-width.  Sigh.
                justify-width-n (or justify-width 0)
                arg-1-width-n (or arg-1-width 0)
                hanging-spaces
                  (if justify-width (inc (- justify-width-n arg-1-width-n)) 1)
                hanging-indent (+ 1 hanging-width ind)
                flow-indent (+ indent ind)]
            (if (and (zstring lloc)
                     (keyword-fn? options (zstring lloc))
                     (not (= caller :map)))
              ; We could also check for (= caller :pair-fn) here,
              ; or at least check to see that it isn't a map.
              (if (zvector? rloc)
                ; This is an embedded :let or :when-let or something.
                ; We check to see if a keyword is found in the :fn-map
                ; (without the :, of course) and if it is and there
                ; is a vector after it, we assume that it must be one of these.
                (let [[hang-or-flow style-vec] (fzprint-hang-unless-fail
                                                 loptions
                                                 hanging-indent
                                                 flow-indent
                                                 fzprint-binding-vec
                                                 rloc)
                      arg-1 (if (= hang-or-flow :hang)
                              (concat-no-nil arg-1
                                             [[(blanks hanging-spaces) :none
                                               :whitespace 2]])
                              arg-1)]
                  [hang-or-flow (concat-no-nil arg-1 style-vec)])
                (let [[hang-or-flow style-vec] (fzprint-hang-unless-fail
                                                 loptions
                                                 hanging-indent
                                                 flow-indent
                                                 fzprint*
                                                 rloc)
                      arg-1 (if (= hang-or-flow :hang)
                              (concat-no-nil arg-1
                                             [[(blanks hanging-spaces) :none
                                               :whitespace 2]])
                              arg-1)]
                  [hang-or-flow (concat-no-nil arg-1 style-vec)]))
              ; Make the above if a cond, and call fzprint-hang-one?  Or
              ; maybe fzprint* if we are calling fzprint-hang-unless-fail,
              ; which I think we are.
              ;
              ; This is a normal two element pair thing
              (let [; Perhaps someday we could figure out if we are already
                    ; completely in flow to this point, and be smarter about
                    ; possibly dealing with the hang or flow now.  But for
                    ; now, we will simply do hang even if arg-1 didn't fit
                    ; on one line if the flow indent isn't better than the
                    ; hang indent.
                    _ (dbg options
                           "fzprint-two-up: before hang.  hanging tried?"
                           (or arg-1-fit-oneline?
                               (and (not flow?)
                                    (>= flow-indent hanging-indent))))
                    ; We used to adjust the hang-flow in-line here to
                    ; make things come out better.  Now we have instead
                    ; made all fo the :justify-tuning to be relative to
                    ; just the justification data or code type, and the
                    ; hang-flow for the data in the justified rhs is
                    ; independent of the hang-flow for the justification
                    ; itself.
                    ; local-roptions
                    ;    (assoc-in local-roptions [:tuning :hang-flow] 1.1)
                    hanging (when (or arg-1-fit-oneline?
                                      (and (not flow?)
                                           (>= flow-indent hanging-indent)))
                              (fzprint* (if (< flow-indent hanging-indent)
                                          (in-hang local-roptions)
                                          local-roptions)
                                        hanging-indent
                                        rloc))
                    hang-count (zcount rloc)
                    _ (log-lines options
                                 "fzprint-two-up: hanging:"
                                 hanging-indent
                                 hanging)
                    hanging-lines (style-lines options hanging-indent hanging)
                    _ (dbg-s options
                             #{:justify :justify-result}
                             "fzprint-two-up:" (zstring lloc)
                             "justify-width:" justify-width
                             "justifying?" justifying?
                             "arg-1-fit-oneline?" arg-1-fit-oneline?
                             "one-line?" one-line?
                             "hang?" hang?
                             "hanging-indent:" hanging-indent
                             "(:width local-roptions)" (:width local-roptions)
                             "hanging" (pr-str hanging))
                    fit? (fzfit-one-line local-roptions hanging-lines)
                    hanging-lines (if fit?
                                    hanging-lines
                                    (when (and (not one-line?) hang?)
                                      hanging-lines))
                    hanging-line-count (first hanging-lines)
                    ; Don't flow if it fit, or it didn't fit and we were doing
                    ; one line on input.  Do flow if we don't have
                    ; hanging-lines and we were not one-line on input.
                    _ (dbg options
                           "fzprint-two-up: fit?" fit?
                           "hanging-lines:" hanging-lines)
                    _ (log-lines options
                                 "fzprint-two-up: hanging-2:"
                                 hanging-indent
                                 hanging)
                    flow-it? (or (not hanging-lines)
                                 ; TODO: figure out what this was supposed to
                                 ; be and fix it, w/out (not hanging-lines)
                                 (and (or (and (not hanging-lines)
                                               (not one-line?))
                                          (not (or fit? one-line?)))
                                      ; this is for situations where the first
                                      ; element is short and so the hanging
                                      ; indent
                                      ; is the same as the flow indent, so there
                                      ; is no point in flow -- unless we don't
                                      ; have
                                      ; any hanging-lines, in which case we
                                      ; better
                                      ; do flow
                                      (or (< flow-indent hanging-indent)
                                          (not hanging-lines))))
                    #_(prn "depth:" (:depth options)
                           "justify-width:" justify-width
                           "flow-it?" flow-it?
                           "arg-1" arg-1)
                    flow-it? (if (use-hang? caller
                                            options
                                            ind
                                            hang-count
                                            hanging-line-count)
                               false
                               flow-it?)
                    #_(inc-pass-count)
                    _ (dbg-s options
                             #{:justify-opt}
                             "fzprint-two-up: before flow. flow-it?"
                             flow-it?)
                    flow (when flow-it? (fzprint* roptions flow-indent rloc))
                    _ (log-lines options
                                 "fzprint-two-up: flow:"
                                 (+ indent ind)
                                 flow)
                    flow-lines (style-lines options (+ indent ind) flow)]
                (when dbg-local?
                  (prn "fzprint-two-up: local-hang:" local-hang?)
                  (prn "fzprint-two-up: one-line?:" one-line?)
                  (prn "fzprint-two-up: hanging-indent:" hanging-indent)
                  (prn "fzprint-two-up: hanging-lines:" hanging-lines)
                  (prn "fzprint-two-up: flow?:" flow?)
                  (prn "fzprint-two-up: force-flow?:" force-flow?)
                  (prn "fzprint-two-up: flow-it?:" flow-it?)
                  (prn "fzprint-two-up: fit?:" fit?)
                  (prn "fzprint-two-up: flow-indent:" flow-indent)
                  (prn "fzprint-two-up: hanging:" (zstring lloc) hanging)
                  (prn "fzprint-two-up: (+ indent ind):" (+ indent ind))
                  (prn "fzprint-two-up: flow:" (zstring lloc) flow))
                (dbg options "fzprint-two-up: before good-enough")
                (dbg-s options
                       #{:justify :justify-opt}
                       "fzprint-two-up: b" (zstring lloc)
                       "hanging-lines:" hanging-lines
                       "flow-lines:" flow-lines
                       "justify-width:" justify-width
                       "justifying?" justifying?
                       "fit?" fit?)
                (if fit?
                  (do (dbg-s options
                             :justify-result
                             "fzprint-two-up:"
                             (zstring lloc)
                             "justify-width:"
                             justify-width
                             "justifying?"
                             justifying?
                             "narrow-width"
                             narrow-width
                             "fit -- did hang")
                      [:hang
                       (concat-no-nil arg-1
                                      [[(blanks hanging-spaces) :none
                                        :whitespace 3]]
                                      hanging)])
                  (when (or hanging-lines flow-lines)
                    (dbg-s options
                           #{:justify-opt}
                           "fzprint-two-up: before good-enough: narrow-width:"
                             narrow-width
                           "justify-width" justify-width
                           "(fzfit-one-line ...)" (fzfit-one-line loptions
                                                                  arg-1-lines)
                           "coll?" (nth (first arg-1) 2))
                    (if (good-enough? caller
                                      (if #_justifying?
                                        justify-width
                                        roptions
                                        non-justify-roptions)
                                      :none-two-up
                                      hang-count
                                      (- hanging-indent flow-indent)
                                      hanging-lines
                                      flow-lines)
                      (do (dbg-s options
                                 :justify-result
                                 "fzprint-two-up:"
                                 (zstring lloc)
                                 "justify-width:"
                                 justify-width
                                 "justifying?"
                                 justifying?
                                 "good enough succeeded")
                          [:hang
                           (concat-no-nil arg-1
                                          [[(blanks hanging-spaces) :none
                                            :whitespace 4]]
                                          hanging)])
                      (do
                        ; If we are justifying and this one was supposed to
                        ; justify and good enough liked the flow better
                        ; than the hang, then let's cancel justifying for
                        ; everyone.
                        (if (and justifying? justify-width)
                          (do (dbg-s
                                options
                                #{:justify :justify-cancelled :justify-result}
                                "fzprint-two-up:"
                                (zstring lloc)
                                "justify-width:"
                                justify-width
                                "justifying?"
                                justifying?
                                "arg-1-fit-oneline?"
                                arg-1-fit-oneline?
                                "one-line?"
                                one-line?
                                "arg-1-lines:"
                                arg-1-lines
                                "hanging-lines:"
                                hanging-lines
                                "flow-lines:"
                                flow-lines
                                "hang?"
                                hang?
                                "cancelled justification, returning nil!"
                                "\n"
                                "good enough follows:")
                              (dbg-s options
                                     #{:justify :justify-cancelled}
                                     "fzprint-two-up:" (zstring lloc)
                                     "\n" (good-enough?
                                            caller
                                            (assoc (if justify-width
                                                     roptions
                                                     non-justify-roptions)
                                              :dbg? true)
                                            :none-two-up
                                            hang-count
                                            (- hanging-indent flow-indent)
                                            hanging-lines
                                            flow-lines))
                              nil)
                          ; We either weren't justifying or this one wasn't
                          ; supposed to justify anyway, so we'll flow.
                          ;
                          ; But if we did use narrow, and the lhs is
                          ; > 1 lines, and it is a collection,
                          ; then let's try the lhs w/out any narrow
                          ; since the narrow can't help us now.
                          ;
                          ; But, we shouldn't be narrowing if we aren't
                          ; justifying, whether or not we wanted to justify.
                          (let [_ (dbg-s options
                                         #{:justify-opt}
                                         "fzprint-two-up: c narrow-width:"
                                           narrow-width
                                         "(fzfit-one-line ...)"
                                           (fzfit-one-line loptions arg-1-lines)
                                         "coll?" (nth (first arg-1) 2))
                                arg-1
                                  (if (and narrow-width
                                           (not (fzfit-one-line loptions
                                                                arg-1-lines))
                                           (= (nth (first arg-1) 2) :left))
                                    (do (dbg-s
                                          options
                                          #{:justify-opt}
                                          "fzprint-two-up: redoing lhs arg-1")
                                        (fzprint* loptions-non-narrow ind lloc))
                                    arg-1)
                                _ (dbg-s options
                                         #{:justify-opt}
                                         "fzprint-two-up: style-lines:"
                                         (style-lines options ind arg-1))]
                            [:flow
                             (concat-no-nil arg-1
                                            (prepend-nl options
                                                        (+ indent ind)
                                                        flow))])))))))))
        :else (if (and justify-width (> (count pair) 2))
                ; Fail justification if we are justifying and
                ; there are more then two things in the pair.
                ; Issue #271.
                ; This is an issue when we have :respect-nl, and
                ; so we put a newline in the first time we format,
                ; and then the second time we format we might well
                ; have 3 things, and so we don't even think about
                ; justifying it, and the other things justify, so
                ; then the output changes to be justified for some
                ; pairs and not others.
                ;
                ; On the other hand, if the third thing is a comment,
                ; this is a bit restrictive.  Let's try just failing
                ; if one of the things is actually a newline.
                (do
                  (dbg-s options
                         #{:justify :justify-result}
                         "fzprint-two-up:"
                         (zstring lloc)
                         "justify-width:"
                         justify-width
                         "justifying?"
                         justifying?
                         "(count pair)"
                         (count pair)
                         "(some zcomment?)"
                         (some zcomment? pair)
                         "zstring pair"
                         (pr-str (map zstring pair))
                         "Justifiction more than 2 things")
                  (if (some zcomment? pair)
                    [:flow
                     ; The following always flows things of 3 or more
                     ; if they have a comment in them.  It doesn't
                     ; cancel justification since this
                     ; (probably?) can't be added by running zprint
                     ; multiple times.
                     (concat-no-nil arg-1
                                    (fzprint-flow-seq
                                      caller
                                      ; Issue #271 -- respect-nl
                                      ; causing files to change
                                      ; after one format.  Kicks
                                      ; them into
                                      ; (= (count pair) 3), and
                                      ; then rightcnt was +1.
                                      ; Was options, not roptions.
                                      roptions
                                      (+ indent ind)
                                      (if modifier? (nnext pair) (next pair))
                                      :force-nl
                                      :newline-first))]
                    (do (dbg-s
                          options
                          #{:justify :justify-result :justify-cancelled}
                          "fzprint-two-up:"
                          (zstring lloc)
                          "justify-width:"
                          justify-width
                          "justifying?"
                          justifying?
                          "(count pair)"
                          (count pair)
                          "(some zcomment?)"
                          (some zcomment? pair)
                          "zstring pair"
                          (pr-str (map zstring pair))
                          "Failed justifiction more than 2 things, no comment")
                        nil))
                  #_nil)
                (do
                  (dbg-s options
                         #{:justify :justify-result}
                         "fzprint-two-up:"
                         (zstring lloc)
                         "justify-width:"
                         justify-width
                         "justifying?"
                         justifying?
                         "(count pair)"
                         (count pair)
                         "More than two things, not justifying, flowing rhs")
                  [:flow
                   ; The following always flows things of 3 or more
                   ; (absent modifers).  If the lloc is a single char,
                   ; then that can look kind of poor.  But that case
                   ; is rare enough that it probably isn't worth dealing
                   ; with.  Possibly a hang-remaining call might fix it.
                   (concat-no-nil arg-1
                                  (fzprint-flow-seq
                                    caller
                                    ; Issue #271 -- respect-nl
                                    ; causing files to change
                                    ; after one format.  Kicks
                                    ; them into
                                    ; (= (count pair) 3), and
                                    ; then rightcnt was +1.
                                    ; Was options, not roptions.
                                    roptions
                                    (+ indent ind)
                                    (if modifier? (nnext pair) (next pair))
                                    :force-nl
                                    :newline-first))]))))))

;;
;; # Two-up printing
;;

(defn fzprint-justify-width
  "Figure the width for a justification of a set of pairs in coll.  
  Also, decide if it makes any sense to justify the pairs at all.
  narrow? says that this call has a narrower width than necessary,
  and triggers a check to see if any of the firsts are collections. 
  If they are not collections, and narrow-width is non-nil, then return 
  nil."
  [caller {{:keys [justify? justify multi-lhs-hang?]} caller, :as options} ind
   narrow-width coll]
  (let [ignore-for-variance (:ignore-for-variance justify)
        no-justify (:no-justify justify)
        ; Get rid of all of the things with only one element in them
        coll-2-or-more (remove nil? (map #(when (> (count %) 1) %) coll))
        firsts (map #(let [narrow-result (when narrow-width
                                           (fzprint* (not-rightmost
                                                       (assoc options
                                                         :width narrow-width))
                                                     ind
                                                     (first %)))
                           ; If the narrow-result didn't work at all, try it
                           ; w/out
                           ; narrowing, so we at least have something.
                           ; This needs to use justify-options as much as the
                           ; one
                           ; above does, just not being narrow.  The code here
                           ; has to match the code in fzprint-two-up where it
                           ; does the lhs again if it fails on narrowing.
                           result (if narrow-result
                                    narrow-result
                                    ; Try again w/out narrowing
                                    (fzprint* (not-rightmost options)
                                              ind
                                              (first %)))]
                       result)
                 coll-2-or-more)
        #_(println "count coll-2-or-more" (count coll-2-or-more)
                   "firsts\n" ((:dzprint options) {} firsts))
        #_(def justall
            (mapv #(fzprint* (not-rightmost options) ind (first %)) coll))
        #_(def just firsts)
        ; If we aren't supposed to justify something at all, remove it
        ; from the variance calculation here.
        firsts (if no-justify (remove #(no-justify (ffirst %)) firsts) firsts)]
    ; If we are narrowing, are any of the first we have encountered
    ; collections?  If not, then we have no reason to narrow, so return
    ; nil.  Also, if we had any lhs that didn't actually fit at all, return
    ; nil as well.
    (when (and (not (contains-nil? firsts))
               (or (not narrow-width)
                   (some true? (mapv #(= (nth (first %) 2) :left) firsts))))
      ; Is there anything that we should ignore for the variance calculation
      ; but still justify?  This is largely for :else, which, when it
      ; appears, it typically the last thing in a cond.  It seems fruitless
      ; to not justify a cond because a short :else drives the variance
      ; too high, since it is rarely hard to line up the :else with
      ; its value, no matter how far apart they are.
      (let [firsts (if ignore-for-variance
                     (remove #(ignore-for-variance (ffirst %)) firsts)
                     firsts)
            style-seq (mapv (partial style-lines options ind) firsts)
            #_(println "style-seq:" ((:dzprint options) {} style-seq))
            #_(def styleseq style-seq)
            ; If we allow multi-lhs-hang?, then act like each was on one
            ; line
            each-one-line?
              (if multi-lhs-hang?
                true
                (reduce #(when %1 (= (first %2) 1)) true style-seq))
            #_(def eol each-one-line?)
            ; max-gap is nilable, so make sure it is a number
            max-gap-configured (:max-gap justify)
            max-gap-allowed (or max-gap-configured 1000)
            max-gap (if max-gap-configured
                      (let [widths (mapv second style-seq)]
                        (if (not (empty? widths))
                          (let [max-width (apply max widths)
                                min-width (apply min widths)]
                            ; Add one for the space
                            (inc (- max-width min-width)))
                          0))
                      0)
            #_(def mg [max-gap max-gap-allowed])
            max-gap-ok? (<= max-gap max-gap-allowed)
            max-variance (:max-variance justify)
            ; i273
            ; take width of last line
            #_(println [(vec (map #(- (peek (nth % 2)) ind) style-seq))])
            ; take max-width of all of the lines
            #_(println [(vec (map #(- (second %) ind) style-seq))])
            #_(println "style-seq:" style-seq)
            alignment (when (and each-one-line? max-gap-ok?)
                        (column-width-variance
                          max-variance
                          ; i273
                          (if (:multi-lhs-overlap? justify)
                            [(vec (map #(- (peek (nth % 2)) ind) style-seq))]
                            [(vec (map #(- (second %) ind) style-seq))])
                          0))
            _ (dbg-s options
                     #{:justify :justify-cancelled :justify-width}
                     "fzprint-justify-width" (pr-str (take 6 (first firsts))
                                                     #_firsts)
                     "max-variance:" max-variance
                     "ind:" ind
                     "ignore-for-variance:" ignore-for-variance
                     "no-justify" no-justify
                     "narrow-width" narrow-width
                     "multi-lhs-overlap?" (:multi-lhs-overlap? justify)
                     "each-one-line?" each-one-line?
                     "alignment:" alignment)
            #_(prn "what:"
                   (ffirst firsts)
                   (first (last firsts))
                   "alignment:" alignment
                   "ind:" ind)
            justify-width (when each-one-line? (first alignment))]
        justify-width))))

(defn fit-within?
  "Take a size and a collection of vectors with two or more elements
  per vector.  The elements are zlocs, the vectors are not.  Return
  the remaining character count or nil if it for sure doesn't fit.
  In order to be sure it doesn't fit, this version doesn't assume
  *any* separators, so it really underestimates the size."
  ([size coll depth]
   (reduce (fn [size element]
             (or (if (= depth 0)
                   (fit-within? size element (inc depth))
                   (let [remaining (- size (count (zstring element)))]
                     (when (pos? remaining) remaining)))
                 (reduced nil)))
     size
     coll))
  ([size coll] (fit-within? size coll 0)))

(defn remove-hangflow
  "Convert a hangflow style-vec to a sequence of regular style-vecs."
  [hf-style-vec]
  (when hf-style-vec (map second hf-style-vec)))

(defn pair-lengths
  "When given the output from fzprint-map-two-up, calculate return the
  output from style-lines for each thing."
  [options ind result]
  (mapv (comp (partial style-lines options ind) second) result))

(defn fzprint-two-up-pass
  "Do one pass mapping fzprint-two-up across a sequence of pairs. The
  options and width are whatever is appropriate for this pass."
  [caller
   {{:keys [justify? force-nl? multi-lhs-hang?]} caller,
    :keys [width rightcnt one-line? parallel?],
    :as options} ind justify-width justify-options narrow-width commas?
   force-flow? coll]
  (let [len (count coll)
        beginning-coll (butlast coll)
        ; If beginning-coll is () because there is only a single pair
        ; in coll, then this all works -- but only because
        ; () is truthy, and zpmap returns () which is also truthy.
        ; I hate relying on the truthy-ness of (), but in this case
        ; it works out and it would be even more complicated to do
        ; it another way.
        beginning-remaining
          (if one-line? (fit-within? (- width ind) beginning-coll) true)
        _ (dbg options
               "fzprint-two-up-pass: remaining:" (- width ind)
               "beginning-remaining:" beginning-remaining)
        beginning (when beginning-remaining
                    (zpmap options
                           (partial fzprint-two-up
                                    caller
                                    options
                                    ind
                                    commas?
                                    justify-width
                                    justify-options
                                    narrow-width
                                    nil ; rightmost-pair?
                                    force-flow?)
                           beginning-coll))
        beginning (if (contains-nil? beginning) nil beginning)
        end-coll [(last coll)]
        ; If this is one-line? is there any point to even looking
        ; at the end?
        end-remaining
          (if one-line?
            (and beginning
                 (fit-within? (- beginning-remaining (or rightcnt 0)) end-coll))
            true)
        _ (dbg options
               "fzprint-two-up-pass: beginning-remaining:" beginning-remaining
               "rightcnt:" rightcnt
               "end-remaining:" end-remaining)
        end (when end-remaining
              (when-let [end-result (fzprint-two-up caller
                                                    options
                                                    ind
                                                    commas?
                                                    justify-width
                                                    justify-options
                                                    narrow-width
                                                    :rightmost-pair
                                                    force-flow? ; force-flow?
                                                    (first end-coll))]
                [end-result]))
        result (cond (= len 1) end
                     :else (concat-no-nil beginning end))]
    (dbg-s options
           #{:justify-result}
           "fzprint-two-up-pass: (count coll):" (count coll)
           "(nil? end):" (nil? end)
           "end:\n" (pr-str end)
           "\n(nil? beginning):" (nil? beginning)
           "beginning:\n" (pr-str beginning)
           "\n(count end):" (count end)
           "(count beginnging):" (count beginning)
           "justify-width:" justify-width)
    result))

(defn fzprint-map-two-up
  "Accept a sequence of pairs, and map fzprint-two-up across those pairs.
  If you have :one-line? set, this will return nil if it is way over,
  but it can't accurately tell exactly what will fit on one line, since
  it doesn't know the separators and such.  So, :one-line? true is a
  performance optimization, so it doesn't do a whole huge map just to
  find out that it could not possibly have fit on one line.  So, this
  returns a sequence of style-vecs, where the indentation for the
  stuff inside of the pairs is already there, but the separators of
  the style-vecs (including indentation and commas) is done by the
  caller of fzprint-map-two-up. Always returns a sequence of vector pairs:
  [[:hang <style-vec-for-one-pair>] [:flow <style-vec-for-one-pair>] ...].
  If you want a style vec instead, call remove-hangflow on the return 
  from fzprint-map-two-up.  This will use one-line?, but not check to see
  that it actually fits.  If you care about that, then you should check the
  return yourself.  It will, however, make an estimate of whether or not
  it will fit and if it clearly doesn't, it will return a nil."
  [caller
   {{:keys [justify? force-nl? multi-lhs-hang? justify]} caller,
    :keys [width rightcnt one-line? parallel?],
    :as options} ind commas? coll]
  (let [len (count coll)]
    ; If it is one-line? and force-nl? and there is more than one thing,
    ; this can't work.
    (when (not (and one-line? force-nl? (> len 1)))
      (let [caller-options (options caller)
            ; If the caller has flow? true, then justification is meaningless
            justify? (if (:flow? (caller options)) nil justify?)
            ; If we are justifying merge in a full options map,
            ; which can contain anything!  Don't validate it since
            ; it was already in the options map to start with.
            [justify-options _]
              (when justify?
                (internal-config-and-validate
                  (merge-deep options {caller (caller-options :justify-hang)})
                  (caller-options :justify-tuning)
                  (str "options in :justify-tuning for " caller)))
            ; Some callers do not have lhs-narrow defined
            lhs-narrow (or (:lhs-narrow justify) 1)
            ;
            ; There are two possibilties for justification:
            ;  o Regular justification, with the lhs as big as it is
            ;  o Narrowed justification, where the lhs is squeezed by a narrower
            ;    width.  Only helps if the lhs contains some collections.
            ;    Also this is only done if the lhs-narrow factor is more than
            ;    1.01.
            ;
            ; Each type of justification requires a different narrow-width.
            ;
            ; We will do both and compare them if they work.  They can fail
            ; to work in two ways:
            ;
            ;  1. They can fail in fzprint-justify-width, because a good
            ;     justify-width can't be found.
            ;  2. They can fail in fzprint-two-up-pass, because the rhs had
            ;     to flow, which will fail the justification.
            ;
            ; If only one of them works, we will use that.
            ;
            ; Do the fzprint-justify-width for any lhs-narrow
            use-narrow? (and (> (- lhs-narrow 1) 0.01) justify? multi-lhs-hang?)
            narrow-width (when use-narrow?
                           (int (+ (/ (- (:width options) ind) lhs-narrow)
                                   ind)))
            justify-narrow-width
              (when (and justify? narrow-width (not one-line?))
                (fzprint-justify-width caller
                                       justify-options
                                       ind
                                       narrow-width
                                       coll))
            ; Do the fzprint-justify-width without lhs-narrow
            justify-width
              (when (and justify? (not one-line?))
                (fzprint-justify-width caller justify-options ind nil coll))
            _ (dbg-s options
                     #{:narrow :justify-width}
                     "fzprint-map-two-up:"
                     "caller:"
                     caller
                     (zstring (first (first coll)))
                     "lhs-narrow:" lhs-narrow
                     "justify-narrow-width:" justify-narrow-width
                     "justify-width:" justify-width)
            _ (dbg-print options
                         "fzprint-map-two-up: one-line?" (:one-line? options)
                         "justify?:" justify?)
            force-flow? nil
            flow-all-if-any? (:flow-all-if-any? caller-options)
            ; If we are justifying, give that a try
            result-narrow (when justify-narrow-width
                            ;
                            ; Pass 1: Justification, using a narrowed lhs
                            ;         width, if any.
                            ;
                            (fzprint-two-up-pass caller
                                                 options
                                                 ind
                                                 justify-narrow-width
                                                 justify-options
                                                 narrow-width
                                                 commas?
                                                 force-flow?
                                                 coll))
            result (when justify-width
                     ;
                     ; Pass 1A: Justification without narrowing
                     ;
                     (fzprint-two-up-pass caller
                                          options
                                          ind
                                          justify-width
                                          justify-options
                                          width
                                          commas?
                                          force-flow?
                                          coll))
            ; Compare the two justifications, if any, and pick the
            ; best.
            result (if (and result result-narrow)
                     ; We have both justifications, compare them
                     (let [result-lines
                             (style-lines-hangflow options ind result)
                           result-narrow-lines
                             (style-lines-hangflow options ind result-narrow)]
                       ;  [<line-count> <max-width> [line-lengths]].
                       ;
                       ; Use the one with the fewer lines
                       ;
                       (dbg-s options
                              #{:narrow :justify-compare}
                              "fzprint-map-two-up: COMPARE "
                              "caller:"
                              caller
                              (zstring (first (first coll)))
                              "lhs-narrow:" lhs-narrow
                              "result-lines:" result-lines
                              "result-narrow-lines:" result-narrow-lines
                              ; "\nresult:"
                              ; ((:dzprint options) {} result)
                              ; "\nresult-narrow:"
                              ; ((:dzprint options) {} result-narrow)
                       )
                       (if (> (first result-narrow-lines) (first result-lines))
                         result
                         result-narrow))
                     ; We don't have both - use what we have, if any
                     (or result result-narrow))
            result (if result
                     result
                     ; The justifications didn't work or wasn't requested,
                     ; let's try the regular approach.
                     ;
                     ; Pass 2: Regular, non-justified
                     ;
                     (fzprint-two-up-pass caller
                                          options
                                          ind
                                          nil     ;justify-width
                                          options ;justify-options
                                          width
                                          commas?
                                          force-flow?
                                          coll))
            ; Check to see if anything flowed, and if care about
            ; how many did because of flow-all-if-any?
            [flow-all-if-any-fail? result hang-flow-set]
              (if flow-all-if-any?
                (let [hang-flow-set (set (mapv first result))]
                  (if (<= (count hang-flow-set) 1)
                    [false result hang-flow-set]
                    [true nil hang-flow-set]))
                [false result nil])
            result (if flow-all-if-any-fail?
                     ; We had a flow but not all flowed, and we had
                     ; flow-all-if-any? true, so try again and force flow
                     ; for all pairs.
                     ;
                     ; Pass 3: Force flow for every pair
                     ;
                     (fzprint-two-up-pass caller
                                          options
                                          ind
                                          nil     ;justify-width
                                          options ;justify-options
                                          width
                                          commas?
                                          true    ;force-flow?
                                          coll)
                     result)
            _ (when flow-all-if-any-fail?
                (let [hang-flow-set (set (mapv first result))]
                  (when (> (count hang-flow-set) 1)
                    (dbg-pr options
                            "fzprint-map-two-up: ****************#############"
                              " force-flow? didn't yield a single value in"
                            " hang-flow-set:" hang-flow-set))))]
        (dbg-pr options
                "fzprint-map-two-up: len:" len
                "justify-width:" justify-width
                "flow-all-if-any?" flow-all-if-any?
                "flow-all-if-any-fail?" flow-all-if-any-fail?
                "hang-flow-set:" hang-flow-set)
        (dbg options
             "fzprint-map-two-up: result:"
             ((:dzprint options) {} result))
        (do #_(let [pair-lens (pair-lengths options ind result)]
                (println "result:"
                         ((:dzprint options) {} result)
                         ((:dzprint options) {} pair-lens)
                         "median:" (median (mapv first pair-lens))
                         "mean:" (int (mean (mapv first pair-lens)))
                         "percent-gt-n:" (percent-gt-n 3
                                                       (mapv first pair-lens))))
            result)))))

;;
;; ## Support sorting of map keys
;;

(defn compare-keys
  "Do a key comparison that works well for numbers as well as
  strings."
  [x y]
  (cond (and (number? x) (number? y)) (compare x y)
        :else (compare (str x) (str y))))

(defn compare-ordered-keys
  "Do a key comparison that places ordered keys first."
  [key-value zdotdotdot x y]
  (cond (and (key-value x) (key-value y)) (compare (key-value x) (key-value y))
        (key-value x) -1
        (key-value y) +1
        (= zdotdotdot x) +1
        (= zdotdotdot y) -1
        :else (compare-keys x y)))

(defn order-out
  "A variety of sorting and ordering options for the output of
  partition-all-2-nc.  It can sort, which is the default, but if
  the caller has a key-order vector, it will extract any keys in
  that vector and place them first (in order) before sorting the
  other keys.  If sorting is not called for, does nothing."
  [caller
   {{:keys [sort? sort-in-code? key-order key-value]} caller,
    :keys [in-code?],
    :as options} access out]
  (dbg-pr options
          "order-out caller:" caller
          "key-order:" key-order
          "sort?" sort?
          "sort-in-code?" sort-in-code?
          "in-code?" in-code?
          "key-value:" key-value)
  (if (and sort? (if in-code? sort-in-code? true))
    (sort #((partial compare-ordered-keys (or key-value {}) (zdotdotdot))
              (get-sexpr options (access %1))
              (get-sexpr options (access %2)))
          out)
    out))

(defn pair-element?
  "This checks to see if an element should be considered part of a
  pair if it comes between other elements, and a single element on
  its own if it would otherwise be the first part of a pair.  Mostly
  this will trigger on comments, but a #_(...) element will also
  trigger this, as will a newline if one appears."
  [zloc]
  (or (zcomment? zloc) (zuneval? zloc) (znewline? zloc)))

(defn nosort?
  "Check a zloc to see if this should trigger no-sort? for this set
   of pairs."
  [no-sort-set zloc]
  (when no-sort-set
    (and (= (ztag zloc) :token)
         (let [s (zstring zloc)]
           (or (no-sort-set s)
               (let [regex-seq (filter (comp not string?) no-sort-set)]
                 (when (not (empty? regex-seq))
                   (some #(re-find % s) regex-seq))))))))


(defn middle-element?
  "This checks to see if an element should be considered the middle element
  of a pair.  At some point, we can expand this, but for now there is only
  one middle element."
  [{:keys [in-code?], :as options} zloc]
  ;  nil)
  (when (= in-code? "condp") (= (zstring zloc) ":>>")))

;;
;; # Ignore keys in maps
;;

(defn remove-key-seq
  "If given a non-collection, simply does a dissoc of the key, but
  if given a sequence of keys, will remove the final one."
  [m ks]
  (if (coll? ks)
    (let [this-key (first ks)
          next-key (next ks)]
      (if next-key
        (let [removed-map (remove-key-seq (get m this-key) (next ks))]
          (if (empty? removed-map)
            (dissoc m this-key)
            (assoc m this-key removed-map)))
        (dissoc m this-key)))
    (dissoc m ks)))

(defn ignore-key-seq-silent
  "Given a map and a key sequence, remove that key sequence if
  it appears in the map, and terminate the reduce if it changes
  the map."
  [m ks]
  (if (coll? ks)
    (if (= (get-in m ks :zprint-not-found) :zprint-not-found)
      m
      (remove-key-seq m ks))
    (if (= (get m ks :zprint-not-found) :zprint-not-found) m (dissoc m ks))))

(defn ignore-key-seq
  "Given a map and a key sequence, remove that key sequence if
  it appears in the map leaving behind a key :zprint-ignored, 
  and terminate the reduce if it changes the map."
  [m ks]
  (if (coll? ks)
    (if (= (get-in m ks :zprint-not-found) :zprint-not-found)
      m
      (assoc-in m ks :zprint-ignored))
    (if (= (get m ks :zprint-not-found) :zprint-not-found)
      m
      (assoc m ks :zprint-ignored))))

(defn map-ignore
  "Take a map and remove any of the key sequences specified from it.
  Note that this only works for sexpressions, not for actual zippers."
  [caller {{:keys [key-ignore key-ignore-silent]} caller, :as options} zloc]
  (let [ignored-silent (if key-ignore-silent
                         (reduce ignore-key-seq-silent zloc key-ignore-silent)
                         zloc)
        ignored (if key-ignore
                  (reduce ignore-key-seq ignored-silent key-ignore)
                  ignored-silent)]
    ignored))

;;
;; # Pre-processing for two-up printing
;;

(defn partition-all-2-nc
  "Input is (zseqnws zloc) or (zseqnws-w-nl) where one assumes that
  these are pairs.  Thus, a seq of zlocs.  Output is a sequence of
  seqs, where the seqs are usually pairs, but might be single things.
  Doesn't pair up comments or #_(...) unevaled sexpressions.  The
  ones before the first part of a pair come as a single element in
  what would usually be a pair, and the ones between the first and
  second parts of a pair come inside the pair.  There may be an
  arbitrary number of elements between the first and second elements
  of the pair (one per line).  If there are any comments or unevaled
  sexpressions, don't sort the keys, as we might lose track of where
  the comments or unevaled s-expressions go."
  [caller options coll]
  (when-not (empty? coll)
    (let [max-length (get-max-length options)
          no-sort-set (:key-no-sort (caller options))]
      (loop [remaining coll
             no-sort? nil
             index 0
             out (transient [])]
        (dbg-pr options
                "partition-all-2-nc: index:" index
                "no-sort?:" no-sort?
                ;  "out:" (map (comp zstring first)(persistent! out))
                "first remaining:" (zstring (first remaining))
                "second remaining:" (zstring (second remaining)))
        (if-not remaining
          [no-sort? (persistent! out)]
          (let [[new-remaining pair-vec new-no-sort?]
                  (cond (pair-element? (first remaining))
                          [(next remaining) [(first remaining)] true]
                        (or (pair-element? (second remaining))
                            (middle-element? options (second remaining)))
                          (let [[comment-seq rest-seq]
                                  ;(split-with pair-element? (next remaining))
                                  (split-with #(or (pair-element? %)
                                                   (middle-element? options %))
                                              (next remaining))]
                            (if (first rest-seq)
                              ; We have more to than just a comment, so we can
                              ; pair it up between two things.
                              [(next rest-seq)
                               (into []
                                     (concat [(first remaining)]
                                             comment-seq
                                             [(first rest-seq)])) true]
                              ; This is the end, don't pair a comment up
                              ; with something on the left if there isn't
                              ; something on the right of it.
                              [(next remaining) [(first remaining)] true]))
                        (= (count remaining) 1) [(next remaining)
                                                 [(first remaining)] nil]
                        :else [(next (next remaining))
                               [(first remaining) (second remaining)] nil])
                new-no-sort? (or new-no-sort?
                                 (nosort? no-sort-set (first remaining)))]
            #_(println "partition-all-2-nc: count new-remaining:"
                       (count new-remaining))
            (dbg-pr options
                    "partition-all-2-nc: pair-vec: first:" (zstring (first
                                                                      pair-vec))
                    "first tag:" (ztag (first pair-vec))
                    "count:" (count pair-vec)
                    "last:" (zstring (last pair-vec)))
            (recur (cond (< (inc index) max-length) new-remaining
                         (and (= (inc index) max-length) new-remaining)
                           (list (zdotdotdot))
                         :else nil)
                   (or no-sort? new-no-sort?)
                   (inc index)
                   (conj! out pair-vec))))))))

;;
;; ## Multi-up printing pre-processing
;;

(defn cleave-end
  "Take a seq, and if it is contains a single symbol, simply return
  it in another seq.  If it contains something else, remove any non
  collections off of the end and return them in their own double seqs,
  as well as return the remainder (the beginning) as a double seq."
  [coll]
  (if (or (zsymbol? (first coll)) (zreader-cond-w-symbol? (first coll)))
    ;(symbol? (first coll))
    (list coll)
    (let [rev-seq (reverse coll)
          [split-non-coll _]
            ;(split-with (comp not zcoll?) rev-seq)
            (split-with #(not (or (zcoll? %) (zreader-cond-w-coll? %))) rev-seq)
          #_(def sncce split-non-coll)
          split-non-coll (map list (reverse split-non-coll))
          remainder (take (- (count coll) (count split-non-coll)) coll)]
      (if (empty? remainder)
        split-non-coll
        (concat (list remainder) split-non-coll)))))

(defn partition-all-sym
  "Similar to partition-all-2-nc, but instead of trying to pair things
  up (modulo comments and unevaled expressions), this begins things
  with a symbol, and then accumulates collections until the next symbol.
  Returns a seq of seqs, where the first thing in each internal seq is
  a protocol and the remaining thing(s) in that seq are the expressions that
  follow.  If there is a single thing, it is returned in its own internal
  seq. ((P (foo [this a) (bar-me [this] b) (barx [this y] (+ c y))) ...)
  Made harder by the fact that the symbol might be inside of a #?() reader
  conditional.  It handles comments before symbols on the symbol indent, 
  and the comments before the collections on the collection indent.  
  Since it doesn't know how many collections there are, this is not trivial.  
  Must be called with a sequence of z-things (these days called a zseq)"
  [options modifier-set coll]
  #_(prn "partition-all-sym:")
  (dbg-pr options "partition-all-sym:" modifier-set)
  #_(def scoll coll)
  (dbg options "partition-all-sym: coll:" (map zstring coll))
  (let [part-sym (partition-by
                   #(or (zsymbol? %) (znil? %) (zreader-cond-w-symbol? %))
                   coll)
        split-non-coll (mapcat cleave-end part-sym)]
    #_(def ps part-sym)
    #_(def snc split-non-coll)
    (loop [remaining split-non-coll
           out (transient [])]
      #_(prn "remaining:" (zprint.repl/pseqzseq remaining))
      #_(prn "out:" (zprint.repl/pseqzseq out))
      (if (empty? remaining)
        (let [result (persistent! out)]
          #_(def pasn result)
          result)
        (let [[next-remaining new-out]
                (cond (and (or (zsymbol? (ffirst remaining))
                               (znil? (ffirst remaining))
                               (zreader-cond-w-symbol? (ffirst remaining)))
                           (not (empty? (second remaining)))
                           ; This keeps a comment after a symbol with no
                           ; collections from being associated with the previous
                           ; symbol instead of standing on its own (as it
                           ; should)
                           (or (not (or (= (ztag (first (second remaining)))
                                           :comment)
                                        (= (ztag (first (second remaining)))
                                           :newline)))
                               (zcoll? (last (second remaining)))))
                        ; We have a non-collection in (first remaining) and
                        ; we might have more than one, either because we just
                        ; have a bunch of non-colls with no colls
                        ; or because we have a modifier and then one or more
                        ; non-colls (possibly with their own modifiers).
                        (if (= (count (first remaining)) 1)
                          ; original
                          (do #_(prn "a:")
                              ; We have a single non-coll, pull the next seq
                              ; of one or more seqs into a seq with it.
                              ; This is where we marry up the non-coll with
                              ; all of its associated colls.
                              [(nthnext remaining 2)
                               (conj! out
                                      (concat (first remaining)
                                              (second remaining)))])
                          (do
                            #_(prn "b:")
                            (if (and modifier-set
                                     (modifier-set (zstring (ffirst
                                                              remaining))))
                              (if (= (count (first remaining)) 2)
                                ; We have exactly two things in
                                ; (first remaining), and the first one is
                                ; both a non-coll and a modifier, so we know
                                ; that the second one is a non-coll, and we
                                ; know that we have a (second remaining) from
                                ; above, so we bring the second remaining
                                ; into the first remaining like we did
                                ; above
                                (do #_(prn "d:")
                                    [(nthnext remaining 2)
                                     (conj! out
                                            (concat (first remaining)
                                                    (second remaining)))])
                                ; We have a modifier as the first thing in a
                                ; seq of non-colls and then some more non-colls
                                ; after that (since we don't have exactly two,
                                ; as that case was caught above).
                                ; Pull the next one into a seq with it.
                                ; Do we need to check that the next one is
                                ; also a non-coll?  That shouldn't be
                                ; necessary,as you won't get colls in
                                ;with non-colls.
                                (do #_(prn "c:")
                                    [(if (next (next (first remaining)))
                                       (cons (next (next (first remaining)))
                                             (next remaining))
                                       (next remaining))
                                     (conj! out
                                            (list (ffirst remaining)
                                                  (second (first
                                                            remaining))))]))
                              ; we have more than one non-coll in first
                              ; remaining, so pull one out, and leave the
                              ; next ones for the next loop
                              [(cons (next (first remaining)) (next remaining))
                               (conj! out (list (ffirst remaining)))])))
                      :else [(next remaining) (conj! out (first remaining))])]
          (recur next-remaining new-out))))))

(defn rstr-vec
  "Create an r-str-vec with the indent appropriate for the r-str if
  it is preceded by a newline."
  ([options ind zloc r-str r-type]
   [[r-str (zcolor-map options (or r-type r-str)) (or r-type :right) ind]])
  ([options ind zloc r-str] (rstr-vec options ind zloc r-str nil)))

(declare interpose-nl-hf)
(declare fzprint-get-zloc-seq)

(defn fzprint-binding-vec
  [{{:keys [nl-separator?]} :binding, :as options} ind zloc]
  (dbg options "fzprint-binding-vec: ind:" ind "zloc:" (zstring (zfirst zloc)))
  (let [options (rightmost options)
        l-str "["
        r-str "]"
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options ind zloc r-str)]
    (dbg-form options
              "fzprint-binding-vec exit:"
              (if (= (zcount zloc) 0)
                (concat-no-nil l-str-vec r-str-vec)
                (concat-no-nil
                  l-str-vec
                  (interpose-nl-hf
                    (:binding options)
                    (inc ind)
                    (fzprint-map-two-up
                      :binding
                      options
                      (inc ind)
                      false
                      (second
                        (partition-all-2-nc
                          :binding
                          options
                          ; This is controlled by the :vector config
                          ; options, because if we added it to the
                          ; :binding option, it would not work because
                          ; the fzprint-list* one line testing doesn't
                          ; know it is a binding vector, it thinks
                          ; that it is just a vector.  Alternatively
                          ; we could probably notice that we were in
                          ; a :binding fn-type, and force :vector
                          ; :respect-nl? to be the same as :binding
                          ; :respect-nl? for the one-line test.  Which
                          ; would fail if there were some other vector
                          ; with newlines in it that wasn't the
                          ; binding vector.  Ultimately this is because
                          ; :respect-nl? (and :respect-bl?) are only
                          ; defined for vectors, maps, lists and sets,
                          ; and that is implemented by changing what
                          ; gets returned as a zloc-seq.
                          (fzprint-get-zloc-seq :vector options zloc)))))
                  r-str-vec)))))

(defn fzprint-hang
  "Try to hang something and try to flow it, and then see which is
  better.  Has hang and flow indents. fzfn is the function to use 
  to do zloc.  Note what fzfn does with the input. Presumably the
  caller knows what the fzfn does, so it has to count the items
  itself and pass it in here as zloc-count if it isn't just (zcount zloc)."
  [{:keys [one-line? force-eol-blanks?], :as options} caller hindent findent
   fzfn zloc-count zloc]
  (dbg options "fzprint-hang: caller:" caller)
  (let [hanging (when (and (not= hindent findent)
                           ((options caller) :hang?)
                           ; If it starts with a newline, we aren't hanging
                           ; it.  Comment, sure, but not newline.
                           (not (znewline? (first zloc))))
                  (concat-no-nil [[(str " ") :none :whitespace 5]]
                                 (fzfn (in-hang options) hindent zloc)))
        #_(prn "fzprint-hang: first hanging:" (first hanging) (second hanging))
        _ (dbg options
               "fzprint-hang: caller:" caller
               "hang?" ((options caller) :hang?))
        hanging (when (not= (nth (second hanging) 2) :comment-inline) hanging)
        hang-count (or zloc-count (zcount zloc))
        hr-lines (style-lines options (dec hindent) hanging)
        ;flow (fzfn options findent zloc)
       ]
    (if (or (fzfit-one-line options hr-lines) one-line?)
      hanging
      (let [flow (let [result (fzfn options findent zloc)]
                   (concat-no-nil
                     ; This will create an end-of-line blanks situation so
                     ; we can test our ability to see it.  If we weren't
                     ; intentionally creating end-of-line blanks, we would
                     ; use prepend-nl here.
                     (if (if force-eol-blanks? nil (first-nl? result))
                       [[(str "\n") :none :indent 42]]
                       [[(str "\n" (blanks findent)) :none :indent 4]])
                     result))
            _ (log-lines options "fzprint-hang: flow:" findent flow)
            fd-lines (style-lines options findent flow)
            _ (dbg-pr options
                      "fzprint-hang: ending: hang-count:" hang-count
                      "hanging:" hanging
                      "flow:" flow)
            hr-good? (when hanging
                       (good-enough? caller
                                     options
                                     :none-hang
                                     hang-count
                                     (- hindent findent)
                                     hr-lines
                                     fd-lines))]
        (if hr-good? hanging flow)))))

(defn fzprint-pairs
  "Always prints pairs on a different line from other pairs. Takes a zloc-seq.
  Defaults to caller as :pair, but will accept :binding as an alternative."
  ([{{:keys [nl-separator? respect-nl?]} :pair, :as options} ind zloc-seq
    caller]
   (dbg-pr options
           "fzprint-pairs:" (zstring (first zloc-seq))
           "rightcnt:" (:rightcnt options))
   (dbg-form options
             "fzprint-pairs: exit:"
             (interpose-nl-hf
               (caller options)
               ind
               (fzprint-map-two-up
                 caller
                 options
                 ind
                 false
                 (let [[_ part] (partition-all-2-nc caller options zloc-seq)]
                   #_(def fp part)
                   (dbg-pr options
                           "fzprint-pairs: partition:" (map (comp zstring first)
                                                         part)
                           "respect-nl?" respect-nl?)
                   part)))))
  ([options ind zloc-seq] (fzprint-pairs options ind zloc-seq :pair)))

(defn check-for-coll?
  "Return true if the first non-newline element in the seq is a coll?"
  [zloc-seq]
  #_(prn "check-for-coll? begin sequence")
  (loop [coll zloc-seq]
    (if-not coll
      nil
      (let [zloc (first coll)]
        #_(prn "check-for-coll? zloc:" (ztag zloc))
        (cond (znewline? zloc) (recur (next coll))
              (zcoll? zloc) true
              (zsymbol? zloc) nil
              (znil? zloc) nil
              ; if we don't know what it is, should we skip it?
              :else (recur (next coll)))))))

(defn check-for-first-coll?
  "Check a series of sequences to see if the first non-newine thing in any 
  of them
  is a zcoll?.  If it is, return true, else nil."
  [seq-series]
  (some check-for-coll? seq-series))

(declare fzprint-hang-remaining)

(defn fzprint-extend
  "Print things with a symbol and collections following.  Kind of like with
  pairs, but not quite. Takes a zloc-seq."
  [{{:keys [nl-separator?]} :extend, :as options} ind zloc-seq]
  #_(def fezloc zloc-seq)
  (dbg options "fzprint-extend:" (zstring (first zloc-seq)))
  (let [part
          (partition-all-sym options (:modifiers (:extend options)) zloc-seq)]
    #_(def fe part)
    (dbg options "fzprint-extend: partition:" (map #(map zstring %) part))
    ; Look for any sequences in part which have zcoll? as their first
    ; element.  If we find any, we don't do the map-two-up, but rather
    ; fzprint-hang-remaining.
    (if (check-for-first-coll? part)
      ; The input does *not* look like an extend, so we won't try to
      ; format it like one.
      (dbg-form options
                "fzprint-extend: fzprint-hang-remaining exit:"
                (let [result (fzprint-hang-remaining :extend
                                                     (assoc options
                                                       :fn-style :fn)
                                                     ind
                                                     ind
                                                     zloc-seq
                                                     nil)]
                  ; If it starts with a newline, remove it, since we will be
                  ; doing
                  ; a prepend-nl for the results of fzprint-extend whenever we
                  ; use it.
                  (if (and (or (= (nth (first result) 2) :indent)
                               (= (nth (first result) 2) :newline))
                           (clojure.string/starts-with? (ffirst result) "\n"))
                    (next result)
                    result)))
      (dbg-form options
                "fzprint-extend: exit:"
                (interpose-nl-hf (:extend options)
                                 ind
                                 (fzprint-map-two-up :extend
                                                     (assoc options
                                                       :fn-style :fn)
                                                     ind
                                                     false
                                                     part))))))

(defn concatv!
  "Given a transient vector v, concatenate all of the other
  elements in all of the remaining collections onto v."
  [v & rest]
  (loop [cols rest
         out v]
    (if cols
      (recur (next cols)
             (loop [col (first cols)
                    out out]
               (if col (recur (next col) (conj! out (first col))) out)))
      out)))

(defn fzprint-one-line
  "Do a fzprint-seq like thing, but do it incrementally and
  if it gets too big, return nil."
  [options ind zloc-seq]
  (dbg-print options "fzprint-one-line:")
  (let [seq-right zloc-seq
        len (count seq-right)
        last-index (dec len)
        gt-1? (> (count seq-right) 1)
        options (assoc options :one-line? true)]
    (loop [zloc-seq seq-right
           new-ind (long ind)
           index 0
           out (transient [])]
      (if (empty? zloc-seq)
        (do (dbg options "fzprint-one-line: exiting count:" (count out))
            (persistent! out))
        (let [next-zloc (first zloc-seq)
              [sep next-options]
                (cond ; this needs to come first in case there
                      ; is only one element in the list -- it needs to have
                      ; the rightcnt passed through
                  (= index last-index) [(if-not (zero? index)
                                          [[" " :none :whitespace 6]]) options]
                  (= index 0) [nil (not-rightmost options)]
                  :else [[[" " :none :whitespace 7]] (not-rightmost options)])
              next-out (fzprint* next-options new-ind next-zloc)
              _ (log-lines options "fzprint-one-line:" new-ind next-out)
              [line-count max-width :as next-lines]
                (style-lines options new-ind next-out)]
          (if-not (fzfit-one-line next-options next-lines)
            (do (dbg options
                     "fzprint-one-line: failed, too wide or too many lines!")
                nil)
            (recur (next zloc-seq)
                   (inc (long max-width))
                   (inc index)
                   (concatv! out sep next-out))))))))

(defn fzprint-seq
  "Take a seq of a zloc, created by (zmap identity zloc).  Return
  a seq of the fzprint* of each element.  No spacing between any
  of these elements. Note that this is not a style-vec, but a seq
  of style-vecs of each of the elements.  These would need to be
  concatenated together to become a style-vec.  ind is either a
  constant or a seq of indents, one for each element in zloc-seq.
  Note that right gets evaluated immediately, while left yields a
  lazy sequence which get evaluated later."
  [options ind zloc-seq]
  (let [max-length (get-max-length options)
        len (count zloc-seq)
        zloc-seq (if (> len max-length)
                   (concat (take max-length zloc-seq) (list (zdotdotdot)))
                   zloc-seq)
        len (count zloc-seq)]
    (dbg options
         "fzprint-seq: (count zloc-seq):" len
         "max-length:" max-length
         "ind:" ind)
    (cond (empty? zloc-seq) nil
          (zero? max-length) [[["#?#" (zcolor-map options :keyword) :element]]]
          :else (let [left (zpmap options
                                  #(fzprint* (not-rightmost options) %1 %2)
                                  (if (coll? ind) ind (repeat ind))
                                  (butlast zloc-seq))
                      right [(fzprint* options
                                       (if (coll? ind) (last ind) ind)
                                       (last zloc-seq))]]
                  (cond (= len 1) right
                        :else (concat-no-nil left right))))))

(declare precede-w-nl)

(defn fzprint-flow-seq
  "Takes zloc-seq, a seq of a zloc, created by (zmap identity zloc),
  and returns a style-vec of the result.  Either it fits on one
  line, or it is rendered on multiple lines.  You can force multiple
  lines with force-nl?. If the seq is empty, returns :noseq, which
  is what you give concat-no-nil if you want this to just disappear.
  If you want it to do less than everything in the original zloc,
  modify the result of (zmap identity zloc) to just contain what
  you want to print. ind is either a single indent, or a seq of
  indents, one for each element in zloc-seq.  Don't concatenate an
  indent/newline on to the beginning of the output from this routine.
  Let this routine do it for you, as it needs to know one is there
  in order to properly deal with any newlines in the actual stream.
  Else you will get two where you only should have one."
  ([caller {{:keys [nl-count]} caller, :as options} ind zloc-seq force-nl?
    nl-first?]
   (dbg-pr options
           "fzprint-flow-seq: count zloc-seq:" (count zloc-seq)
           "nl-first?" nl-first?
           "zloc-seq:" (map zstring zloc-seq))
   (let [coll-print (fzprint-seq options ind zloc-seq)
         ; If we are force-nl?, then don't bother trying one-line
         one-line (apply concat-no-nil
                    (interpose [[" " :none :whitespace 8]] coll-print))
         _ (log-lines options "fzprint-flow-seq:" ind one-line)
         one-line-lines (style-lines options ind one-line)]
     (dbg-pr options "fzprint-flow-seq: coll-print:" coll-print)
     (dbg-form
       options
       "fzprint-flow-seq: exit:"
       (if (and (not force-nl?) (fzfit-one-line options one-line-lines))
         one-line
         (if (not (empty? coll-print))
           (apply concat-no-nil
             (precede-w-nl options ind coll-print (not nl-first?) nl-count))
           :noseq)))))
  ([caller options ind zloc-seq]
   (fzprint-flow-seq caller options ind zloc-seq nil nil))
  ([caller options ind zloc-seq force-nl?]
   (fzprint-flow-seq caller options ind zloc-seq force-nl? nil)))

(defn fzprint-hang-one
  "Try out the given zloc, and if it fits on the current line, just
  do that. It might fit on the same line, as this may not be the rest
  of the list that we are printing. If not, check it out with good-enough?
  and do the best you can.  Three choices, really: fits on same line, 
  does ok as hanging, or better with flow. hindent is hang-indent, and 
  findent is flow-indent, and each contains the initial separator.  
  Might be nice if the fn-style actually got sent to this fn."
  [caller {:keys [one-line? width], {:keys [hang-avoid]} caller, :as options}
   hindent findent zloc]
  (dbg-pr options
          "fzprint-hang-one:" (zstring zloc)
          " hindent:" hindent
          "findent:" findent)
  (when (:dbg-hang options)
    (println (dots (:pdepth options))
             "h1 caller:"
             caller
             (zstring (if (zcoll? zloc) (zfirst zloc) zloc))))
  (let [local-options (if (and (not one-line?) (not (:hang? (caller options))))
                        (assoc options :one-line? true)
                        options)
        ; If we don't have an hindent, we better not be trying to hang
        ; things -- in this case, we'll just flow.
        hindent (or hindent findent)
        hang-count (zcount zloc)
        ; This implements :hang-avoid for fzprint-hang-one, instead of just
        ; for fzprint-hang-remaining.  It didn't change the tests, but
        ; removed some silly formatting when using :arg2 and small widths.
        hanging (when (and (not= hindent findent)
                           (or (not hang-avoid)
                               (< hang-count (* (- width hindent) hang-avoid))))
                  (fzprint* (in-hang local-options) hindent zloc))
        hanging (concat-no-nil [[" " :none :whitespace 9]] hanging)
        _ (log-lines options "fzprint-hang-one: hanging:" (dec hindent) hanging)
        hr-lines (style-lines options (dec hindent) hanging)]
    _
    (dbg options
         "fzprint-hang-one: hr-lines:" hr-lines
         "hang-count:" hang-count)
    ; if hanging is nil and one-line? is true, then we didn't fit
    ; and should exit
    ;
    ; if hanging is nil and one-line? is nil, and hang? nil,
    ; then we we don't hang and this didn't fit on the same
    ; line and we should contine
    ;
    ; if hanging is true, then if one-line? is true and fzfit-one-line
    ; is true, then we just go with hanging
    ;
    ; if hanging is true and if fzfit-one-line is true, then we go
    ; with hanging.  Which is probably the same as just above.
    ;
    ; if hanging is true and if one-line? is nil, and if hang? is
    ; nil, and fzfit-one-line is true then it fit on one line and we
    ; should go with hanging.
    ;
    ;
    ; Summary:
    ;
    ; go with hanging if:
    ;
    ;  o fzfit-one-line true
    ;  o one-line? true
    ;
    ; Otherwise, see about flow too
    ;
    (if (or (fzfit-one-line options hr-lines) one-line?)
      hanging
      (let [flow (prepend-nl options findent (fzprint* options findent zloc))
            _ (log-lines options "fzprint-hang-one: flow:" findent flow)
            fd-lines (style-lines options findent flow)
            _ (dbg options "fzprint-hang-one: fd-lines:" fd-lines)
            #_(println "fzprint-hang-one: fd-lines:" fd-lines)
            _ (dbg options
                   "fzprint-hang-one: ending: hang-count:" hang-count
                   "hanging:" (pr-str hanging)
                   "flow:" (pr-str flow))
            hr-good? (and (:hang? (caller options))
                          ; no point in calling good-enough if no hr-lines
                          hr-lines
                          (good-enough? caller
                                        options
                                        :none-hang-one
                                        hang-count
                                        (- hindent findent)
                                        hr-lines
                                        fd-lines))]
        #_(println "fzprint-hang-one: hr-good?" hr-good?)
        (if hr-good? hanging flow)))))

;;
;; # Constant pair support
;;

(declare zcomment-or-newline?)

; This, you might think, would be faster.  But in reality it is almost
; exactly the same as the version that reverses the list.
; And even a bit more complex to understand, so we'll leave it
; here for additional possible optimizations and simplifications later.

#_(defn count-constant-pairs-new
    "Given a seq of zlocs, work backwards from the end, and see how
  many elements are pairs of constants (using zconstant?).  So that
  (... :a (stuff) :b (bother)) returns 4, since both :a and :b are
  zconstant? true. This is made more difficult by having to skip
  comments along the way as part of the pair check, but keep track
  of the ones we skip so the count is right in the end.  We don't
  expect any spaces in this but newlines must be handled, because 
  this seq should have been produced by zmap or its equivalent.
  Returns two things: [paired-item-count actual-paired-items],
  where paired-item-count is the number of things from the end of
  the seq you have to trim off to get the constant pairs included,
  and the actual-paired-items is the count of the items to be checked
  against the constant-pair-min (which is exclusive of comments and
  newlines).  "
    [zloc-seq]
    (let [zloc-seq (if (vector? zloc-seq) zloc-seq (into [] zloc-seq))
          len (count zloc-seq)
          last-element-idx (dec len)]
      ; If we don't have at least two elements, we aren't doing anything useful
      (if (not (pos? last-element-idx))
        [0 0]
        (loop [idx last-element-idx
               element-count 0
               paired-element-count 0
               ; since it is reversed, we need a constant every second element
               constant-required? nil
               pair-size 0
               actual-pair-size 0]
          (let [element (nth zloc-seq idx)]
            #_(prn "count-constant-pairs: element-count:" element-count
                   "paired-element-count:" paired-element-count
                   "constant-required:" constant-required?
                   "pair-size:" pair-size
                   "actual-pair-size:" actual-pair-size
                   "element:" (zstring element))
            (let [comment-or-newline? (zcomment-or-newline? element)]
              (if (and (not comment-or-newline?)
                       constant-required?
                       (not (zconstant? element)))
                ; we counted the right-hand and any comments of this pair, but
                ; it isn't a pair so exit now with whatever we have so far
                [(- element-count pair-size)
                 (- paired-element-count actual-pair-size)]
                (let [element-count (inc element-count)
                      paired-element-count (if comment-or-newline?
                                             paired-element-count
                                             (inc paired-element-count))
                      pair-size (if (and constant-required?
                                         (not comment-or-newline?))
                                  ; must be a constant, so start count over
                                  0
                                  (inc pair-size))
                      actual-pair-size (if (and constant-required?
                                                (not comment-or-newline?))
                                         ; start count of actual pairs over as
                                         ; well
                                         0
                                         (if comment-or-newline?
                                           ; we are only counting actual pairs
                                           ; here
                                           actual-pair-size
                                           (inc actual-pair-size)))]
                  ; Are we finished?
                  (if (zero? idx)
                    ; Yes, remove potential elements of this pair, since we
                    ; haven't
                    ; seen the end of it, and return
                    [(- element-count pair-size)
                     (- paired-element-count actual-pair-size)]
                    ; Not yet finished
                    (recur (dec idx)
                           element-count
                           paired-element-count
                           (if comment-or-newline?
                             constant-required?
                             (not constant-required?))
                           pair-size
                           actual-pair-size))))))))))

(defn count-constant-pairs
  "Given a seq of zlocs, work backwards from the end, and see how
  many elements are pairs of constants (using zconstant? or the
  supplied constant-pair-fn).  So that (... :a (stuff) :b (bother))
  returns 4, since both :a and :b are zconstant? true. This is made
  more difficult by having to skip comments along the way as part
  of the pair check, but keep track of the ones we skip so the count
  is right in the end.  We don't expect any spaces in this but
  newlines must be handled, because this seq should have been
  produced by zmap or its equivalent.  Returns two things:
  [paired-item-count actual-paired-items], where paired-item-count
  is the number of things from the end of the seq you have to trim
  off to get the constant pairs included, and the actual-paired-items
  is the count of the items to be checked against the constant-pair-min
  (which is exclusive of comments and newlines)."
  [options constant-pair-fn zloc-seq]
  (loop [zloc-seq-rev (reverse zloc-seq)
         element-count 0
         paired-element-count 0
         ; since it is reversed, we need a constant every second element
         constant-required? nil
         pair-size 0
         actual-pair-size 0]
    (let [element (first zloc-seq-rev)]
      #_(prn "count-constant-pairs: element-count:" element-count
             "paired-element-count:" paired-element-count
             "constant-required:" constant-required?
             "pair-size:" pair-size
             "actual-pair-size:" actual-pair-size
             "element:" (zstring element))
      (if (empty? zloc-seq-rev)
        ; remove potential elements of this pair, since we haven't
        ; seen the end of it, and return
        [(- element-count pair-size) (- paired-element-count actual-pair-size)]
        (let [comment-or-newline? (zcomment-or-newline? element)]
          #_(prn "count-constant-pair:" (get-sexpr options element)
                 "constant-pair-fn:" constant-pair-fn)
          (if (and (not comment-or-newline?)
                   constant-required?
                   (if constant-pair-fn
                     ; If we can't call sexpr on it, it isn't a constant
                     (not (when (zsexpr? element)
                            (constant-pair-fn (get-sexpr options element))))
                     (not (zconstant? element))))
            ; we counted the right-hand and any comments of this pair, but it
            ; isn't a pair so exit now with whatever we have so far
            [(- element-count pair-size)
             (- paired-element-count actual-pair-size)]
            (recur (next zloc-seq-rev)
                   (inc element-count)
                   (if comment-or-newline?
                     paired-element-count
                     (inc paired-element-count))
                   (if comment-or-newline?
                     constant-required?
                     (not constant-required?))
                   (if (and constant-required? (not comment-or-newline?))
                     ; must be a constant, so start count over
                     0
                     (inc pair-size))
                   (if (and constant-required? (not comment-or-newline?))
                     ; start count of actual pairs over as well
                     0
                     (if comment-or-newline?
                       ; we are only counting actual pairs here
                       actual-pair-size
                       (inc actual-pair-size))))))))))

(defn constant-pair
  "Argument is a zloc-seq.  Output is a [pair-seq non-paired-item-count],
  if any.  If there are no pair-seqs, pair-seq must be nil, not an
  empty seq.  This will largely ignore newlines and comments."
  [caller
   {{:keys [constant-pair? constant-pair-fn constant-pair-min]} caller,
    :as options} zloc-seq]
  #_(prn "constant-pair:" caller constant-pair-fn)
  (if constant-pair?
    (let [[paired-item-count actual-paired-items]
            (count-constant-pairs options constant-pair-fn zloc-seq)
          non-paired-item-count (- (count zloc-seq) paired-item-count)
          _ (dbg options
                 "constant-pair: non-paired-items:" non-paired-item-count
                 "paired-item-count:" paired-item-count
                 "actual-paired-items:" actual-paired-items)
          pair-seq (when (>= actual-paired-items constant-pair-min)
                     (drop non-paired-item-count zloc-seq))]
      [pair-seq non-paired-item-count])
    [nil (count zloc-seq)]))

;;
;; # Take into account constant pairs
;;

(declare interpose-either-nl-hf)

(defn zcomment-or-newline?
  "If this zloc is a comment or a newline, return true."
  [zloc]
  (or (zcomment? zloc) (znewline? zloc)))

(defn ensure-start-w-nl
  "Given a style-vec, ensure it starts with a newline.  If it doesn't,
  then put one in.  We could take the whole newline, but the indent is
  really the only unique thing."
  [ind style-vec]
  #_(def eswn style-vec)
  #_(prn "ensure-start-w-nl:" style-vec)
  (let [element-type (nth (first style-vec) 2)]
    #_(prn "ensure-start-w-nl:" element-type)
    (if (or (= element-type :newline) (= element-type :indent))
      style-vec
      ; Don't need prepend-nl, since we wouldn't be doing this if there
      ; was a newline on the front of style-ec
      (concat-no-nil [[(str "\n" (blanks ind)) :none :indent 6]] style-vec))))

(defn ensure-end-w-nl
  "Given a style-vec, ensure it ends with a newline.  If it doesn't,
  then put one in."
  [ind style-vec]
  #_(def eewn style-vec)
  #_(prn "ensure-end-w-nl:" style-vec)
  (let [element-type (nth (last style-vec) 2)]
    #_(prn "ensure-end-w-nl:" element-type)
    (if (or (= element-type :newline) (= element-type :indent))
      style-vec
      (concat-no-nil style-vec [[(str "\n" (blanks ind)) :none :indent 7]]))))


; This version does hang first, and if it passes use-hang?, it
; doesn't bother to do flow.

(defn fzprint-hang-remaining
  "zloc-seq is a seq of zlocs of a collection.  We already know
  that the given zloc won't fit on the current line. [Besides, we
  ensure that if there are two things remaining anyway. ???] So
  now, try hanging and see if that is better than flow.  Unless
  :hang? is nil, in which case we will just flow.  hindent is
  hang-indent, and findent is flow-indent. This should never be
  called with :one-line because this is only called from fzprint-list*
  after the one-line processing is done. If the hindent equals the
  flow indent, then just do flow.  Do only zloc-count non-whitespace
  elements of zloc-seq if it exists."
  ([caller
    {:keys [dbg? width],
     {:keys [hang? constant-pair? constant-pair-min hang-avoid hang-expand
             hang-diff nl-separator? respect-nl?]}
       caller,
     :as options} hindent findent zloc-seq fn-style zloc-count]
   (when (:dbg-hang options)
     (println (dots (:pdepth options)) "hr:" (zstring (first zloc-seq))))
   (dbg-pr options
           "fzprint-hang-remaining first:" (zstring (first zloc-seq))
           "hindent:" hindent
           "findent:" findent
           "caller:" caller
           "nl-separator?:" nl-separator?
           "(count zloc-seq):" (count zloc-seq))
   ; (in-hang options) slows things down here, for some reason
   (let [seq-right zloc-seq
         seq-right (if zloc-count (take zloc-count seq-right) seq-right)]
     (if (empty? seq-right)
       :noseq
       (let [[pair-seq non-paired-item-count]
               (constant-pair caller options seq-right)
             _ (dbg options
                    "fzprint-hang-remaining count pair-seq:"
                    (count pair-seq))
             #_(dbg options
                    "fzprint-hang-remaining: *=*=*=*=*=*" (zstring (first
                                                                     zloc-seq))
                    "hindent:" hindent
                    "findent:" findent
                    "caller:" caller
                    "hang?" hang?
                    "hang-diff" hang-diff)
             ; Now determine if there is any point in doing a hang, because
             ; if the flow is beyond the expand limit, there is really no
             ; chance that the hang is not beyond the expand limit.
             ; This is what good-enough? does:
             ;  (<= (/ (dec p-lines) p-count) hang-expand)
             ;  Also need to account for the indent diffs.
             ; Would be nice to move this into a common routine, since this
             ; duplicates logic in good-enough?
             ;
             ; Yes, and this caused a problem when I put in the
             ; hang-if-equal-flow? option in good-enough, so that now
             ; we can't cancel the hang even though we are beyond the
             ; hang-expand
             ; because the hang might be the same as the flow, and in that case
             ; we don't really care how long the hang-expand is. We could make
             ; this a feature, by having a large-ish hang-expand and having it
             ; override hang-if-equal-flow.  If we do that, we have to reorder
             ; the checks in good-enough to put the hang-expand check first.
             ; I can't see any great reason for doing a flow if the hang and
             ; flow are equal, though, so we won't do that now.  And this
             ; code comes out.
             ;
             #_#_[flow flow-lines] (zat options flow) ; PT
             _ (dbg options
                    "fzprint-hang-remaining: first hang?" hang?
                    "hang-avoid" hang-avoid
                    "findent:" findent
                    "hindent:" hindent
                    "(count seq-right):" (count seq-right)
                    "thing:" (when hang-avoid (* (- width hindent) hang-avoid)))
             hang? (and
                     hang?
                     ; This is a key for "don't hang no matter what", it isn't
                     ; about making it prettier. People call this routine with
                     ; these values equal to ensure that it always flows.
                     (not= hindent findent)
                     ; This is not the original, below.
                     ; If we are doing respect-nl?, then the count of seq-right
                     ; is going to be a lot more, even if it doesn't end up
                     ; looking different than before.  So, perhaps we should
                     ; adjust hang-avoid here?  Perhaps double it or something?
                     (or (not hang-avoid)
                         (< (count seq-right) (* (- width hindent) hang-avoid)))
                     ; If the first thing in the flow is a comment, maybe we
                     ; shouldn't be hanging anything?
                     #_(not= (nth (first flow) 2) :comment-inline) ; PT
                     ;flow-lines
                     ;;TODO make this uneval!!!
                     #_(or (<= (- hindent findent) hang-diff)
                           (<= (/ (dec (first flow-lines)) (count seq-right))
                               hang-expand)))
             _ (dbg options "fzprint-hang-remaining: second hang?" hang?)
             hanging
               (#?@(:bb [do]
                    :clj [zfuture options]
                    :cljs [do])
                (let [hang-result
                        (when hang?
                          (if-not pair-seq
                            ; There are no paired elements
                            (fzprint-flow-seq caller
                                              (in-hang options)
                                              hindent
                                              seq-right
                                              :force-nl
                                              nil ;nl-first?
                            )
                            (if (not (zero? non-paired-item-count))
                              (concat-no-nil
                                ; The elements that are not paired
                                (dbg-form options
                                          "fzprint-hang-remaining: mapv:"
                                          (ensure-end-w-nl
                                            hindent
                                            (fzprint-flow-seq
                                              caller
                                              (not-rightmost (in-hang options))
                                              hindent
                                              (take non-paired-item-count
                                                    seq-right)
                                              :force-nl
                                              nil ;nl-first?
                                            )))
                                ; The elements that are paired
                                (dbg-form
                                  options
                                  "fzprint-hang-remaining: fzprint-hang:"
                                  (fzprint-pairs (in-hang options)
                                                 hindent
                                                 pair-seq)))
                              ; All elements are paired
                              (fzprint-pairs (in-hang options)
                                             hindent
                                             pair-seq))))]
                  [hang-result (style-lines options hindent hang-result)]))
             ; We used to calculate hang-count by doing the hang an
             ; then  counting the output.  But ultimately this is
             ; simple a series of map calls to the elements of
             ; seq-right, so we go right to the source for this
             ; number now.  That let's us move the interpose calls
             ; above this point.
             [hanging [hanging-line-count :as hanging-lines]] (zat options
                                                                   hanging)
             hang-count (count seq-right)
             flow? (not (use-hang? caller
                                   options
                                   hindent
                                   hang-count
                                   hanging-line-count))
             #_(inc-pass-count)
             flow
               (when flow?
                 (#?@(:bb [do]
                      :clj [zfuture options]
                      :cljs [do])
                  (let [flow-result
                          (if-not pair-seq
                            ; We don't have any constant pairs
                            (fzprint-flow-seq caller
                                              options
                                              findent
                                              seq-right
                                              :force-nl
                                              :nl-first)
                            (if (not (zero? non-paired-item-count))
                              ; We have constant pairs, ; but they follow
                              ; some stuff that isn't paired.
                              ; Do the elements that are not pairs
                              (concat-no-nil
                                (ensure-end-w-nl findent
                                                 (fzprint-flow-seq
                                                   caller
                                                   (not-rightmost options)
                                                   findent
                                                   (take non-paired-item-count
                                                         seq-right)
                                                   :force-nl
                                                   :nl-first))
                                ; The elements that are constant pairs
                                (fzprint-pairs options findent pair-seq))
                              ; This code path is where we have all
                              ; constant
                              ; pairs.
                              (fzprint-pairs options findent pair-seq)))]
                    ; Skip the first line when doing the calcuation so that
                    ; good-enough doesn't change the layout from the original
                    [flow-result
                     (style-lines
                       options
                       findent
                       ; Issue #173 -- the following code caused code to
                       ; disappear, because if there was just one thing
                       ; in flow-result, then it would be empty and
                       ; style-lines would return nil, causing neither
                       ; hang nor flow to be used.
                       ;
                       ; (if (not pair-seq)
                       ;   (next flow-result)
                       ;   flow-result)
                       ;
                       ; Now we do a similar thing -- as long as flow-result
                       ; has more than one thing, below when we call
                       ; good-enough.
                       flow-result)])))
             [flow flow-lines] (when flow (zat options flow)) ; PT
             _ (log-lines options
                          "fzprint-hang-remaining: hanging:"
                          hindent
                          hanging)
             _ (dbg options
                    "fzprint-hang-remaining: hanging-lines:" hanging-lines
                    "hang-count:" hang-count)]
         (dbg options "fzprint-hang-remaining: flow-lines:" flow-lines)
         (when dbg?
           (if (zero? hang-count)
             (println "hang-count = 0:" (str (map zstring zloc-seq)))))
         (log-lines options "fzprint-hang-remaining: flow" findent flow)
         ; If we did hang and not flow, then we better use it.
         (if (and hanging-lines (not flow-lines))
           (if (first-nl? hanging)
             hanging
             (concat-no-nil [[" " :none :whitespace 10]] hanging))
           (when flow-lines
             (if
               ; Only call good-enough if we have both hanging-lines and
               ; flow-lines!
               (and hanging-lines
                    (good-enough?
                      caller
                      options
                      fn-style
                      hang-count
                      (- hindent findent)
                      hanging-lines
                      ; If we have more than one line in the flow
                      ; and we didn't have any constant pairs,
                      ; then decrease the line count for the flow.
                      ; This seems to be necessary based on the results,
                      ; but it can't be done in good-enough in all cases,
                      ; because it breaks lots of stuff.  This was
                      ; previously done above, in the call to style-lines,
                      ; where we just skipped the first line.  That
                      ; seems like a bad idea, so we now just create
                      ; a new flow-lines to cover this situation.
                      ; This was provoked by Issue #173 where we lost
                      ; code when there was only one thing in flow-result,
                      ; and we skipped that thing, causing style-lines
                      ; to return nil and the whole thing disappeared.
                      (if (and (not pair-seq) (> (first flow-lines) 1))
                        [(dec (first flow-lines)) (second flow-lines)
                         (nth flow-lines 2)]
                        flow-lines)))
               ; If hanging starts with a newline, don't put a blank at the
               ; end of the previous line.
               (if (first-nl? hanging)
                 hanging
                 (concat-no-nil [[" " :none :whitespace 10]] hanging))
               (ensure-start-w-nl findent flow))))))))
  ([caller options hindent findent zloc fn-style]
   (fzprint-hang-remaining caller options hindent findent zloc fn-style nil)))


; This version overlaps hang and flow, which can run into trouble when
; you get very deep -- it runs out of threads.  But we'll keep it here
; just for illustrative purposes, since when it works, it is pretty fast.
#_(defn fzprint-hang-remaining-overlap
    "zloc-seq is a seq of zlocs of a collection.  We already know
  that the given zloc won't fit on the current line. [Besides, we
  ensure that if there are two things remaining anyway. ???] So
  now, try hanging and see if that is better than flow.  Unless
  :hang? is nil, in which case we will just flow.  hindent is
  hang-indent, and findent is flow-indent. This should never be
  called with :one-line because this is only called from fzprint-list*
  after the one-line processing is done. If the hindent equals the
  flow indent, then just do flow.  Do only zloc-count non-whitespace
  elements of zloc-seq if it exists."
    ([caller
      {:keys [dbg? width],
       {:keys [hang? constant-pair? constant-pair-min hang-avoid hang-expand
               hang-diff nl-separator? respect-nl?]}
         caller,
       :as options} hindent findent zloc-seq fn-style zloc-count]
     (when (:dbg-hang options)
       (println (dots (:pdepth options)) "hr:" (zstring (first zloc-seq))))
     (dbg-pr options
             "fzprint-hang-remaining first:" (zstring (first zloc-seq))
             "hindent:" hindent
             "findent:" findent
             "caller:" caller
             "nl-separator?:" nl-separator?
             "(count zloc-seq):" (count zloc-seq))
     ; (in-hang options) slows things down here, for some reason
     (let [seq-right zloc-seq
           seq-right (if zloc-count (take zloc-count seq-right) seq-right)
           [pair-seq non-paired-item-count]
             (constant-pair caller options seq-right)
           _ (dbg options
                  "fzprint-hang-remaining count pair-seq:"
                  (count pair-seq))
           flow
             (#?@(:bb [do]
                  :clj [zfuture options]
                  :cljs [do])
              (let [flow-result (if-not pair-seq
                                  ; We don't have any constant pairs
                                  (fzprint-flow-seq options
                                                    findent
                                                    seq-right
                                                    :force-nl
                                                    :nl-first)
                                  (if (not (zero? non-paired-item-count))
                                    ; We have constant pairs, ; but they follow
                                    ; some stuff that isn't paired.
                                    ; Do the elements that are not pairs
                                    (concat-no-nil
                                      (ensure-end-w-nl
                                        findent
                                        (fzprint-flow-seq
                                          (not-rightmost options)
                                          findent
                                          (take non-paired-item-count seq-right)
                                          :force-nl
                                          :nl-first))
                                      ; The elements that are constant pairs
                                      (fzprint-pairs options findent pair-seq))
                                    ; This code path is where we have all
                                    ; constant
                                    ; pairs.
                                    (fzprint-pairs options findent pair-seq)))]
                ; Skip the first line when doing the calcuation so that
                ; good-enough doesn't change the layout from the original
                [flow-result
                 (style-lines
                   options
                   findent
                   (if (not pair-seq) (next flow-result) flow-result))]))
           #_(dbg options
                  "fzprint-hang-remaining: *=*=*=*=*=*" (zstring (first
                                                                   zloc-seq))
                  "hindent:" hindent
                  "findent:" findent
                  "caller:" caller
                  "hang?" hang?
                  "hang-diff" hang-diff)
           ; Now determine if there is any point in doing a hang, because
           ; if the flow is beyond the expand limit, there is really no
           ; chance that the hang is not beyond the expand limit.
           ; This is what good-enough? does:
           ;  (<= (/ (dec p-lines) p-count) hang-expand)
           ;  Also need to account for the indent diffs.
           ; Would be nice to move this into a common routine, since this
           ; duplicates logic in good-enough?
           ;
           ; Yes, and this caused a problem when I put in the
           ; hang-if-equal-flow? option in good-enough, so that now
           ; we can't cancel the hang even though we are beyond the hang-expand
           ; because the hang might be the same as the flow, and in that case
           ; we don't really care how long the hang-expand is. We could make
           ; this a feature, by having a large-ish hang-expand and having it
           ; override hang-if-equal-flow.  If we do that, we have to reorder
           ; the checks in good-enough to put the hang-expand check first.
           ; I can't see any great reason for doing a flow if the hang and
           ; flow are equal, though, so we won't do that now.  And this
           ; code comes out.
           ;
           #_#_[flow flow-lines] (zat options flow) ; PT
           _ (dbg options
                  "fzprint-hang-remaining: first hang?" hang?
                  "hang-avoid" hang-avoid
                  "findent:" findent
                  "hindent:" hindent
                  "(count seq-right):" (count seq-right)
                  "thing:" (when hang-avoid (* (- width hindent) hang-avoid)))
           hang? (and
                   hang?
                   ; This is a key for "don't hang no matter what", it isn't
                   ; about making it prettier. People call this routine with
                   ; these values equal to ensure that it always flows.
                   (not= hindent findent)
                   ; This is not the original, below.
                   ; If we are doing respect-nl?, then the count of seq-right
                   ; is going to be a lot more, even if it doesn't end up
                   ; looking different than before.  So, perhaps we should
                   ; adjust hang-avoid here?  Perhaps double it or something?
                   (or (not hang-avoid)
                       (< (count seq-right) (* (- width hindent) hang-avoid)))
                   ; If the first thing in the flow is a comment, maybe we
                   ; shouldn't be hanging anything?
                   #_(not= (nth (first flow) 2) :comment-inline) ; PT
                   ;flow-lines
                   ;;TODO make this uneval!!!
                   #_(or (<= (- hindent findent) hang-diff)
                         (<= (/ (dec (first flow-lines)) (count seq-right))
                             hang-expand)))
           _ (dbg options "fzprint-hang-remaining: second hang?" hang?)
           hanging
             (#?@(:bb [do]
                  :clj [zfuture options]
                  :cljs [do])
              (let [hang-result
                      (when hang?
                        (if-not pair-seq
                          ; There are no paired elements
                          (fzprint-flow-seq (in-hang options)
                                            hindent
                                            seq-right
                                            :force-nl
                                            nil ;nl-first?
                          )
                          (if (not (zero? non-paired-item-count))
                            (concat-no-nil
                              ; The elements that are not paired
                              (dbg-form options
                                        "fzprint-hang-remaining: mapv:"
                                        (ensure-end-w-nl
                                          hindent
                                          (fzprint-flow-seq
                                            (not-rightmost (in-hang options))
                                            hindent
                                            (take non-paired-item-count
                                                  seq-right)
                                            :force-nl
                                            nil ;nl-first?
                                          )))
                              ; The elements that are paired
                              (dbg-form options
                                        "fzprint-hang-remaining: fzprint-hang:"
                                        (fzprint-pairs (in-hang options)
                                                       hindent
                                                       pair-seq)))
                            ; All elements are paired
                            (fzprint-pairs (in-hang options)
                                           hindent
                                           pair-seq))))]
                [hang-result (style-lines options hindent hang-result)]))
           ; We used to calculate hang-count by doing the hang an then counting
           ; the output.  But ultimately this is simple a series of map calls
           ; to the elements of seq-right, so we go right to the source for this
           ; number now.  That let's us move the interpose calls above this
           ; point.
           [hanging hanging-lines] (zat options hanging)
           [flow flow-lines] (zat options flow) ; PT
           hang-count (count seq-right)
           _ (log-lines options
                        "fzprint-hang-remaining: hanging:"
                        hindent
                        hanging)
           _ (dbg options
                  "fzprint-hang-remaining: hanging-lines:" hanging-lines
                  "hang-count:" hang-count)]
       (dbg options "fzprint-hang-remaining: flow-lines:" flow-lines)
       (when dbg?
         (if (zero? hang-count)
           (println "hang-count = 0:" (str (map zstring zloc-seq)))))
       (log-lines options "fzprint-hang-remaining: flow" findent flow)
       (when flow-lines
         (if (good-enough? caller
                           options
                           fn-style
                           hang-count
                           (- hindent findent)
                           hanging-lines
                           flow-lines)
           ; If hanging starts with a newline, don't put a blank at the
           ; end of the previous line.
           (if (first-nl? hanging)
             hanging
             (concat-no-nil [[" " :none :whitespace 10]] hanging))
           (ensure-start-w-nl findent flow)))))
    ([caller options hindent findent zloc fn-style]
     (fzprint-hang-remaining caller options hindent findent zloc fn-style nil)))

;;
;; # Find out and print what comes before the next element
;;

(defn fzprint-get-zloc-seq
  "Get the zloc seq, with or without newlines, as indicated by the options."
  [caller options zloc]
  (let [caller-options (caller options)
        zloc-seq (cond (:respect-nl? caller-options) (zmap-w-nl identity zloc)
                       (:respect-bl? caller-options) (zmap-w-bl identity zloc)
                       :else (zmap identity zloc))]
    (dbg-pr options "fzprint-get-zloc-seq:" (map zstring zloc-seq))
    zloc-seq))

(declare drop-thru-first-non-whitespace)

(defn extract-meta
  "Given a zloc, if it is a zmeta?, then add all of the things at
  the beginning (which are :meta) to the ouput and then the last
  thing (which should be :token) to the output."
  [caller options out-vec element]
  (if (zmeta? element)
    (let [remaining (drop-thru-first-non-whitespace
                      (fzprint-get-zloc-seq caller options element))]
      #_(dbg-pr options "extract-meta remaining:" remaining)
      (if (= (ztag (first remaining)) :meta)
        ; do it again
        (extract-meta caller options (conj out-vec element) (first remaining))
        (apply conj (conj out-vec element) remaining)))
    (conj out-vec element)))

(defn fzprint-split-meta-in-seq
  "Given the results from fzprint-get-zloc-seq, if any of the elements are
  zmeta?, then if :meta :split? true, make the second and succeeding
  elements of the meta an independent element in the outer seq.  
  Returns a zloc-seq."
  [caller options zloc-seq]
  (let [result (if (:split? (:meta options))
                 (reduce (partial extract-meta caller options) [] zloc-seq)
                 zloc-seq)]
    (dbg-pr options
            "fzprint-split-meta-in-seq: split?" (:split? (:meta options))
            "result:" (map zstring result)
            "tags:" (map ztag result))
    result))

(defn newline-or-comment?
  "Given an zloc, is it a newline or a comment?"
  [zloc]
  (when zloc
    (let [zloc-tag (ztag zloc)]
      (or (= zloc-tag :newline) (= zloc-tag :comment)))))

(defn remove-last-newline
  "Given a seq of style-vecs, look at the last one, and if it is a
  :newline, then remove it.  But the last one might be a single
  one, in which case we will remove the whole thing, and it might be
  the last one in a sequence, in which case we will remove just that
  one.  If there is nothing left, return [[[\"\" :none :none]]]."
  [ssv]
  #_(prn "remove-last-newline:" ssv)
  (let [last-style-vec (last ssv)]
    (if-not (= (nth (last last-style-vec) 2) :newline)
      ssv
      (let [last-len (count last-style-vec)
            total-len (count ssv)
            remove-one
              (concat (butlast ssv)
                      (if (= last-len 1) [] (vector (butlast last-style-vec))))]
        (if (empty? remove-one) [[["" :none :none]]] remove-one)))))

(defn remove-one-newline
  "Given a single style-vec, look at the last element, and if it is a
  :newline, remove it.  If there is nothing left, return :noseq"
  [style-vec]
  #_(prn "remove-one-newline:" style-vec)
  (let [last-style-vec (last style-vec)]
    (if-not (= (nth last-style-vec 2) :newline)
      style-vec
      (let [remaining (butlast style-vec)]
        (if (empty? remaining) :noseq remaining)))))

(defn add-newline-to-comment
  "Given [[[\";stuff\" :none :comment]]] or 
  [[[\";bother\" :none :comment-inline 1]]] add [\"\n\" :none :newline]
  to the inside of it."
  [indent fzprint*-return]
  (let [the-type (nth (first fzprint*-return) 2)]
    (if (or (= the-type :comment) (= the-type :comment-inline))
      (concat fzprint*-return [[(str "\n" (blanks indent)) :none :newline 1]])
      fzprint*-return)))

(defn gather-up-to-next-zloc
  "Given a zloc-seq, gather newlines and comments up to the next
  zloc into a seq.  Returns [seq next-zloc next-count]."
  [zloc-seq]
  (loop [nloc-seq zloc-seq
         out []
         next-count 0]
    (if (not (newline-or-comment? (first nloc-seq)))
      [out (first nloc-seq) next-count]
      (recur (next nloc-seq) (conj out (first nloc-seq)) (inc next-count)))))

(defn fzprint-up-to-next-zloc
  "Using the information returned from fzprint-up-to-first-zloc or
  fzprint-up-to-next-zloc, find the next zloc and return 
  [pre-next-style-vec next-zloc next-count zloc-seq]"
  [caller options ind [_ _ current-count zloc-seq :as next-data]]
  (let [starting-count (inc current-count)
        nloc-seq (nthnext zloc-seq starting-count)]
    (dbg-pr options
            "fzprint-up-to-next-zloc: starting-count:" starting-count
            "zloc-seq:" (map zstring zloc-seq))
    (if-not (= (:ztype options) :zipper)
      [:noseq (first nloc-seq) starting-count zloc-seq]
      (let [[pre-next-zloc-seq next-zloc next-count] (gather-up-to-next-zloc
                                                       nloc-seq)
            next-count (+ starting-count next-count)]
        (dbg-pr options
                "fzprint-up-to-next-zloc: next-count:" next-count
                "pre-next-zloc-seq:" (map zstring pre-next-zloc-seq))
        (if (empty? pre-next-zloc-seq)
          ; The normal case -- nothing before the first interesting zloc
          [:noseq next-zloc next-count zloc-seq]
          ; There were newlines or comments (or both) before the first
          ; interesting zloc
          (let [coll-print
                  (fzprint-flow-seq caller options ind pre-next-zloc-seq)
                ; we are set up for fzprint-seq, but fzprint-flow-seq does
                ; a full-on style-vec, so turn it back into fzprint-seq style
                ; output
                coll-print (mapv vector coll-print)
                ; We aren't trying to interpose anything here, we are just
                ; trying to print the stuff we have in a way that will work.
                ; Remove the last newline if we are not the first thing
                coll-print (if (not= starting-count 0)
                             (remove-last-newline coll-print)
                             coll-print)
                coll-out (apply concat-no-nil coll-print)
                ; If we are down inside a list and  the first thing is a
                ; comment, ensure we start with a newline.  If it is an
                ; inline comment, then it will get fixed later.
                coll-out (if (and (not= starting-count 0)
                                  (let [first-type (nth (first coll-out) 2)]
                                    (or (= first-type :comment)
                                        (= first-type :comment-inline))))
                           (ensure-start-w-nl ind coll-out)
                           coll-out)
                ; Ensure that we end with a newline if we are
                ; the first thing
                coll-out (if (not= starting-count 0)
                           coll-out
                           (ensure-end-w-nl ind coll-out))
                ; Make sure it ends with a newline, since all comments and
                ; newlines better end with a newline.  But how could it
                ; not end with a newline?  We only put comments and newlines
                ; in here, and added newlines to comments.  So we will assume
                ; that it ends with a newline.
               ]
            [coll-out next-zloc next-count zloc-seq]))))))

(defn fzprint-up-to-first-zloc
  "Returns [pre-first-style-vec first-zloc first-count zloc-seq], where
  pre-first-style-vec will be :noseq if there isn't anything, and first-count
  is what you give to nthnext to get to the first-zloc in zloc-seq."
  [caller options ind zloc]
  (dbg-pr options "fzprint-up-to-first-zloc")
  (if-not (= (:ztype options) :zipper)
    [:noseq (first zloc) 0 zloc]
    (let [zloc-seq (fzprint-get-zloc-seq caller options zloc)
          zloc-seq (fzprint-split-meta-in-seq caller options zloc-seq)]
      ; Start at -1 so that when fzprint-up-to-next-zloc skips, it goes
      ; to zero.
      (fzprint-up-to-next-zloc caller options ind [nil nil -1 zloc-seq]))))

(defn get-zloc-seq-right
  "Using return from fzprint-up-to-first-zloc or fzprint-up-to-next-zloc,
  [pre-next-style-vec next-zloc next-count zloc-seq], return a zloc-seq
  pointer to just beyond the specific zloc which was found by the
  fzprint-up-to-first or fzprint-up-to-next call.  You don't give this
  a number, you give it the data structure from the thing that you found."
  [[_ _ next-count zloc-seq :as input-data]]
  (if (>= next-count (count zloc-seq))
    (throw (#?(:clj Exception.
               :cljs js/Error.)
            (str "get-zloc-seq-right input data inconsistent:" input-data)))
    (let [zloc-seq (nthnext zloc-seq (inc next-count))]
      #_(prn "get-zloc-seq-right: next-count:" next-count
             "zloc-seq:" (map zstring zloc-seq))
      zloc-seq)))


;;
;; # Indent-only support
;;

(defn at-newline?
  "Is this a newline or equivalent?  Comments and newlines are both
  newlines for the purposed of this routine."
  [zloc]
  (let [this-tag (ztag zloc)] (or (= this-tag :comment) (= this-tag :newline))))

(defn next-newline
  "Given a zloc that is down inside of a collection, presumably
  a list, return a vector containing the number of printing elements
  we had to traverse to get to it as well as the newline."
  [zloc]
  (loop [nloc zloc
         index 0]
    #_(prn "next-newline:" (zstring nloc) "tag:" (tag nloc))
    (let [next-right (right* nloc)]
      (if next-right
        (if (at-newline? nloc)
          [index nloc]
          (recur (right* nloc)
                 (if-not (zprint.zutil/whitespace? nloc) (inc index) index)))
        [index nloc]))))


(defn next-actual
  "Return the next actual element, ignoring comments and whitespace
  and everything else but real elements."
  [zloc]
  #_(prn "next-actual: zloc" (zstring zloc))
  (loop [nloc zloc]
    (if-not nloc
      nloc
      (let [next-nloc (zprint.zutil/zrightnws nloc)
            next-tag (tag next-nloc)]
        #_(prn "nloc:" nloc
               "next-actual: next-nloc:" (zstring next-nloc)
               "next-tag:" next-tag)
        (if-not (or (= next-tag :newline) (= next-tag :comment))
          next-nloc
          (recur next-nloc))))))

(defn first-actual
  "Return the first actual element, ignoring comments and whitespace
  and everything else but real elements."
  [zloc]
  (if (at-newline? zloc) (next-actual zloc) zloc))

(defn hang-zloc?
  "Should we hang this zloc, or flow it.  We assume that we are at
  the start of the collection (though this could be generalized to
  deal with other starting locations easily enough).  Return true
  if we should hang it based just on the information in the zloc
  itself.  The criteria are: If there is a newline after the second
  thing in the zloc, and the amount of space prior to the third thing
  is the same as the amount of space prior to the second thing, then
  the incoming zloc was hung and we should do the same. Of course, it
  would also only be hung if the second thing was on the same line as
  the first thing."
  [zloc]
  #_(prn "hang-zloc: zloc:" zloc "at-newline?:" (at-newline? zloc))
  (let [zloc (first-actual zloc) ; skip comments/newlines at start
        [count-prior-to-newline newline] (next-newline zloc)]
    #_(prn "at-newline?:" (at-newline? zloc)
           "hang-zloc?: count-prior...:" count-prior-to-newline
           "zloc:" (zstring zloc))
    ; Are the first two real things on the same line?
    (if (< count-prior-to-newline 2)
      ; no -- then it can't be a hang
      false
      (let [second-element (zprint.zutil/zrightnws
                             (if (zprint.zutil/whitespace? zloc)
                               (zprint.zutil/zrightnws zloc)
                               zloc))
            second-indent (length-before second-element)
            third-element (next-actual second-element)
            third-indent (length-before third-element)]
        #_(prn "hang-zloc?: second-element:" (zstring second-element)
               "second-indent:" second-indent
               "third-element:" (zstring third-element)
               "third-tag:" (tag third-element)
               "third-indent:" third-indent)
        (and second-element third-element (= second-indent third-indent))))))

(defn indent-shift
  "Take a style-vec that was once output from indent-zmap, and fix
  up all of the :indent elements in it by adding (- actual-ind ind)
  to them.  If we find a multiple thing in here, call indent-shift
  recursively with the ind and cur-ind that is approprite.  All of
  the actual indents are correct already -- all we are doing is
  setting up their base.  There is no attempt to determine if we
  are exceeding any configured width."
  [caller options ind actual-ind svec]
  (let [shift-ind actual-ind]
    (dbg-pr options
            "indent-shift: ind:" ind
            "actual-ind:" actual-ind
            "shift-ind:" shift-ind
            "svec:" svec)
    (loop [cur-seq svec
           cur-ind actual-ind
           out []]
      (if-not cur-seq
        out
        (let [this-seq (first cur-seq)
              new-seq (if (vector? (first this-seq))
                        ; is this ind correct?
                        (indent-shift caller options ind cur-ind this-seq)
                        (let [[s color type] this-seq
                              next-seq (first (next cur-seq))
                              this-shift (if (and next-seq
                                                  (not (vector? (first
                                                                  next-seq)))
                                                  (= (nth next-seq 2) :indent))
                                           0
                                           shift-ind)]
                          (cond (= type :indent) [(str s (blanks this-shift))
                                                  color type 42]
                                (= type :right) [s color type shift-ind]
                                :else this-seq)))
              _ (dbg-pr options
                        "indent-shift: cur-ind:" cur-ind
                        "this-seq:" this-seq
                        "new-seq:" new-seq)
              ; Shouldn't this be (inc cur-ind)?
              [linecnt max-width lines] (style-lines options cur-ind [new-seq])
              ; Figure out where we are
              last-width (last lines)]
          (dbg-pr options
                  "indent-shift: last-width:" last-width
                  "new-seq:" new-seq)
          ; Should this be (inc last-width)?
          (recur (next cur-seq) last-width (conj out new-seq)))))))

(declare merge-fzprint-seq)

(defn indent-zmap
  "Implement :indent-only?.  This routine is the central one through
  which all :indent-only? processing flows, and replaces all of the
  detailed logic in fzprint-list*, fzprint-vec*, and fzprint-map*.
  This is called directly by fzprint-vec*, which handles both vectors
  and sets, and through fzprint-indent by fzprint-list* and
  fzprint-map*.  Thus, all of the data structures get their
  :indent-only? processing handled by ident-zmap.  coll-print is
  the output from fzprint-seq, which is a style-vec in the making
  without spacing, but with extra [] around the elements.  Everything
  is based off of ind, and we know nothing to the left of that.
  ind must be the left end of everything, not the right of l-str!
  The actual-ind is to the right of l-str.  When we get a newline,
  replace any spaces after it with our own, and that would be to
  bring it to ind + indent.  "
  ([caller
    {:keys [width rightcnt], {:keys [wrap-after-multi?]} caller, :as options}
    ind actual-ind coll-print indent first-indent-only?]
   (let [coll-print (merge-fzprint-seq coll-print)
         last-index (dec (count coll-print))
         rightcnt (fix-rightcnt rightcnt)
         actual-indent (+ ind indent)]
     (dbg-pr options
             "indent-zmap: ind:" ind
             "actual-ind:" actual-ind
             "first-indent-only?" first-indent-only?
             "indent:" indent
             "actual-indent:" actual-indent
             "coll-print:" coll-print)
     (loop [cur-seq coll-print
            cur-ind actual-ind
            index 0
            beginning? true ; beginning of line
            ; transient here slowed things down, in a similar routine
            l-str-indent? true
            out []]
       (if-not cur-seq
         out
         (let [this-seq (first cur-seq)]
           (when this-seq
             (let [multi? (> (count this-seq) 1)
                   _ (log-lines options "indent-zmap:" ind this-seq)
                   _ (dbg-pr options
                             "indent-zmap loop: cur-ind:" cur-ind
                             "multi?" multi?
                             "(count this-seq):" (count this-seq)
                             "this-seq:" this-seq
                             "out:" out)
                   this-seq
                     (if multi?
                       (indent-shift caller options actual-ind cur-ind this-seq)
                       this-seq)
                   [linecnt max-width lines]
                     (style-lines options cur-ind this-seq)
                   ; Figure out where we are
                   last-width (last lines)
                   ; How can this be right if there are multiple lines?
                   ; Because we called indent-zmap to get the indents right,
                   ; and they will be but for the first line, which style-lines
                   ; fixed because it got the cur-ind..
                   thetype (nth (last this-seq) 2)
                   ; This is the total width of the current line
                   ; relative to ind
                   len (- last-width cur-ind)
                   _ (dbg options
                          "linecnt:" linecnt
                          "last-width:" last-width
                          "len:" len
                          "type:" thetype)
                   len (max 0 len)
                   ; This isn't the only newline, actually.  Sometimes they
                   ; are comment or comment-inline.  Later, for indent-shift,
                   ; they are :indents.  Figure this out!
                   newline? (= thetype :newline)
                   comma? (= thetype :comma)
                   isempty? (empty? (first (first this-seq)))
                   comment? (or (= thetype :comment)
                                (= thetype :comment-inline))
                   ; Adjust for the rightcnt on the last element
                   ;first-comment? (and comment? (= index 0))
                   ;first-newline? (and newline? (= index 0))
                   ;l-str-indent? (or first-comment? first-newline?)
                   l-str-indent? (and l-str-indent? (or comment? newline?))
                   actual-indent (if (and (> index 0) first-indent-only?)
                                   ind
                                   (+ ind indent))
                   width (if (= index last-index) (- width rightcnt) width)
                   ; need to check size, and if one line and fits, should fit
                   ; ??? why does it fit if this is the first thing?  Because
                   ; if it isn't, things won't get better?  Seems to me like
                   ; if the first thing doesn't fit, we should return nil.
                   ;
                   ; But this is all about indent-only, not fitting.  But
                   ; we will probably care about fitting someday.
                   fit? (<= (+ cur-ind len) width)
                   ; If we don't care about fit, then don't do this!!
                   new-ind (cond newline? actual-indent
                                 :else (+ cur-ind 1 len))]
               (dbg-pr
                 options
                 "------ this-seq:" this-seq
                 "lines:" lines
                 "linecnt:" linecnt
                 "multi?" multi?
                 "thetype:" thetype
                 "newline?:" newline?
                 "comment?:" comment?
                 "comma?:" comma?
                 "l-str-indent?:" l-str-indent?
                 "first-indent-only?" first-indent-only?
                 "actual-indent:" actual-indent
                 "index:" index
                 "beginning?:" beginning?
                 "max-width:" max-width
                 "last-width:" last-width
                 "len:" len
                 "cur-ind:" cur-ind
                 "isempty?:" isempty?
                 "new-ind:" new-ind
                 "width:" width
                 "fit?" fit?)
               (recur ; [cur-seq, cur-ind, index, beginning?, out]
                 (next cur-seq)
                 new-ind
                 (inc index)
                 ; beginning can happen because we created an indent
                 ; or because a multi already had one.
                 (or (and isempty? beginning?) newline? (= thetype :indent))
                 ; l-str-indent
                 l-str-indent?
                 ; out
                 (if isempty?
                   out
                   ; TODO: concat-no-nil fails here, why?
                   (concat out
                           (cond
                             ; we don't want blanks if the next thing is a
                             ; newline
                             newline? [[(str
                                          "\n"
                                          (let [next-seq (first (next cur-seq))
                                                #_(prn "next-seq:" next-seq)
                                                newline-next?
                                                  (when next-seq
                                                    (= (nth (first next-seq) 2)
                                                       :newline))]
                                            (if newline-next?
                                              ""
                                              (blanks (if l-str-indent?
                                                        actual-ind
                                                        actual-indent))))) :none
                                        :indent 12]]
                             ; Remove next line, unnecessary
                             (zero? index) this-seq
                             :else (if (or beginning? comma?)
                                     this-seq
                                     (concat-no-nil [[" " :none :whitespace 12]]
                                                    this-seq)))))))))))))
  ([caller options ind actual-ind coll-print indent]
   (indent-zmap caller options ind actual-ind coll-print indent nil)))

(defn newline-seq?
  "Given a vector of vectors, decide if we should merge these individually
  into the top level vector."
  [newline-vec]
  (let [starts-with-nl-vec (mapv #(clojure.string/starts-with? (first %) "\n")
                             newline-vec)
        #_(println "newline-seq? starts-with-nl-vec" starts-with-nl-vec)
        true-seq (distinct starts-with-nl-vec)]
    (and (= (count true-seq) 1) (= (first true-seq) true))))

(defn merge-fzprint-seq
  "Given the output from fzprint-seq, which is a seq of the
  output of fzprint*, apply a function to each of them that has
  more than one element (since less has no meaning) and when the
  function returns true, merge the vector in as individual elements."
  [fzprint-seq-vec]
  (into []
        (reduce #(if (newline-seq? %2)
                   (into [] (concat %1 (mapv vector %2)))
                   (conj %1 %2))
          []
          fzprint-seq-vec)))

(defn fzprint-indent
  "This function assumes that :indent-only? was set for the caller
  in the options (since anything else doesn't make sense).  It takes
  a zloc and the ind, which is where we are on the line this point,
  and will process the zloc to include any newlines.  Of course we
  have to have all of the white space in the zloc too, since we
  need to ask some questions about what we are starting with at
  some point.  We don't add newlines and we let the newlines that
  are in there do their thing.  We might add newlines if we move
  beyond the right margin, but for now, we don't (and it isn't
  entirely clear how or if that would work).  This routine has to
  make decisions about the indent, that is whether to hang or flow
  the expression. It does that based on what was done in the input
  if the configuration allows."
  ([caller l-str r-str options ind zloc fn-style arg-1-indent
    first-indent-only?]
   (let [flow-indent (:indent (caller options))
         l-str-len (count l-str)
         ; Make sure that the flow-indent deals with the size of the
         ; l-str -- important for anonymous functions
         flow-indent (+ flow-indent (- l-str-len 1))
         ; If it is a map, then an indent of (count l-str) (which is 1)
         ; is all that makes sense.
         flow-indent (if (= caller :map) (count l-str) flow-indent)
         flow-indent (if (and (> flow-indent l-str-len) (= caller :list))
                       ; If we don't think this could be a fn, indent minimally
                       (if arg-1-indent flow-indent l-str-len)
                       flow-indent)
         actual-ind (+ ind l-str-len)
         ; We could enable :comma? for lists, sets, vectors someday
         zloc-seq (if (:comma? (caller options))
                    (zmap-w-nl-comma identity zloc)
                    (zmap-w-nl identity zloc))
         _ (dbg-pr options
                   "fzprint-indent: caller:" caller
                   "l-str-len:" l-str-len
                   "ind:" ind
                   "fn-style:" fn-style
                   "arg-1-indent:" arg-1-indent
                   "flow-indent:" flow-indent
                   "actual-ind:" actual-ind
                   "comma?" (:comma? (caller options))
                   "zloc" (zstring zloc)
                   "zloc-seq" (map zstring zloc-seq))
         coll-print (fzprint-seq options ind zloc-seq)
         _ (dbg-pr options "fzprint-indent: coll-print:" coll-print)
         indent-only-style (:indent-only-style (caller options))
         ; If we have the possibility of :input-hang, then try if it is
         ; configured.
         already-hung? (when (and indent-only-style
                                  (= indent-only-style :input-hang))
                         (hang-zloc? (down* zloc)))
         raw-indent (if (and arg-1-indent already-hung?)
                      (- arg-1-indent ind)
                      flow-indent)
         indent raw-indent
         coll-print-contains-nil? (contains-nil? coll-print)
         _ (dbg-pr options
                   "fzprint-indent:" (zstring zloc)
                   "ind:" ind
                   "fn-style:" fn-style
                   "indent-only-style:" indent-only-style
                   "already-hung?:" already-hung?
                   "arg-1-indent:" arg-1-indent
                   "l-str-len:" (count l-str)
                   "actual-ind:" actual-ind
                   "raw-indent:" raw-indent
                   "coll-print-contains-nil?:" coll-print-contains-nil?
                   "indent:" indent)
         coll-print (when-not coll-print-contains-nil? coll-print)]
     ; indent needs to adjust for the size of l-str-vec, since actual-ind
     ; has l-str-vec in it so that indent-zmap knows where we are on the
     ; line.  Just like fzprint-one-line needs one-line-ind, not ind.
     (let [output (indent-zmap caller
                               options
                               ind
                               actual-ind
                               coll-print
                               indent
                               first-indent-only?)]
       (dbg-pr options "fzprint-indent: output:" output)
       output)))
  ([caller l-str r-str options ind zloc fn-style arg-1-indent]
   (fzprint-indent caller
                   l-str
                   r-str
                   options
                   ind
                   zloc
                   fn-style
                   arg-1-indent
                   nil)))

(defn zfind-seq
  "Find the location, counting from zero, and counting every element 
  in the seq, of the first zthing?.  Return its index if it is found, 
  nil if not."
  [zthing? zloc-seq]
  (loop [nloc zloc-seq
         i 0]
    (when (not (nil? nloc))
      (if (zthing? (first nloc)) i (recur (next nloc) (inc i))))))

;;
;; # Utilities to modify list printing in various ways
;;

;;
;; Which fn-styles use :list {:indent n} instead of
;; :list {:indent-arg n}
;;

(def body-set
  #{:binding :arg1-> :arg2 :arg2-fn :arg2-pair :pair-fn :fn :arg1-body
    :arg1-pair-body :none-body :noarg1-body :flow-body :arg2-extend-body
    :arg1-force-nl-body :arg2-force-nl-body :guided-body :arg1-extend-body
    :force-nl-body})

;;
;; Note Well -- every key in body-map should also appear in body-set!
;;

(def body-map
  {:arg1-body :arg1,
   :arg1-pair-body :arg1-pair,
   :arg1-force-nl-body :arg1-force-nl,
   :arg2-extend-body :arg2-extend,
   :arg1-extend-body :arg1-extend,
   :none-body :none,
   :flow-body :flow,
   :noarg1-body :noarg1,
   :force-nl-body :force-nl,
   :guided-body :guided,
   :arg2-force-nl-body :arg2-force-nl})

;;
;; If the noarg1? value is set, this is the mapping for functions
;; immediately below
;; 

(def noarg1-set #{:noarg1 :arg1->})

(def noarg1-map
  {:arg1 :none,
   :arg1-pair :pair-fn,
   :arg1-extend :extend,
   :arg2 :arg1,
   :arg2-pair :arg1-pair})

(defn noarg1
  "Set noarg1 in the options if it is the right fn-type."
  [options fn-type]
  (if (noarg1-set fn-type) (assoc options :no-arg1? true) options))

; This is accessed prior to the -body being removed, so both types need to
; be in here

(def fn-style->caller
  {:arg1-pair-body :pair,
   :arg1-pair :pair,
   :arg2-pair :pair,
   :extend :extend,
   :binding :binding,
   :arg1-extend :extend,
   :arg1-extend-body :extend,
   :arg2-extend :extend,
   :arg2-extend-body :extend,
   :pair-fn :pair})

(defn find-nl-count
  "Look at a style-vec, and if it is more than one line, return n, otherwise
  return 1."
  [options ind n out style-vec]
  (let [lines (style-lines options ind style-vec)
        fit? (fzfit-one-line options lines)]
    (conj out (if fit? 1 n))))

(defn create-nl-count-vec
  "Given a zloc-seq, create an nl-count vector which has the right
  number (i.e., nl-count) of newlines between everything that is already
  multi-line, and 1 otherwise.  Return the vector."
  [options ind nl-count coll-print]
  (reduce (partial find-nl-count options ind nl-count) [] coll-print))

(defn get-respect-indent
  "Given an options map, get the respect-nl?, respect-bl? and indent-only?
  options from the caller's options, and if the caller doesn't define these,
  use the values from the backup section of the options map. Return
  [respect-nl? respect-bl? indent-only?]"
  ; Note that the routine make-caller exists, and see its use in fzprint*
  ; That is a different way to solve this problem
  ;
  ; We just evaluate the things that need to be evaluated, since this is
  ; called a *lot*!.
  [options caller backup]
  (let [caller-options (caller options)
        respect-nl? (get caller-options :respect-nl? :undef)
        respect-bl? (get caller-options :respect-bl? :undef)
        indent-only? (get caller-options :indent-only? :undef)]
    [(if (not= respect-nl? :undef) respect-nl? (:respect-nl? (backup options)))
     (if (not= respect-bl? :undef) respect-bl? (:respect-bl? (backup options)))
     (if (not= indent-only? :undef)
       indent-only?
       (:indent-only? (backup options)))]))

(defn allow-one-line?
  "Should we allow this function to print on a single line?"
  [{:keys [fn-force-nl fn-gt2-force-nl fn-gt3-force-nl], :as options} len
   fn-style]
  (not (or (fn-force-nl fn-style)
           (and (> len 3) (fn-gt2-force-nl fn-style))
           (and (> len 4) (fn-gt3-force-nl fn-style))
           (if-let [future-caller (fn-style->caller fn-style)]
             (let [caller-map (future-caller options)]
               (or (:flow? caller-map) (:force-nl? caller-map)))))))

(defn modify-zloc-legacy
  "If the (caller options) has a value for :return-altered-zipper, then
  examine the value.  It should be [<depth> <symbol> <fn>]. 
  If the <depth> is nil, any depth will do. If the
  <symbol> is nil, any symbol will do.  If the <depth> and <symbol>
  match, then the <fn> is called as (fn caller options zloc), and must
  return a new zloc."
  [caller options zloc]
  (let [[depth trigger-symbol modify-fn :as return-altered-zipper-value]
          (:return-altered-zipper (caller options))]
    (dbg options
         "modify-zloc-legacy caller:" caller
         "ztype" (:ztype options)
         "return-altered-zipper-value:" return-altered-zipper-value)
    (if (or (not= (:ztype options) :zipper) (nil? return-altered-zipper-value))
      zloc
      (let [call-fn? (and (or (nil? depth) (= (:depth options) depth))
                          (or (not trigger-symbol)
                              (and (zsymbol? (zfirst zloc))
                                   (= trigger-symbol (zsexpr (zfirst zloc)))))
                          modify-fn)]
        (dbg options
             "modify-zloc:-legacy zloc" (zstring zloc)
             "call-fn?" call-fn?)
        (if call-fn?
          (let [return (modify-fn caller options zloc)]
            (dbg options "modify-zloc-legacy return:" (zstring return))
            return)
          zloc)))))

(declare fzprint-guide)
(declare any-zcoll?)
(declare wrap-zmap)

(defn lookup-fn-str
  "Look up the fn-str in the :fn-map.  If the result is another string,
  look that up.  Prevent infinite loops."
  ([fn-map fn-str fn-str-set]
   (if (fn-str-set fn-str)
     (throw (#?(:clj Exception.
                :cljs js/Error.)
             (str "Circular :fn-map lookup! fn-str: '"
                  fn-str
                  "' has already been used in this lookup."
                  " fn-strs in this lookup: "
                  fn-str-set)))
     (let [result (fn-map fn-str)]
       (when result
         (if (string? result)
           (lookup-fn-str fn-map result (conj fn-str-set fn-str))
           result)))))
  ([fn-map fn-str] (lookup-fn-str fn-map fn-str #{})))

(defn get-correct-options-map
  "Given a fn-style, which might be a keyword or might be avector with 
  one or two options maps, get the correct one based on the :ztype 
  in the options. Returns [fn-style options-map]"
  [options fn-style]
  (if (vector? fn-style)
    (if (= (count fn-style) 2)
      ; only one option map
      [(first fn-style) (second fn-style)]
      ; Two options maps, pick the right one
      [(first fn-style)
       (if (= :zipper (:ztype options)) (second fn-style) (nth fn-style 2))])
    ; Just a fn-style, no options map
    [fn-style nil]))

(declare handle-fn-style)

(defn lookup-fn-type-map
  "Given a keyword fn-type, look it up in the fn-type-map and handle
  any aliasing and options maps that come up. This includes adding 
  options maps to the options. Returns [options fn-style]"
  ; In this routine, fn-type is a keyword, and fn-style might be a bare
  ; fn-type, or it might be a vector with options maps.
  ([options fn-type fn-type-set]
   (if (fn-type-set fn-type)
     (throw (#?(:clj Exception.
                :cljs js/Error.)
             (str "Circular :fn-type-map lookup! fn-type: '"
                  fn-type
                  "' has already been used in this lookup."
                  " fn-types in this lookup: "
                  fn-type-set)))
     (let [fn-style ((:fn-type-map options) fn-type)]
       (if fn-style
         ; We got something, let's see what we got
         (handle-fn-style options fn-style (conj fn-type-set fn-type))
         [options fn-type]))))
  ([options fn-type] (lookup-fn-type-map fn-type #{})))

(defn handle-fn-style
  "Takes current options map and a fn-style, which might be a single
  fn-type, and might be a vector with one or two options maps, and
  handles the lookups. Returns [new-options fn-type] Note: We allow
  strings in the :fn-map only as bare strings, not in the fn-style
  position within the vector.  You can say 'do it like this', but
  not 'do it like this, with a few little changes'"
  ([options fn-style fn-type-set]
   (if fn-style
     ; We got something, let's see what we got
     (let [[new-fn-type options-map] (get-correct-options-map options fn-style)
           [new-options latest-fn-type]
             (if new-fn-type
               (lookup-fn-type-map options new-fn-type fn-type-set)
               [options new-fn-type])]
       #_(println "handle-fn-style: :fn-style:" fn-style
                  "new-fn-type" new-fn-type
                  "latest-fn-type" latest-fn-type
                  "count new-options:" (count new-options)
                  "count options-map" (count options-map)
                  ":fn" (:fn (:fn-type-map options)))
       (if options-map
         [(first (zprint.config/config-and-validate "fn-style:"
                                                    nil
                                                    new-options
                                                    options-map
                                                    ; validate?
                                                    nil)) latest-fn-type]
         [new-options latest-fn-type]))
     ; No fn-style, return what we were called with.
     [options fn-style]))
  ([options fn-style] (handle-fn-style options fn-style #{})))

(defn handle-new-fn-style
  "If the :fn-style that is in the options differs from the fn-style
  we were called with (which is the one we already had), then we
  will deal with it.  It might be a traditional keyword, in which
  case we just return it as the new fn-style.  But it might be a
  string, in which case we need to look it up in the :fn-map, and
  deal with handling the results (or failure) of that lookup.  In
  any case, return [options fn-style]"
  [caller options fn-style fn-map user-fn-map option-fn]
  (let [new-fn-style (let [new-fn-style (:fn-style options)]
                       (when (not= new-fn-style fn-style) new-fn-style))]
    (if new-fn-style
      (if (string? new-fn-style)
        (let [found-fn-style (or (lookup-fn-str fn-map new-fn-style)
                                 (lookup-fn-str user-fn-map new-fn-style))]
          (if found-fn-style
            ; Found something, use that.
            (handle-fn-style options found-fn-style)
            ; Didn't find it.
            (throw (#?(:clj Exception.
                       :cljs js/Error.)
                    (str " When "
                         caller
                         " called an option-fn "
                         (option-fn-name option-fn)
                         " it returned a fn-style which"
                         " was a string '"
                         new-fn-style
                         "' which was not found in the :fn-map!")))))
        ; It wasn't a string, but it might be a full-on
        ; complex vector style
        (handle-fn-style options new-fn-style))
      ; Didn't get a new fn-style in new-options,
      ; use the ones we already have
      [options fn-style])))

(defn fn-style+option-fn
  "Take the current fn-style and lots of other important things,
  and handle lookups in the fn-type-map, as well as calling
  option-fn(s) as necessary.  
  Returns [options fn-style zloc l-str r-str changed?], where changed?
  refers only to the zloc, l-str, or r-str."
  ([caller options fn-style zloc l-str r-str option-fn-set]
   (dbg-s options
          #{:call-option-fn}
          "fn-style+option-fn caller:" caller
          "fn-style:" fn-style
          "option-fn-set:" option-fn-set)
   (let [fn-map (:fn-map options)
         user-fn-map (:user-fn-map options)
         ; no validation because we assume these were validated in :fn-map
         [options fn-style] (handle-fn-style options fn-style)
         ; Now fn-style isn't a vector even if it was before
         option-fn (:option-fn (options caller))
         ; check the option-fn for having been called before
         _ (when (option-fn-set option-fn)
             (throw (#?(:clj Exception.
                        :cljs js/Error.)
                     (str "Circular :option-fn lookup! option-fn: '"
                          option-fn
                          "' has already been called and is being called again!"
                          " option-fns in this call chain: "
                          option-fn-set))))
         ; If we have an option-fn, call it.
         ; new-options are the pre config-and-validated options and are not
         ; worth much, since they have raw styles in them
         [options zloc l-str r-str changed?]
           (if option-fn
             (call-option-fn caller options option-fn zloc l-str r-str)
             [options zloc l-str r-str nil])
         ; If we got a new fn-style in new-options, handle it
         [options fn-style] (handle-new-fn-style caller
                                                 options
                                                 fn-style
                                                 fn-map
                                                 user-fn-map
                                                 option-fn)
         ; Did we get a new option-fn? that is not nil?  If yes, do this again
         [options fn-style zloc l-str r-str new-changed?]
           (let [new-option-fn (:option-fn (caller options))]
             (if (and new-option-fn (not= option-fn new-option-fn))
               (fn-style+option-fn caller
                                   options
                                   fn-style
                                   zloc
                                   l-str
                                   r-str
                                   (conj option-fn-set option-fn))
               ; Didn't get a new option-fn, just return what we have
               [options fn-style zloc l-str r-str nil]))
         changed? (or changed? new-changed?)]
     [options fn-style zloc l-str r-str changed?]))
  ([caller options fn-style zloc l-str r-str]
   (fn-style+option-fn caller options fn-style zloc l-str r-str #{})))

(declare fzprint-noformat)

(defn fzprint-list*
  "Print a list, which might be a list or an anon fn.  
  Lots of work to make a list look good, as that is typically code. 
  Presently all of the callers of this are :list or :vector-fn."
  [caller l-str r-str
   ; The options map can get re-written down a bit below, so don't get
   ; anything with destructuring that might change with a rewritten  options
   ; map!
   {:keys [fn-map user-fn-map one-line? fn-style no-arg1? fn-force-nl quote?],
    :as options} ind zloc]
  (dbg-s options
         :next-inner
         "fzprint-list*: ind:" ind
         "fn-style:" fn-style
         "option-fn:" (:option-fn (options caller))
         "rightcnt:" (:rightcnt options))
  (if (= (:format options) :off)
    (fzprint-noformat l-str r-str options zloc)
    ; We don't need to call get-respect-indent here, because all of the
    ; callers of fzprint-list* define respect-nl?, respect-bl? and indent-only?
    (let [max-length (get-max-length options)
          ; Legacy modify-zloc for compatibility, now deprecated.
          ; It was only experimental in the first place, so we can actually
          ; delete it at some point.
          ; This capability is now handled by extending the option-fn
          ; mechanism to support rewriting a zipper or a structure,
          ; in part to support specifying it in the :fn-map for only
          ; certain functions.
          zloc (modify-zloc-legacy caller options zloc)
          ; zcount does (zmap identity zloc) which counts comments and the
          ; newline after it, but no other newlines
          len (zcount zloc)
          zloc (if (> len max-length) (ztake-append max-length zloc '...) zloc)
          len (zcount zloc)
          l-str-len (count l-str)
          indent (:indent (options caller))
          ; NOTE WELL -- don't use arg-1-zloc (or arg-2-zloc, etc.) as
          ; a condition, because it might well be legitimately nil when
          ; formatting structures.
          [pre-arg-1-style-vec arg-1-zloc arg-1-count zloc-seq :as first-data]
            (fzprint-up-to-first-zloc caller options (+ ind l-str-len) zloc)
          #_(prn "fzprint-list* zloc-seq:" (map zstring zloc-seq))
          arg-1-coll? (not (or (zkeyword? arg-1-zloc) (zsymbol? arg-1-zloc)))
          ; Use an alternative arg-1-indent if the fn-style is forced on input
          ; and we don't actually have an arg-1 from which we can get an indent.
          ; Now, we might want to allow arg-1-coll? to give us an arg-1-indent,
          ; maybe, someday, so we could hang next to it.
          ; But for now, this will do.
          arg-1-indent-alt? (and arg-1-coll? fn-style)
          ; Expand fn-str to really be the thing to look up in the fn-map
          fn-str (if-not arg-1-coll? (zstring arg-1-zloc))
          ; If we don't have a fn-str, then we might have a fn-type.
          ; NOTE WELL: a fn-style is a :arg1 or something like that.  This
          ; is called a ::fn-type in the spec!
          ; Here, a fn-type is a keyword of the "type" of the fn-str,
          ; i.e. :list, :map, :vector or :set.
          fn-type (when-not fn-str
                    (cond (zlist? arg-1-zloc) :list
                          (zmap? arg-1-zloc) :map
                          (zvector? arg-1-zloc) :vector
                          (zset? arg-1-zloc) :set
                          :else nil))
          ; If we have been told that we are in a quote?, and :quote has
          ; something to say about what we should do for formatting, then
          ; regardless of any fn-str, we will do it.
          [fn-style fn-str fn-type]
            (if (and quote?
                     (or (not= (get fn-map :quote :none) :none)
                         (not= (get user-fn-map :quote :none) :none)))
              [nil nil :quote]
              [fn-style fn-str fn-type])
          ; Look up the fn-str in both fn-maps, and then if we don't get
          ; something, look up the fn-type in both maps.
          fn-style (or fn-style
                       ; This is where we would need to handle option maps
                       ; if we are doing that.
                       (lookup-fn-str fn-map fn-str)
                       (lookup-fn-str user-fn-map fn-str)
                       ; See if the "type" of the fn-str is defined
                       (fn-map fn-type)
                       (user-fn-map fn-type))
          ; if we don't have a function style after all of that, let's see
          ; if we can get one by removing the namespacing.
          ; This will not interact with the fn-type because if we have a
          ; fn-str then we don't have a fn-type!
          fn-style (if (and (not fn-style) fn-str)
                     (let [fn-str (last (clojure.string/split fn-str #"/"))]
                       ; Fix for Issue #276 -- didn't used to use
                       ; lookup-fn-str here.
                       (or (lookup-fn-str fn-map fn-str)
                           (lookup-fn-str user-fn-map fn-str)))
                     fn-style)
          ; If we have a fn-str and not a fn-style, see if we have a default
          ; for functions which were not set explicitly to :none
          fn-style (if (and fn-str (nil? fn-style))
                     (:default-not-none fn-map)
                     fn-style)
          ; If we have a fn-str and not a fn-style, see if we have a default
          ; fn-style for every function which doesn't have one explicitly set
          ; or where it was :none
          fn-style (if (= fn-style :none) nil fn-style)
          fn-style (if (and fn-str (nil? fn-style)) (:default fn-map) fn-style)
          ; Do we have a [fn-style options] vector?
          ; **** NOTE: The options map can change here, and if it does,
          ; some of the things found in it above would have to change too!
          ; This not only looks up the fn-style, but also handles any
          ; vector that might be present in the initial fn-style.
          vector-fn-style? (vector? fn-style)
          ; After this, it isn't a vector any more...
          ; Note that changed? refers only zloc, l-str, or r-str!!
          [options fn-style zloc l-str r-str changed?]
            (fn-style+option-fn caller options fn-style zloc l-str r-str)
          ; recalculate necessary information
          l-str-len (if changed? (count l-str) l-str-len)
          ; zcount is not free, so only do it if the zloc changed
          len (if changed? (zcount zloc) len)
          guide (or (:guide options) (guide-debug caller options))
          ; Remove :guide and any forced :fn-style from options so they only
          ; happen once!
          options (dissoc options :guide :fn-style)
          #_(println "\nguide after:" guide "\nguide options:" (:guide options))
          _ (when guide (dbg-pr options "fzprint-list* guide:" guide))
          ; If we messed with the options for any of two reasons, then find
          ; new stuff.  This will probably change only zloc-seq because
          ; of :respect-nl? or :indent-only?  But :meta {:split? true} could
          ; change it as well.
          ; TODO: Figure out where :indent-only? and :respect-nl? are handled
          ; and :meta {:split? true} also, and see if we are handling them
          ; correctly here.
          [pre-arg-1-style-vec arg-1-zloc arg-1-count zloc-seq :as first-data]
            (if (or changed? vector-fn-style?)
              (fzprint-up-to-first-zloc caller options (+ ind l-str-len) zloc)
              first-data)
          ; Get rid of the any vector surrounding the fn-style.
          ; Don't do this too soon, as multiple things are driven off of
          ; (vector? fn-style), above
          fn-style (if (vector? fn-style) (first fn-style) fn-style)
          ; Finish finding all of the interesting stuff in the first two
          ; elements
          [pre-arg-2-style-vec arg-2-zloc arg-2-count _ :as second-data]
            ; The ind is wrong, need arg-1-indent, but we don't have it yet.
            (fzprint-up-to-next-zloc caller
                                     options
                                     ;(+ ind l-str-len)
                                     (+ ind indent)
                                     first-data)
          ; This len doesn't include newlines or other whitespace or
          len (zcount-zloc-seq-nc-nws zloc-seq)
          #_(prn "fzprint-list* pre-arg-1-style-vec:" pre-arg-1-style-vec
                 "pre-arg-2-style-vec:" pre-arg-2-style-vec
                 "arg-1-zloc:" (zstring arg-1-zloc)
                 "arg-2-zloc:" (zstring arg-2-zloc)
                 "arg-1-count:" arg-1-count
                 "arg-2-count:" arg-2-count
                 "len:" len)
          ; If fn-style is :replace-w-string, then we have an interesting
          ; set of things to do.
          ;
          [options arg-1-zloc l-str l-str-len r-str len zloc-seq]
            (if (and (= fn-style :replace-w-string)
                     (:replacement-string (options caller))
                     (= len 2))
              [(assoc (update-in options [caller] dissoc :replacement-string)
                 :rightcnt (dec (:rightcnt options))) arg-2-zloc
               (:replacement-string (options caller))
               (count (:replacement-string (options caller))) "" 1
               (remove-one zloc-seq arg-1-count)]
              [options arg-1-zloc l-str l-str-len r-str len zloc-seq])
          #_(prn "fzprint-list*: l-str:" l-str
                 "l-str-len:" l-str-len
                 "len:" len
                 "fn-style:" fn-style)
          ; Get indents which might have changed if the options map was
          ; re-written by the function style being a vector.
          indent (:indent (options caller))
          indent-arg (:indent-arg (options caller))
          indent-only? (:indent-only? (options caller))
          ; set indent based on fn-style
          indent (if (body-set fn-style) indent (or indent-arg indent))
          indent (+ indent (dec l-str-len))
          #_(prn "fzprint-list* indent:" indent "indent-arg:" indent-arg)
          ; If we have a :guide value, then we are going to use it no
          ; matter the fn-style we had before.  Note that we kept the
          ; original fn-style around long enough to get the indent figured
          ; out, immediately above.  And we went to :guided in time to
          ; cause one-line-ok? to be false, immediately below.
          fn-style (if guide :guided fn-style)
          one-line-ok? (allow-one-line? options len fn-style)
          one-line-ok? (when-not indent-only? one-line-ok?)
          one-line-ok? (if (= fn-style :guided) nil one-line-ok?)
          one-line-ok? (if (not= pre-arg-1-style-vec :noseq) nil one-line-ok?)
          ; If this is :binding, then the fn-gt2-force-nl applies to :binding
          ; for the number of things in the let (or whatever), and the
          ; otherwise unused fn-type of :binding-vector is checked to see if
          ; it should force us to not do one line as well.
          one-line-ok? (if (= fn-style :binding)
                         (and one-line-ok?
                              (allow-one-line? options
                                               (zcount arg-2-zloc)
                                               :binding-vector))
                         one-line-ok?)
          one-line-ok? (if (:force-nl? (options caller)) nil one-line-ok?)

	  ; If we have one-line-ok? on in the options map, then override all
	  ; of the other calculations we have made.
	  one-line-ok? (if (:one-line-ok? options) true one-line-ok?)
	  ; We will get rid of :one-line-ok? in the options map below, after
	  ; we actually try to format something to see if it fits on one line.

          ; remove -body from fn-style if it was there
          fn-style (or (body-map fn-style) fn-style)
          ; Fix up :fn for multi-arity functions
          ; If the second thing is a list, :fn maps to :flow in this case
          fn-style
            (if (and (= fn-style :fn) (zlist? arg-2-zloc)) :flow fn-style)
          ; All styles except :hang, :flow, and :flow-body and :binding need
          ; three elements minimum. We could put this in the fn-map,
          ; but until there are more than three (well four) exceptions, seems
          ; like too much mechanism.
          fn-style (if (#{:hang :flow :flow-body :binding :replace-w-string
                          :guided :list}
                        fn-style)
                     fn-style
                     (if (< len 3) nil fn-style))
          ;fn-style (if (= fn-style :hang) fn-style (if (< len 3) nil fn-style))
          fn-style (if no-arg1? (or (noarg1-map fn-style) fn-style) fn-style)
          ; no-arg? only affect one level down...
          options (if no-arg1? (dissoc options :no-arg1?) options)
          ; If l-str isn't one char, create an indent adjustment.  Largely
          ; for anonymous functions, which otherwise would have their own
          ; :anon config to parallel :list, which would be just too much
          indent-adj (dec l-str-len)
          ; The default indent is keyed off of whether or not the first thing
          ; in the list is itself a list, since that list could evaluate to a
          ; fn.  You can't replace the zlist? with arg-1-coll?, since if you do
          ; multi-arity functions aren't done right, since the argument vector
          ; is a coll?, and so arg-1-coll? is set, and then you get a two space
          ; indent for multi-arity functions, which is wrong.
          ; We could, conceivably, use zvector? here to specifically handle
          ; multi-arity functions.  Or we could remember we are in a defn and
          ; do something special there, or we could at least decide that we
          ; were in code when we did this zlist? thing, since that is all about
          ; code.  That wouldn't work if it was the top-level form, but would
          ; otherwise.
          default-indent (if (zlist? arg-1-zloc) indent l-str-len)
          ; Note that much is driven from arg-1-indent, since if this is nil
          ; the assumption is that the first argument is a collection, and that
          ; the indent should be l-str-len.  See local-indent below.
          ; Don't use fn-str here, as it might be old data if there was an
          ; option-fn that returned a new zloc yielding a new arg-1-zloc!
          arg-1-indent (if-not arg-1-coll?
                         (+ ind (inc l-str-len) (count (zstring arg-1-zloc))))
          ; If we don't have an arg-1-indent, and we noticed that the inputs
          ; justify using an alternative, then use the alternative.
          arg-1-indent (or arg-1-indent (when arg-1-indent-alt? (+ indent ind)))
          ; If we have anything in pre-arg-2-style-vec, then we aren't hanging
          ; anything and we replace any existing arg-1-indent with a normal
          ; one.
          arg-1-indent (if (= pre-arg-2-style-vec :noseq)
                         arg-1-indent
                         (when arg-1-indent (+ indent ind)))
          ; Tell people inside that we are in code.
          ; We don't catch places where the first thing in a list is
          ; a collection or a seq which yields a function.
          options (if (not arg-1-coll?)
                    ; quote? might have cancelled out fn-str, but if we still
                    ; want to think of ourselves as in-code? then use
                    ; (or fn-str quote?) instead of fn-str, below.
                    ; This would affect the default map sorting and how
                    ; condp is formatted, and probably not much else.
                    (assoc options :in-code? fn-str)
                    options)
          options (assoc options :pdepth (inc (long (or (:pdepth options) 0))))
          _ (when (:dbg-hang options)
              (println (dots (:pdepth options)) "fzs" fn-str))
          new-ind (+ indent ind)
          one-line-ind (+ l-str-len ind)
          options (if fn-style (dissoc options :fn-style) options)
          ; Update the call stack with the final fn-style we used
          ; Note that we already have a call stack frame (which may have been
          ; altered by the option-fn), so we have to take what is there
          ; and, possibly, change the fn-style.
          #_#_call-stack (:call-stack options)
          #_#_options
            (if (not= fn-style (:fn-style (first call-stack)))
              (assoc options
                :call-stack (conj (next call-stack)
                                  (assoc (first call-stack)
                                    :fn-style fn-style)))
              options)
          loptions (not-rightmost options)
          roptions options
          l-str-vec [[l-str (zcolor-map options l-str) :left]]
          ; Fudge the ind a bit for r-str-vec for anon fns: #()
          r-str-vec
            (rstr-vec options (+ ind (max 0 (dec l-str-len))) zloc r-str)
          _ (dbg-pr
              options
              "fzprint-list*:" (zstring zloc)
              "fn-str" fn-str
              "fn-style:" fn-style
              "len:" len
              "ind:" ind
              "indent:" indent
              "default-indent:" default-indent
              "one-line-ok?" one-line-ok?
              "arg-1-coll?" arg-1-coll?
              "arg-1-indent:" arg-1-indent
              "arg-1-zloc:" (zstring arg-1-zloc)
              "pre-arg-1-style-vec:" pre-arg-1-style-vec
              "l-str:" (str "'" l-str "'")
              "l-str-len:" l-str-len
              "r-str-vec:" r-str-vec
              "indent-adj:" indent-adj
              "one-line?" one-line?
              "indent-only?" indent-only?
              "in-code?" (:in-code? options)
              "rightcnt:" (:rightcnt options)
              "replacement-string:" (:replacement-string (caller options))
              "force-nl?" (:force-nl? (caller options))
              ":ztype:" (:ztype options))
          one-line (if (and (zero? len) (= pre-arg-1-style-vec :noseq))
                     :empty
                     (when one-line-ok?
                       (fzprint-one-line options one-line-ind zloc-seq)))
	  ; :one-line-ok? is only good for one try for the whole expression.
	  ; After that, it needs to go away whether or not we fit it onto
	  ; one line.
	  options (dissoc options :one-line-ok?)]
      (cond
        (= one-line :empty) (concat-no-nil l-str-vec r-str-vec)
        indent-only? (concat-no-nil l-str-vec
                                    (fzprint-indent caller
                                                    l-str
                                                    r-str
                                                    options
                                                    ind
                                                    zloc
                                                    fn-style
                                                    arg-1-indent)
                                    r-str-vec)
        one-line (concat-no-nil l-str-vec one-line r-str-vec)
        ; Don't put anything other than :guide here at the beginning before
        ; we check one-line?
        (= fn-style :guided)
          (let [zloc-count (count zloc-seq)
                ; If we have something other than a collection in the first
                ; position, use the indent we figured out above, else use
                ; what is probably an indent of 1
                local-indent
                  (if arg-1-indent indent (+ default-indent indent-adj))]
            #_(prn ":guided!")
            (concat-no-nil l-str-vec
                           (fzprint-guide caller
                                          options
                                          ; this is where we are w/out any
                                          ; indent
                                          ind
                                          ; this is where we are with the l-str
                                          one-line-ind
                                          local-indent
                                          guide
                                          zloc-seq)
                           r-str-vec))
        ; If we are in :one-line? mode, then either we called fzprint-one-line,
        ; above, or it was a guide, and we just did it and moved on.  If
        ; we get here and we are in one-line? mode, then we have failed.
        ; Note that the (dbg ...) returns nil regardless of whether or not
        ; it is enabled, which actually affects the control flow!
        one-line?
          (dbg options "fzprint-list*:" fn-str " one-line did not work!!!")
        ; =================================
        ; All additional fn-styles go here
        ; =================================
        (dbg options "fzprint-list*: fn-style:" fn-style) nil
        (and (= len 0) (= pre-arg-1-style-vec :noseq)) (concat-no-nil l-str-vec
                                                                      r-str-vec)
        (= len 1)
          ; While len is one, don't assume that there is actually only one
          ; thing to print and use fzprint*.  len only counts the non-comment
          ; and non-nl elements, and there might be other things to print.
          (concat-no-nil
            l-str-vec
            (fzprint-flow-seq caller roptions one-line-ind zloc-seq)
            r-str-vec)
        ; In general, we don't have a fn-style if we have less than 3 elements.
        ; However, :binding is allowed with any number up to this point, so we
        ; have to check here.  :binding is actually allowed with at least two
        ; elements, the third through n are optional.
        (and (= fn-style :binding) (> len 1) (zvector? arg-2-zloc))
          (let [[hang-or-flow binding-style-vec]
                  (fzprint-hang-unless-fail loptions
                                            (or arg-1-indent (+ indent ind))
                                            (+ indent ind)
                                            fzprint-binding-vec
                                            arg-2-zloc)
                binding-style-vec (if (= hang-or-flow :hang)
                                    (concat-no-nil [[" " :none :whitespace 14]]
                                                   binding-style-vec)
                                    binding-style-vec)]
            (concat-no-nil l-str-vec
                           pre-arg-1-style-vec
                           ; TODO: get rid of inc ind
                           (fzprint* loptions (inc ind) arg-1-zloc)
                           pre-arg-2-style-vec
                           binding-style-vec
                           (concat-no-nil
                             ; Here we use options, because fzprint-flow-seq
                             ; will sort it out.  It will also handle an
                             ; empty zloc-seq by returning :noseq, so we
                             ; don't have to check for (> len 2) before
                             ; we call it.
                             (fzprint-hang-remaining caller
                                                     options
                                                     (+ indent ind)
                                                     (+ indent ind)
                                                     (get-zloc-seq-right
                                                       second-data)
                                                     :binding)
                             r-str-vec)))
        (= fn-style :pair-fn)
          (let [zloc-seq-right-first (get-zloc-seq-right first-data)
                zloc-count (count zloc-seq)]
            (concat-no-nil l-str-vec
                           pre-arg-1-style-vec
                           (fzprint* loptions (inc ind) arg-1-zloc)
                           ; Removed the assoc-in 7/26/21 since I can't
                           ; see that it could be used.  Maybe it is left
                           ; over from when a zloc was passed down and not
                           ; a zloc-seq?
                           (fzprint-hang options
                                         #_(assoc-in options
                                             [:pair :respect-nl?]
                                             (:respect-nl? (caller options)))
                                         :pair-fn
                                         arg-1-indent
                                         (+ indent ind)
                                         fzprint-pairs
                                         zloc-count
                                         zloc-seq-right-first)
                           r-str-vec))
        (= fn-style :extend)
          (let [zloc-seq-right-first (get-zloc-seq-right first-data)]
            (concat-no-nil l-str-vec
                           pre-arg-1-style-vec
                           (fzprint* loptions (inc ind) arg-1-zloc)
                           (prepend-nl options
                                       (+ indent ind)
                                       ; I think fzprint-pairs will sort out
                                       ; which
                                       ; is and isn't the rightmost because of
                                       ; two-up
                                       (fzprint-extend options
                                                       (+ indent ind)
                                                       zloc-seq-right-first))
                           r-str-vec))
        ; needs (> len 2) but we already checked for that above in fn-style
        (or (and (= fn-style :fn) (not (zlist? arg-2-zloc)))
            (= fn-style :arg2)
            (= fn-style :arg2-force-nl)
            (= fn-style :arg2-fn)
            (= fn-style :arg2-pair)
            (= fn-style :arg2-extend))
          (let [[pre-arg-3-style-vec arg-3-zloc arg-3-count _ :as third-data]
                  ; The ind is wrong, need arg-1-indent, but we don't have it
                  ; yet.
                  (fzprint-up-to-next-zloc caller
                                           options
                                           ; This is probably wrong
                                           ; (+ ind l-str-len)
                                           (+ ind indent)
                                           second-data)
                #_(prn "pre-arg-1-style-vec:" pre-arg-1-style-vec)
                #_(prn "arg-1-zloc:" (zstring arg-1-zloc))
                #_(prn "pre-arg-2-style-vec:" pre-arg-2-style-vec)
                #_(prn "arg-2-zloc:" (zstring arg-2-zloc))
                #_(prn "pre-arg-3-style-vec:" pre-arg-3-style-vec)
                #_(prn "arg-3-zloc:" (zstring arg-3-zloc))
                zloc-seq-right-third (get-zloc-seq-right third-data)
                second-element (fzprint-hang-one
                                 caller
                                 (if (not arg-3-zloc) options loptions)
                                 ; This better not be nil
                                 arg-1-indent
                                 (+ indent ind)
                                 arg-2-zloc)
                [line-count max-width]
                  ; arg-1-indent better not be nil here either
                  (style-lines loptions arg-1-indent second-element)
                first-three
                  (when second-element
                    (let [first-two-wo-pre-arg-1
                            (concat-no-nil
                              (fzprint* loptions (+ indent ind) arg-1-zloc)
                              pre-arg-2-style-vec
                              second-element
                              pre-arg-3-style-vec)
                          local-options
                            (if (not zloc-seq-right-third) options loptions)
                          first-two-one-line? (fzfit-one-line
                                                local-options
                                                (style-lines
                                                  local-options
                                                  (+ ind indent)
                                                  first-two-wo-pre-arg-1))
                          ; Add pre-arg-1-style-vec back in, which might push
                          ; it to two lines (or many lines), but that
                          ; doesn't matter.
                          first-two (concat-no-nil pre-arg-1-style-vec
                                                   first-two-wo-pre-arg-1)]
                      (when-not first-two-one-line?
                        (dbg-pr options
                                "fzprint-list*: :arg2-* first two didn't fit:"
                                first-two))
                      (concat-no-nil
                        first-two
                        (if (or (= fn-style :arg2)
                                (= fn-style :arg2-force-nl)
                                (= fn-style :arg2-pair)
                                (= fn-style :arg2-fn)
                                (= fn-style :arg2-extend)
                                (and (zvector? arg-3-zloc) (= line-count 1)))
                          (fzprint-hang-one
                            caller
                            (if (not zloc-seq-right-third) options loptions)
                            (if (and (= pre-arg-3-style-vec :noseq)
                                     first-two-one-line?)
                              ; hang it if possible
                              max-width
                              ; flow it
                              (+ indent ind))
                            (+ indent ind)
                            arg-3-zloc)
                          (prepend-nl options
                                      (+ indent ind)
                                      (fzprint* (if (not zloc-seq-right-third)
                                                  options
                                                  loptions)
                                                (+ indent ind)
                                                arg-3-zloc))))))]
            (when first-three
              (if (not zloc-seq-right-third)
                ; if nothing after the third thing, means just three things
                (concat-no-nil l-str-vec first-three r-str-vec)
                ; more than three things
                (concat-no-nil
                  l-str-vec
                  first-three
                  (cond (= fn-style :arg2-pair)
                          (prepend-nl options
                                      (+ indent ind)
                                      (fzprint-pairs options
                                                     (+ indent ind)
                                                     zloc-seq-right-third))
                        (= fn-style :arg2-extend)
                          (prepend-nl options
                                      (+ indent ind)
                                      (fzprint-extend options
                                                      (+ indent ind)
                                                      zloc-seq-right-third))
                        :else (fzprint-hang-remaining caller
                                                      ;options
                                                      (if (= fn-style :arg2-fn)
                                                        (assoc options
                                                          :fn-style :fn)
                                                        options)
                                                      (+ indent ind)
                                                      ; force flow
                                                      (+ indent ind)
                                                      zloc-seq-right-third
                                                      fn-style))
                  r-str-vec))))
        (and (= fn-style :arg1-mixin) (> len 3))
          (let [[pre-arg-3-style-vec arg-3-zloc arg-3-count _ :as third-data]
                  (fzprint-up-to-next-zloc caller
                                           options
                                           (+ ind indent)
                                           second-data)
                [pre-arg-4-style-vec arg-4-zloc arg-4-count _ :as fourth-data]
                  (fzprint-up-to-next-zloc caller
                                           options
                                           (+ ind indent)
                                           third-data)
                arg-vec-index (or (zfind-seq #(or (zvector? %)
                                                  (when (zlist? %)
                                                    (zvector? (zfirst %))))
                                             zloc-seq)
                                  0)
                doc-string? (string? (get-sexpr-or-nil options arg-3-zloc))
                mixin-start (if doc-string? arg-4-count arg-3-count)
                mixin-length (- arg-vec-index mixin-start 1)
                mixins? (pos? mixin-length)
                doc-string (when doc-string?
                             (fzprint-hang-one caller
                                               loptions
                                               (+ indent ind)
                                               ; force flow
                                               (+ indent ind)
                                               arg-3-zloc))
                #_(prn ":arg1-mixin: doc-string?" doc-string?
                       "mixin-start:" mixin-start
                       "mixin-length:" mixin-length
                       "mixins?" mixins?
                       "arg-vec-index:" arg-vec-index
                       "doc-string" doc-string
                       "arg-1-count:" arg-1-count
                       "arg-1-zloc:" (zstring arg-1-zloc)
                       "arg-2-count:" arg-2-count
                       "arg-2-zloc:" (zstring arg-2-zloc)
                       "arg-3-count:" arg-3-count
                       "arg-3-zloc:" (zstring arg-3-zloc)
                       "arg-4-count:" arg-4-count
                       "arg-4-zloc:" (zstring arg-4-zloc))
                ; Have to deal with no arg-vec-index!!
                mixins
                  (when mixins?
                    (let [mixin-sentinal (fzprint-hang-one caller
                                                           loptions
                                                           (+ indent ind)
                                                           ; force flow
                                                           (+ indent ind)
                                                           (if doc-string?
                                                             arg-4-zloc
                                                             arg-3-zloc))
                          [line-count max-width] (style-lines loptions
                                                              (+ indent ind)
                                                              mixin-sentinal)]
                      (concat-no-nil
                        (if doc-string? pre-arg-4-style-vec pre-arg-3-style-vec)
                        mixin-sentinal
                        (fzprint-hang-remaining
                          caller
                          loptions
                          ; Apparently hang-remaining gives
                          ; you a space after the current
                          ; thing, so we need to account
                          ; for it now, since max-width is
                          ; the end of the current thing
                          (inc max-width)
                          (dec (+ indent indent ind))
                          (get-zloc-seq-right
                            (if doc-string fourth-data third-data))
                          fn-style
                          mixin-length))))]
            (concat-no-nil
              l-str-vec
              pre-arg-1-style-vec
              (fzprint* loptions (inc ind) arg-1-zloc)
              pre-arg-2-style-vec
              (fzprint-hang-one caller
                                (if (= len 2) options loptions)
                                arg-1-indent
                                (+ indent ind)
                                arg-2-zloc)
              (cond (and doc-string? mixins?) (concat-no-nil pre-arg-3-style-vec
                                                             doc-string
                                                             (remove-one-newline
                                                               mixins))
                    doc-string? (concat-no-nil pre-arg-3-style-vec doc-string)
                    mixins? (remove-one-newline mixins)
                    :else :noseq)
              (fzprint-hang-remaining
                caller
                (noarg1 options fn-style)
                (+ indent ind)
                ; force flow
                (+ indent ind)
                (nthnext zloc-seq
                         (if mixins?
                           arg-vec-index
                           (if doc-string? arg-4-count arg-3-count)))
                fn-style)
              r-str-vec))
        (or (= fn-style :arg1-pair)
            (= fn-style :arg1)
            (= fn-style :arg1-force-nl)
            (= fn-style :arg1->))
          (concat-no-nil
            l-str-vec
            pre-arg-1-style-vec
            (fzprint* loptions (inc ind) arg-1-zloc)
            pre-arg-2-style-vec
            (fzprint-hang-one caller
                              (if (= len 2) options loptions)
                              arg-1-indent
                              (+ indent ind)
                              arg-2-zloc)
            ; then either pair or remaining-seq
            ; we don't do a full hanging here.
            ; We wouldn't be here if len < 3
            (if (= fn-style :arg1-pair)
              (prepend-nl options
                          (+ indent ind)
                          (fzprint-pairs options
                                         (+ indent ind)
                                         (get-zloc-seq-right second-data)))
              (fzprint-hang-remaining caller
                                      (noarg1 options fn-style)
                                      (+ indent ind)
                                      ; force flow
                                      (+ indent ind)
                                      (get-zloc-seq-right second-data)
                                      fn-style))
            r-str-vec)
        ; we know that (> len 2) if fn-style not= nil
        (= fn-style :arg1-extend)
          (let [zloc-seq-right-second (get-zloc-seq-right second-data)]
            (cond (zvector? arg-2-zloc)
                    ; This will put the second argument
                    ; (a vector) on a different line than
                    ; the function name.  No known uses
                    ; for this code as of 7/20/19.  It
                    ; does work with :respect-nl and has
                    ; tests.
                    (concat-no-nil
                      l-str-vec
                      pre-arg-1-style-vec
                      (fzprint* loptions (+ indent ind) arg-1-zloc)
                      pre-arg-2-style-vec
                      (prepend-nl options
                                  (+ indent ind)
                                  (fzprint* loptions (+ indent ind) arg-2-zloc))
                      (prepend-nl options
                                  (+ indent ind)
                                  (fzprint-extend options
                                                  (+ indent ind)
                                                  zloc-seq-right-second))
                      r-str-vec)
                  :else (concat-no-nil
                          l-str-vec
                          pre-arg-1-style-vec
                          (fzprint* loptions (inc ind) arg-1-zloc)
                          pre-arg-2-style-vec
                          (fzprint-hang-one caller
                                            (if (= len 2) options loptions)
                                            arg-1-indent
                                            (+ indent ind)
                                            arg-2-zloc)
                          (prepend-nl options
                                      (+ indent ind)
                                      (fzprint-extend options
                                                      (+ indent ind)
                                                      zloc-seq-right-second))
                          r-str-vec)))
        (or (= fn-style :wrap) (:wrap? (caller options)))
          (let [new-ind (+ indent ind)
                coll-print (fzprint-seq options new-ind zloc-seq)
                _ (dbg-pr options "fzprint-list*: :wrap coll-print:" coll-print)
                wrap-coll? (:wrap-coll? (caller options))]
            ; Regular Pprocessing
            (if (and (not wrap-coll?) (any-zcoll? options new-ind zloc))
              (concat-no-nil l-str-vec
                             (apply concat-no-nil
                               (precede-w-nl options
                                             new-ind
                                             coll-print
                                             :no-nl-first
                                             (:nl-count (caller options))))
                             r-str-vec)
              ; Since there are either no collections in this vector
              ; or ; set ; or ; whatever, or if there are,
              ; it is ok to wrap them, ; print it
              ; wrapped on the same line as much as possible:
              ;           [a b c d e f
              ;            g h i j]
              (concat-no-nil l-str-vec
                             (do (dbg-pr options
                                         "fzprint-list*: wrap coll-print:"
                                         coll-print)
                                 (wrap-zmap caller
                                            options
                                            (+ ind l-str-len)
                                            new-ind
                                            coll-print))
                             r-str-vec)))
        ; This is where we want to interpret a list as just a list, and not
        ; something with a function as the first element.  It works not only
        ; for lists, but for vectors as well with :fn-format.
        (or (= fn-style :list) (:list? (caller options)))
          (let [new-ind (+ indent ind)
                coll-print (fzprint-seq options new-ind zloc-seq)
                _ (dbg-pr options
                          "fzprint-list*: :list coll-print:"
                          coll-print)]
            (if (contains-nil? coll-print)
              nil
              (if (:nl-separator? (caller options))
                ; If we are doing nl-separator, we are going to look
                ; at each of the elements in coll-print, and if it is
                ; multi-line, we will put :nl-count newlines after it
                ; otherwise we will do 1 newline.
                ; If nl-separator? is true (this arm of the if)
                ; and nl-count is a vector, then we will
                ; assume 2 for the nl-count.
                (let [nl-count (or (:nl-count (caller options)) 2)
                      nl-count (if (vector? nl-count) 2 nl-count)
                      nl-count-vector (create-nl-count-vec options
                                                           new-ind
                                                           nl-count
                                                           coll-print)
                      _ (dbg-s options
                               #{:list}
                               "count coll-print:" (count coll-print)
                               "nl-count-vector:" nl-count-vector
                               "caller:" caller
                               "(:nl-count caller):" (:nl-count (caller
                                                                  options)))]
                  (concat-no-nil l-str-vec
                                 (apply concat-no-nil
                                   (precede-w-nl options
                                                 new-ind
                                                 coll-print
                                                 :no-nl-first
                                                 nl-count-vector
                                                 #_(:nl-count (caller
                                                                options))))
                                 r-str-vec))
                ; nl-separator? false -- nl-count is the number of newlines
                ; between every element in the list.  Defaults to 1 in
                ; precede-w-nl.
                (concat-no-nil l-str-vec
                               (apply concat-no-nil
                                 (precede-w-nl options
                                               new-ind
                                               coll-print
                                               :no-nl-first
                                               (:nl-count (caller options))))
                               r-str-vec))))
        ; Unspecified seq, might be a fn, might not.
        ; If (first zloc) is a seq, we won't have an
        ; arg-1-indent.  In that case, just flow it
        ; out with remaining seq.  Since we already
        ; know that it won't fit on one line.  If it
        ; might be a fn, try hanging and flow and do
        ; what we like better.  Note that default-indent
        ; might be 1 here, which means that we are pretty
        ; sure that the (zfirst zloc) isn't a function
        ; and we aren't doing code.
        ;
        :else (concat-no-nil
                l-str-vec
                pre-arg-1-style-vec
                ; Can't use arg-1-zloc here as the if test, because when
                ; formatting structures, arg-1-zloc might well be nil!
                (if (not (zero? len))
                  (fzprint* loptions (+ l-str-len ind) arg-1-zloc)
                  :noseq)
                ; Same here -- can't use arg-1-zloc as if test!!
                (if (not (zero? len))
                  (let [zloc-seq-right-first (get-zloc-seq-right first-data)]
                    (if zloc-seq-right-first
                      ; We have something else to format after arg-1-zloc
                      (if arg-1-indent
                        ; Use fzprint-hang-remaining for :flow as well, with
                        ; hindent = findent to force flow, so that constant
                        ; pairing is done for :flow functions.
                        (let [result (fzprint-hang-remaining
                                       caller
                                       (noarg1 options fn-style)
                                       (if (= fn-style :flow)
                                         ; If the fn-type is :flow, make the
                                         ; hindent = findent so that it will
                                         ; flow
                                         (+ indent ind)
                                         arg-1-indent)
                                       ; Removed indent-adj because it caused
                                       ; several problems, issue #163
                                       (+ indent ind)
                                       ; Can't do this, because
                                       ; hang-remaining
                                       ; doesn't take a seq
                                       zloc-seq-right-first
                                       ;(znthnext zloc 0)
                                       fn-style)]
                          (dbg-pr options
                                  "fzprint-list*: r-str-vec:" r-str-vec
                                  "result:" result)
                          result)
                        ; This is collection as the first thing. Used to handle
                        ; :flow here as well, but now it goes through
                        ; fzprint-hang-remaining with hindent = findent so that
                        ; constant pairing works for flow.
                        (let [local-indent (+ default-indent ind indent-adj)]
                          (concat-no-nil ;[[(str "\n" (blanks local-indent))
                                         ;:none
                                         ;:indent]]
                            (fzprint-flow-seq caller
                                              (noarg1 options fn-style)
                                              local-indent
                                              ;(nthnext (zmap identity
                                              ;zloc) 1)
                                              zloc-seq-right-first
                                              :force-nl
                                              :newline-first))))
                      ; Nothing else after arg-1-zloc
                      :noseq))
                  :noseq)
                r-str-vec)))))

(defn fzprint-list
  "Pretty print and focus style a :list element."
  [options ind zloc]
  (dbg-pr options "fzprint-list")
  (fzprint-list* :list "(" ")" (rightmost options) ind zloc))

(defn fzprint-anon-fn
  "Pretty print and focus style a fn element."
  [options ind zloc]
  (dbg-pr options "fzprint-anon-fn")
  (fzprint-list* :list "#(" ")" (rightmost options) ind zloc))

(defn any-zcoll?
  "Return true if there are any collections in the collection."
  [options ind zloc]
  (let [coll?-seq (zmap zcoll? zloc)] (reduce #(or %1 %2) nil coll?-seq)))

;;
;; # Put things on the same line
;;

(defn wrap-zmap
  "Given the output from fzprint-seq, which is a style-vec in
  the making without spacing, but with extra [] around the elements,
  wrap the elements to the right margin. cur-ind is where we are now,
  and ind is where we should be after a newline."
  [caller
   {:keys [width rightcnt],
    {:keys [wrap-after-multi? respect-nl? no-wrap-after]} caller,
    :as options} cur-ind ind coll-print]
  #_(prn "wrap-zmap:" coll-print)
  (let [last-index (dec (count coll-print))
        rightcnt (fix-rightcnt rightcnt)]
    (loop [cur-seq coll-print
           cur-ind cur-ind
           index 0
           previous-newline? false
           ; transient here slows things down, interestingly enough
           out []]
      (if-not cur-seq
        (do (dbg-pr options "wrap-zmap: out:" out) out)
        (let [next-seq (first cur-seq)]
          (when next-seq
            (let [multi? (> (count (first cur-seq)) 1)
                  this-seq (first cur-seq)
                  _ (log-lines options "wrap-zmap:" ind this-seq)
                  _ (dbg-pr options "wrap-zmap: ind:" ind "this-seq:" this-seq)
                  [linecnt max-width lines] (style-lines options ind this-seq)
                  last-width (last lines)
                  len (- last-width ind)
                  len (max 0 len)
                  ; Do we have a no-wrap element?
                  no-wrap-element? (when no-wrap-after
                                     (no-wrap-after (first (first this-seq))))
                  ; If we do, then add its length to the length of the
                  ; existing element
                  next-len (when (and no-wrap-element? (second cur-seq))
                             (let [[linecnt max-width lines]
                                     (style-lines options ind (second cur-seq))
                                   last-width (last lines)]
                               (max 0 (- last-width ind))))
                  ; If we have a no-wrap element, make the index the index
                  ; of the next element, so we handle rightcnt correctly
                  original-len len
                  [len index] (if next-len
                                ; Add the lengths together, and also one for the
                                ; space
                                [(+ len next-len 1) (inc index)]
                                [len index])
                  newline? (= (nth (first this-seq) 2) :newline)
                  comment?
                    (if respect-nl? nil (= (nth (first this-seq) 2) :comment))
                  comment-inline? (if respect-nl?
                                    nil
                                    (= (nth (first this-seq) 2)
                                       :comment-inline))
                  width (if (= index last-index) (- width rightcnt) width)
                  ; need to check size, and if one line and fits, should fit
                  fit? (and (not newline?)
                            (or (zero? index) (not comment?))
                            (or (zero? index)
                                (and (if multi? (= linecnt 1) true)
                                     (<= (+ cur-ind len) width))))
                  _ (when no-wrap-element?
                      (dbg-pr options
                              "wrap-zmap: no-wrap-element:" (first (first
                                                                     this-seq))
                              "original-len:" original-len
                              "next-len:" next-len
                              "index:" index
                              "rightcnt:" rightcnt
                              "fit?" fit?))
                  new-ind (cond
                            ; Comments cause an overflow of the size
                            (or comment? comment-inline?) (inc width)
                            (and multi? (> linecnt 1) (not wrap-after-multi?))
                              width
                            fit? (+ cur-ind len 1)
                            newline? ind
                            :else (+ ind len 1))]
              #_(prn "------ this-seq:" this-seq)
              #_(println
                  "lines:" lines
                  "\nlinecnt:" linecnt
                  "\nmulti?" multi?
                  "\nnewline?:" newline?
                  "\ncomment?" comment?
                  "\ncomment-inline?" comment-inline?
                  "\nprevious-newline?:" previous-newline?
                  "\nlinecnt:" linecnt
                  "\nmax-width:" max-width
                  "\nlast-width:" last-width
                  "\nno-wrap-element?" no-wrap-element?
                  "\nnext-len:" next-len
                  "\nlen:" len
                  "\nindex:" index
                  "\ncur-ind:" cur-ind
                  "\nnew-ind:" new-ind
                  "\nwidth:" width
                  "\nfit?" fit?)
              ; need to figure out what to do with a comment,
              ; want to force next line to not fit whether or not
              ; this line fit.  Comments are already multi-line, and
              ; it is really not clear what multi? does in this routine
              (recur (next cur-seq)
                     new-ind
                     (inc index)
                     newline?
                     ; TODO: concat-no-nil fails here, why?
                     (concat
                       out
                       (if fit?
                         (if (not (zero? index))
                           (concat-no-nil [[" " :none :whitespace 15]] this-seq)
                           this-seq)
                         (if newline?
                           [[(str "\n"
                                  ; Fix sets and vectors to have
                                  ; terminal right thing after a
                                  ; comment or newline be indented
                                  ; like other elements are.  Used
                                  ; to just be (blanks (dec new-ind))
                                  ; now the if checks to see if we
                                  ; are at the end, and does new-ind,
                                  ; which is like the other stuff.
                                  ; But wrong for the future of where
                                  ; we are going, as it happens.
                                  (blanks
                                    ; Figure out what the next thing is
                                    (let [this-seq-next (first (next cur-seq))
                                          newline-next?
                                            (when this-seq-next
                                              (= (nth (first this-seq-next) 2)
                                                 :newline))]
                                      ; If the next thing is a newline,
                                      ; don't put any blanks on this line
                                      (if newline-next? 0 (dec new-ind)))))
                             :none :indent 21]]
                           ; Unclear if a prepend-nl would be useful here...
                           (if previous-newline?
                             (concat-no-nil [[" " :none :whitespace 16]]
                                            this-seq)
                             (prepend-nl options ind this-seq)))))))))))))

(defn count-comments-and-newlines
  "Given a seq from fzprint-seq, count the newlines and contiguous comments
  at the beginning of the list.  A comment preceded by a newline or comment
  doesn't count."
  [coll-print]
  (loop [cur-seq coll-print
         previous-comment? false
         previous-newline? false
         comment-and-newline-count 0]
    (if-not cur-seq
      comment-and-newline-count
      (let [element-type (nth (ffirst cur-seq) 2)
            comment? (or (= element-type :comment)
                         (= element-type :comment-inline))
            newline? (= element-type :newline)]
        (if-not (or newline? comment?)
          comment-and-newline-count
          (recur (next cur-seq)
                 comment?
                 newline?
                 (if comment?
                   (if previous-newline?
                     ; Don't count the newline preceding a comment
                     (dec comment-and-newline-count)
                     comment-and-newline-count)
                   (inc comment-and-newline-count))))))))

(defn zcount-comments-and-newlines
  "Given a zloc-seq, count the newlines and contiguous comments
  at the beginning of the list.  A comment preceded by a newline or comment
  doesn't count."
  [zloc-seq]
  (loop [cur-seq zloc-seq
         previous-comment? false
         previous-newline? false
         comment-and-newline-count 0]
    (if-not cur-seq
      comment-and-newline-count
      (let [tag (ztag (first cur-seq))
            comment? (= tag :comment)
            newline? (= tag :newline)]
        (if-not (or newline? comment?)
          comment-and-newline-count
          (recur (next cur-seq)
                 comment?
                 newline?
                 (if comment?
                   (if previous-newline?
                     ; Don't count the newline preceding a comment
                     (dec comment-and-newline-count)
                     comment-and-newline-count)
                   (inc comment-and-newline-count))))))))

(defn inline-comment->comment
  "If the first thing in a guided output-seq is an inline comment, turn
  it into a regular comment.  Output a possibly modified output-seq."
  [output-seq]
  (if (= (nth (first output-seq) 2) :comment-inline)
    (let [[s c] (first output-seq)
          new-first [s c :comment]]
      (if (> (count output-seq) 1) [new-first (next output-seq)] [new-first]))
    output-seq))

(defn guided-output
  "Return information to be added to the output vector along
  with other information [param-map previous-data out].  Will do an
  fzprint* on zloc unless next-seq has data in it to use."
  ; [caller options incoming-seq zloc next-guide cur-index guide-seq
  ;  index param-map mark-map previous-data out]
  [caller
   {:keys [width rightcnt one-line?],
    {:keys [wrap-after-multi? wrap-multi? respect-nl?]} caller,
    :as options} zloc rightmost-zloc? next-guide cur-index guide-seq
   element-index index
   {:keys [excess-guided-newline-count align-key last-cur-index rightcnt cur-ind
           ind indent spaces one-line-ind group-seq all-fit?],
    :as param-map} mark-map
   [previous-newline? previous-guided-newline? unguided-newline-out?
    previous-comment? :as previous-data] out]
  ; We either have a zloc or we have a group, but not both, so there
  ; are no decisions to make about which one to use.
  (dbg-s options :guide "guided-output: zloc:" (pr-str (zstring zloc)))
  (if (and group-seq (empty? group-seq) (not zloc))
    ; We have a group, but it is empty, so do nothing -- but be sure and
    ; remove it, or we're going to do a lot of nothing
    [(dissoc param-map :group-seq) previous-data out]
    (let [group-seq (if (and (empty? group-seq) zloc) nil group-seq)
          uneval? (= (ztag zloc) :uneval)
          guided-newline? (= next-guide :newline)
          ; incoming-pairs is [pair-ind pair-result]
          do-pairs? (or (= next-guide :pair-end)
                        (= next-guide :element-pair-group)
                        (= next-guide :element-pair-*))
          options (if (= next-guide :element-guide)
                    (assoc options :guide (second guide-seq))
                    options)
          ; Forget incoming pairs
          incoming-seq nil
          ; incoming-seq is the incoming sequence, if any.  The only thing that
          ; is incoming at present is the pair information.
          incoming-seq (if guided-newline? [["\n" :none :newline]] incoming-seq)
          ; Now incoming-seq is either the pair information or possibly a
          ; newline
          ; generated because we have a :newline in the guide.
          ;
          ; There are two fundemental approaches here -- this-line and
          ; next-line.
          ; We will use fzprint* to see if something fits on this line
          ; in some cases, though not in all.  We also may have an
          ; incoming-seq which might fit on this line and might need to go to
          ; the next line. The pairs code looks at previous-newline? and
          ; doesn't try the current line if it is set.
          ;
          ; These values are true for this and next line.
          options (if rightmost-zloc? options (not-rightmost options))
          align-ind (when align-key (get mark-map align-key))
          ; If we have both align-ind and spaces, then add the spaces
          align-ind (when align-ind (if spaces (+ align-ind spaces) align-ind))
          ; Find out how big the incoming pairs (or guided-newline) is
          incoming-lines (style-lines options (or (+ indent ind)) incoming-seq)
          ; This is only true for this line, not next line.
          align-spaces (when align-ind (max 0 (- align-ind cur-ind)))
          ; There are two key values for the this-line and next-line calls
          ; to fzprint*: what value to give to fzprint* for ind, and how
          ; many spaces do we need to put on whatever is out there now to
          ; get there.  this-ind, this-spaces, next-ind, next-spaces.
          ;
          ; Figure out where we are on the line now, so we can call fzprint*
          ; If we have previous-newline? true, then we have a newline but
          ; no actual spaces to the right of it.  If we don't, then we are
          ; somewhere on the line, and just have to put some spaces between
          ; where we are and where the next thing begins.
          ;
          group-newline? (when group-seq (= (ztag (first group-seq)) :newline))
          regular-space
            (if (or previous-newline? (zero? element-index) group-newline?) 0 1)
          additional-spaces (or align-spaces spaces 0)
          beyond-cur-ind (max additional-spaces regular-space)
          ; How many spaces to the right of what is already there?
          ; Note that if we had a previous-newline, we don't really have cur-ind
          ; spaces there at this time, so we need to account for that.
          this-spaces
            (if previous-newline? (+ beyond-cur-ind cur-ind) beyond-cur-ind)
          this-ind (+ beyond-cur-ind cur-ind)
          ; Just trying this out here for fzprint-hang-remaining
          early-next-ind
            (or align-ind
                (+ (if (zero? element-index) one-line-ind (+ indent ind))
                   (or spaces 0)))
          this-early-next-ind early-next-ind
          [do-hang-remaining? hang-remaining-seq]
            (cond (or (= next-guide :element-newline-best-group)
                      (= next-guide :element-newline-best-*))
                    [true group-seq]
                  (or (= next-guide :element-best)
                      (= next-guide :element-best-first)
                      (= next-guide :element-best-*))
                    [true [zloc]])
          [do-extend? extend-seq]
            (cond (or (= next-guide :element-newline-extend-group)
                      (= next-guide :element-newline-extend-*))
                    [true group-seq]
                  (or (= next-guide :element-extend)
                      (= next-guide :element-extend-first)
                      (= next-guide :element-extend-*))
                    [true [zloc]])
          [do-wrap-flow? wrap-flow-seq]
            (cond (or (= next-guide :element-wrap-flow-group)
                      (= next-guide :element-wrap-flow-*))
                    [true group-seq])
          try-this? (and (or zloc do-pairs? group-seq)
                         (or (not previous-newline?) do-wrap-flow?)
                         (if (or (= next-guide :element-best-first)
                                 (= next-guide :element-extend-first))
                           all-fit?
                           true)
                         (not guided-newline?)
                         (or do-hang-remaining?
                             do-extend?
                             do-wrap-flow?
                             (and (< cur-ind width) (< this-ind width))))
          this-result
            (when try-this?
              (cond
                do-pairs? (fzprint-pairs (in-hang options) this-ind group-seq)
                do-hang-remaining? (fzprint-hang-remaining
                                     caller
                                     options
                                     #_(assoc options :dbg? true)
                                     ; This is the correct hindent, but it will
                                     ; include a space if it hangs it.
                                     this-ind
                                     early-next-ind
                                     hang-remaining-seq
                                     nil ;fn-type
                                   )
                do-wrap-flow? (fzprint-one-line options this-ind wrap-flow-seq)
                do-extend? (prepend-nl options
                                       (+ indent ind)
                                       (fzprint-extend options
                                                       ; Might be this-ind?
                                                       this-ind
                                                       extend-seq))
                ; We don't need to use fzprint-hang-unless-fail, because
                ; that is pretty much what we are doing with everything.
                (= next-guide :element-binding-vec)
                  (fzprint-binding-vec (in-hang options) this-ind zloc)
                (or (= next-guide :element-binding-group)
                    (= next-guide :element-binding-*))
                  (fzprint-pairs (in-hang options) this-ind group-seq :binding)
                :else (fzprint* (in-hang options) this-ind zloc)))
          ; If we did fzprint-hang-remaining and it has a single space at the
          ; beginning, then drop that space.
          this-result (if (and (or do-hang-remaining? do-extend?)
                               (= (nth (first this-result) 2) :whitespace)
                               (= (ffirst this-result) " "))
                        (next this-result)
                        this-result)
          #_(println "this-result:" this-result)
          this-lines (style-lines options this-ind this-result)
          ; Force wrap-multi? true for this guide if we are doing binding,
          ; regardless of its value in the options map.
          wrap-multi? (if (or (= next-guide :element-binding-group)
                              (= next-guide :element-binding-*))
                        true
                        wrap-multi?)
          ; this-multi? says that there is more than one thing in this-result,
          ; not that it is multi-line!!
          this-multi? (when this-result (> (count this-result) 1))
          ; this-linecnt has the number of lines in this-result.
          this-linecnt (when this-lines (first this-lines))
          ; This says that we either didn't try a this or we did and the
          ; this didn't fit.
          this-fit? (and (or zloc do-pairs? group-seq)
                         (and (not (empty? this-result))
                              (if this-multi?
                                (or wrap-multi? (<= this-linecnt 1))
                                true)))
          ; output-seq is either the results of the this-line fzprint* or the
          ; incoming sequence.
          output-seq (or this-result incoming-seq)
          ; Test whatever we've got for fit
          output-newline? (= (nth (first output-seq) 2) :newline)
          fail-fit? (or guided-newline?
                        output-newline?
                        (= (nth (first output-seq) 2) :indent)
                        (= (nth (first output-seq) 2) :comment-inline)
                        (and (not (zero? element-index))
                             (= (nth (first output-seq) 2) :comment))
                        ; We tried a this, and it didn't fit.
                        (and try-this? (not this-fit?)))
          #_(dbg-s options
                   :guide
                   "guided-output: this-lines:" this-lines
                   "this-multi?" this-multi?
                   "this-linecnt:" this-linecnt
                   "this-ind:" this-ind
                   "try-this?" try-this?
                   "this-fit?" this-fit?
                   "fail-fit?" fail-fit?)
          ; If we tried it and it didn't fit, forget about any spaces before
          ; we try it on the next line
          spaces (when spaces (if (and try-this? (not this-fit?)) nil spaces))
          ; If we tried it and it didn't fit, forget about any alignment before
          ; we try it on the next line
          align-ind (when align-ind
                      (if (and try-this? (not this-fit?)) nil align-ind))
          ; next-ind is the place we will base the fzprint* call on, and in
          ; this case it is also the number of spaces we will need to put
          ; out there before placing the return from fzprint*.
          next-ind (or align-ind
                       (+ (if (zero? element-index) one-line-ind (+ indent ind))
                          (or spaces 0)))
          ; If we have some kind of alignment, that is, align-ind or spaces,
          ; and we didn't even try it with this-fzprint*, then we need to
          ; see if it fits on the next line too, and if not, then do it
          ; without any alignment for real.  But only if we aren't already
          ; in-hang?
          early-next-ind next-ind
          test-fit? (and (not try-this?)
                         (or align-ind spaces)
                         (not (:in-hang? options)))
          ; We are going to do something on the next line.
          try-next? (and (or zloc do-pairs? group-seq)
                         (not this-fit?)
                         (not output-newline?))
          ; If we didn't try a this-line fzprint*, and we have some kind
          ; of slightly optional alignment, then see if it fits with the
          ; alignment on the next line.
          next-result (when (and test-fit? try-next?)
                        (if do-pairs?
                          (fzprint-pairs (in-hang options) next-ind group-seq)
                          (fzprint* (in-hang options) next-ind zloc)))
          first-next-result next-result
          ; We did the test-fit? and it didn't fit, so forget any alignment
          ; or spaces -- or not.
          next-ind (if (and try-next? test-fit? (empty? next-result))
                     (if (zero? element-index) one-line-ind (+ indent ind))
                     next-ind)
          ; See how it would fit if it were on the next line and we don't
          ; have a this that fit (either because we didn't try the this
          ; or because we did and the result didn't fit)
          ; Also don't flow if the this generated a newline.
          next-result (if (and try-next? (empty? next-result))
                        (cond
                          do-pairs? (fzprint-pairs options next-ind group-seq)
                          ;   (= next-guide :element-binding-vec)
                          ;         (fzprint-binding-vec options next-ind zloc)
                          do-hang-remaining? (fzprint-hang-remaining
                                               caller
                                               options
                                               #_(assoc options :dbg? true)
                                               ; flow it if we are doing it here
                                               next-ind
                                               next-ind
                                               hang-remaining-seq
                                               nil ;fn-type
                                             )
                          do-wrap-flow? (fzprint-hang-remaining
                                          caller
                                          options
                                          #_(assoc options :dbg? true)
                                          ; flow it if we are doing it here
                                          next-ind
                                          next-ind
                                          wrap-flow-seq
                                          nil ;fn-type
                                        )
                          do-extend? (prepend-nl options
                                                 (+ indent ind)
                                                 (fzprint-extend options
                                                                 ; Might be
                                                                 ; this-ind?
                                                                 next-ind
                                                                 extend-seq))
                          (= next-guide :element-binding-vec)
                            (fzprint-binding-vec options next-ind zloc)
                          (or (= next-guide :element-binding-group)
                              (= next-guide :element-binding-*))
                            (fzprint-pairs options next-ind group-seq :binding)
                          :else (fzprint* options next-ind zloc))
                        next-result)
          ; If we did fzprint-hang-remaining and it has a newline at the
          ; beginning, then we should drop that because we are going to
          ; do it ourselves since this is the "next" processing.
          #_(prn "next-result" next-result)
          next-result (if (and (or do-hang-remaining?
                                   do-extend?
                                   do-wrap-flow?
                                   (= next-guide :element-binding-*)
                                   (= next-guide :element-binding-group))
                               (= (nth (first next-result) 2) :indent)
                               (clojure.string/starts-with? (ffirst next-result)
                                                            "\n"))
                        (next next-result)
                        next-result)
          next-lines (style-lines options next-ind next-result)
          #_(dbg-s options
                   :guide
                   "guided-output: next-lines:" next-lines
                   "first-next-result:" first-next-result
                   "early-next-ind:" early-next-ind
                   "next-ind:" next-ind
                   "test-fit?" test-fit?
                   "try-next?" try-next?
                   "(+ indent ind):" (+ indent ind)
                   "beyond-cur-ind:" beyond-cur-ind
                   "cur-ind:" cur-ind
                   "element-index:" element-index)
          #_(dbg-s options
                   :guide
                   "guided-output: next?" (not (empty? next-result))
                   "this?" (not (empty? this-result)))
          output-seq (or next-result output-seq)
          ; This says that we don't fit if we used the result from the
          ; next line fzprint*
          fail-fit? (or fail-fit? next-result)
          comment? (= (nth (first output-seq) 2) :comment)
          comment-inline? (= (nth (first output-seq) 2) :comment-inline)
          newline? (or (and (= (nth (first output-seq) 2) :newline)
                            (= (count output-seq) 1))
                       guided-newline?)
          ; Note that this isn't an :indent guide, this is an indent at
          ; the start of the output-seq, which means a newline.
          ; Or a newline.  In any case, this will prevent a prepend-nl.
          indent? (or (= (nth (first output-seq) 2) :indent)
                      (= (nth (first output-seq) 2) :newline))
          ; output-seq might be nil, in which case several of these things
          ; are nil
          ; multi? says that there is more than one thing in output-seq, not
          ; it is multi-line!!
          multi? (when output-seq (> (count output-seq) 1))
          #_(dbg-s options
                   :guide
                   "guided-output: ind:" ind
                   "index:" index
                   "cur-index:" cur-index
                   "element-index:" element-index)
          ; Use whichever style-lines output makes sense, and figure the length
          ; of the last line which will become the new cur-ind (through new-ind,
          ; below).
          [linecnt max-width lines] (when output-seq
                                      (or next-lines this-lines incoming-lines))
          last-width (last lines)
          ; If it is multi? and (> linecnt 1), then it doesn't fit at present.
          ; If that changed, the fzprint-seq at the beginning would be wrong,
          ; because it says to do it at the "beginning".  Now, we could do
          ; what indent-only does and shift it all over, which can work, but
          ; isn't what we are doing today.
          ; If that ever changed, we would need to check the widest line, not
          ; just the last line as we are doing now.  Plus, we would need check
          ; the widest line against the width, and the last line against the
          ; rightcnt adjusted width as well to ensure a fit.
          ;
          ; If one line and fits, should fit.
          fit? (not fail-fit?)
          ; Calculate new location on the line, which is the end of the thing
          ; we are outputing now.
          new-ind
            (cond
              ; Comments cause an overflow of the size, forcing the next
              ; thing onto a new line
              (or comment? comment-inline?) (inc width)
              ; Uneval stuff with a previous newline will force the next
              ; thing onto a new line
              (and uneval? previous-newline?) (inc width)
              ; If is multi-line, and we have more than one line, and
              ; we don't allow anything after a multi-line thing on
              ; the same line, then force the next thing onto a new line
              (and multi? (> linecnt 1) (not wrap-after-multi?)) (inc width)
              ; If it is multi-line, and it is more than one line, and
              ; we *do* allow things after the last line, then the length
              ; of the last line is the new cur-ind.
              (and multi? (> linecnt 1)) last-width
              ; This is the old fit?
              fit? last-width
              ; When this is (+ indent ind), that is part of what  makes
              ; :spaces after a newline be "spaces beyond the indent",
              ; not "spaces instead of the indent".
              ; Also, if (zero? element-index), then we have put some stuff
              ; before the first :element in the guide.  The first element
              ; should be indented as if it were still on the same line
              ; as the l-str, and one-line-ind is that indent.
              ;
              ; Just be aware that this is something of a lie, since
              ; if we have a newline there really isn't anything on the
              ; line after it, since we can't take the chance that we
              ; might not have another newline next and we can't do
              ; trailing newlines.  So the next call to guided-ouput
              ; needs to understand that cur-ind is the number of spaces
              ; that we wish were after a newline but aren't actually
              ; there.
              newline? (if (zero? element-index) one-line-ind (+ indent ind))
              :else last-width)
          param-map (dissoc param-map :excess-guided-newline-count)
          param-map (assoc param-map
                      :cur-ind new-ind
                      :all-fit? (and fit? all-fit?))]
      ; We used to forget about spaces here in some situations, but
      ; really we only wanted to forget about them after :element or
      ; :element-align or :newline (a guided one), so we do that in
      ; fzprint-guide now.
      (dbg-s options
             :guide
             "guided-output: ------ incoming out:"
             (color-str ((:dzprint options)
                          {}
                          (into []
                                (let [out-len (count out)
                                      out-drop (int (* 0.8 out-len))
                                      out-drop (if (< (- out-len out-drop) 10)
                                                 (- out-len 10)
                                                 out-drop)]
                                  (condense-depth 1 out))))
                        :blue))
      (dbg-s-pr options :guide "guided-output; ------ next-guide:" next-guide)
      (dbg-s options
             :guide
             "guided-output: ------ output-seq:"
             (color-str ((:dzprint options) {} (condense-depth 1 output-seq))
                        :green))
      (dbg-s-pr options :guide "guided-output: ------ mark-map:" mark-map)
      ;"(first cur-seq)" (first cur-seq)
      (dbg-s
        options
        :guide "guided-output:"
        "\ncaller:" caller
        "\nindex:" index
        "\nzloc?" (not (empty? zloc))
        "\ngroup-seq-len:" (count group-seq)
        "\ngroup-newline?" group-newline?
        "\ncur-index:" cur-index
        "\nelement-index:" element-index
        "\nrightmost-zloc?" rightmost-zloc?
        "\none-line?" one-line?
        "\ndo-pairs?" do-pairs?
        "\nindent?" indent?
        "\nnewline?" newline?
        "\nguided-newline?" guided-newline?
        "\noutput-newline?" output-newline?
        "\nexcess-guided-newline-count:" excess-guided-newline-count
        "\nprevious-newline?" previous-newline?
        "\nunguided-newline-out?" unguided-newline-out?
        "\nprevious-comment?" previous-comment?
        "\nalign-key:" align-key
        "\nalign-ind:" align-ind
        "\nalign-spaces:" align-spaces
        "\nspaces:" spaces
        "\nTHIS:" ""
        "\n this-lines:" this-lines
        "\n this-ind:" this-ind
        "\n this-early-next-ind:" this-early-next-ind
        "\n this-spaces:" this-spaces
        "\n this-multi?" this-multi?
        "\n this-linecnt:" this-linecnt
        "\n try-this?" try-this?
        "\n this-fit?" this-fit?
        "\n fail-fit?" fail-fit?
        "\n do-hang-remaining?" do-hang-remaining?
        "\n do-extend?" do-extend?
        "\n do-wrap-flow?" do-wrap-flow?
        "\n regular-space:" regular-space
        "\n additional-spaces:" additional-spaces
        "\n beyond-cur-ind:" beyond-cur-ind
        "\nNEXT:" ""
        "\n next-lines:" next-lines
        "\n test-fit?" test-fit?
        "\n try-next?" try-next?
        "\n early-next-ind:" early-next-ind
        "\n next-ind:" next-ind
        "\n (+ indent ind):" (+ indent ind)
        "\nmulti?" multi?
        "\nwrap-multi?" wrap-multi?
        "\nlines:" lines
        "\nlinecnt:" linecnt
        "\nmax-width:" max-width
        "\nlast-width:" last-width
        "\nind:" ind
        "\nindent:" indent
        "\ncur-ind:" cur-ind
        "\nnew-ind:" new-ind
        "\nwidth:" width
        "\nfit?" fit?
        "\nall-fit?" all-fit?
        "\nfail-fit?" fail-fit?)
      [;
       ; param-map
       ;
       ; Get rid of one-time parameters and update things that have
       ; changed
       param-map
       ;
       ; previous-data
       ;
       [; previous-newline?
        newline?
        ; previous-guided-newline?
        guided-newline?
        ; unguided-newline-out?
        (and (not guided-newline?)
             (not (and previous-comment? newline?))
             (and (not fit?) (or newline? #_(not previous-newline?))))
        ; previous-comment?
        (or comment? comment-inline?)]
       ;
       ; out
       ;
       (let [guided-output-out
               (if fit?
                 ; Note that newlines don't fit
                 (if (not (zero? index))
                   (concat-no-nil [[(blanks this-spaces) :none :whitespace 25]]
                                  output-seq)
                   ; This might be nil, but that's ok
                   output-seq)
                 (if newline?
                   (concat-no-nil
                     ; If we have excess-guided-newline-count, then
                     ; output it now.  These newlines have no spaces
                     ; after them, so they should not be used to start
                     ; a line with something else on it!  We dec because
                     ; the next thing is a guarenteed newline.
                     (if (and excess-guided-newline-count
                              (pos? (dec excess-guided-newline-count)))
                       (repeat (dec excess-guided-newline-count)
                               ["\n" :indent 22])
                       :noseq)
                     [[(str "\n") :none :indent 21]])
                   ; This doesn't fit, and isn't a newline
                   ; Do we need a newline, or do we already have one
                   ; we could use?
                   ;
                   ; This will be a problem, as the simple case says
                   ; "Sure, we can use a guided newline here."
                   ; Don't let a comment come after a guided-newline
                   (if (and previous-newline?
                            (not (and comment? previous-guided-newline?)))
                     ; We have just done a newline that we can use.
                     (do (dbg-s options
                                :guide
                                "guided-output: previous-newline? etc.")
                         ; If output-seq starts with an inline-comment, make
                         ; it a regular comment, since it isn't inline with
                         ; anything amymore.
                         (concat-no-nil [[(blanks next-ind) :none :whitespace
                                          16]]
                                        (inline-comment->comment output-seq)))
                     ; Do we already have a newline at the beginning of a bunch
                     ; of output, or is this the very first thing?
                     (if (or indent? (zero? element-index))
                       ; Yes, don't prepend another newline
                       output-seq
                       (do (dbg-s options :guide "guided-output: prepend-nl:")
                           (prepend-nl
                             options
                             ; This code is related to the code under
                             ; fit? above, but without
                             ; previous-newline?, as that is assumed
                             ; since we are calling prepend-nl.
                             ;
                             ; Note that this is largely for ensuring
                             ; that non-inline comments end up indented
                             ; to match the indentation of the next
                             ; :element or :elment-align
                             next-ind
                             output-seq))))))]
         (dbg-s options
                :guide
                (color-str (str "guided-output returned additional out:"
                                (when (nil? guided-output-out)
                                  (str " - ALTOGETHER FAILED TO FIT!"
                                       (when one-line? "\n *** ONE-LINE ***"))))
                           :bright-blue)
                ((:dzprint options)
                  {:color? true}
                  guided-output-out
                  #_(into [] (condense-depth 1 guided-output-out))))
         ; If we have a nil guided-output-out, we don't add it on, we
         ; return a nil which will stop everything!
         (when guided-output-out (concat out guided-output-out)))])))

(defn comment-or-newline?
  "Is this element in the output from fzprint-seq a comment or a newline?"
  ; Should this include :indent?  Maybe not, as it looks like it is used
  ; only for constant pairing?
  [element]
  (let [element-type (nth (first element) 2)]
    (or (= element-type :comment)
        (= element-type :comment-inline)
        (= element-type :newline))))

(defn guide-here
  "Given the param map, return the location for here."
  [param-map mark-map]
  (max (or (when (:align-key param-map)
             (max 0
                  (- (+ (get mark-map (:align-key param-map))
                        (or (:spaces param-map) 0))
                     (:ind param-map))))
           0)
       (- (+ (:cur-ind param-map) (or (:spaces param-map) 1))
          (:ind param-map))))


(defn fzprint-guide
  "Given a zloc-seq wrap the elements to the right margin 
  and be guided by the guide seq."
  [caller
   {:keys [width rightcnt one-line?],
    {:keys [wrap-after-multi? respect-nl? indent]} caller,
    :as options} ind cur-ind local-indent guide zloc-seq]
  (dbg-s options
         :guide (color-str (str "fzprint-guide: entry: "
                                (zstring (first zloc-seq)))
                           :purple)
         "caller:" caller
         "ind:" ind
         "cur-ind:" cur-ind
         "local-indent:" local-indent
         "guide:" (color-str ((:dzprint options) {:style :guideguide} guide)
                             :blue))
  ; If it is one-line? and we have any :newlines in the guide, we are
  ; finished now
  (if (and one-line? (some #{:newline} guide))
    ; We clearly aren't going to output a single line
    (do (dbg-s options
               :guide
               "fzprint-guide: returned nil - one-line? is true and guide"
               "has :newline!")
        nil)
    (let [rightcnt (fix-rightcnt rightcnt)
          last-cur-index (dec (count zloc-seq))]
      (when-not guide
        (throw (#?(:clj Exception.
                   :cljs js/Error.)
                (str "No guide but fn-style is :guide for this sequence: "
                     (mapv zstring zloc-seq)))))
      (loop [cur-zloc zloc-seq
             cur-index 0
             guide-seq guide
             element-index 0
             index 0
             param-map {:cur-ind cur-ind,
                        :ind ind,
                        :one-line-ind cur-ind,
                        :indent local-indent,
                        :last-cur-index last-cur-index,
                        :rightcnt rightcnt,
                        :initial-options options,
                        :all-fit? true}
             mark-map {}
             [previous-newline? previous-guided-newline? unguided-newline-out?
              previous-comment? :as previous-data]
               nil
             options options
             out []]
        ; We can't just check for cur-seq here, or any groups we might be
        ; accumulating will be lost, since :group-end has to come beyond
        ; the last :element that finished cur-seq
        ;(if-not (or guide-seq cur-seq))
        (if (or (not (or guide-seq cur-zloc (:guided-newline-count param-map)))
                (nil? out))
          (do (dbg-s options
                     :guide
                     (color-str "fzprint-guide: out:" :bright-red)
                     ((:dzprint options) {} (into [] (condense-depth 1 out))))
              out)
          (if (> index 3000)
            (throw (#?(:clj Exception.
                       :cljs js/Error.)
                    (str "When processing a guide"
                           " the iteration limit of 3000 was"
                         " reached!" (first guide-seq))))
            (let [first-guide-seq (first guide-seq)
                  _ (dbg-s
                      options
                      :guide
                      "\n\nfzprint-guide: =====> (first guide-seq):"
                        first-guide-seq
                      "\nfzprint-guide: initial param-map:"
                        ((:dzprint options)
                          {}
                          (assoc (dissoc param-map :group-seq :initial-options)
                            :group-seq-len (count (:group-seq param-map)))))
                  ; If we are out of guide-seq, but we still have cur-seq
                  ; which we must because of the if-not above, then keep
                  ; doing elements in guide-seq for as long as we have cur-seq
                  ;
                  ; TODO: Change this to [:element], or it will fail when
                  ; the guide-seq runs out.  But it can be nice to have it
                  ; fail for debugging.
                  _ (when (empty? guide-seq)
                      (dbg-s options
                             :guide
                             "fzprint-guide: guide ran out! guide:"
                               (color-str ((:dzprint options)
                                            {:vector {:wrap? false}}
                                            guide)
                                          :bright-red)
                             "\nexpression:" ((:dzprint options)
                                               {:vector {:wrap? false}}
                                               (into []
                                                     (map zstring zloc-seq)))))
                  ; Sometimes we come through here when we have
                  ; (:guided-newline-count param-map)
                  ; and that's ok.
                  guide-seq (or guide-seq [:element-*])
                  ; First, look into what we have coming up in the sequence
                  ; we are formatting
                  comment? (= (ztag (first cur-zloc)) :comment)
                  ; always used together
                  comment-inline? comment?
                  next-newline? (= (ztag (first cur-zloc)) :newline)
                  uneval? (= (ztag (first cur-zloc)) :uneval)]
              (cond
                (and (:group-seq param-map) (:grouping? param-map))
                  ; we are accumulating elements from cur-zloc
                  ; and we only accept :element between :group-begin and
                  ; :group-end
                  ;
                  ; First, split off any comments or newlines or uneval
                  ; off of the front of zloc-seq
                  (cond
                    (= first-guide-seq :group-end)
                      ; It is entirely possible tha the group is empty, but
                      ; if it is, that will be handled by the -group call
                      ; to guided-output, which will notice the existence of
                      ; group-seq, and that it is empty, and gracefully do
                      ; nothing.
                      (do (dbg-s options
                                 :guide
                                 (color-str
                                   "fzprint-guide: === end accumulating a group"
                                   :bright-red))
                          (recur cur-zloc
                                 cur-index
                                 (next guide-seq)
                                 element-index
                                 (inc index)
                                 (assoc param-map :grouping? nil)
                                 mark-map
                                 previous-data
                                 options
                                 out))
                    (= first-guide-seq :element)
                      (let [[comments-or-newlines-cur-zloc remaining-cur-zloc]
                              (split-with pair-element? cur-zloc)
                            group-seq (:group-seq param-map)
                            ; Add the comments and newlines from cur-zloc to
                            ; group-seq
                            group-seq (into []
                                            (concat
                                              group-seq
                                              comments-or-newlines-cur-zloc))
                            next-zloc (first remaining-cur-zloc)
                            ; Do one more :element off of zloc-seq, if any
                            group-seq (if next-zloc
                                        (conj group-seq
                                              (first remaining-cur-zloc))
                                        group-seq)]
                        (dbg-s options
                               :guide
                               (color-str
                                 "fzprint-guide: === save a group element"
                                 :bright-red))
                        (recur
                          (next remaining-cur-zloc)
                          (+ cur-index (count comments-or-newlines-cur-zloc) 1)
                          (next guide-seq)
                          element-index
                          (inc index)
                          (assoc param-map :group-seq group-seq)
                          mark-map
                          previous-data
                          options
                          out))
                    :else (throw (#?(:clj Exception.
                                     :cljs js/Error.)
                                  (str
                                    "When processing a guide and accumulating a"
                                    " group only :element is allowed,"
                                    " but encountered: '"
                                    first-guide-seq
                                    "' instead!"))))
                ;
                ; process things that absorb information out of guide-seq
                ; without changing next-seq
                ;
                (and (= first-guide-seq :newline) unguided-newline-out?)
                  ; skip a guided newline if we had an unguided-newline-out
                  ; on the last output
                  (do (dbg-s options
                             :guide
                             (color-str "fzprint-guide: === skip guided newline"
                                        :bright-red)
                             "since we had unguided-newline-out on last output")
                      (recur cur-zloc
                             cur-index
                             (next guide-seq)
                             element-index
                             (inc index)
                             ; Forget spaces on every guided :newline
                             (dissoc param-map :spaces :align-key)
                             mark-map
                             [previous-newline? previous-guided-newline?
                              ;unguided-newline-out?
                              nil previous-comment?]
                             options
                             out))
                (= first-guide-seq :mark)
                  ; put the cur-ind plus spaces into the mark map with
                  ; key from the next guide-seq
                  (do (dbg-s
                        options
                        :guide
                        (color-str "fzprint-guide: === :mark key:" :bright-red)
                        (first (next guide-seq))
                        "value:"
                        (+ (:cur-ind param-map) (or (:spaces param-map) 1)))
                      (recur cur-zloc
                             cur-index
                             ; skip an extra to account for the mark key
                             (nnext guide-seq)
                             element-index
                             (inc index)
                             param-map
                             (assoc mark-map
                               (first (next guide-seq))
                                 (+ (:cur-ind param-map)
                                    (or (:spaces param-map) 1)))
                             previous-data
                             options
                             out))
                (= first-guide-seq :mark-at)
                  ; put the cur-ind plus spaces into the mark map with
                  ; key from the next guide-seq
                  (do (dbg-s options
                             :guide
                             (color-str "fzprint-guide: === :mark-at key:"
                                        :bright-red)
                             (first (next guide-seq))
                             "value:"
                             (+ (:one-line-ind param-map)
                                (first (nnext guide-seq))))
                      (recur cur-zloc
                             cur-index
                             ; skip two to account for the mark key and the
                             ; spaces count
                             (nthnext guide-seq 3)
                             element-index
                             (inc index)
                             param-map
                             (assoc mark-map
                               (first (next guide-seq))
                                 (+ (:one-line-ind param-map)
                                    (first (nnext guide-seq))))
                             previous-data
                             options
                             out))
                (= first-guide-seq :mark-at-indent)
                  ; put the cur-ind plus spaces into the mark map with
                  ; key from the next guide-seq
                  (do (dbg-s options
                             :guide
                             (color-str
                               "fzprint-guide: === :mark-at-indent key:"
                               :bright-red)
                             (first (next guide-seq))
                             "value:"
                             (+ (:ind param-map)
                                (:indent param-map)
                                (first (nnext guide-seq))))
                      (recur cur-zloc
                             cur-index
                             ; skip two to account for the mark key and the
                             ; spaces count
                             (nthnext guide-seq 3)
                             element-index
                             (inc index)
                             param-map
                             (assoc mark-map
                               (first (next guide-seq))
                                 (+ (:indent param-map)
                                    (:ind param-map)
                                    (first (nnext guide-seq))))
                             previous-data
                             options
                             out))
                (= first-guide-seq :spaces)
                  ; save the spaces for when we actually do output
                  ; note that spaces after a newline are beyond the indent
                  ; also note that spaces are additive, they do not replace
                  ; any previous spaces
                  (do (dbg-s options
                             :guide
                             (color-str "fzprint-guide: === spaces" :bright-red)
                             (first (next guide-seq)))
                      (recur cur-zloc
                             cur-index
                             ; skip an extra to account for the spaces count
                             (nnext guide-seq)
                             element-index
                             (inc index)
                             (assoc param-map
                               :spaces (+ (first (next guide-seq))
                                          (or (:spaces param-map) 0)))
                             mark-map
                             previous-data
                             options
                             out))
                (= first-guide-seq :indent)
                  ; save a new indent value in param-map
                  (do (dbg-s options
                             :guide
                             "fzprint-guide: === :indent"
                             (first (next guide-seq)))
                      (recur cur-zloc
                             cur-index
                             ; skip an extra to account for the indent value
                             (nnext guide-seq)
                             element-index
                             (inc index)
                             (assoc param-map :indent (first (next guide-seq)))
                             mark-map
                             previous-data
                             options
                             out))
                (= first-guide-seq :indent-here)
                  ; save a new indent value in param-map
                  (do
                    (dbg-s options
                           :guide "fzprint-guide: === :indent-here"
                           "align-key:" (:align-key param-map)
                           "align-ind:" (when (:align-key param-map)
                                          (get mark-map (:align-key param-map)))
                           "cur-ind:" (:cur-ind param-map)
                           "spaces:" (:spaces param-map))
                    (recur cur-zloc
                           cur-index
                           (next guide-seq)
                           element-index
                           (inc index)
                           ;   (assoc param-map :indent (first (next
                           ;   guide-seq)))
                           (assoc param-map
                             :indent
                               ; This needs to be the alignment if we
                               ; have some, or the cur-ind + spaces
                               ; if we have some.  Not always adding it to
                               ; spaces.  Note that the align-key value
                               ; from mark-map - cur-ind is the number
                               ; of spaces we need, not the indent.
                               ; The indent would be the align value - the
                               ; ind.
                               (guide-here param-map mark-map)) ; assoc
                                                                ; :indent
                           mark-map
                           previous-data
                           options
                           out))
                (= first-guide-seq :indent-align)
                  ; save a new indent value in param-map
                  (let [align-key (first (next guide-seq))
                        _ (dbg-s options
                                 :guide (color-str
                                          "fzprint-guide: === :indent-align"
                                          :bright-red)
                                 "key:" align-key
                                 "value:" (get mark-map align-key)
                                 "cur-ind:" (:cur-ind param-map))]
                    (recur cur-zloc
                           cur-index
                           ; skip an extra to account for the align-key
                           (nnext guide-seq)
                           element-index
                           (inc index)
                           (assoc param-map
                             :indent
                               ; Note that the align-key value from
                               ; mark-map - cur-ind is the number of spaces
                               ; we need, not the indent.  The indent
                               ; would be the align value - the ind.
                               (or (when (get mark-map align-key)
                                     (max 0
                                          (- (get mark-map align-key)
                                             (:ind param-map))))
                                   ; If no align-key, don't change
                                   ; the indent
                                   (:indent param-map))) ; assoc :indent
                           mark-map
                           previous-data
                           options
                           out))
                (= first-guide-seq :indent-reset)
                  ; put the indent back where it was originally
                  (do (dbg-s options
                             :guide
                             (color-str "fzprint-guide: === :indent-reset"
                                        :bright-red))
                      (recur cur-zloc
                             cur-index
                             (next guide-seq)
                             element-index
                             (inc index)
                             (assoc param-map :indent local-indent)
                             mark-map
                             previous-data
                             options
                             out))
                (= first-guide-seq :options)
                  ; Start using an updated options map
                  (let [[merged-option-map _] (internal-config-and-validate
                                                options
                                                (first (next guide-seq))
                                                "fzprint-guide: options:"
                                                ; Don't validate because these
                                                ; were either validated on
                                                ; return from option-fn or
                                                ; when read in the options map
                                                nil)
                        _ (dbg-s options
                                 :guide
                                 (color-str "fzprint-guide: === :options"
                                            :bright-red)
                                 (first (next guide-seq)))]
                    (recur cur-zloc
                           cur-index
                           ; skip an extra to account for the options map
                           (nnext guide-seq)
                           element-index
                           (inc index)
                           param-map
                           mark-map
                           previous-data
                           merged-option-map
                           out))
                (= first-guide-seq :options-reset)
                  ; put the options map back to where it was when we started
                  (do (dbg-s options
                             :guide
                             (color-str "fzprint-guide: === :options-reset"
                                        :bright-red))
                      (recur cur-zloc
                             cur-index
                             (next guide-seq)
                             element-index
                             (inc index)
                             param-map
                             mark-map
                             previous-data
                             (:initial-options param-map)
                             out))
                (= first-guide-seq :align)
                  ; Set up for an alignment on the next :element
                  ; Find the align-key, and save it in the param-map
                  (let [align-key (first (next guide-seq))
                        _ (dbg-s options
                                 :guide (color-str "fzprint-guide: === :align"
                                                   :bright-red)
                                 "key:" align-key
                                 "value:" (get mark-map align-key))]
                    (recur cur-zloc
                           cur-index
                           ; skip an extra to account for the align-key
                           (nnext guide-seq)
                           element-index
                           (inc index)
                           ; remember the align-key, forget spaces when
                           ; we get :align
                           (assoc (dissoc param-map :spaces)
                             :align-key align-key)
                           mark-map
                           previous-data
                           options
                           out))
                (:guided-newline-count param-map)
                  ; we are currently counting newlines, see if we have more
                  ; or if we should output any excess that we have counted
                  (if (or (= first-guide-seq :newline)
                          (= first-guide-seq :newline-force))
                    ; we have another :newline, just count it
                    (do (dbg-s options
                               :guide
                               (color-str
                                 "fzprint-guide: === counting guided newlines:"
                                 :bright-red)
                               (inc (:guided-newline-count param-map)))
                        (recur cur-zloc
                               cur-index
                               (next guide-seq)
                               element-index
                               (inc index)
                               (dissoc (assoc param-map
                                         :guided-newline-count
                                           ; Only count them if we have real
                                           ; cur-zloc or this is a
                                           ; :newline-force
                                           (if (or cur-zloc
                                                   (= first-guide-seq
                                                      :newline-force))
                                             (inc (:guided-newline-count
                                                    param-map))
                                             (:guided-newline-count param-map))
                                         :cur-ind (+ (:indent param-map)
                                                     (:ind param-map)))
                                 :spaces
                                 :align-key)
                               mark-map
                               previous-data
                               options
                               out))
                    ; we are counting guided-newlines, and we have found a guide
                    ; that is not a :newline, so we need to determine the number
                    ; of excess guided-newlines we have, by counting the actual
                    ; newlines and comparing them
                    (let
                      [_ (dbg-s
                           options
                           :guide
                             (color-str
                               "fzprint-guide: === finished counting newlines"
                               :bright-red)
                           ":guided-newline-count:" (:guided-newline-count
                                                      param-map))
                       comment-and-newline-count (zcount-comments-and-newlines
                                                   cur-zloc)
                       guided-newline-count (:guided-newline-count param-map)
                       excess-guided-newline-count
                         (max 0
                              (- guided-newline-count
                                 comment-and-newline-count))
                       param-map (dissoc param-map :guided-newline-count)
                       ; Since we expect a newline or equivalent sometime
                       ; soon, as in next, let's fix up the cur-ind to
                       ; represent that.
                       param-map (assoc param-map
                                   :cur-ind (+ (:indent param-map)
                                               (:ind param-map)))
                       param-map (if (pos? excess-guided-newline-count)
                                   (assoc param-map
                                     :excess-guided-newline-count
                                       excess-guided-newline-count)
                                   param-map)
                       ; If we have excess-guided-newline-count then do
                       ; output right now, fake a guided newline to make
                       ; this happen
                       [new-param-map new-previous-data new-out]
                         (if (pos? excess-guided-newline-count)
                           (do
                             (dbg-s
                               options
                               :guide
                               (color-str
                                 "fzprint-guide === excess-guided-newline-count:"
                                 :bright-red)
                               excess-guided-newline-count)
                             (guided-output
                               caller
                               options
                               nil ; zloc
                               nil ; rightmost-zloc?
                               :newline ;guide-seq
                               cur-index
                               guide-seq
                               element-index
                               index
                               param-map
                               mark-map
                               previous-data
                               out))
                           [param-map previous-data out])]
                      (recur cur-zloc
                             cur-index
                             ; don't move forward, as we were dealing with
                             ; the end of a previous set of :newlines
                             guide-seq
                             element-index
                             (inc index)
                             new-param-map
                             mark-map
                             new-previous-data
                             options
                             new-out)))
                (or (= first-guide-seq :newline)
                    (= first-guide-seq :newline-force))
                  ; start counting guided newlines
                  (do (dbg-s
                        options
                        :guide
                        (color-str
                          "fzprint-guide: === start counting guided newlines"
                          :bright-red))
                      (recur cur-zloc
                             cur-index
                             (next guide-seq)
                             element-index
                             (inc index)
                             ; Forget spaces on every guided :newline
                             ; TODO: PROTOTYPE
                             (if (or (not (empty? cur-zloc))
                                     (= first-guide-seq :newline-force))
                               (dissoc (assoc param-map
                                         :guided-newline-count 1
                                         :cur-ind (+ (:ind param-map)
                                                     (:indent param-map)))
                                 :spaces
                                 :align-key)
                               param-map)
                             mark-map
                             previous-data
                             options
                             out))
                ; :pair-begin has to come after the newline handling
                ; :group-begin has to come after the newline handling
                (= first-guide-seq :group-begin)
                  ; create an empty seq to accumulate zlocs
                  (do (dbg-s options
                             :guide
                             (color-str
                               "fzprint-guide: === start accumulating a group"
                               :bright-red))
                      (recur cur-zloc
                             cur-index
                             (next guide-seq)
                             element-index
                             (inc index)
                             (assoc param-map
                               :group-seq []
                               :grouping? true)
                             mark-map
                             previous-data
                             options
                             out))
                ; :element-newline-best-* also has to come after
                ; newline handling
                (or (= first-guide-seq :element-newline-best-*)
                    (= first-guide-seq :element-newline-extend-*)
                    (= first-guide-seq :element-wrap-flow-*)
                    (= first-guide-seq :element-binding-*)
                    (= first-guide-seq :element-pair-*))
                  ; Consider everything else for a -* command.
                  ; If we ree already accumulating a group, that's ok (though
                  ; unnecessary).
                  (let [_ (dbg-s options
                                 :guide (color-str (str "fzprint-guide: === "
                                                        first-guide-seq)
                                                   :bright-red)
                                 "rightcnt:" (:rightcnt options))
                        ; Is this the last thing in guide-seq?
                        _ (when-not (empty? (next guide-seq))
                            (throw (#?(:clj Exception.
                                       :cljs js/Error.)
                                    (str
                                      first-guide-seq
                                      "not the last command in the guide!"))))
                        ; Get a place to put the pairs
                        param-map (if (:group-seq param-map)
                                    param-map
                                    (assoc param-map :group-seq []))
                        ; Pick up all of the remaining cur-zloc
                        param-map (assoc param-map
                                    :group-seq (concat (:group-seq param-map)
                                                       cur-zloc))
                        _ (dbg-s options
                                 :guide
                                 "fzprint-guide: === " first-guide-seq
                                 " group-seq:" (pr-str (map zstring
                                                         (:group-seq
                                                           param-map))))]
                    (if (empty? (:group-seq param-map))
                      ; Nothing to do, nothing left in the zloc-seq!
                      (recur cur-zloc
                             cur-index
                             (next guide-seq)
                             element-index
                             (inc index)
                             (dissoc param-map :group-seq :grouping?)
                             mark-map
                             previous-data
                             options
                             out)
                      ; we have zlocs to process
                      (let [[new-param-map new-previous-data new-out]
                              (guided-output caller
                                             options
                                             nil ; zloc
                                             true ; rightmost-zloc?
                                             first-guide-seq
                                             cur-index
                                             guide-seq
                                             element-index
                                             index
                                             param-map
                                             mark-map
                                             previous-data
                                             out)]
                        (recur nil ; we finished them off
                               (+ cur-index (count cur-zloc)) ; same here
                               (next guide-seq)
                               element-index
                               (inc index)
                               (dissoc new-param-map :group-seq :grouping)
                               mark-map
                               new-previous-data
                               options
                               new-out))))
                ;
                ;  Start looking at cur-seq
                ;
                ; At this point, the only guide left is :element,
                ; soo we must be sitting on it.
                ; All of the others are (and must be) processed above.
                ;
                ; Anything left in cur-zloc?
                (and (nil? cur-zloc) (empty? (:group-seq param-map)))
                  ; No, nothing left in cur-zloc.  This isn't a failure,
                  ; because we might be accumulating pairs or just have
                  ; made too long a guide.  Just keep going and eat up
                  ; the guide-seq.
                  (do (dbg-s options
                             :guide
                             (color-str "fzprint-guide: === ran out of cur-zloc"
                                        :bright-red))
                      (recur (next cur-zloc)
                             (inc cur-index)
                             (next guide-seq)
                             element-index
                             (inc index)
                             param-map
                             mark-map
                             previous-data
                             options
                             out))
                (or comment? comment-inline? uneval? next-newline?)
                  ; Do unguided output, moving cur-zloc without changing
                  ; guide-seq
                  (let
                    [_ (dbg-s
                         options
                         :guide
                           (color-str
                             "fzprint-guide: === process comments, newlines, uneval"
                             :bright-red)
                         "comment?" comment?
                         "comment-inline?" comment-inline?
                         "uneval?" uneval?
                         "next-newline?" next-newline?)
                     [new-param-map new-previous-data new-out]
                       (guided-output caller
                                      options
                                      (first cur-zloc)
                                      (empty? (next cur-zloc))
                                      nil ; unknown
                                      cur-index
                                      guide-seq
                                      element-index
                                      index
                                      param-map
                                      mark-map
                                      previous-data
                                      out)]
                    (recur (next cur-zloc)
                           (inc cur-index)
                           ; We may have used information from guide-seq, but
                           ; we didn't "consume" it yet.
                           guide-seq
                           element-index
                           (inc index)
                           new-param-map
                           mark-map
                           new-previous-data
                           options
                           new-out))
                (or (= first-guide-seq :element)
                    (= first-guide-seq :element-guide)
                    (= first-guide-seq :element-best)
                    (= first-guide-seq :element-extend)
                    (= first-guide-seq :element-best-first)
                    (= first-guide-seq :element-extend-first)
                    (= first-guide-seq :element-best-*)
                    (= first-guide-seq :element-extend-*)
                    (= first-guide-seq :element-*)
                    (= first-guide-seq :element-binding-vec))
                  ; Do basic guided output, moving both cur-seq and guide-seq
                  (let [_ (dbg-s options
                                 :guide
                                 (color-str (str "fzprint-guide: === "
                                                 first-guide-seq)
                                            :bright-red))
                        _ (when (and (or (= first-guide-seq :element-*)
                                         (= first-guide-seq :element-best-*)
                                         (= first-guide-seq :element-extend-*))
                                     (not (empty? (next guide-seq))))
                            (throw (#?(:clj Exception.
                                       :cljs js/Error.)
                                    (str
                                      first-guide-seq
                                      " is not the last command in guide!"))))
                        [new-param-map new-previous-data new-out]
                          (guided-output caller
                                         options
                                         (first cur-zloc)
                                         (empty? (next cur-zloc))
                                         first-guide-seq
                                         cur-index
                                         guide-seq
                                         element-index
                                         index
                                         param-map
                                         mark-map
                                         previous-data
                                         out)]
                    (recur (next cur-zloc)
                           (inc cur-index)
                           (cond (= first-guide-seq :element-guide)
                                   ; Make sure to consume the guide too
                                   (nnext guide-seq)
                                 (or (= first-guide-seq :element-*)
                                     (= first-guide-seq :element-best-*)
                                     (= first-guide-seq :element-extend-*))
                                   ; We just stay "stuck" on last command
                                   ; in guide-seq for :element-* and
                                   ; :element-best-*
                                   guide-seq
                                 :else (next guide-seq))
                           (inc element-index)
                           (inc index)
                           ; forget spaces or alignment after :element
                           (dissoc new-param-map :spaces :align-key)
                           mark-map
                           new-previous-data
                           options
                           new-out))
                (or (= first-guide-seq :element-newline-best-group)
                    (= first-guide-seq :element-newline-extend-group)
                    (= first-guide-seq :element-wrap-flow-group)
                    (= first-guide-seq :element-pair-group)
                    (= first-guide-seq :element-binding-group))
                  ; Operate on previously grouped elements
                  (let [_ (dbg-s options
                                 :guide
                                 (color-str (str "fzprint-guide: === "
                                                 first-guide-seq)
                                            :bright-red))
                        [new-param-map new-previous-data new-out]
                          (guided-output caller
                                         options
                                         ; Use previously created group
                                         nil
                                         (empty? cur-zloc)
                                         first-guide-seq
                                         cur-index
                                         guide-seq
                                         element-index
                                         index
                                         param-map
                                         mark-map
                                         previous-data
                                         out)]
                    (recur cur-zloc
                           cur-index
                           (next guide-seq)
                           element-index
                           (inc index)
                           ; forget spaces or alignment after :element
                           ; also forget the group, a we have used it up
                           (dissoc new-param-map :spaces :align-key :group-seq)
                           mark-map
                           new-previous-data
                           options
                           new-out))
                ;
                ; Something we didn't expect is going on here
                ; TODO: Fix this for realistic error reporting
                ;
                :else (throw (#?(:clj Exception.
                                 :cljs js/Error.)
                              (str "Unknown values: guide-seq: '"
                                   first-guide-seq
                                   "' "
                                   "\ncur-zloc:"
                                   (zstring (first cur-zloc)))))))))))))

(defn remove-nl
  "Remove any [_ _ :newline] from the seq."
  [coll]
  (remove #(= (nth (first %) 2) :newline) coll))

(defn comment-in-zloc-seq?
  "If there are any comments at the top level of the zloc-seq, return true,
  else nil."
  [zloc-seq]
  (reduce #(when (= (ztag %2) :comment) (reduced true)) false zloc-seq))

(defn fzprint-vec*
  "Print basic stuff like a vector or a set or an array.  Several options 
  for how to print them."
  [caller l-str r-str
   {:keys [rightcnt in-code?],
    {:keys [wrap-coll? wrap? binding? option-fn-first option-fn sort?
            sort-in-code? fn-format indent force-nl?]}
      caller,
    :as options} ind zloc]
  (dbg options
       "fzprint-vec* ind:" ind
       "indent:" indent
       "caller:" caller
       "ztag:" (ztag zloc))
  (if (= (:format options) :off)
    (fzprint-noformat l-str r-str options zloc)
    (if (and binding? (= (:depth options) 1))
      (fzprint-binding-vec options ind zloc)
      (let [[respect-nl? respect-bl? indent-only?]
              (get-respect-indent options caller :vector)
            #_#_options
              (assoc options
                :call-stack (conj
                              (:call-stack options)
                              {:tag (ztag zloc), :caller caller, :zloc zloc}))
            options (if (and (= caller :prefix-tags) (= :quote (ztag zloc)))
                      (assoc options :quote? true)
                      options)
            [options new-options]
              (if option-fn-first
                (call-option-fn-first caller options option-fn-first zloc)
                [options nil])
            _ (when option-fn-first
                (dbg-pr options
                        "fzprint-vec* option-fn-first new options"
                        new-options))
            ; Fix this to handle new returns.
            [options zloc l-str r-str]
              (if option-fn
                (call-option-fn caller options option-fn zloc l-str r-str)
                [options #_nil zloc l-str r-str])
            ; Do this after call-option-fn in case anything changed.
            l-str-len (count l-str)
            l-str-vec [[l-str (zcolor-map options l-str) :left]]
            r-str-vec
              (rstr-vec options (+ ind (max 0 (dec l-str-len))) zloc r-str)
            len (zcount zloc)
            #_(when option-fn
                (dbg-pr options
                        "fzprint-vec* option-fn new options"
                        new-options))
            {{:keys [wrap-coll? wrap? binding? respect-bl? respect-nl? sort?
                     fn-format sort-in-code? indent indent-only?]}
               caller,
             :as options}
              options
            ; If we are doing indent-only, we aren't doing guides
            guide (when (not indent-only?)
                    (or (:guide options) (guide-debug caller options)))
            options (dissoc options :guide)
            _ (when guide (dbg-pr options "fzprint-vec* guide:" guide))
            zloc-seq (cond (or respect-nl? indent-only?) (zmap-w-nl identity
                                                                    zloc)
                           respect-bl? (zmap-w-bl identity zloc)
                           :else (zmap identity zloc))]
        (cond
          guide (concat-no-nil l-str-vec
                               (fzprint-guide
                                 ; TODO: FIX THIS
                                 :vector
                                 options
                                 ; this is where we are w/out any indent
                                 ind
                                 ; this is where we are with the l-str
                                 (+ l-str-len ind)
                                 indent
                                 guide
                                 zloc-seq)
                               r-str-vec)
          fn-format
            ; If we have fn-format, move immediately to fzprint-list* and
            ; let :vector-fn configuration drive what we do (e.g.,
            ; indent-only, ; or whatever).  That is to say that
            ; :indent-only? in :vector doesn't override option-fn-first
            ; or option-fn
            (fzprint-list* :vector-fn
                           l-str
                           r-str
                           ; This could (dissoc options [:fn-format
                           ; :vector])
                           ;    (assoc-in
                           (assoc options :fn-style fn-format)
                           ;    [:vector :fn-format] nil)
                           ind
                           zloc)
          :else
            (let [; If sort? is true, then respect-nl? and respect-bl? make
                  ; no sense.  And vice versa.
                  ; If respect-nl? or respect-bl?, then no sort.
                  ; If we have comments, then no sort, because we'll lose
                  ; the comment context.
                  indent (or indent (count l-str))
                  new-ind (if indent-only? ind (+ indent ind))
                  _ (dbg-pr options
                            "fzprint-vec*:" (zstring zloc)
                            "new-ind:" new-ind)
                  zloc-seq (if (and sort?
                                    (if in-code? sort-in-code? true)
                                    (not (comment-in-zloc-seq? zloc-seq))
                                    (not respect-nl?)
                                    (not respect-bl?)
                                    (not indent-only?))
                             (order-out caller options identity zloc-seq)
                             zloc-seq)
                  coll-print (if (zero? len)
                               [[["" :none :whitespace 17]]]
                               (fzprint-seq options new-ind zloc-seq))
                  _ (dbg-pr options "fzprint-vec*: coll-print:" coll-print)
                  ; If we got any nils from fzprint-seq
                  ; and we were in :one-line mode then
                  ; give up -- it didn't fit on one line.
                  coll-print (if-not (contains-nil? coll-print) coll-print)
                  one-line (when coll-print
                             ; should not be necessary with contains-nil? above
                             (apply concat-no-nil
                               (interpose [[" " :none :whitespace 18]]
                                 ; This causes single line things to
                                 ; also respect-nl when it is enabled.
                                 ; Could be separately controlled
                                 ; instead of with :respect-nl? if desired.
                                 (if (or respect-nl? :respect-bl? indent-only?)
                                   coll-print
                                   (remove-nl coll-print)))))
                  _ (log-lines options "fzprint-vec*:" new-ind one-line)
                  _ (dbg-pr options
                            "fzprint-vec*: new-ind:" new-ind
                            "force-nl?" force-nl?
                            "one-line:" one-line)
                  one-line-lines (style-lines options new-ind one-line)]
              (if (zero? len)
                (concat-no-nil l-str-vec r-str-vec)
                (when one-line-lines
                  (if (and (not force-nl?)
                           (fzfit-one-line options one-line-lines))
                    (concat-no-nil l-str-vec one-line r-str-vec)
                    (if indent-only?
                      ; Indent Only
                      (concat-no-nil l-str-vec
                                     (indent-zmap caller
                                                  options
                                                  ind
                                                  ; actual-ind
                                                  (+ ind l-str-len)
                                                  coll-print
                                                  indent)
                                     r-str-vec)
                      ; Regular Pprocessing
                      (if (or (and (not wrap-coll?)
                                   (any-zcoll? options new-ind zloc))
                              (not wrap?)
                              force-nl?)
                        (concat-no-nil l-str-vec
                                       (apply concat-no-nil
                                         (precede-w-nl options
                                                       new-ind
                                                       coll-print
                                                       :no-nl-first
                                                       (:nl-count (caller
                                                                    options))))
                                       r-str-vec)
                        ; Since there are either no collections in this
                        ; vector
                        ; or set or whatever, or if there are, it is ok to
                        ; wrap them, print it wrapped on the same line as
                        ; much as possible:
                        ;           [a b c d e f
                        ;            g h i j]
                        (concat-no-nil l-str-vec
                                       (do (dbg-pr
                                             options
                                             "fzprint-vec*: wrap coll-print:"
                                             coll-print)
                                           (wrap-zmap caller
                                                      options
                                                      new-ind
                                                      new-ind
                                                      coll-print))
                                       r-str-vec))))))))))))

(defn fzprint-vec
  [options ind zloc]
  (fzprint-vec* :vector "[" "]" (rightmost options) ind zloc))

(defn fzprint-array
  [options ind zloc]
  (fzprint-vec* :array "[" "]" (rightmost options) ind zloc))

(defn fzprint-set
  "Pretty print and focus style a :set element."
  [options ind zloc]
  (fzprint-vec* :set "#{" "}" (rightmost options) ind zloc))

; not clear transient helps here
(defn interpose-either
  "Do the same as interpose, but different seps depending on pred?.
  If sep-nil is nil, then when pred? is false we don't interpose
  anything!"
  [sep-true sep-nil pred? coll]
  (loop [coll coll
         out (transient [])
         interpose? nil]
    (if (empty? coll)
      (persistent! out)
      (recur (next coll)
             (if interpose?
               (conj-it! out sep-true (first coll))
               (if (or (zero? (count out)) (nil? sep-nil))
                 (conj! out (first coll))
                 (conj-it! out sep-nil (first coll))))
             (pred? (first coll))))))

(defn precede-w-nl
  "Move through a sequence of style vecs and ensure that at least
  one newline (actually an indent) appears before each element.  If
  a newline in the style-vecs is where we wanted one, well and good.
  Comments are now not recognized as different, increasing our
  appreciation of diversity.  If not-first? is truthy, then don't
  put a newline before the first element.  nl-count is the count
  of newlines after the first.  If it is nil, assume 1.  It may be
  a vector of newlines, and the next element of the vector is used after
  any non-comment non-newline is processed.  The last element of the
  vector is used once it runs out."
  [options ind coll not-first? nl-count]
  (dbg-pr options
          "precede-w-nl: (count coll)" (count coll)
          "not-first?" not-first?
          "nl-count:" nl-count)
  (let [nl-count (or nl-count 1)
        ; If it isn't a vector, make it one
        nl-count (if (vector? nl-count) nl-count [nl-count])
        nl-count (into [1] nl-count)]
    (loop [coll coll
           ind-seq (if (coll? ind) ind (vector ind))
           out (transient [])
           added-nl? not-first?
           ; We only do one nl at the beginning, regardless of nl-count
           num-nl (if not-first? (first nl-count) (dec (first nl-count)))
           nl-count-vec nl-count]
      (if (empty? coll)
        (let [result (persistent! out)
              _ (dbg-pr options "precede-w-nl: exit:" result)
              ; If the thing before the last was a comment, then remove the
              ; last thing (which must be a newline, though we didn't put
              ; it there)
              previous-element-index (- (count result) 2)
              previous-type (when (not (neg? previous-element-index))
                              (nth (first (nth result previous-element-index))
                                   2))]
          result)
        (let [[[s color what] :as element] (first coll)
              ; This element may have many things in it, or sometimes
              ; just one.
              ;
              ; I believe that if the first thing is a newline then they
              ; must all be newlines.  We could check the last, or all of
              ; them here, I suppose.  But these have to come from
              ; fzprint-newline, to the best of my knowledge, and that is
              ; how it works.
              indent (first ind-seq)
              newline? (= what :newline)
              ; Don't pick up embedded comments
              comment? (= what :comment)
              comment-inline? (= what :comment-inline)
              ; Let's make sure about the last
              last-what (nth (last element) 2)]
          (dbg-pr options
                  "precede-w-nl: element:" element
                  "added-nl?" added-nl?
                  "num-nl:" num-nl
                  "comment?" comment?)
          (recur
            (next coll)
            ; Move along ind-seq until we reach the last one, then just
            ; keep using the last one.
            (if-let [next-ind (next ind-seq)]
              next-ind
              ind-seq)
            (if newline?
              ; It is a :newline, so just use it as it is.
              ; Except if the next thing out is also a newline, we'll have
              ; trailing spaces after this newline, which we need to prevent.
              ; The next thing coming along could be a newline, or we could be
              ; going to output a newline ourselves.  Either means that we
              ; should not be doing spaces after a newline now.
              (let [next-coll (next coll)]
                (if (empty? next-coll)
                  (conj! out element)
                  (let [[[_ _ next-what]] (first next-coll)
                        next-nl-count-vec nl-count-vec]
                    (dbg-pr options
                            "precede-w-nl next-what:" next-what
                            "(inc num-nl):" (inc num-nl)
                            "(first next-nl-count-vec):" (first
                                                           next-nl-count-vec))
                    (if (or (= next-what :newline)
                            (< (inc num-nl) (first next-nl-count-vec)))
                      ; don't put out a newline with spaces before another
                      ; newline -- note that what == :newline here
                      (conj! out [["\n" color :newline 3]])
                      (conj! out element)))))
              ; It is not a :newline, so we want to make sure we have
              ; the proper number of newlines in front of it.
              (if (>= num-nl (first nl-count-vec))
                ; We already have enough newlines in front of it
                (conj! out element)
                ; We need some newlines and the element
                (conj-it! out
                          [[(str (apply str
                                   (repeat (- (first nl-count-vec) num-nl)
                                           "\n"))
                                 (blanks indent)) :none :indent 28]]
                          element)))
            ; Is there a newline as the last thing we just did?
            ; Two ways for that to happen. (which are???)
            newline?
            ; Count the newlines we have output
            (if newline?
              (inc num-nl)
              (if comment? (dec (first nl-count-vec)) 0))
            ; Move along nl-count-vec until we reach the last one,
            ; then just keep using the last one.  Don't move if it is a
            ; newline or either type of comment.
            (if (or newline? comment? comment-inline?)
              nl-count-vec
              (if (next nl-count-vec) (next nl-count-vec) nl-count-vec))))))))

(defn count-newline-types
  "Analyze a style-vec which contains only newlines, and return the count 
  of newlines in the style vec.  We assume that each :newline style-vec 
  contains one newline (i.e., it was generated by fzprint-newlines)."
  [newline-style-vec]
  (let [count-of-types (count (distinct (map #(nth % 2) newline-style-vec)))]
    #_(prn "count-newline-types: " count-of-types
           " style-vec:" newline-style-vec)
    (when (or (not= count-of-types 1)
              (not= (nth (first newline-style-vec) 2) :newline))
      (throw
        (#?(:clj Exception.
            :cljs js/Error.)
         (str "Internal Error!  Please submit an issue with an example"
                " of how to reproduce this error!"
              " count-newline-types: more than one type or wrong type! count: "
                count-of-types
              " style-vec: " newline-style-vec))))
    (count newline-style-vec)))

(defn count-right-blanks
  "Count the number of blanks at the right end of a string."
  [s]
  (loop [i (count s)]
    (if (neg? i)
      (count s)
      (if (clojure.string/ends-with? (subs s 0 i) " ")
        (recur (dec i))
        (- (count s) i)))))

(defn trimr-blanks
  "Trim only blanks off the right end of a string."
  [s]
  (loop [i (count s)]
    (if (neg? i)
      ""
      (if (clojure.string/ends-with? (subs s 0 i) " ")
        (recur (dec i))
        (subs s 0 i)))))

(defn repeat-style-vec-nl
  "Given a count n, and style vec that ends with a newline and an associated
  indent of some number of spaces, return a sequence of n of those style vecs
  but remove spaces from all but the last of them."
  [n style-vec]
  (let [no-space-n (max (dec n) 0)]
    (if (zero? no-space-n)
      style-vec
      (let [[s color what] (last style-vec)
            no-space-element [(trimr-blanks s) color what]
            no-space-style-vec
              (into [] (concat (butlast style-vec) no-space-element))]
        (into [] (concat (repeat no-space-n no-space-style-vec) style-vec))))))

(defn trimr-blanks-element
  "Given an element, trim the blanks out of the string."
  [[s color what]]
  [(trimr-blanks s) color what])

(defn trimr-blanks-style-vec
  "Given a style-vec, trim the blanks out of each element."
  [style-vec]
  (mapv trimr-blanks-element style-vec))

(defn repeat-element-nl
  "Given a count n, and single element from a style-vec which
  contains a newline and an indent of some number of spaces, return
  a sequence of n of those style vecs but remove spaces from all
  but the last of them.  This is so that we don't have trailing spaces
  on lines."
  [n element]
  #_(prn "repeat-element-nl: n:" n "element:" element)
  (let [no-space-n (max (dec n) 0)]
    (if (zero? no-space-n)
      [element]
      (let [[s color what] element
            no-space-element [(trimr-blanks s) color what]
            result
              (into [] (concat (repeat no-space-n no-space-element) [element]))]
        #_(prn "repeat-element-nl: result:" result)
        result))))

(defn next-non-comment-nl
  "Given a coll of [hangflow style-vec] pairs, return the 
  [hangflow style-vec] pair where the style-vec is not a 
  :comment, :comment-inline, :newline or :indent."
  [coll]
  (loop [coll coll]
    (if (empty? coll)
      nil
      (let [[_ style-vec] (first coll)
            [_ _ what] (first style-vec)]
        (if (or (= what :comment)
                (= what :comment-inline)
                (= what :indent)
                (= what :newline))
          (recur (next coll))
          (first coll))))))

; transient helped a lot here

(defn interpose-either-nl-hf
  "Do very specialized interpose, but different seps depending on pred-fn
  return and nl-separator? and nl-separator-all?. This assumes that 
  sep-* does one line, and sep-*-nl does two lines. coll is
  a series of [[:flow [['\n  ' :none :newline 2]]] 
               [:flow [['ZprintType' :black :element] ...]]] fragments from
  fzprint-map-two-up."
  [sep-comma sep-comma-nl sep sep-nl
   {:keys [nl-separator? nl-separator-all?], :as suboptions} ;nl-separator?
   comma? coll]
  #_(prn "ienf: sep:" sep "comma?" comma? "coll:" coll)
  (loop [coll coll
         out (transient [])
         previous-needs-comma? nil
         add-nl? nil
         first? true
         newline-count 0]
    (if (empty? coll)
      (apply concat-no-nil
        (persistent!
          ; Handle final newlines, if any
          (if (zero? newline-count)
            out
            (conj-it! out (repeat-element-nl newline-count (first sep))))))
      (let [[hangflow style-vec] (first coll)
            [_ _ what] (first style-vec)]
        #_(prn "====>>>>>>>> interpose-either-nl-hf: style-vec:" style-vec)
        (cond
          (= what :newline)
            ; We have one or more newlines.  We are going to keep
            ; track of what we've seen and will actually output things
            ; later, when we know what we actually have.
            ; For now, just increase the count and don't do anything
            ; else.  If we have anything in addition to newlines, we have a
            ; problem because we will lose them as the style-vec
            ; goes away, which is why count-newline-types will throw
            ; an exception if it encounters this.
            (do #_(prn "interpose-either-nl-hf: hangflow: " hangflow)
                (recur (next coll)
                       out
                       previous-needs-comma?
                       add-nl?
                       first?
                       (+ newline-count (count-newline-types style-vec))))
          :else
            ; We have a normal style-vec that we will process.  This one
            ; may have plenty of newlines, but there isn't one first.
            ; But we might have seen plenty of newlines
            ; before this -- or not.
            (let [[interpose-style-vec interpose-count]
                    (if previous-needs-comma?
                      (if add-nl? [sep-comma-nl 2] [sep-comma 1])
                      (if add-nl? [sep-nl 2] [sep 1]))
                  ; if first? we assume that we get one newline from caller
                  interpose-count (if first? 1 interpose-count)
                  addtl-nl-needed (max (- newline-count interpose-count) 0)]
              ; Here is where we need to figure out if two newlines are
              ; coming out in order, and ensure that the first ones don't
              ; have any spaces after them.
              #_(prn "ienf: interpose-style-vec:" interpose-style-vec)
              (recur
                (next coll)
                (if first?
                  (if (zero? addtl-nl-needed)
                    (conj! out style-vec)
                    (conj-it! out
                              (repeat-element-nl addtl-nl-needed (first sep))
                              style-vec))
                  (if (zero? addtl-nl-needed)
                    (conj-it! out interpose-style-vec style-vec)
                    (conj-it! out
                              (trimr-blanks-style-vec interpose-style-vec)
                              (repeat-element-nl addtl-nl-needed (first sep))
                              style-vec)))
                (and comma?
                     ; We got rid of newlines above
                     (not= what :comment)
                     (not= what :comment-inline)
                     ; Is there a non comment or non newline/indent
                     ; element left in coll, or is this the last one?
                     ; This returns the [hangflow style-vec], but we
                     ; are not using the data, just the existence of
                     ; the thing here
                     ; Fix for Issue #137.
                     (next-non-comment-nl (next coll)))
                ; should we put an extra new-line before the next
                ; element?
                ; Two styles here:
                ;  o  always put one if the previous pair contained a
                ;  new-line
                ;     which could be (but is not) the default
                ;     To do this you would do:
                ;       (and nl-separator? (not (single-line?
                ;       style-vec)))
                ;  o  put one only if the previous right hand part of
                ;  the
                ;     pair did a flow (which is the current default)
                ;     To do this, you look for whether or not the
                ;     return
                ;     from fzprint-map-two-up said it was a flow
                (or (and nl-separator? (= hangflow :flow)) nl-separator-all?)
                nil ;first?
                0 ;newline-count
              )))))))

(defn interpose-nl-hf
  "Put a single or double line between pairs returned from
  fzprint-map-two-up.  The second argument is the map resulting
  from (:map options) or (:pair options) or whatever.  It should
  have :nl-separator? and :nl-separator-all? in it."
  [suboptions ind coll]
  (interpose-either-nl-hf nil
                          nil
                          [[(str "\n" (blanks ind)) :none :indent 29]]
                          [[(str "\n") :none :indent 30]
                           [(str "\n" (blanks ind)) :none :indent 31]]
                          suboptions
                          nil ; comma?
                          coll))

(defn fzprint-map*
  [caller l-str r-str
   {:keys [one-line? ztype map-depth in-code?],
    {:keys [comma? key-ignore key-ignore-silent nl-separator? force-nl? lift-ns?
            lift-ns-in-code? indent],
     :as map-options}
      caller,
    :as options} ind zloc ns]
  (if (= (:format options) :off)
    (fzprint-noformat
      ; Fix Issue #275
      (if ns (str "#" ns l-str) l-str)
      r-str
      options
      zloc)
    (let [[respect-nl? respect-bl? indent-only?]
            (get-respect-indent options caller :map)]
      (dbg-pr options "fzprint-map* caller:" caller)
      (if indent-only?
        (let [options (assoc options :map-depth (inc map-depth))
              ; Put a namespaced map back together
              l-str (if ns (str "#" ns l-str) l-str)
              l-str-vec [[l-str (zcolor-map options l-str) :left]]
              r-str-vec (rstr-vec options ind zloc r-str)]
          (if (zero? (zcount zloc))
            (concat-no-nil l-str-vec r-str-vec)
            (concat-no-nil l-str-vec
                           (fzprint-indent caller
                                           l-str
                                           r-str
                                           options
                                           ind
                                           zloc
                                           nil ;fn-style
                                           nil) ;arg-1-indent, will prevent hang
                           r-str-vec)))
        (let [options (assoc options :map-depth (inc map-depth))
              zloc (if (and (= ztype :sexpr) (or key-ignore key-ignore-silent))
                     (map-ignore caller options zloc)
                     zloc)
              ; TODO: fix this?
              #_#_options
                (assoc options
                  :call-stack (conj
                                (:call-stack options)
                                {:tag (ztag zloc), :caller caller, :zloc zloc}))
              [no-sort? pair-seq] (partition-all-2-nc
                                    caller
                                    (no-max-length options)
                                    (cond respect-nl? (zseqnws-w-nl zloc)
                                          respect-bl? (zseqnws-w-bl zloc)
                                          :else (zseqnws zloc)))
              _ (dbg-s options
                       :justify
                       "fzprint-map* pair-seq:"
                       (mapv #(vector (count %) (mapv (comp pr-str zstring) %))
                         pair-seq))
              #_(dbg-pr "fzprint-map* pair-seq:"
                        (map (comp zstring first) pair-seq))
              ; don't sort if we are doing respect-nl?
              no-sort? (or no-sort? respect-nl? respect-bl?)
              [ns lift-pair-seq]
                (zlift-ns (assoc map-options :in-code? in-code?) pair-seq ns)
              _ (dbg-pr options
                        "fzprint-map* zlift-ns ns:" ns
                        "no-sort?" no-sort?)
              l-str (if ns (str "#" ns l-str) l-str)
              pair-seq (or lift-pair-seq pair-seq)
              pair-seq
                (if no-sort? pair-seq (order-out caller options first pair-seq))
              ; This is where you might put max-length
              max-length (get-max-length options)
              pair-count (count pair-seq)
              pair-seq (if (> pair-count max-length)
                         (concat (take max-length pair-seq)
                                 (list (list (zdotdotdot))))
                         pair-seq)
              indent (count l-str)
              l-str-vec [[l-str (zcolor-map options l-str) :left]]
              r-str-vec (rstr-vec options ind zloc r-str)]
          (if (empty? pair-seq)
            (concat-no-nil l-str-vec r-str-vec)
            (let [_ (dbg-pr options
                            "fzprint-map*:" (zstring zloc)
                            "ind:" ind
                            "comma?" comma?
                            "rightcnt:" (:rightcnt options))
                  ; A possible one line representation of this map, but this is
                  ; optimistic and needs to be validated.
                  pair-print-one-line
                    (fzprint-map-two-up
                      caller
                      ; that will turn off justification
                      ; If one-line? isn't set, then set it.
                      (if one-line? options (assoc options :one-line? true))
                      (+ indent ind)
                      comma?
                      pair-seq)
                  pair-print-one-line (remove-hangflow pair-print-one-line)
                  _ (when pair-print-one-line
                      (dbg-s options
                             :ppol
                             "pair-print-one-line:"
                             ((:dzprint options) {} pair-print-one-line)))
                  ; Assume it fits on one line, and if it doesn't then
                  ; we will find out about it pretty soon now
                  one-line (when pair-print-one-line
                             (apply concat-no-nil
                               (interpose-either
                                 [["," (zcolor-map options :comma) :whitespace
                                   19] [" " :none :whitespace 23]]
                                 [[" " :none :whitespace 20]]
                                 (constantly comma?)
                                 pair-print-one-line)))
                  one-line-lines (style-lines options (+ indent ind) one-line)
                  one-line (when (fzfit-one-line options one-line-lines)
                             one-line)]
              (if one-line
                (concat-no-nil l-str-vec one-line r-str-vec)
                ; It didn't fit on one line.
                (when (not one-line?)
                  ; We weren't required to fit it on one line
                  (let [pair-print (fzprint-map-two-up caller
                                                       options
                                                       (+ indent ind)
                                                       comma?
                                                       pair-seq)]
                    (concat-no-nil
                      l-str-vec
                      ; Use (+ indent ind) to make sure the indent is
                      ; correct.  Issue #274.
                      (interpose-either-nl-hf
                        ; comma? true
                        [["," (zcolor-map options :comma) :whitespace 21]
                         [(str "\n" (blanks (+ indent ind))) :none :indent 32]]
                        [["," (zcolor-map options :comma) :whitespace 22]
                         ; Fix issue #59 -- don't
                         ; put blanks to indent before the next \n
                         ["\n" :none :indent 33]
                         [(str "\n" (blanks (+ indent ind))) :none :indent 34]]
                        ; comma? nil
                        [[(str "\n" (blanks (+ indent ind))) :none :indent 35]]
                        [["\n" :none :indent 36]
                         [(str "\n" (blanks (+ indent ind))) :none :indent 37]]
                        (:map options) ;nl-separator?
                        comma?
                        pair-print)
                      r-str-vec)))))))))))

(defn fzprint-map
  "Format a real map."
  [options ind zloc]
  (let [[ns lifted-map]
          (when (znamespacedmap? zloc)
            ; Only true when operating on zippers
            (let [zloc-seq (zmap identity zloc)]
              (dbg-pr options "fzprint-map: zloc-seq" (map zstring zloc-seq))
              [(zstring (first zloc-seq)) (second zloc-seq)]))]
    (dbg-pr options
            "fzprint-map: ns:" ns
            "indent:" (:indent (:map options))
            "map-options:" (dissoc (:map options) :key-value-options))
    (if ns
      (fzprint-map* :map "{" "}" (rightmost options) ind lifted-map ns)
      (fzprint-map* :map "{" "}" (rightmost options) ind zloc nil))))

(defn object-str?
  "Return true if the string starts with #object["
  [s]
  (re-find #"^#object\[" s))

(defn fzprint-object
  "Print something that looks like #object[...] in a way
  that will acknowledge the structure inside of the [...]"
  ([options ind zloc zloc-value]
   (fzprint-vec* :object
                 "#object["
                 "]"
                 options
                 ind
                 (zobj-to-vec zloc zloc-value)))
  ([options ind zloc]
   (fzprint-vec* :object "#object[" "]" options ind (zobj-to-vec zloc))))

(defn hash-identity-str
  "Find the hash-code identity for an object."
  [obj]
  #?(:bb (str (hash obj))
     :clj (Integer/toHexString (System/identityHashCode obj))
     :cljs (str (hash obj))))

; (with-out-str
;    (printf "%08x" (System/identityHashCode obj))))

(defn fzprint-atom
  [{{:keys [object?]} :atom, :as options} ind zloc]
  (if (and object? (object-str? (zstring zloc)))
    (fzprint-object options ind zloc (zderef zloc))
    (let [l-str "#<"
          r-str ">"
          indent (count l-str)
          l-str-vec [[l-str (zcolor-map options l-str) :left]]
          r-str-vec (rstr-vec options ind zloc r-str)
          arg-1 (str "Atom@" (hash-identity-str zloc))
          arg-1-indent (+ ind indent 1 (count arg-1))]
      (dbg-pr options
              "fzprint-atom: arg-1:" arg-1
              "zstring arg-1:" (zstring zloc))
      (concat-no-nil l-str-vec
                     [[arg-1 (zcolor-map options :none) :element]]
                     (fzprint-hang-one :unknown
                                       (rightmost options)
                                       arg-1-indent
                                       (+ indent ind)
                                       (zderef zloc))
                     r-str-vec))))

(defn fzprint-future-promise-delay-agent
  "Print out a future or a promise or a delay.  These can only be 
  sexpressions, since they don't exist in a textual representation 
  of code (or data for that matter).  That means that we can use 
  regular sexpression operations on zloc."
  [options ind zloc]
  (let [zloc-type (cond (zfuture? zloc) :future
                        (zpromise? zloc) :promise
                        (zdelay? zloc) :delay
                        (zagent? zloc) :agent
                        :else (throw (#?(:clj Exception.
                                         :cljs js/Error.)
                                      "Not a future, promise, or delay:"
                                      (zstring zloc))))]
    (if (and (:object? (options zloc-type)) (object-str? (zstring zloc)))
      (if (or (= zloc-type :agent) (realized? zloc))
        (fzprint-object options ind zloc (zderef zloc))
        (fzprint-object options ind zloc))
      (let [l-str "#<"
            r-str ">"
            indent (count l-str)
            l-str-vec [[l-str (zcolor-map options l-str) :left]]
            r-str-vec (rstr-vec options ind zloc r-str)
            type-str (case zloc-type
                       :future "Future@"
                       :promise "Promise@"
                       :delay "Delay@"
                       :agent "Agent@")
            arg-1 (str type-str (hash-identity-str zloc))
            #?@(:bb [_ nil]
                :clj [arg-1
                      (if (and (= zloc-type :agent) (agent-error zloc))
                        (str arg-1 " FAILED")
                        arg-1)])
              arg-1-indent
            (+ ind indent 1 (count arg-1)) zloc-realized?
            (if (= zloc-type :agent) true (realized? zloc)) value
            (if zloc-realized?
              (zderef zloc)
              (case zloc-type
                :future "pending"
                :promise "not-delivered"
                :delay "pending"))
              options
            (if zloc-realized? options (assoc options :string-str? true))]
        (dbg-pr options
                "fzprint-fpda: arg-1:" arg-1
                "zstring arg-1:" (zstring zloc))
        (concat-no-nil l-str-vec
                       [[arg-1 (zcolor-map options :none) :element]]
                       (fzprint-hang-one :unknown
                                         (rightmost options)
                                         arg-1-indent
                                         (+ indent ind)
                                         value)
                       r-str-vec)))))

(defn fzprint-fn-obj
  "Print a function object, what you get when you put a function in
  a collection, for instance.  This doesn't do macros, you will notice.
  It also can't be invoked when zloc is a zipper."
  [{{:keys [object?]} :fn-obj, :as options} ind zloc]
  (if (and object? (object-str? (zstring zloc)))
    (fzprint-object options ind zloc)
    (let [l-str "#<"
          r-str ">"
          indent (count l-str)
          l-str-vec [[l-str (zcolor-map options :fn) :left]]
          r-str-vec (rstr-vec options ind zloc r-str :fn)
          arg-1-left "Fn@"
          arg-1-right (hash-identity-str zloc)
          arg-1-indent (+ ind indent 1 (count arg-1-left) (count arg-1-right))
          class-str (pr-str #?(:clj (class zloc)
                               :cljs (type zloc)))
          #?@(:clj [[class-name & more]
                    (s/split (s/replace-first class-str #"\$" "/") #"\$") color
                    (if (re-find #"clojure" class-name)
                      (zcolor-map options :fn)
                      :none) arg-2 (str class-name (when more "[fn]"))]
              :cljs [name-js (str (.-name zloc)) color
                     (if (or (re-find #"^clojure" name-js)
                             (re-find #"^cljs" name-js))
                       (zcolor-map options :fn)
                       :none) name-split (clojure.string/split name-js #"\$")
                     arg-2
                     (str (apply str (interpose "." (butlast name-split)))
                          "/"
                          (last name-split))])]
      (dbg-pr options
              "fzprint-fn-obj: arg-1:"
              arg-1-left
              arg-1-right
              "zstring arg-1:"
              (zstring zloc))
      (concat-no-nil l-str-vec
                     [[arg-1-left (zcolor-map options :fn) :element]]
                     [[arg-1-right (zcolor-map options :none) :element]]
                     (fzprint-hang-one :unknown
                                       (rightmost (assoc options
                                                    :string-str? true
                                                    :string-color color))
                                       arg-1-indent
                                       (+ indent ind)
                                       arg-2)
                     r-str-vec))))

(defn fzprint-ns
  [options ind zloc]
  (let [l-str "#<"
        r-str ">"
        indent (count l-str)
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options ind zloc r-str)
        arg-1 "Namespace"
        arg-1-indent (+ ind indent 1 (count arg-1))]
    (dbg-pr options "fzprint-ns: arg-1:" arg-1 "zstring arg-1:" (zstring zloc))
    (concat-no-nil l-str-vec
                   [[arg-1 (zcolor-map options :none) :element]]
                   (fzprint-hang-one :unknown
                                     (rightmost options)
                                     arg-1-indent
                                     (+ indent ind)
                                     (ns-name zloc))
                   r-str-vec)))

(defn dec-depth
  "Given an options map, decrement the :depth value and return the result."
  [options]
  (when options (assoc options :depth (dec (or (:depth options) 1)))))

(defn fzprint-record
  [{{:keys [record-type? to-string?]} :record, :as options} ind zloc]
  (if to-string?
    (fzprint* options ind (. zloc toString))
    (if-not record-type?
      ; if not printing as record-type, turn it into map
      (fzprint* options ind (into {} zloc))
      (let [l-str "#"
            r-str ""
            indent (count l-str)
            l-str-vec [[l-str (zcolor-map options l-str) :left]]
            r-str-vec (rstr-vec options ind zloc r-str)
            arg-1 #?(:clj (pr-str (class zloc))
                     :cljs
                       (clojure.string/replace (pr-str (type zloc)) "/" "."))
            arg-1 (let [tokens (clojure.string/split arg-1 #"\.")]
                    (apply str (into [] (interpose "." tokens))))
            arg-1-indent (+ ind indent 1 (count arg-1))]
        (dbg-pr options
                "fzprint-record: arg-1:" arg-1
                "zstring zloc:" (zstring zloc))
        (concat-no-nil l-str-vec
                       [[arg-1 (zcolor-map options :none) :element]]
                       (fzprint-hang-one :record
                                         (dec-depth options)
                                         ;(rightmost options)
                                         arg-1-indent
                                         (+ indent ind)
                                         ; this only works because
                                         ; we never actually get here
                                         ; with a zipper, just an sexpr
                                         (into {} zloc))
                       r-str-vec)))))

(defn drop-thru-first-non-whitespace
  "Drop elements of the sequence up to and including the first element
  that is not zwhitespace?"
  [coll]
  (let [no-whitespace (drop-while zwhitespace? coll)] (drop 1 no-whitespace)))

(defn take-thru-first-non-whitespace
  "Take all elements of the sequence up to and including the first element
  that is not zwhitespace?"
  [coll]
  (loop [coll coll
         out []]
    (if coll
      (let [element (first coll)]
        (if (not (zwhitespace? element))
          (conj out element)
          (recur (next coll) (conj out element))))
      out)))

(defn fzprint-meta
  "Print the two items in a meta node.  Different because it doesn't print
  a single collection, so it doesn't do any indent or rightmost.  It also
  uses a different approach to calling fzprint-flow-seq with the
  results zmap, so that it prints all of the seq, not just the rightmost."
  [options ind zloc]
  (let [l-str "^"
        r-str ""
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options ind zloc r-str)
        ; i224
        zloc-seq (fzprint-get-zloc-seq :list options zloc)
        zloc-seq (if (:split? (:meta options))
                   ; If we are splitting the meta, we already pulled out
                   ; everything but the first thing into the outer zloc-seq
                   ; in fzprint-split-meta-in-seq prior to calling this routine.
                   (take-thru-first-non-whitespace zloc-seq)
                   zloc-seq)]
    (dbg-pr options
            "fzprint-meta: zloc:" (zstring zloc)
            "zloc-seq" (map zstring zloc-seq))
    (concat-no-nil l-str-vec
                   (if (:indent-only? (:list options))
                     ; Since l-str isn't a "pair" and shouldn't be
                     ; considered in the indent, we don't tell
                     ; fzprint-indent about it.
                     (fzprint-indent :vector
                                     l-str
                                     ""
                                     options
                                     ind
                                     zloc
                                     nil
                                     nil
                                     :first-indent-only?)
                     (fzprint-flow-seq
                       :meta
                       ; No rightmost, because this isn't
                       ; a collection.  This is essentially
                       ; two separate things.
                       options
                       ; no indent for second line, as the
                       ; leading ^ is not a normal
                       ; collection beginning.
                       ; Generate a separate indent for the
                       ; first thing, and use ind for the
                       ; remaining.
                       (apply vector
                         (+ (count l-str) ind)
                         (repeat (dec (count zloc-seq)) ind))
                       zloc-seq))
                   r-str-vec)))

(defn fzprint-reader-macro
  "Print a reader-macro, often a reader-conditional. Adapted for differences
  in parsing #?@ between rewrite-clj and rewrite-cljs.  Also adapted for
  the rewrite-clj not parsing namespaced maps in the version presently
  used."
  [options ind zloc]
  (let [zstr (zstring (zfirst zloc))
        ; rewrite-cljs parses #?@ differently from rewrite-clj.  In
        ; rewrite-cljs zfirst is ?@, not ?, so deal with that.
        ; Not clear which is correct, I could see it go either way.
        alt-at? (and (= (count zstr) 2) (= (subs zstr 1 2) "@"))
        reader-cond? (= (subs zstr 0 1) "?")
        ; are we dealing with a namespaced map?
        ; 5/30/19 I don't know if we ever encounter this anymore...
        ; Was unable to get namespaced? to be true despite running all 616
        ; tests and some repl testing as well.
        namespaced? (= (subs zstr 0 1) ":")
        at? (or (= (ztag (zsecond zloc)) :deref) alt-at?)
        ; If :reader-cond doesn't have these things, then let :map govern
        [respect-nl? respect-bl? indent-only?]
          (get-respect-indent options :reader-cond :map)
        l-str (cond (and reader-cond? at?) "#?@"
                    (and reader-cond? (zcoll? (zsecond zloc))) "#?"
                    reader-cond?
                      (throw (#?(:clj Exception.
                                 :cljs js/Error.)
                              (str "Unknown reader macro: '" (zstring zloc)
                                   "' zfirst zloc: " (zstring (zfirst zloc)))))
                    namespaced? (str "#" zstr)
                    :else "#")
        r-str ""
        ; Error to debug zpst
        _ (when (:dbg-bug? options)
            #?(:clj (+ "a" "b")
               :cljs nil))
        indent (count l-str)
        ; we may want to color this based on something other than
        ; its actual character string
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options ind zloc r-str)
        floc
          (if (and at? (not alt-at?)) (zfirst (zsecond zloc)) (zsecond zloc))]
    (dbg-pr options
            "fzprint-reader-macro: zloc:" (zstring zloc)
            "floc:" (zstring floc)
            "l-str:" l-str)
    ; This isn't really all that correct, but does yield the right output.
    ; Question about whether or not it does the right stuff for focus.
    ; Maybe there is some way to call fzprint-indent with just the
    ; #? and "", and let it deal with the underlying list. I don't know
    ; if that would be possible, though maybe if we carefully positioned
    ; the floc for that case.  But could we hack in the ["" :none :element]
    ; in that case? At present documented that focus and :indent-only
    ; don't work together..
    (if indent-only?
      (let [l-str-io (if reader-cond? (str l-str "(") l-str)
            r-str-io (if reader-cond? ")" "")
            l-str-vec-io [[l-str-io (zcolor-map options l-str-io) :left]]
            r-str-vec-io (rstr-vec options ind zloc r-str-io)]
        (concat-no-nil l-str-vec-io
                       (if reader-cond?
                         (fzprint-indent :map
                                         l-str-io
                                         r-str-io
                                         (rightmost options)
                                         ind
                                         floc
                                         nil ;fn-style
                                         nil) ;arg-1-indent, will prevent hang
                         (fzprint-indent
                           :map
                           l-str-io
                           r-str-io
                           (rightmost options)
                           ind
                           (if namespaced? (znextnws-w-nl zloc) zloc)
                           nil ; fn-style
                           nil) ;arg-1-indent
                       )
                       r-str-vec-io))
      (concat-no-nil
        l-str-vec
        ; Because there is a token here in the zipper,
        ; we need something to make the focus
        ; positioning come out right.
        [["" :none :element]]
        (if reader-cond?
          ; yes rightmost, this is a collection
          (fzprint-map* :reader-cond
                        "("
                        ")"
                        (rightmost options)
                        ; Here is where we might adjust the
                        ; indent, but if we do it here
                        ; (since this looks like a list),
                        ; we also have to deal with it when
                        ; the map code is doing the next
                        ; thing (like :cljs after  :clj).
                        ; If you just (dec indent) here
                        ; you break 14 tests.
                        (+ indent ind)
                        floc
                        nil)
          ; not reader-cond?
          (fzprint-flow-seq :reader-cond
                            options
                            (+ indent ind)
                            (let [zloc-seq
                                    (cond respect-nl? (zmap-w-nl identity zloc)
                                          respect-bl? (zmap-w-bl identity zloc)
                                          :else (zmap identity zloc))]
                              (if namespaced? (next zloc-seq) zloc-seq))))
        r-str-vec))))

(defn fzprint-newline
  "Given an element which contains newlines, split it up into individual
  newline elements."
  [options ind zloc]
  (let [zstr (zstring zloc)
        [newline-count _] (newline-vec zstr)]
    (dbg-pr options
            "fzprint-newline: zloc:" (zstring zloc)
            "newline-count:" newline-count
            "ind:" ind)
    (into []
          (repeat newline-count [(str "\n" (blanks ind)) :none :newline 2]))))

(def prefix-tags
  {:quote "'",
   :syntax-quote "`",
   :unquote "~",
   :unquote-splicing "~@",
   :deref "@",
   :var "#'",
   :uneval "#_"})

(defn prefix-options
  "Change options as necessary based on prefix tag."
  [options prefix-tag]
  (cond (= prefix-tag :uneval) (assoc options
                                 :color-map (:color-map (:uneval options)))
        (= prefix-tag :syntax-quote)
          (-> options
              (assoc-in [:color-map :paren]
                        (:syntax-quote-paren (:color-map options)))
              (assoc-in [:color-map :hash-paren]
                        (:syntax-quote-paren (:color-map options))))
        :else options))

(defn make-caller
  "Sometime we need to give a caller to a routine, and there isn't
  a specific caller in the configuration.  So, we will use the configuration
  from some other caller and make up a new one just for this situation.
  The key-seq is the series of keys to both look up and create.  The
  caller is the new caller, and the existing-caller is the one from which
  we we will extract the information. This returns a new options map with
  the new-caller in it."
  [options new-caller existing-caller key-seq]
  (update-in options
             (concat [new-caller] key-seq)
             #(do % (get-in options (concat [existing-caller] key-seq)))))

;; Fix fzprint* to look at cursor to see if there is one, and
;; fzprint to set cursor with binding.  If this works, might pass
;; it around.  Maybe pass ctx to everyone and they can look at it
;; or something.  But for testing, let's just do this.

(defn integrate-next-inner
  "If the value of :next-inner is a map, then config-and-validate it. If
  the value of :next-inner is a vector of maps, then config-and-validate
  each of the maps in turn."
  [options]
  (dbg-pr options "integrate-next-inner:")
  (let [next-inner (:next-inner options :unset)]
    (cond (map? next-inner) (first (zprint.config/config-and-validate
                                     "next-inner:"
                                     nil
                                     (dissoc options :next-inner)
                                     next-inner
                                     nil ; validate?
                                   ))
          (vector? next-inner) (reduce #(first
                                          (zprint.config/config-and-validate
                                            "next-inner-vector"
                                            nil
                                            %1
                                            %2
                                            nil))
                                 (dissoc options :next-inner)
                                 next-inner)
          (= next-inner :unset) options
          :else options)))

;;
;; Print exactly as we got it, but colorize (or :hiccup or :html) it.
;;
;; Except, remove any spaces prior to newlines.
;;

(defn remove-spaces-pre-nl
  "Take a style vec and remove any :whitespace element prior to a :newline
  or an :indent element."
  [svec]
  (loop [nvec svec
         out []]
    (if (empty? nvec)
      out
      (let [[_ _ this-what :as this-vec] (first nvec)
            [_ _ next-what :as next-vec] (second nvec)]
        (recur (next nvec)
               (if (and (= this-what :whitespace)
                        (or (= next-what :newline) (= next-what :indent)))
                 out
                 (conj out this-vec)))))))

(defn fzprint-noformat
  "Take a collection, and map fzprint* across it to get it colorized, but
  take every single whitespace and everything.  It should come out just like
  it went in, but with colors (or whatever)."
  [l-str r-str options zloc]
  (let [l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options 0 zloc r-str)
        len (zcount zloc)
        _ (dbg-s-pr options
                    :noformat
                    "fzprint-noformat zloc" (zstring zloc)
                    "len:" len)
        fzprint*-seq (zmap-all-nl-comment (partial fzprint* options 0) zloc)
        _ (dbg-s-pr options
                    :noformat
                    "fzprint-noformat fzprint*-seq:"
                    fzprint*-seq)
        concat-vec
          (if (zero? len)
            (concat-no-nil l-str-vec r-str-vec)
            (concat-no-nil l-str-vec (apply concat fzprint*-seq) r-str-vec))
        _ (dbg-s-pr options :noformat "fzprint-noformat concat-vec:" concat-vec)
        remove-spaces-vec (remove-spaces-pre-nl concat-vec)
        _ (dbg-s-pr options
                    :noformat
                    "fzprint-noformat remove-spaces-vec:"
                    remove-spaces-vec)]
    remove-spaces-vec))
;;
;; # The center of the zprint universe
;;
;; Looked into alternative ways to dispatch this, but at the end of
;; the day, this looked like the best.
;;

(defn fzprint*
  "The pretty print part of fzprint."
  [{:keys [width rightcnt hex? shift-seq dbg? dbg-print? dbg-s in-hang?
           one-line? string-str? string-color depth max-depth trim-comments?
           in-code? max-hang-depth max-hang-span max-hang-count next-inner],
    :as options} indent zloc]
  (let [avail (- width indent)
        ; note that depth affects how comments are printed, toward the end
        options (assoc options :depth (inc depth))
        ; Can't use dbg-s directly here, as it is also a local value!
        _ (dbg-s-pr options
                    :next-inner
                    "fzprint* " (pr-str (zstring zloc))
                    " next-inner:" (:next-inner options))
        options (if next-inner
                  ; There are two kinds of next-inner maps.  The normal
                  ; kind is something to add to the current options map,
                  ; and to do that, we will use config-and-validate for
                  ; reasons explained below.  The other kind is a map that
                  ; was saved and we are just restoring it, and that will
                  ; entirely replace the current options map.
                  (integrate-next-inner options)
                  options)
        options (if (or dbg? dbg-print? dbg-s)
                  (assoc options
                    :dbg-indent (str (get options :dbg-indent "")
                                     (cond one-line? "o"
                                           in-hang? "h"
                                           :else ".")))
                  options)
        _ (dbg-s-pr options
                    :next-inner
                    "fzprint* **** rightcnt:"
                    rightcnt
                    "depth:"
                    depth
                    "indent:"
                    indent
                    "in-hang?:"
                    in-hang?
                    ":next-inner:"
                    (:next-inner options)
                    (pr-str (zstring zloc)))
        dbg-data @fzprint-dbg
        dbg-focus? (and dbg? (= dbg-data (second (zfind-path zloc))))
        options (if dbg-focus? (assoc options :dbg :on) options)
        _ (if dbg-focus? (println "fzprint dbg-data:" dbg-data))]
    #_(def zlocx zloc)
    ; We don't check depth if it is not a collection.  We might have
    ; just not incremented depth if it wasn't a collection, but this
    ; may be equivalent.
    (cond
      (and (zcoll? zloc)
           (or (>= depth max-depth) (zero? (get-max-length options))))
        (if (= zloc (zdotdotdot))
          [["..." (zcolor-map options :none) :element]]
          [[(:max-depth-string options) (zcolor-map options :keyword)
            :element]])
      ; Try to fix up runaway exponential time increases with very deep
      ; strucures.  Note this is typically only affects maps, but it would
      ; affect lists that were not code.
      (and in-hang?
           (not one-line?)
           (not in-code?)
           ;(> (/ indent width) 0.3)
           (or (> (- depth in-hang?) max-hang-span)
               (and (not one-line?)
                    (> (zcount zloc) max-hang-count)
                    (> depth max-hang-depth))))
        nil
      (zrecord? zloc) (fzprint-record options indent zloc)
      (zlist? zloc) (fzprint-list options indent zloc)
      (zvector? zloc) (fzprint-vec options indent zloc)
      (or (zmap? zloc) (znamespacedmap? zloc)) (fzprint-map options indent zloc)
      (zset? zloc) (fzprint-set options indent zloc)
      (zanonfn? zloc) (fzprint-anon-fn options indent zloc)
      (zfn-obj? zloc) (fzprint-fn-obj options indent zloc)
      (zarray? zloc) (if (:object? (:array options))
                       (fzprint-object options indent zloc)
                       (fzprint-array #?(:clj (if (:hex? (:array options))
                                                (assoc options
                                                  :hex? (:hex? (:array options))
                                                  :shift-seq
                                                    (zarray-to-shift-seq zloc))
                                                options)
                                         :cljs options)
                                      indent
                                      (zexpandarray zloc)))
      (zatom? zloc) (fzprint-atom options indent zloc)
      (zmeta? zloc) (fzprint-meta options indent zloc)
      (prefix-tags (ztag zloc))
        (fzprint-vec* :prefix-tags
                      (prefix-tags (ztag zloc))
                      ""
                      ; Pick up the :indent-only?, :respect-nl?, and
                      ; respect-bl? config from :list
                      ; Note that the routine get-respect-indent exists,
                      ; and its use in fzprint-vec* and fzprint-map* also
                      ; solves a similar problem
                      (-> (prefix-options options (ztag zloc))
                          (make-caller :prefix-tags :list [:indent-only?])
                          (make-caller :prefix-tags :list [:respect-nl?])
                          (make-caller :prefix-tags :list [:respect-bl?]))
                      indent
                      zloc)
      (zns? zloc) (fzprint-ns options indent zloc)
      (or (zpromise? zloc) (zfuture? zloc) (zdelay? zloc) (zagent? zloc))
        (fzprint-future-promise-delay-agent options indent zloc)
      (zreader-macro? zloc) (fzprint-reader-macro options indent zloc)
      ; This is needed to not be there for newlines in parse-string-all,
      ; but is needed for respect-nl? support.
      ;(and (= (ztag zloc) :newline) (> depth 0)) [["\n" :none :newline]]
      (and (= (ztag zloc) :newline) (> depth 0))
        (fzprint-newline options indent zloc)
      :else
        (let [zstr (zstring zloc)
              overflow-in-hang? (and in-hang?
                                     (> (+ (count zstr) indent (or rightcnt 0))
                                        width))]
          (cond
            (and (zcomment? zloc) (not (some #{\;} zstr)))
              ; We should remvoe them when we get zutil fixed.
              (fzprint-newline options indent zloc)
            (zcomment? zloc)
              (let [zcomment
                      ; trim-comments? is true for parse-string-all
                      (if (and (zero? depth) (not trim-comments?))
                        zstr
                        ; Remove trailing newlines and spaces
                        (clojure.string/trimr zstr))
                    ; Only check for inline comments if we are doing them
                    ; otherwise we get left with :comment-inline element
                    ; types that don't go away
                    inline-comment-vec (when (:inline? (:comment options))
                                         (inlinecomment? zloc))]
                (dbg options
                     "fzprint* trim-comments?:" trim-comments?
                     "inline-comment-vec:" inline-comment-vec)
                (if (and (:count? (:comment options)) overflow-in-hang?)
                  (do (dbg options "fzprint*: overflow comment ========") nil)
                  (if inline-comment-vec
                    [[zcomment (zcolor-map options :comment) :comment-inline
                      (first inline-comment-vec) (second inline-comment-vec)]]
                    [[zcomment (zcolor-map options :comment) :comment]])))
            (= (ztag zloc) :comma) [[zstr (zcolor-map options :comma) :comma]]
            #?@(:cljs [(and (= (ztag zloc) :whitespace)
                            (clojure.string/includes? zstr ","))])
              #?@(:cljs [[["," (zcolor-map options :comma) :comma]]])
            ; Really just testing for whitespace, comments filtered above
            (zwhitespaceorcomment? zloc) [[zstr :none :whitespace 24]]
            ; At this point, having filtered out whitespace and
            ; comments above, now we expect zsexpr will work for all of
            ; the remaining things.
            ;
            ; If we are going to overflow, and we are doing a hang, let's
            ; stop now!
            overflow-in-hang? (do (dbg options "fzprint*: overflow <<<<<<<<<<")
                                  nil)
            (zkeyword? zloc) [[zstr (zcolor-map options :keyword) :element]]
            :else (let [zloc-sexpr (get-sexpr options zloc)]
                    (cond (string? zloc-sexpr) [[(if string-str?
                                                   (str zloc-sexpr)
                                                   ; zstr
                                                   (zstring zloc))
                                                 (if string-color
                                                   string-color
                                                   (zcolor-map options :string))
                                                 :element]]
                          (showfn? options zloc-sexpr)
                            [[zstr (zcolor-map options :fn) :element]]
                          (show-user-fn? options zloc-sexpr)
                            [[zstr (zcolor-map options :user-fn) :element]]
                          (number? zloc-sexpr)
                            [[(if hex? (znumstr zloc hex? shift-seq) zstr)
                              (zcolor-map options :number) :element]]
                          (symbol? zloc-sexpr)
                            [[zstr (zcolor-map options :symbol) :element]]
                          (nil? zloc-sexpr) [[zstr (zcolor-map options :nil)
                                              :element]]
                          (true? zloc-sexpr) [[zstr (zcolor-map options :true)
                                               :element]]
                          (false? zloc-sexpr) [[zstr (zcolor-map options :false)
                                                :element]]
                          (char? zloc-sexpr) [[zstr (zcolor-map options :char)
                                               :element]]
                          (or (instance? #?(:clj java.util.regex.Pattern
                                            :cljs (type #"regex"))
                                         zloc-sexpr)
                              (re-find #"^#\".*\"$" zstr))
                            [[zstr (zcolor-map options :regex) :element]]
                          :else [[zstr (zcolor-map options :none)
                                  :element]])))))))

;;
;; # External interface to all fzprint functions
;;

(defn fzprint
  "The pretty print part of fzprint."
  [options indent zloc]
  #_(def opt options)
  (dbg options "fzprint: indent:" indent "(:indent options)" (:indent options))
  ; if we are doing specs, find the docstring and modify it with
  ; the spec output.
  #_(println "fn-name:" (:fn-name options))
  #_(println "spec:" (:value (:spec options)))
  (let [zloc (if-not (and (= (:ztype options) :zipper) (:value (:spec options)))
               zloc
               (add-spec-to-docstring zloc (:value (:spec options))))
        style-vec (fzprint* (assoc options
                              :depth 0
                              :map-depth 0
                              ; Add a map of zfns to the options for use
                              ; by guides that need them.
                              :zfn-map (zfn-map))
                            indent
                            zloc)]
    #_(def fsv style-vec)
    style-vec))

;    (if (= (:ztype options) :sexpr)
;      style-vec
;      (if (:wrap? (:comment options))
;        (fzprint-wrap-comments options style-vec)
;        style-vec))))

;;
;; # Basic functions for testing results -- used only for tests
;;

(defn line-count "Count lines in a string." [s] (inc (count (re-seq #"\n" s))))

(defn line-widths
  "Return a vector the lengths of lines."
  [s]
  (map count (clojure.string/split s #"\n")))

(defn max-width
  "Split a string into lines, and figure the max width."
  [s]
  (reduce max (line-widths s)))

;;
;; # Tab Expansion
;;

(defn expand-tabs
  "Takes a string, and expands tabs inside of the string based
  on a tab-size argument."
  ([tab-size s]
   ; If we don't have tabs, don't do anything.
   (if (clojure.string/includes? s "\t")
     (apply str
       (loop [char-seq (seq s)
              cur-len (long 0)
              out (transient [])]
         (if (empty? char-seq)
           (persistent! out)
           (let [this-char (first char-seq)
                 tab-expansion (if (= this-char \tab)
                                 (- tab-size (mod cur-len tab-size))
                                 nil)]
             (recur (rest char-seq)
                    (if (= this-char \newline)
                      0
                      (+ cur-len (long (or tab-expansion 1))))
                    (if tab-expansion
                      (apply conj-it! out (repeat tab-expansion \space))
                      (conj! out this-char)))))))
     s))
  ([s] (expand-tabs 8 s)))

;;
;; # Line Endings
;;

(defn determine-ending-split-lines
  "Given a string, find the line ending that is predominent in the beginning
  of the string, and split the string into separate lines.  Returns 
  [line-ending-string vector-of-lines]"
  [s]
  (if (clojure.string/includes? s "\r")
    ; Figure out the line endings
    (let [lines (clojure.string/split s #"\r\n|\r|\n" -1)
          first-lines (clojure.string/split (subs s 0 (min (count s) 2000))
                                            #"\r")
          #_(prn "first-lines:" first-lines)
          nl-count
            (reduce #(if (clojure.string/starts-with? %2 "\n") (inc %1) %1)
              0
              first-lines)
          #_(prn "nl-count:" nl-count)
          line-ending (if (>= nl-count (/ (count first-lines) 2)) "\r\n" "\r")]
      [line-ending lines])
    ; If no \r, then we assume \n line endings
    ["\n" (clojure.string/split s #"\n" -1)]))

;;
;; # Needed for expectations testing
;;
;; Seems defrecord doesn't work in test environment, which is pretty odd.
;;

(defrecord r [left right])
(defn make-record [left right] (new r left right))

;;
;; End of testing functions
;;
