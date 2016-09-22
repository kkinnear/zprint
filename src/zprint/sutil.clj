(ns zprint.sutil (:require clojure.string))

;;
;; # Sexpression functions, see map at the end
;;

(defn sstring "The string value of this sexpr." [sexpr] (pr-str sexpr)) 

;;
;; Pure clojure hex conversion.
;;

(def hexseq ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "a" "b" "c" "d" "e" "f"])
  
(def hexbyte
  (vec (map #(str (nth hexseq (bit-shift-right (bit-and % 240) 4))
                  (nth hexseq (bit-and % 15)))
         (range 256))))

(defn hexadj [b] (if (< b 0) (+ b 256) b))

(defn hex-byte
  "Turn the low byte of a number into hex"
  [n]
  (nth hexbyte (hexadj (bit-and n 255))))

(defn hex-number
  "Turn a number into hex. The shift-seq encodes the amount of the number
  that should be turned into hex."
  [n shift-seq]
  (apply str (map #(hex-byte (unsigned-bit-shift-right n %)) shift-seq)))

(def int-array-type (type (int-array [0])))
(def byte-array-type (type (byte-array [0])))
(def short-array-type (type (short-array [0])))
(def long-array-type (type (long-array [0])))

(defn array-to-shift-seq
  "Given an array of integers, what is the shift-seq to give
  to hex-number to make them into hex?"
  [a]
  (let [t (type a)]
    (cond (= t byte-array-type) [0]
          (= t short-array-type) [8 0]
          (= t int-array-type) [24 16 8 0]
          (= t long-array-type) [56 48 40 32 24 16 8 0]
          :else nil)))

(defn snumstr
  "Does pr-str, but takes an additional argument for hex conversion. Only
  works for bytes at this time."
  [zloc hex? shift-seq]
  (if (and (integer? zloc) hex?)
    (if (string? hex?)
      (str hex? (hex-number zloc shift-seq))
      (hex-number zloc shift-seq))
    (pr-str zloc)))

(defn sseqnws
  "Return a seq of everything after this. Maps get
  special handling here, as a seq of a map is a bunch
  of map elements, which are pretty much vectors of
  [k v] pairs."
  [sexpr]
  (if (map? sexpr) (apply concat (seq sexpr)) (seq sexpr)))

(defn smap-right
  "Map a function of all of the elements to ther right
  of this."
  [zfn sexpr]
  (if (coll? sexpr) (mapv zfn (next sexpr)) nil))

(defn sfocus-style
  "Take the various inputs and come up with a style.  But we
  don't do focus, so that's easy."
  [style _ sexpr]
  style)

(defn snth
  "Find gthe nth element inside of this sexpr."
  [sexpr n]
  (when (coll? sexpr) (nthnext sexpr n)))

(defn scount
  "How many children does sexpr have?"
  [sexpr]
  (if (coll? sexpr) (count sexpr) 0))

(defn smap
  "Return a vector containing the return of applying a function to
  every element inside of sexpr."
  [zfn sexpr]
  ;(println "smap: sexpr:" sexpr)
  (let [v (if (coll? sexpr) (mapv zfn sexpr) [])]
    ;(println "smap:" v)
    v))

(defn sfn? "Is this an anonymous fn?" [sexpr] (fn? sexpr))

(defn sfocus
  "Is this the focus.  It is possible that this could
  be implemented with path's and such, but that is not a goal
  at this point."
  [sexpr fsexpr]
  nil)

(defn sfind-root-and-path
  "This is inherently impossible, as we don't have
  an up capability.  But we could build one as we
  go down which would give us an up capability (or
  at least we would always know where we were).  An
  interesting idea, but for now, return essentially
  nothing."
  [sexpr]
  ["root" []])

(defn swhitespace?
  "Return true if this is whitespace.  But as we
  don't have any whitespace in regular s-expressions,
  we will always return false."
  [sexpr]
  nil)

(defn sfirst
  "Do the first thing, with the right amount of arguments."
  [sexpr]
  (first sexpr))

(defn ssecond
  "Do the second thing, with the right amount of arguments."
  [sexpr]
  (second sexpr))

(defn slist?
  "A list? that includes cons."
  [sexpr]
  (or (list? sexpr) (seq? sexpr)))

(defn slast
  "last which can take two arguments."
  [sexpr]
  (if (coll? sexpr) (last sexpr) sexpr))

(def byte-array-type (type (byte-array [(byte 0)])))

(defn sbyte-array?
  "Is this a byte array?"
  [sexpr]
  (= (type sexpr) byte-array-type))

(defn sarray? "Is this an array?" [x] (when x (.isArray (type x))))

(defn satom? "Is this an atom?" [x] (when x (= clojure.lang.Atom (class x))))

(defn sderef "Deref this thing." [x] (deref x))

(defn sexpandarray "Blow an array out into a vector." [a] (mapv identity a))

(defn sns? "Is this a namespace?" [x] (if (symbol? x) (find-ns x)))

(defn sobj-to-vec
  "Turn something whose pr-str starts with #object into a vector.
  obj is the thing that prints as #object, and val is its value.
  Two forms, one with and one w/out val.  val could be nil, or
  anything, so there isn't a particularly good sentinal here."
  ([obj val]
   (let [obj-term (-> (pr-str obj)
                    (clojure.string/replace #"^\#object\[" "")
                    (clojure.string/split #" " 3))]
     [(read-string (first obj-term)) (second obj-term) val]))
  ([obj]
   (let [obj-term (-> (pr-str obj)
                    (clojure.string/replace #"^\#object\[" "")
                    (clojure.string/replace #"\]$" "")
                    (clojure.string/split #" " 3))]
     [(read-string (first obj-term)) (second obj-term)
      (read-string (nth obj-term 2))])))

(defn spromise? "Is this a promise?" [x] (re-find #"promise" (pr-str (type x))))

(defn sagent?
  "Is this an agent?"
  [x]
  (re-find #"clojure.lang.Agent" (pr-str (type x))))

; This is faster, but only works in 1.8:
;  (clojure.string/includes? (pr-str (type x)) "promise"))

(defn sconstant?
  "Is this a constant?"
  [x]
  (or (keyword? x) (string? x) (number? x)))

;;
;; # Define function map from keyword to actual function for r operation
;;

(def sf
  {:zstring sstring,
   :znumstr snumstr,
   :zbyte-array? sbyte-array?,
   :zcomment? (constantly false),
   :zsexpr identity,
   :zseqnws sseqnws,
   :zmap-right smap-right,
   :zfocus-style sfocus-style,
   :zfirst sfirst,
   :zsecond ssecond,
   :znth snth,
   :zcount scount,
   :zmap smap,
   ;   :zfn? sfn?
   :zanonfn? (constantly false),
   ; this only works because lists, anon-fn's, etc. are checked before this
   ; is used.
   :zfn-obj? fn?,
   :zfocus sfocus,
   :zfind-path sfind-root-and-path,
   :zwhitespace? swhitespace?,
   :zlist? slist?,
   :zvector? vector?,
   :zmap? map?,
   :zset? set?,
   :zcoll? coll?,
   :zmeta? (constantly false),
   :zuneval? (constantly false),
   :ztag (constantly nil),
   :zparseuneval (constantly nil),
   :zlast slast,
   :zarray? sarray?,
   :zatom? satom?,
   :zderef sderef,
   :zrecord? record?,
   :zns? (constantly false),
   :zobj-to-vec sobj-to-vec,
   :zexpandarray sexpandarray,
   :znewline? (constantly false),
   :zwhitespaceorcomment? (constantly false),
   :zmap-all map,
   :zfuture? future?,
   :zpromise? spromise?,
   :zkeyword? keyword?,
   :zdelay? delay?,
   :zconstant? sconstant?,
   :zagent? sagent?,
   :zreader-macro? (constantly false),
   :zarray-to-shift-seq array-to-shift-seq,
   :zdotdotdot (constantly '...),
   :zsymbol? symbol?})