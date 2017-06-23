(ns zprint.ansi
  (:require clojure.string))

;;
;; # Do ANSI Escape code processing
;;

(def ansi-codes
  {:off 0,
   :bold 1,
   :underline 3,
   :blink 5,
   :reverse 7,
   :hidden 8,
   :strike 9,
   :black 30,
   :none 30,
   :red 31,
   :green 32,
   :yellow 33,
   :blue 34,
   :magenta 35,
   :purple 35,
   :cyan 36,
   :white 37,
   :xsf 38,
   :back-red 41,
   :back-green 42,
   :back-yellow 43,
   :back-blue 44,
   :back-magenta 45,
   :back-purple 45,
   :back-cyan 66})

(defn color-str
  "Wraps a string with ANSI escape codes."
  [s & ansi]
  (let [ansi-str (apply str (interpose \; (map ansi-codes ansi)))]
    (str \u001b \[ ansi-str \m s \u001b \[ \0 \m)))