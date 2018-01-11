(ns zprint.zprint
  #?@(:cljs [[:require-macros
              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])
    [clojure.string :as s]
    [zprint.zfns :refer
     [zstring znumstr zbyte-array? zcomment? zsexpr zseqnws zmap-right
      zfocus-style zfirst zfirst-no-comment zsecond zthird zfourth znthnext
      zcount zmap zanonfn? zfn-obj? zfocus zfind-path zwhitespace? zlist?
      zvector? zmap? zset? zcoll? zuneval? zmeta? ztag zparseuneval zlast
      zarray? zatom? zderef zrecord? zns? zobj-to-vec zexpandarray znewline?
      zwhitespaceorcomment? zmap-all zpromise? zfuture? zdelay? zkeyword?
      zconstant? zagent? zreader-macro? zarray-to-shift-seq zdotdotdot zsymbol?
      znil? zreader-cond-w-symbol? zreader-cond-w-coll? zlift-ns zinlinecomment?
      zfind zmap-w-nl]]
    [zprint.ansi :refer [color-str]]
    [zprint.config :refer [validate-options merge-deep]]
    [zprint.zutil :refer [add-spec-to-docstring]]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip :as z]
    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))

(declare interpose-nl-hf)

;;
;; # Utility Functions
;;

(defn blanks
  "Produce a blank string of desired size."
  [n]
  (apply str (repeat n " ")))

(defn dots
  "Produce a dot string of desired size."
  [n]
  (apply str (repeat n ".")))

(defn indent "error" [])

(defn conj-it!
  "Make a version of conj! that take multiple arguments."
  [& rest]
  (loop [out (first rest)
         more (next rest)]
    (if more (recur (conj! out (first more)) (next more)) out)))

(defn split-lf
  "Do split for newlines, instead of using regular expressions."
  [s]
  (loop [input s
         out []]
    (if-not input
      out
      (let [next-lf (clojure.string/index-of input "\n")
            chunk (if next-lf (subs input 0 next-lf) input)]
        (recur (if next-lf (subs input (inc next-lf)) nil) (conj out chunk))))))

(defn split-lf-2
  "Do split for newlines, instead of using regular expressions.
  Maximum split is 2."
  [s]
  (if-let [next-lf (clojure.string/index-of s "\n")]
    [(subs s 0 next-lf) (subs s (inc next-lf))]
    [s]))

;;
;; # Use pmap when we have it
;;

#?(:clj (defn zpmap
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
  #?(:clj (if (:parallel? options) (deref value) value)
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
          (println (apply str (blanks ind) (map first style-vec))))
      (println dbg-indent dbg-output "--------------- no style-vec"))))

;;
;; # What is a function?
;;

(defn showfn?
  "Show this thing as a function?"
  [fn-map f]
  (when (not (string? f))
    (let [f-str (str f)]
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
                           :cljs :default) e
                   nil)))))))

(defn show-user-fn?
  "Show this thing as a user defined function?  Assumes that we
  have already handled any clojure defined functions!"
  [options f]
  (when (not (string? f))
    (let [f-str (str f)
          user-fn-map (:user-fn-map options)]
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
                           :cljs :default) e
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
    {:keys [hang-flow hang-type-flow hang-flow-limit general-hang-adjust
            hang-if-equal-flow?]}
      :tuning,
    {:keys [hang-expand hang-diff hang-size hang-adjust]} caller,
    :as options} fn-style p-count indent-diff
   [p-lines p-maxwidth p-length-seq p-what] [b-lines b-maxwidth _ b-what]]
  (let [p-last-maxwidth (last p-length-seq)
        hang-diff (or hang-diff 0)
        hang-expand (or hang-expand 1000.)
        hang-adjust (or hang-adjust general-hang-adjust)
        #_(options (if (and p-lines
                            p-count
                            (pos? p-count)
                            (not (<= indent-diff hang-diff))
                            (not (<= (/ (dec p-lines) p-count) hang-expand)))
                     (assoc options :dbg? true)
                     options))
        options (if (or p-what b-what) (assoc options :dbg? true) options)
        result
          (if (not b-lines)
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
                 (or (zero? p-lines)
                     (and ; do we have lines to operate on?
                          (> b-lines 0)
                          (> p-count 0)
                          ; if the hang and the flow are the same size, why not
                          ; hang?
                          (if (and (= p-lines b-lines) hang-if-equal-flow?)
                            true
                            ; is the difference between the indents so small
                            ; that
                            ; we don't care?
                            (and (if (<= indent-diff hang-diff)
                                   true
                                   ; Do the number of lines in the hang exceed
                                   ; the number
                                   ; of elements in the hang?
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
                                     ; necessarily
                                     ; take the shortest
                                     ; once we did (dec p-lines) here, fwiw
                                     ; then we tried it w/out the dec, now we
                                     ; let you
                                     ; set it in :tuning.  The whole point of
                                     ; having a
                                     ; hang-adjust of -1 is to allow hangs when
                                     ; the
                                     ; number of lines in a hang is the same as
                                     ; the
                                     ; number of lines in a flow.
                                     ;(< (/ p-lines b-lines) factor)))))))]
                                     (< (/ (+ p-lines hang-adjust) b-lines)
                                        factor)))))))))]
    (dbg options
         (if result "++++++" "XXXXXX")
         "p-what" p-what
         "good-enough? caller:" caller
         "fn-style:" fn-style
         "width:" width
         "rightcnt:" rightcnt
         "hang-expand:" hang-expand
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
  "Scan a collection, and return the number of nils or empty collections
  present (if any), and nil otherwise."
  [coll]
  (let [n (count (filter #(if (coll? %) (empty? %) (nil? %)) coll))]
    (when (not (zero? n)) n)))

(defn concat-no-nil-alt
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

(defn concat-no-nil
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
      #_((and comment? (not just-eol?))
          ; if a comment and we didn't just do
          ; a newline, then terminate the previous
          ; line and do a line just with the comment
          (assoc in
            0 (conj out cur-len count-s)
            1 0
            2 true))
      ; if we are told to terminate the line or it
      ; is a comment, we terminate the line with the
      ; size of the string added to it
      (or (and eol? (not (and just-eol? (zero? count-s)))) comment?)
        [(conj out (+ cur-len count-s)) 0 true comment?]
      ;(assoc in 0 (conj out (+ cur-len count-s)) 1 0 2 true 3 comment?)
      ; no reason to terminate the line, just accumulate
      ; the size in cur-len
      :else [out (+ cur-len count-s) nil comment?])))
; (assoc in 1 (+ cur-len count-s) 2 nil 3 comment?))))

(defn generate-ll
  [count-comment? [out cur-len just-eol? just-comment? :as in]
   [s _ tag :as element]]
  (let [[l r] (if (or (= tag :whitespace) (= tag :indent) (= tag :newline))
                (split-lf-2 s)
                #_(clojure.string/split s #"\n" 2)
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
  (let [length-vec
          (first ; this final accumulate-ll is to terminate the last line,
                 ; the one in progress
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
    #_(prn "line-lengths: style-vec:" style-vec
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
          result [(count lengths) (apply max lengths) lengths]
          dbg-ge (:dbg-ge options)
          what (when (and dbg-ge (= (find-what style-vec) dbg-ge)) dbg-ge)]
      #_(when (not= lengths lengths-iter) (swap! lldiff conj style-vec))
      (if what (conj result what) result))))

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
   "`" :quote,
   "~" :quote,
   "~@" :quote,
   "@" :deref})


(defn zcolor-map
  "Look up the thing in the zprint-color-map.  Accepts keywords or
  strings."
  [{:keys [color-map], :as options} key-or-str]
  (color-map (if (keyword? key-or-str) key-or-str (str->key key-or-str))))


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
  (dbg options "fzprint-hang-unless-fail:" (zstring (zfirst zloc)))
  (let [hanging (fzfn (in-hang options) hindent zloc)]
    (dbg-form
      options
      "fzprint-hang-unless-fail: exit:"
      (if (and hanging (fzfit options (style-lines options hindent hanging)))
        [:hang hanging]
        ; hang didn't work, do flow
        (do (dbg options "fzprint-hang-unless-fail: hang failed, doing flow")
            [:flow
             (concat-no-nil [[(str "\n" (blanks findent)) :none :indent]]
                            (fzfn options findent zloc))])))))

(defn replace-color
  "Given a style-vec with exactly one thing in it, replace the color
  with whatever local color we have determined is correct."
  [local-color style-vec]
  (if (= (count style-vec) 1)
    (let [[[string color element]] style-vec] [[string local-color element]])
    style-vec))

(declare fzprint-binding-vec)
(declare middle-element?)

(defn fzprint-two-up
  "Print a single pair of things (though it might not be exactly a
  pair, given comments and :extend and the like), like bindings in
  a let, clauses in a cond, keys and values in a map.  Controlled
  by various maps, the key of which is caller.  This will return a
  style-vec (or nil), unless hangflow? is true, in which case it
  will return [:hang <style-vec>] or [:flow <style-vec>] so that
  the upstream folks know whether this was a hang or flow and can
  do the right thing based on that."
  [caller
   {:keys [one-line? dbg? dbg-indent in-hang? do-in-hang? map-depth],
    {:keys [hang? dbg-local? dbg-cnt? indent indent-arg flow? key-color
            key-depth-color key-value-color]}
      caller,
    :as options} ind commas? justify-width rightmost-pair?
   [lloc rloc xloc :as pair]]
  (if dbg-cnt? (println "two-up: caller:" caller "hang?" hang? "dbg?" dbg?))
  (if (or dbg? dbg-local?)
    (println (or dbg-indent "")
             "==========================" (str "\n" (or dbg-indent ""))
             "fzprint-two-up:" (zstring lloc)
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
             "commas?" commas?
             "rightmost-pair?" rightmost-pair?))
  (let [local-hang? (or one-line? hang?)
        indent (or indent indent-arg)
        local-options
          (if (not local-hang?) (assoc options :one-line? true) options)
        loptions (c-r-pair commas? rightmost-pair? nil options)
        roptions (c-r-pair commas? rightmost-pair? :rightmost options)
        local-roptions
          (c-r-pair commas? rightmost-pair? :rightmost local-options)
        ; If we have a key-value-color map, and the key we have matches any
        ; of the keys in the map, then merge the resulting color-map elements
        ; into the current color-map.  Could be problematic if lloc is a
        ; modifier, but at present modifiers are only for extend and
        ; key-value-color is only for maps, so they can't both show up
        ; at once.
        value-color-map (and key-value-color (key-value-color (zsexpr lloc)))
        local-roptions (if value-color-map
                         (merge-deep local-roptions
                                     {:color-map value-color-map})
                         local-roptions)
        roptions (if value-color-map
                   (merge-deep roptions {:color-map value-color-map})
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
        local-color (if key-color (key-color (zsexpr lloc)) local-color)
        #_local-color
        #_(cond (and map-depth (= caller :map) (= map-depth 2)) :green
                (and map-depth (= caller :map) (= map-depth 1)) :blue
                (and map-depth (= caller :map) (= map-depth 3)) :yellow
                (and map-depth (= caller :map) (= map-depth 4)) :red
                :else nil)
        arg-1 (fzprint* loptions ind lloc)
        arg-1 (if local-color (replace-color local-color arg-1) arg-1)
        ; If we are going to print the second thing on the line, we need
        ; to know how big the first thing is, so we can see if the second
        ; thing fits on the line.
        [arg-1-line-count arg-1-max-width :as arg-1-lines]
          (style-lines options ind arg-1)
        ; If arg-1 already takes multiple lines, we aren't going to do
        ; anything interesting with a modifier.
        _ (dbg options
               "fzprint-two-up before modifier: arg-1-line-count:"
                 arg-1-line-count
               "arg-1-max-width:" arg-1-max-width)
        modifier?
          (if (and arg-1-line-count (> arg-1-line-count 1)) nil modifier?)
        ; See if we can merge the first and second things and have them
        ; stay on the same line?
        combined-arg-1 (if modifier?
                         (concat-no-nil arg-1
                                        [[(str " ") :none :whitespace]]
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
        _ (dbg options
               "fzprint-two-up after modifier: arg-1-line-count:"
                 arg-1-line-count
               "arg-1-max-width:" arg-1-max-width)
        lloc (if modifier? rloc lloc)
        rloc (if modifier? xloc rloc)
        ;     arg-1-fit-oneline? (and (not force-nl?)
        ;                             (fzfit-one-line loptions arg-1-lines))
        arg-1-fit-oneline? (and (not flow?)
                                (fzfit-one-line loptions arg-1-lines))
        arg-1-fit? (or arg-1-fit-oneline?
                       (when (not one-line?) (fzfit loptions arg-1-lines)))
        ; sometimes arg-1-max-width is nil because fzprint* returned nil,
        ; but we need to have something for later code to use as a number
        arg-1-width (- (or arg-1-max-width 0) ind)]
    ; If we don't *have* an arg-1, no point in continuing...
    ;  If arg-1 doesn't fit, maybe that's just how it is!
    ;  If we are in-hang, then we can bail, but otherwise, not.
    (when (and arg-1 (or arg-1-fit? (not in-hang?)))
      (cond
        (= (count pair) 1) [:hang (fzprint* roptions ind lloc)]
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
                hanging-spaces
                  (if justify-width (inc (- justify-width arg-1-width)) 1)
                hanging-indent (+ 1 hanging-width ind)
                flow-indent (+ indent ind)]
            (if (and (zstring lloc)
                     (keyword-fn? options (zstring lloc))
                     (zvector? rloc))
              ; This is an embedded :let or :when-let or something
              ; Presently we assume that anything with a vector after something
              ; that is a keyword must be one of these, but we could check
              ; for a :binding fn-style instead which might make more sense.
              (let [[hang-or-flow style-vec] (fzprint-hang-unless-fail
                                               loptions
                                               hanging-indent
                                               flow-indent
                                               fzprint-binding-vec
                                               rloc)
                    arg-1 (if (= hang-or-flow :hang)
                            (concat-no-nil arg-1
                                           [[(blanks hanging-spaces) :none
                                             :whitespace]])
                            arg-1)]
                [hang-or-flow (concat-no-nil arg-1 style-vec)])
              ; This is a normal two element pair thing
              (let [; Perhaps someday we could figure out if we are already
                    ; completely in flow to this point, and be smarter about
                    ; possibly dealing with the hang or flow now.  But for
                    ; now, we will simply do hang even if arg-1 didn't fit
                    ; on one line if the flow indent isn't better than the
                    ; hang indent.
                    _ (dbg options
                           "fzprint-two-up: before hang.  hanging tried?"
                           (and arg-1-fit-oneline?
                                (and (not flow?)
                                     (>= flow-indent hanging-indent))))
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
                    fit? (fzfit-one-line local-roptions hanging-lines)
                    hanging-lines (if fit?
                                    hanging-lines
                                    (when (and (not one-line?) hang?)
                                      hanging-lines))
                    ; Don't flow if it fit, or it didn't fit and we were doing
                    ; one line on input.  Do flow if we don't have
                    ; hanging-lines
                    ; and we were not one-line on input.
                    _ (log-lines options
                                 "fzprint-two-up: hanging-2:"
                                 hanging-indent
                                 hanging)
                    flow-it? (and (or (and (not hanging-lines) (not one-line?))
                                      (not (or fit? one-line?)))
                                  ; this is for situations where the first
                                  ; element is short and so the hanging indent
                                  ; is the same as the flow indent, so there
                                  ; is
                                  ; no point in flow -- unless we don't have
                                  ; any hanging-lines, in which case we better
                                  ; do flow
                                  (or (< flow-indent hanging-indent)
                                      (not hanging-lines)))
                    _ (dbg options
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
                  (prn "fzprint-two-up: flow-it?:" flow-it?)
                  (prn "fzprint-two-up: fit?:" fit?)
                  (prn "fzprint-two-up: flow-indent:" flow-indent)
                  (prn "fzprint-two-up: hanging:" (zstring lloc) hanging)
                  (prn "fzprint-two-up: (+ indent ind):" (+ indent ind))
                  (prn "fzprint-two-up: flow:" (zstring lloc) flow))
                (dbg options "fzprint-two-up: before good-enough")
                (if fit?
                  [:hang
                   (concat-no-nil arg-1
                                  [[(blanks hanging-spaces) :none :whitespace]]
                                  hanging)]
                  (when (or hanging-lines flow-lines)
                    (if (good-enough? caller
                                      roptions
                                      :none-two-up
                                      hang-count
                                      (- hanging-indent flow-indent)
                                      hanging-lines
                                      flow-lines)
                      [:hang
                       (concat-no-nil arg-1
                                      [[(blanks hanging-spaces) :none
                                        :whitespace]]
                                      hanging)]
                      (if justify-width
                        nil
                        [:flow
                         (concat-no-nil arg-1
                                        [[(str "\n" (blanks (+ indent ind)))
                                          :none :indent]]
                                        flow)])))))))
        :else [:flow ; The following always flows things of 3 or more
               ; (absent modifers).  If the lloc is a single char,
               ; then that can look kind of poor.  But that case
               ; is rare enough that it probably isn't worth dealing
               ; with.  Possibly a hang-remaining call might fix it.
               (concat-no-nil
                 arg-1
                 #_(fzprint* loptions ind lloc)
                 [[(str "\n" (blanks (+ indent ind))) :none :indent]]
                 ; This is a real seq, not a zloc seq
                 #_(fzprint-remaining-seq options
                                          (+ indent ind)
                                          nil
                                          :force-nl
                                          (next pair))
                 (fzprint-flow-seq options
                                   (+ indent ind)
                                   (if modifier? (nnext pair) (next pair))
                                   :force-nl))]))))

;;
;; # Two-up printing
;;

(defn fzprint-justify-width
  "Figure the width for a justification of a set of pairs in coll.  
  Also, decide if it makes any sense to justify the pairs at all.
  For instance, they all need to be one-line."
  [caller {{:keys [justify?]} caller, :as options} ind coll]
  (let [firsts (remove nil?
                 (map #(when (> (count %) 1) (fzprint* options ind (first %)))
                   coll))
        #_(def just firsts)
        style-seq (map (partial style-lines options ind) firsts)
        #_(def styleseq style-seq)
        each-one-line? (reduce #(when %1 (= (first %2) 1)) true style-seq)
        #_(def eol each-one-line?)
        justify-width (when each-one-line?
                        (reduce #(max %1 (second %2)) 0 style-seq))]
    (when justify-width (- justify-width ind))))

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
  "Convert a hangflow style-vec to a regular style-vec."
  [hf-style-vec]
  (when hf-style-vec (map second hf-style-vec)))

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
   {{:keys [justify? force-nl?]} caller,
    :keys [width rightcnt one-line? parallel?],
    :as options} ind commas? coll]
  (let [caller-map (caller options)
        len (count coll)
        justify-width (when (and justify? (not one-line?))
                        (fzprint-justify-width caller options ind coll))
        caller-options (when justify-width (options caller))]
    (dbg-print options
               "fzprint-map-two-up: one-line?" (:one-line? options)
               "justify?:" justify?)
    ; If it is one-line? and force-nl? and there is more than one thing,
    ; this can't work.
    (when (not (and one-line? force-nl? (> len 1)))
      #_(def jo [])
      (loop [justify-width justify-width
             justify-options
               (if justify-width
                 (-> options
                     (merge-deep {caller (caller-options :justify-hang)})
                     (merge-deep {:tuning (caller-options :justify-tuning)}))
                 options)]
        #_(def jo (conj jo [justify-width justify-options]))
        (let [beginning-coll (butlast coll)
              beginning-remaining
                (if one-line? (fit-within? (- width ind) beginning-coll) true)
              _ (dbg options
                     "fzprint-map-two-up: remaining:" (- width ind)
                     "beginning-remaining:" beginning-remaining)
              ;"(butlast coll):" (butlast coll))
              beginning (when beginning-remaining
                          (zpmap options
                                 (partial fzprint-two-up
                                          caller
                                          justify-options
                                          ind
                                          commas?
                                          justify-width
                                          nil)
                                 beginning-coll))
              ; this line will fix the justify, but not necessarily
              ; the rest of the problems with hangflow output -- like
              ; the style-lines below.
              beginning (if (contains-nil? beginning) nil beginning)
              end-coll [(last coll)]
              end-remaining (if one-line?
                              (and beginning
                                   (fit-within? (- beginning-remaining rightcnt)
                                                end-coll))
                              true)
              _ (dbg options
                     "fzprint-map-two-up: beginning-remaining:"
                       beginning-remaining
                     "rightcnt:" rightcnt
                     "end-remaining:" end-remaining)
              ;"(last coll):" (last coll))
              end (when end-remaining
                    (when-let [end-result (fzprint-two-up caller
                                                          justify-options
                                                          ind
                                                          commas?
                                                          justify-width
                                                          :rightmost-pair
                                                          (first end-coll))]
                      [end-result]))
              result (cond (= len 1) end
                           :else (concat-no-nil beginning end))]
          (dbg options
               "fzprint-map-two-up: len:" len
               "(nil? end):" (nil? end)
               "end:" end
               "(nil? beginning):" (nil? beginning)
               "beginning:" beginning
               "(count end):" (count end)
               "(count beginnging):" (count beginning)
               "justify-width:" justify-width
               "result:" result)
          ; if we got a result or we didn't but it wasn't because we
          ; were trying to justify things
          (if (or result (not justify-width))
            result
            ; try again, without justify-width
            (recur nil options)))))))

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
  (if (and sort? (if in-code? sort-in-code? true))
    (sort #((partial compare-ordered-keys (or key-value {}) (zdotdotdot))
             (zsexpr (access %1))
             (zsexpr (access %2)))
          out)
    out))

(defn pair-element?
  "This checks to see if an element should be considered part of a
  pair if it comes between other elements, and a single element on
  its own if it would otherwise be the first part of a pair.  Mostly
  this will trigger on comments, but a #_(...) element will also
  trigger this."
  [zloc]
  (or (zcomment? zloc) (zuneval? zloc)))

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
  "Input is (zseqnws zloc) where one assumes that these are pairs.
  Thus, a seq of zlocs.  Output is a sequence of seqs, where the
  seqs are usually pairs, but might be single things.  Doesn't pair
  up comments or #_(...) unevaled sexpressions.  The ones before
  the first part of a pair come as a single element in what would
  usually be a pair, and the ones between the first and second parts
  of a pair come inside the pair.  There may be an arbitrary number
  of elements between the first and second elements of the pair
  (one per line).  If there are any comments or unevaled sexpressions,
  don't sort the keys, as we might lose track of where the comments
  or unevaled s-expressions go."
  [{:as options, :keys [max-length]} coll]
  (when-not (empty? coll)
    (loop [remaining coll
           no-sort? nil
           index 0
           out (transient [])]
      (if-not remaining
        [no-sort? (persistent! out)]
        (let [[new-remaining pair-vec new-no-sort?]
                (cond
                  (pair-element? (first remaining)) [(next remaining)
                                                     [(first remaining)] true]
                  (or (pair-element? (second remaining))
                      (middle-element? options (second remaining)))
                    (let [[comment-seq rest-seq]
                            ;(split-with pair-element? (next remaining))
                            (split-with #(or (pair-element? %)
                                             (middle-element? options %))
                                        (next remaining))]
                      [(next rest-seq)
                       (into []
                             (concat [(first remaining)]
                                     comment-seq
                                     [(first rest-seq)])) true])
                  (= (count remaining) 1) [(next remaining) [(first remaining)]
                                           nil]
                  :else [(next (next remaining))
                         [(first remaining) (second remaining)] nil])]
          (recur (if (not= index max-length) new-remaining (list (zdotdotdot)))
                 (or no-sort? new-no-sort?)
                 (inc index)
                 (conj! out pair-vec)))))))

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
            (split-with #(not (or (zcoll? %) (zreader-cond-w-coll? %)))
                        rev-seq)
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
  a protocol and the remaining thing in that seq are the expressions that
  follow.  If there is a single thing, it is returned in its own internal
  seq. ((P (foo [this a) (bar-me [this] b) (barx [this y] (+ c y))) ...)
  Made harder by the fact that the symbol might be inside of a #?() reader
  conditional.  It handles comments before symbols on the symbol indent, 
  and the comments before the collections on the collection indent.  
  Since it doesn't know how many collections there are, this is not trivial.  
  Must be called with a sequence of z-things"
  [options modifier-set coll]
  #_(prn "partition-all-sym-static:" modifier-set)
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
      #_(prn "remaining:" remaining)
      #_(prn "out:" out)
      ;(prn "remaining:" (map (comp zstring first) remaining))
      ;(prn "out:" (map (comp zstring first) out))
      (if (empty? remaining)
        (persistent! out)
        (let [[next-remaining new-out]
                (cond
                  (and (or (zsymbol? (ffirst remaining))
                           (znil? (ffirst remaining))
                           (zreader-cond-w-symbol? (ffirst remaining)))
                       (not (empty? (second remaining))))
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
                      (do #_(prn "b:")
                          (if (and modifier-set
                                   (modifier-set (zstring (ffirst remaining))))
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
                                                (second (first remaining))))]))
                            ; we have more than one non-coll in first
                            ; remaining, so pull one out, and leave the
                            ; next ones for the next loop
                            [(cons (next (first remaining)) (next remaining))
                             (conj! out (list (ffirst remaining)))])))
                  :else [(next remaining) (conj! out (first remaining))])]
          (recur next-remaining new-out))))))

(defn rstr-vec
  "Create an r-str-vec with, possibly, a newline at the beginning if
  the last thing before it is a comment."
  ([options ind zloc r-str r-type]
   (let [nl (when (zcomment? (zlast zloc))
              [[(str "\n" (blanks ind)) :none :indent]])]
     (concat nl
             [[r-str (zcolor-map options (or r-type r-str))
               (or r-type :right)]])))
  ([options ind zloc r-str] (rstr-vec options ind zloc r-str nil)))

(defn fzprint-binding-vec
  [{{:keys [nl-separator?]} :binding, :as options} ind zloc]
  (dbg options "fzprint-binding-vec:" (zstring (zfirst zloc)))
  (let [options (rightmost options)
        l-str "["
        r-str "]"
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (inc ind) zloc r-str)]
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
                      (second (partition-all-2-nc options (zseqnws zloc)))))
                  r-str-vec)))))

(defn fzprint-hang
  "Try to hang something and try to flow it, and then see which is
  better.  Has hang and flow indents. fzfn is the function to use 
  to do zloc.  Note what fzfn does with the input.  For instance,
  fzprint-pairs does a (zmap-right identity zloc).  Presumably the
  caller knows what the fzfn does, so it has to count the items
  itself and pass it in here as zloc-count if it isn't just (zcount zloc)."
  [{:keys [one-line?], :as options} caller hindent findent fzfn zloc-count zloc]
  (dbg options "fzprint-hang:" (zstring (zfirst zloc)) "caller:" caller)
  (let [hanging (when (and (not= hindent findent) ((options caller) :hang?))
                  (concat-no-nil [[(str " ") :none :whitespace]]
                                 (fzfn (in-hang options) hindent zloc)))
        hang-count (or zloc-count (zcount zloc))
        hr-lines (style-lines options (dec hindent) hanging)
        ;flow (fzfn options findent zloc)
        ]
    (if (or (fzfit-one-line options hr-lines) one-line?)
      hanging
      (let [flow (concat-no-nil [[(str "\n" (blanks findent)) :none :indent]]
                                (fzfn options findent zloc))
            _ (log-lines options "fzprint-hang: flow:" findent flow)
            fd-lines (style-lines options findent flow)
            _ (dbg options
                   "fzprint-hang: ending: hang-count:" hang-count
                   "hanging:" hanging
                   "flow:" flow)
            hr-good? (when (:hang? (caller options))
                       (good-enough? caller
                                     options
                                     :none-hang
                                     hang-count
                                     (- hindent findent)
                                     hr-lines
                                     fd-lines))]
        (if hr-good? hanging flow)))))

(defn fzprint-pairs
  "Always prints pairs on a different line from other pairs."
  [{{:keys [nl-separator?]} :pair, :as options} ind zloc]
  (dbg options "fzprint-pairs:" (zstring (zfirst zloc)))
  (dbg-form
    options
    "fzprint-pairs: exit:"
    (interpose-nl-hf
      (:pair options)
      ind
      (fzprint-map-two-up
        :pair
        options
        ind
        false
        (let [[_ part] (partition-all-2-nc options (zmap-right identity zloc))]
          #_(def fp part)
          (dbg options
               "fzprint-pairs: partition:"
               (map (comp zstring first) part))
          part)))))

(defn fzprint-extend
  "Print things with a symbol and collections following.  Kind of like with
  pairs, but not quite. This skips over zloc and does everything to the
  right of it!"
  [{{:keys [nl-separator?]} :extend, :as options} ind zloc]
  #_(def fezloc zloc)
  (dbg options "fzprint-extend:" (zstring (zfirst zloc)))
  (dbg-form
    options
    "fzprint-extend: exit:"
    (interpose-nl-hf
      (:extend options)
      ind
      (fzprint-map-two-up
        :extend
        (assoc options :fn-style :fn)
        ind
        false
        (let [part (partition-all-sym options
                                      (:modifiers (:extend options))
                                      (zmap-right identity zloc))]
          #_(def fe part)
          (dbg options "fzprint-extend: partition:" (map #(map zstring %) part))
          part)))))

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
  [options ind zloc]
  (dbg-print options "fzprint-one-line:")
  (let [seq-right (zmap identity zloc)
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
                      ; is only one
                      ; element in the list -- it needs to have
                      ; the rightcnt
                      ; passed through
                      (= index last-index) [(if-not (zero? index)
                                              [[" " :none :whitespace]])
                                            options]
                      (= index 0) [nil (not-rightmost options)]
                      :else [[[" " :none :whitespace]] (not-rightmost options)])
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
  "Take a seq of a zloc, created by (zmap identity zloc) when zloc
  is a collection, or (zmap-right identity zloc) when zloc is already
  inside of a collection, and return a seq of the fzprint* of each 
  element.  No spacing between any of these elements. Note that this
  is not a style-vec, but a seq of style-vecs of each of the elements.
  These would need to be concatenated together to become a style-vec.
  ind is either a constant or a seq of indents, one for each element in
  zloc-seq."
  [{:keys [max-length], :as options} ind zloc-seq]
  (let [len (count zloc-seq)
        zloc-seq (if (> len max-length)
                   (concat (take max-length zloc-seq) (list (zdotdotdot)))
                   zloc-seq)]
    (dbg options "fzprint-seq: (count zloc-seq):" len)
    (when-not (empty? zloc-seq)
      (let [left (zpmap options
                        #(fzprint* (not-rightmost options) %1 %2)
                        (if (coll? ind) ind (repeat ind))
                        (butlast zloc-seq))
            right [(fzprint* options
                             (if (coll? ind) (last ind) ind)
                             (last zloc-seq))]]
        (cond (= len 1) right
              :else (concat-no-nil left right))))))

(defn fzprint-flow-seq
  "Take a seq of a zloc, created by (zmap identity zloc) or
  and return a style-vec of the result.  Either it fits on one line, 
  or it is rendered on multiple lines.  You can force multiple lines 
  with force-nl?. If you want it to do less than everything in the 
  original zloc, modify the result of (zmap identity zloc) to just 
  contain what you want to print. ind is either a single indent,
  or a seq of indents, one for each element in zloc-seq."
  ([options ind zloc-seq force-nl?]
   (dbg options "fzprint-flow-seq: count zloc-seq:" (count zloc-seq))
   (let [coll-print (fzprint-seq options ind zloc-seq)
         one-line (apply concat-no-nil
                    (interpose [[" " :none :whitespace]] coll-print))
         _ (log-lines options "fzprint-flow-seq:" ind one-line)
         one-line-lines (style-lines options ind one-line)]
     (dbg-form
       options
       "fzprint-flow-seq: exit:"
       (if (and (not force-nl?) (fzfit-one-line options one-line-lines))
         one-line
         (apply concat-no-nil
           (if (coll? ind)
             (drop 1
                   (interleave
                     (map #(vector [(str "\n" (blanks %)) :none :indent]) ind)
                     coll-print))
             (interpose [[(str "\n" (blanks ind)) :none :indent]]
               coll-print)))))))
  ([options ind zloc-seq] (fzprint-flow-seq options ind zloc-seq nil)))


(defn fzprint-hang-one
  "Try out the given zloc, and if it fits on the current line, just
  do that. It might fit on the same line, as this may not be the rest
  of the list that we are printing. If not, check it out with good-enough?
  and do the best you can.  Three choices, really: fits on same line, 
  does ok as hanging, or better with flow. hindent is hang-indent, and 
  findent is flow-indent, and each contains the initial separator.  
  Might be nice if the fn-style actually got sent to this fn."
  [caller {:keys [one-line?], :as options} hindent findent zloc]
  (dbg options "fzprint-hang-one: hindent:" hindent "findent:" findent)
  (when (:dbg-hang options)
    (println (dots (:pdepth options))
             "h1 caller:"
             caller
             (zstring (if (zcoll? zloc) (zfirst zloc) zloc))))
  (let [local-options (if (and (not one-line?) (not (:hang? (caller options))))
                        (assoc options :one-line? true)
                        options)
        hanging (when (not= hindent findent)
                  (fzprint* (in-hang local-options) hindent zloc))
        hang-count (zcount zloc)
        hanging (concat-no-nil [[" " :none :whitespace]] hanging)
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
      (let [flow (concat-no-nil [[(str "\n" (blanks findent)) :none :indent]]
                                (fzprint* options findent zloc))
            _ (log-lines options "fzprint-hang-one: flow:" findent flow)
            fd-lines (style-lines options findent flow)
            _ (dbg options "fzprint-hang-one: fd-lines:" fd-lines)
            _ (dbg options
                   "fzprint-hang-one: ending: hang-count:" hang-count
                   "hanging:" (pr-str hanging)
                   "flow:" (pr-str flow))
            hr-good? (and (:hang? (caller options))
                          (good-enough? caller
                                        options
                                        :none-hang-one
                                        hang-count
                                        (- hindent findent)
                                        hr-lines
                                        fd-lines))]
        (if hr-good? hanging flow)))))

;;
;; # Constant pair support
;;

(defn count-constant-pairs
  "Given a seq of zlocs, work backwards from the end, and see how
  many elements are pairs of constants (using zconstant?).  So that
  (... :a (stuff) :b (bother)) returns 4, since both :a and :b are
  zconstant? true. This is made more difficult by having to skip
  comments along the way as part of the pair check, but keep track
  of the ones we skip so the count is right in the end.  We don't
  expect any whitespace in this, because this seq should have been
  produced by zmap-right or its equivalent, which already skips the
  whitespace."
  [seq-right]
  (loop [seq-right-rev (reverse seq-right)
         element-count 0
         ; since it is reversed, we need a constant second
         constant-required? nil
         pair-size 0]
    (let [element (first seq-right-rev)]
      (if (empty? seq-right-rev)
        ; remove potential elements of this pair, since we haven't
        ; seen the end of it
        (- element-count pair-size)
        (let [comment? (zcomment? element)]
          (if (and (not comment?) constant-required? (not (zconstant? element)))
            ; we counted the right-hand and any comments of this pair, but it
            ; isn't a pair so exit now with whatever we have so far
            (- element-count pair-size)
            (recur (next seq-right-rev)
                   (inc element-count)
                   (if comment? constant-required? (not constant-required?))
                   (if (and constant-required? (not comment?))
                     ; must be a constant, so start count over
                     0
                     (inc pair-size)))))))))

(defn constant-pair
  "Argument is result of (zmap-right identity zloc), that is to say
  a seq of zlocs.  Output is a [pair-seq non-paired-item-count],
  if any.  If there are no pair-seqs, pair-seq must be nil, not
  an empty seq."
  [caller {{:keys [constant-pair? constant-pair-min]} caller, :as options}
   seq-right]
  (if constant-pair?
    (let [paired-item-count (count-constant-pairs seq-right)
          non-paired-item-count (- (count seq-right) paired-item-count)
          _ (dbg options
                 "constant-pair: non-paired-items:"
                 non-paired-item-count)
          pair-seq (when (>= paired-item-count constant-pair-min)
                     (second (partition-all-2-nc options
                                                 (drop non-paired-item-count
                                                       seq-right))))]
      [pair-seq non-paired-item-count])
    [nil (count seq-right)]))

;;
;; # Take into account constant pairs
;;

(declare interpose-either-nl-hf)

(declare fzprint-hang-remaining)

#_(defn fzprint-hang-remaining-perf-vs-format
    "zloc is already down inside a collection, it is not the collection
  itself. Operate on what is to the right of zloc.  We already know
  that the given zloc won't fit on the current line. [Besides, we
  ensure that if there are two things remaining anyway. ???] So
  now, try hanging and see if that is better than flow.  Unless
  :hang? is nil, in which case we will just flow.  hindent is
  hang-indent, and findent is flow-indent. This should never be
  called with :one-line because this is only called from fzprint-list*
  after the one-line processing is done. If the hindent equals the
  flow indent, then just do flow.  Do only zloc-count non-whitespace
  elements of zloc."
    ([caller
      {:keys [dbg? depth perf-vs-format],
       {:keys [hang? constant-pair? constant-pair-min hang-expand hang-diff
               nl-separator?]}
         caller,
       :as options} hindent findent zloc fn-style zloc-count]
     (when (:dbg-hang options)
       (println (dots (:pdepth options)) "hr" (zstring zloc)))
     (dbg options
          "fzprint-hang-remaining:" (zstring zloc)
          "hindent:" hindent
          "findent:" findent
          "caller:" caller
          "nl-separator?:" nl-separator?)
     ; (in-hang options) slows things down here, for some reason
     (let [seq-right (zmap-right identity zloc)
           seq-right (if zloc-count (take zloc-count seq-right) seq-right)
           [pair-seq non-paired-item-count]
             (constant-pair caller options seq-right)
           _ (dbg options
                  "fzprint-hang-remaining count pair-seq:"
                  (count pair-seq))
           hang? (and hang?
                      ; This is a key for "don't hang no matter what", it isn't
                      ; about making it prettier. People call this routine with
                      ; these values equal to ensure that it always flows.
                      (not= hindent findent)
                      ;flow-lines
                      ;;TODO make this uneval!!!
                      #_(or (<= (- hindent findent) hang-diff)
                            (<= (/ (dec (first flow-lines)) (count seq-right))
                                hang-expand)))
           ; The zfuture options, below, kicks this off in a separate thread,
           ; and the subsequent zat waits for it to complete.
           hanging
             (#?@(:clj [zfuture options]
                  :cljs [do])
              (let [hang-result
                      (when hang?
                        (if-not pair-seq
                          ; There are no paired elements
                          (apply concat-no-nil
                            (interpose [[(str "\n" (blanks hindent)) :none
                                         :indent]]
                              (fzprint-seq (in-hang options)
                                           hindent
                                           seq-right)))
                          (if (not (zero? non-paired-item-count))
                            (concat-no-nil
                              ; The elements that are not paired
                              (dbg-form
                                options
                                "fzprint-hang-remaining: mapv:"
                                (apply concat-no-nil
                                  (interpose [[(str "\n" (blanks hindent)) :none
                                               :indent]]
                                    (zpmap
                                      options
                                      (partial fzprint*
                                               (not-rightmost (in-hang options))
                                               hindent)
                                      (take non-paired-item-count seq-right)))))
                              ; Got to separate them because they were done in
                              ; two
                              ; pieces
                              [[(str "\n" (blanks hindent)) :none :indent]]
                              ; The elements that are paired
                              (dbg-form options
                                        "fzprint-hang-remaining: fzprint-hang:"
                                        (interpose-nl-hf
                                          (:pair options)
                                          hindent
                                          (fzprint-map-two-up :pair
                                                              ;caller
                                                              (in-hang options)
                                                              hindent
                                                              nil
                                                              pair-seq))))
                            ; All elements are paired
                            (interpose-nl-hf
                              (:pair options)
                              hindent
                              (fzprint-map-two-up :pair
                                                  ;caller
                                                  (in-hang options)
                                                  hindent
                                                  nil
                                                  pair-seq)))))]
                [hang-result (style-lines options hindent hang-result)]))
           flow
             (#?@(:clj [zfuture options]
                  :cljs [do])
              (let [flow-result
                      (if-not pair-seq
                        ; We don't have any constant pairs
                        (apply concat-no-nil
                          (interpose [[(str "\n" (blanks findent)) :none
                                       :indent]]
                            (fzprint-seq options findent seq-right)))
                        (if (not (zero? non-paired-item-count))
                          ; We have constant pairs, ; but they follow
                          ; some stuff that isn't paired.
                          (concat-no-nil
                            ; The elements that are not pairs
                            (apply concat-no-nil
                              (interpose [[(str "\n" (blanks findent)) :none
                                           :indent]]
                                (zpmap options
                                       (partial fzprint*
                                                (not-rightmost options)
                                                findent)
                                       (take non-paired-item-count seq-right))))
                            ; Got to separate them since we are doing them in
                            ; two
                            ; pieces
                            [[(str "\n" (blanks findent)) :none :indent]]
                            ; The elements that are constant pairs
                            (interpose-nl-hf (:pair options)
                                             findent
                                             (fzprint-map-two-up :pair
                                                                 ;caller
                                                                 options
                                                                 findent
                                                                 nil
                                                                 pair-seq)))
                          ; This code path is where we have all constant pairs.
                          (interpose-nl-hf (:pair options)
                                           findent
                                           (fzprint-map-two-up :pair
                                                               ;caller
                                                               options
                                                               findent
                                                               nil
                                                               pair-seq))))]
                [flow-result (style-lines options findent flow-result)]))
           ; Now that we have also kicked off a flow, let's see if there
           ; is any point in waiting for it?
           [hanging hanging-lines] (zat options hanging)
           hang-count (count seq-right)
           _ (log-lines options
                        "fzprint-hang-remaining: hanging:"
                        hindent
                        hanging)
           _ (dbg options
                  "fzprint-hang-remaining: hanging-lines:" hanging-lines
                  "hang-count:" hang-count)
           ; flow? is -- should we wait for the flow to complete, or ignore it?
           flow?
             (if perf-vs-format
               (if (> depth perf-vs-format)
                 ;this is "if it hangs, take it": (if hanging nil true)
                 ;this is "if it hangs and isn't too bad, take it"
                 (if (and hanging (number? (first hanging-lines)))
                   (not (<= (/ (dec (first hanging-lines)) (count seq-right))
                            hang-expand))
                   true)
                 true)
               true)
           #_(options (let [[_ _ _ b-what] flow-lines]
                        (if b-what (assoc options :dbg? true) options)))
           #_(dbg options
                  "fzprint-hang-remaining: *=*=*=*=*=*" (zstring zloc)
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
           ; Yes, and this caused a proble when I put in the
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
           ; We used to calculate hang-count by doing the hang an then counting
           ; the output.  But ultimately this is simple a series of map calls
           ; to the elements of seq-right, so we go right to the source for this
           ; number now.  That let's us move the interpose calls above this
           ; point.
           [flow flow-lines] (when flow? (zat options flow))]
       (dbg options "fzprint-hang-remaining: flow-lines:" flow-lines)
       (when dbg?
         (if (zero? hang-count)
           (println "hang-count = 0:" (str (zmap-right zstring zloc)))))
       (log-lines options "fzprint-hang-remaining: flow" findent flow)
       (if flow-lines
         (if (good-enough? caller
                           options
                           fn-style
                           hang-count
                           (- hindent findent)
                           hanging-lines
                           flow-lines)
           (concat-no-nil [[" " :none :whitespace]] hanging)
           (concat-no-nil [[(str "\n" (blanks findent)) :none :indent]] flow))
         (when hanging-lines
           (concat-no-nil [[" " :none :whitespace]] hanging)))))
    ([caller options hindent findent zloc fn-style]
     (fzprint-hang-remaining caller options hindent findent zloc fn-style nil)))

;
; This fzprint-hang-remaining doesn't overlap the hang and the flow,
; but does the hang first, and if we are at a sufficient depth in
; perf-vs-format, then we don't flow if the hang worked.  A better
; approach would be to do the overlapped hang and flow unless we
; were at or beyond that critical depth, and then do one or the other.
; Even with the totally serial nature of this, it isn't terribly slower
; when perf-vs-format is nil, and it is maybe 86% of the time it takes
; when perf-vs-format is 5, for fzprint-list*.  Which isn't nothing.
; Obviously only interesting on a function where it gets deeper than
; whatever depth you specify.  Not going to use it now, but not going
; to get rid of it either.
;
#_(defn fzprint-hang-remaining-serial
    "zloc is already down inside a collection, it is not the collection
  itself. Operate on what is to the right of zloc.  We already know
  that the given zloc won't fit on the current line. [Besides, we
  ensure that if there are two things remaining anyway. ???] So
  now, try hanging and see if that is better than flow.  Unless
  :hang? is nil, in which case we will just flow.  hindent is
  hang-indent, and findent is flow-indent. This should never be
  called with :one-line because this is only called from fzprint-list*
  after the one-line processing is done. If the hindent equals the
  flow indent, then just do flow.  Do only zloc-count non-whitespace
  elements of zloc."
    ([caller
      {:keys [dbg? depth perf-vs-format],
       {:keys [hang? constant-pair? constant-pair-min hang-expand hang-diff
               nl-separator?]}
         caller,
       :as options} hindent findent zloc fn-style zloc-count]
     (when (:dbg-hang options)
       (println (dots (:pdepth options)) "hr" (zstring zloc)))
     (dbg options
          "fzprint-hang-remaining:" (zstring zloc)
          "hindent:" hindent
          "findent:" findent
          "caller:" caller
          "nl-separator?:" nl-separator?)
     ; (in-hang options) slows things down here, for some reason
     (let [seq-right (zmap-right identity zloc)
           seq-right (if zloc-count (take zloc-count seq-right) seq-right)
           [pair-seq non-paired-item-count]
             (constant-pair caller options seq-right)
           _ (dbg options
                  "fzprint-hang-remaining count pair-seq:"
                  (count pair-seq))
           hang? (and hang?
                      ; This is a key for "don't hang no matter what", it isn't
                      ; about making it prettier. People call this routine with
                      ; these values equal to ensure that it always flows.
                      (not= hindent findent)
                      ;flow-lines
                      ;;TODO make this uneval!!!
                      #_(or (<= (- hindent findent) hang-diff)
                            (<= (/ (dec (first flow-lines)) (count seq-right))
                                hang-expand)))
           ; The zfuture options, below, kicks this off in a separate thread,
           ; and the subsequent zat waits for it to complete.
           hanging
             (#?@(:clj [zfuture options]
                  :cljs [do])
              (let [hang-result
                      (when hang?
                        (if-not pair-seq
                          ; There are no paired elements
                          (apply concat-no-nil
                            (interpose [[(str "\n" (blanks hindent)) :none
                                         :indent]]
                              (fzprint-seq (in-hang options)
                                           hindent
                                           seq-right)))
                          (if (not (zero? non-paired-item-count))
                            (concat-no-nil
                              ; The elements that are not paired
                              (dbg-form
                                options
                                "fzprint-hang-remaining: mapv:"
                                (apply concat-no-nil
                                  (interpose [[(str "\n" (blanks hindent)) :none
                                               :indent]]
                                    (zpmap
                                      options
                                      (partial fzprint*
                                               (not-rightmost (in-hang options))
                                               hindent)
                                      (take non-paired-item-count seq-right)))))
                              ; Got to separate them because they were done in
                              ; two
                              ; pieces
                              [[(str "\n" (blanks hindent)) :none :indent]]
                              ; The elements that are paired
                              (dbg-form options
                                        "fzprint-hang-remaining: fzprint-hang:"
                                        (interpose-nl-hf
                                          (:pair options)
                                          hindent
                                          (fzprint-map-two-up :pair
                                                              ;caller
                                                              (in-hang options)
                                                              hindent
                                                              nil
                                                              pair-seq))))
                            ; All elements are paired
                            (interpose-nl-hf
                              (:pair options)
                              hindent
                              (fzprint-map-two-up :pair
                                                  ;caller
                                                  (in-hang options)
                                                  hindent
                                                  nil
                                                  pair-seq)))))]
                [hang-result (style-lines options hindent hang-result)]))
           [hanging hanging-lines] (zat options hanging)
           hang-count (count seq-right)
           _ (log-lines options
                        "fzprint-hang-remaining: hanging:"
                        hindent
                        hanging)
           _ (dbg options
                  "fzprint-hang-remaining: hanging-lines:" hanging-lines
                  "hang-count:" hang-count)
           flow?
             (if perf-vs-format
               (if (> depth perf-vs-format)
                 ;this is "if it hangs, take it": (if hanging nil true)
                 ;this is "if it hangs and isn't too bad, take it"
                 (if (and hanging (number? (first hanging-lines)))
                   (not (<= (/ (dec (first hanging-lines)) (count seq-right))
                            hang-expand))
                   true)
                 true)
               true)
           flow
             (#?@(:clj [zfuture options]
                  :cljs [do])
              (let [flow-result
                      (when flow?
                        (if-not pair-seq
                          ; We don't have any constant pairs
                          (apply concat-no-nil
                            (interpose [[(str "\n" (blanks findent)) :none
                                         :indent]]
                              (fzprint-seq options findent seq-right)))
                          (if (not (zero? non-paired-item-count))
                            ; We have constant pairs, ; but they follow
                            ; some stuff that isn't paired.
                            (concat-no-nil
                              ; The elements that are not pairs
                              (apply concat-no-nil
                                (interpose [[(str "\n" (blanks findent)) :none
                                             :indent]]
                                  (zpmap options
                                         (partial fzprint*
                                                  (not-rightmost options)
                                                  findent)
                                         (take non-paired-item-count
                                               seq-right))))
                              ; Got to separate them since we are doing them in
                              ; two
                              ; pieces
                              [[(str "\n" (blanks findent)) :none :indent]]
                              ; The elements that are constant pairs
                              (interpose-nl-hf (:pair options)
                                               findent
                                               (fzprint-map-two-up :pair
                                                                   ;caller
                                                                   options
                                                                   findent
                                                                   nil
                                                                   pair-seq)))
                            ; This code path is where we have all constant
                            ; pairs.
                            (interpose-nl-hf (:pair options)
                                             findent
                                             (fzprint-map-two-up :pair
                                                                 ;caller
                                                                 options
                                                                 findent
                                                                 nil
                                                                 pair-seq)))))]
                [flow-result (style-lines options findent flow-result)]))
           #_(options (let [[_ _ _ b-what] flow-lines]
                        (if b-what (assoc options :dbg? true) options)))
           #_(dbg options
                  "fzprint-hang-remaining: *=*=*=*=*=*" (zstring zloc)
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
           ; Yes, and this caused a proble when I put in the
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
           ; We used to calculate hang-count by doing the hang an then counting
           ; the output.  But ultimately this is simple a series of map calls
           ; to the elements of seq-right, so we go right to the source for this
           ; number now.  That let's us move the interpose calls above this
           ; point.
           [flow flow-lines] (zat options flow)]
       (dbg options "fzprint-hang-remaining: flow-lines:" flow-lines)
       (when dbg?
         (if (zero? hang-count)
           (println "hang-count = 0:" (str (zmap-right zstring zloc)))))
       (log-lines options "fzprint-hang-remaining: flow" findent flow)
       (if flow-lines
         (if (good-enough? caller
                           options
                           fn-style
                           hang-count
                           (- hindent findent)
                           hanging-lines
                           flow-lines)
           (concat-no-nil [[" " :none :whitespace]] hanging)
           (concat-no-nil [[(str "\n" (blanks findent)) :none :indent]] flow))
         (when hanging-lines
           (concat-no-nil [[" " :none :whitespace]] hanging)))))
    ([caller options hindent findent zloc fn-style]
     (fzprint-hang-remaining caller options hindent findent zloc fn-style nil)))

(defn fzprint-hang-remaining ;-original
  "zloc is already down inside a collection, it is not the collection
  itself. Operate on what is to the right of zloc.  We already know
  that the given zloc won't fit on the current line. [Besides, we
  ensure that if there are two things remaining anyway. ???] So
  now, try hanging and see if that is better than flow.  Unless
  :hang? is nil, in which case we will just flow.  hindent is
  hang-indent, and findent is flow-indent. This should never be
  called with :one-line because this is only called from fzprint-list*
  after the one-line processing is done. If the hindent equals the
  flow indent, then just do flow.  Do only zloc-count non-whitespace
  elements of zloc."
  ([caller
    {:keys [dbg? width],
     {:keys [hang? constant-pair? constant-pair-min hang-avoid hang-expand
             hang-diff nl-separator?]}
       caller,
     :as options} hindent findent zloc fn-style zloc-count]
   (when (:dbg-hang options)
     (println (dots (:pdepth options)) "hr" (zstring zloc)))
   (dbg options
        "fzprint-hang-remaining:" (zstring zloc)
        "hindent:" hindent
        "findent:" findent
        "caller:" caller
        "nl-separator?:" nl-separator?)
   ; (in-hang options) slows things down here, for some reason
   (let [seq-right (zmap-right identity zloc)
         seq-right (if zloc-count (take zloc-count seq-right) seq-right)
         [pair-seq non-paired-item-count]
           (constant-pair caller options seq-right)
         _ (dbg options
                "fzprint-hang-remaining count pair-seq:"
                (count pair-seq))
         flow
           (#?@(:clj [zfuture options]
                :cljs [do])
            (let [flow-result
                    (if-not pair-seq
                      ; We don't have any constant pairs
                      (apply concat-no-nil
                        (interpose [[(str "\n" (blanks findent)) :none :indent]]
                          (fzprint-seq options findent seq-right)))
                      (if (not (zero? non-paired-item-count))
                        ; We have constant pairs, ; but they follow
                        ; some stuff that isn't paired.
                        (concat-no-nil
                          ; The elements that are not pairs
                          (apply concat-no-nil
                            (interpose [[(str "\n" (blanks findent)) :none
                                         :indent]]
                              (zpmap options
                                     (partial fzprint*
                                              (not-rightmost options)
                                              findent)
                                     (take non-paired-item-count seq-right))))
                          ; Got to separate them since we are doing them in
                          ; two
                          ; pieces
                          [[(str "\n" (blanks findent)) :none :indent]]
                          ; The elements that are constant pairs
                          (interpose-nl-hf (:pair options)
                                           findent
                                           (fzprint-map-two-up :pair
                                                               ;caller
                                                               options
                                                               findent
                                                               nil
                                                               pair-seq)))
                        ; This code path is where we have all constant pairs.
                        (interpose-nl-hf (:pair options)
                                         findent
                                         (fzprint-map-two-up :pair
                                                             ;caller
                                                             options
                                                             findent
                                                             nil
                                                             pair-seq))))]
              [flow-result (style-lines options findent flow-result)]))
         #_(options (let [[_ _ _ b-what] flow-lines]
                      (if b-what (assoc options :dbg? true) options)))
         #_(dbg options
                "fzprint-hang-remaining: *=*=*=*=*=*" (zstring zloc)
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
         ; Yes, and this caused a proble when I put in the
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
         hang? (and hang?
                    ; This is a key for "don't hang no matter what", it isn't
                    ; about making it prettier. People call this routine with
                    ; these values equal to ensure that it always flows.
                    (not= hindent findent)
                    ; This is not the original, below.
                    (or (not hang-avoid)
                        (< (count seq-right) (* (- width hindent) hang-avoid)))
                    ;flow-lines
                    ;;TODO make this uneval!!!
                    #_(or (<= (- hindent findent) hang-diff)
                          (<= (/ (dec (first flow-lines)) (count seq-right))
                              hang-expand)))
         hanging
           (#?@(:clj [zfuture options]
                :cljs [do])
            (let [hang-result
                    (when hang?
                      (if-not pair-seq
                        ; There are no paired elements
                        (apply concat-no-nil
                          (interpose [[(str "\n" (blanks hindent)) :none
                                       :indent]]
                            (fzprint-seq (in-hang options) hindent seq-right)))
                        (if (not (zero? non-paired-item-count))
                          (concat-no-nil
                            ; The elements that are not paired
                            (dbg-form
                              options
                              "fzprint-hang-remaining: mapv:"
                              (apply concat-no-nil
                                (interpose [[(str "\n" (blanks hindent)) :none
                                             :indent]]
                                  (zpmap
                                    options
                                    (partial fzprint*
                                             (not-rightmost (in-hang options))
                                             hindent)
                                    (take non-paired-item-count seq-right)))))
                            ; Got to separate them because they were done in two
                            ; pieces
                            [[(str "\n" (blanks hindent)) :none :indent]]
                            ; The elements that are paired
                            (dbg-form options
                                      "fzprint-hang-remaining: fzprint-hang:"
                                      (interpose-nl-hf
                                        (:pair options)
                                        hindent
                                        (fzprint-map-two-up :pair
                                                            ;caller
                                                            (in-hang options)
                                                            hindent
                                                            nil
                                                            pair-seq))))
                          ; All elements are paired
                          (interpose-nl-hf (:pair options)
                                           hindent
                                           (fzprint-map-two-up :pair
                                                               ;caller
                                                               (in-hang options)
                                                               hindent
                                                               nil
                                                               pair-seq)))))]
              [hang-result (style-lines options hindent hang-result)]))
         ; We used to calculate hang-count by doing the hang an then counting
         ; the output.  But ultimately this is simple a series of map calls
         ; to the elements of seq-right, so we go right to the source for this
         ; number now.  That let's us move the interpose calls above this
         ; point.
         [flow flow-lines] (zat options flow)
         [hanging hanging-lines] (zat options hanging)
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
         (println "hang-count = 0:" (str (zmap-right zstring zloc)))))
     (log-lines options "fzprint-hang-remaining: flow" findent flow)
     (when flow-lines
       (if (good-enough? caller
                         options
                         fn-style
                         hang-count
                         (- hindent findent)
                         hanging-lines
                         flow-lines)
         (concat-no-nil [[" " :none :whitespace]] hanging)
         (concat-no-nil [[(str "\n" (blanks findent)) :none :indent]] flow)))))
  ([caller options hindent findent zloc fn-style]
   (fzprint-hang-remaining caller options hindent findent zloc fn-style nil)))

;;
;; # Utilities to modify list printing in various ways
;;


;;
;; Which fn-styles use :list {:indent n} instead of
;; :list {:indent-arg n}
;;

(def body-set
  #{:binding :arg1-> :arg2 :arg2-fn :arg2-pair :pair-fn :fn :arg1-body
    :arg1-pair-body :none-body :noarg1-body :flow-body})

(def body-map
  {:arg1-body :arg1,
   :arg1-pair-body :arg1-pair,
   :none-body :none,
   :flow-body :flow,
   :noarg1-body :noarg1,
   :force-nl-body :force-nl})

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

(def fn-style->caller
  {:arg1-pair-body :pair,
   :arg1-pair :pair,
   :arg2-pair :pair,
   :extend :extend,
   :binding :binding,
   :arg1-extend :extend,
   :arg2-extend :extend,
   :pair-fn :pair})

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

(defn fzprint-list*
  "Print a list, which might be a list or an anon fn.  
  Lots of work to make a list look good, as that is typically code. 
  Presently all of the callers of this are :list."
  [caller l-str r-str
   {:keys [fn-map user-fn-map one-line? fn-style no-arg1? fn-force-nl],
    {:keys [indent-arg indent]} caller,
    :as options} ind zloc]
  (let [len (zcount zloc)
        l-str-len (count l-str)
        arg-1-coll? (not (or (zkeyword? (zfirst zloc))
                             (zsymbol? (zfirst zloc))))
        fn-str (if-not arg-1-coll? (zstring (zfirst zloc)))
        fn-style (or fn-style (fn-map fn-str) (user-fn-map fn-str))
        ; if we don't have a function style, let's see if we can get
        ; one by removing the namespacing
        fn-style (if (and (not fn-style) fn-str)
                   (fn-map (last (clojure.string/split fn-str #"/")))
                   fn-style)
        ; set indent based on fn-style
        indent (if (body-set fn-style) indent (or indent-arg indent))
        one-line-ok? (allow-one-line? options len fn-style)
        ; remove -body from fn-style if it was there
        fn-style (or (body-map fn-style) fn-style)
        ; All styles except :hang need three elements minimum.
        ; We could put this in the fn-map, but until there is more
        ; than one exception, seems like too much mechanism.
        fn-style (if (= fn-style :hang) fn-style (if (< len 3) nil fn-style))
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
        default-indent (if (zlist? (zfirst zloc)) indent l-str-len)
        arg-1-indent (if-not (or arg-1-coll? (zcomment? (zfirst zloc)))
                       (+ ind (inc l-str-len) (count fn-str)))
        ; Tell people inside that we are in code.
        ; We don't catch places where the first thing in a list is
        ; a collection or a seq which yields a function.
        options (if (not arg-1-coll?) (assoc options :in-code? fn-str) options)
        options (assoc options :pdepth (inc (long (or (:pdepth options) 0))))
        _ (when (:dbg-hang options)
            (println (dots (:pdepth options)) "fzs" fn-str))
        new-ind (+ indent ind)
        one-line-ind (+ l-str-len ind)
        options (if fn-style (dissoc options :fn-style) options)
        loptions (not-rightmost options)
        roptions options
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        _ (dbg options
               "fzprint-list*:" (zstring zloc)
               "fn-str" fn-str
               "fn-style:" fn-style
               "ind:" ind
               "indent:" indent
               "default-indent:" default-indent
               "one-line-ok?" one-line-ok?
               "arg-1-coll?" arg-1-coll?
               "arg-1-indent:" arg-1-indent
               "l-str:" (str "'" l-str "'")
               "indent-adj:" indent-adj
               "len:" len
               "one-line?:" one-line?
               "rightcnt:" (:rightcnt options))
        one-line (if (zero? len)
                   :empty
                   (when one-line-ok?
                     (fzprint-one-line options one-line-ind zloc)))]
    (cond
      one-line (if (= one-line :empty)
                 (concat-no-nil l-str-vec r-str-vec)
                 (concat-no-nil l-str-vec one-line r-str-vec))
      ; If we are in :one-line mode, and it didn't fit on one line,
      ; we are done!  We don't see this debugging, below.  Suppose
      ; we never get here?
      one-line?
        (dbg options "fzprint-list*:" fn-str " one-line did not work!!!")
      (dbg options "fzprint-list*: fn-style:" fn-style) nil
      (= len 0) (concat-no-nil l-str-vec r-str-vec)
      (= len 1) (concat-no-nil l-str-vec
                               (fzprint* roptions one-line-ind (zfirst zloc))
                               r-str-vec)
      ; needs (> len 2) but we already checked for that above in fn-style
      (and (= fn-style :binding) (zvector? (zsecond zloc)))
        (let [[hang-or-flow binding-style-vec] (fzprint-hang-unless-fail
                                                 loptions
                                                 arg-1-indent
                                                 (+ indent ind)
                                                 fzprint-binding-vec
                                                 (zsecond zloc))
              binding-style-vec (if (= hang-or-flow :hang)
                                  (concat-no-nil [[" " :none :whitespace]]
                                                 binding-style-vec)
                                  binding-style-vec)]
          (concat-no-nil l-str-vec
                         ; TODO: get rid of inc ind
                         (fzprint* loptions (inc ind) (zfirst zloc))
                         binding-style-vec
                         [[(str "\n" (blanks (+ indent ind))) :none :indent]]
                         ; here we use options, because fzprint-flow-seq
                         ; will sort it out
                         (fzprint-flow-seq options
                                           (+ indent ind)
                                           (nthnext (zmap identity zloc) 2)
                                           :force-nl)
                         r-str-vec))
      (= fn-style :pair-fn) (concat-no-nil
                              l-str-vec
                              (fzprint* loptions (inc ind) (zfirst zloc))
                              ;    [[(str " ") :none :whitespace]]
                              (fzprint-hang
                                options
                                :pair-fn
                                arg-1-indent
                                (+ indent ind)
                                fzprint-pairs
                                (count (zmap-right identity (znthnext zloc 0)))
                                (znthnext zloc 0))
                              r-str-vec)
      (= fn-style :extend)
        (concat-no-nil l-str-vec
                       (fzprint* loptions (inc ind) (zfirst zloc))
                       [[(str "\n" (blanks (+ indent ind))) :none :indent]]
                       ; I think fzprint-pairs will sort out which
                       ; is and isn't the rightmost because of two-up
                       (fzprint-extend options (+ indent ind) (znthnext zloc 0))
                       r-str-vec)
      ; needs (> len 2) but we already checked for that above in fn-style
      (or (and (= fn-style :fn) (not (zlist? (zsecond zloc))))
          (= fn-style :arg2)
          (= fn-style :arg2-fn)
          (= fn-style :arg2-pair)
          (= fn-style :arg2-extend))
        (let [second-element (fzprint-hang-one caller
                                               (if (= len 2) options loptions)
                                               arg-1-indent
                                               (+ indent ind)
                                               (zsecond zloc))
              [line-count max-width]
                (style-lines loptions arg-1-indent second-element)
              third (zthird zloc)
              first-three
                (when second-element
                  (concat-no-nil
                    (fzprint* loptions
                              ;(inc ind)
                              (+ indent ind)
                              (zfirst zloc))
                    second-element
                    (if (or (= fn-style :arg2)
                            (= fn-style :arg2-pair)
                            (= fn-style :arg2-fn)
                            (and (zvector? third) (= line-count 1)))
                      (fzprint-hang-one caller
                                        (if (= len 3) options loptions)
                                        ;(inc max-width)
                                        max-width
                                        (+ indent ind)
                                        third)
                      (concat-no-nil [[(str "\n" (blanks (+ indent ind))) :none
                                       :indent]]
                                     (fzprint* (if (= len 3) options loptions)
                                               (+ indent ind)
                                               third)))))]
          (when first-three
            (if (= len 3)
              (concat-no-nil l-str-vec first-three r-str-vec)
              (concat-no-nil
                l-str-vec
                first-three
                (cond
                  (= fn-style :arg2-pair)
                    (concat-no-nil
                      [[(str "\n" (blanks (+ indent ind))) :none :indent]]
                      (fzprint-pairs options (+ indent ind) (znthnext zloc 2)))
                  (= fn-style :arg2-extend)
                    (concat-no-nil
                      [[(str "\n" (blanks (+ indent ind))) :none :indent]]
                      (fzprint-extend options (+ indent ind) (znthnext zloc 2)))
                  :else (fzprint-hang-remaining caller
                                                ;options
                                                (if (= fn-style :arg2-fn)
                                                  (assoc options :fn-style :fn)
                                                  options)
                                                (+ indent ind)
                                                ; force flow
                                                (+ indent ind)
                                                (znthnext zloc 2)
                                                fn-style))
                r-str-vec))))
      (and (= fn-style :arg1-mixin) (> len 3))
        (let [arg-vec-index (or (zfind #(or (zvector? %)
                                            (when (zlist? %)
                                              (zvector? (zfirst %))))
                                       zloc)
                                0)
              doc-string? (string? (zsexpr (zthird zloc)))
              mixin-start (if doc-string? 4 3)
              mixin-length (- arg-vec-index mixin-start)
              mixins? (pos? mixin-length)
              doc-string (when doc-string?
                           (fzprint-hang-one caller
                                             loptions
                                             (+ indent ind)
                                             ; force flow
                                             (+ indent ind)
                                             (zthird zloc)))
              ; Have to deal with no arg-vec-index!!
              mixins
                (when mixins?
                  (let [mixin-sentinal (fzprint-hang-one caller
                                                         loptions
                                                         (+ indent ind)
                                                         ; force flow
                                                         (+ indent ind)
                                                         (if doc-string?
                                                           (zfourth zloc)
                                                           (zthird zloc)))
                        [line-count max-width]
                          (style-lines loptions (+ indent ind) mixin-sentinal)]
                    (concat-no-nil mixin-sentinal
                                   (fzprint-hang-remaining
                                     caller
                                     loptions
                                     ; Apparently hang-remaining gives you a
                                     ; space after the current thing, so we
                                     ; need to account for it now, since
                                     ; max-width is the end of the current
                                     ; thing
                                     (inc max-width)
                                     (dec (+ indent indent ind))
                                     (znthnext zloc (if doc-string? 3 2))
                                     fn-style
                                     mixin-length))))]
          (concat-no-nil
            l-str-vec
            (fzprint* loptions (inc ind) (zfirst zloc))
            (fzprint-hang-one caller
                              (if (= len 2) options loptions)
                              arg-1-indent
                              (+ indent ind)
                              (zsecond zloc))
            (cond (and doc-string? mixins?) (concat-no-nil doc-string mixins)
                  doc-string? doc-string
                  mixins? mixins
                  ; This is a hack, would be nice to have a better way to
                  ; handle these situations.  Likely the only one so far.
                  :else [["" :none :whitespace]])
            (fzprint-hang-remaining
              caller
              (noarg1 options fn-style)
              (+ indent ind)
              ; force flow
              (+ indent ind)
              (znthnext zloc
                        (if mixins? (dec arg-vec-index) (if doc-string? 2 1)))
              fn-style)
            r-str-vec))
      (or (= fn-style :arg1-pair)
          (= fn-style :arg1)
          (= fn-style :arg1-force-nl)
          (= fn-style :arg1->))
        (concat-no-nil
          l-str-vec
          (fzprint* loptions (inc ind) (zfirst zloc))
          (fzprint-hang-one caller
                            (if (= len 2) options loptions)
                            arg-1-indent
                            (+ indent ind)
                            (zsecond zloc))
          ; then either pair or remaining-seq
          ; we don't do a full hanging here.
          (when (> len 2)
            (if (= fn-style :arg1-pair)
              (concat-no-nil
                [[(str "\n" (blanks (+ indent ind))) :none :indent]]
                (fzprint-pairs options (+ indent ind) (znthnext zloc 1)))
              (fzprint-hang-remaining caller
                                      (noarg1 options fn-style)
                                      (+ indent ind)
                                      ; force flow
                                      (+ indent ind)
                                      (znthnext zloc 1)
                                      fn-style)))
          r-str-vec)
      ; we know that (> len 2) if fn-style not= nil
      (= fn-style :arg1-extend)
        (cond
          (zvector? (zsecond zloc))
            (concat-no-nil
              l-str-vec
              (fzprint* loptions (inc ind) (zfirst zloc))
              [[(str "\n" (blanks (+ indent ind))) :none :indent]]
              (fzprint* loptions (inc ind) (zsecond zloc))
              [[(str "\n" (blanks (+ indent ind))) :none :indent]]
              ; This needs to be (znthnext zloc 1) and not 2 because
              ; fzprint-extend does (zmap-right identity zloc), skipping
              ; the first one!
              (fzprint-extend options (+ indent ind) (znthnext zloc 1))
              r-str-vec)
          :else (concat-no-nil
                  l-str-vec
                  (fzprint* loptions (inc ind) (zfirst zloc))
                  (fzprint-hang-one caller
                                    (if (= len 2) options loptions)
                                    arg-1-indent
                                    (+ indent ind)
                                    (zsecond zloc))
                  [[(str "\n" (blanks (+ indent ind))) :none :indent]]
                  (fzprint-extend options (+ indent ind) (znthnext zloc 1))
                  r-str-vec))
      ;
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
      :else
        (concat-no-nil
          l-str-vec
          (fzprint* loptions (+ l-str-len ind) (zfirst zloc))
          (if (and arg-1-indent (not= fn-style :flow))
            (fzprint-hang-remaining caller
                                    (noarg1 options fn-style)
                                    arg-1-indent
                                    (+ indent ind indent-adj)
                                    (znthnext zloc 0)
                                    fn-style)
            ; This might be a collection as the first thing, or it
            ; might be a :flow type.  Do different indents for these.
            (let [local-indent (if (= fn-style :flow)
                                 (+ indent ind)
                                 (+ default-indent ind indent-adj))]
              (concat-no-nil [[(str "\n" (blanks local-indent)) :none :indent]]
                             (fzprint-flow-seq (noarg1 options fn-style)
                                               local-indent
                                               (nthnext (zmap identity zloc) 1)
                                               :force-nl))))
          r-str-vec))))

(defn fzprint-list
  "Pretty print and focus style a :list element."
  [options ind zloc]
  (fzprint-list* :list "(" ")" (rightmost options) ind zloc))

(defn fzprint-anon-fn
  "Pretty print and focus style a fn element."
  [options ind zloc]
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
  wrap the elements to the right margin."
  [caller
   {:keys [width rightcnt max-length],
    {:keys [wrap-after-multi?]} caller,
    :as options} ind coll-print]
  #_(prn "wz:" coll-print)
  (let [last-index (dec (count coll-print))
        rightcnt (fix-rightcnt rightcnt)]
    (loop [cur-seq coll-print
           cur-ind ind
           index 0
           previous-newline? false
           ; transient here slows things down, interestingly enough
           out []]
      (if-not cur-seq
        out
        (let [next-seq (first cur-seq)]
          (when next-seq
            (let [multi? (> (count (first cur-seq)) 1)
                  this-seq (first cur-seq)
                  _ (log-lines options "wrap-zmap:" ind this-seq)
                  _ (dbg options "wrap-zmap: ind:" ind "this-seq:" this-seq)
                  [linecnt max-width lines] (style-lines options ind this-seq)
                  last-width (last lines)
                  len (- last-width ind)
                  len (max 0 len)
                  newline? (= (nth (first this-seq) 2) :newline)
                  width (if (= index last-index) (- width rightcnt) width)
                  ; need to check size, and if one line and fits, should fit
                  fit? (and (not newline?)
                            (or (zero? index)
                                (and (if multi? (= linecnt 1) true)
                                     (<= (+ cur-ind len) width))))
                  new-ind (cond
                            (or (= (nth (first this-seq) 2) :comment)
                                (= (nth (first this-seq) 2) :comment-inline))
                              (inc width)
                            (and multi? (> linecnt 1) (not wrap-after-multi?))
                              width
                            fit? (+ cur-ind len 1)
                            newline? ind
                            :else (+ ind len 1))]
              #_(prn "------ this-seq:" this-seq
                     "lines:" lines
                     "linecnt:" linecnt
                     "multi?" multi?
                     "newline?:" newline?
                     "previous-newline?:" previous-newline?
                     "linecnt:" linecnt
                     "max-width:" max-width
                     "last-width:" last-width
                     "len:" len
                     "cur-ind:" cur-ind
                     "new-ind:" new-ind
                     "width:" width
                     "fit?" fit?)
              ; need to figure out what to do with a comment,
              ; want to force next line to not fit whether or not
              ; this line fit.  Comments are already multi-line, and
              ; it is really not clear what multi? does in this routine
              (recur
                (next cur-seq)
                new-ind
                (inc index)
                newline?
                ; TODO: concat-no-nil fails here, why?
                (concat
                  out
                  (if fit?
                    (if (not (zero? index))
                      (concat-no-nil [[" " :none :whitespace]] this-seq)
                      this-seq)
                    (if newline?
                      [[(str "\n" (blanks (dec new-ind))) :none :indent]]
                      (if previous-newline?
                        (concat-no-nil [[" " :none :indent]] this-seq)
                        (concat-no-nil [[(str "\n" (blanks ind)) :none :indent]]
                                       this-seq)))))))))))))
(defn remove-nl
  "Remove any [_ _ :newline] from the seq."
  [coll]
  (remove #(= (nth (first %) 2) :newline) coll))

(defn internal-validate
  "Validate an options map that was returned from some internal configuration
  expression or configuration.  Either returns the options map or throws
  an error."
  [options error-str]
  (let [errors (validate-options options)
        errors (when errors
                 (str "Options resulting from " error-str
                      " had these errors: " errors))]
    (if (not (empty? errors))
      (throw (#?(:clj Exception.
                 :cljs js/Error.)
              errors))
      options)))

(defn fzprint-vec*
  "Print basic stuff like a vector or a set.  Several options for how to
  print them."
  [caller l-str r-str
   {:keys [rightcnt in-code?],
    {:keys [wrap-coll? wrap? binding? option-fn-first respect-nl? sort?
            sort-in-code?]}
      caller,
    :as options} ind zloc]
  (if (and binding? (= (:depth options) 1))
    (fzprint-binding-vec options ind zloc)
    (let [l-str-vec [[l-str (zcolor-map options l-str) :left]]
          r-str-vec (rstr-vec options ind zloc r-str)
          new-options (when option-fn-first
                        (let [first-sexpr (zsexpr (zfirst-no-comment zloc))]
                          (internal-validate
                            (option-fn-first options first-sexpr)
                            (str ":vector :option-fn-first called with "
                                 first-sexpr))))
          #_(prn "new-options:" new-options)
          {{:keys [wrap-coll? wrap? binding? option-fn-first respect-nl? sort?
                   sort-in-code?]}
             caller,
           :as options}
            (merge-deep options new-options)
          ; If sort? is true, then respect-nl? makes no sense.  At present,
          ; sort? and respect-nl? are not both supported for the same structure,
          ; so this doesn't really matter, but if in the future they were, this
          ; would help.
          respect-nl? (and respect-nl? (not sort?))
          new-ind (+ (count l-str) ind)
          _ (dbg-pr options "fzprint-vec*:" (zstring zloc) "new-ind:" new-ind)
          zloc-seq
            (if respect-nl? (zmap-w-nl identity zloc) (zmap identity zloc))
          zloc-seq (if (and sort? (if in-code? sort-in-code? true))
                     (order-out caller options identity zloc-seq)
                     zloc-seq)
          coll-print (if (zero? (zcount zloc))
                       [[["" :none :whitespace]]]
                       (fzprint-seq options new-ind zloc-seq))
          _ (dbg-pr options "fzprint-vec*: coll-print:" coll-print)
          ; If we got any nils from fzprint-seq and we were in :one-line mode
          ; then give up -- it didn't fit on one line.
          coll-print (if-not (contains-nil? coll-print) coll-print)
          one-line (when coll-print
                     ; should not be necessary with contains-nil? above
                     (apply concat-no-nil
                       (interpose [[" " :none :whitespace]]
                         ; This causes single line things to also respect-nl
                         ; when it is enabled.  Could be separately controlled
                         ; instead of with :respect-nl? if desired.
                         (if respect-nl? coll-print (remove-nl coll-print)))))
          _ (log-lines options "fzprint-vec*:" new-ind one-line)
          one-line-lines (style-lines options new-ind one-line)]
      (when one-line-lines
        (if (fzfit-one-line options one-line-lines)
          (concat-no-nil l-str-vec one-line r-str-vec)
          (if (or (and (not wrap-coll?) (any-zcoll? options new-ind zloc))
                  (not wrap?))
            (concat-no-nil l-str-vec
                           (apply concat-no-nil
                             (interpose [[(str "\n" (blanks new-ind)) :none
                                          :indent]]
                               (remove-nl coll-print)))
                           r-str-vec)
            ; Since there are either no collections in this vector or set or
            ; whatever, or if there are, it is ok to wrap them, print it
            ; wrapped on the same line as much as possible:
            ;           [a b c d e f
            ;            g h i j]
            (concat-no-nil
              l-str-vec
              (do (dbg options "fzprint-vec*: wrap coll-print:" coll-print)
                  (wrap-zmap caller options new-ind coll-print))
              r-str-vec)))))))

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
  "Do the same as interpose, but different seps depending on pred?."
  [sep-true sep-nil pred? coll]
  (loop [coll coll
         out (transient [])
         interpose? nil]
    (if (empty? coll)
      (persistent! out)
      (recur (next coll)
             (if interpose?
               (conj-it! out sep-true (first coll))
               (if (zero? (count out))
                 (conj! out (first coll))
                 (conj-it! out sep-nil (first coll))))
             (pred? (first coll))))))

; transient helped a lot here
(defn interpose-either-nl-hf
  "Do the same as interpose, but different seps depending on pred-fn
  return and nl-separator?."
  [sep-true sep-true-nl sep-nil sep-nil-nl
   {:keys [nl-separator? nl-separator-flow?], :as suboptions} ;nl-separator?
   pred-fn coll]
  (loop [coll coll
         out (transient [])
         interpose? nil
         add-nl? nil]
    (if (empty? coll)
      (apply concat-no-nil (persistent! out))
      (let [[hangflow style-vec] (first coll)]
        (recur (next coll)
               (if interpose?
                 (conj-it! out (if add-nl? sep-true-nl sep-true) style-vec)
                 (if (zero? (count out))
                   ;(empty? out)
                   (conj! out style-vec)
                   (conj-it! out (if add-nl? sep-nil-nl sep-nil) style-vec)))
               (when pred-fn (pred-fn style-vec))
               ; should we put an extra new-line before the next element?
               ; Two styles here:
               ;  o  always put one if the previous pair contained a new-line
               ;     which could be (but is not) the default
               ;     To do this you would do:
               ;       (and nl-separator? (not (single-line? style-vec)))
               ;  o  put one only if the previous right hand part of the
               ;     pair did a flow (which is the current default)
               ;     To do this, you look for whether or not the return
               ;     from fzprint-map-two-up said it was a flow
               (and nl-separator? (= hangflow :flow)))))))

(defn interpose-nl-hf
  "Put a single or double line between pairs returned from fzprint-map-two-up.
  The first argument is the map resulting from (:map options) or (:pair options)
  or whatever.  It should have :nl-separator? and :nl-separator-flow? in it."
  [suboptions ind coll]
  (interpose-either-nl-hf nil
                          nil
                          [[(str "\n" (blanks ind)) :none :indent]]
                          [[(str "\n") :none :indent]
                           [(str "\n" (blanks ind)) :none :indent]]
                          suboptions
                          #_(:nl-separator? suboptions)
                          nil
                          coll))

(defn fzprint-map*
  [caller l-str r-str
   {:keys [one-line? ztype map-depth in-code?],
    {:keys [comma? key-ignore key-ignore-silent nl-separator? force-nl? lift-ns?
            lift-ns-in-code?]}
      caller,
    :as options} ind zloc]
  (let [options (assoc options :map-depth (inc map-depth))
        zloc (if (and (= ztype :sexpr) (or key-ignore key-ignore-silent))
               (map-ignore caller options zloc)
               zloc)
        [no-sort? pair-seq] (partition-all-2-nc options (zseqnws zloc))
        [ns lift-pair-seq] (when (and lift-ns?
                                      (if in-code? lift-ns-in-code? true))
                             (zlift-ns pair-seq))
        l-str (if ns (str "#:" ns l-str) l-str)
        pair-seq (or lift-pair-seq pair-seq)
        pair-seq
          (if no-sort? pair-seq (order-out caller options first pair-seq))
        indent (count l-str)
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)]
    (if (empty? pair-seq)
      (concat-no-nil l-str-vec r-str-vec)
      (let [_ (dbg options
                   "fzprint-map*:" (zstring zloc)
                   "ind:" ind
                   "comma?" comma?
                   "rightcnt:" (:rightcnt options))
            ; A possible one line representation of this map, but this is
            ; optimistic and needs to be validated.
            pair-print-one-line
              (fzprint-map-two-up
                caller
                (if one-line? options (assoc options :one-line? true))
                (+ indent ind)
                comma?
                pair-seq)
            pair-print-one-line (remove-hangflow pair-print-one-line)
            ; Does it fit on line line?
            pair-print-one-line
              (when (fzfit-one-line
                      options
                      (style-lines options (+ indent ind) pair-print-one-line))
                pair-print-one-line)
            one-line (when pair-print-one-line
                       (apply concat-no-nil
                         (interpose-either [[", " :none :whitespace]]
                                           [[" " :none :whitespace]]
                                           (constantly comma?)
                                           pair-print-one-line)))
            one-line-lines (style-lines options (+ indent ind) one-line)
            one-line (when (fzfit-one-line options one-line-lines) one-line)]
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
              (concat-no-nil l-str-vec
                             ;(apply concat-no-nil
                             (interpose-either-nl-hf
                               [["," ;(str "," (blanks (inc ind)))
                                 :none :whitespace]
                                [(str "\n" (blanks (inc ind))) :none :indent]]
                               [["," ;(str "," (blanks (inc ind)))
                                 :none :whitespace]
                                [(str "\n" (blanks (inc ind))) :none :indent]
                                [(str "\n" (blanks (inc ind))) :none :indent]]
                               [[(str "\n" (blanks (inc ind))) :none :indent]]
                               [[(str "\n" (blanks (inc ind))) :none :indent]
                                [(str "\n" (blanks (inc ind))) :none :indent]]
                               (:map options)
                               ;nl-separator?
                               #(and comma?
                                     (not= (nth (first %) 2) :comment)
                                     (not= (nth (first %) 2) :comment-inline))
                               pair-print)
                             ; )
                             r-str-vec))))))))

(defn fzprint-map
  "Format a real map. ONLY WORKES ON STRUCTURES AT PRESENT"
  [options ind zloc]
  (let [[ns lifted-map] nil]
    ;(zlift-ns zloc)]
    (if ns
      (fzprint-map* :map
                    (str "#:" ns "{")
                    "}"
                    (rightmost options)
                    ind
                    lifted-map)
      (fzprint-map* :map "{" "}" (rightmost options) ind zloc))))

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
  #?(:clj (Integer/toHexString (System/identityHashCode obj))
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
          r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
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
            r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
            type-str (case zloc-type
                       :future "Future@"
                       :promise "Promise@"
                       :delay "Delay@"
                       :agent "Agent@")
            arg-1 (str type-str (hash-identity-str zloc))
            #?@(:clj [arg-1
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
          r-str-vec (rstr-vec options (+ indent ind) zloc r-str :fn)
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
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        arg-1 "Namespace"
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
                                     (ns-name zloc))
                   r-str-vec)))

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
            r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
            arg-1 (pr-str #?(:clj (class zloc)
                             :cljs (type zloc)))
            arg-1 (let [tokens (clojure.string/split arg-1 #"\.")]
                    (apply str
                      (conj (into [] (interpose "." (butlast tokens)))
                            "/"
                            (last tokens))))
            arg-1-indent (+ ind indent 1 (count arg-1))]
        (dbg-pr options
                "fzprint-record: arg-1:" arg-1
                "zstring zloc:" (zstring zloc))
        (concat-no-nil l-str-vec
                       [[arg-1 (zcolor-map options :none) :element]]
                       (fzprint-hang-one :record
                                         options
                                         ;(rightmost options)
                                         arg-1-indent
                                         (+ indent ind)
                                         ; this only works because
                                         ; we never actually get here
                                         ; with a zipper, just an sexpr
                                         (into {} zloc))
                       r-str-vec)))))

(defn fzprint-uneval
  "Trim the #_ off the front of the uneval, and try to print it."
  [options ind zloc]
  (let [l-str "#_"
        r-str ""
        indent (count l-str)
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        uloc (zparseuneval zloc)
        #_(def zs (zstring zloc))
        #_(def un uloc)]
    (dbg-pr options
            "fzprint-uneval: zloc:" (zstring zloc)
            "uloc:" (zstring uloc))
    (concat-no-nil l-str-vec
                   (fzprint* (assoc options
                               :color-map (:color-map (:uneval options)))
                             (+ indent ind)
                             uloc)
                   r-str-vec)))

(defn fzprint-meta
  "Print the two items in a meta node.  Different because it doesn't print
  a single collection, so it doesn't do any indent or rightmost.  It also
  uses a different approach to calling fzprint-flow-seq with the
  results zmap, so that it prints all of the seq, not just the rightmost."
  [options ind zloc]
  (let [l-str "^"
        r-str ""
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options ind zloc r-str)]
    (dbg-pr options "fzprint-meta: zloc:" (zstring zloc))
    (concat-no-nil
      l-str-vec
      (fzprint-flow-seq
        ; No rightmost, because this isn't a collection.
        ; This is essentially two separate things.
        options
        ; no indent for second line, as the leading ^ is
        ; not a normal collection beginning
        ; TODO: change this to (+ (count l-str) ind)
        (apply vector (+ (count l-str) ind) (repeat (dec (zcount zloc)) ind))
        ;[(inc ind) ind]
        (zmap identity zloc))
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
        namespaced? (= (subs zstr 0 1) ":")
        at? (or (= (ztag (zsecond zloc)) :deref) alt-at?)
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
        #_(when (:dbg-bug? options) (+ "a" "b"))
        indent (count l-str)
        ; we may want to color this based on something other than
        ; its actual character string
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str)
        floc
          (if (and at? (not alt-at?)) (zfirst (zsecond zloc)) (zsecond zloc))]
    (dbg-pr options
            "fzprint-reader-macro: zloc:" (zstring zloc)
            "floc:" (zstring floc)
            "l-str:" l-str)
    (concat-no-nil
      l-str-vec
      ; Because there is a token here in the zipper, we need something to
      ; make the focus positioning come out right.
      [["" :none :element]]
      (if reader-cond?
        ; yes rightmost, this is a collection
        (fzprint-map* :reader-cond
                      "("
                      ")"
                      (rightmost options)
                      (+ indent ind)
                      floc)
        ; not reader-cond?
        (fzprint-flow-seq options
                          (+ indent ind)
                          (let [zloc-seq (zmap identity zloc)]
                            (if namespaced? (next zloc-seq) zloc-seq))))
      r-str-vec)))

(defn fzprint-prefix*
  "Print the single item after a variety of prefix characters."
  [options ind zloc l-str]
  (let [r-str ""
        indent (count l-str)
        ; Since this is a single item, no point in figure an indent
        ; based on the l-str length."
        l-str-vec [[l-str (zcolor-map options l-str) :left]]
        ; Either these both have to be :element, or :left and :right
        r-str-vec (rstr-vec options (+ indent ind) zloc r-str :right)
        floc (zfirst zloc)
        #_(def zqs (zstring zloc))
        #_(def qun floc)]
    (dbg-pr options
            "fzprint-prefix*: zloc:" (zstring zloc)
            "floc:" (zstring floc))
    (concat-no-nil l-str-vec
                   ; no rightmost, as we don't know if this is a collection
                   (fzprint* options (+ indent ind) floc)
                   r-str-vec)))


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
        (= prefix-tag :syntax-quote) (assoc-in options
                                       [:color-map :paren]
                                       (:syntax-quote-paren (:color-map
                                                              options)))
        :else options))

;; Fix fzprint* to look at cursor to see if there is one, and
;; fzprint to set cursor with binding.  If this works, might pass
;; it around.  Maybe pass ctx to everyone and they can look at it
;; or something.  But for testing, let's just do this.

;;
;; # The center of the zprint universe
;;
;; Looked into alternative ways to dispatch this, but at the end of
;; the day, this looked like the best.
;;

(defn fzprint*
  "The pretty print part of fzprint."
  [{:keys [width rightcnt fn-map hex? shift-seq dbg? dbg-print? in-hang?
           one-line? string-str? string-color depth max-depth trim-comments?
           in-code? max-hang-depth max-hang-span max-hang-count],
    :as options} indent zloc]
  (let [avail (- width indent)
        ; note that depth affects how comments are printed, toward the end
        options (assoc options :depth (inc depth))
        options (if (or dbg? dbg-print?)
                  (assoc options
                    :dbg-indent (str (get options :dbg-indent "")
                                     (cond one-line? "o"
                                           in-hang? "h"
                                           :else ".")))
                  options)
        _ (dbg options
               "fzprint* **** rightcnt:"
               rightcnt
               (pr-str (zstring zloc)))
        dbg-data @fzprint-dbg
        dbg-focus? (and dbg? (= dbg-data (second (zfind-path zloc))))
        options (if dbg-focus? (assoc options :dbg :on) options)
        _ (if dbg-focus? (println "fzprint dbg-data:" dbg-data))]
    #_(def zlocx zloc)
    (cond (and (> depth max-depth) (zcoll? zloc))
            (if (= zloc (zdotdotdot))
              [["..." (zcolor-map options :none) :element]]
              [["##" (zcolor-map options :keyword) :element]])
          (and in-hang?
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
          (zmap? zloc) (fzprint-map options indent zloc)
          (zset? zloc) (fzprint-set options indent zloc)
          (zanonfn? zloc) (fzprint-anon-fn options indent zloc)
          (zfn-obj? zloc) (fzprint-fn-obj options indent zloc)
          (zarray? zloc)
            (if (:object? (:array options))
              (fzprint-object options indent zloc)
              (fzprint-array #?(:clj (if (:hex? (:array options))
                                       (assoc options
                                         :hex? (:hex? (:array options))
                                         :shift-seq (zarray-to-shift-seq zloc))
                                       options)
                                :cljs options)
                             indent
                             (zexpandarray zloc)))
          (zatom? zloc) (fzprint-atom options indent zloc)
          (zmeta? zloc) (fzprint-meta options indent zloc)
          (prefix-tags (ztag zloc)) (fzprint-prefix*
                                      (prefix-options options (ztag zloc))
                                      indent
                                      zloc
                                      (prefix-tags (ztag zloc)))
          (zns? zloc) (fzprint-ns options indent zloc)
          (or (zpromise? zloc) (zfuture? zloc) (zdelay? zloc) (zagent? zloc))
            (fzprint-future-promise-delay-agent options indent zloc)
          (zreader-macro? zloc) (fzprint-reader-macro options indent zloc)
          ; This is needed to not be there for newlines in parse-string-all,
          ; but is needed for respect-nl? support.
          (and (= (ztag zloc) :newline) (> depth 0)) [["\n" :none :newline]]
          :else
            (let [zstr (zstring zloc)
                  overflow-in-hang?
                    (and in-hang?
                         (> (+ (count zstr) indent (or rightcnt 0)) width))]
              (cond
                (zcomment? zloc)
                  (let [zcomment
                          ; Do we have a file-level comment that is way too
                          ; long??
                          (if (and (zero? depth) (not trim-comments?))
                            zstr
                            (clojure.string/replace zstr "\n" ""))
                        ; Only check for inline comments if we are doing them
                        ; otherwise we get left with :comment-inline element
                        ; types that don't go away
                        inline-spaces (when (:inline? (:comment options))
                                        (zinlinecomment? zloc))]
                    (if (and (:count? (:comment options)) overflow-in-hang?)
                      (do (dbg options "fzprint*: overflow comment ========")
                          nil)
                      #_[[zcomment (zcolor-map options :comment) :comment]]
                      (if inline-spaces
                        [[zcomment (zcolor-map options :comment) :comment-inline
                          inline-spaces]]
                        [[zcomment (zcolor-map options :comment) :comment]])))
                ; Really just testing for whitespace, comments filtered above
                (zwhitespaceorcomment? zloc) [[zstr :none :whitespace]]
                ; At this point, having filtered out whitespace and
                ; comments above, now we expect zsexpr will work for all of
                ; the remaining things.
                ;
                ; If we are going to overflow, and we are doing a hang, let's
                ; stop now!
                overflow-in-hang?
                  (do (dbg options "fzprint*: overflow <<<<<<<<<<") nil)
                (zkeyword? zloc) [[zstr (zcolor-map options :keyword) :element]]
                (string? (zsexpr zloc))
                  [[(if string-str?
                      (str (zsexpr zloc))
                      ; zstr
                      (zstring zloc))
                    (if string-color string-color (zcolor-map options :string))
                    :element]]
                (showfn? fn-map (zsexpr zloc)) [[zstr (zcolor-map options :fn)
                                                 :element]]
                (show-user-fn? options (zsexpr zloc))
                  [[zstr (zcolor-map options :user-fn) :element]]
                (number? (zsexpr zloc))
                  [[(if hex? (znumstr zloc hex? shift-seq) zstr)
                    (zcolor-map options :number) :element]]
                (nil? (zsexpr zloc)) [[zstr (zcolor-map options :nil) :element]]
                :else [[zstr (zcolor-map options :none) :element]])))))

;;
;; # Comment Wrap Support
;;

(defn last-space
  "Take a string and an index, and look for the last space prior to the
  index. If we wanted to tie ourselves to 1.8, we could use 
  clojure.string/last-index-of, but we don't.  However, we use similar
  conventions, i.e., if no space is found, return nil, and if the index
  is a space return that value, and accept any from-index, including one
  larger than the length of the string."
  [s from-index]
  (let [from-index (min (dec (count s)) from-index)
        rev-seq (reverse (take (inc from-index) s))
        seq-after-space (take-while #(not= % \space) rev-seq)
        space-index (- from-index (count seq-after-space))]
    (if (neg? space-index) nil space-index)))

(defn next-space
  "Take a string and an index, and look for the next space *after* the
  index. If no space is found, return nil. Accept any from-index, 
  including one larger than the length of the string."
  [s from-index]
  (let [from-index (inc from-index)]
    (when (< from-index (count s))
      (let [seq-after-space (take-while #(not= % \space)
                                        (drop from-index (seq s)))
            space-index (+ from-index (count seq-after-space))]
        (if (>= space-index (count s)) nil space-index)))))

; transient may have made this worse
(defn wrap-comment
  "If this is a comment, and it is too long, word wrap it to the right width.
  Note that top level comments may well end with a newline, so remove it
  and reapply it at the end if that is the case."
  [width [s color stype :as element] start]
  (if-not (= stype :comment)
    element
    (let [comment-width (- width start)
          semi-str (re-find #";*" s)
          rest-str (subs s (count semi-str))
          space-str (re-find #" *" rest-str)
          rest-str (subs rest-str (count space-str))
          newline? (re-find #"\n$" s)
          comment-width (- comment-width (count semi-str) (count space-str))
          #_(println "\ncomment-width:" comment-width
                     "semi-str:" semi-str
                     "space-str:" space-str
                     "rest-str:" rest-str)]
      (loop [comment-str rest-str
             out (transient [])]
        #_(prn "comment-str:" comment-str)
        (if (empty? comment-str)
          (if (zero? (count out))
            ;(empty? out)
            (if newline?
              [[semi-str color stype] ["\n" :none :indent]]
              [[semi-str color stype]])
            (persistent! (if newline? (conj! out ["\n" :none :indent]) out)))
          (let [last-space-index (if (<= (count comment-str) comment-width)
                                   (dec (count comment-str))
                                   (if (<= comment-width 0)
                                     (or (next-space comment-str 0)
                                         (dec (count comment-str)))
                                     (or (last-space comment-str comment-width)
                                         (next-space comment-str comment-width)
                                         (dec (count comment-str)))))
                next-comment (clojure.string/trimr
                               (subs comment-str 0 (inc last-space-index)))]
            #_(prn "last-space-index:" last-space-index
                   "next-comment:" next-comment)
            (recur
              (subs comment-str (inc last-space-index))
              (if (zero? (count out))
                ;(empty? out)
                (conj! out [(str semi-str space-str next-comment) color stype])
                (conj! (conj! out [(str "\n" (blanks start)) :none :indent])
                       [(str semi-str space-str next-comment) color
                        :comment-wrap])))))))))

(defn loc-vec
  "Takes the start of this vector and the vector itself."
  [start [s]]
  (let [split (split-lf s)
        #_(clojure.string/split s #"\n")]
    (if (= (count split) 1) (+ start (count s)) (count (last split)))))

(defn style-loc-vec
  "Take a style-vec and produce a style-loc-vec with the starting column
  of each element in the style-vec."
  [style-vec]
  (butlast (reductions loc-vec 0 style-vec)))

; Transient didn't help here, rather it hurt a bit.

(defn lift-vec
  "Take a transient output vector and a vector and lift any style-vec elements
  out of the input vector."
  [out-vec element]
  (if (string? (first element))
    (conj out-vec element)
    (loop [element-vec element
           out out-vec]
      (if-not element-vec
        out
        (recur (next element-vec) (conj out (first element-vec)))))))

(defn lift-style-vec
  "Take a style-vec [[s color type] [s color type] [[s color type]
  [s color type]] [s color type] ...] and lift out the inner vectors."
  [style-vec]
  (reduce lift-vec [] style-vec))

(defn fzprint-wrap-comments
  "Take the final output style-vec, and wrap any comments which run over
  the width. Looking for "
  [{:keys [width], :as options} style-vec]
  #_(def wcsv style-vec)
  (let [start-col (style-loc-vec style-vec)
        #_(def stc start-col)
        _ (dbg options "fzprint-wrap-comments: style-vec:" (pr-str style-vec))
        _ (dbg options "fzprint-wrap-comments: start-col:" start-col)
        wrap-style-vec (mapv (partial wrap-comment width) style-vec start-col)
        #_(def wsv wrap-style-vec)
        _ (dbg options "fzprint-wrap-comments: wrap:" (pr-str style-vec))
        out-style-vec (lift-style-vec wrap-style-vec)]
    out-style-vec))

(defn fzprint-inline-comments
  "Try to bring inline comments back onto the line on which they belong."
  [{:keys [width], :as options} style-vec]
  #_(def fic style-vec)
  (loop [cvec style-vec
         out []]
    (if-not cvec
      out
      (let [[s c e :as element] (first cvec)
            [_ _ ne nn :as next-element] (second cvec)
            new-element (cond (and (= e :indent) (= ne :comment-inline))
                                [(blanks nn) c :whitespace]
                              (= e :comment-inline) [s c :comment]
                              :else element)]
        (recur (next cvec) (conj out new-element))))))

;;
;; # External interface to all fzprint functions
;;

(defn fzprint
  "The pretty print part of fzprint."
  [options indent zloc]
  #_(def opt options)
  #_(println "fzprint: indent:" indent "(:indent options)" (:indent options))
  ; if we are doing specs, find the docstring and modify it with
  ; the spec output.
  #_(println "fn-name:" (:fn-name options))
  #_(println "spec:" (:value (:spec options)))
  (let [zloc (if-not (and (= (:ztype options) :zipper) (:value (:spec options)))
               zloc
               (add-spec-to-docstring zloc (:value (:spec options))))
        style-vec (fzprint* (assoc options
                              :depth 0
                              :map-depth 0)
                            indent
                            zloc)]
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
                    (apply conj-it! out (seq (blanks tab-expansion)))
                    (conj! out this-char))))))))
  ([s] (expand-tabs 8 s)))

;;
;; # Needed for expectations testing
;;
;; Seems defrecord doesn't work in test environment, which is pretty odd.
;;

(defrecord r [left right])
(defn make-record [l r] (new r l r))

;;
;; End of testing functions
;;