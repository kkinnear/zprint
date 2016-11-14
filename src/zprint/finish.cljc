(ns zprint.finish
  (:require [clojure.string :as s]
            [zprint.ansi :refer [color-str]]))

;;
;; # Process results from fzprint*
;;

(def no-style-map {:f identity, :b identity, :c identity})

(defn within?
  "Is n with the closed range of low to high?"
  [n [low high]]
  (and (>= n low) (<= n high)))

(defn ground-color-to-style
  "Ignore any foreground/background designation, and use the
  focus and the color to figure out a style.  Intimately 
  associated with build-styles.
  You don't have to have a color, but you do need a ground.
  If the ground is :c, it is used, otherwise the ground is
  determined from the focus.  In focus gets :f, otherwise :b.
  If you don't have a color, the style you get
  is the same as the key for the ground you get from the
  focus.  If you don't have a focus, everything is in focus."
  [{:keys [style-map focus]} s color element idx]
  (if color
    ((style-map (if (= element :cursor-element)
                  (do (println "cursor-element:" s) :c)
                  (if (or (not focus) (within? idx focus)) :f :b)))
      color)
    :b))

(defn add-length
  "Given [string :style <start>] turn it into
  [string :style <start> <length>]"
  [[s style start]]
  [s style start (count s)])

(defn gc-vec-to-style-vec
  "Take an index and a [string :color] produce a
  [string :style] with the correct elements (i.e., the
  elements with the correct idx) having a different 
  background for focus output."
  [ctx idx [s keyword-color element]]
  [s (ground-color-to-style ctx s keyword-color element idx)])

(defn trim-vec
  "Take a vector of any length, and trim it to be
  only n elements in length."
  [n v]
  (into [] (take n v)))

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
  is based on the :style-map in the ctx map."
  ([ctx cvec focus-vec]
   (let [;gc-vec (mapv (partial trim-vec 3) cvec)
         gc-vec cvec
         str-style-vec (map-indexed (partial gc-vec-to-style-vec
                                             (assoc ctx :focus focus-vec))
                                    gc-vec)]
     str-style-vec))
  ([ctx cvec] (cvec-to-style-vec ctx cvec nil)))

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