(ns ^:no-doc zprint.main
  (:require ;[clojure.string :as str]
            [zprint.core :refer
             [zprint-str czprint zprint-file-str set-options!]])
  #?(:clj (:gen-class)))

;;
;; This is the root namespace to run zprint as an uberjar
;;
;; # How to use this:
;;
;; java -jar zprint-filter {<options-map-if-any>} <infile >outfile
;;


;;
;; # Main
;;

(defn -main
  "Read a file from stdin, format it, and write it to sdtout.  Do
  not load any additional libraries for configuration, and process
  as fast as we can using :parallel?"
  [& args]
  (set-options! {:additional-libraries? false, :parallel? true})
  (let [options (first args)
        _ (when (and options (not (clojure.string/blank? options)))
            (try (set-options! (read-string options))
                 (catch Exception e
                   (println "Failed to use command line options: '"
                            options
                            "' because: "
                            e
                            "."))))
        in-str (slurp *in*)
        ; _ (println "-main:" in-str)
        fmt-str (try (zprint-file-str in-str "<stdin>")
                     (catch Exception e
                       (str "Failed to zprint: " e "\n" in-str)))]
    ;
    ; We used to do this: (spit *out* fmt-str) and it worked fine
    ; in the uberjar, presumably because the JVM eats any errors on
    ; close when it is exiting.  But when using clj, apparently it
    ; will close stdout, and if there is an error, it won't eat it
    ; but will let it bubble up to the top level.
    ;
    ; Now, we write and flush explicitly, sidestepping that particular
    ; problem. In part because there are plenty of folks that say that
    ; closing stdout is a bad idea.
    ;
    (.write *out* fmt-str)
    (.flush *out*)
    ;
    ; Since we did :parallel? we need to shut down the pmap threadpool
    ; so the process will end!
    (shutdown-agents)))
