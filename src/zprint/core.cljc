(ns zprint.core
  #?@(:cljs [[:require-macros
              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])
  (:require
    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])
    clojure.string
    #?@(:cljs [[cljs.reader :refer [read-string]]])
    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])
    [zprint.zprint :as zprint :refer
     [fzprint line-count max-width line-widths expand-tabs zcolor-map
      determine-ending-split-lines]]
    [zprint.finish :refer
     [cvec-to-style-vec compress-style no-style-map color-comp-vec
      handle-lines]]
    [zprint.comment :refer
     [fzprint-inline-comments fzprint-wrap-comments
      fzprint-align-inline-comments blanks]]
    [zprint.config :as config :refer
     [config-set-options! get-options config-configure-all! reset-options!
      help-str get-explained-options get-explained-set-options
      get-explained-all-options get-default-options validate-options apply-style
      perform-remove no-color-map merge-deep]]
    [zprint.zutil :refer
     [zmap-all zcomment? edn* whitespace? string find-root-and-path-nw]]
    [zprint.sutil]
    [zprint.focus :refer [range-ssv]]
    [zprint.range :refer
     [expand-range-to-top-level split-out-range reassemble-range]]
    [sci.core :as sci]
    [rewrite-clj.parser :as p]
    #_[clojure.spec.alpha :as s])
  #?@(:clj ((:import (java.net URL URLConnection)
                     (java.util.concurrent Executors)
                     (java.io File)
                     (java.util Date)))))

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
(def ^:dynamic ^:no-doc *cache-path*
  #?(:clj (str (System/getProperty "user.home") File/separator ".zprint")
     :cljs nil))

(defn set-options!
  "There is an internal options-map containing default values which is 
  configured from ~/.zprintrc when zprint is first used.  set-options! 
  is used to alter the internal options-map by specifying individual
  options-map values that will be merged into the internal options-map.
  Typically, it is called with only new-options, an options map.  If
  you add a doc-str, that will show up when the internal options map
  is displayed with (czprint nil :explain).  The argument op-options
  is an options map that is only examined if the call to set-options!
  is the first use of the zprint library.  If it is, operational options
  are examined in the op-options map to see where to find formatting
  options.  Operational options are those such as cwd-zprintrc? and
  search-config?."
  ([new-options doc-str op-options]
   (do (config-set-options! new-options doc-str op-options) nil))
  ([new-options doc-str] (do (config-set-options! new-options doc-str) nil))
  ([new-options] (do (config-set-options! new-options) nil)))

; Default [:cache :location]
(def ^:dynamic ^:no-doc *default-cache-loc* ".")
; Default [:cache :directory]
(def ^:dynamic ^:no-doc *default-cache-dir* ".zprint")
; Default [:url :cache-dir]
(def ^:dynamic ^:no-doc *default-url-cache* "urlcache")
; Default [:url :cache-secs]
(def ^:dynamic ^:no-doc *default-url-cache-secs* 300)

(defn ^:no-doc load-options!
  "Loads options from url, expecting an edn options map that will be passed
  to set-options! Valid options will be cached in 
  (str (:cache-loc (:cache options)) 
       File/separator 
       (:cache-dir (:cache options))
       File/separator
       (:url (:cache-dir (:cache options))))
  for (:cache-secs (:url options)) or 5 minutes if :cache-secs is nil.
  If [:cache :location] contains a \".\", it is considered a Java property, 
  else it is considered an environment variable.  In either case, 
  it is looked up.
  Invalid options will throw an Exception.
  HTTP urls will have the Cache-Control max-age parameter respected,
  falling back to the Expires header if set."
  [options url]
  #?(:clj
       (let [^URL url (if (instance? URL url) url (URL. url))
             host (if (= "" (.getHost url)) "nohost" (.getHost url))
             url-as-filename (str host "_" (hash (str url)))
             cache-loc (or (:location (:cache options)) "")
             ; If we have no cache-loc, make it the current directory
             cache-loc (if (empty? cache-loc)
                         *default-cache-loc*
                         ; If the cache-loc includes a ".", then treat it
                         ; as a Java system property, else an environment
                         ; variable.
                         (if (clojure.string/includes? cache-loc ".")
                           (System/getProperty cache-loc)
                           (System/getenv cache-loc)))
             ; Default cache-dir to .zprint
             cache-dir (or (:directory (:cache options)) *default-cache-dir*)
             cache-path (str cache-loc File/separator cache-dir)
             ; Default urldir to "urlcache"
             urldir (or (:cache-dir (:url options)) *default-url-cache*)
             cache-secs (or (:cache-secs (:url options))
                            *default-url-cache-secs*)
             cache (if (:cache-path (:url options))
                     (io/file (:cache-path (:url options)))
                     (io/file (str cache-path File/separator urldir)
                              url-as-filename))
             cache-item (if (and (.exists cache) (not (zero? (.length cache))))
                          (try (-> (slurp cache)
                                   (sci/eval-string)
                                   #_(clojure.edn/read-string))
                               (catch Exception e (.delete cache) nil)))
             active-cache? (and cache-item
                                (> (:expires cache-item)
                                   (System/currentTimeMillis)))]
         #_(prn "cache items:"
                "\noptions:" options
                "\ncache-loc:" cache-loc
                "\ncache-dir:" cache-dir
                "\ncache-path:" cache-path
                "\nurl-dir:" urldir
                "\ncache-secs:" cache-secs
                "\ncache:" cache
                "\ncache-items:" cache-item
                "\nactive-cache?:" active-cache?)
         (if active-cache?
           ;1> cached, non expired version of url used
           (set-options! (:options cache-item) (str "cached options from " url))
           (try
             (let [^URLConnection remote-conn (doto (.openConnection url)
                                                (.setConnectTimeout 1000)
                                                (.connect))
                   remote-opts (some-> (slurp (.getInputStream remote-conn))
                                       (sci/eval-string)
                                       #_(clojure.edn/read-string))]
               (if remote-opts
                 (do
                   ;2> no valid cache, remote used, async best-effort cache
                   (set-options! remote-opts (str "options from " url))
                   (.. (Executors/newSingleThreadExecutor)
                       (submit
                         (reify
                           Runnable
                             (run [this]
                               (try
                                 (io/make-parents cache)
                                 (let [cc (.getHeaderField remote-conn
                                                           "Cache-Control")
                                       [_ max-age]
                                         (if cc
                                           (re-matches
                                             #"(?i).*?max-age\s*=\s*(\d+)"
                                             cc))
                                       cache-expiry
                                         (if max-age
                                           (+ (System/currentTimeMillis)
                                              (* 1000 (Long/parseLong max-age)))
                                           (let [expires (.getExpiration
                                                           remote-conn)]
                                             (if (and expires
                                                      (not (zero? expires)))
                                               expires
                                               (+ (System/currentTimeMillis)
                                                  (* 1000 cache-secs)))))]
                                   (spit cache
                                         (pr-str {:expires cache-expiry,
                                                  :options remote-opts})))
                                 (catch Exception e
                                   (.println System/err
                                             (format
                                               "WARN: cache failed for %s: %s"
                                               url
                                               (.getMessage e))))))))
                       (get)))
                 ;3> no cache, blank remote
                 (throw (Exception. "ERROR: retrieving config from %s" url))))
             (catch Exception e
               (if cache-item
                 (do
                   ;4> expired cache but remote failed, use cache
                   (set-options! (:options cache-item)
                                 (str "cached, but expired, options from " url))
                   (.println
                     System/err
                     (format
                       "WARN: using expired cache config for %s after error: %s"
                       url
                       (.getMessage e))))
                 (throw ;5> no cache, failed remote
                   (Exception. (format "ERROR: retrieving config from %s: %s"
                                       url
                                       (.getMessage e)))))))))
     :cljs nil))

(defn configure-all!
  "Do external configuration regardless of whether or not it already
  been done, replacing any existing configuration.  Returns nil if successful,
  a vector of errors if not."
  []
  (config-configure-all!))

;;
;; # Zipper determination and handling
;;

(defn ^:no-doc rewrite-clj-zipper?
  "Is this a rewrite-clj zipper node? A surprisingly hard thing to 
  determine, actually."
  [z]
  (when (and (coll? z)
             (let [type-str (pr-str (type (first z)))]
               (and (> (count type-str) 16)
                    (= "rewrite_clj.node" (subs type-str 0 16)))))
    ;  (= "rewrite_clj.node" (subs (pr-str (type (first z))) 0 16)))
    z))

(defn ^:no-doc zipper?
  "Is this a zipper?"
  [z]
  (when (coll? z) (or (rewrite-clj-zipper? z) (:tag (first z)))))

(defn ^:no-doc get-zipper
  "If it is a zipper or a string, return a zipper, else return nil.
  Always trims whitespace (including nl) off of strings before parsing!
  Returns [zloc line-ending-str], with line-ending-str nil if x was a
  zipper."
  [options x]
  (if (string? x)
    (let [[line-ending lines] (determine-ending-split-lines x)
          lines (if (:expand? (:tab options))
                  (map (partial expand-tabs (:size (:tab options))) lines)
                  lines)
          ; Glue lines back together with \n line ending, to work around
          ; rewrite-clj bug with \r\n endings on comments.  Otherwise,
          ; the rewrite-clj parse would "convert" them all to \n for us,
          ; which is really what we need anyway.
          x (clojure.string/join "\n" lines)
          n (p/parse-string (clojure.string/trim x))]
      (when n [(edn* n) line-ending]))
    (when (zipper? x) [x nil])))

;;
;; # Interface into zprint.zprint namespace
;;
;!zprint {:format :next :vector {:wrap? false}}

(defn ^:no-doc fzprint-style
  "Do a basic zprint and output the style vector and the options used for
  further processing: [<style-vec> options line-ending]"
  [coll options]
  (let [[input options line-ending]
          (cond (:zipper? options)
                  #?(:clj (if (zipper? coll)
                            [coll options nil]
                            (throw (Exception. (str
                                                 "Collection is not a zipper"
                                                 " yet :zipper? specified!"))))
                     :cljs [coll options nil])
                (:parse-string? options)
                  (if (string? coll)
                    (let [[form line-end] (get-zipper options coll)]
                      [form options line-end])
                    (throw (#?(:clj Exception.
                               :cljs js/Error.)
                            (str "Collection is not a string yet"
                                 " :parse-string? specified!"))))
                (:zloc? (:focus (:output options)))
                  ; We have a zloc which we want to display with
                  ; focus.  First, we have to find the root and path
                  ; of the zloc.
                  (let [[root path] (find-root-and-path-nw coll)]
                    [root (assoc-in options [:output :focus :path] path) nil])
                :else [nil options nil])
        z-type (if input :zipper :sexpr)
        input (or input coll)]
    (cond (nil? input)
            [[["nil" (zcolor-map options :nil) :element]] options line-ending]
          (:drop? options) [[["" :none]] options line-ending]
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
               options
               line-ending]))))

#?(:clj (declare get-docstring-spec))

(defn ^:no-doc process-rest-options
  "Take some internal-options and the & rest of a zprint/czprint
  call and figure out the options and width and all of that, but
  stop short of integrating these values into the existing options
  that show up with (get-options). Note that internal-options MUST
  NOT be a full options-map.  It needs to be just the options that
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
      (let [[width-or-options special-option]
              (if (#{:explain :explain-set :support :explain-justified :help}
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

(defn ^:no-doc determine-options
  "Take some internal-options and the & rest of a zprint/czprint
  call and figure out the options and width and all of that. Note
  that internal-options MUST NOT be a full options-map.  It needs
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

(defn ^:no-doc zprint*
  "Basic setup for fzprint call, used by all top level fns. Third
  argument can be either a number or a map, and if the third is a
  number, the fourth (if any) must be a map.  The internal-options
  is either an empty map or {:parse-string? true} for the -fn
  functions, and cannot be overridden by an options argument. Returns
  a vector with the style-vec and the options used: 
  [<style-vec> options line-ending]"
  [coll special-option actual-options]
  (if special-option
    (case special-option
      :explain (fzprint-style (get-explained-options)
                              (merge-deep (get-default-options) actual-options))
      :explain-set (fzprint-style (get-explained-set-options)
                                  (merge-deep (get-default-options)
                                              actual-options))
      :explain-justified (fzprint-style (get-explained-options)
                                        (merge-deep (get-default-options)
                                                    actual-options
                                                    {:map {:justify? true}}))
      :support (fzprint-style (get-explained-all-options)
                              (merge-deep (get-default-options) actual-options))
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

(defn ^:no-doc parse-string-all-options
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

(defn ^:no-doc range-vec
  "Select the elements from start to end from a vector."
  [v [start end]]
  (take (- end start) (drop start v)))

(defn ^:no-doc remove-loc
  "If this is a :newline, :indent, :whitespace, or :right, trim off the 
  4th thing."
  [tuple]
  (let [[s color element] tuple]
    (if (or (= element :newline)
            (= element :indent)
            (= element :whitespace)
            (= element :right))
      [s color element]
      tuple)))

(defn ^:no-doc remove-newline-indent-locs ; i132
  "Remove the debugging information on :indent and :newline style-vec
  elements when doing :return-cvec? true."
  [cvec]
  (mapv remove-loc cvec))

(defn ^:no-doc any-respect?
  "If any of :respect-nl?, :respect-bl?, or :indent-only? are set, return
  true."
  [caller options]
  (let [callers-options (caller options)]
    (or (:respect-nl? callers-options)
        (:respect-bl? callers-options)
        (:indent-only? callers-options))))

(defn ^:no-doc any-respect-at-all?
  "Look throught the options, and see if any of :respect-nl?, :respect-bl?
  or :indent-only are enabled for anything.  Return false if none are enabled,
  truthy if any are."
  [options]
  (or (any-respect? :list options)
      (any-respect? :vector options)
      (any-respect? :set options)
      (any-respect? :map options)))

(defn ^:no-doc find-eol-blanks
  "Given a str-style-vec, find all of the places where the end of a line
  has blanks.  Output the tuples that have that and the ones that 
  follow. If no-respect? is truthy, then only do this if no :respect-nl,
  :respect-bl, or indent-only are set."
  [options ssv coll no-respect?]
  (when (cond (string? coll) (not (clojure.string/blank? coll))
              (zipper? coll) (not (clojure.string/blank? (rewrite-clj.zip/string
                                                           coll)))
              :else nil)
    (if (or (not no-respect?) (not (any-respect-at-all? options)))
      (loop [style-vec ssv
             previous-ends-w-blanks? nil
             previous-tuple nil
             out []]
        (if-not (first style-vec)
          (if previous-ends-w-blanks? (conj out previous-tuple) out)
          (let [[s _ e :as tuple] (first style-vec)
                add-previous-to-out? (and (or (= e :indent) (= e :newline))
                                          previous-ends-w-blanks?)
                ends-w-blanks? (clojure.string/ends-with? s " ")]
            (recur
              (next style-vec)
              ends-w-blanks?
              tuple
              (if add-previous-to-out? (conj out previous-tuple) out))))))))

(defn ^:no-doc zprint-str-internal
  "Take a zipper or string and pretty print with fzprint, 
  output a str.  Key :color? is false by default, and should
  be set to true in internal-options to make things colored.
  Special processing for :parse-string-all?, with
  not only a different code path, but a different default for 
  :parse {:interpose nil} to {:interpose true}"
  [internal-options coll & rest]
  (let [[special-option rest-options] (process-rest-options internal-options
                                                            rest)]
    #_(println "special-option:" special-option "rest-options:" rest-options)
    (dbg rest-options "zprint-str-internal VVVVVVVVVVVVVVVV")
    (if (:parse-string-all? rest-options)
      (if (string? coll)
        (let [[line-ending lines] (determine-ending-split-lines coll)
              current-options (merge-deep (get-options) rest-options)
              lines (if (:expand? (:tab current-options))
                      (map (partial expand-tabs (:size (:tab current-options)))
                        lines)
                      lines)
              ; Glue lines back together with \n line ending, to work around
              ; rewrite-clj bug with \r\n endings on comments.  Otherwise,
              ; the rewrite-clj parse would "convert" them all to \n for us,
              ; which is really what we need anyway.
              coll (clojure.string/join "\n" lines)
              result (process-multiple-forms (parse-string-all-options
                                               rest-options)
                                             zprint-str-internal
                                             ":parse-string-all? call"
                                             (edn* (p/parse-string-all coll)))
              str-w-line-endings
                (if (or (nil? line-ending) (= line-ending "\n"))
                  result
                  (clojure.string/replace result "\n" line-ending))]
          (dbg rest-options "zprint-str-internal ^^^ pmf ^^^ pmf ^^^ pmf ^^^")
          str-w-line-endings)
        (throw (#?(:clj Exception.
                   :cljs js/Error.)
                (str ":parse-string-all? requires a string!"))))
      (let [actual-options (determine-options rest-options)
            [cvec options line-ending]
              (zprint* coll special-option actual-options)
            #_(println "special-option:" special-option
                       "actual-options:" (apply sorted-map
                                           (flatten (seq actual-options)))
                       "\n\n\noptions:" (apply sorted-map
                                          (flatten (seq options))))
            #_(def aopt actual-options)
            cvec-wo-empty cvec
            #_(def cvwoe cvec-wo-empty)
            focus-vec (if-let [path (:path (:focus (:output options)))]
                        (range-ssv cvec-wo-empty path))
            #_(println "focus-vec:" focus-vec)
            accept-vec (handle-lines options cvec-wo-empty focus-vec)
            #_(println "accept-vec:" accept-vec)
            #_(def av accept-vec)
            #_(println "elide:" (:elide (:output options)))
            eol-blanks (when (:test-for-eol-blanks? options)
                         (find-eol-blanks options cvec-wo-empty coll nil))
            eol-str (when (not (empty? eol-blanks))
                      (str "=======  eol-blanks: " eol-blanks))
            inline-style-vec (if (:inline? (:comment options))
                               (fzprint-inline-comments options cvec-wo-empty)
                               cvec-wo-empty)
            #_(def ssvi inline-style-vec)
            inline-style-vec (if (:inline? (:comment options))
                               (fzprint-align-inline-comments options
                                                              inline-style-vec)
                               inline-style-vec)
            #_(def ssvia inline-style-vec)
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
            #_(def cs color-style)
            str-w-line-endings
              (if (or (nil? line-ending) (= line-ending "\n"))
                color-style
                (clojure.string/replace color-style "\n" line-ending))]
        (dbg rest-options "zprint-str-internal ^^^^^^^^^^^^^^^^^^")
        (if eol-str
          eol-str
          (if (:return-cvec? options)
            (remove-newline-indent-locs cvec)  ; i132
            str-w-line-endings))))))

(defn ^:no-doc get-fn-source
  "Call source-fn, and if it isn't there throw an exception."
  [fn-name]
  (or #?(:clj (try (source-fn fn-name) (catch Exception e nil)))
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
  "Take coll, a Clojure data structure or a string containing Clojure code or
  data, format it readably, and output a str. Additional optional arguments: 

      (zprint-str coll <numeric-width>)
      (zprint-str coll <numeric-width> <options-map>)
      (zprint-str coll <options-map>)

  If coll is a string containing Clojure source:

        (zprint-str coll {:parse-string? true})

      (zprint nil :help)    ; for more information
      (zprint nil :explain) ; to see the current options-map"
  {:doc/format :markdown}
  [coll & rest]
  (apply zprint-str-internal {} coll rest))

(defn czprint-str
  "Take coll, a Clojure data structure or a string containing Clojure code or
  data, format it readably, and output a str containing ANSI escapes to 
  syntax color the output. Additional optional arguments: 

      (czprint-str coll <numeric-width>)
      (czprint-str coll <numeric-width> <options-map>)
      (czprint-str coll <options-map>)

  If coll is a string containing Clojure source:

        (czprint-str coll {:parse-string? true})

      (czprint nil :help)    ; for more information
      (czprint nil :explain) ; to see the current options-map"
  {:doc/format :markdown}
  [coll & rest]
  (apply zprint-str-internal {:color? true} coll rest))

(defn zprint
  "Take coll, a Clojure data structure or a string containing Clojure code or
  data, format it readably, and output to stdout. Additional optional 
  arguments: 

      (zprint coll <numeric-width>)
      (zprint coll <numeric-width> <options-map>)
      (zprint coll <options-map>)

  If coll is a string containing Clojure source::

        (zprint coll {:parse-string? true})

      (zprint nil :help)    ; for more information
      (zprint nil :explain) ; to see the current options-map"
  {:doc/format :markdown}
  [coll & rest]
  (println (apply zprint-str-internal {} coll rest)))

(defn czprint
  "Take coll, a Clojure data structure or a string containing Clojure code or
  data, format it readably, and produce output to stdout containing ANSI 
  escapes to syntax color the output. Optional arguments: 

      (czprint coll <numeric-width>)
      (czprint coll <numeric-width> <options-map>)
      (czprint coll <options-map>)

  If coll is a string containing Clojure source:

        (czprint coll {:parse-string? true})

      (czprint nil :help)    ; for more information
      (czprint nil :explain) ; to see the current options-map"
  {:doc/format :markdown}
  [coll & rest]
  (println (apply zprint-str-internal {:color? true} coll rest)))

#?(:clj
     (defmacro zprint-fn-str
       "Given a function name, fn-name, retrieve the source for it,
  and return a string with the source formatted in a highly readable
  manner. Appends any available specs to the end of the docstring. 
  Optional arguments:

      (zprint-fn-str fn-name <numeric-width>)
      (zprint-fn-str fn-name <numeric-width> <options-map>)
      (zprint-fn-str fn-name <options-map>)

      (zprint nil :help)    ; for more information
      (zprint nil :explain) ; to see the current options-map "
       {:doc/format :markdown}
       [fn-name & rest]
       `(apply zprint-str-internal
          {:parse-string? true, :fn-name '~fn-name}
          (get-fn-source '~fn-name)
          ~@rest
          [])))

#?(:clj
     (defmacro czprint-fn-str
       "Given a function name, fn-name, retrieve the source for it,
  and return a string with the source formatted in a highly readable
  manner, including ANSI escape sequences to syntax color the output.
  Appends any available specs to the end of the docstring. 
  Optional arguments:

      (czprint-fn-str fn-name <numeric-width>)
      (czprint-fn-str fn-name <numeric-width> <options-map>)
      (czprint-fn-str fn-name <options-map>)

      (czprint nil :help)    ; for more information
      (czprint nil :explain) ; to see the current options-map"
       {:doc/format :markdown}
       [fn-name & rest]
       `(apply zprint-str-internal
          {:parse-string? true, :color? true, :fn-name '~fn-name}
          (get-fn-source '~fn-name)
          ~@rest
          [])))

#?(:clj
     (defmacro zprint-fn
       "Given a function name, fn-name, retrieve the source for it,
  and output to stdout the source formatted in a highly readable
  manner. Appends any available specs to the end of the docstring.
  Optional arguments:

      (zprint-fn fn-name <numeric-width>)
      (zprint-fn fn-name <numeric-width> <options-map>)
      (zprint-fn fn-name <options-map>)

      (zprint nil :help)    ; for more information
      (zprint nil :explain) ; to see the current options-map"
       {:doc/format :markdown}
       [fn-name & rest]
       `(println (apply zprint-str-internal
                   {:parse-string? true, :fn-name '~fn-name}
                   (get-fn-source '~fn-name)
                   ~@rest
                   []))))

#?(:clj
     (defmacro czprint-fn
       "Given a function name, fn-name, retrieve the source for it,
  and output to stdout the source formatted in a highly readable
  manner. Includes ANSI escape sequences to provide syntax coloring,
  and appends any available specs to the end of the docstring.
  Optional arguments:

      (czprint-fn fn-name <numeric-width>)
      (czprint-fn fn-name <numeric-width> <options-map>)
      (czprint-fn fn-name <options-map>)

      (czprint nil :help)    ; for more information
      (czprint nil :explain) ; to see the current options-map"
       {:doc/format :markdown}
       [fn-name & rest]
       `(println (apply zprint-str-internal
                   {:parse-string? true, :color? true, :fn-name '~fn-name}
                   (get-fn-source '~fn-name)
                   ~@rest
                   []))))

;;
;; # File operations
;;

;;
;; ## Parse a comment to see if it has an options map in it
;;

(defn ^:no-doc get-options-from-comment
  "s is string containing a comment.  See if it starts out ;!zprint
  (with any number of ';' allowed), and if it does, attempt to parse
  it as an options-map.  Return [options error-str] with only options
  populated if it works, and throw an exception if it doesn't work.
  Use sci/eval-string to create sandboxed functions if any exist in
  the options map."
  [zprint-num s]
  (let [s-onesemi (clojure.string/replace s #"^;+" ";")
        comment-split (clojure.string/split s-onesemi #"^;!zprint ")]
    (when-let [possible-options (second comment-split)]
      (try [(sci/eval-string possible-options) nil]
           (catch #?(:clj Exception
                     :cljs :default)
             e
             ; If it doesn't work, don't return an error-str, throw an
             ; Exception!
             (throw (#?(:clj Exception.
                        :cljs js/Error.)
                     (str "Unable to create zprint options-map from: '"
                            possible-options
                          "' found in !zprint directive number: " zprint-num
                          " because: " e))))))))

;;
;; ## Process the sequences of forms in a file
;;

(defn ^:no-doc spaces?
  "If a string is all spaces and has at least one space, 
  returns the count of the spaces, otherwise nil."
  [s]
  (let [len (count s)]
    (if (zero? len) nil (when (empty? (clojure.string/replace s " " "")) len))))

;!zprint {:format :next :vector {:wrap? false}}

(defn ^:no-doc process-form
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
        internal-options (if (empty? next-options)
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

(defn ^:no-doc interpose-w-comment
  "A comment aware interpose. It takes a seq of strings, leaves out
  empty strings, and interposes interpose-str between everything,
  except after a comment.  After a comment, it will interpose a
  single newline if there were no blank lines between the comment
  and a following comment. If there was any number of blank lines
  after a comment, it will interpose interpose-comment-str before
  the next (non-comment) element. Output is a vector of strings."
  [seq-of-strings interpose-str]
  #_(prn "seq-of-strings" seq-of-strings)
  (if (empty? seq-of-strings)
    []
    (loop [sos seq-of-strings
           previous-comment? nil
           start-interpolating? nil
           out []]
      (if-not sos
        out
        (let [s (first sos)
              empty-string? (empty? s)
              ; comments must start with ; since parsing removes leading spaces
              comment? (clojure.string/starts-with? s ";")]
          #_(prn "s:" s "empty-string?" empty-string? "comment?" comment?)
          (recur (next sos)
                 comment? ; previous-comment?
                 (or (not empty-string?) start-interpolating?) ; start-inter?
                 (cond empty-string? out
                       (not start-interpolating?) (conj out s)
                       previous-comment? (-> out
                                             (conj "\n")
                                             (conj s))
                       :else (-> out
                                 (conj interpose-str)
                                 (conj s)))))))))

(defn ^:no-doc remove-shebang
  "Given a string which contains multiple lines, check the first line to
  see if it begins with a shebang, that is: #!.  If it does, remove that
  line and return it as the shebang, else shebang is nil.  Returns:
  [shebang filestring]"
  [filestring]
  (if (clojure.string/starts-with? filestring "#!")
    (clojure.string/split filestring #"\n" 2)
    [nil filestring]))

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

;; An example of what is going on here with the reductions:
;;
;;zprint.core=> (czprint sozf)
;;([{} "" 0 0]
;; [{} "" 0 0]
;; [{} "(ns foo)" 0 0]
;; [{} "" 0 0]
;; [{} ";abc" 0 0]
;; [{:format :next, :width 10} ";!zprint {:format :next :width 10}" 0 1]
;; [{:format :next, :width 10} ";def" 0 1]
;; [{} "(defn baz\n  [])" 0 1]
;; [{} "" 0 1])
;;nil
;;
;; Note that (defn baz []) came out on two lines because of {:width 10}

(defn ^:no-doc process-multiple-forms
  "Take a sequence of forms (which are zippers of the elements of
  a file or a string containing multiple forms somewhere), and not 
  only format them for output but also handle comments containing 
  ;!zprint that affect the options-map throughout the processing."
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
      (apply str (interpose-w-comment seq-of-strings interpose-str))
      (apply str seq-of-strings))))

;;
;; ## Process an entire file
;;

(defn zprint-file-str
  "Take a string, which typically holds the contents of an entire
  file, but doesn't have to, and format the entire string, outputing
  a formatted string.  It respects white space at the top level,
  while ignoring it within all top level forms (unless
  :indent-only, :respect-bl, or :respect-nl are used).
  It allows comments at the top
  level, as well as in function definitions, and also supports
  ;!zprint directives at the top level. See File Comment API for
  information on ;!zprint directives. zprint-specifier is the thing
  that will be used in messages if errors are detected in ;!zprint
  directives, so it should identify the file (or other element) to
  allow the user to find the problem. new-options is an options-map 
  containing options to be used when doing the formatting (and will 
  be overriddden by any options in ;!zprint directives).  doc-str is 
  an optional string to be used when setting the new-options into the 
  configuration."
  ([file-str zprint-specifier new-options doc-str]
   (let [original-options (get-options)
         original-doc-map (get-explained-all-options)]
     (when new-options (set-options! new-options doc-str))
     (try
       ; Make sure to get trailing newlines by using -1
       (let [; If the filestring starts with #!, remove it and save it
             [shebang file-str] (remove-shebang file-str)
             [line-ending lines] (determine-ending-split-lines file-str)
             lines (if (:expand? (:tab (get-options)))
                     (map (partial expand-tabs (:size (:tab (get-options))))
                       lines)
                     lines)
             ; Glue lines back together with \n line ending, to work around
             ; rewrite-clj bug with \r\n endings on comments.  Otherwise,
             ; the rewrite-clj parse would "convert" them all to \n for us,
             ; which is really what we need anyway.
             filestring (clojure.string/join "\n" lines)
             range-start (:start (:range (:input (get-options))))
             ; If shebang correct for one less line
             range-start (when range-start
                           (if shebang (dec range-start) range-start))
             range-end (:end (:range (:input (get-options))))
             ; If shebang correct for one less line
             range-end (when range-end (if shebang (dec range-end) range-end))
             _ (when (or range-start range-end)
                 (dbg new-options
                      "zprint-file-str: range-start:" range-start
                      "range-end:" range-end))
             ; If we are doing ranges, we really care about lines being a
             ; vector
             lines (if (and (or range-start range-end) (not (vector? lines)))
                     (into [] lines)
                     lines)
             [actual-start actual-end] (when (or range-start range-end)
                                         (expand-range-to-top-level
                                           filestring
                                           lines
                                           range-start
                                           range-end
                                           (:dbg? (get-options))))
             _ (when (or range-start range-end)
                 (dbg new-options
                      "zprint-file-str: actual-start:" actual-start
                      "actual-end:" actual-end))
             [before-lines range after-lines]
               (when (and actual-start actual-end)
                 (split-out-range lines actual-start actual-end))
             filestring (if range (clojure.string/join "\n" range) filestring)
             _ (when (and actual-start actual-end)
                 (dbg-pr new-options
                         "zprint-file-str: lines count:" (count lines)
                         "before count:" (count before-lines)
                         "range count:" (count range)
                         "after count:" (count after-lines)
                         "range:" range))
             ends-with-nl? (clojure.string/ends-with? file-str "\n")
             forms (edn* (p/parse-string-all filestring))
             pmf-options {:process-bang-zprint? true}
             pmf-options (if (:interpose (:parse (get-options)))
                           (assoc pmf-options :trim-comments? true)
                           pmf-options)
             pmf-options (if shebang
                           (merge-deep pmf-options
                                       (:more-options (:script (get-options))))
                           pmf-options)
             #_(def fileforms (zmap-all identity forms))
             out-str (process-multiple-forms pmf-options
                                             zprint-str-internal
                                             zprint-specifier
                                             forms)
             _ (dbg-pr new-options "zprint-file-str: out-str:" out-str)
             ; If we did a range, insert the formatted range back into
             ; the before and after lines.
             out-str (if range
                       (reassemble-range before-lines out-str after-lines)
                       out-str)
             out-str (if shebang (str shebang "\n" out-str) out-str)
             out-str (if (and ends-with-nl?
                              (not (clojure.string/ends-with? out-str "\n")))
                       (str out-str "\n")
                       out-str)]
         (if (= line-ending "\n")
           out-str
           (clojure.string/replace out-str "\n" line-ending)))
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
  repspectively. ;!zprint directives are recognized in the file.
  See the File Comment API for information on ;!zprint directives.
  file-name is a string, and is usually the name of the input file
  but could be anything to help identify the input file when errors
  in ;!zprint directives are reported.  options is an options-map
  containing any additional options to be used for this operation, 
  and will be overridden by any options specified in ;!zprint directives."
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

(defn ^:no-doc format-spec
  "Take a spec and a key, and format the output as a string. Width is
  because the width isn't really (:width options)."
  [options describe-fn fn-spec indent key]
  (when-let [key-spec (get fn-spec key)]
    (let [key-str (str (name key) ": ")
          total-indent (+ (count key-str) indent)
          ; leave room for double-quote at the end
          width (dec (- (:width options) total-indent))
          key-spec-data (describe-fn key-spec)
          spec-str (zprint-str key-spec-data width)
          spec-no-nl (clojure.string/split spec-str #"\n")
          spec-shift-right
            (apply str (interpose (str "\n" (blanks total-indent)) spec-no-nl))]
      (str (blanks indent) key-str spec-shift-right))))

#?(:clj
     (defn ^:no-doc get-docstring-spec
       "Given a function name (which, if used directly, needs to be quoted)
  return a string which contains the spec information that could go
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