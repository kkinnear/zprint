(ns zprint.finish
  (:require [clojure.string :as s]
            [zprint.ansi :refer [color-str]]
            [zprint.focus :refer [type-ssv range-ssv]]))

;;
;; # Process results from fzprint*
;;

#_(def no-style-map {:f identity, :b identity, :c identity})
#_(def no-style-map {:f (partial conj [:reverse]), :b identity, :c identity})
(def no-style-map
  {:f #(if (not= %1 :none) (conj [:reverse] %1) [:reverse]),
   :b identity,
   :c identity})

(defn within?
  "Is n within the closed range of low to high?"
  [n [low high]]
  (and (>= n low) (<= n high)))

(defn within-vec?
  "Is n within any of the the closed range of low to high?"
  [n low-high-vec]
  (some (partial within? n) low-high-vec))

(defn ground-color-to-style
  "Ignore any foreground/background designation, and use the
  focus and the color to figure out a style.  Intimately 
  associated with build-styles.
  You don't have to have a color, but you do need a ground.
  If the ground is :c, it is used, otherwise the ground is
  determined from the focus.  In focus gets :f, otherwise :b.
  If you don't have a color, the style you get
  is the same as the key for the ground you get from the
  focus.  If you don't have a focus, you get the background."
  [{:keys [style-map focus select]} s color element idx]
  (let [output? (if select (within-vec? idx select) true)]
    (when output?
      ((style-map (if (= element :cursor-element)
                    (do (println "cursor-element:" s) :c)
                    #_(if (or (not focus) (within? idx focus)) :f :b)
                    (if
                      ; this is the right solution
                      (and focus (within? idx focus) (not= element :indent))
                      ;(and focus (within? idx focus))
                      ; this is the hack solution
                      ;(not (clojure.string/starts-with? s "\n"))
                      :f
                      :b)))
        (or color :none)))))

(defn add-length
  "Given [string :style <start>] turn it into
  [string :style <start> <length>]"
  [[s style start]]
  [s style start (count s)])

(defn gc-vec-to-style-vec
  "Take an index and a [string :color element] and produce a
  [string :style element] with the correct elements (i.e., the
  elements with the correct idx) having a different 
  background for focus output. The ctx is a map which
  must have a :style-map and may have a :focus.  The
  :focus is a two element vector of start and end elements
  which are in focus."
  [ctx idx [s keyword-color element]]
  (let [style (ground-color-to-style ctx s keyword-color element idx)]
    #_(prn "s:" s "keyword-color:" keyword-color "style:" style)
    (when style [s (when (not= style :none) style) element])))

(defn trim-vec
  "Take a vector of any length, and trim it to be
  only n elements in length."
  [n v]
  (into [] (take n v)))

(defn elide-indent
  "Take an ssv element which is presumably an indent, and do 1/2
  of it.  If the argument is nil, do a newline with no indent."
  [ssv-element]
  (if ssv-element
    (if (= "\n" (clojure.string/replace (first ssv-element) " " ""))
      [(apply str "\n" (repeat (/ (dec (count (first ssv-element))) 1) " "))
       :none]
      ["\n" :none])
    ["\n" :none]))

(defn replace-nil-seq
  "Replace all sequences of nil in the sequence with elide"
  [ctx ssv-in elide]
  (let [last-element (:last-element ctx)
        elide [(first elide) (second last-element) (nth last-element 2)]]
    (loop [ssv ssv-in
           doing-nil? false
           last-elide nil
           out []]
      (if (empty? ssv)
        (if doing-nil?
          (-> out
              (conj
                (gc-vec-to-style-vec ctx 0 (or last-elide (elide-indent nil))))
              (conj (gc-vec-to-style-vec ctx 0 elide))
              (conj (gc-vec-to-style-vec ctx 0 (:last-element ctx))))
          out)
        (let [this-ssv (first ssv)
              this-elide (if (and doing-nil? (not (nil? this-ssv)))
                           (elide-indent this-ssv)
                           nil)]
          (recur (next ssv)
                 (nil? this-ssv)
                 (if this-elide this-elide last-elide)
                 (cond (and doing-nil? (nil? this-ssv)) out
                       (and doing-nil? (not (nil? this-ssv)))
                         (-> out
                             (conj (gc-vec-to-style-vec ctx 0 this-elide))
                             (conj (gc-vec-to-style-vec ctx 0 elide))
                             (conj this-ssv))
                       (nil? this-ssv) out
                       :else (conj out this-ssv))))))))

; presently unused
(defn index-vec
  "Given a cvec, generate an index vector which can be input to map
  and will make map work like map-indexed -- unless there are
  :comment-wrap elements, in which case the :comment-wrap element
  will have the same element idx as the previous :comment element."
  [cvec]
  (loop [remaining-cvec cvec
         idx 0
         out []]
    (if-not remaining-cvec
      out
      (let [[_ _ element-type] (first remaining-cvec)
            new-idx (if (= (nth (first remaining-cvec) 2) :comment-wrap)
                      idx
                      (inc idx))]
        (recur (next remaining-cvec) new-idx (conj out new-idx))))))

(defn cvec-to-style-vec
  "Take a [[string :color <anything>] 
           [string :color <anything>] ...] input.
  The focus is a vector of [start-focus end-focus] which are the 
  inclusive values for the focus.  The end is inclusive because it 
  gets a bit dicey if it was 'beyond', since how much beyond would 
  be interesting given the amount of whitespace in the input.
  Not clear at this point just what the counts in the focus-vec count,
  possibly things with <anything> == :element, possibly just any
  [string color <anything>] vector.
  From this, build of: [[string :style] [string :style] ...], where
  :style might be a color, like :blue or :none, or it might be a 
  java-text-pane style (which would have a color encoded in it).  This
  is based on the :style-map in the ctx map. Note that this :style-map
  doesn't have any relation to the :style-map in the options map."
  ([ctx cvec focus-vec select-vec]
   (let [ctx (assoc ctx :last-element (last cvec))
         str-style-vec-w-nil (map-indexed (partial gc-vec-to-style-vec
                                                   (assoc ctx
                                                     :focus focus-vec
                                                     :select select-vec))
                                          cvec)
         #_(map (partial gc-vec-to-style-vec
                         (assoc ctx
                           :focus focus-vec
                           :select select-vec))
             (index-vec cvec)
             cvec)
         count-w-nil (count str-style-vec-w-nil)
         str-style-vec (remove nil? str-style-vec-w-nil)
         elide-vec (when (:elide ctx) [(:elide ctx) :none])
         str-style-vec (if (= count-w-nil (count str-style-vec))
                         str-style-vec
                         (if elide-vec
                           ; Replace sequences of nil with elide-vec
                           (replace-nil-seq ctx str-style-vec-w-nil elide-vec)
                           str-style-vec))]
     str-style-vec))
  ([ctx cvec] (cvec-to-style-vec ctx cvec nil))
  ([ctx cvec focus-vec] (cvec-to-style-vec ctx cvec focus-vec nil)))

(defn compress-style
  "Take a [[string :style] [string :style] ...] vector and
  build a list of: [[string :style <start> <length>] 
                    [string :style <start> <length>]...]
  from it.  This will compress strings which have the same style."
  ([str-style-vec initial-pos]
   (loop [ss-vec str-style-vec
          current nil
          pos initial-pos
          out []]
     (let [ss (first ss-vec)]
       (if-not ss
         (conj out (add-length current))
         (let [same-style? (= (second current) (second ss))]
           (recur (next ss-vec)
                  (if same-style?
                    [(str (first current) (first ss)) (second current)
                     (nth current 2)]
                    [(first ss) (second ss) pos])
                  (+ pos (count (first ss)))
                  (if (or same-style? (= initial-pos pos))
                    out
                    (conj out (add-length current)))))))))
  ([str-style-vec] (compress-style str-style-vec 0)))

;;
;; # Focus processing
;;
;; This capability, not presently maintained, is why the functions
;; are fzprint, not zprint, in zprint.clj.
;;

(defn replace-focus-w-cursor
  "Take a [[string :color <anything>] 
           [string :color <anything>] ...] as input.
  and a focus-vec and, possibly, a non-empty cursor-vec.  If
  there is a cursor-vec, replace the focus-vec items with a cursor
  vec and return a new focus-vec and gcw-vec as [focus-vec gcw-vec], 
  else just return with no changes"
  [gcw-vec [focus-start focus-end :as focus-vec] cursor-vec]
  (if (empty? cursor-vec)
    [focus-vec gcw-vec]
    (let [[front back] (split-at focus-start gcw-vec)]
      [[focus-start (+ focus-start (dec (count cursor-vec)))]
       (concat front cursor-vec (drop (inc (- focus-end focus-start)) back))])))

(defn color-style
  "Turn a [string :color] into an ansi colored string."
  [[s color]]
  (if (nil? color)
    s
    (if (coll? color) (apply color-str s color) (color-str s color))))

(defn color-comp-vec
  "Use output from compress-style -- but just the [string :style] part,
  which since we used identity as the color map, should be just
  [string :color].  Produce a single string with ansi escape sequences embedded
  in it."
  [comp-vec]
  (apply str (mapv color-style comp-vec)))


;;
;; # Cursor
;;
;; A cursor is a vector [<string> <int>], where the string
;; is displayed and the integer is where in the string the
;; cursor 'highlight' should be displayed
;;
;; Appear to be two ways to do this at present, fzprint-cursor,
;; (which still exists, but may or may not work), and  meta-data
;; on the sexpr which is the cursor.
;;

(defn floor
  "Ensure one number is above a certain value."
  [f n]
  (if (>= n f) n f))

(def ^:dynamic fzprint-cursor ["default-cursor" 0])

(defn cursor-style
  "Take a [<string> cursor-number] pair and produce the style-vec
  that will display it. Allow for existing characters.
  This is a style-vec that map-style can use, i.e.,
  [[string <start> <length>] ...]"
  ([[s cursor] existing-count]
   (prn "cursor-style: s:" s ",cursor:" cursor)
   (if cursor
     (let [s (if (>= cursor (count s)) (str s " ") s)
           ;s (if (empty? s) " " s)
           len (count s)
           cursor (min (floor 0 (dec len)) cursor)]
       (filterv #(not (empty? (first %)))
         [[(subs s 0 cursor) :none :element]
          [(str (get s cursor)) :none :cursor-element]
          [(subs s (inc cursor) len) :none :element]]))
     [[s :none :element]]))
  ([str-cursor] (cursor-style str-cursor 0)))

;;
;; # Treat a cvec like it has lines in it
;;

(defn newline-vec
  "Find out how many newlines are in a string, and where they appear.
  Returns either nil for no newlines, or a vector [<count> #{:b :m :e}]
  for beginning, middle, or end (or all three)."
  [s]
  (let [nl-split (clojure.string/split (str s " ") #"\n")
        nl-num (dec (count nl-split))]
    (when (not (zero? nl-num))
      (let [where (if (empty? (first nl-split)) #{:b} #{})
            where (if (= (last nl-split) " ") (conj where :e) where)
            where (if (> nl-num (count where)) (conj where :m) where)]
        [nl-num where]))))

(defn cvec-lines
  "Return a vector containing vectors each with the cvec elements 
  for the start and end of each line."
  [cvec]
  (loop [cvec-nl (map (comp newline-vec first) cvec)
         idx 0
         start 0
         out []]
    #_(if (zero? idx) (println "cvec-nl:" cvec-nl))
    (if (empty? cvec-nl)
      (conj out [start (dec idx)])
      (let [[n where :as cvec-element] (first cvec-nl)]
        #_(println "idx:" idx
                   "cvec-element:" cvec-element
                   "start:" start
                   "out:" out)
        (cond
          (nil? cvec-element) (recur (next cvec-nl) (inc idx) start out)
          (and (= n 1) (:b where))
            (recur (next cvec-nl) (inc idx) idx (conj out [start (dec idx)]))
          (and (= n 1) (:e where))
            (recur (next cvec-nl) (inc idx) (inc idx) (conj out [start idx]))
          (and (> n 1) (:b where) (:m where))
            (recur
              (next cvec-nl)
              (inc idx)
              idx
              (apply conj (conj out [start (dec idx)]) (repeat n [idx idx])))
          (:m where) (recur (next cvec-nl)
                            (inc idx)
                            start
                            (apply conj out (repeat n [start idx]))))))))

(defn find-line
  "Given a cvec index, return the line that it is in."
  [lines idx]
  (reduce #(if (within? idx %2) (reduced %1) (inc %1)) 0 lines))

(defn surround-focus
  "Given a cvec and a focus-vec, and the number of line before and after
  the focus, output a vector of vectors of cvec indicies that cover the 
  desired lines. [[start end] [start end] ...]"
  [lines-to-cvec [focus-begin focus-end] [before after]]
  (let [line-count (count lines-to-cvec)
        focus-begin-line (find-line lines-to-cvec focus-begin)
        focus-end-line (find-line lines-to-cvec focus-end)
        #_(println "focus-begin-line:" focus-begin-line
                   "focus-end-line:" focus-end-line)
        before-line (- focus-begin-line before)
        before-line (if (pos? before-line) before-line 0)
        after-line (+ focus-end-line after)
        after-line (if (>= after-line line-count) (dec line-count) after-line)
        surround-vec [(first (nth lines-to-cvec before-line))
                      (second (nth lines-to-cvec after-line))]]
    surround-vec))

(defn find-range
  "If given a single integer, return the range from lines.  If given
  a range of lines, return the beginning of the first line and the end
  of the last line."
  [lines line-selector]
  (cond (number? line-selector) (nth lines line-selector)
        (vector? line-selector) [(first (nth lines (first line-selector)))
                                 (second (nth lines (second line-selector)))]
        :else (throw (#?(:clj Exception.
                         :cljs js/Error.)
                      (str "Line selector '" line-selector
                           "' must be a number or a vector!" line-selector)))))

(defn select-lines
  "line-vec is a vector of individual lines, or two-vecs of
  line ranges: [1 2 [3-5] 8 9]. Returns a vector of cvec element
  ranges [[0 20] [45-70] ...].  lines is the return from cvec-lines,
  which maps lines onto cvec ranges."
  [lines-to-cvec line-vec]
  (map (partial find-range lines-to-cvec) line-vec))

(defn handle-lines
  "Take the current cvec and any focus-vec and the options map,
  and figure out a set of cvecs to use.  Don't generate lines
  array unless we need to."
  [{{:keys [focus lines paths]} :output, :as options} cvec focus-vec]
  (when (or lines paths (:surround focus))
    (let [lines-to-cvec (cvec-lines cvec)
          surround (:surround focus)
          #_(println "lines:" lines "surround:" surround)
          cvec-ranges (if lines (select-lines lines-to-cvec lines) [])
          #_(println "cvec-ranges:" cvec-ranges)
          cvec-ranges (if surround
                        (conj cvec-ranges
                              (surround-focus lines-to-cvec focus-vec surround))
                        cvec-ranges)
          #_(println "cvec-ranges:" cvec-ranges)
          ; Turn the paths into cvec ranges
          path-vecs (when paths (map (partial range-ssv cvec) paths))
          #_(println "path-vecs:" path-vecs)
          ; Turn cvec ranges for the bare expressions into complete lines
          path-vecs (map #(surround-focus lines-to-cvec % [0 0]) path-vecs)
          #_(println "path-vecs:" path-vecs)
          cvec-ranges (if path-vecs (concat cvec-ranges path-vecs) cvec-ranges)
          #_(def cvr cvec-ranges)]
      (if (empty? cvec-ranges) nil cvec-ranges))))