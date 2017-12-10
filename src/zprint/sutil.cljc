(ns zprint.sutil
  (:require clojure.string
            zprint.zfns
            #?@(:cljs [[cljs.reader :refer [read-string]]])))

;;
;; # Sexpression functions, see map at the end
;;

(defn sstring "The string value of this sexpr." [sexpr] (pr-str sexpr))

;;
;; Pure clojure hex conversion.
;;

#?(:clj
     (do
       (def hexseq
         ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "a" "b" "c" "d" "e" "f"])
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
           (pr-str zloc))))
   :cljs (defn snumstr "Does pr-str." [zloc hex? shift-seq] (pr-str zloc)))

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

(defn snthnext
  "Find the nthnext of this sexpr."
  [sexpr n]
  (when (coll? sexpr) (nthnext sexpr n)))

(defn sfind
  "Find the locations (counting from zero, and only counting non-whitespace
  elements) of the first zthing?.  Return its index if it is found, nil if not."
  [zthing? sexpr]
  (when (coll? sexpr)
    (loop [sloc sexpr
           i 0]
      (when sloc (if (zthing? (first sloc)) i (recur (next sloc) (inc i)))))))

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

(defn sthird
  "Define a third since we need one, and znth isn't really nth."
  [sexpr]
  (nth sexpr 2))

(defn sfourth
  "Define a fourth since we need one, and znth isn't really nth."
  [sexpr]
  (nth sexpr 3))

(defn slist?
  "A list? that includes cons."
  [sexpr]
  (or (list? sexpr) (seq? sexpr)))

(defn slast
  "last which can take two arguments."
  [sexpr]
  (if (coll? sexpr) (last sexpr) sexpr))

(defn sarray?
  "Is this an array?"
  [x]
  (when x
    #?(:clj (.isArray (type x))
       :cljs (array? x))))

(defn satom?
  "Is this an atom?"
  [x]
  (when x
    #?(:clj (= clojure.lang.Atom (class x))
       :cljs nil)))

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

(defn spromise?
  "Is this a promise?"
  [x]
  #?(:clj (re-find #"promise" (pr-str (type x)))
     :cljs nil))

(defn sagent?
  "Is this an agent?"
  [x]
  #?(:clj (re-find #"clojure.lang.Agent" (pr-str (type x)))
     :cljs nil))

; This is faster, but only works in 1.8:
;  (clojure.string/includes? (pr-str (type x)) "promise"))

(defn sconstant?
  "Is this a constant?"
  [x]
  (or (keyword? x) (string? x) (number? x)))

(defn slift-ns
  "Perform a lift-ns on a pair-seq that is returned from
  partition-2-all-nc, which is a seq of pairs of zlocs that may or
  may not have been sorted and which may or may not have had things
  removed from it and may or may not actually be pairs.  Could be
  single things, could be multiple things.  If contains multiple
  things, the first thing is the key, but if it is just a single
  thing, the first thing is *not* a key. So we only need to work
  on the first of each seq which has more than one element in it,
  and possibly replace it. This will only lift out a ns if all keys
  in seqs with more than one element have the same namespace. Returns
  the [namespace pair-seq] or nil."
  [pair-seq]
  (let [strip-ns (fn [named]
                   (if (symbol? named)
                     (symbol nil (name named))
                     (keyword nil (name named))))]
    (loop [ns nil
           pair-seq pair-seq
           out []]
      (let [[k & rest-of-pair :as pair] (first pair-seq)
            #_(println "k:" k "rest-of-pair:" rest-of-pair)
            current-ns (when (and rest-of-pair (or (keyword? k) (symbol? k)))
                         (namespace k))]
        (if-not k
          (when ns [ns out])
          (if current-ns
            (if ns
              (when (= ns current-ns)
                (recur ns
                       (next pair-seq)
                       (conj out (cons (strip-ns k) rest-of-pair))))
              (recur current-ns
                     (next pair-seq)
                     (conj out (cons (strip-ns k) rest-of-pair))))
            (when (= (count pair) 1)
              (recur ns (next pair-seq) (conj out pair)))))))))

(defn sredef-call
  "Redefine all of the traversal functions for s-expressions, then
  call the function of no arguments passed in."
  [body-fn]
  (with-redefs [zprint.zfns/zstring sstring
                zprint.zfns/znumstr snumstr
                zprint.zfns/zcomment? (constantly false)
                zprint.zfns/zsexpr identity
                zprint.zfns/zseqnws sseqnws
                zprint.zfns/zmap-right smap-right
                zprint.zfns/zfocus-style sfocus-style
                zprint.zfns/zfirst sfirst
                zprint.zfns/zfirst-no-comment sfirst
                zprint.zfns/zsecond ssecond
                zprint.zfns/zthird sthird
                zprint.zfns/zfourth sfourth
                zprint.zfns/znthnext snthnext
                zprint.zfns/zcount scount
                zprint.zfns/zmap smap
                zprint.zfns/zmap-w-nl smap
                ;   zprint.zfns/zfn? sfn?
                zprint.zfns/zanonfn? (constantly false)
                ; this only works because lists, anon-fn's, etc. are checked
                ; before this
                ; is used.
                zprint.zfns/zfn-obj? fn?
                zprint.zfns/zfocus sfocus
                zprint.zfns/zfind-path sfind-root-and-path
                zprint.zfns/zwhitespace? swhitespace?
                zprint.zfns/zlist? slist?
                zprint.zfns/zvector? vector?
                zprint.zfns/zmap? map?
                zprint.zfns/zset? set?
                zprint.zfns/zcoll? coll?
                zprint.zfns/zmeta? (constantly false)
                zprint.zfns/zuneval? (constantly false)
                zprint.zfns/ztag (constantly nil)
                zprint.zfns/zparseuneval (constantly nil)
                zprint.zfns/zlast slast
                zprint.zfns/zarray? sarray?
                zprint.zfns/zatom? satom?
                zprint.zfns/zderef sderef
                zprint.zfns/zrecord? record?
                zprint.zfns/zns? (constantly false)
                zprint.zfns/zobj-to-vec sobj-to-vec
                zprint.zfns/zexpandarray sexpandarray
                zprint.zfns/znewline? (constantly false)
                zprint.zfns/zwhitespaceorcomment? (constantly false)
                zprint.zfns/zmap-all map
                zprint.zfns/zfuture? #?(:clj future?
                                        :cljs (constantly false))
                zprint.zfns/zpromise? spromise?
                zprint.zfns/zkeyword? keyword?
                zprint.zfns/zdelay? delay?
                zprint.zfns/zconstant? sconstant?
                zprint.zfns/zagent? sagent?
                zprint.zfns/zreader-macro? (constantly false)
                zprint.zfns/zarray-to-shift-seq #?(:clj array-to-shift-seq
                                                   :cljs nil)
                zprint.zfns/zdotdotdot (constantly '...)
                zprint.zfns/zsymbol? symbol?
                zprint.zfns/znil? nil?
                zprint.zfns/zreader-cond-w-symbol? (constantly false)
                zprint.zfns/zreader-cond-w-coll? (constantly false)
                zprint.zfns/zlift-ns slift-ns
                zprint.zfns/zinlinecomment? (constantly false)
                zprint.zfns/zfind sfind]
    (body-fn)))