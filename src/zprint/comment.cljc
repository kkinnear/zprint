(ns ^:no-doc zprint.comment
  #?@(:cljs [[:require-macros
              [zprint.macros :refer
               [dbg dbg-pr dbg-form dbg-print dbg-s dbg-s-pr zfuture]]]])
  (:require #?@(:clj [[zprint.macros :refer
                       [dbg-pr dbg dbg-form dbg-print dbg-s dbg-s-pr zfuture]]])
            [clojure.string :as s]
            [zprint.zfns :refer [zstring ztag]]
	    [zprint.util :refer [variance]]
            [rewrite-clj.zip :as z :refer [left* up* tag length]]
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

(defn capital?
  "True if the first letter of the string is a capital letter."
  [s]
  (re-find #"^[A-Z]" s))

(defn capitalize-first
  "Capitalize the first character.  Don't mess with the others."
  [s]
  (str (clojure.string/capitalize (subs s 0 1)) (subs s 1)))

(defn adjust-border
  "Possibly reduce the border because we don't have a lot of space left.
  Returns a new width."
  [start-col max-width border width]
  (let [actual-space (- max-width start-col)
        ; Adjust border for how much space we really have
        ; but don't use too much!
        border (int (min border (/ actual-space 6)))
        new-width (- width border)]
    #_(println "adjust-border: start-col:" start-col
             "max-width:" max-width
             "border:" border
             "width:" width
             "output width:" new-width)
    new-width))

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
    (let [next-left (left* ploc)]
      (if next-left
        [total-up next-left]
        ; can't go left, what about up?
        (let [moving-up (up* ploc)
              up-tag (when moving-up (tag moving-up))
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
                 "(tag ploc):" (tag ploc)
                 "ploc:" (zstring ploc)
                 "next-zloc:" (zstring next-zloc))
          (if length-right-of-newline
            ; hit a newline
            (do #_(prn "length-before: length-right-of-newline:"
                         length-right-of-newline
                       "indent-before:" indent-before)
                (+ length-right-of-newline indent-before))
            (recur next-zloc (+ indent-before (count zstr) up-size))))))))


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
  (loop [nloc (left* zloc)
         spaces 0
         passed-nl? false]
    (let #?(:clj [tnloc (ztag nloc)]
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
                           [:comma
                            (+ spaces (- (count nstr) (count trim-nstr)))]
                           ; it was all whitespace -- don't correct
                           [:whitespace spaces]))
                       [tnloc spaces]))])
         #_(prn "inlinecomment? tnloc:" tnloc
                "spaces:" spaces
                "nloc:" (zstring nloc))
         (cond (nil? tnloc) nil ; the start of the zloc
               (= tnloc :newline) (recur (left* nloc) spaces true)
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
               :else (recur (left* nloc)
                            ^long (+ ^long (length nloc) spaces)
                            passed-nl?)))))

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
  [width border [s color stype :as element] start]
  (if-not (or (= stype :comment) (= stype :comment-inline))
    element
    (let [width (adjust-border start width border width)
          comment-width (- width start)
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
  [{:keys [width], {:keys [border]} :comment, :as options} style-vec]
  #_(def wcsv style-vec)
  (let [start-col (style-loc-vec (or (:indent options) 0) style-vec)
        _ (dbg-s options
                 #{:comment-wrap}
                 "fzprint-wrap-comments: indent:" (:indent options)
                 "border:" border
                 "count style-vec:" (count style-vec)
                 "count start-col:" (count start-col)
                 "start-col:" start-col)
        #_(def stc start-col)
        _ (dbg-s options
                 #{:comment-wrap}
                 "fzprint-wrap-comments: style-vec:"
                 ((:dzprint options) {:list {:wrap? true, :indent 1}} style-vec)
                 #_(pr-str style-vec))
        _ (dbg-s options #{:wrap} "fzprint-wrap-comments: start-col:" start-col)
        wrap-style-vec (mapv (partial wrap-comment width border) style-vec start-col)
        #_(def wsv wrap-style-vec)
        _ (dbg-s
            options
            #{:comment-wrap}
            "fzprint-wrap-comments: wrapped:"
            ((:dzprint options) {:list {:wrap? true, :indent 1}} wrap-style-vec)
            #_(pr-str style-vec))
        out-style-vec (lift-style-vec wrap-style-vec)]
    out-style-vec))

(defn fzprint-wrap-comments-alt
  "Take the final output style-vec, and wrap any comments which run over
  the width. Looking for "
  [{:keys [width], :as options} style-vec]
  #_(def wcsv style-vec)
  (let [start-col (style-loc-vec (or (:indent options) 0) style-vec)
        _ (dbg-s options
                 #{:wrap}
                 "fzprint-wrap-comments: indent:" (:indent options)
                 "count style-vec:" (count style-vec)
                 "count start-col:" (count start-col))
        #_(def stc start-col)
        _ (dbg-s options
                 #{:wrap}
                 "fzprint-wrap-comments: style-vec:"
                 ((:dzprint options) {:list {:wrap? true, :indent 1}} style-vec)
                 #_(pr-str style-vec))
        _ (dbg-s options #{:wrap} "fzprint-wrap-comments: start-col:" start-col)
        wrap-style-vec (mapv (partial wrap-comment width) style-vec start-col)
        #_(def wsv wrap-style-vec)
        _ (dbg-s options
                 #{:wrap}
                 "fzprint-wrap-comments: wrap:"
                 (pr-str style-vec))
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

;;
;; # Smart Wrap
;;

(defn get-semis-and-spaces
  "Given a string that starts with a semicolon, count the semicolons
  and the spaces after it, and return both: [semi-count space-count blank?]."
  [s]
  (let [semi-str (re-find #"^;*" s)
        rest-str (subs s (count semi-str))
        space-str (re-find #"^ *" rest-str)]
    [(count semi-str) (count space-str)]))

(defn match-regex-seq
  "Given a vector of regex expressions, attempt to match each 
  against a string.  If any match, return the matched value which
  must be a vector, else nil."
  [regex-seq regex-source groups? s]
  (when (first regex-seq)
    (let [match (try (re-find (first regex-seq) s)
                     (catch #?(:clj Exception
                               :cljs :default)
                       e
                       (throw (#?(:clj Exception.
                                  :cljs js/Error.)
                               (str "Invalid regular expression: '"
                                      (pr-str (first regex-seq))
                                    "' in " regex-source
				      " produced " (str e))))))]
      #_(println "match-regex-seq:" match "s:" (pr-str s))
      (if match
        (if (or (vector? match) (not groups?))
          match
          (throw (#?(:clj Exception.
                     :cljs js/Error.)
                  (str "match-and-modify: regex from "
		       regex-source " doesn't return groups. Regex: '"
		       (pr-str (first regex-seq))
                       "'."))))
        (match-regex-seq (next regex-seq) regex-source groups? s)))))

(defn match-and-modify-comment
  "Given a series of regular expressions, which *must* match
  two groups where the first group is a variable number of semicolons
  at the beginning, and the second group is some exdented item like
  a 'o', '-', '1', or '1.', if any of them match, replace the second group 
  with spaces and return the resulting string, otherwise return the
  string unchanged. Returns [s skip?] in case the comment string
  matched the end+skip-cg vector of regexs."
  [end+start-cg end+skip-cg s]
  #_(println "match-and-modify-comment s:" s)
  (let [skip? (match-regex-seq end+skip-cg :end+skip-cg nil s)]
    (if skip?
      [s true]
      (let [match (match-regex-seq end+start-cg :end+start-cg true s)]
        (if match
          (let [[whole-match semi-match after-semi-match] match]
            #_(println "match-and-modify-comment match:" match)
            [(str semi-match
                  (blanks (count after-semi-match))
                  (subs s (count whole-match))) false])
          [s false])))))

(defn fit-in-comment-group?
  "Figure out if this comments fits in the current comment group.
  Start simple, enhance later. Returns :fit, :skip, :next to represent
  what to do when dealing with this line.  :fit means it goes in the
  current comment group, :skip means that it doesn't go into any
  comment group, and :next means that it goes in the next comment group."
  [s this-start-col end+start-cg end+skip-cg start-col number-semis current-spacing]
  #_(println "fit-in-comment-group? s:" s)
  (let [[semi-count space-count] (get-semis-and-spaces s)
        len (count s)]
    (cond 
      ; If it matches either regex group, then it terminates a group
      (match-regex-seq end+skip-cg :end+skip-cg nil s) :skip
      (match-regex-seq end+start-cg  :end+start-cg true s) :next
      (and (= this-start-col start-col)
           (= semi-count number-semis)
           (= space-count current-spacing)) :fit
      :else :next)))

(defn get-next-comment-group
  "Get the next comment group.  A comment groups is a set of comments
  which are:
    o On contiguous lines
    o Contain no blank comment lines
    o Have the starting column of their first ; the same
    o Have the same number of beginning ; characters
    o Have the same number of spaces after their rightmost ;
       or
    o Have an outdented single character or number, and have the
      same number of spaces between the rightmost semi and the beginning
      of the text if you ignore the outdented character or number


  The final critiera is managed by a set of regular expressions
  which each match two groups -- the contiguous ; characters, and
  any outdented information after the rightmost ; and before the
  remaining text.

  Takes a starting index, and two vectors.  The start-col-vec is a
  vector of the starting columns of each element in the style-vec.
  They must be the same length!  Returns a vector with the starting
  element in the style vec of each comment of this comment group.
  Returns [depth [next-element-index semi-count space-count
  comment-group-members]] If next-element-index is nil, this was
  the last comment group in the style-vec and it may or may not
  have any additional indexes in the vector.
  
  Also tracks the depth, and whether any comments are interesting
  if they are at the top level."
  [{{:keys [smart-wrap?],
     {:keys [end+start-cg end+skip-cg top-level?]} :smart-wrap}
      :comment,
    :as options} #_top-level depth index start-col-vec style-vec]
  (dbg-s options
	 #{:smart-wrap}
	 "get-next-comment-group: depth:" depth
	 "end+start-cg" end+start-cg
	 "end+skip-cg" end+skip-cg)
  (loop [idx index
         depth depth
         start-col 0
         number-semis 0 ; non-zero says we currently in a group
         current-spacing 0
         nl-indent-count 0
         out [nil 0 0]]
    ; Find the start of the comment group
    (let [[s color what :as element] (nth style-vec idx ["" :none :end])]
      (dbg-s-pr options
             #{:smart-wrap}
             "get-next-comment-group: depth:" depth
	     "s:" s
	     "what:" what
	     "number-semis:" number-semis
	     "current-spacing:" current-spacing
	     "out:" out)
      (if (= what :end)
        ; We are out of elements to search.  We might have been
        ; in a comment group, and we might not have.  In either
        ; case, the first element is nil, which is correct as it
        ; signals that there isn't another comment group to look for.
        ; Use number-semis as a sentinal for whether we are currently
        ; in a comment-group.
        (if (zero? number-semis)
          [depth out]
          [depth
           (assoc out
             1 number-semis
             2 current-spacing)])
        (cond
          (= what :left) (recur (inc idx)
                                (inc depth)
                                start-col
                                number-semis
                                current-spacing
                                nl-indent-count
                                out)
          (= what :right) (recur (inc idx)
                                 (dec depth)
                                 start-col
                                 number-semis
                                 current-spacing
                                 nl-indent-count
                                 out)
	  ; Don't bother looking for comments at the top level, since
	  ; every top level line is in a style-vec by itself.
	  (zero? depth) (recur (inc idx)
	                       depth
			       start-col
			       number-semis
			       current-spacing
			       nl-indent-count
			       out)
          :else
            (if (zero? number-semis)
              ; we are not yet in a group -- just looking for a comment
              (if (and (= what :comment) #_(or top-level? (> depth 0)))
                ; any comment starts a group
                (let [[s skip?]
                        (match-and-modify-comment end+start-cg end+skip-cg s)
                      ; having possibly modified the beginning of the
                      ; comment
                      [semi-count space-count] (when-not skip?
                                                 (get-semis-and-spaces s))]
                  #_(println "semi-count:" semi-count
                             "space-count:" space-count
                             "blank?" blank?
                             "s:" s)
                  (if skip?
                    ; blank comment line or one to skip, ignore it
                    (recur (inc idx) depth 0 0 0 0 out)
                    (recur (inc idx)
                           depth
                           (nth start-col-vec idx)
                           semi-count
                           space-count
                           nl-indent-count
                           (conj out idx))))
                ; not a comment, or not an interesting one, anyway
                ; and not yet in a group -- move on
                (recur (inc idx) depth 0 0 0 0 out))
              ; we are already in a group, see if we should remain in it
              (cond (or (= what :newline) (= what :indent))
                      (if (zero? nl-indent-count)
                        ; This is our first newline or indent, we are still
                        ; in the group.
                        (recur (inc idx)
                               depth
                               start-col
                               number-semis
                               current-spacing
                               1
                               out)
                        ; Too many newlines, we're done.
                        ; Start next comment group search with next
                        ; index.
                        [depth
                         (assoc out
                           0 (inc idx)
                           1 number-semis
                           2 current-spacing)])
                    (= what :comment)
                      ; Need to see if we should remain in the group
                      (let [fit-return (fit-in-comment-group? s
                                                              (nth start-col-vec
                                                                   idx)
                                                              end+start-cg
                                                              end+skip-cg
                                                              start-col
                                                              number-semis
                                                              current-spacing)]
                        (cond
                          (= fit-return :fit)
                            ; yes, this fits in the current group,
                            ; continue
                            (recur (inc idx)
                                   depth
                                   start-col    ; don't need a new one
                                   number-semis ; of any of these
                                   current-spacing
                                   0
                                   (conj out idx))
                          (= fit-return :skip) [depth
                                                (assoc out
                                                  0 (inc idx)
                                                  1 number-semis
                                                  2 current-spacing)]
                          (= fit-return :next) [depth
                                                (assoc out
                                                  0 idx
                                                  1 number-semis
                                                  2 current-spacing)]
                          :else
                            (throw
                              (#?(:clj Exception.
                                  :cljs js/Error.)
                               (str
                                 "Internal error in get-next-comment-group: '"
                                 fit-return
                                 "'.")))))
                    ; Not a newline or a comment, ends comment group
                    :else [depth
                           (assoc out
                             0 (inc idx)
                             1 number-semis
                             2 current-spacing)])))))))
	   

(defn wrap-onto-new-line
  "Given a comment group, wrap the final line onto a newly created line."
  [start-col-vec style-vec comment-group]
  )

(defn style-lines-in-comment-group
  "Do style-lines (rather differently) for a set of comments in a 
  comment-group.  Return [<line-count> <max-width> [line-lengths]]."
  [start-col-vec style-vec comment-group]
  ; We can do this much more easily, since we know that a comment
  ; group has only comments, and only one per line.  we also know
  ; the starting column of all of them.
  (let [lengths (mapv #(count (first (nth style-vec %))) comment-group)
        ; Add the starting column to the actual length.  Assumes
        ; they all have the same starting column!
        lengths (mapv (partial + (nth start-col-vec (first comment-group)))
                 lengths)
        max-length (apply max lengths)]
    [(count comment-group) max-length lengths]))

(defn first-space-left
  "Find the first space to the left of idx in the string s. Returns a number
  or nil if no space."
  [s idx]
  (clojure.string/last-index-of s " " idx))

(defn split-str-at-space-left
  "Split the string at the first space to the left of idx, if any is found.
  Return [start-str end-str].  If no space is found, end-str is nil.
  If idx is beyond the end of s, then puts all of s into start-str, and
  makes end-str be an empty string, not nil.
  Note -- leaves the first left space out of the resulting strings."
  [s idx]
  (if (>= idx (count s))
    [s ""]
    (let [space-left (first-space-left s idx)]
      (if space-left
        [(subs s 0 space-left) (subs s (inc space-left))]
        [s nil]))))

(defn insert-str-into-style-vec
  "Insert string s into the style-vec at index style-idx."
  [style-vec s style-idx]
  (let [[old-s color what :as element] (nth style-vec style-idx)]
    (assoc style-vec style-idx [s color what])))

(defn delete-style-vec-element
  "Make a style-vec element an empty string and remove :newline or :indent
   immediately before it."
  [style-vec style-idx]
  (let [[rs color right-what :as right-element] (nth style-vec style-idx)
        [ls color left-what :as left-element] (nth style-vec (dec style-idx))]
    #_(prn "delete-style-vec-element: " left-element right-element)
    (if (and (= right-what :comment)
             (or (= left-what :indent) (= left-what :newline)))
      (assoc style-vec
        style-idx ["" :none :deleted]
        (dec style-idx) ["" :none :deleted])
      #_style-vec
      (throw (#?(:clj Exception.
                 :cljs js/Error.)
              (str "can't delete style vec element idx: " style-idx
                   " element: " right-element))))))
	           
(defn ends-w-punctuation?
  "Take a string, an return true if it ends with some kind of
  punctuation.  Returns nil if it does not, which signals that
  some kind of terminating punctuation must be added."
  [s]
  (if (empty? s)
    true
    (or (clojure.string/ends-with? s ".")
        (clojure.string/ends-with? s "!")
        (clojure.string/ends-with? s "?"))))

(defn figure-separator
  "Takes two string fragments to glue together.  We assume a space
  between them (unless the left is empty), but figure out if we
  need to have a period and possibly capitalize the beginning of
  the second one.  Returns [separator new-right]"
  [left right]
  (let [terminated? (ends-w-punctuation? left)
        capitalized? (capital? right)
        right-more-than-one? (> (count (re-find #"^\w*" right)) 1)
	; If there is nothing on the left, we don't need a space.  We
	; also don't know if it was the end of a sentence, and have
	; no particularly convenient way to find out.
	left-empty? (empty? left)]
    #_(prn "figure-separator: left:" left "right:" right)
    (cond left-empty? ["" right]
          (and terminated? capitalized?) [" " right]
          terminated? [" " (capitalize-first right)]
          (and capitalized? right-more-than-one?) [". " right]
          :else [" " right])))

(defn figure-separator-alt
  "Takes two string fragments to glue together.  We assume a space
  between them (unless the left is empty), but figure out if we
  need to have a period and possibly capitalize the beginning of
  the second one.  Returns [separator new-right]"
  [left right]
  (let [ends-with-period? (clojure.string/ends-with? left ".")
        capitalized? (capital? right)
        right-more-than-one? (> (count (re-find #"^\w*" right)) 1)
	; If there is nothing on the left, we don't need a space.  We
	; also don't know if it was the end of a sentence, and have
	; no particularly convenient way to find out.
	left-empty? (empty? left)]
    #_(prn "figure-separator: left:" left "right:" right)
    (cond left-empty? ["" right]
          (and ends-with-period? capitalized?) [" " right]
          ends-with-period? [" " (capitalize-first right)]
          (and capitalized? right-more-than-one?) [". " right]
          :else [" " right])))

(defn move-ls-to-us
  "Look at just the text (not semis or initial spaces) of the us and ls
  and the available space, and determine if there is any reason to move
  some or all of the ls onto the us.  If there is, do it, and return new
  strings.  Returns [new-us new-ls].  If new-ls is nil, then no changes
  were warrented.  If new-ls is empty (but not nil), then it means that
  all of the text from the ls moved to the us.  Figures out the necessary
  separator(s), based in part of the existing punctuation."
  [us-text ls-text available-space]
  (let [; First, figure the separator(s), since we can't tell what will
        ; fit without knowing what separators to use, if any.
        [separator new-ls-text] (figure-separator us-text ls-text)
        available-space (- available-space (count separator))
        ; If new-ls is nil, no changes were made.  If new-ls is empty
        ; but non-nil, then it is all in new-end-us, to be moved up
        ; to the us in its entirety.
        [new-end-us new-ls] (split-str-at-space-left new-ls-text
                                                     available-space)]
    (if new-ls
      ; We have two pieces, so new-end-us goes up, and new-ls
      ; stays here.
      [(str us-text separator new-end-us) new-ls]
      ; We didn't make any changes, leave things as they are.
      [us-text nil])))

(defn move-ls-to-us-alt
  "Look at just the text (not semis or initial spaces) of the us and ls
  and the available space, and determine if there is any reason to move
  some or all of the ls onto the us.  If there is, do it, and return new
  strings.  Returns [new-us new-ls].  If new-ls is nil, then no changes
  were warrented.  If new-ls is empty (but not nil), then it means that
  all of the text from the ls moved to the us.  Figures out the necessary
  separator(s), based in part of the existing punctuation."
  [us-text ls-text available-space max-variance]
  (let [; First, figure the separator(s), since we can't tell what will
        ; fit without knowing what separators to use, if any.
        [separator new-ls-text] (figure-separator us-text ls-text)
        available-space (- available-space (count separator))
        interline-variance (variance [(count us-text) (count new-ls-text)])
        ; If new-ls is nil, no changes were made.  If new-ls is empty
        ; but non-nil, then it is all in new-end-us, to be moved up
        ; to the us in its entirety.
        [new-end-us new-ls] (if (< interline-variance max-variance)
                              [us-text nil]
                              (split-str-at-space-left new-ls-text
                                                       available-space))]
    (if new-ls
      ; We have two pieces, so new-end-us goes up, and new-ls
      ; stays here.
      [(str us-text separator new-end-us) new-ls]
      ; We didn't make any changes, leave things as they are.
      [us-text nil])))

(defn balance-two-comments
  "Take the index into the style-vec for two comments, and balance the
  words in them. Returns a new style-vec in [new-style-vec changed?]."
  [start-col-vec style-vec semi-count space-count usable-space upper-idx
   lower-idx comment-group]
  (let [[us ucolor uwhat :as upper-element] (nth style-vec upper-idx)
        [ls lcolor lwhat :as lower-element] (nth style-vec lower-idx)
        ; All of the space measurement up to this point includes the
        ; semis and spaces on the front of every line.
        upper-space (count us)
        lower-space (count ls) ]
    #_(prn "balance-two-comments: usable-space:" usable-space
           "upper-idx:" upper-idx
           "lower-idx:" lower-idx
           "us:" us
           "ls:" ls)
    (cond
      (> upper-space usable-space)
        ; move from upper to lower
        (let [[new-us new-start-ls] (split-str-at-space-left us usable-space)]
          (if new-start-ls
            (let [end-ls (subs ls (+ semi-count space-count))
                  new-ls (str (apply str (repeat semi-count ";"))
                              (blanks space-count)
                              new-start-ls
                              " "
                              end-ls)]
              [(-> style-vec
                   (insert-str-into-style-vec new-us upper-idx)
                   (insert-str-into-style-vec new-ls lower-idx)) true])
            [style-vec nil]))
      (and
        (do #_(prn "(< upper-space usable-space)" (< upper-space usable-space)
                   "lower-space:" lower-space
                   "ls:" ls)
            (< upper-space usable-space))
        ; If the lower is shorter than the upper, and the lower
        ; is the last line in the comment-group,
        ; and the lower won't entirely fit onto the upper (and
        ; thus allow us to remove a comment line), then don't
        ; bother changing these lines
        (do #_(println "(< lower-space upper-space)" (< lower-space upper-space)
                       "(- usable-space upper-space)" (- usable-space
                                                         upper-space)
                       "(+ semi-count space-count)" (+ semi-count space-count)
                       "(- lower-space (+ semi-count space-count))"
                         (- lower-space (+ semi-count space-count)))
            true)
        (not (and (< lower-space upper-space)
                  (= (count comment-group) 2)
                  (neg? (- (- usable-space upper-space)
                           ; Include space between them
                           (inc (- lower-space (+ semi-count space-count))))))))
        ; Move from lower to upper if possible
        ; We need something from the lower string that is less than
        ; the available space between the end of the upper string and
        ; the end of the usable space.
        ; Don't forget to handle reducing available space by whatever
        ; separators are required between the existing us and the
        ; new-end-us.  If any, as the existing us might be empty!
        (let [available-space (- usable-space upper-space)
              front-matter (+ semi-count space-count)
              ; Get lower and upper string w/out semis or spaced
              ls-text (subs ls front-matter)
              us-text (subs us front-matter)
              ; If new-ls is non-nil, that means that things changed.
              ; However, new-ls might still be empty, indicating that
              ; everything moved to the new-us.!
              [new-us new-ls]
                (move-ls-to-us us-text ls-text available-space)]
          #_(prn "balance-two-comments: new-us:" new-us "new-ls" new-ls)
          ; If new-ls is non-nil, it might still be empty!
          (if new-ls
            (let [full-us (str (apply str (repeat semi-count ";"))
                               (blanks space-count)
                               new-us)]
              (if (empty? new-ls)
                [(-> style-vec
                     (insert-str-into-style-vec full-us upper-idx)
                     (delete-style-vec-element lower-idx)) true]
                (let [full-ls (str (apply str (repeat semi-count ";"))
                                   (blanks space-count)
                                   new-ls)]
                  [(-> style-vec
                       (insert-str-into-style-vec full-us upper-idx)
                       (insert-str-into-style-vec full-ls lower-idx)) true])))
            ; We didn't make any changes, leave things as they are.
            [style-vec nil]))
      :else [style-vec nil])))

(defn balance-two-comments-alt
  "Take the index into the style-vec for two comments, and balance the
  words in them. Returns a new style-vec."
  [start-col-vec style-vec semi-count space-count usable-space upper-idx
   lower-idx comment-group]
  (let [[us ucolor uwhat :as upper-element] (nth style-vec upper-idx)
        [ls lcolor lwhat :as lower-element] (nth style-vec lower-idx)
        ; All of the space measurement up to this point includes the
        ; semis and spaces on the front of every line.
        upper-space (count us)
        lower-space (count ls)]
    #_(println "balance-two-comments:" upper-idx lower-idx)
    (cond (> upper-space usable-space)
            ; move from upper to lower
            (let [[new-us new-start-ls] (split-str-at-space-left us
                                                                 usable-space)]
              (if new-start-ls
                (let [end-ls (subs ls (+ semi-count space-count))
                      new-ls (str (apply str (repeat semi-count ";"))
                                  (blanks space-count)
                                  new-start-ls
                                  " "
                                  end-ls)]
                  (-> style-vec
                      (insert-str-into-style-vec new-us upper-idx)
                      (insert-str-into-style-vec new-ls lower-idx)))
                style-vec))
          (and (do #_(prn "(< upper-space usable-space)"
	                     (< upper-space usable-space)
			     "lower-space:"
			     lower-space
			     "ls:"
			     ls)
	            (< upper-space usable-space))
               ; If the lower is shorter than the upper, and the lower
	       ; is the last line in the comment-group,
               ; and the lower won't entirely fit onto the upper (and
               ; thus allow us to remove a comment line), then don't
               ; bother changing these lines
	       (do 
	         #_(println "(< lower-space upper-space)" 
		           (< lower-space upper-space)
			  "(- usable-space upper-space)"
			   (- usable-space upper-space)
			  "(+ semi-count space-count)"
			   (+ semi-count space-count)
			  "(- lower-space (+ semi-count space-count))"
			   (- lower-space (+ semi-count space-count)))
		  true)
               (not (and (< lower-space upper-space)
	                 (= (count comment-group) 2)
                         (neg? (- (- usable-space upper-space)
                                  ; Include space between them
                                  (inc (- lower-space
                                          (+ semi-count space-count))))))))
            ; move from lower to upper if possible
            ; We need something from the lower string that is less than
            ; the available space between the end of the upper string and
            ; the end of the usable space.  Reduce available-space
	    ; by one to account for separator if we put something in it.
            (let [available-space (dec (- usable-space upper-space))
                  ; Get lower string w/out semis or spaced
                  ls-text (subs ls (+ semi-count space-count))
                  ; If new-ls is non-nil, it might still be empty!
                  [new-end-us new-ls] (split-str-at-space-left ls-text
                                                               available-space)]
              (if new-ls
                ; We have two pieces, so the left goes up, and the right
                ; stays here.
                (let [us-text (subs us (+ semi-count space-count))
		      ; Figure out if we need a period and space
                      [separator new-end-us] (figure-separator us-text 
		                                               new-end-us)
                      new-us (str us separator new-end-us)]
                  (if (empty? new-ls)
                    (-> style-vec
                        (insert-str-into-style-vec new-us upper-idx)
                        (delete-style-vec-element lower-idx))
                    (let [new-ls (str (apply str (repeat semi-count ";"))
                                      (blanks space-count)
                                      new-ls)]
                      (-> style-vec
                          (insert-str-into-style-vec new-us upper-idx)
                          (insert-str-into-style-vec new-ls lower-idx)))))
                style-vec))
          :else style-vec)))

(defn flow-comments-in-group
  "For multple line comment groups, do some 'smart' word wrapping,
  where we will move words back up to a line with less than the
  others, and also move words down when a line is too long.  Returns
  style-vec."
  [{:keys [width],
    {{:keys [border max-variance last-max #_top-level?]} :smart-wrap} :comment,
    :as options} start-col-vec style-vec semi-count space-count comment-group]
  (let [comment-lines
          (style-lines-in-comment-group start-col-vec style-vec comment-group)
        ; They all have the same start-col, by definition
        start-col (nth start-col-vec (first comment-group))
        max-width (second comment-lines)
        length-vec (nth comment-lines 2)
	length-len (count length-vec)
        butlast-length-vec (butlast length-vec) 
        last-len (peek length-vec)
        max-not-last (if (> length-len 1)
	                (apply max butlast-length-vec)
			(first length-vec))
        include-last? (and (> length-len 1) (>= last-len max-not-last))
        cg-variance
          (or (variance (if include-last? length-vec (butlast length-vec))) 
	      0)
        last-force? (and (> length-len 1)
	                 (> last-len max-not-last)
                         (> (- last-len max-not-last) last-max))
        #_#_actual-space (- max-width start-col)
        #_#_border (int (min border (/ actual-space 6)))
        #_#_width (- width border)
        ; Adjust border for how much space we really have
        ; but don't use too much!
        width (adjust-border start-col max-width border width)
        usable-space (- width start-col)
        line-count (count comment-group)]
    (dbg-s options
           #{:smart-wrap :flow-comments}
           "flow-comments-in-group: max-variance:" max-variance
           "cg-variance:" cg-variance
           "include-last?" include-last?
           "last-len:" last-len
           "max-not-last:" max-not-last
           "(- last-len max-not-last):" (- last-len max-not-last)
           "max-width:" max-width
           "usable-space:" usable-space)
    (if (and (< cg-variance max-variance)
             (>= max-width (int (/ usable-space 2)))
             (< max-width usable-space)
             (>= line-count 3)
             (not last-force?))
      ; Don't do anything to this comment-group
      style-vec
      ; Loop through lines in comment group, taking them two at a time
      (loop [cg comment-group
             style-vec style-vec
             already-changed? nil]
        (if (<= (count cg) 1)
          style-vec
          (let [[new-style-vec changed?] (balance-two-comments
                                           start-col-vec
                                           style-vec
                                           semi-count
                                           space-count
                                           usable-space
                                           (first cg)
                                           (second cg)
                                           cg
                                           (if already-changed? 0 20))]
            ; Did we delete the second one?
            (if (= (nth (nth new-style-vec (second cg)) 2) :deleted)
              ; rewrite the comment group to drop it out
              (recur (concat (list (first cg)) (nnext cg))
                     new-style-vec
                     (or already-changed? changed?))
              (recur (next cg)
                     new-style-vec
                     (or already-changed? changed?)))))))))

(defn fzprint-smart-wrap
  "Do smart wrap over the entire style-vec.  Returns a possibly new
  style-vec.  Note that top-level isn't really a thing, since every
  actual top-level element comes in one at a time, so they will all
  have their own comment group, and never get smart wrapped because
  of that.  We have been keeping track of the depth, and we continue
  to do so since there might be some reason we cared in the future,
  but it really makes no actual difference to the output at this
  point.  That is to say, from this routine, you can't do top-level
  smart wrapping, period.  Because you never see two top-level
  comments at the same time."
  [{:keys [width],
    {:keys [smart-wrap?], {:keys [border #_top-level?]} :smart-wrap} :comment,
    :as options} style-vec]
  (def fsw-in style-vec)
  (dbg-s options
         #{:smart-wrap}
         "fzprint-smart-wrap smart-wrap?"
         smart-wrap?
         "border:"
         border
         #_"top-level?"
         #_top-level?
         ((:dzprint options) {:list {:wrap? true, :indent 1}} style-vec))
  (let [start-col (style-loc-vec (or (:indent options) 0) style-vec)
        style-vec (into [] style-vec)]
    (loop [idx 0
           depth 0
           style-vec style-vec]
      (let [[new-depth comment-group]
              (get-next-comment-group options #_top-level? depth idx start-col style-vec)
            next-idx (first comment-group)
            semi-count (second comment-group)
            space-count (nth comment-group 2)
            cg (subvec comment-group 3)]
        (dbg-s options
               #{:smart-wrap :comment-group}
               "fzprint-smart-wrap comment-group:" cg
               "depth:" new-depth
               "next-idx:" next-idx
               "semi-count:" semi-count
               "space-count:" space-count)
        (if (empty? cg)
          style-vec
          (let [new-style-vec (flow-comments-in-group options
                                                      start-col
                                                      style-vec
                                                      semi-count
                                                      space-count
                                                      cg)]
            (if (not (nil? next-idx))
              (recur next-idx new-depth new-style-vec)
              new-style-vec)))))))

;;
;; Inline Comments

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
      (let [out (if (> (count current-seq) 0) (conj out current-seq) out)]
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
                       (throw (#?(:clj Exception.
                                  :cljs js/Error.)
                               (str "find-aligned-inline-comments a:" index))))
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
                       (throw (#?(:clj Exception.
                                  :cljs js/Error.)
                               (str "find-aligned-inline-comments b:" index))))
                     ; new starting column
                     start-column
                     ; distance from the last inline comment is zero
                     0
                     ; if we have more than one current inline comments,
                     ; add them to the out vector
                     (if (> (count current-seq) 0) (conj out current-seq) out)))
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
                     (if (> (count current-seq) 0) (conj out current-seq) out))
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
                     (throw (#?(:clj Exception.
                                :cljs js/Error.)
                             (str "find-consecutive-inline-comments:" index))))
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
                     (if (> (count current-seq) 0) (conj out current-seq) out)
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
  #_(prn "comment-vec-column:" style-vec comment-vec)
  (let [start-column (comment-column comment-vec style-vec)
        spaces-before (if (= (count style-vec) 1)
                        (nth (first style-vec) 3)
                        (loc-vec 0 (nth style-vec (dec inline-comment-index))))]
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
  (if (zero? inline-comment-index)
    style-vec
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
                           (str
                             "change-start-column: comment preceded by neither"
                             " an :indent nor :whitespace!"
                             e))))]
      (assoc style-vec previous-element-index new-previous-element))))

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
            _ (dbg-s options
                     :align
                     "fzprint-align-inline-comments: comment-vec:"
                     comment-vec)
            comment-vec-column (comment-vec-all-column style-vec comment-vec)]
        (reduce align-comment-vec style-vec comment-vec-column)))))

