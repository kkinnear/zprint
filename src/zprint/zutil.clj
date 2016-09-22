(ns
  zprint.zutil
  (:require
   clojure.string
   [rewrite-clj.parser :as p :only [parse-string]]
   [rewrite-clj.node :as n]
   [rewrite-clj.zip :as z]))

;;
;; Zipper oriented style printers
;;


;;
;; Check to see if we are at the focus by checking the
;; path.
;;

(declare find-root-and-path)

(defn zfocus
  "Is the zipper zloc equivalent to the path floc.  In this
  case, floc isn't a zipper, but was turned into a path early on."
  [zloc floc]
  (let [[_ zpath] (find-root-and-path zloc)] (= zpath floc)))

(defn zfocus-style
  "Take the various inputs and come up with a style."
  [style zloc floc]
  (let [style (if (= style :f) style (if (zfocus zloc floc) :f :b))] style))

(defn z-coll? "Is the zloc a collection?" [zloc] (z/seq? zloc))

(defn zuneval? "Is this a #_(...)" [zloc] (= (z/tag zloc) :uneval))

(defn zmeta? "Is this a ^{...}" [zloc] (= (z/tag zloc) :meta))

(defn zquote?
  "Is this a '(...) or '[ ... ] or some other quote?"
  [zloc]
  (= (z/tag zloc) :quote))

(defn zreader-macro? "Is this a @..." [zloc] (= (z/tag zloc) :reader-macro))

(defn ztag "Return the tag for this zloc" [zloc] (z/tag zloc))

(defn zparseuneval
  "Turn an uneval zloc with #_ starting it into a zipper."
  [zloc]
  (z/edn* (p/parse-string
            (clojure.string/triml
              (clojure.string/replace-first (z/string zloc) #"#_" "")))))

(defn zcreateuneval
  "Turn a zloc into an #_ uneval zipper."
  [zloc]
  (z/edn* (p/parse-string (clojure.string/triml (str "#_" (z/string zloc))))))

(defn zcomment?
  "Returns true if this is a comment."
  [zloc]
  (when zloc (= (z/tag zloc) :comment)))

(defn znewline?
  "Returns true if this is a newline."
  [zloc]
  (when zloc (= (z/tag zloc) :newline)))

(defn znumstr
  "Does z/string, but takes an additional argument for hex conversion.
  Hex conversion is not implemented for zippers, though, because at present
  it is only used for byte-arrays, which don't really show up here."
  [zloc _ _]
  (z/string zloc))

(defn zfirst
  "Find the first non-whitespace zloc inside of this zloc, or
  the first whitespace zloc that is the focus."
  [zloc]
  (let [nloc (z/down* zloc)] (if nloc (z/skip z/right* z/whitespace? nloc))))

(defn zsecond
  "Find the second non-whitespace zloc inside of this zloc."
  [zloc]
  (if-let [first-loc (zfirst zloc)]
    (if-let [nloc (z/right* first-loc)] (z/skip z/right* z/whitespace? nloc))))

(defn zrightnws
  "Find the next non-whitespace zloc inside of this zloc."
  [zloc]
  (if zloc
    (if-let [nloc (z/right* zloc)] (z/skip z/right* z/whitespace? nloc))))

(defn zrightmost
  "Find the rightmost non-whitespace zloc at this level"
  [zloc]
  (loop [nloc (zrightnws zloc)
         ploc zloc]
    (if-not nloc ploc (recur (zrightnws nloc) nloc))))

(defn zleftnws
  "Find the next non-whitespace zloc inside of this zloc."
  [zloc]
  (if zloc (if-let [nloc (z/left* zloc)] (z/skip z/left* z/whitespace? nloc))))

(defn zleftmost
  "Find the leftmost non-whitespace zloc at this level"
  [zloc]
  (loop [nloc (zleftnws zloc)
         ploc zloc]
    (if-not nloc ploc (recur (zleftnws nloc) nloc))))

(defn znextnws
  "Find the next non-whitespace zloc."
  [zloc]
  (if (z/end? zloc)
    zloc
    (if-let [nloc (z/next* zloc)] (z/skip z/next* z/whitespace? nloc))))

(defn zprevnws
  "Find the next non-whitespace zloc."
  [zloc]
  (if-let [ploc (z/prev* zloc)] (z/skip z/prev* z/whitespace? ploc)))

(defn znth
  "Find the nth non-whitespace zloc inside of this zloc."
  [zloc n]
  (loop [nloc (z/down* zloc)
         i n]
    (if (or (nil? nloc) (= i 0)) nloc (recur (zrightnws nloc) (dec i)))))

(defn zmap
  "Return a vector containing the return of applying a function to 
  every non-whitespace zloc inside of zloc."
  [zfn zloc]
  (loop [nloc (z/down* zloc)
         out []]
    (if-not nloc
      out
      (recur (z/right* nloc)
             (if-let [result (when (not (z/whitespace? nloc)) (zfn nloc))]
               (conj out result)
               out)))))

(defn zmap-all
  "Return a vector containing the return of applying a function to 
  every zloc inside of zloc."
  [zfn zloc]
  (loop [nloc (z/down* zloc)
         out []]
    (if-not nloc out (recur (z/right* nloc) (conj out (zfn nloc))))))

(defn zmap-right
  "Apply a function to every non-whitespace zloc to right of zloc."
  [zfn zloc]
  (loop [nloc (z/right* zloc)
         out []]
    (if-not nloc
      out
      (recur (z/right* nloc)
             (if (z/whitespace? nloc) out (conj out (zfn nloc)))))))

(defn zseqnws
  "Return a seq of all of the non-whitespace children of zloc."
  [zloc]
  (zmap identity zloc))

(defn zcount
  "How many non-whitespace children does zloc have?"
  [zloc]
  (count (zseqnws zloc)))

(defn find-root-and-path
  "Create a vector with the root as well as another vector
  which contains the number of right moves after each down
  down to find a particular zloc.  The right moves include
  both whitespace and comments."
  [zloc]
  (if zloc
    (loop [nloc zloc
           left 0
           out ()]
      (if-not (z/left* nloc)
        (if-not (z/up* nloc) [nloc out] (recur (z/up* nloc) 0 (cons left out)))
        (recur (z/left* nloc) (inc left) out)))))

(defn find-root-and-path-nw
  "Create a vector with the root as well as another vector
  which contains the number of right moves after each down
  down to find a particular zloc.  The right moves are
  non-whitespace, but include comments."
  [zloc]
  (if zloc
    (loop [nloc zloc
           left 0
           out ()]
      (if-not (z/left* nloc)
        (if-not (z/up* nloc) [nloc out] (recur (z/up* nloc) 0 (cons left out)))
        (recur (z/left* nloc) (if (z/whitespace? nloc) left (inc left)) out)))))

(defn find-root
  "Find the root from a zloc by doing lots of ups."
  [zloc]
  (loop [nloc zloc] (if-not (z/up nloc) nloc (recur (z/up nloc)))))

(defn move-down-and-right
  "Move one down and then right a certain number of steps."
  [zloc right-count]
  (loop [nloc (z/down* zloc)
         remaining-right right-count]
    (if (zero? remaining-right)
      nloc
      (recur (z/right* nloc) (dec remaining-right)))))

(defn follow-path
  "Follow the path vector from the root and return the zloc
  at this location."
  [path-vec zloc]
  (reduce move-down-and-right zloc path-vec))

(defn zanonfn? "Is this an anonymous fn?" [zloc] (= (z/tag zloc) :fn))

(defn zlast
  "Return the last non-whitespace (but possibly comment) element inside
  of this zloc."
  [zloc]
  (let [nloc (z/down* zloc)] (when nloc (zrightmost nloc))))

(defn zsexpr?
  "Returns true if this can be converted to an sexpr. Works around a bug
  where n/printable-only? returns false for n/tag :fn, but z/sexpr fails
  on something with n/tag :fn"
  [zloc]
  (and (not= :fn (z/tag zloc)) (not (n/printable-only? (z/node zloc)))))

(defn zkeyword?
  "Returns true if this is a keyword."
  [zloc]
  (and (zsexpr? zloc) (keyword? (z/sexpr zloc))))

(defn zsymbol?
  "Returns true if this is a symbol."
  [zloc]
  (and zloc (zsexpr? zloc) (symbol? (z/sexpr zloc))))

(defn zdotdotdot
  "Return a zloc that will turn into a string of three dots."
  []
  (z/edn* (p/parse-string "...")))

(defn zconstant?
  "Returns true if this is a keyword, string, or number, in other words,
  a constant."
  [zloc]
  (when (zsexpr? zloc)
    (let [sexpr (z/sexpr zloc)]
      (or (keyword? sexpr) (string? sexpr) (number? sexpr)))))

;;
;; # Integrate specs with doc-string
;;
;; Find find-docstring could be a lot smarter, and perhaps
;; find the docstring in the meta data (so that, defn might
;; work, for instance).

(defn find-docstring
  "Find a docstring in a zipper of a function."
  [zloc]
  (let [fn-name (z/string (z/down zloc))]
    (cond (or (= fn-name "defn") (= fn-name "defmacro"))
            (let [docloc (z/right (z/right (z/down zloc)))]
              (when (string? (z/sexpr docloc)) docloc))
          :else nil)))
                
(defn add-spec-to-docstring
  "Given a zipper of a function definition, add the spec info
  to the docstring."
  [zloc spec-str]
  (println "spec-str:" spec-str)
  (if-let [doc-zloc (find-docstring zloc)]
    (let [new-doc-zloc
            (z/replace*
              doc-zloc
              (z/node (z/edn*
                        (p/parse-string
                          (str "\"" (str (z/sexpr doc-zloc)) spec-str "\"")))))]
      (z/edn* (z/root new-doc-zloc)))
    zloc))

;;
;; # Define function map from keyword to actual function for zipper operation
;;

(def zf
  {:zstring z/string,
   :znumstr znumstr,
   :zbyte-array? (constantly false),
   :zcomment? zcomment?,
   :zsexpr z/sexpr,
   :zseqnws zseqnws,
   :zmap-right zmap-right,
   :zfocus-style zfocus-style,
   :zfirst zfirst,
   :zsecond zsecond,
   :znth znth,
   :zcount zcount,
   :zmap zmap,
   :zanonfn? zanonfn?,
   :zfn-obj? (constantly false),
   :zfocus zfocus,
   :zfind-path find-root-and-path,
   :zwhitespace? z/whitespace?,
   :zlist? z/list?,
   :zvector? z/vector?,
   :zmap? z/map?,
   :zset? z/set?,
   :zcoll? z-coll?,
   :zuneval? zuneval?,
   :zmeta? zmeta?,
   :ztag ztag,
   :zparseuneval zparseuneval,
   :zlast zlast,
   :zarray? (constantly false),
   :zatom? (constantly false),
   :zderef (constantly false),
   :zrecord? (constantly false),
   :zns? (constantly false),
   :zobj-to-vec (constantly nil),
   :zexpandarray (constantly nil),
   :znewline? znewline?,
   :zwhitespaceorcomment? z/whitespace-or-comment?,
   :zmap-all zmap-all,
   :zpromise? (constantly false),
   :zfuture? (constantly false),
   :zdelay? (constantly false),
   :zkeyword? zkeyword?,
   :zconstant? zconstant?,
   :zagent? (constantly false),
   :zreader-macro? zreader-macro?,
   :zarray-to-shift-seq (constantly nil),
   :zdotdotdot zdotdotdot,
   :zsymbol? zsymbol?})