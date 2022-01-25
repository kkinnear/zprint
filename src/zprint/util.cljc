(ns ^:no-doc zprint.util
  (:require [clojure.string :as s]))

(defn local-abs
  "Return the absolute value of a number."
  [n]
  (if (neg? n) (- n) n))

(defn size
  "Return the size of an sexpr, essentially the number of characters
  in its string representation.  If the sexpr is already a string,
  return the number of characters in the string plus 2 to account
  for the double quotes, unless no-string-adj? is non-nil.  In that
  case, simply return the character count of the string.  If sexpr
  is nil, return nil."
  ([sexpr no-string-adj?]
   (when sexpr
     #_(prn "size:" sexpr "string?" (string? sexpr))
     (let [s (str sexpr)
           s (cond (clojure.string/starts-with? s ":zprint.core/")
                     (clojure.string/replace s ":zprint.core/" "::")
                   (clojure.string/starts-with? s ":clojure.core/")
                     (clojure.string/replace s ":clojure.core/" "::")
                   :else s)
           s-count (count s)]
       (if (and (not no-string-adj?) (string? sexpr)) (+ s-count 2) s-count))))
  ([sexpr] (size sexpr nil)))

(defn median
  "Find the median of a series of numbers."
  [coll]
  (let [sorted-coll (sort coll)
        len (count coll)
        middle (/ len 2)]
    (if (odd? len)
      (nth sorted-coll middle)
      (let [lower (dec middle)
            lower-middle (nth sorted-coll lower)
            upper-middle (nth sorted-coll middle)]
        (/ (+ lower-middle upper-middle) 2)))))

(defn mean
  "Find the mean of a series of numbers."
  [coll]
  (when (not (empty? coll)) (/ (apply + coll) (count coll))))

(defn percent-gt-n
  "Return the percentage of numbers greater than n."
  [n coll]
  (when (not (empty? coll))
    (let [count-gt (reduce (fn [cnt m] (if (> m n) (inc cnt) cnt)) 0 coll)
          percentage (int (* (/ count-gt (count coll)) 100))]
      percentage)))

(defn variance
  "Return the variance of a sequence of numbers. Ignore nil values.
  Return the variance or nil if there are no numbers."
  [coll]
  (let [coll (remove nil? coll)
        #_(println "coll:" coll "len:" (count coll))
        len (count coll)]
    (when (not (zero? len))
      (let [mean (/ (apply + coll) len)
            dev-from-mean (mapv (partial - mean) coll)
            sq-dev-from-mean (mapv #(* % %) dev-from-mean)
            variance (int (/ (apply + sq-dev-from-mean) len))]
        #_(println "variance:" variance)
        variance))))

(defn find-max
  "Given a sequence of numbers, return the indices of all of the
  numbers that are equal to the maximum number.  Returns: 
  [max-number [indicies-of-max-number] length-of-sequence]"
  [coll]
  (let [indicies (reduce (fn [[max-so-far indicies index] n]
                           (cond (nil? n) [max-so-far indicies (inc index)]
                                 (> n max-so-far) [n [index] (inc index)]
                                 (= n max-so-far) [max-so-far
                                                   (conj indicies index)
                                                   (inc index)]
                                 :else [max-so-far indicies (inc index)]))
                   [0 [] 0]
                   coll)]
    indicies))

(defn remove-indicies
  "Given a vector, set specific indicies to nil."
  [indicies coll]
  (if indicies (apply assoc coll (interleave indicies (repeat nil))) coll))

(defn remove-max-not-half
  "Given a vector of numbers, remove every instance of the maximum number 
  from the vector and replace it with nil, unless it would remove more 
  than half of the numbers in the vector, in which case return the vector
  unchanged. Returns: [indicies-removed vector-with-max-removed]"
  [coll-vec]
  (let [[max-number indicies length] (find-max coll-vec)]
    (if (> (count indicies) (/ length 2))
      [nil coll-vec]
      [indicies (remove-indicies indicies coll-vec)])))

(defn remove-indicies-from-columns
  "Given a vector of indicies and a vector of vectors (columns), remove 
  the specified indicies from the columns by replacing them with nil, starting
  at the vector specified by the index."
  [index indicies columns]
  (if (< index (count columns))
    (let [[beginning end] (split-at index columns)
          beginning (into [] beginning)
          end (into [] end)
          new-end (mapv (partial remove-indicies indicies) end)]
      (into beginning new-end))
    columns))

(defn count-non-nil
  "Count the non-nil items in a sequence."
  [coll]
  (reduce (fn [cnt x] (if (nil? x) cnt (inc cnt))) 0 coll))

(defn column-width-variance
  "Given a vector of vectors, where each vector represents the sizes
  in a column, find the variance of the column, and if it is too high
  remove the largest and then second largest values to see if we can
  get it low enough for alignment.  If yes, return the alignment and the
  new vector of vectors (where the rows that were not considered for the
  successful variance calculation have been removed from all (inc index)
  columns).  If no, return nil and the unchanged vector of vectors. 
  Returns: [max-width-or-nil columns]"
  [max-variance columns index]
  (if (>= index (count columns))
    [nil columns]
    (let [column (nth columns index)
          #_(println "column:" column "index:" index)
          beginning-variance (variance column)
          row-count (count-non-nil column)]
      #_(println "beginning-variance:" beginning-variance)
      (cond
        (nil? beginning-variance) [nil columns]
        (> max-variance beginning-variance) [(first (find-max column)) columns]
        ; Unless we have at least 3 rows, we aren't removing anything to
        ; try and get the variance to work!
        (> row-count 2)
          (let [[first-indicies first-column-wo-max] (remove-max-not-half
                                                       column)
                #_(println "column:" column)
                #_(println "first-column-wo-max:" first-column-wo-max)
                first-variance (variance first-column-wo-max)]
            (cond (nil? first-variance) [nil columns]
                  (> max-variance first-variance)
                    [(first (find-max first-column-wo-max))
                     (remove-indicies-from-columns (inc index)
                                                   first-indicies
                                                   columns)]
                  :else
                    (let [[second-indicies second-column-wo-max]
                            (remove-max-not-half first-column-wo-max)
                          second-variance (variance second-column-wo-max)]
                      (cond (nil? second-variance) [nil columns]
                            ; Have we removed half of the rows
                            ; between the first and second rounds?
                            (>= (+ (count first-indicies)
                                   (count second-indicies))
                                (/ row-count 2))
                              [nil columns]
                            (> max-variance second-variance)
                              [(first (find-max second-column-wo-max))
                               (remove-indicies-from-columns
                                 (inc index)
                                 (into first-indicies second-indicies)
                                 columns)]
                            :else [nil columns]))))
        :else [nil columns]))))

(defn size-and-extend
  "Given a seq and a length, return a vector which contains the
  size of every element in the seq and is the length specified.  If
  the element is already a string, the size is the number of
  characters plus 2 for the surrounding double quotes, unless
  no-string-adj? is non-nil, in which case just the character count
  of an actual string is used.  If the length is less than the
  length of the input seq, then skip the remaining elements.  If
  the length is greater than the length of the input seq, fill out
  the missing elements with nils, and ensure that the last element
  is replaced by a nil (to avoid influencing the spacing of a column
  that it doesn't have)."
  [length no-string-adj? coll]
  (let [last-good-col (dec (count coll))]
    (loop [coll coll
           index 0
           out []]
      (if (>= index length)
        out
        (recur (next coll)
               (inc index)
               (conj out
                     (if (>= index last-good-col)
                       nil
                       (size (first coll) no-string-adj?))))))))

(defn size-and-extend-butlast
  "Given a sequence of seqs, produce a new sequence of seqs where
  each element in the seq is replaced by the size of that element.
  If the element is already a string, the size is the number of
  characters plus 2 for the surrounding double quotes, unless
  no-string-adj? is non-nil, in which case just the character count
  of an actual string is used.  Do this for all of the elements in
  every seq but the last.  In addition, for every seq that is shorter
  than the longest one, fill out the missing elements with nils."
  ([seq-of-seqs max-len no-string-adj?]
   #_(println "size-and-extend-butlast: seq-of-seqs" seq-of-seqs)
   #_(println "no-string-adj?" no-string-adj?)
   (let [len (dec (apply max (map count seq-of-seqs)))
         len (if max-len (min max-len len) len)
         seq-of-sizes (mapv (partial size-and-extend len no-string-adj?)
                        seq-of-seqs)]
     seq-of-sizes))
  ([seq-of-seqs] (size-and-extend-butlast seq-of-seqs nil nil))
  ([seq-of-seqs max-len] (size-and-extend-butlast seq-of-seqs max-len nil)))

(defn create-columns
  "Given a seq of seqs, create a vector of vectors where every
  internal vector contains a series of integers representing the
  width of the element in that column across all of the seqs.  The
  width for the elements is their string length. Note that the
  length of actual strings is increased by two to account for the
  double quotes unless no-string-adj? is non-nil, in which case
  these routines assume that was already handled.  The various input
  seqs do not have to be the same length, and there will be as many
  columns as one less than the count of elements in the longest seq
  (or number-of-columns if it is specified and less than the count
  of the elements in the longest seq).  For seqs which do not extend
  to the maximum length, their positions in the column vectors will
  be filled with nil."
  ([seq-of-seqs number-of-columns no-string-adj?]
   (let [seq-of-sizes (size-and-extend-butlast seq-of-seqs
                                               number-of-columns
                                               no-string-adj?)
         transpose (apply mapv vector seq-of-sizes)]
     transpose))
  ([seq-of-seqs] (create-columns seq-of-seqs nil nil))
  ([seq-of-seqs number-of-columns]
   (create-columns seq-of-seqs number-of-columns nil)))

(defn column-alignment
  "Given a seq-of-seqs which contain elements to justify, return a
  vector with the size of the maximum element in each column that
  should be used to justify the next column.  Note that the length
  of actual strings is increased by two to account for the double
  quotes unless no-string-adj? is non-nil, in which case these
  routines assume that was already handled.  If number-of-columns
  is given, only justify that many columns, else justify all but
  the last. Note that if the max-variance is exceeded after having
  skipped the largest and second largest in a column, then the return
  is nil, signifying that justification is not possible."
  ([max-variance seq-of-seqs number-of-columns no-string-adj?]
   (let [columns (create-columns seq-of-seqs number-of-columns no-string-adj?)
         #_(println "column count:" (count columns))
         max-width-vec
           (second
             (reduce
               (fn [[columns max-width-vec] index]
                 (let [[max-width new-columns]
                         (column-width-variance max-variance columns index)]
                   #_(println "max-width:" max-width)
                   (if max-width
                     [new-columns (conj max-width-vec max-width)]
                     ; If we fail, then fail completely, don't return a
                     ; short max-width-vec!  Issue #212.
                     (reduced [columns nil]))))
               [columns []]
               (range (count columns))))]
     max-width-vec))
  ([max-variance seq-of-seqs]
   (column-alignment max-variance seq-of-seqs nil nil))
  ([max-variance seq-of-seqs number-of-columns]
   (column-alignment max-variance seq-of-seqs number-of-columns nil)))

(defn cumulative-alignment
  "Given a vector of max-widths from column-alignment, produce a vector
  of the cumulative alignment positions for the second through nth columns."
  [max-width-vec]
  (second
    (reduce (fn [[current-width cumulative-widths] column-max-width]
              (let [this-alignment (+ current-width (inc column-max-width))]
                [this-alignment (conj cumulative-widths this-alignment)]))
      [0 []]
      max-width-vec)))

