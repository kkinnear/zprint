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

(defn abs
  "Return the absolute value of a number."
  [n]
  (if (neg? n) (- n) n)) 

(defn find-row
  "Given a vector of rows, find the row that contains a line number,
  linenumber, and return the number of that row in the vector.  If
  none exists, return the next row. Note that line numbers are 1
  based, not zero based for this routine and the information in
  row-vec.  Uses a binary search. If the line number is before the
  first information in the row-vec, returns :before-beginning, and
  if it is after the last information in the row-vec, returns
  :beyond-end. Note that the row-vec as returned from edamame
  parse-string-all contains not only maps like {:row 5 :end-row 10}
  but also nils for things that didn't have paired delimiters round
  them (e.g., keywords, strings, etc.).  You must remove those
  before calling find-row.  Note that find-row returns an index
  into row-vec, and it must be the row-vec that has had nils removed
  from it."
  ([row-vec linenumber scan-size]
   (let [size (count row-vec)]
     ; We are zero based, but edamame row numbers appear to be 1 based.
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
  ([row-vec n] (find-row row-vec n 4)))

(defn expand-range-to-top-level
  "Given a sequence of lines, and a range of lines inside of them,
  expand the range such that it covers everything from just beyond
  the previous top level expression before the start to the end of
  the top level expression containing the end of the range.  Returns
  [actual-start actual-end]."
  [filestring lines start end]
  (let [line-count (count lines)
        row-vec (mapv meta
                  (parse-string-all filestring {:all true, :read-cond :preserve
		  :auto-resolve {:current *ns*}}))
        row-vec (into [] (remove nil? row-vec))
        #_(zprint.core/czprint row-vec)
        start-row-idx (find-row row-vec (inc start))
        #_(println "start-row:" start-row-idx)
        actual-start (if (or (= start-row-idx :before-beginning)
                             (= start-row-idx 0))
                       0
                       (:end-row (get row-vec (dec start-row-idx))))
        end-row-idx (find-row row-vec (inc end))
        #_(println "end-row:" end-row-idx)
        actual-end (if (= end-row-idx :beyond-end)
                     (dec line-count)
                     (let [end-row (get row-vec end-row-idx)]
                       (if (< (inc end) (:row end-row))
                         (:row end-row)
                         (:end-row end-row))))]
    [actual-start actual-end]))

;;
;; # Take apart a series of lines based on a range
;;

(defn split-out-range
  "Given lines, sequence of lines, and a start and end of a range,
  split the sequence of lines into three parts: [before-lines range
  after-lines].  If any of these collections would be empty, return
  an empty sequence. End must be equal to or greater than start."
  [lines start end]
  (let [before start
        range (inc (- end start))
        after (- (dec (count lines)) end)]
    #_(println "before:" before "range:" range "after:" after)
    [(take before lines) (take range (drop before lines))
     (take after (drop (+ before range) lines))]))

