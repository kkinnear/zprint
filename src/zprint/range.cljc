(ns ^:no-doc zprint.range
  (:require [clojure.string :as s]
            [edamame.core :refer [parse-string-all]]))

;;
;; # Handle range specification
;;

(defn in-row?
  "If a line number n is in a particular edamame row, return the row map.
  If it is not in the row, return +1 or -1 to indicate which direction
  to look."
  [n row]
  #_(println "n:" n "row:" row)
  (when row
    (if (<= (:row row) n (:end-row row)) row (if (< n (:row row)) -1 +1))))

(defn row-before?
  "Given two rows, is the first before the second?"
  [row-a row-b]
  #_(println "row-before? row-a:" row-a "row-b:" row-b)
  (< (:end-row row-a) (:row row-b)))

(defn between-rows?
  "Given two rows, if the linenumber is between the rows, return true, else
  nil."
  [linenumber row-a row-b]
  #_(println "between-rows? linenumber:" linenumber
             "row-a:" row-a
             "row-b:" row-b)
  (when (and (map? row-a) (map? row-b))
    (if (row-before? row-a row-b)
      (< (:end-row row-a) linenumber (:row row-b))
      (< (:end-row row-b) linenumber (:row row-a)))))

(defn scan-for-row
  "Given a row-vec, and a current index into the row-vec, if the
  linenumber is within that row, return the index to that row.   If
  the linenumber is not in that row, then scan either way for
  max-tries looking for a match for this linenumber.  Return the
  row index if a row is found containing this linenumber. If it is
  between two rows, return the row after.  If we fall off the either
  end of the row-vec, then return :before-beginning or :beyone-end.  
  If we don't find anything after trying for max-tries, :before or
  :after, depending on which way we should try next."
  [row-vec row-vec-index linenumber max-tries]
  (loop [current-index row-vec-index
         previous-index nil
         tries 0]
    (let [row (get row-vec current-index)
          row-or-direction (in-row? linenumber row)]
      #_(println "scan-for-row current-index:" current-index
                 "previous-index:" previous-index
                 "tries:" tries
                 "row:" row
                 "row-or-direction" row-or-direction)
      (cond (map? row-or-direction) current-index   ; in this row
            (nil? row-or-direction)
              (if (pos? current-index) :beyond-end :before-beginning)
            :else
              (if (>= tries max-tries)
                ; tell caller where to look next
                (if (pos? row-or-direction) :after :before)
                (let [next-index (+ current-index row-or-direction)
                      next-row (get row-vec next-index)
                      row-or-direction (in-row? linenumber next-row)]
                  (cond (map? row-or-direction) next-index ; we are in the row,
                                                           ; return its index
                        (nil? row-or-direction)
                          (if (pos? next-index) :beyond-end :before-beginning)
                        :else (if (between-rows? linenumber row next-row)
                                ; We are between rows, return later one
                                (if (row-before? row next-row)
                                  next-index
                                  current-index)
                                ; Keep looking
                                (recur (+ current-index row-or-direction)
                                       current-index
                                       (inc tries))))))))))

(defn abs "Return the absolute value of a number." [n] (if (neg? n) (- n) n))

(defn find-row
  "Given a vector of rows, find the row that contains a line number,
  linenumber, and return the number of that row in the vector.
  row-vec looks like this: 
  [{:row 2, :col 1, :end-row 7, :end-col 21}
   {:row 9, :col 1, :end-row 18, :end-col 6} 
   {:row 20, :col 1, :end-row 29, :end-col 6}]
  If none exists, return the next row. Note that line numbers are 1
  based, not zero based for this routine and the information in
  row-vec, but the index into row-vec that this routine returns is
  zero based.  Uses a binary search. If the line number is before the
  first information in the row-vec, returns :before-beginning, and
  if it is after the last information in the row-vec, returns
  :beyond-end. Note that the row-vec as returned from edamame
  parse-string-all contains not only maps like {:row 5 :end-row 10}
  but also nils for things that didn't have paired delimiters around
  them (e.g., keywords, strings, etc.).  You must remove those
  from row-vec before calling find-row.  Note that find-row returns an 
  index into row-vec, and it must be the row-vec that has had nils 
  removed from it (or this routine would do that for you)."
  ([row-vec linenumber dbg? scan-size]
   (when dbg?
     (println "find-row: linenumber:" linenumber "scan-size:" scan-size))
   (let [size (count row-vec)]
     ; We are 1 based, because edamame row numbers are 1 based.
     (loop [row-vec-index (int (/ size 2))
            previous-index 0
            tries 0]
       #_(println "\n\n================== row-vec-index:" row-vec-index)
       (if (> tries 10)
         :fail
         (let [maybe-index
                 (scan-for-row row-vec row-vec-index linenumber scan-size)]
           ; If it is a number, that is the row-vec-index to return
           ; If it is :before, we ran off the beginning, :after the end
           ; nil means that we didn't find it, but can keep looking
           #_(println "maybe-index:" maybe-index)
           (cond
             (number? maybe-index) maybe-index  ; we found something to return
             ; is it in this row?
             (or (= maybe-index :before-beginning) (= maybe-index :beyond-end))
               maybe-index
             :else ; Has to be :before or :after
               (do #_(println "find-row: maybe-index:" maybe-index
                              "row-vec-index:" row-vec-index
                              "previous-index:" previous-index
                              "abs:" (abs (- row-vec-index previous-index)))
                   (recur ((if (= maybe-index :before) - +)
                            row-vec-index
                            (int (/ (abs (- row-vec-index previous-index)) 2)))
                          row-vec-index
                          (inc tries)))))))))
  ([row-vec n dbg?] (find-row row-vec n dbg? 4)))

(defn next-non-blank-line
  "Given a sequence of lines and a starting line index in that sequence,
  return the index of the first non-blank line including or after that
  starting line index."
  [line-vec index]
  (let [max-idx (dec (count line-vec))]
    (loop [idx index]
      (let [line (nth line-vec idx)]
        ; Return current idx if it is non-blank or the last line
        (if (or (not (empty? (clojure.string/trim line))) (>= idx max-idx))
          idx
          (recur (inc idx)))))))

(defn previous-non-blank-line
  "Given a sequence of lines and a starting line index in that sequence,
  return the index of the first previous non-blank line including or before
  that starting line index.  Returns -1 if no non-blank line found."
  [line-vec index]
  (loop [idx index]
    (let [line (nth line-vec idx)]
      ; Return current idx if it is non-blank
      (cond (not (empty? (clojure.string/trim line))) idx
            ; if the first line is not non-blank, then we didn't find one
            (zero? idx) -1
            ; keep looking for a non-blank line
            :else (recur (dec idx))))))

(defn expand-range-to-top-level
  "Given a string which contains lines and a vector of those lines,
  and a range of lines inside of them, expand the range such that
  it covers everything from the first non-blank line beyond the
  previous top level expression before the start to the end of the
  top level expression containing the end of the range.  Returns
  [actual-start actual-end].  Note that start, end, actual-start
  and actual-end are all zero based line numbers."
  ; But also note that parse-string-all (and thus row-vec) and
  ; find-row all operate with one-based line numbers!!!
  [filestring lines start end dbg?]
  (when dbg? (println "expand-range-to-top-level: start:" start "end:" end))
  (let [line-count (count lines)
        start (if (number? start) start 0)
        end (if (number? end) end line-count)
        ; If end is before start, make them the same
        end (if (< end start) start end)
        ; Get a vector of maps describing all top level expressions using
        ; one based line numbers.  For example:
        ; [{:col 1, :end-col 21, :end-row 7, :row 2}
        ;  {:col 1, :end-col 6, :end-row 20, :row 11}
        ;  {:col 1, :end-col 70, :end-row 26, :row 22}
        ;  {:col 1, :end-col 48, :end-row 29, :row 27}]
        row-vec (try (mapv meta
                       (parse-string-all
                         filestring
                         {:all true,
                          :features #{:clj :cljs},
                          ; Ensure that reader-conditionals have something
                          ; show up with meta data regardless of the
                          ; "features" in the reader-conditional
                          :read-cond (fn [expr] (with-meta [] (meta expr))),
                          :auto-resolve {:current *ns*}}))
                     ; If we can't parse it, we have set row-vec to nil, which
                     ; should cause us to format everything.
                     (catch #?(:clj Exception
                               :cljs :default)
                       e
                       nil))
        row-vec (when row-vec (into [] (remove nil? row-vec)))
        ; If row-vec is nil, then we didn't parse this, so do everything
        ; silently.
        _ (when dbg? (prn row-vec))
        ; Figure out which expression start falls within, after making
        ; it a one-based line number.  -idx are indexes into row-vec,
        ; *not* linenumbers
        start-row-idx (if row-vec (find-row row-vec (inc start) dbg?) :fail)
        _ (when dbg?
            (println "expand-range-to-top-level start-row-idx:"
                     start-row-idx
                     (if (number? start-row-idx)
                       (str "row:" (nth row-vec start-row-idx)
                            "previous row:" (nth row-vec
                                                 (max 0 (dec start-row-idx))))
                       "")))
        actual-start
          ; -1 is a signal to not start at the beginning unless the end is
          ; also -1, in which case it is a signal to put everything in the
          ; before
          (cond (= start-row-idx :fail) -1
                (and (= start-row-idx :before-beginning) (not (neg? start))) 0
                (= start-row-idx :before-beginning) -1
                (= start-row-idx 0) 0
                (= start-row-idx :beyond-end) line-count
                ; normal case -- the line beyond the previous form
                ; where (dec start-row-idx) is presumably the previous form
                :else (:end-row (get row-vec (dec start-row-idx))))
        ; Now, move actual-start to the first non-blank line after or equal to
        ; actual-start.  But not if it is zero or negative, since we don't
        ; want to mess with the range if it encompasses the beginning of
        ; the file.
        actual-start (if (or (< actual-start 1) (>= actual-start line-count))
                       actual-start
                       (next-non-blank-line lines actual-start))
        end-row-idx (if row-vec (find-row row-vec (inc end) dbg?) :fail)
        _ (when dbg?
            (println "expand-range-to-top-level end-row-idx:"
                     end-row-idx
                     (if (number? end-row-idx)
                       (str "row:" (nth row-vec end-row-idx))
                       "")))
        actual-end (cond
                     (or (= end-row-idx :fail) (= end-row-idx :beyond-end))
                       ; We are beyond the end or it didn't parse, say the
                       ; end is beyond the last line.
                       line-count
                     (= end-row-idx :before-beginning)
                       ; Someone is confused here too, say the end is the
                       ; start.
                       :do-nothing
                     :else (let [end-row (get row-vec end-row-idx)]
                             ; end-row-idx is either the row in which end falls
                             ; or the next row if it was between rows
                             ; Note: :row is the start line of a row-map
                             ;
                             ; Does end fall between two top-level expressions?
                             (if (< (inc end) (:row end-row))
                               ; Yes -- are start and end in same gap
                               ; between expressions?
                               (if (= end-row-idx start-row-idx)
                                 ; Yes, do nothing
                                 :do-nothing
                                 ; No, work backward to the first non-blank
                                 ; line prior to the end
                                 (previous-non-blank-line lines end))
                               ; No, end falls inside of an expression, so use
                               ; the end of that expression.  Make it zero
                               ; based.
                               (dec (:end-row end-row)))))
        actual-start (if (= actual-end :do-nothing) -1 actual-start)
        actual-end (if (= actual-end :do-nothing) -1 actual-end)]
    [actual-start actual-end]))

;;
;; # Take apart a series of lines based on a range
;;

(defn split-out-range
  "Given lines, a sequence of lines, and a start and end of a range,
  split the sequence of lines into three parts: [before-lines range
  after-lines].  If any of these collections would be empty, return
  an empty sequence. End must be equal to or greater than start. If
  end is neg?, there will be no range."
  [lines start end]
  (let [start (max start 0)
        before start
        range (if (neg? end) 0 (inc (- end start)))
        after (- (dec (count lines)) end)]
    #_(println "before:" before "range:" range "after:" after)
    [(take before lines) (take range (drop before lines))
     (take after (drop (+ before range) lines))]))

(defn reassemble-range
  "Given before-lines, range, and after-lines where before-lines
  and after-lines are sequences of lines, and range is a string
  which has been formatted, reassemble these three chunks into a
  single string with appropriate newlines joining them together.
  Note that this is *not* an inverse to split-out-range, because
  in that routine range is a seq of lines, and in this routine
  range is a string.  Note also that we only join them with a 
  newline if there are two pieces, otherwise we get extra lines."
  [before-lines range after-lines]
  (let [before? (not (empty? before-lines))
        before-lines (if before? (clojure.string/join "\n" before-lines) "")
        range? (not (empty? range))
        after? (not (empty? after-lines))
        after-lines (if after? (clojure.string/join "\n" after-lines) "")
        before-range (if (and before? range?) "\n" "")
        range-after (if (and range? after?) "\n" "")
        range-after (if (and (not range?) before? after?) "\n" range-after)]
    (str before-lines before-range range range-after after-lines)))

