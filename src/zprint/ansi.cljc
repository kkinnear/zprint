(ns ^:no-doc zprint.ansi)

;;
;; # Do ANSI Escape code processing
;;

(def ansi-codes
  {:off 0,
   :reset 0,
   :bold 1,
   :faint 2,
   :italic 3,
   :underline 4,
   :blink 5,
   :reverse 7,
   :hidden 8,
   :strike 9,
   :normal 22,
   :italic-off 23,
   :underline-off 24,
   :blink-off 25,
   :reverse-off 27,
   :hidden-off 28,
   :strike-off 29,
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
   :back-black 40,
   :back-red 41,
   :back-green 42,
   :back-yellow 43,
   :back-blue 44,
   :back-magenta 45,
   :back-purple 45,
   :back-cyan 46,
   :back-white 47,
   :bright-black 90,
   :bright-red 91,
   :bright-green 92,
   :bright-yellow 93,
   :bright-blue 94,
   :bright-magenta 95,
   :bright-purple 95,
   :bright-cyan 96,
   :bright-white 97,
   :back-bright-black 100,
   :back-bright-red 101,
   :back-bright-green 102,
   :back-bright-yellow 103,
   :back-bright-blue 104,
   :back-bright-magenta 105,
   :back-bright-purple 105,
   :back-bright-cyan 106,
   :back-bright-white 107})

(defn color-str
  "Wraps a string with ANSI escape codes."
  [s & ansi]
  (let [ansi-str (apply str (interpose \; (map ansi-codes ansi)))]
    (str \u001b \[ ansi-str \m s \u001b \[ \0 \m)))