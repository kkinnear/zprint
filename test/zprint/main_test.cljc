(ns zprint.main-test
  (:require [expectations.clojure.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            #?(:cljs [cljs.test :refer-macros [deftest is]])
            #?(:clj [clojure.test :refer [deftest is]])
            #?(:cljs [cljs.tools.reader :refer [read-string]])
            [zprint.main :refer [-main]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :refer [split join]]
            [zprint.core :refer
             [zprint-str set-options! czprint-str zprint-file-str]]
            [zprint.zprint :refer [line-count]]
            [zprint.config :as config :refer
             [; config-set-options! get-options config-configure-all!
              ;  reset-options! help-str get-explained-options
              ;  get-explained-all-options get-default-options validate-options
              ;  apply-style perform-remove no-color-map
              about merge-deep]]
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

;!zprint {:comment {:wrap? false} :reader-cond {:indent -3} :fn-map {"more-of" :arg1-pair "defexpect" [:arg1-body {:style :respect-nl :next-inner {:style :respect-nl-off}}] "do" [:none-body {:style :respect-nl :next-inner {:style :respect-nl-off}}] }}         

;;; This whole file is only interesting in Clojure, not Clojurescript, since
;;; we are testing the uberjar command switch processing and the file access

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
                          (-main ":debug" "-lfsw"
                                 "ttt_test.clj" "tt2_test.clj"))))

  (expect zero? (:exit (sh "chmod" "+w" "tt2_test.clj")))

  (expect
    "Processing file ttt_test.clj\nProcessed 1 file, none of which requires formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsc" "ttt_test.clj"))))

  (expect
    "Processing file ttt_test.clj\nProcessed 1 file, none of which required formatting.\n"
    (with-out-str (binding [*err* *out*]
                    (-main ":debug" "-lfsw" "ttt_test.clj"))))

  (expect
    "(defn tictactoe-game\n  []\n  [:div [:div [:h1 (:text @app-state)] [:p \"Do you want to play a game?\"]]\n   [:center\n    [:svg {:view-box \"0 0 3 3\", :width 500, :height 500}\n     (for [x-cell (range (count (:board @app-state)))\n           y-cell (range (count (:board @app-state)))]\n       ^{:key (str x-cell y-cell)}\n       [:rect\n        {:width 0.9,\n         :height 0.9,\n         :fill (if (= :empty (get-in @app-state [:board y-cell x-cell]))\n                 \"green\"\n                 \"purple\"),\n         :x x-cell,\n         :y y-cell,\n         :on-click (fn rectangle-click [e]\n                     (println \"Cell\" x-cell y-cell \"was clicked!\")\n                     (println (swap! app-state assoc-in\n                                [:board y-cell x-cell]\n                                :clicked)))}])]]])\n"
    (slurp "ttt_test.clj"))

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
          "Processing file ttt_test.clj\nFormatting required in file ttt_test.clj\nProcessed 1 file, 1 of which require formatting.\n"
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
          "Processing file ttt_test.clj\nProcessed 1 file, none of which require formatting.\n"
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
