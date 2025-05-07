;!zprint {:style :require-justify :comment {:wrap? false} :reader-cond {:indent -3} :fn-map {"more-of" :arg1-pair "defexpect" [:arg1-body {:style :respect-nl :next-inner {:style :respect-nl-off}}] "do" [:none-body {:style :respect-nl :next-inner {:style :respect-nl-off}}] }}         

;;; This whole file is only interesting in Clojure, not Clojurescript, since
;;; we are testing the uberjar command switch processing and the file access

(ns zprint.main-test
  (:require
    [expectations.clojure.test #?(:clj :refer
                                  :cljs :refer-macros)
                                 [defexpect expect]]
    #?(:cljs [cljs.test :refer-macros [deftest is]])
    #?(:clj [clojure.test :refer [deftest is]])
    #?(:cljs [cljs.tools.reader :refer [read-string]])
    #?(:clj [zprint.main :refer [-main]])
    #?(:clj [clojure.java.shell :refer [sh]])
    [clojure.string :refer [split join]]
    [zprint.core    :refer [zprint-str set-options! czprint-str zprint-file-str
                            configure-all!]]
    [zprint.zprint  :refer [line-count]]
    [zprint.config  :as    config
                    :refer [about merge-deep get-default-options]]
    #_[zprint.config :refer :all :exclude
       [set-options! configure-all! get-options]]
    #?@(:clj [[clojure.repl :refer :all]])))


;
; Test the main program on its own, and also in the uberjar if it can
; be found.  If we don't find the uberjar, we emit a message to that effect
; but we do not cause an error.
;

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

(set-options! {:configured? true})

;;
;; NOTE WELL!!
;;
;; The main program typically configures itself from the various .rc files 
;; (except it doesn't when we do (set-options! {:configured? true}) like we 
;; do above).
;;
;; It also reads in any options map off of the command line and then does
;; a set-options! with that options map.  
;;
;; Thus ANY OPTIONS MAP ON A COMMAND LINE IN THESE TESTS WILL PERSIST
;; FROM TEST TO TEST UNLESS YOU DO SOMETHING ABOUT IT.
;;
;; What you do about it is:
;;
;;  (set-options! (get-default-options))
;;
;; After any test which had an options map on the command line.
;;
;; This will reset the internal options map to the original defaults, but
;; it will not configure from the outside .rc files -- as it would if you
;; did (configure-all!)!
;;  


#?(:clj
(defexpect main-tests

  ;; Emit a file we can test with into the current directory

  (def ttt-test
    "(defn tictactoe-game  []  [:div [:div [:h1 (:text @app-state)] [:p \"Do you want to play a game?\"]]\n   [:center\n    [:svg {:view-box \"0 0 3 3\", :width 500, :height 500}\n     (for [x-cell (range (count (:board @app-state)))\n           y-cell (range (count (:board @app-state)))]\n       ^{:key (str x-cell y-cell)}\n       [:rect\n        {:width 0.9,\n         :height 0.9,\n         :fill (if (= :empty (get-in @app-state [:board y-cell x-cell]))\n                 \"green\"\n                 \"purple\"),\n         :x x-cell,\n         :y y-cell,\n         :on-click (fn rectangle-click [e]\n                     (println \"Cell\" x-cell y-cell \"was clicked!\")\n                     (println (swap! app-state assoc-in\n                                [:board y-cell x-cell]\n                                :clicked)))}])]]])\n")

  (expect nil (spit "ttt_test.clj" ttt-test))
  (expect nil (spit "tt2_test.clj" ttt-test))

  (expect
    "Processing file ttt_test.clj\nFormatting required in file ttt_test.clj\nProcessed 1 file, 1 of which requires formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsc" "ttt_test.clj"))))

  (expect "Processing file ttt_test.clj\n"
          (with-out-str (binding [*err* *out*]
                          (-main ":debug" "-lc" "ttt_test.clj"))))

  (expect "Formatting required in file ttt_test.clj\n"
          (with-out-str (binding [*err* *out*]
                          (-main ":debug" "-fc" "ttt_test.clj"))))

  (expect "Processed 1 file, 1 of which requires formatting.\n"
          (with-out-str (binding [*err* *out*]
                          (-main ":debug" "-sc" "ttt_test.clj"))))

  (expect ""
          (with-out-str (binding [*err* *out*]
                          (-main ":debug" "-c" "ttt_test.clj"))))

  (expect
    "Processing file ttt_test.clj\nFormatting required in file ttt_test.clj\nProcessing file tt2_test.clj\nFormatting required in file tt2_test.clj\nProcessed 2 files, 2 of which require formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsc" "ttt_test.clj" "tt2_test.clj"))))

  (expect "Processing file ttt_test.clj\nProcessing file tt2_test.clj\n"
          (with-out-str (binding [*err* *out*]
                          (-main ":debug" "-lc"
                                 "ttt_test.clj" "tt2_test.clj"))))

  (expect
    "Formatting required in file ttt_test.clj\nFormatting required in file tt2_test.clj\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-fc" "ttt_test.clj" "tt2_test.clj"))))

  (expect "Processed 2 files, 2 of which require formatting.\n"
          (with-out-str (binding [*err* *out*]
                          (-main ":debug" "-sc"
                                 "ttt_test.clj" "tt2_test.clj"))))

  (expect ""
          (with-out-str (binding [*err* *out*]
                          (-main ":debug" "-c" "ttt_test.clj" "tt2_test.clj"))))


  (expect
    "Processing file ttt_test.clj\nFormatted file ttt_test.clj\nProcessed 1 file, 1 of which required formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsw" "ttt_test.clj"))))

  (expect
    "(defn tictactoe-game\n  []\n  [:div [:div [:h1 (:text @app-state)] [:p \"Do you want to play a game?\"]]\n   [:center\n    [:svg {:view-box \"0 0 3 3\", :width 500, :height 500}\n     (for [x-cell (range (count (:board @app-state)))\n           y-cell (range (count (:board @app-state)))]\n       ^{:key (str x-cell y-cell)}\n       [:rect\n        {:width 0.9,\n         :height 0.9,\n         :fill (if (= :empty (get-in @app-state [:board y-cell x-cell]))\n                 \"green\"\n                 \"purple\"),\n         :x x-cell,\n         :y y-cell,\n         :on-click (fn rectangle-click [e]\n                     (println \"Cell\" x-cell y-cell \"was clicked!\")\n                     (println (swap! app-state assoc-in\n                                [:board y-cell x-cell]\n                                :clicked)))}])]]])\n"
    (slurp "ttt_test.clj"))

  (expect zero? (:exit (sh "chmod" "-w" "tt2_test.clj")))

  (expect
    "Processing file ttt_test.clj\nProcessing file tt2_test.clj\nFailed to write output file: tt2_test.clj because java.io.FileNotFoundException: tt2_test.clj (Permission denied)\nProcessed 2 files, with 1 error, 1 of which required formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsw" "ttt_test.clj" "tt2_test.clj"))))

  (expect zero? (:exit (sh "chmod" "+w" "tt2_test.clj")))

  (expect
    "Processing file ttt_test.clj\nProcessed 1 file, none of which requires formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsc" "ttt_test.clj"))))

  (expect
    "Processing file ttt_test.clj\nProcessed 1 file, none of which required formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsw" "ttt_test.clj"))))

  ;;
  ;; {:files ...} tests
  ;;
  ;; Since {:files ...} shows up on the command line as an options map, we
  ;; will do a (set-options! (get-default-options)) after every test.
  ;;


  (expect
    "Processing file ttt_test.clj\nFormatting required in file ttt_test.clj\nProcessed 1 file, 1 of which requires formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug"
                           "{:width 40 :files {:glob \"ttt_test.clj\"}}"
                           "-lfsc"))))

  ;;
  ;; Does it work with :directory?
  ;;

  (set-options! (get-default-options))

  (expect
    "Processing file ttt_test.clj\nFormatting required in file ttt_test.clj\nProcessed 1 file, 1 of which requires formatting.\n"
    (with-out-str
      (binding [*err* *out*]
        (-main ":debug"
               "{:width 40 :files {:directory \".\" :glob \"ttt_test.clj\"}}"
               "-lfsc"))))

  ;;
  ;; Does directory actually do something?  Let's use :directory to force
  ;; it to look where the file doesn't exist
  ;;

  (set-options! (get-default-options))

  (expect
    "Unable to access files specified by: '{:files {:directory \"..\", :glob \"ttt_test.clj\"}}'\n"
    (with-out-str
      (binding [*err* *out*]
        (-main ":debug"
               "{:width 40 :files {:directory \"..\" :glob \"ttt_test.clj\"}}"
               "-lfsc"))))


  ; Get rid of previous options map: :width 40
  ; If we did (configure-all!), it would configure from outside .rc files
  ; as well, so don't do that!

  (set-options! (get-default-options))

  (expect
    "Cannot have :files key in command-line options: '{:files {:glob \"ttt_test.clj\"}}' and also process files supplied by the shell!\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "{:files {:glob \"ttt_test.clj\"}}"
                           "-lfsc" "tt2_test.clj"))))

  (set-options! (get-default-options))


  (expect zero? (:exit (sh "chmod" "-w" "tt2_test.clj")))

  (expect
    "Processing file ttt_test.clj\nProcessing file tt2_test.clj\nFailed to write output file: tt2_test.clj because java.io.FileNotFoundException: tt2_test.clj (Permission denied)\nProcessed 2 files, with 1 error, 1 of which required formatting.\n"
    (with-out-str
      (binding [*err* *out*]
        (-main ":debug" "{:files {:glob \"tt*_test.clj\"}}" "-lfsw"))))

  (set-options! (get-default-options))

  (expect zero? (:exit (sh "chmod" "+w" "tt2_test.clj")))

  ;;
  ;; See if the above command actually processed one file
  ;;

  (expect
    "(defn tictactoe-game\n  []\n  [:div [:div [:h1 (:text @app-state)] [:p \"Do you want to play a game?\"]]\n   [:center\n    [:svg {:view-box \"0 0 3 3\", :width 500, :height 500}\n     (for [x-cell (range (count (:board @app-state)))\n           y-cell (range (count (:board @app-state)))]\n       ^{:key (str x-cell y-cell)}\n       [:rect\n        {:width 0.9,\n         :height 0.9,\n         :fill (if (= :empty (get-in @app-state [:board y-cell x-cell]))\n                 \"green\"\n                 \"purple\"),\n         :x x-cell,\n         :y y-cell,\n         :on-click (fn rectangle-click [e]\n                     (println \"Cell\" x-cell y-cell \"was clicked!\")\n                     (println (swap! app-state assoc-in\n                                [:board y-cell x-cell]\n                                :clicked)))}])]]])\n"
    (slurp "ttt_test.clj"))

  ;;
  ;; What happens if we don't have :glob in :files?
  ;;

  (expect
    "In Operational options, The value of the key-sequence [:files] -> {} did not contain the key :glob\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "{:files {}}" "-lfsc"))))


  ;;
  ;; Run tests using the actual uberjar, not just the main routine and library
  ;;
  ;; If the uberjar isn't found, don't fail, just output the message.

  (def uberjar
    (join "-" (interpose "filter" (split (zprint.config/about) #"-"))))

  (def target-uberjar (str "target/" uberjar))

  (if (zero? (:exit (sh "ls" target-uberjar)))
    (do

      ;; Output a file that needs formatting
      (expect nil (spit "ttt_test.clj" ttt-test))

      ;; Check it

      (expect
        (more-of result
          1 (:exit result)
          "Processing file ttt_test.clj\nFormatting required in file ttt_test.clj\nProcessed 1 file, 1 of which requires formatting.\n"
            (:err result)
          "" (:out result))
        (sh "java" "-jar" target-uberjar "-lfsc" "ttt_test.clj"))

      ;; Format it if necessary

      (expect
        (more-of result
          0 (:exit result)
          "Processing file ttt_test.clj\nFormatted file ttt_test.clj\nProcessed 1 file, 1 of which required formatting.\n"
            (:err result)
          "" (:out result))
        (sh "java" "-jar" target-uberjar "-lfsw" "ttt_test.clj"))

      ;; Check it again

      (expect
        (more-of result
          0 (:exit result)
          "Processing file ttt_test.clj\nProcessed 1 file, none of which requires formatting.\n"
            (:err result)
          "" (:out result))
        (sh "java" "-jar" target-uberjar "-lfsc" "ttt_test.clj"))

      ;; Format it again, see what happens

      (expect
        (more-of result
          0 (:exit result)
          "Processing file ttt_test.clj\nProcessed 1 file, none of which required formatting.\n"
            (:err result)
          "" (:out result))
        (sh "java" "-jar" target-uberjar "-lfsw" "ttt_test.clj")))
    (println "Didn't find uberjar"
             target-uberjar
             ", skipping 13 uberjar tests!!"))

  ;; End of uberjar processing

  ;; Remove test Clojure file

  (expect zero? (:exit (sh "rm" "ttt_test.clj")))
  (expect zero? (:exit (sh "rm" "tt2_test.clj")))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
  ;; End of defexpect
  ;;
  ;; All tests MUST come before this!!!
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


))
