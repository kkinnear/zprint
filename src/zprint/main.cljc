(ns ^:no-doc zprint.main
  (:require ;[clojure.string :as str]
    [zprint.core :refer [zprint-str czprint zprint-file-str set-options! load-options!]]
    [zprint.config :refer [get-options get-explained-options]])
  #?(:clj (:gen-class)))

;;
;; This is the root namespace to run zprint as an uberjar
;;
;; # How to use this:
;;
;; java -jar zprint-filter {<options-map-if-any>} <infile >outfile
;;

;!zprint {:vector {:wrap? false}}

(defn vec-str-to-str
  "Take a vector of strings and concatenate them into one string with
  newlines between them."
  [vec-str]
  (apply str (interpose "\n" vec-str)))

(def main-help-str
  (vec-str-to-str
    [(:version (get-options))
     ""
     " zprint <options-map> <input-file >output-file"
     " zprint <switches <input-file >output-file"
     ""
     " Where zprint is any of:"
     ""
     (str "  " (clojure.string/replace (:version (get-options)) "-" "m-"))
     (str "  " (clojure.string/replace (:version (get-options)) "-" "l-"))
     (str "  "
          "java -jar zprint-filter-"
          (second (clojure.string/split (:version (get-options)) #"-")))
     ""
     " <options-map> is a Clojure map containing zprint options."
     "               Note that since it contains spaces, it must be"
     "               wrapped in quotes, for example:"
     "               '{:width 120}'"
     ""
     "               Use the -e switch to see the total options"
     "               map, which will show you what is configurable."
     ""
     " <switches> may be any single one of:"
     ""
     "  -d       --default      Accept no configuration input."
     "  -h       --help         Output this help text."
     "  -u       --url     URL  Load options from URL."
     "  -v       --version      Output the version of zprint."
     "  -e       --explain      Output configuration, showing where"
     "                          non-default values (if any) came from."
     ""
     " You can have either an <options-map> or <switches>, but not both!"
     ""]))


;;
;; # Main
;;

(defn -main
  "Read a file from stdin, format it, and write it to sdtout.  
  Process as fast as we can using :parallel?"
  [& args]
  ; Turn off multi-zprint locking since graalvm can't handle it, and
  ; we only do one zprint at a time here in the uberjar.
  (zprint.redef/remove-locking)
  (let [options (first args)
        ; Some people wanted a zprint that didn't take configuration.
        ; If you say "--default" or "-d", that is what you get.
        ; --default or -s means that you get no configuration read from
        ; $HOME/.zprintrc or anywhere else.  You get the defaults.
        ;
        ; Basic support for "-s" or "--standard" is baked in, but
        ; not turned on.
        version? (or (= options "--version") (= options "-v"))
        help? (or (= options "--help") (= options "-h"))
        explain? (or (= options "--explain") (= options "-e"))
        format? (not (or version? help? explain?))
        default? (or (= options "--default") (= options "-d"))
        standard? (or (= options "--standard") (= options "-s"))
        url? #?(:clj (or (= options "--url") (= options "-u"))
                :default nil)
        [option-status option-stderr switch?]
          (if (and (not (clojure.string/blank? options))
                   (clojure.string/starts-with? options "-"))
            ; standard not yet implemented
            (cond
              (or version? help? default? #_standard? explain?) [0 nil true]
              url? #?(:clj (try (load-options! (second args))
                                [0 nil false]
                                (catch Exception e [1 (str e) false]))
                      :default nil)
              :else
                [1 (str "Unrecognized switch: '" options "'" "\n" main-help-str)
                 true])
            [0 nil false])
        _ (cond default? (set-options! {:configured? true, :parallel? true})
                standard?
                  (set-options!
                    {:configured? true, #_:style, #_:standard, :parallel? true})
                :else (set-options! {:parallel? true}))
        [option-status option-stderr]
          (if (and (not switch?)
                   (not url?)
                   format?
                   options
                   (not (clojure.string/blank? options)))
            (try [0 (set-options! (read-string options))]
                 (catch Exception e
                   [1
                    (str "Failed to use command line options: '"
                         options
                         "' because: "
                         e
                         ".")]))
            [option-status option-stderr])
        in-str (when (and (= option-status 0) format?) (slurp *in*))
        [format-status stdout-str format-stderr]
          (if (and (= option-status 0) format?)
            (try [0 (zprint-file-str in-str "<stdin>") nil]
                 (catch Exception e [1 in-str (str "Failed to zprint: " e)]))
            [0 nil])
        option-stderr (cond version? (:version (get-options))
                            help? main-help-str
                            explain? (zprint-str (get-explained-options))
                            :else option-stderr)
        exit-status (+ option-status format-status)
        stderr-str (cond (and option-stderr format-stderr)
                           (str option-stderr ", " format-stderr)
                         (not (or option-stderr format-stderr)) nil
                         :else (str option-stderr format-stderr))]
    ;
    ; We used to do this: (spit *out* fmt-str) and it worked fine
    ; in the uberjar, presumably because the JVM eats any errors on
    ; close when it is exiting.  But when using clj, spit will close
    ; stdout, and when clj closes stdout there is an error and it will
    ; bubble up to the top level.
    ;
    ; Now, we write and flush explicitly, sidestepping that particular
    ; problem. In part because there are plenty of folks that say that
    ; closing stdout is a bad idea.
    ;
    ; Getting this to work with graalvm was a pain.  In particular,
    ; w/out the (str ...) around fmt-str, graalvm would error out
    ; with an "unable to find a write function" error.  You could
    ; get around this by offering graalvm a reflectconfig file, but
    ; that is just one more file that someone needs to have to be
    ; able to make this work.  You could also get around this (probably)
    ; by type hinting fmt-str, though I didn't try that.
    ;
    ; Write whatever is supposed to go to stdout
    (let [^java.io.Writer w (clojure.java.io/writer *out*)]
      (.write w (str stdout-str))
      (.flush w))
    ; Write whatever is supposed to go to stderr, if any
    (when stderr-str
      (let [^java.io.Writer w (clojure.java.io/writer *err*)]
        (.write w (str stderr-str "\n"))
        (.flush w)))
    ; Since we did :parallel? we need to shut down the pmap threadpool
    ; so the process will end!
    (shutdown-agents)
    (System/exit exit-status)))
