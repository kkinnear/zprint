;!zprint {:style :require-justify :fn-map {"more-of" :arg1-pair}}
(ns zprint.test-config
  (:require
    [clojure.java.shell        :refer [sh]]
    [babashka.fs               :refer [copy cwd file move delete-if-exists
                                       delete-tree exists? expand-home
                                       set-posix-file-permissions]]
    [babashka.process          :refer [process destroy-tree]]
    [clojure.tools.cli         :refer [parse-opts]]
    [clojure.string]
    [clojure.data              :refer [diff]]
    [lambdaisland.ansi         :refer [text->hiccup]]
    [zprint.guide              :refer [areguide]]
    [zprint.core               :refer [czprint-str configure-all! zprint-str
                                       zprint-file-str set-options!]]
    [zprint.config             :refer [get-options get-explained-options]]
    [expectations.clojure.test :refer [defexpect expect use-fixtures]]))

;;
;; DEBUG flag
;;

(def debug? false)

;;
;; Test Numbering
;;

(def total-tests 50)

(def current-test 0)

(def tests-enabled (range 50))

;;
;; RC file handling
;;

(def rc-files ["~/.zprintrc" "~/.zprint.edn" ".zprintrc" ".zprint.edn"])

(defn save-rc-files
  "Save all files in file-vec, and return a vector containing
  file-vec and another vector with a true or nil in each position
  for the files in file-vec. The return is to be passed to
  restore-rc-files."
  [file-vec]
  (let [save (fn [f]
               (when (exists? (expand-home f))
                 (let [fpath (expand-home f)
                       fname (str fpath)
                       sname (str fname ".sav")]
                   (println (str "Saving " fname " as " sname))
                   (move fname sname {:replace-existing true})
                   true)))
        flags (mapv save file-vec)]
    (when (some identity flags)
      (println)
      (println (str "If you ^C out of this operation,"
                    " be sure to restore the above files!!!")))
    [file-vec flags]))

(defn restore-rc-files
  "Restore all zprintrc and zprint.edn files that were saved, and remove
  any that were not present to be saved.  If debug? is true, then move
  any existing files in file-vec to .old"
  [[file-vec flags]]
  (let [restore-or-remove
          (fn [f flag]
            (let [fname (str (expand-home f))]
              (when (and debug? (exists? fname))
                (println "Saving " fname " as " (str fname ".old"))
                (move fname (str fname ".old") {:replace-existing true}))
              (if flag
                (if (exists? (str fname ".sav"))
                  (let [sname (str fname ".sav")]
                    (println (str "\nRestoring " sname " to " fname))
                    (move sname fname {:replace-existing true})
                    true)
                  (println (str "Error restoring '" f "' because " fname ".sav")
                           " does not exist!"))
                ; Remove any file that might be there, since
                ; there wasn't one there when we saved files.
                (delete-tree fname))))]
    ; Restore in the opposite order as the save
    (mapv restore-or-remove (reverse file-vec) (reverse flags))))

(use-fixtures :once
              {:before #(def rc-info (save-rc-files rc-files)),
               :after #(restore-rc-files rc-info)})
;;
;; Utility Functions
;;

(defn replace-fns
  "Take a string, and replace all #<Fn...> with nil. Go across lines, and not
  greedy."
  [s]
  (clojure.string/replace s #"\#\<Fn(?s)(.*?)\>" "nil"))

(defn line-count "Count lines in a string." [s] (inc (count (re-seq #"\n" s))))

(defn remove-color
  [text]
  (if (string? text) (apply str (mapv last (text->hiccup text))) text))

(declare process-map)

(defn do-sh
  "Takes the process-map, a vector of string arguments, and an optional
  string file name to bind to stdin.  Returns the entire result map,
  with :exit, :out and :err keys."
  ([pm arg-vec input-file]
   (let [cwd (str (cwd))
         arg-vec (conj arg-vec :dir cwd)
         pre-arg-vec (:pre-args pm)
         arg-vec (if pre-arg-vec (apply conj pre-arg-vec arg-vec) arg-vec)
         _ (when debug?
             (prn "do-sh: executable:" (:executable pm)
                  "arg-vec:" arg-vec
                  "input-file" input-file))
         result-map (if input-file
                      (apply sh
                        (:executable pm)
                        (conj arg-vec :in (file (str cwd "/" input-file))))
                      (apply sh (:executable pm) arg-vec))]
     result-map))
  ([pm arg-str] (do-sh pm arg-str nil)))

(defn do-command
  "Get the executable out of the global process-map, and takes a vector of 
  string arguments, and an optional string file name to bind to stdin.  
  Returns the entire result map, with :exit, :out and :err keys."
  ([arg-vec input-file] (do-sh process-map arg-vec input-file))
  ([arg-vec] (do-command arg-vec nil)))

;;
;; Test control
;;

(defn current-test-enabled?
  "Check the current-test value, and see if it is enabled in the global
  tests-enabled vector."
  []
  (some #{current-test} tests-enabled))

(defn display-test
  "Display the test information, including the number, after incrementing the
  current-test value (if requested)."
  ([increment? s]
   ;
   ; Increment a global variable containing the current test
   ;
   (when increment? (def current-test (inc current-test)))
   (when (current-test-enabled?)
     (println "........." (format "%2d" current-test) s)))
  ([s] (display-test true s)))

(def same-test nil)

(defmacro execute-test
  "Execute the current-test if it is enabled."
  [& rest]
  `(when (current-test-enabled?) ~@rest))


;;
;; Parse args
;;

(def cli-options
  ;; An option with a required argument
  [["-v" "--version VERSION" "zprint version number"]
   ["-s" "--style STYLE"
    "Style of run: uberjar, graalvm-mac-i, graalvm-mac-a, graalvm-linux, babashka"
    :validate
    [#(or (= % "uberjar")
          (= % "graalvm-mac-i")
          (= % "graalvm-mac-a")
          (= % "graalvm-linux")
          (= % "babashka"))
     "Style must be one of uberjar, graalvm-mac-i, graalvm-mac-a, graalvm-linux, babashka"]]
   ["-t" "--tests VECTOR-OF-TESTS" "A vector of test numbers to run"]
   ["-h" "--help"] ["-d" "--debug"]])

(def arg-options (:options (parse-opts *command-line-args* cli-options)))

(when debug? (prn "args:" *command-line-args*))

(when debug? (prn "arg-options:" arg-options))

(defn usage
  [options-summary]
  (->> ["test_config -- tests uberjar and pre-built graalvm binaries." ""
        "Usage: bb testconfig:bb -vths" "" "Options:" options-summary ""
        "Note: both -v and -s must appear if either do."]
       (clojure.string/join \newline)))

(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn load-string-protected
  "Use load-string, and return a string error if it doesn't work.
  [value error-string]"
  [s]
  (try (let [value (load-string s)] [value nil])
       (catch Exception e
         [nil (str "The string '" s "' failed to convert properly: " e)])))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary], :as arg-map}
          (parse-opts args cli-options)
        [tests error-str] (load-string-protected (:tests options))]
    (when (:debug options) (def debug? true))
    (when debug?
      (prn "Options:" options
           "Arguments:" arguments
           "Errors:" errors
           "tests:" tests
           "error-str:" error-str))
    (or
      (cond (:help options) ; help => exit OK with usage summary
              {:exit-message (usage summary), :ok? true}
            errors ; errors => exit with description of errors
              {:exit-message (error-msg errors)}
            ;; custom validation on arguments
            (not (and (:version options) (:style options)))
              {:exit-message (error-msg ["Both -v and -s are required!"])}
            error-str {:exit-message (error-msg [error-str])}
            (when tests
              (or (not (vector? tests))
                  (not (reduce #(and %1 (number? %2)) true tests))))
              {:exit-message (error-msg [(str
                                           "The value of -t (--tests): '"
                                           (:tests options)
                                           "' is not a vector of numbers!")])})
      (let [style (:style options)
            version (:version options)
            [executable pre-args tests-not-allowed]
              (cond (= style "uberjar")
                      [(str "/usr/bin/java")
                       ["-jar" (str "target/zprint-filter-" version)] nil]
                    (= style "graalvm-mac-i") [(str "./" "zprintmi-" version)
                                               nil nil]
                    (= style "graalvm-mac-a") [(str "./" "zprintma-" version)
                                               nil nil]
                    (= style "graalvm-linux") [(str "./" "zprintl-" version) nil
                                               nil]
                    (= style "babashka") [(str "./bb-1.3.183-SNAPSHOT")
                                          ["-Sforce" "zprint:src"] [1]])]
        (if (exists? executable)
          (do (when tests (def tests-enabled tests))
              (when tests-not-allowed
                (println (str "\nNOTE: Test"
                              (if (> (count tests-not-allowed) 1) "s " " ")
                              tests-not-allowed
                              " not supported when using "
                              style))
                (def tests-enabled
                  (seq (apply disj (set tests-enabled) tests-not-allowed))))
              (assoc arg-map
                :executable executable
                :pre-args pre-args))
          {:exit-message (str "The specified executable: "
                              executable
                              " could not be found!"),
           :ok? false})))))

(defn exit [status msg] (println msg) (System/exit status))

(defn process-args
  "Process arguments using clojure.tools.cli, and return a map which 
  contains an executable in :executable if it all work, or exit if there
  were errors or help requested."
  []
  (let [{:keys [action options executable exit-message ok?], :as process-map}
          (validate-args *command-line-args*)]
    (when exit-message (exit (if ok? 0 1) exit-message))
    process-map))

(defn clean-up-options-map-structure
  "Take an options map structure, and remove things that are altered because
  we are in this test, and add a correct version.  Also, remove all regexes."
  [m]
  (clojure.walk/postwalk #(if (= (type %) java.util.regex.Pattern) :regex %)
                         (-> m
                             (dissoc :parallel? :url :width)
                             #_(assoc :version (:version arg-options)))))

(defn clean-up-options-map-str
  "Take a string representation of an options map, and remove all of the
  specific '#<...>' things from it.  Return an options map structure."
  [s]
  (read-string (clojure.string/replace s #"(?s)\#\<.*?\>" ":a-fn-id")))

(defn clean-up-options-map
  "Take a string representation of an options map, and clean it up so that
  it will compare reasonably.  Return a structure for comparison."
  [s]
  (clean-up-options-map-structure (clean-up-options-map-str s)))

; Parse the command line, and save the results of the command line
; parsing for later

(def process-map (process-args))

(when debug? (prn "Process Map:" process-map))

;;
;; How to write a test
;;
;;  Generally, use more-of, and name the thing 'result-map'.
;;
;; In the result-map:
;;  (:exit result-map) is the exit status of the command
;;  (:out result-map) is a string which contains the value of stdout
;;  (:err result-map) is a string which contains the value of stderr
;;
;;
;; (expect
;;   (more-of
;;     result-map
;;     0 (:exit result-map)
;;     196 (count (:out result-map))
;;     70 (count (remove-color (:out result-map)))
;; "{:a :test,\n :bar :baz,\n :only :a,\n :test :foo,\n :this :is,\n :thix
;; :is}"
;;      (remove-color (:out result-map)))
;;  (do-command ["--url" "http://127.0.0.1:8081/test_config.edn"]
;;              "test_config.map.clj"))
;;
;; If you are counting lines or characters, the function 'remove-color'
;; can be helpful.
;;
;; Use 'spit' to create files:
;;
;; (spit (str (expand-home "~/.zprintrc"))
;;       "{:width 40 :url {:cache-dir \"configurldir\" :cache-secs 9}}" )

;;
;; Command
;;

(def command (:executable process-map))

;; 
;; Beginning of tests
;;

(defexpect config-tests

  ;
  ; Create new, narrow, ~/.zprintrc
  ; and color local ./.zprintrc
  ;

  (println "Creating testing files.\n")

  (spit (str (expand-home "~/.zprintrc"))
        "{:width 40 :url {:cache-dir \"configurldir\" :cache-secs 9}}")


  (spit "test_config.edn" "{:color? true}")

  (spit "test_config.map.clj"
        "{:this :is :a :test :thix :is :only :a :test :foo :bar :baz}")

  (spit "test_config.string.clj" "\"Hello World!\"")


  ; Remove any previously cached url

  (delete-tree (str (expand-home "~/.zprint/configurldir")))

  ;;
  ;; Do display here so that there will be a current test and so that
  ;; execute-test will work
  ;;

  ;----------------------------------------------------------------
  (display-test "--url")

  ;;
  ;; NOTE WELL: All of the URL tests are considered a single test from
  ;; the -t standpoint.  They depend on each other, and so you can't run
  ;; one without the others.  So they are all test #1, and may only be
  ;; selected as a unit.   They need to be test #1, because they are also
  ;; not operational in babashka, so when running in babashka, test #1
  ;; is always not allowed.
  ;;
  ;; Because of this, the web server starting and stopping can be done
  ;; as part of test #1.
  ;;


  ;;
  ;; Start web server for URL testing
  ;;

  (execute-test (def web-server
                  (process "./bbfiles/http-server.bb -p 8081 >/dev/null 2&>1"
                           {:shutdown destroy-tree})))

  ; Sleep for 5 seconds to let it come up

  (execute-test (Thread/sleep 5000))

  ;;
  ;; Debugging examples
  ;;


  #_(prn (do-sh "/bin/bash"
                ["-c" "cat /Users/kkinnear/.zprint/configurldir/127*"]))
  #_(prn (do-sh "/bin/bash" ["-c" "date +%s"]))
  #_(prn "..............")
  #_(prn (:pre-args process-map))
  #_(prn "..............")
  #_(prn (do-sh process-map
                ["--url" "http://127.0.0.1:8081/test_config.edn"]
                "test_config.map.clj"))

  ;;
  ;; --url
  ;;

  ;
  ; Try --url
  ;
  ; It should do the map in 6 lines in color.
  ;  6 lines from ~/.zprintrc width 40
  ;  color from config_test.edn
  ;  197 means 6 lines in color
  ;   71 means 6 lines w/out color
  ;  192 means 1 line w/color
  ;

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        196 (count (:out result-map))
        70 (count (remove-color (:out result-map)))
        "{:a :test,\n :bar :baz,\n :only :a,\n :test :foo,\n :this :is,\n :thix :is}"
          (remove-color (:out result-map)))
      (do-command ["--url" "http://127.0.0.1:8081/test_config.edn"]
                  "test_config.map.clj")))

  ;
  ; Remove file that is the target of --url, see if cache works
  ;

  ;----------------------------------------------------------------
  (display-test same-test "--url, with URL missing, see if cache works")

  (delete-tree "test_config.edn")

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        196 (count (:out result-map))
        70 (count (remove-color (:out result-map)))
        "{:a :test,\n :bar :baz,\n :only :a,\n :test :foo,\n :this :is,\n :thix :is}"
          (remove-color (:out result-map)))
      (do-command ["--url" "http://127.0.0.1:8081/test_config.edn"]
                  "test_config.map.clj")))

  ; Expire the cache -- wait 10 seconds

  ;----------------------------------------------------------------
  (display-test same-test "--url, with URL missing, see if expired cache works")

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        196 (count (:out result-map))
        70 (count (remove-color (:out result-map)))
        "WARN: using expired cache config for http://127.0.0.1:8081/test_config.edn after error: http://127.0.0.1:8081/test_config.edn\n"
          (:err result-map)
        "{:a :test,\n :bar :baz,\n :only :a,\n :test :foo,\n :this :is,\n :thix :is}"
          (remove-color (:out result-map)))
      (do (Thread/sleep 10000)
          (do-command ["--url" "http://127.0.0.1:8081/test_config.edn"]
                      "test_config.map.clj"))))

  ;----------------------------------------------------------------
  (display-test same-test "--url, with URL missing, and no cache")

  ; Remove cache file directory
  (delete-tree (str (expand-home "~/.zprint/configurldir")))

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "Unable to process --url switch value: 'http://127.0.0.1:8081/test_config.edn' because java.lang.Exception: ERROR: retrieving config from http://127.0.0.1:8081/test_config.edn: http://127.0.0.1:8081/test_config.edn\n"
          (:err result-map))
      (do-command ["--url" "http://127.0.0.1:8081/test_config.edn"]
                  "test_config.map.clj")))

  ;----------------------------------------------------------------
  (display-test same-test
                "--url, with URL not a valid Clojure map, and no cache")

  ;
  ; The URL is there, but not a valid Clojure map
  ;

  ; Make a test config file that is not a valid Clojure map

  (spit "test_config.edn" "{:color? true")

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "Unable to process --url switch value: 'http://127.0.0.1:8081/test_config.edn' because java.lang.Exception: ERROR: retrieving config from http://127.0.0.1:8081/test_config.edn: EOF while reading, expected } to match { at [1,1]\n"
          (:err result-map))
      (do-command ["--url" "http://127.0.0.1:8081/test_config.edn"]
                  "test_config.map.clj")))

  ;----------------------------------------------------------------
  (display-test same-test
                "--url, with URL not a valid zprint options map, and no cache")

  ;
  ; Valid Clojure map, but not valid zprint options map
  ;

  (spit "test_config.edn" "{:colorx? true}")

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "Unable to process --url switch value: 'http://127.0.0.1:8081/test_config.edn' because java.lang.Exception: ERROR: retrieving config from http://127.0.0.1:8081/test_config.edn: set-options! for options from http://127.0.0.1:8081/test_config.edn found these errors: In options from http://127.0.0.1:8081/test_config.edn, In the key-sequence [:colorx?] the key :colorx? was not recognized as valid!\n"
          (:err result-map))
      (do-command ["--url" "http://127.0.0.1:8081/test_config.edn"]
                  "test_config.map.clj")))


  ;----------------------------------------------------------------
  (display-test same-test "--url-only, with valid URL and no cache")

  ;
  ; Fix URL file
  ;

  (spit "test_config.edn" "{:color? true}")

  ;
  ; This should print on a single line, in color, since it is --url-only,
  ; which means that it ignores the ~/.zprintrc which has a :width of 40,
  ; causing multiple lines.

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        191 (count (:out result-map))
        65 (count (remove-color (:out result-map)))
        "{:a :test, :bar :baz, :only :a, :test :foo, :this :is, :thix :is}"
          (remove-color (:out result-map)))
      (do-command ["--url-only" "http://127.0.0.1:8081/test_config.edn"]
                  "test_config.map.clj")))


  ;----------------------------------------------------------------
  (display-test same-test "--url-only, with valid URL, file w/fn, and no cache")

  ;
  ; URL file with fn included, should pass as of 1.1
  ;

  ; Remove cache file directory
  (delete-tree (str (expand-home "~/.zprint/configurldir")))

  ; URL config file containing fn, which should now work since we use sci
  (spit "test_config.edn" "{:vector {:option-fn-first (fn [x y] {})}}")

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        65 (count (:out result-map))
        "{:a :test, :bar :baz, :only :a, :test :foo, :this :is, :thix :is}"
          (:out result-map))
      (do-command ["--url-only" "http://127.0.0.1:8081/test_config.edn"]
                  "test_config.map.clj")))

  ;; End of URL tests
  ;;
  ;; Stop web server, wait 2 seconds
  ;;

  (execute-test (destroy-tree web-server))

  (execute-test (Thread/sleep 2000))

  ;----------------------------------------------------------------
  (display-test "basic config test")

  ;
  ; Try basic config and see if that works.
  ;

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          14 (count (:out result-map))
                          "\"Hello World!\"" (:out result-map))
                        (do-command [] "test_config.string.clj")))


  ;----------------------------------------------------------------
  (display-test "basic config with comment in ~/.zprintrc test")

  ;
  ; Set up for comment in zprintrc test
  ;

  ;echo "{:width 40 :url {:cache-dir \"configurldir\" ;test
  ;:cache-secs 9}}" > ~/.zprintrc

  (spit (str (expand-home "~/.zprintrc"))
        "{:width 40 :url {:cache-dir \"configurldir\" ;test
:cache-secs 9}}")

  ;
  ; Try basic config with a comment in .zprintrc
  ;

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          14 (count (:out result-map))
                          "\"Hello World!\"" (:out result-map))
                        (do-command [] "test_config.string.clj")))

  ;----------------------------------------------------------------
  (display-test "{:cwd-zprintrc? true} on command line test")

  ;
  ; put zprintrc back w/out a comment
  ;

  (spit (str (expand-home "~/.zprintrc"))
        "{:width 40 :url {:cache-dir \"configurldir\" :cache-secs 9}}")

  ; Make a local .zprintrc

  (spit "./.zprintrc" "{:color? true}")

  ;
  ; See if we can get {:cwd-zprintrc? true} to be recognized on the command
  ; line
  ;

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          23 (count (:out result-map))
                          "\"Hello World!\"" (remove-color (:out result-map)))
                        (do-command ["{:cwd-zprintrc? true}"]
                                    "test_config.string.clj")))


  ;----------------------------------------------------------------
  (display-test "{:search-config? true} on command line test")

  ;
  ; See if we can get {:search-config? true} to be recognized on the
  ; command line
  ;

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          23 (count (:out result-map))
                          "\"Hello World!\"" (remove-color (:out result-map)))
                        (do-command ["{:search-config? true}"]
                                    "test_config.string.clj")))


  ;----------------------------------------------------------------
  (display-test "~/.zprintrc being used test")

  ;
  ; Is ~/.zprintrc being used at all?
  ;

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        70 (count (:out result-map))
        "{:a :test,\n :bar :baz,\n :only :a,\n :test :foo,\n :this :is,\n :thix :is}"
          (:out result-map))
      (do-command [] "test_config.map.clj")))


  ;----------------------------------------------------------------
  (display-test ".zprintrc being used test")

  ;
  ; Is .zprintrc being used at all?
  ;

  ; Use alternative input (it is restored after this test)

  (spit "test_config.string.clj" "(def a :b) (def c :d)")

  ; Make a local .zprintrc

  (spit "./.zprintrc" "{:parse {:interpose \"\nxxx\n\"}}")

  ; Set up a  ~/.zprintrc where it will read the local .zprintrc

  (spit (str (expand-home "~/.zprintrc")) "{:search-config? true}")

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          "(def a :b)\nxxx\n(def c :d)" (:out result-map))
                        (do-command [] "test_config.string.clj")))

  ; Restore test_config.string.clj

  (spit "test_config.string.clj" "\"Hello World!\"")

  ; Restore ~/.zprintrc

  (spit (str (expand-home "~/.zprintrc"))
        "{:width 40 :url {:cache-dir \"configurldir\" :cache-secs 9}}")

  ; Remove .zprintrc

  (delete-tree "./.zprintrc")

  ;----------------------------------------------------------------
  (display-test "-d test -- ~/.zprintrc should not be used")

  ;
  ; ~/.zprintrc should not be used now, since this is -d
  ;

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        65 (count (:out result-map))
        "{:a :test, :bar :baz, :only :a, :test :foo, :this :is, :thix :is}"
          (:out result-map))
      (do-command ["-d"] "test_config.map.clj")))

  ;----------------------------------------------------------------
  (display-test "bad ~/.zprintrc, invalid Clojure map test")

  ;
  ; Try bad basic ~/.zprintrc
  ;
  ;  This is not a valid Clojure map (missing last '}'")
  ;

  (spit (str (expand-home "~/.zprintrc"))
        "{:width 40 :url {:cache-dir \"configurldir\" :cache-secs 9}")

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "Unable to read configuration from file /Users/kkinnear/.zprintrc because clojure.lang.ExceptionInfo: EOF while reading"
          (re-find #".*EOF while reading" (:err result-map)))
      (do-command [] "test_config.string.clj")))

  ;----------------------------------------------------------------
  (display-test "invalid zprint options map in ~/.zprintrc")

  ;
  ; Try another bad basic ~/.zprintrc
  ;
  ;  This is a valid Clojure map, but not a valid zprint options map
  ;

  (spit (str (expand-home "~/.zprintrc"))
        "{:widthxxx 40 :url {:cache-dir \"configurldir\" :cache-secs 9}}")

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "In Home directory file: /Users/kkinnear/.zprintrc, In the key-sequence [:widthxxx] the key :widthxxx was not recognized as valid!\n"
          (:err result-map))
      (do-command [] "test_config.string.clj")))

  ;
  ; Put back a reasonable ~/.zprintrc
  ;

  (spit (str (expand-home "~/.zprintrc"))
        "{:width 40 :url {:cache-dir \"configurldir\" :cache-secs 9}}")

  ;----------------------------------------------------------------
  (display-test "-v test")

  ;
  ; Test -v switch (version output)
  ;

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          (str "zprint-" (:version (:options process-map)) "\n")
                            (:err result-map))
                        (do-command ["-v"])))

  ;----------------------------------------------------------------
  (display-test "-h test")

  ;
  ; Test -h switch (help output)
  ;
  ;   Of course, it needs to be fixed when we change the help output. That
  ;   said, we are just going to confirm the line count here, in part
  ;   because the version is also output, and trying to do a comparison
  ;   for all of that is tedious.
  ;
  ; Also, when running in babashka, the --url, -u, and --url-only switches
  ; are not supported, so that the number of lines is different.
  ;

  (execute-test (expect
                  (more-of result-map
                    0 (:exit result-map)
                    (if (= (:style (:options process-map)) "babashka") 67 70)
                      (line-count (:err result-map))
                    "" (:out result-map))
                  (do-command ["-h"])))


  ;----------------------------------------------------------------
  (display-test "--explain-all test")

  ;
  ; Test --explain-all switch (explain output)
  ;
  ;
  ; Note that --explain-all output goes to stderr, and is thus available in
  ; (:err result-map)
  ;

  ; Get zprint to know about any changes to the configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  ; Get the current options map in the form that it will show up with
  ; --explain-all

  (def options-map-str-internal (zprint-str (get-explained-options)))

  (execute-test
    (expect (more-of result-map
              0 (:exit result-map)
              nil (let [results (take 2
                                      (clojure.data/diff
                                        (clean-up-options-map (:err result-map))
                                        (clean-up-options-map
                                          options-map-str-internal)))]
                    (if (= results [nil nil]) nil (czprint-str results)))
              "" (:out result-map))
            (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test ":coerce-to-false test")

  ;
  ; Test :coerce-to-false
  ;

  ;
  ; Configure some strange .zprintrc files to test coerce-to-false
  ;

  (spit (str (expand-home "~/.zprintrc"))
        "{:cwd-zprintrc? 1 :coerce-to-false 0 :parallel? \"junk\"}")
  (spit "./.zprintrc" "{:parallel? :stuff :coerce-to-false :stuff :width 500}")


  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          false (:value (:parallel? (clean-up-options-map-str
                                                      (:err result-map))))
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "fn using (fn ...) in ~/.zprintrc file test")

  ;
  ; Configure some strange .zprintrc files to test fns in zprintrc files
  ;

  ; ~/.zprintrc gets a fn

  (spit (str (expand-home "~/.zprintrc"))
        "{:vector {:option-fn-first (fn [x y] {})}}")

  ; no ./.zprintrc

  (delete-tree "./.zprintrc")

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:set-by (:option-fn-first
                                            (:vector (clean-up-options-map-str
                                                       (:err result-map)))))
                                 "Home directory file")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "fn using #(...) in ~/.zprintrc file test")

  ; ~/.zprintrc gets a fn

  (spit (str (expand-home "~/.zprintrc"))
        "{:vector {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  ; no ./.zprintrc

  (delete-tree "./.zprintrc")

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:set-by (:option-fn-first
                                            (:vector (clean-up-options-map-str
                                                       (:err result-map)))))
                                 "Home directory file")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test
    "fn #(...) in .zprintrc, unused, no :cwd-zprintrc?/:search-config")

  ; .zprintrc gets a fn

  (spit "./.zprintrc"
        "{:vector {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  ; no ~/.zprintrc

  (delete-tree (str (expand-home "~/.zprintrc")))

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          nil (:set-by (:option-fn-first
                                         (:vector (clean-up-options-map-str
                                                    (:err result-map)))))
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "fn #(...) in .zprintrc found since :search-config?")

  ; .zprintrc gets a fn

  (spit "./.zprintrc"
        "{:vector {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  (spit (str (expand-home "~/.zprintrc")) "{:search-config? true}")


  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:set-by (:option-fn-first
                                            (:vector (clean-up-options-map-str
                                                       (:err result-map)))))
                                 ":search-config? file:")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "fn #(...) in .zprintrc found since :cwd-zprintrc?")

  ; .zprintrc gets a fn

  (spit "./.zprintrc"
        "{:vector {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  (spit (str (expand-home "~/.zprintrc")) "{:cwd-zprintrc? true}")

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:set-by (:option-fn-first
                                            (:vector (clean-up-options-map-str
                                                       (:err result-map)))))
                                 ":cwd-zprintrc? file:")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test
    "local .zprintrc file that does not parse found with :search-config?")

  ; .zprintrc gets a bad file


  (spit "./.zprintrc"
        "{:vector :stuff {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  (spit (str (expand-home "~/.zprintrc")) "{:search-config? true}")

  (execute-test (expect (more-of result-map
                          1 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:err result-map)
                                 "Unable to read configuration from file")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test
    "local .zprintrc file that does not parse found with :cwd-zprintrc?")

  ; .zprintrc gets a bad file

  (spit "./.zprintrc"
        "{:vector :stuff {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  (spit (str (expand-home "~/.zprintrc")) "{:cwd-zprintrc? true}")

  (execute-test (expect (more-of result-map
                          1 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:err result-map)
                                 "Unable to read configuration from file")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "local .zprintrc file with bad data found with :search-config?")

  ; .zprintrc gets a bad file

  (spit "./.zprintrc"
        "{:vectorxxx :stuff {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  (spit (str (expand-home "~/.zprintrc")) "{:search-config? true}")


  (execute-test (expect (more-of result-map
                          1 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:err result-map)
                                 "Unable to read configuration from file")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "local .zprintrc file with bad data found with :cwd-zprintrc?")

  ; .zprintrc gets a bad file

  (spit "./.zprintrc"
        "{:vectorxxx :stuff {:option-fn-first #(do (nil? %1) (nil? %2) {})}}")

  (spit (str (expand-home "~/.zprintrc")) "{:cwd-zprintrc? true}")

  (execute-test (expect (more-of result-map
                          1 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:err result-map)
                                 "Unable to read configuration from file")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "fn is valid in ./.zprintrc file test")

  ;
  ; Configure some strange .zprintrc files to test fns in zprintrc files
  ;

  ; ~/.zprintrc does not get a fn
  (spit (str (expand-home "~/.zprintrc"))
        "{:cwd-zprintrc? true :vector {:option-fn-first nil}}")

  ; .zprintrc gets a fn

  (spit "./.zprintrc" "{:vector {:option-fn-first (fn [x y] {})}}")

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          true (clojure.string/starts-with?
                                 (:set-by (:option-fn-first
                                            (:vector (clean-up-options-map-str
                                                       (:err result-map)))))
                                 ":cwd-zprintrc? file:")
                          "" (:out result-map))
                        (do-command ["--explain-all"])))

  ;----------------------------------------------------------------
  (display-test "fn using (fn [x y] ...) on command line test")

  ; ~/.zprintrc gets a fn

  (spit (str (expand-home "~/.zprintrc"))
        "{:vector {:option-fn-first (fn [x y] {})}}")

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          "\"Hello World!\"" (:out result-map)
                          "" (:err result-map))
                        (do-command
                          ["{:vector {:option-fn-first (fn [x y] {})}}"]
                          "test_config.string.clj")))

  ;----------------------------------------------------------------
  (display-test "fn using #(...) on command line test")


  ; No ~/.zprintrc

  (delete-tree (str (expand-home "~/.zprintrc")))

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  (execute-test
    (expect (more-of result-map
              0 (:exit result-map)
              "\"Hello World!\"" (:out result-map)
              "" (:err result-map))
            (do-command
              ["{:vector {:option-fn-first #(do (nil? %1) (nil? %2) {})}}"]
              "test_config.string.clj")))

  ;
  ; -w tests
  ;
  ; Note that these expect the filename as one of the arguments, not the
  ; "input file", when calling do-command.

  ;----------------------------------------------------------------
  (display-test "-w test with one file")

  ; This tests not only that the command doesn't blow up, but that
  ; the formatting actually matches that done by the library built
  ; in here.

  ; Put in a reasonable (and different than before) .zprintrc

  (spit (str (expand-home "~/.zprintrc")) "{:width 40 :style :justified}")

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  ; Note that {:keys [:replace-existing]} does not work in copy
  ; so we have to delete the file first!

  (delete-tree "test_config.map1.clj")

  (copy "test_config.map.clj" "test_config.map1.clj")
  (def test_config.map1.clj (slurp "test_config.map1.clj"))

  ; Get zprint to know about new configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          "" (:out result-map)
                          "" (:err result-map)
                          (zprint-file-str test_config.map1.clj "-w" {})
                            (slurp "test_config.map1.clj"))
                        (do-command ["-w" "test_config.map1.clj"])))

  (delete-tree "test_config.map1.clj")

  ;----------------------------------------------------------------
  (display-test "-w test with two files")

  ;
  ; -w with multiple files
  ;

  ; This tests not only that the command doesn't blow up, but that
  ; the formatting actually matches that done by the library built
  ; in here.

  ; Put in a reasonable (and different than before) .zprintrc

  (spit (str (expand-home "~/.zprintrc")) "{:width 40 :style :justified}")

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  ; Note that {:keys [:replace-existing]} does not work in copy
  ; so we have to delete the file(s) first!

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  (copy "test_config.map.clj" "test_config.map1.clj")
  (copy "test_config.map.clj" "test_config.map2.clj")

  (def test_config.map1.clj (slurp "test_config.map1.clj"))
  (def test_config.map2.clj (slurp "test_config.map2.clj"))

  ; Get zprint to know about new configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          "" (:out result-map)
                          "" (:err result-map)
                          (zprint-file-str test_config.map1.clj "-w" {})
                            (slurp "test_config.map1.clj")
                          (zprint-file-str test_config.map2.clj "-w" {})
                            (slurp "test_config.map2.clj"))
                        (do-command ["-w" "test_config.map1.clj"
                                     "test_config.map2.clj"])))

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  ;----------------------------------------------------------------
  (display-test "-w test with two files, one of which doesn't exist")

  ;
  ; -w with multiple files, one of which doesn't exist
  ;

  ; This tests not only that the command doesn't blow up, but that
  ; the formatting actually matches that done by the library built
  ; in here.

  ; Put in a reasonable (and different than before) .zprintrc

  (spit (str (expand-home "~/.zprintrc")) "{:width 40 :style :justified}")

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  ; Note that {:keys [:replace-existing]} does not work in copy
  ; so we have to delete the file(s) first!

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  (copy "test_config.map.clj" "test_config.map1.clj")

  ; No test_config.map2.clj

  (def test_config.map1.clj (slurp "test_config.map1.clj"))

  ; Get zprint to know about new configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "" (:out result-map)
        "Failed to open file test_config.map2.clj because: java.io.FileNotFoundException: test_config.map2.clj (No such file or directory)\n"
          (:err result-map)
        (zprint-file-str test_config.map1.clj "-w" {})
          (slurp "test_config.map1.clj"))
      (do-command ["-w" "test_config.map1.clj" "test_config.map2.clj"])))

  (delete-tree "test_config.map1.clj")

  ;----------------------------------------------------------------
  (display-test "-w test with two files, the first which is read-only")

  ;
  ; -w with two files, the first of which is read-only
  ;

  ; This tests not only that the command doesn't blow up, but that
  ; the formatting actually matches that done by the library built
  ; in here.

  ; Put in a reasonable (and different than before) .zprintrc

  (spit (str (expand-home "~/.zprintrc")) "{:width 40 :style :justified}")

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  ; Note that {:keys [:replace-existing]} does not work in copy
  ; so we have to delete the file(s) first!

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  (copy "test_config.map.clj" "test_config.map1.clj")
  (copy "test_config.map.clj" "test_config.map2.clj")

  ; Make test_config.map2.clj read-only -- maybe?

  (set-posix-file-permissions "test_config.map2.clj" "-wx------")

  (def test_config.map1.clj (slurp "test_config.map1.clj"))

  ; Get zprint to know about new configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "" (:out result-map)
        "Failed to open file test_config.map2.clj because: java.io.FileNotFoundException: test_config.map2.clj (Permission denied)\n"
          (:err result-map)
        (zprint-file-str test_config.map1.clj "-w" {})
          (slurp "test_config.map1.clj"))
      (do-command ["-w" "test_config.map1.clj" "test_config.map2.clj"])))

  (delete-tree "test_config.map1.clj")

  ;----------------------------------------------------------------
  (display-test
    "-w test with two files, the first which fails to format: bad !zprint map")

  ;
  ; -w with two files, the first of which fails to format
  ;

  ; This tests not only that the command doesn't blow up, but that
  ; the formatting actually matches that done by the library built
  ; in here.

  ; Put in a reasonable (and different than before) .zprintrc

  (spit (str (expand-home "~/.zprintrc")) "{:width 40 :style :justified}")

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  ; Note that {:keys [:replace-existing]} does not work in copy
  ; so we have to delete the file(s) first!

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  #_(copy "test_config.map.clj" "test_config.map1.clj")

  ; Create a bad test_config.map1.clj file

  (spit
    "test_config.map1.clj"
    ";!zprint {:format\n {:this :is :a :test :thix :is :only :a :test :foo :bar :baz}")

  (copy "test_config.map.clj" "test_config.map2.clj")

  (def test_config.map1.clj (slurp "test_config.map1.clj"))
  (def test_config.map2.clj (slurp "test_config.map2.clj"))

  ; Get zprint to know about new configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "" (:out result-map)
        #(clojure.string/starts-with?
           %
           "Failed to format file: test_config.map1.clj because java.lang.Exception: Unable to create zprint options-map from: '{:format\n' found in !zprint directive number: 1 because: clojure.lang.ExceptionInfo: EOF while reading")
          (:err result-map)
        (zprint-file-str test_config.map2.clj "-w" {})
          (slurp "test_config.map2.clj"))
      (do-command ["-w" "test_config.map1.clj" "test_config.map2.clj"])))

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  ;----------------------------------------------------------------
  (display-test
    "-w test with two files, the first which fails to format: bad !zprint key")

  ;
  ; -w with two files, the first of which fails to format with bad key in
  ; !zprint
  ;

  ; This tests not only that the command doesn't blow up, but that
  ; the formatting actually matches that done by the library built
  ; in here.

  ; Put in a reasonable (and different than before) .zprintrc

  (spit (str (expand-home "~/.zprintrc")) "{:width 40 :style :justified}")

  ; No ./.zprintrc

  (delete-tree "./.zprintrc")

  ; Note that {:keys [:replace-existing]} does not work in copy
  ; so we have to delete the file(s) first!

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  #_(copy "test_config.map.clj" "test_config.map1.clj")

  ; Create a different bad test_config.map1.clj file

  (spit
    "test_config.map1.clj"
    ";!zprint {:widthx 90}\n {:this :is :a :test :thix :is :only :a :test :foo :bar :baz}")


  (copy "test_config.map.clj" "test_config.map2.clj")

  (def test_config.map1.clj (slurp "test_config.map1.clj"))
  (def test_config.map2.clj (slurp "test_config.map2.clj"))

  ; Get zprint to know about new configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  (execute-test
    (expect
      (more-of result-map
        1 (:exit result-map)
        "" (:out result-map)
        "Failed to format file: test_config.map1.clj because java.lang.Exception: In ;!zprint number 1 in test_config.map1.clj, In the key-sequence [:widthx] the key :widthx was not recognized as valid!\n"
          (:err result-map)
        (zprint-file-str test_config.map2.clj "-w" {})
          (slurp "test_config.map2.clj"))
      (do-command ["-w" "test_config.map1.clj" "test_config.map2.clj"])))

  (delete-tree "test_config.map1.clj")
  (delete-tree "test_config.map2.clj")

  ;----------------------------------------------------------------
  (display-test "local .zprintrc having a fn that is used")

  ;
  ; local .zprintrc having a fn that is actually used
  ;

  (def mapp9
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    [longer route] 
        (handler this is \"a\" test \"this\" is \"only a\" test))
       ; How do comments work here?
       true (should be paired with true)
       false (should be paired with false)
       6 (should be paired with 6)
       \"string\" (should be paired with string)
       :post (m/app 
                    [a really long route] handler
                    [route] 
                    handler))")
  ; This is really what we are testing, does this kind of function work
  ; when given in the local zprintrc?

  (def local-zprintrc
    "{:fn-map {\"app\" [:none
   {:list {:constant-pair-min 1,
           :constant-pair-fn
               #(or (keyword? %)
                   (string? %)
                   (number? %)
                   (= true %)
                   (= false %)
                   (vector? %))},
    :pair {:justify? true},
    :next-inner
      {:list {:constant-pair-min 4,
              :constant-pair-fn nil},
       :pair {:justify? false}}}]}
       }")

  ; This tests not only that the command doesn't blow up, but that
  ; the formatting actually matches that done by the library built
  ; in here.


  ; Create testing files

  (spit (str (expand-home "~/.zprintrc")) "{:search-config? true}")
  (spit ".zprintrc" local-zprintrc)
  (spit "test_config.source.clj" mapp9)

  (def test_config.source.clj (slurp "test_config.source.clj"))

  ; Get zprint to know about new configure files
  ; and notice if it doesn't go well!

  (expect nil (configure-all!))

  (execute-test (expect
                  (more-of result-map
                    0 (:exit result-map)
                    80 (:width (get-options))
                    (zprint-file-str test_config.source.clj "fn" {:width 60})
                      (:out result-map)
                    "" (:err result-map))
                  (do-command ["{:width 60}"] "test_config.source.clj")))

  (delete-tree "test_config.source.clj")

  ;----------------------------------------------------------------
  (display-test "command line access to guide option-fn")

  ;
  ; command line access to guide option-fn that is actually used
  ;

  (delete-tree (str (expand-home "~/.zprintrc")))
  (delete-tree ".zprintrc")

  (expect nil (configure-all!))

  ; Create testing files

  (spit
    "test_config.source.clj"
    "(are [x y z] (= x y z) \n    3 (stuff y) (bother z) \n   4 (foo y) (bar z)) ")

  (def test_config.source.clj (slurp "test_config.source.clj"))

  (def test_config.out.clj
    "(are [x y z] (= x y z)\n  3 (stuff y) (bother z)\n  4 (foo y) (bar z))")

  #_(set-options!
      "{:width 80,
       :fn-map {\"are\" [:guided
                       {:list {:option-fn (partial areguide {:justify? false})},
                        :next-inner {:list {:option-fn nil}}}]}}")

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        80 (:width (get-options))
        (zprint-file-str
          test_config.source.clj
          "guide option-fn"
          {:width 80,
           :fn-map {"are" [:guided
                           {:list {:option-fn (partial areguide
                                                       {:justify? false})},
                            :next-inner {:list {:option-fn nil}}}]}})
          (:out result-map)
        test_config.out.clj (:out result-map)
        "" (:err result-map))
      (do-command
        ["{:width 80 :fn-map {\"are\" [:guided {:list {:option-fn (partial areguide {:justify? false})} :next-inner {:list {:option-fn nil}}}]}}"]
        "test_config.source.clj")))

  ; Clean up

  (expect nil (configure-all!))
  (delete-tree "test_config.source.clj")

  ;----------------------------------------------------------------
  (display-test
    "command line access to guide option-fn w/string to set-options!")

  ;
  ; command line access to guide option-fn that is actually used
  ;

  (delete-tree (str (expand-home "~/.zprintrc")))
  (delete-tree ".zprintrc")

  (expect nil (configure-all!))

  ; Create testing files

  (spit
    "test_config.source.clj"
    "(are [x y z] (= x y z) \n    3 (stuff y) (bother z) \n   4 (foo y) (bar z)) ")

  (def test_config.source.clj (slurp "test_config.source.clj"))

  (def test_config.out.clj
    "(are [x y z] (= x y z)\n  3 (stuff y) (bother z)\n  4 (foo y) (bar z))")

  (set-options!
    "{:width 80,
       :fn-map {\"are\" [:guided
                       {:list {:option-fn (partial areguide {:justify? false})},
                        :next-inner {:list {:option-fn nil}}}]}}")

  (execute-test
    (expect
      (more-of result-map
        0 (:exit result-map)
        80 (:width (get-options))
        (zprint-file-str test_config.source.clj "guide option-fn" {})
          (:out result-map)
        test_config.out.clj (:out result-map)
        "" (:err result-map))
      (do-command
        ["{:width 80 :fn-map {\"are\" [:guided {:list {:option-fn (partial areguide {:justify? false})} :next-inner {:list {:option-fn nil}}}]}}"]
        "test_config.source.clj")))

  ; Clean up

  (expect nil (configure-all!))
  (delete-tree "test_config.source.clj")

  ;----------------------------------------------------------------
  (display-test "html output")

  ;
  ; test basic html output
  ;

  ;
  ; Create necessary files
  ;

  (spit "test_config.source.clj" "(a :b \"c\")\n")

  (def test_config.out.clj
    "<p style=\"font-size:20px;font-family: Lucidia Concole, Courier, monospace\"><span style=\"color:black\">(a<br>&nbsp&nbsp:b<br>&nbsp&nbsp\"c\")</span><span style=\"color:black\"><br></span></p>")

  (execute-test (expect (more-of result-map
                          0 (:exit result-map)
                          80 (:width (get-options))
                          test_config.out.clj (:out result-map)
                          "" (:err result-map))
                        (do-command ["{:width 4 :output {:format :html}}"]
                                    "test_config.source.clj")))

  (delete-tree "test_config.source.clj")

  ;;
  ;; Clean Up
  ;;

  (delete-tree (str (expand-home "~/.zprintrc")))
  (delete-tree ".zprintrc")
  (delete-tree "test_config.edn")
  (delete-tree "test_config.map.clj")
  (delete-tree "test_config.string.clj")

)

