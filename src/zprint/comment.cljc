(ns ^:no-doc zprint.comment
  #?@(:cljs [[:require-macros
              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])
  (:require #?@(:clj [[zprint.macros :refer
                       [dbg-pr dbg dbg-form dbg-print zfuture]]])
            [clojure.string :as s]
            [zprint.zfns :refer [zstring ztag]]
            [zprint.zutil]
            #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))

#_(tufte/add-basic-println-handler! {})

;;
;; # Utility Functions
;;

;
; Interestingly, this is faster than (apply str (repeat n \space)) by
; about 30%.

(defn blanks
  "Produce a blank string of desired size."
  [n]
  (apply str (repeat n " ")))

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

(defn tag-l-size
  "Given a tag into which you can go down from rewrite-clj, which must be
  a collection of some kind, return the size the l-str.  All of the tag
  values into which you can go down must be in this list for indent-before
  to work correctly.  It uses these values when it steps up out of one of
  these things to see how big the thing would have been if it showed up
  as characters."
  [t]
  (case t
    :list 1
    :vector 1
    :set 2
    :map 1
    :uneval 2
    :reader-macro 1
    :meta 1
    :quote 1
    :syntax-quote 1
    :fn 2
    :unquote 1
    :deref 1
    :namespaced-map 1
    0))

(defn left-or-up
  "Take a zloc and move left if possible, or move up if necessary.
  Return a vector with [up-size new-zloc]"
  [zloc]
  (loop [ploc zloc
         total-up 0]
    #_(prn "left-or-up: ploc:" (zstring ploc) "total-up:" total-up)
    (let [next-left (zprint.zutil/left* ploc)]
      (if next-left
        [total-up next-left]
        ; can't go left, what about up?
        (let [moving-up (zprint.zutil/up* ploc)
              up-tag (when moving-up (zprint.zutil/tag moving-up))
              up-size (tag-l-size up-tag)]
          #_(prn "left-or-up: up-tag:" up-tag)
          (if-not moving-up
            ; can't go up, ran out of expression
            [total-up nil]
            (recur moving-up (+ total-up up-size))))))))

(defn length-after-newline
  "Given a string, return the number of characters to the right
  of any newlines in the string.  Will return nil if no newlines
  in the string."
  [s]
  (let [nl-split (clojure.string/split (str s " ") #"\n")
        nl-num (dec (count nl-split))]
    (when-not (zero? nl-num) (dec (count (last nl-split))))))

(defn length-before
  "Given a zloc, find the amount of printing space before it on its
  current line."
  [zloc]
  (let [[up-size next-zloc] (left-or-up zloc)]
    (loop [ploc next-zloc
           indent-before up-size]
      (if-not ploc
        (do #_(prn "length-before: if-not ploc:" indent-before) indent-before)
        ; we assume we have a ploc
        (let [zstr (if ploc (zstring ploc) "")
              length-right-of-newline (length-after-newline zstr)
              [up-size next-zloc] (left-or-up ploc)]
          #_(prn "length-before: (nil? ploc):" (nil? ploc)
                 "zstr:" zstr
                 "up-size:" up-size
                 "length-right-of-newline:" length-right-of-newline
                 "(tag ploc):" (zprint.zutil/tag ploc)
                 "ploc:" (zstring ploc)
                 "next-zloc:" (zstring next-zloc))
          (if length-right-of-newline
            ; hit a newline
            (do #_(prn "length-before: length-right-of-newline:"
                         length-right-of-newline
                       "indent-before:" indent-before)
                (+ length-right-of-newline indent-before))
            (recur next-zloc (+ indent-before (count zstr) up-size))))))))

;;
;; # Comment Wrap Support
;;

(defn inlinecomment?
  "If this is an inline comment, returns a vector with the amount
  of space that was between this and the previous element and the
  starting column of this inline comment.  That means that if we
  go left, we get something other than whitespace before a newline.
  If we get only whitespace before a newline, then this is considered
  an inline comment if the comment at the end of the previous line
  was an inline comment and we were aligned with that comment.
  Assumes zloc is a comment."
  [zloc]
  #_(prn "inlinecomment? zloc:" (zstring zloc))
  (loop [nloc (zprint.zutil/left* zloc)
         spaces 0
         passed-nl? false]
    (let
      #?(:clj [tnloc (ztag nloc)]
         :cljs [[tnloc spaces]
                (let [tnloc (ztag nloc)]
                  (if (= tnloc :whitespace)
                    ; might be whitespace with an embedded comma in cljs
                    (let [nstr (zstring nloc)
                          trim-nstr (clojure.string/trimr nstr)]
                      (if (pos? (count trim-nstr))
                        ; it had something besides spaces in it
                        ; we will assume a comma
                        ;  correct things
                        [:comma (+ spaces (- (count nstr) (count trim-nstr)))]
                        ; it was all whitespace -- don't correct
                        [:whitespace spaces]))
                    [tnloc spaces]))])
      #_(prn "inlinecomment? tnloc:" tnloc
             "spaces:" spaces
             "nloc:" (zstring nloc))
      (cond
        (nil? tnloc) nil  ; the start of the zloc
        (= tnloc :newline) (recur (zprint.zutil/left* nloc) spaces true)
        (or (= tnloc :comment) (= tnloc :comment-inline))
          ; Two comments in a row don't have a newline showing between
          ; them, it is captured by the first comment.  Sigh.
          ; Except now it isn't, as we split the newlines out.
          (do #_(prn "inlinecomment? found previous comment!")
              ; is it an inline comment?
              (when (inlinecomment? nloc)
                ; figure the total alignment from the newline
                (let [nloc-length-before (length-before nloc)
                      zloc-length-before (length-before zloc)]
                  #_(prn "inlinecomment?:"
                         "nloc-length-before:" nloc-length-before
                         "zloc-length-before:" zloc-length-before
                         "spaces:" spaces)
                  (if (= nloc-length-before zloc-length-before)
                    ; we have a lineup
                    [spaces zloc-length-before]
                    nil))))
        (not= tnloc :whitespace)
          (if passed-nl? nil [spaces (length-before zloc)])
        :else (recur (zprint.zutil/left* nloc)
                     ^long (+ ^long (zprint.zutil/length nloc) spaces)
                     passed-nl?)))))

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
  (if-not (or (= stype :comment) (= stype :comment-inline))
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
            (if newline?
              [[semi-str color stype] ["\n" :none :indent 38]]
              [[semi-str color stype]])
            (persistent! (if newline? (conj! out ["\n" :none :indent 39]) out)))
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
                (conj! (conj! out [(str "\n" (blanks start)) :none :indent 40])
                       [(str semi-str space-str next-comment) color
                        :comment-wrap])))))))))

(defn loc-vec
  "Takes the start of this vector and the vector itself."
  [start [s]]
  (let [split (split-lf s)]
    (if (= (count split) 1) (+ start (count s)) (count (last split)))))

(defn style-loc-vec
  "Take a style-vec and produce a style-loc-vec with the starting column
  of each element in the style-vec. Accepts a beginning indent."
  [indent style-vec]
  (butlast (reductions loc-vec indent style-vec)))

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
  (dbg options "fzprint-wrap-comments: indent:" (:indent options))
  #_(def wcsv style-vec)
  (let [start-col (style-loc-vec (or (:indent options) 0) style-vec)
        #_(def stc start-col)
        _ (dbg options "fzprint-wrap-comments: style-vec:" (pr-str style-vec))
        _ (dbg options "fzprint-wrap-comments: start-col:" start-col)
        wrap-style-vec (mapv (partial wrap-comment width) style-vec start-col)
        #_(def wsv wrap-style-vec)
        _ (dbg options "fzprint-wrap-comments: wrap:" (pr-str style-vec))
        out-style-vec (lift-style-vec wrap-style-vec)]
    out-style-vec))

(defn find-element-from-end
  "Find a the first element of this type working from the end of a 
  style-vec.  Return the index of the element."
  [element-pred? style-vec]
  (loop [index (dec (count style-vec))]
    (if (neg? index)
      nil
      (let [[_ _ e] (nth style-vec index)]
        (if (element-pred? e) index (recur (dec index)))))))

(defn line-size
  "Given a style-vec, how big is it in actual characters.  This doesn't
  handle newlines."
  [style-vec]
  (apply + (map (partial loc-vec 0) style-vec)))

(defn space-before-comment
  "Given a style-vec, whose last element in a comment, find the amount
  of space before that comment on the line."
  [style-vec]
  (let [indent-index (find-element-from-end #(or (= % :indent) (= % :newline))
                                            style-vec)
        this-line-vec
          (if indent-index (nthnext style-vec indent-index) style-vec)]
    (line-size (butlast this-line-vec))))

(defn fzprint-inline-comments
  "Try to bring inline comments back onto the line on which they belong."
  [{:keys [width], :as options} style-vec]
  #_(def fic style-vec)
  (dbg-pr options "fzprint-inline-comments:" style-vec)
  (loop [cvec style-vec
         last-out ["" nil nil]
         out []]
    (if-not cvec
      (do #_(def fico out) out)
      (let [[s c e :as element] (first cvec)
            [_ _ ne nn :as next-element] (second cvec)
            [_ _ le] last-out
            new-element
              (cond (and (or (= e :indent) (= e :newline))
                         (= ne :comment-inline))
                      (if-not (or (= le :comment) (= le :comment-inline))
                        ; Regular line to get the inline comment
                        [(blanks nn) c :whitespace 25]
                        ; Last element was a comment...
                        ; Can't put a comment on a comment, but
                        ; we want to indent it like the last
                        ; comment.
                        ; How much space before the last comment?
                        (do #_(prn "inline:" (space-before-comment out))
                            [(str "\n" (blanks (space-before-comment out))) c
                             :indent 41]
                            #_element))
                    :else element)]
        (recur (next cvec) new-element (conj out new-element))))))

;;
;; ## Align inline comments
;;

(def max-aligned-inline-comment-distance 5)

(defn find-aligned-inline-comments
  "Given a style-vec, find previously aligned inline comments and
  output the as a sequence of vectors of comments. The previously
  aligned comments do not have to be consecutive, but they can't
  be separated by more than max-aligned-inline-comment-distance.
  Each comment itself is a vector: [indent-index inline-comment-index],
  yielding a [[[indent-index inline-comment-index] [indent-index
  inline-comment-index] ...] ...].  The indexes are into the
  style-vec."
  [style-vec]
  #_(def fcic style-vec)
  (loop [cvec style-vec
         index 0
         last-indent 0
         current-seq []
         current-column 0
         distance 0
         out []]
    (if-not cvec
      (let [out (if (> (count current-seq) 1) (conj out current-seq) out)]
        #_(def fcico out)
        out)
      (let [[s c e spaces start-column :as element] (first cvec)]
        (cond
          (= e :comment-inline)
            (if (= start-column current-column)
              ; include this inline comment in the current-seq, since
              ; it has the same starting column
              (recur (next cvec)
                     (inc index)
                     nil
                     (if last-indent
                       (conj current-seq [last-indent index])
                       (do (throw
                             (#?(:clj Exception.
                                 :cljs js/Error.)
                              (str "find-aligned-inline-comments a:" index)))
                           []))
                     current-column
                     ; distance from last inline comment is zero
                     0
                     out)
              ; start a new current-seq, since this comment's starting
              ; column doesn't match the current-column of the current-seq
              (recur (next cvec)
                     (inc index)
                     nil
                     (if last-indent
                       [[last-indent index]]
                       (do (throw
                             (#?(:clj Exception.
                                 :cljs js/Error.)
                              (str "find-aligned-inline-comments b:" index)))
                           []))
                     ; new starting column
                     start-column
                     ; distance from the last inline comment is zero
                     0
                     ; if we have more than one current inline comments,
                     ; add them to the out vector
                     (if (> (count current-seq) 1) (conj out current-seq) out)))
          (or (= e :indent) (= e :newline))
            (if (>= distance max-aligned-inline-comment-distance)
              ; We have gone too far
              (recur (next cvec)
                     (inc index)
                     ; last-indent is this index
                     index
                     []
                     ; current-column
                     0
                     ; distance
                     0
                     (if (> (count current-seq) 1) (conj out current-seq) out))
              ; We have not gone too far
              (recur (next cvec)
                     (inc index)
                     ; last-indent is this index
                     index
                     current-seq
                     current-column
                     ; we've passed another line
                     (inc distance)
                     out))
          :else (recur (next cvec)
                       (inc index)
                       last-indent
                       current-seq
                       current-column
                       distance
                       out))))))

(defn find-consecutive-inline-comments
  "Given a style-vec, find consecutive inline comments and output
  the as a sequence of vectors of comments.  Each comment itself
  is a vector: [indent-index inline-comment-index], yielding a
  [[[indent-index inline-comment-index] [indent-index inline-comment-index]
  ...] ...]"
  [style-vec]
  #_(def fcic style-vec)
  (loop [cvec style-vec
         index 0
         last-indent 0
         current-seq []
         out []]
    (if-not cvec
      (do #_(def fcico out) out)
      (let [[s c e :as element] (first cvec)]
        (cond
          (= e :comment-inline)
            (recur (next cvec)
                   (inc index)
                   nil
                   (if last-indent
                     (conj current-seq [last-indent index])
                     (do (throw
                           (#?(:clj Exception.
                               :cljs js/Error.)
                            (str "find-consecutive-inline-comments:" index)))
                         []))
                   out)
          (or (= e :indent) (= e :newline))
            (recur (next cvec)
                   (inc index)
                   index
                   (if last-indent
                     ; if we have a last-indent, then we didn't
                     ; just have a comment
                     []
                     ; if we don't have a last-indent, then we
                     ; did just have a comment previously, so keep
                     ; collecting comments
                     current-seq)
                   (if last-indent
                     ; if we have a last-indent, then we didn't
                     ; just have a comment.  But if we have more
                     ; than one comment vector in current-seq,
                     ; make sure we keep track of that
                     (if (> (count current-seq) 1) (conj out current-seq) out)
                     ; if we didn't have last-indent, then we
                     ; just had a comment, so keep collecting
                     ; them
                     out))
          :else (recur (next cvec) (inc index) last-indent current-seq out))))))

(defn comment-column
  "Takes a single vector of [indent-index comment-index] and will show the
  column on the line in which the comment starts."
  [[indent-index comment-index] style-vec]
  (when-not (vector? style-vec)
    (throw (#?(:clj Exception.
               :cljs js/Error.)
            (str "comment-column: style-vec not a vector!! " style-vec))))
  (loop [index indent-index
         column 0]
    (if (= index comment-index)
      column
      (recur (inc index) (loc-vec column (nth style-vec index))))))

(defn comment-vec-column
  "Take a single inline comment vector:
  [indent-index inline-comment-index] 
  and replace it with [inline-comment-index start-column spaces-before]."
  [style-vec [indent-index inline-comment-index :as comment-vec]]
  (let [start-column (comment-column comment-vec style-vec)
        spaces-before (loc-vec 0 (nth style-vec (dec inline-comment-index)))]
    [inline-comment-index start-column spaces-before]))

(defn comment-vec-seq-column
  "Take a single vector of inline comments
  [[indent-index inline-comment-index] [indent-index inline-comment-index]
   ...] and replace it with [[inline-comment-index start-column spaces-before]
   [inline-comment-index start-column spaces-before] ...]"
  [style-vec comment-vec-seq]
  (map (partial comment-vec-column style-vec) comment-vec-seq))

(defn comment-vec-all-column
  "Take a seq of all of the comments as produced by 
  find-consecutive-inline-comments, and turn it into:
  [[[inline-comment-index start-column spaces-before] [inline-comment-index
  start-column spaces-before]
  ...] ...]"
  [style-vec comment-vec-all]
  (map (partial comment-vec-seq-column style-vec) comment-vec-all))

(defn minimum-column
  "Given a set of inline comments:
  [[inline-comment-index start-column spaces-before]
   [inline-comment-index start-column spaces-before] ...], determine
   the minimum column at which they could be aligned."
  [comment-vec]
  (let [minimum-vec (map #(inc (- (second %) (nth % 2))) comment-vec)
        minimum-col (apply max minimum-vec)]
    minimum-col))

(defn change-start-column
  "Given a new start-column, and a vector 
  [[inline-comment-index start-column spaces-before]
  and a style-vec, return a new style-vec with the inline-comment starting
  at a new column."
  [new-start-column style-vec
   [inline-comment-index start-column spaces-before :as comment-vec]]
  (let [delta-spaces (- new-start-column start-column)
        new-spaces (+ spaces-before delta-spaces)
        previous-element-index (dec inline-comment-index)
        #_(prn "change-start-column:"
               "spaces-before:" spaces-before
               "delta-spaces:" delta-spaces
               "new-spaces:" new-spaces)
        [s c e :as previous-element] (nth style-vec previous-element-index)
        new-previous-element
          (cond (= e :indent) [(str "\n" (blanks new-spaces)) c e]
                (= e :whitespace) [(str (blanks new-spaces)) c e 26]
                :else (throw
                        (#?(:clj Exception.
                            :cljs js/Error.)
                         (str "change-start-column: comment preceded by neither"
                              " an :indent nor :whitespace!"
                              e))))]
    (assoc style-vec previous-element-index new-previous-element)))

(defn align-comment-vec
  "Given one set of inline comments: 
  [[inline-comment-index start-column spaces-before]
   [inline-comment-index start-column spaces-before] ...], align them 
   as best as possible, and return the modified style-vec."
  [style-vec comment-vec]
  (let [minimum-col (minimum-column comment-vec)]
    (reduce (partial change-start-column minimum-col) style-vec comment-vec)))

(defn fzprint-align-inline-comments
  "Given the current style-vec, align all consecutive inline comments."
  [options style-vec]
  (dbg-pr options "fzprint-align-inline-comments: style-vec:" style-vec)
  (let [style (:inline-align-style (:comment options))]
    (if (= style :none)
      style-vec
      (let [comment-vec (cond (= style :aligned) (find-aligned-inline-comments
                                                   style-vec)
                              (= style :consecutive)
                                (find-consecutive-inline-comments style-vec))
            comment-vec-column (comment-vec-all-column style-vec comment-vec)]
        (reduce align-comment-vec style-vec comment-vec-column)))))

