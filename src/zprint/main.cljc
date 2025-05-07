;!zprint {:style [:sort-require :require-justify]}
(ns ^:no-doc zprint.main
  (:require ;[clojure.string :as str]
    [zprint.config :refer [config-and-validate-all get-explained-options
                           get-explained-set-options get-options merge-deep
                           sci-load-string select-op-options vec-str-to-str]]
    [zprint.core   :refer [czprint load-options! set-options! zprint-file-str
                           zprint-str]]
    #?@(:clj [[babashka.fs :refer [glob]]]))
  #?(:clj (:gen-class)))


;!zprint {:comment {:wrap? false} :reader-cond {:indent -3}}
;!zprint {:vector {:wrap? false}}

;;
;; This is the root namespace to run zprint as an uberjar
;;
;; # How to use this:
;;
;; java -jar zprint-filter {<options-map-if-any>} <infile >outfile
;;


(def main-help-str
  (vec-str-to-str
    [(:version (get-options))
     ""
     "zprint <options-map> <input-file >output-file"
     "zprint <switches> <input-file >output-file"
     "zprint -w input-and-output-file(s)"
     "zprint <options-map> -w input-and-output-file(s)"
     "zprint <switches> -w input-and-output-file(s)"
     ""
     "Where zprint is any of:"
     ""
     (str " " (clojure.string/replace (:version (get-options)) "-" "m-"))
     (str " " (clojure.string/replace (:version (get-options)) "-" "l-"))
     (str " "
          "java -jar zprint-filter-"
          (second (clojure.string/split (:version (get-options)) #"-")))
     ""
     "<options-map> is a Clojure map containing zprint options. Must be first."
     "              Note that since it contains spaces, it must be"
     "              wrapped in quotes, for example:"
     "              '{:width 120}'"
     ""
     "               Use the -e switch to see the total options"
     "               map, which will show you what is configurable."
     ""
     "<switches> which do no formatting, only one allowed:"
     ""
     " -h  --help         Output this help text."
     " -v  --version      Output the version of zprint."
     " -e  --explain      Output non-default configuration values, showing"
     "                    where any non-default values where set."
     "     --explain-all  Output full configuration, including all default"
     "                    values, while showing where non-default values set."
     ""
     "<switches> which control configuration, only one allowed:"
     ""
     " -d  --default      Accept no configuration input."
     #?@(:bb [""]
         :clj [" -u  --url URL      Load options from URL."
               "     --url-only URL Load only options found from URL,"
               "                    ignore all .zprintrc, .zprint.edn files."
               ""])
     "<switches> which process named files:  May follow a configuration switch"
     "                                       or an options map, but not both!"
     ""
     " -w  --write FILE                read, format, and write to FILE (or FILEs),"
     "                                 -w *.clj is supported."
     " -c  --check FILE                read and check format of FILE (or FILEs)"
     "                                 -c *.clj is supported."
     ""
     "Variations on -w, --write and -c, -check:"
     ""
     " -lw  --list-write      FILE  like -w, but indicate which files processed."
     " -fw  --formatted-write FILE  like -w, but indicate which files changed."
     " -sw  --summary-write   FILE  like -w, but include a summary of the number"
     "                              of files processed and how many required a"
     "                              format change, as well as any errors."
     ""
     "Combinations are allowed, w/write and c/check must always be last,"
     "and order matters for -- switches.  Examples:"
     ""
     "  -lfw, -lfsw, -fsw, -flw, -sflw, etc."
     "  --list-formatted-write, --list-formatted-summary-write, etc."
     ""
     "All combinations of -w and --write switches are also allowed "
     "with -c and --check switches:"
     ""
     "  -lfc, -lfsc, -fsc, -flc, -sflc, etc."
     "  --list-formatted-check, --list-formatted-summary-check, etc."
     ""
     "The -w, -c, and -e switches are the only switches where you may also"
     "have an options map!"
     ""]))

#?(:clj (defn write-to-stderr
          "Take a string, and write it to stderr."
          [s]
          (let [^java.io.Writer w (clojure.java.io/writer *err*)]
            (.write w (str s "\n"))
            (.flush w))))

#?(:clj
(defn format-file
  "Take a single argument, a filename string, and format it.  Read
  from the file, then rewrite the file with the formatted source.
  Return [exit-status required-format], where exit-status is
  either 0 if successful, or 1 if there were problems, and
  required-format is 0 if not, 1 if it did.  In the event of
  problems, don't change the contents of the file, but output text
  describing the problem to stderr."
  [filename check? list? formatted?]
  (let [[exit-status required-format in-str format-str format-stderr]
          (as-> [0 0 nil nil nil] running-status
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (when list? (write-to-stderr (str "Processing file " filename)))
              (try
                [0 0 (slurp filename) nil nil]
                (catch Exception e
                  [1
                   0
                   nil
                   nil
                   (str
                     "Failed to open file '" filename
                     "' because: "
                       (if (clojure.string/starts-with? filename "{")
                         (str e " NOTE: options map must come before switches!")
                         e))])))
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (if (= exit-status 1)
                ; We are done, move on
                running-status
                (try [0 0 in-str (zprint-file-str in-str filename) nil]
                     (catch Exception e
                       [1
                        0
                        nil
                        nil
                        (str "Failed to format file: " filename
                             " because " e)]))))
            ;
            ; See comment in -main about graalVM issues with this code
            ;
            ; Write whatever is supposed to go to stdout
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (if (= exit-status 1)
                ; We are done, move on
                running-status
                ; Did we actually change anything?
                (if (not= in-str format-str)
                  ; Yes, write it out
                  (if check?
                    (do (when formatted?
                          (write-to-stderr (str "Formatting required in file "
                                                filename)))
                        [0 1 nil nil nil])
                    (try (let [^java.io.Writer w (clojure.java.io/writer
                                                   filename)]
                           (.write w (str format-str))
                           (.flush w)
                           (.close w)
                           (when formatted?
                             (write-to-stderr (str "Formatted file " filename)))
                           [0 1 nil nil nil])
                         (catch Exception e
                           [1
                            1
                            nil
                            nil
                            (str "Failed to write output file: " filename
                                 " because " e)])))
                  ; No, we didn't actually change anything, just move on
                  [0 0 nil nil nil])))
            ; Write whatever is supposed to go to stderr, if anything
            (let [[exit-status required-format in-str format-str format-stderr]
                    running-status]
              (when format-stderr (write-to-stderr format-stderr))
              ; We don't change the running-status because we wrote to stderr
              running-status))]
    [exit-status required-format])))

(defn elements-before-last-switch
  "Given the args from the command line, find the last arg that
  starts with -, and return [count-of-elements-before-last-switch
  pointer-to-last-switch-in-the-seq-of-args]."
  [args]
  (let [arg-rev (reverse args)
        len (count args)
        switch-count (reduce #(if (clojure.string/starts-with? %2 "-")
                                (reduced %1)
                                (inc %1))
                       0
                       arg-rev)
        count-before-last-switch (if (= len switch-count)
                                   ; No switch
                                   nil
                                   (- len (inc switch-count)))]
    #_(println "len:" len
               "switch-count:" switch-count
               "count-before-last-switch" count-before-last-switch)
    count-before-last-switch))

(defn pair-sum "Sum two vector pairs." [[a b] [c d]] [(+ a c) (+ b d)])

#?(:clj
(defn parse-switches
  "Look for all switches, other than the ones that process named files.
  Return [[version? help? explain? explain-all? default? standard? url? url-only?]
          url-arg error-string]"
  [arg-seq check? write?]
  (let [arg-count (count arg-seq)
        check-or-write? (or check? write?)]
    (loop [args arg-seq
           [version?
            help?
            explain?
            explain-all?
            default?
            standard?
            url?
            url-only?]
             nil
           url-arg nil
           error-string nil]
      (if (or (nil? args) error-string)
        [[version?
          help?
          explain?
          explain-all?
          default?
          standard?
          url?
          url-only?]
         url-arg
         (if error-string
           error-string
           (cond (and (or version? help? default? standard?) (> arg-count 1))
                   (str "Switch '"
                        (first arg-seq)
                        "' cannot appear with any other switches or arguments.")
                 (and check-or-write? (or version? help? explain? explain-all?))
                   (str "Switch '" (first arg-seq)
                        "' cannot appear with any variant of "
                          (if check? "--check" "--write"))
                 (and url? url-only?)
                   "Switches url and url-only cannot appear together"
                 (and (or url? url-only?) (nil? url-arg))
                   (str "Switch "
                        (if url? "-u or --url" "--url-only")
                        " requires an argument")
                 :else nil))]
        (let [next-arg (clojure.string/trim (first args))
              valid-switch?
                ; No --url, -u, --url-only in babashka
                (#?(:bb #{"--version" "-v" "--help" "-h" "--explain" "-e"
                          "--explain-all" "--default" "-d" "--standard" "-s"}
                    :clj #{"--version" "-v" "--help" "-h" "--explain" "-e"
                           "--explain-all" "--default" "-d" "--standard" "-s"
                           "--url" "-u" "--url-only"})
                 next-arg)
              version? (or version? (= next-arg "--version") (= next-arg "-v"))
              help? (or help? (= next-arg "--help") (= next-arg "-h"))
              explain? (or explain? (= next-arg "--explain") (= next-arg "-e"))
              explain-all? (or explain-all? (= next-arg "--explain-all"))
              default? (or default? (= next-arg "--default") (= next-arg "-d"))
              standard?
                (or standard? (= next-arg "--standard") (= next-arg "-s"))
              url? #?(:clj (or url? (= next-arg "--url") (= next-arg "-u"))
                      :default nil)
              url-only? #?(:clj (or url-only? (= next-arg "--url-only"))
                           :default nil)
              url-arg (when (or url? url-only?) (second args))]
          (recur (if url-arg (nnext args) (next args))
                 [version?
                  help?
                  explain?
                  explain-all?
                  default?
                  standard?
                  url?
                  url-only?]
                 url-arg
                 (when (not valid-switch?)
                   (str "Unknown switch '" next-arg "'")))))))))

(defn bool-to-switch
  "Turn the booleans into switches."
  [[version? help? explain? explain-all? default? standard? url? url-only?]]
  (clojure.string/join ", "
                       (cond-> []
                         version? (conj "-v or --version")
                         help? (conj "-h or --help")
                         explain? (conj "-e or --explain")
                         explain-all? (conj "--explain-all")
                         default? (conj "-d or --default")
                         standard? (conj "-s or --standard")
                         url? (conj "-u or --url")
                         url-only? (conj "-url-only"))))

;;
;; # Main
;;

#?(:clj
(defn -main
  "Read a file from stdin, format it, and write it to sdtout.  
  Process as fast as we can using :parallel?"
  [& args]
  ; Turn off multi-zprint locking since graalvm can't handle it, and
  ; we only do one zprint at a time here in the uberjar.
  #?(:bb nil
     :clj (zprint.redef/remove-locking))
  (let [debug? (and args (= (first args) ":debug"))
        args (if debug? (next args) args)
        before-last-switch (elements-before-last-switch args)
        last-switch-in-seq (when before-last-switch
                             (nthnext args before-last-switch))
        possible-wc-switch (when before-last-switch
                             (clojure.string/trim (first last-switch-in-seq)))
        write-set #{"-w" "-lw" "-lfw" "-lsw" "-lfsw" "-lsfw" "-fw" "-fsw" "-sw"
                    "-flw" "-flsw" "-fslw" "-sfw" "-slw" "-sflw" "-slfw"
                    "--write" "--list-write" "--list-formatted-write"
                    "--list-format-write" "--list-formatted-summary-write"
                    "--formatted-write" "--list-format-summary-write"
                    "--format-write" "--formatted-summary-write"
                    "--summary-write" "--format-summary-write"}
        check-set #{"-c" "-lc" "-lfc" "-lsc" "-lfsc" "-lsfc" "-fc" "-fsc" "-sc"
                    "-flc" "-flsc" "-fslc" "-sfc" "-slc" "-sflc" "-slfc"
                    "--check" "--list-check" "--list-formatted-check"
                    "--list-format-check" "--list-formatted-summary-check"
                    "--formatted-check" "--list-format-summary-check"
                    "--format-check" "--formatted-summary-check"
                    "--format-summary-check" "--summary-check"}
        write? (write-set possible-wc-switch)
        check? (check-set possible-wc-switch)
        [list? formatted? summary?]
          (when (or write? check?)
            (if (clojure.string/starts-with? possible-wc-switch "--")
              [(clojure.string/includes? possible-wc-switch "list")
               (or (clojure.string/includes? possible-wc-switch "formatted")
                   (clojure.string/includes? possible-wc-switch "format"))
               (clojure.string/includes? possible-wc-switch "summary")]
              [(clojure.string/includes? possible-wc-switch "l")
               (clojure.string/includes? possible-wc-switch "f")
               (clojure.string/includes? possible-wc-switch "s")]))
        files (when (or write? check?) (next last-switch-in-seq))
        ; Remove write or check switch and files, if any
        args (if (or write? check?) (take before-last-switch args) args)
        args (if (empty? args) nil args)
        ; Do we have an options map?  If we have one, it must be first.
        possible-options (and args (clojure.string/trim (first args)))
        options (when (and possible-options
                           (clojure.string/starts-with? possible-options "{"))
                  possible-options)
        ; Remove options from args
        args (if options (next args) args)
        #_(prn "before-last-switch:" before-last-switch
               "possible-wc-switch:" possible-wc-switch
               "debug?" debug?
               "write?" write?
               "check?" check?
               "list?" list?
               "formatted?" formatted?
               "summary?" summary?
               "files:" files)
        #_(println)
        #_(prn "options:" options "args:" args)
        #_(println)
        [[version?
          help?
          explain?
          explain-all?
          default?
          standard?
          url?
          url-only?]
         url-arg
         error-string]
          (parse-switches args check? write?)
        #_(prn "bool-to-switch:" (bool-to-switch [version?
                                                  help?
                                                  explain?
                                                  explain-all?
                                                  default?
                                                  standard?
                                                  url?
                                                  url-only?])
               "url-arg:" url-arg
               "error-string:" error-string)
        [option-status exit-status option-stderr op-options]
          ; [option-status exit-status option-stderr op-options]
          ; options-status :incomplete
          ;                :complete
          ; exit-status 0 or 1 (only interesting if option-stderr non-nil)
          ; option-stderr string to output to stderr
          ; op-options are options which don't affect formatting but do
          ; affect how things operate.
          (as-> [:incomplete 0 nil {}] running-status
            ; Was there a problem parsing the switches?
            (if error-string [:complete 1 error-string {}] running-status)
            ; Handle having an options map options with incorrect switch
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                ; Do we have a options and a switch that doesn't work with it?
                ; options and explain? or url? are the only allowed switch
                ; combinations
                (if (and options
                         (or version? help? default? standard? url-only?))
                  ; No, we have something that appears to be options, and
                  ; a switch after which is not allowed with options.
                  [:complete
                   1
                   (str "Error processing command line '"
                        (clojure.string/join " " (into [options] args))
                        "', providing an options map and the switch "
                        (bool-to-switch
                          [version? help? nil default? standard? nil url-only?])
                        #_switches
                        " is not allowed!"
                        "\n"
                        main-help-str)
                   {}]
                  running-status)))
            ; Handle switches with extraneous data
            #_(let [[option-status exit-status option-stderr op-options]
                      running-status]
                (if (= option-status :complete)
                  running-status
                  ; Does this switch have too much data
                  (if nil
                    #_(and valid-switch? (not arg-count-ok?))
                    [:complete
                     1
                     (str "Error processing switch '"
                          #_switches
                          "', providing "
                          #_(dec arg-count)
                          " additional argument"
                          (if nil #_(= (dec arg-count) 1) "" "s")
                          " was incorrect!"
                          "\n"
                          main-help-str)
                     {}]
                    running-status)))
            ; Handle version and help switches
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if (or version? help?)
                  [:complete
                   0
                   (cond version? (:version (get-options))
                         help? main-help-str)
                   op-options]
                  running-status)))
            ; If we have options, get any operational-options out of them
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (or (= option-status :complete) (empty? options))
                running-status
                (try
                  ; The op-options are not validated here because they will
                  ; be validated when they are first used.
                  [:incomplete 0 nil (select-op-options (read-string options))]
                  (catch Exception e
                    [:complete
                     1
                     (str "Failed to use command line operational options: '"
                          options
                          "' because: "
                          e
                          ".")
                     {}]))))
            ; Check to see if existing shell-supplied files conflict with
            ; :files key in the command-line options map.
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              ; If we are already complete, or there aren't any command
              ; line options at all, then skip this.
              (if (or (= option-status :complete) (empty? options))
                running-status
                (if (and files (:files op-options))
                  [:complete
                   1
                   (str "Cannot have :files key in command-line options: '"
                        options
                        "' and also process files supplied by the shell!")
                   {}]
                  running-status)))
            ; Get all of the operational-options (op-options),
            ; merging in any that were on the command line
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              #_(println "pre config-and-validate-all"
                         "\noption-status:" option-status
                         "\nexit-status:" exit-status
                         "\noption-stderr:" option-stderr
                         "\nop-options:" op-options
                         "\nfiles:" files)
              (if (= option-status :complete)
                running-status
                ; You might think that this config-and-validate-all
                ; would configure everything, but it is just for
                ; operational options, and the new-map doesn't ever
                ; get used as the 'real' options map.
                (let [[new-map doc-map errors] (config-and-validate-all
                                                 op-options)
                      #_(println "post config-and-validate-all"
                                 "\nnew-map selections:" (select-op-options
                                                           new-map)
                                 "\ncolor:" (:color? (get-options))
                                 "\nerrors:" errors)
                      ; TODO: Why are we doing a merge-deep here, when we did
                      ; a config-and-validate-all above to put op-options
                      ; into new-map already?
                      ;
                      ; Add op-options to new-map, for a more complete set
                      new-map (select-op-options (merge-deep new-map
                                                             op-options))]
                  #_(println "post merge"
                             "\nnew-map:" new-map
                             "\ncolor:" (:color? (get-options))
                             "\nerrors:" errors)
                  ; Nothing here, but they must come out of the new-map, below.
                  ; Where did they come from?
                  #_(prn "** op-options:" op-options)
                  (if errors
                    [:complete 1 errors nil]
                    ; TODO: Why are we doing select-op-options here?
                    [:incomplete 0 nil (select-op-options new-map)]))))
            ; If --url try to load the args - along with other args
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if url?
                  #?(:clj (try (load-options! op-options url-arg)
                               [:incomplete 0 nil op-options]
                               (catch Exception e
                                 [:complete
                                  1
                                  (str "Unable to process --url switch value: '"
                                         (second args)
                                       "' because " e)
                                  op-options]))
                     :default running-status)
                  running-status)))
            ; If --url-only try to load the args - with no other options
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if url-only?
                  #?(:clj
                  (try (set-options! {:configured? true})
                       (load-options! op-options url-arg)
                       [:incomplete 0 nil op-options]
                       (catch Exception e
                         [:complete
                          1
                          (str "Unable to process --url-only switch value: '"
                                 (second args)
                               "' because " e)
                          op-options]))
                     :default running-status)
                  running-status)))
            ; if --default or --standard just use what we have, nothing else
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if (or default? standard?)
                  (do (cond default? (set-options! {:configured? true})
                            standard? (set-options! {:configured? true,
                                                     #_:style,
                                                     #_:standard}))
                      [:incomplete 0 nil op-options])
                  [:incomplete 0 nil op-options])))
            ; Configure non-operational options from files, and
            ; don't complete things as we will layer any command line
            ; options on top of these.  This is where all of the useful
            ; things from the various .zprintrc files get actually
            ; configured.
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (do (set-options! {} "" op-options)
                    [:incomplete 0 nil op-options])))
            ; Configure any command line options.  If we get here, that
            ; is all that is left to do -- all switches except -e have
            ; been handled at this point.
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (or (= option-status :complete) (empty? options))
                running-status
                ; Accept fns from command-line options map
                (try (set-options! (sci-load-string options)
                                   "command-line options"
                                   op-options)
                     [:incomplete 0 nil op-options]
                     (catch Exception e
                       [:complete
                        1
                        (str "Failed to use options map on the command line: '"
                             options
                             "' because: "
                             e
                             ".")
                        {}]))))
            ; We now have all options configured, so process -e to explain what
            ; we have for a configuration from the various command files
            ; and switches.
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if explain?
                  ; Force set-options to configure using op-options
                  ; in case they haven't configured before
                  [:complete
                   0
                   (zprint-str (get-explained-set-options))
                   op-options]
                  running-status)))
            ; We now have all options configured, so process -explain-all
            ; to explain what we have for a configuration from the various
            ; command files and switches.
            (let [[option-status exit-status option-stderr op-options]
                    running-status]
              (if (= option-status :complete)
                running-status
                (if explain-all?
                  ; Force set-options to configure using op-options
                  ; in case they havent' configured before
                  [:complete 0 (zprint-str (get-explained-options)) op-options]
                  running-status)))
            ; We could initialize using the op-options if we haven't done
            ; so already, but if we didn't have any command line options, then
            ; there are no op-options that matter.
          )
        #_(prn "option-status" option-status
               "exit-status" exit-status
               "option-stderr" option-stderr
               "op-options" op-options
               "color?" (:color? (get-options))
               "files:" files)
        [exit-status option-stderr files]
          ; If we already have files, or we are already done, skip this
          (if (or files option-stderr)
            [exit-status option-stderr files]
            ; We don't already have files, let's see if we can get some
            ; fome the :files key in the command-line options map
            (let [[exit-status option-stderr files]
                    (try [0
                          nil
                          (when (:files op-options)
                            (map str
                              (glob (or (:directory (:files op-options)) ".")
                                    (:glob (:files op-options)))))]
                         (catch Exception e
                           [1
                            (str
                              "Failed to successfully process :files key from "
                              "command-line options:'" {:files (:files
                                                                 op-options)}
                              "' because: " e)
                            nil]))]
              #_(prn "(:files op-options)" (:files op-options) "files:" files)
              (if (zero? exit-status)
                (if (empty? files)
                  ; We didn't get anything, were we supposed to?
                  (if (:files op-options)
                    ; Yes, we were
                    [1
                     (str "Unable to access files specified by: '"
                          {:files (:files op-options)}
                          "'")
                     nil]
                    ; No
                    [exit-status option-stderr files])
                  ; We got something, we assume it workes
                  (do #_(prn "here:" (:files op-options))
                      [exit-status option-stderr files]))
                [exit-status option-stderr files])))
        #_(prn "exit-status" exit-status
               "option-stderr" option-stderr
               "op-options" op-options)
        #_(prn "files:" files)]
    ; If option-stderr has something in it, we have either had some
    ; kind of a problem or we have processed the switch and it has
    ; output.  In either case, if option-stderr is non-nil we need
    ; to exit, and the exit status will be used.  Conversely, if
    ; option-stderr is nil, the exit status has no meaning.
    (if option-stderr
      (do (write-to-stderr option-stderr)
          (when (not debug?) (shutdown-agents) (System/exit exit-status)))
      ; Do whatever formatting we should do
      (if (or write? check?)
        ; We have one or more filenames to deal with
        (let [[total-exit-status total-required-format]
                (reduce #(pair-sum %1 (format-file %2 check? list? formatted?))
                  [0 0]
                  files)
              file-count (count files)]
          #_(prn "total-exit-status:" total-exit-status
                 "total-required-format:" total-required-format)
          (when summary?
            (write-to-stderr
              (str
                "Processed "
                file-count
                " file"
                (if (or (> file-count 1) (zero? file-count)) "s, " ", ")
                (if (pos? total-exit-status)
                  (str "with "
                       total-exit-status
                       " error"
                       (if (> total-exit-status 1) "s" "")
                       ", ")
                  "")
                (if (pos? total-required-format) total-required-format "none")
                " of which "
                (if check?
                  (if (< total-required-format 2) "requires" "require")
                  "required")
                " formatting.")))
          (when (not debug?)
            (shutdown-agents)
            (System/exit (if write?
                           (if (> total-exit-status 0) 1 0)
                           ; For check? we return the number of files that
                           ; needed formatting, even though we didn't
                           ; actually format them, and the errors as well
                           (+ total-required-format total-exit-status)))))
        ; We are operating on stdin and stdout
        (when (not debug?)
          (let [in-str (slurp *in*)
                [format-status stdout-str format-stderr]
                  (try [0 (zprint-file-str in-str "<stdin>") nil]
                       (catch Exception e
                         [1 in-str (str "Failed to zprint: " e)]))]
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
            (when format-stderr (write-to-stderr format-stderr))
            ; Since we did :parallel? we need to shut down the pmap threadpool
            ; so the process will end!
            (shutdown-agents)
            (System/exit format-status)))))))
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;
   ; End of #?(:clj ...)
   ;
   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  )

