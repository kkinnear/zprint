(ns zprint.lumo
  (:require [lumo.core :refer [*command-line-args*]]
            [cljs.nodejs :as nodejs]
            [clojure.string :as str]
            [cljs.reader :refer [read-string]]
            [zprint.core :refer
             [zprint-str czprint zprint-file-str set-options!]]))

(js/require "process")
(js/require "fs")

;;
;; This is the root namespace to run zprint with planck
;;
;; # How to use this:
;;
;; lumo -K -c `lein classpath` -r
;;
;; Also:
;;
;; lumo -K -c `lein classpath` -r  '{<options-map>}'
;;
;; The first time you run this, it will take maybe two minutes
;; to create the cache.
;; 
;;
;; => (require 'zprint.lumo)
;; => (in-ns 'zprint.lumo)
;;
;;
;; As a script:
;;
;; lumo -k <cache-dir> -c <dir>:<dir>:...:<dir> -m zprint.planck
;;
;; The first time you run this, it will take maybe two minutes
;; to create the cache.
;;
;; where:
;;  -k <cache-dir> is a directory where all of the .js output goes
;;  -c <classpath> is the classpath.  Using `lein classpath` is good
;;     for development, but slow for actual use.  Really the whole
;;     thing in the script file.  There are only two jars necesary:
;;
;;       zprint-0.2.n.jar
;;       rewrite-cljs-0.4.3.jar
;;

;;
;; # Print function definitions
;;
;; Can we make czprint-fn work for planck?  This requires a quoted name.
;;

#_(defn czprint-fn
    "Do the czprint-fn thing for planck."
    [fn-name]
    (zprint.core/czprint (planck.repl/fetch-source
                           (planck.repl/get-var (planck.repl/get-aenv) fn-name))
                         {:parse-string? true}))

;;
;; # File Descriptors
;; 

(def *stdin* 0)
(def *stdout* 1)

;;
;; # File Functions
;;

(defn slurp
  "Read from a file or file descriptor."
  [file]
  (js/fs.readFileSync file "utf8"))

(defn spit
  "Write to a file or file descriptor."
  [file data]
  (js/fs.writeSync file data))

;;
;; # Environment
;;

(defn get-zprintrc-file-str
  "Look up 'HOME' in the environment, and build a string to find ~/.zprintrc"
  []
  (let [home-str js/process.env.HOME]
    (when home-str (str home-str "/.zprintrc"))))

(defn set-zprintrc!
  "Read in any ~/.zprintrc file and set it in the options."
  []
  (let [zprintrc-file-str (get-zprintrc-file-str)]
    (try (when zprintrc-file-str
           (let [zprintrc-str (slurp zprintrc-file-str)]
             (when zprintrc-str
               (set-options! (read-string zprintrc-str)
                             (str "File: " zprintrc-file-str)))))
         (catch :default e
           (str "Failed to use .zprintrc file: '"
                zprintrc-file-str
                "' because: "
                e
                ".")))))


;;
;; # Main
;;

(defn -main
  "Read a file from stdin, format it, and write it to sdtout."
  []
  (set-zprintrc!)
  (let [options (first *command-line-args*)
        _ (when options
            (try (set-options! (read-string options))
                 (catch :default e
                   (println "Failed to use command line options: '"
                            options
                            "' because: "
                            e
                            "."))))
        in-str (slurp *stdin*)
        ; _ (println "-main:" in-str)
        fmt-str (try (zprint-file-str in-str "<stdin>")
                     (catch :default e
                       (str "Failed to zprint: " e "\n" in-str)))]
    (spit *stdout* fmt-str)))
