(ns zprint.planck
  (:require [planck.core :refer [slurp spit]]
            [cljs.core :refer [*command-line-args*]]
            [planck.shell :refer [sh]]
            [clojure.string :as str]
            [cljs.reader :refer [read-string]]
            planck.repl
            planck.io
            [zprint.core :refer
             [zprint-str czprint zprint-file-str set-options!]]))

;;
;; This is the root namespace to run zprint with planck
;;
;; # How to use this:
;;
;; planck -K -c `lein classpath` -r
;;
;; Also:
;;
;; planck -K -c `lein classpath` -r  '{<options-map>}'
;;
;; The first time you run this, it will take maybe two minutes
;; to create the cache.
;; 
;;
;; => (require 'zprint.planck)
;; => (in-ns 'zprint.planck)
;;
;;
;; As a script:
;;
;; planck -k <cache-dir> -c <dir>:<dir>:...:<dir> -m zprint.planck
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

(defn czprint-fn
  "Do the czprint-fn thing for planck."
  [fn-name]
  (zprint.core/czprint (planck.repl/fetch-source
                         (planck.repl/get-var (planck.repl/get-aenv) fn-name))
                       {:parse-string? true}))

;;
;; # Environment
;;

(defn get-env
  "Run a shell to get the environment, and turn it into a cljs map."
  []
  (into {}
        (map (fn [line] (str/split line #"=" 2))
          (-> (:out (sh "env"))
              (str/split #"\n")))))

(defn get-zprintrc-file-str
  "Look up 'HOME' in the environment, and build a string to find ~/.zprintrc"
  []
  (let [home-str ((get-env) "HOME")]
    (when home-str (str home-str "/.zprintrc"))))

(defn set-zprintrc!
  "Read in any ~/.zprintrc file and set it in the options."
  []
  (let [zprintrc-file-str (get-zprintrc-file-str)]
    (try (when zprintrc-file-str
           (let [zprintrc-str (slurp zprintrc-file-str)]
             (when zprintrc-str (set-options! (read-string zprintrc-str)))))
         (catch :default e
           (str "Failed to utilize .zprintrc file: '"
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
        _ (when options (set-options! (read-string options)))
        in-str (slurp planck.core/*in*)
        ; _ (println "-main:" in-str)
        fmt-str (try (zprint-file-str in-str "<stdin>")
                     (catch :default e
                       (str "Failed to zprint: " e "\n" in-str)))]
    (spit *out* fmt-str)))
