(ns zprint.core
  ;#?@(:cljs [[:require-macros [zprint.macros :refer [dbg dbg-pr dbg-form
  ;dbg-print]]]])
  (:require #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])
            clojure.string
            #?@(:cljs [[cljs.reader :refer [read-string]]])
            #?@(:clj [[clojure.repl :refer [source-fn]]])
            [zprint.zprint :as zprint :refer
             [fzprint blanks line-count max-width line-widths expand-tabs
              zcolor-map fzprint-wrap-comments fzprint-inline-comments]]
            [zprint.finish :refer
             [cvec-to-style-vec compress-style no-style-map color-comp-vec
              handle-lines]]
            [zprint.config :as config :refer
             [config-set-options! get-options config-configure-all!
              reset-options! help-str get-explained-options
              get-explained-all-options get-default-options validate-options
              apply-style perform-remove no-color-map merge-deep]]
            [zprint.zutil :refer
             [zmap-all zcomment? edn* whitespace? string find-root-and-path-nw]]
            [zprint.sutil]
            [zprint.focus :refer [range-ssv]]
            [rewrite-clj.parser :as p]
            #_[clojure.spec.alpha :as s]))


;;
;; zprint
;;
;; A complete pretty printing package for Clojure.
;;
;; Prints both structures and code at the repl, and code in files.
;; Highly configurable, doesn't lose comments.  Completely ignores
;; any incoming whitespace and newlines -- produces its own idea of
;; the best output possible for a given output width.
;;
;; There are a number of namespaces:
;;
;; core     user visible API
;; config   configuration processing and storage
;; zprint   actual pretty printing logic, relies on zutil or sutil
;; zutil    zipper manipulation routines
;; sutil    sexpression manipulation routines
;; focus    add focus to output of zprint
;; finish   process result of zprint into desired type
;; ansi     do coloring for ansi terminal output
;; repl     contains a bunch test cases for zprint development
;; 
;; Basic code flow:
;;
;; The user visible API in zprint.core determines whether the thing
;; to be pretty printed is an sexpression which should be pretty
;; printed directly, or a string which should be parsed into a
;; zipper and then printed (based on :parse-string?).
;; It also handles some exceptional calls directly (e.g.,
;; (czprint nil :describe)), but generally calls
;; zprint.zprint/fzprint* to do the pretty printing.  The options
;; map has been properly configured to use the routines for
;; sexpressions (in sutil) or for zippers (in zutil).
;;
;; zprint.zprint/fzprint* is the routine that handles pretty
;; printing anything -- it dispatches to a variety of fzprint-
;; routines, each one handling a different type of structure.
;; Each of the fzprint- routines takes an option map, which contains
;; not only the configured options and proper utility routines
;; (configured by zprint.core), but also additional information useful
;; during the run-time processing of the structure.
;;
;; zprint/fzprint* returns a str-style-vec, which is a structure
;; like this:
;;
;; [[<string> <color> <type>][<string> <color> <type>] ...]
;;
;; The strings are the actual things to be output, the color is the
;; color in which to output them (if using color), and the type is
;; the type of the information in the string, which is one of:
;;
;; :whitespace   blanks and newlines
;; :element      actual strings containing output
;; :left, :right signals left or right end of a collection
;;
;; This information is processed into useable output by the
;; zprint.core functions by calling functions in zprint.finish and
;; zprint.ansi.
;;
;; zprint.focus is used when calling the zprint.core functions with
;; a zipper, and will assist the user in creating output which shows
;; a focus on some internal structure inside a containing structure.
;; Presently, the API for this is not documented.
;;
;;

;;
;; Clean up the API a bit by putting all of the public functions
;; in zprint.core
;;

(defn set-options!
  "Add some options to the current options, checking to make
  sure that they are correct."
  ([new-options doc-str] (do (config-set-options! new-options doc-str) nil))
  ([new-options] (do (config-set-options! new-options) nil)))

(defn configure-all!
  "Do external configuration if it has not already been done, 
  replacing any internal configuration.  Returns nil if successful, 
  a vector of errors if not."
  []
  (config-configure-all!))

;;
;; # Zipper determination and handling
;;

(defn rewrite-clj-zipper?
  "Is this a rewrite-clj zipper node? A surprisingly hard thing to 
  determine, actually."
  [z]
  (when (and (coll? z)
             (let [type-str (pr-str (type (first z)))]
               (and (> (count type-str) 16)
                    (= "rewrite_clj.node" (subs type-str 0 16)))))
    ;  (= "rewrite_clj.node" (subs (pr-str (type (first z))) 0 16)))
    z))

(defn zipper?
  "Is this a zipper?"
  [z]
  (when (coll? z) (or (rewrite-clj-zipper? z) (:tag (first z)))))

(defn get-zipper
  "If it is a zipper or a string, return a zipper, else return nil."
  [options x]
  (if (string? x)
    (let [x (if (:expand? (:tab options))
              (expand-tabs (:size (:tab options)) x)
              x)
          n (p/parse-string (clojure.string/trim x))]
      (when n (edn* n)))
    (when (zipper? x) x)))

;;
;; # Interface into zprint.zprint namespace
;;
;!zprint {:format :next :vector {:wrap? false}}

(defn fzprint-style
  "Do a basic zprint and output the style vector and the options used for
  further processing: [<style-vec> options]"
  [coll options]
  (let [[input options]
          (cond
            (:zipper? options)
              #?(:clj (if (zipper? coll)
                        [coll options]
                        (throw (#?(:clj Exception.
                                   :cljs js/Error.)
                                (str "Collection is not a zipper"
                                     " yet :zipper? specified!"))))
                 :cljs [coll options])
            (:parse-string? options) (if (string? coll)
                                       [(get-zipper options coll) options]
                                       (throw
                                         (#?(:clj Exception.
                                             :cljs js/Error.)
                                          (str "Collection is not a string yet"
                                               " :parse-string? specified!"))))
            (:zloc? (:focus (:output options)))
              ; We have a zloc which we want to display with
              ; focus.  First, we have to find the root and path
              ; of the zloc.
              (let [[root path] (find-root-and-path-nw coll)]
                [root (assoc-in options [:output :focus :path] path)])
            :else [nil options])
        z-type (if input :zipper :sexpr)
        input (or input coll)]
    (cond (nil? input) [[["nil" (zcolor-map options :nil) :element]] options]
          (:drop? options) [[["" :none]] options]
          ;(if (or (nil? input) (:drop? options))
          ;  (and (:spaces? options)
          ;       (:file? options)
          ;        (or
          ;          ; we ar getting rid of just spaces between expr
          ;          (= (:left-space (:parse options)) :drop)
          ;          ; we are getting rid of all whitespace between expr
          ;          (:interpose (:parse options)))))
          ;
          ;[[["nil" (zcolor-map options :nil) :element]] options]
          :else
            (let [options (assoc options :ztype z-type)
                  fzprint-fn (partial fzprint
                                      options
                                      (if (and (:file? options)
                                               (= (:left-space (:parse options))
                                                  :keep))
                                        (or (:indent options) 0)
                                        0)
                                      input)]
              #_(def coreopt options)
              [(if (= z-type :zipper)
                 (zprint.zutil/zredef-call fzprint-fn)
                 (zprint.sutil/sredef-call fzprint-fn))
               options]))))

(declare get-docstring-spec)

(defn process-rest-options
  "Take some internal-options and the & rest of a zprint/czprint
  call and figure out the options and width and all of that, but
  stop short of integrating these values into the existing options
  that show up with (get-options). Note that internal-options MUST
  NOT be a full options map.  It needs to be just the options that
  have been requested for this invocation.  Does auto-width if that
  is requested, and determines if there are 'special-options', which
  may short circuit the other options processing. 
  Returns [special-option rest-options]"
  [internal-options [width-or-options options]]
  #_(println "process-rest-options: internal-options:" internal-options
             "width-or-options:" width-or-options
             "options:" options)
  #_(def prio internal-options)
  #_(def prwoo width-or-options)
  #_(def pro options)
  (cond
    (= width-or-options :default) [:default (get-default-options)]
    :else
      (let [[width-or-options special-option] (if (#{:explain :support
                                                     :explain-justified :help}
                                                   width-or-options)
                                                [nil width-or-options]
                                                [width-or-options nil])
            configure-errors (when-not (:configured? (get-options))
                               (configure-all!))
            width (when (number? width-or-options) width-or-options)
            rest-options (cond (and width (map? options)) options
                               (map? width-or-options) width-or-options)
            width-map (if width {:width width} {})
            ;      new-options (merge-deep rest-options width-map
            ;      internal-options)
            new-options (merge-deep internal-options rest-options width-map)
            auto-width
              (when (and (not width)
                         ; check both new-options and already
                         ; configured ones
                         (:auto-width? new-options
                                       (:auto-width? (get-options))))
                (let [terminal-width-fn
                        #?(:clj (resolve 'table.width/detect-terminal-width)
                           :cljs nil)
                      actual-width (when terminal-width-fn (terminal-width-fn))]
                  (when (number? actual-width) {:width actual-width})))
            new-options
              (if auto-width (merge-deep new-options auto-width) new-options)
            #_(def nopt new-options)]
        [special-option new-options])))

(defn determine-options
  "Take some internal-options and the & rest of a zprint/czprint
  call and figure out the options and width and all of that. Note
  that internal-options MUST NOT be a full options map.  It needs
  to be just the options that have been requested for this invocation.
  Does auto-width if that is requested, and determines if there are
  'special-options', which may short circuit the other options
  processing. Returns [special-option actual-options]"
  [rest-options]
  (let [; Do what config-and-validate does, minus the doc-map
        configure-errors (when-not (:configured? (get-options))
                           (configure-all!))
        errors (validate-options rest-options)
        combined-errors
          (str (when configure-errors
                 (str "Global configuration errors: " configure-errors))
               (when errors (str "Option errors in this call: " errors)))]
    (if (not (empty? combined-errors))
      (throw (#?(:clj Exception.
                 :cljs js/Error.)
              combined-errors))
      ; remove set elements before doing anything else
      (let [[internal-map rest-options _]
              (perform-remove nil nil (get-options) rest-options)
            ; updated-map is the holder of the actual configuration
            ; as of this point
            [updated-map _ style-errors]
              (apply-style nil nil internal-map rest-options)
            style-errors (when style-errors
                           (str "Option errors in this call: " style-errors))
            actual-options (if (not (empty? style-errors))
                             (throw (#?(:clj Exception.
                                        :cljs js/Error.)
                                     style-errors))
                             (config/add-calculated-options
                               (merge-deep updated-map rest-options)))]
        #_(def dout actual-options)
        ; actual-options is a complete options-map
        actual-options))))

;;
;; # Fundemental interface for fzprint-style, does configuration
;;

(defn zprint*
  "Basic setup for fzprint call, used by all top level fns. Third
  argument can be either a number or a map, and if the third is a
  number, the fourth (if any) must be a map.  The internal-options
  is either an empty map or {:parse-string? true} for the -fn
  functions, and cannot be overridden by an options argument. Returns
  a vector with the style-vec and the options used: [<style-vec> options]"
  [coll special-option actual-options]
  (if special-option
    (case special-option
      :explain (fzprint-style (get-explained-options) (get-default-options))
      :explain-justified (fzprint-style (get-explained-options)
                                        (merge-deep (get-default-options)
                                                    {:map {:justify? true}}))
      :support (fzprint-style (get-explained-all-options) (get-default-options))
      :help (println help-str)
      (println (str "Unknown keyword option: " special-option)))
    (fzprint-style coll
                   (if-let [fn-name (:fn-name actual-options)]
                     (if (:docstring? (:spec actual-options))
                       #?(:clj (assoc-in actual-options
                                 [:spec :value]
                                 (get-docstring-spec actual-options fn-name))
                          :cljs actual-options)
                       actual-options)
                     actual-options))))

(declare process-multiple-forms)

(defn parse-string-all-options
  "Handle options for :parse-string-all?, by removing
  :parse-string-all? and changing the default for 
  :parse {:interpose } to be true instead of nil."
  [options]
  (-> (if (nil? (:interpose (:parse options)))
          (assoc-in options [:parse :interpose] true)
          options)
      (dissoc :parse-string-all?)
      (assoc :trim-comments? true)))

;;
;; # API Support
;;
;; Note that :parse-string-all? support is related to the
;; zprint-file file parsing and printing support, but that
;; they are not the same.  The :parse-string-all? support is
;; designed for taking in a string and doing something useful
;; with it if it has multiple forms in it, while the file support
;; is focused on doing a whole file.  As such, the :interpose
;; support for :parse-string-all? isn't going to play well with
;; the file support.  The :left-space :keep|:drop support is
;; designed for the file support.
;;
;; That said, they both go through the process-multiple-forms
;; function, so that we now have a nice way to test that support.

(defn range-vec
  "Select the elements from start to end from a vector."
  [v [start end]]
  (take (- end start) (drop start v)))

(defn czprint-str-internal
  "Take a zipper or string and pretty print with fzprint, 
  output a str.  Key :color? is true by default, and should
  be set to false in internal-options to make this non-colored.
  Special processing for :parse-string-all?, with
  not only a different code path, but a different default for 
  :parse {:interpose nil} to {:interpose true}"
  [internal-options coll & rest]
  (let [[special-option rest-options] (process-rest-options internal-options
                                                            rest)]
    (if (:parse-string-all? rest-options)
      (if (string? coll)
        (process-multiple-forms (parse-string-all-options rest-options)
                                czprint-str-internal
                                ":parse-string-all? call"
                                (edn* (p/parse-string-all coll)))
        (throw (#?(:clj Exception.
                   :cljs js/Error.)
                (str ":parse-string-all? requires a string!"))))
      (let [actual-options (determine-options rest-options)
            [cvec options] (zprint* coll special-option actual-options)
            cvec-wo-empty cvec
            #_(def cvwoe cvec-wo-empty)
            ; (remove #(empty? (first %)) cvec)
            focus-vec (if-let [path (:path (:focus (:output options)))]
                        (range-ssv cvec-wo-empty path))
            #_(def pa (:path (:focus options)))
            #_(println "focus-vec:" focus-vec)
            accept-vec (handle-lines options cvec-wo-empty focus-vec)
            #_(println "accept-vec:" accept-vec)
            #_(def av accept-vec)
            #_(println "elide:" (:elide (:output options)))
            inline-style-vec (if (:inline? (:comment options))
                               (fzprint-inline-comments options cvec-wo-empty)
                               cvec-wo-empty)
            #_(def ssvi inline-style-vec)
            str-style-vec (cvec-to-style-vec {:style-map no-style-map,
                                              :elide (:elide (:output options))}
                                             inline-style-vec
                                             #_cvec-wo-empty
                                             focus-vec
                                             accept-vec)
            #_(def ssvx str-style-vec)
            wrapped-style-vec (if (:wrap? (:comment options))
                                (fzprint-wrap-comments options str-style-vec)
                                str-style-vec)
            #_(def ssvy wrapped-style-vec)
            comp-style (compress-style wrapped-style-vec)
            #_(def cps comp-style)
            ; don't do extra processing unless we really need it
            color-style (if (or accept-vec focus-vec (:color? options))
                          (color-comp-vec comp-style)
                          (apply str (mapv first comp-style)))
            #_(def cs color-style)]
        (if (:return-cvec? options) cvec color-style)))))

(defn get-fn-source
  "Call source-fn, and if it isn't there throw an exception."
  [fn-name]
  (or (try #?(:clj (source-fn fn-name))
           (catch #?(:clj Exception
                     :cljs :default) e
             nil))
      (throw (#?(:clj Exception.
                 :cljs js/Error.)
              (str "No definition found for a function named: " fn-name)))))

;;
;; # User level printing functions
;;
;; (*zprint <to-print> <width> <options-map>)
;;
;; zprint       pretty print to *out*
;; czprint      pretty print to *out* with ansi colors
;;
;; zprint-str   pretty print to string
;; czprint-str  pretty print to string with ansi colors
;;
;; options:
;;
;;   See config.clj
;;

(defn zprint-str
  "Take a strutcure or a string and  pretty print it, and
  output a str. (zprint-str nil :help) for more information."
  [coll & rest]
  (apply czprint-str-internal {:color? false} coll rest))

(defn czprint-str
  "Take a structure or string and pretty print it, and output 
  a str that has ansi color in it.  (czprint nil :help) for 
  more information."
  [coll & rest]
  (apply czprint-str-internal {} coll rest))

(defn zprint
  "Take a structure or string and pretty print it. 
  (zprint nil :help) for more information."
  [coll & rest]
  (println (apply czprint-str-internal {:color? false} coll rest)))

(defn czprint
  "Take a zipper or string and pretty print it.
  (czprint nil :help) for more information."
  [coll & rest]
  (println (apply czprint-str-internal {} coll rest)))

#?(:clj (defmacro zprint-fn-str
          "Take a fn name, and pretty print it, output a string."
          [fn-name & rest]
          `(apply czprint-str-internal
             {:parse-string? true, :color? false}
             (get-fn-source '~fn-name)
             ~@rest
             [])))

#?(:clj
     (defmacro czprint-fn-str
       "Take a fn name, and pretty print it with syntax highlighting
  into a string."
       [fn-name & rest]
       `(apply czprint-str-internal
          {:parse-string? true}
          (get-fn-source '~fn-name)
          ~@rest
          [])))

#?(:clj (defmacro zprint-fn
          "Take a fn name, and pretty print it."
          [fn-name & rest]
          `(println (apply czprint-str-internal
                      {:parse-string? true, :color? false, :fn-name '~fn-name}
                      (get-fn-source '~fn-name)
                      ~@rest
                      []))))

#?(:clj (defmacro czprint-fn
          "Take a fn name, and pretty print it with syntax highlighting."
          [fn-name & rest]
          `(println (apply czprint-str-internal
                      {:parse-string? true, :fn-name '~fn-name}
                      (get-fn-source '~fn-name)
                      ~@rest
                      []))))

;;
;; # File operations
;;

;;
;; ## Parse a comment to see if it has an options map in it
;;

(defn get-options-from-comment
  "s is string containing a comment.  See if it starts out ;!zprint, 
  and if it does, attempt to parse it as an options map.  
  Return [options error-str] with only one of the two populated 
  if it started with ;!zprint, and nil otherwise."
  [zprint-num s]
  (let [comment-split (clojure.string/split s #"^;!zprint ")]
    (when-let [possible-options (second comment-split)]
      (try
        [(read-string possible-options) nil]
        (catch #?(:clj Exception
                  :cljs :default) e
          [nil
           (str "Unable to create zprint options map from: '" possible-options
                "' found in !zprint directive number: " zprint-num
                " because: " e)])))))

;;
;; ## Process the sequences of forms in a file
;;

(defn spaces?
  "If a string is all spaces and has at least one space, 
  returns the count of the spaces, otherwise nil."
  [s]
  (let [len (count s)]
    (if (zero? len) nil (when (empty? (clojure.string/replace s " " "")) len))))

;!zprint {:format :next :vector {:wrap? false}}

(defn process-form
  "Take one form from a file and process it.  The primary goal is
  of course to produce a string to put into the output file.  In
  addition, see if that string starts with ;!zprint and if it does,
  pass along that information back to the caller.  The input is a 
  [[next-options <previous-string>] form], where next-options accumulates
  the information to be applied to the next non-comment/non-whitespace
  element in the file.  The output is [next-options output-str zprint-num], 
  since reductions is used to call this function.  See process-multiple-forms
  for what is actually done with the various :format values."
  [rest-options
   zprint-fn
   zprint-specifier
   [next-options _ indent zprint-num]
   form]
  (let [comment? (zcomment? form)
        whitespace-form? (whitespace? form)
        [new-options error-str] (when (and comment?
                                           (zero? indent)
                                           (:process-bang-zprint? rest-options))
                                  (get-options-from-comment (inc zprint-num)
                                                            (string form)))
        ; Develop the internal-options we want to call the zprint-fn
        ; with, and also an options map with those integrated we can use
        ; to decide what we are doing ourselves. zprint-fn will integrate
        ; them into the options map as well.
        next-options
          (if (zero? indent) next-options (assoc next-options :indent indent))
        internal-options (if (or comment?
                                 ;whitespace-form?
                                 (empty? next-options))
                           rest-options
                           (merge-deep rest-options next-options))
        decision-options (merge-deep (get-options) internal-options)
        ; Now make decisions about things
        space-count (when whitespace-form?
                      (if (:interpose (:parse decision-options))
                        ; we are getting rid of all whitespace between expr
                        0
                        (spaces? (zprint.zutil/string form))
                        #_(if (= (:left-space (:parse decision-options)) :drop)
                            ; we are getting rid of just spaces between expr
                            (spaces? (zprint.zutil/string form))
                            nil)))
        drop? (not (not (and space-count
                             (not (= :skip (:format next-options)))
                             (or (:interpose (:parse decision-options))
                                 (= (:left-space (:parse decision-options))
                                    :drop)))))
        ; If this was a ;!zprint line, don't wrap it
        local-options
          (if new-options
            {:comment {:wrap? false}, :zipper? true, :file? true, :drop? drop?}
            {:zipper? true, :file? true, :drop? drop?})
        internal-options (merge-deep internal-options local-options)
        #_(do (println "-----------------------")
              (println "form:")
              (prn (zprint.zutil/string (or (zprint.zutil/zfirst form) form)))
              (println "space-count:" space-count)
              (println "indent:" indent)
              (println "whitespace-form?:" whitespace-form?)
              (println "interpose:" (:interpose (:parse decision-options)))
              (println "left-space:" (:left-space (:parse decision-options)))
              (println "new-options:" new-options)
              (println "(:indent next-options):" (:indent next-options))
              (println "internal-options:" internal-options)
              (println "next-options:" next-options))
        output-str
          ; Should we zprint this form?
          (if (or (= :off (:format decision-options))
                  (and (not (or comment? whitespace-form?))
                       ; used to be next-options but if not a comment then
                       ; they are in internal-options
                       (= :skip (:format internal-options))))
            (string form)
            ; call zprint-str-internal or an alternative if one exists
            (zprint-fn internal-options form))
        local? (or (= :skip (:format new-options))
                   (= :next (:format new-options)))]
    (when (and new-options (not local?))
      (set-options! new-options
                    (str ";!zprint number " (inc zprint-num)
                         " in " zprint-specifier)))
    (when error-str (println "Warning: " error-str))
    [(cond local? (merge-deep next-options new-options)
           (or comment? whitespace-form?) next-options
           :else {})
     output-str
     (or space-count 0)
     (if new-options (inc zprint-num) zprint-num)]))

;;
;; # File comment API
;;
;; In order to properly process a file, sometimes you want to alter
;; the value of the zprint options map for a single function definition,
;; or turn it off completely and then on again later.  Or, possibly,
;; set some defaults which hold while formatting only this file.
;;
;; This is all possible because of the zprint comment API.
;;
;; If a comment starts with the string ";!zprint ", then the rest
;; of the string will be parsed as a zprint options map.
;;
;; For example:
;;
;;   ;!zprint {:vector {:wrap? false}}
;;
;; will turn off vector wrapping in the file and it will stay that way
;; until the end of the file (or another ;!zprint comment alters it).
;;
;; The API:
;;
;; ;!zprint <options>   perform a (set-options! <options>) which will
;;                      be used until altered or the end of the file is
;;                      reached
;;
;; ;!zprint {:format :off} Do not format successive forms with zprint to
;;                         the end of the file
;;
;; ;!zprint {:format :on}  Format successive forms with zprint (default)
;;
;; ;!zprint {:format :skip} Do not format the next non-comment/non-whitespace
;;                          element with zprint.
;;
;; ;!zprint {:format :next <other-options>} Format the next non-comment
;;                                          non-whitespace element with the
;;                                          specified <other-options>
;;

(defn process-multiple-forms
  "Take a sequence of forms (which are zippers of the elements of
  a file or a string containing multiple forms somewhere), and not 
  only format them for output but also handle comments containing 
  ;!zprint that affect the options map throughout the processing."
  [rest-options zprint-fn zprint-specifier forms]
  (let [interpose-option (or (:interpose (:parse rest-options))
                             (:interpose (:parse (get-options))))
        interpose-str
          (cond (or (nil? interpose-option) (false? interpose-option)) nil
                (string? interpose-option) interpose-option
                ; here is where :interpose true turns into :interpose "\n"
                (true? interpose-option) "\n"
                :else (throw (#?(:clj Exception.
                                 :cljs js/Error.)
                              (str "Unsupported {:parse {:interpose value}}: "
                                   interpose-option))))
        seq-of-zprint-fn
          (reductions
            (partial process-form rest-options zprint-fn zprint-specifier)
            [{} "" 0 0]
            (zmap-all identity forms))
        #_(def sozf seq-of-zprint-fn)
        seq-of-strings (map second seq-of-zprint-fn)]
    #_(def sos seq-of-strings)
    (if interpose-str
      (apply str (interpose interpose-str (remove empty? seq-of-strings)))
      (apply str seq-of-strings))))

;;
;; ## Process an entire file
;;

(defn zprint-file-str
  "Take a string, which typically holds the contents of an entire
  file, but doesn't have to, and format the entire string, outputing
  a formatted string.  It respects white space at the top level,
  while completely ignoring it within all top level forms (typically
  defs and function definitions).  It allows comments at the top
  level, as well as in function definitions, and also supports
  ;!zprint directives at the top level.  zprint-specifier is the
  thing that will be used in messages if errors are detected in
  ;!zprint directives, so it should identify the file (or other
  element) to allow the user to find the problem. new-options are
  optional options to be used when doing the formatting (and will
  be overriddden any options in ;!zprint directives).  doc-str is
  an optional string to be used when setting the new-options into
  the configuration."
  ([file-str zprint-specifier new-options doc-str]
   (let [original-options (get-options)
         original-doc-map (get-explained-all-options)]
     (when new-options (set-options! new-options doc-str))
     (try (let [lines (clojure.string/split file-str #"\n")
                lines (if (:expand? (:tab (get-options)))
                        (map (partial expand-tabs (:size (:tab (get-options))))
                          lines)
                        lines)
                filestring (clojure.string/join "\n" lines)
                ; If file ended with a \newline, make sure it still does
                filestring (if (= (last file-str) \newline)
                             (str filestring "\n")
                             filestring)
                forms (edn* (p/parse-string-all filestring))]
            #_(def fileforms (zmap-all identity forms))
            (process-multiple-forms {:process-bang-zprint? true, :color? false}
                                    czprint-str-internal
                                    zprint-specifier
                                    forms))
          (finally (reset-options! original-options original-doc-map)))))
  ([file-str zprint-specifier new-options]
   (zprint-file-str file-str
                    zprint-specifier
                    new-options
                    "zprint-file-str input"))
  ([file-str zprint-specifier]
   (zprint-file-str file-str zprint-specifier nil nil)))

#?(:clj
     (defn zprint-file
       "Take an input file infile and an output file outfile, and format
  every form in the input file with zprint and write it to the
  output file. infile and outfile are input to slurp and spit,
  repspectively. file-name is a string, and is usually the name of
  the input file but could be anything to help identify the input
  file when errors in ;!zprint directives are reported.  options
  are any additional options to be used for this operation, and
  will be overridden by any options in ;!zprint directives."
       ([infile file-name outfile options]
        (let [file-str (slurp infile)
              outputstr (zprint-file-str file-str
                                         (str "file: " file-name)
                                         options
                                         (str "zprint-file input for file: "
                                              file-name))]
          (spit outfile outputstr)))
       ([infile file-name outfile] (zprint-file infile file-name outfile nil))))

;;
;; # Process specs to go into a doc-string
;;

(defn format-spec
  "Take a spec and a key, and format the output as a string. Width is
  because the width isn't really (:width options)."
  [options describe-fn fn-spec indent key]
  (when-let [key-spec (get fn-spec key)]
    (let [key-str (str (name key) ": ")
          total-indent (+ (count key-str) indent)
          ; leave room for double-quote at the end
          width (dec (- (:width options) total-indent))
          key-spec-data (describe-fn key-spec)
          spec-str (zprint-str key-spec-data width {:style :spec})
          spec-no-nl (clojure.string/split spec-str #"\n")
          spec-shift-right
            (apply str (interpose (str "\n" (blanks total-indent)) spec-no-nl))]
      (str (blanks indent) key-str spec-shift-right))))

#?(:clj
     (defn get-docstring-spec
       "Given a function name (which, if used directly, needs to be quoted)
  return a string which is contains the spec information that could go
  in the doc string."
       [{:keys [width rightcnt], {:keys [indent]} :list, :as options} fn-name]
       (let [{n :ns, nm :name, :as m} (meta (resolve fn-name))
             get-spec-fn (resolve 'clojure.spec.alpha/get-spec)
             describe-fn (resolve 'clojure.spec.alpha/describe)]
         (when (and get-spec-fn describe-fn)
           (when-let [fn-spec (get-spec-fn (symbol (str (ns-name n))
                                                   (name nm)))]
             (apply str
               "\n\n" (blanks indent)
               "Spec:\n" (interpose "\n"
                           (remove nil?
                             (map (partial format-spec
                                           options
                                           describe-fn
                                           fn-spec
                                           (+ indent indent))
                               [:args :ret :fn])))))))))