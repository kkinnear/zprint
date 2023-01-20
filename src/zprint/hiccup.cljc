(ns ^:no-doc zprint.hiccup
  (:require clojure.string))

;;
;; # Do hiccup processing
;;

(def html-codes
  {:bold "font-weight:bold",
   :faint "opacity:50%",
   :italic "font-style:italic",
   :underline "text-decoration: underline",
   :blink "",
   :reverse "",
   :hidden "display: none",
   :strike "text-decoration: line-through",
   :normal "text-decoration: none",
   :italic-off "font-style:normal",
   :underline-off "font-style:normal",
   :blink-off "",
   :reverse-off "",
   :hidden-off "display: unset",
   :strike-off "text-decoration: none",
   :black "color:black",
   :none "color:black",
   :red "color:red",
   :green "color:green",
   :yellow "color:yellow",
   :blue "color:blue",
   :magenta "color:magenta",
   :purple "color:purple",
   :cyan "color:cyan",
   :white "color:white"
   ; ....
  })

#_(def ansi-codes
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



(defn hiccup-color-str
  "Wraps a string with hiccup expressions."
  ([s & ansi]
   (let [style-str (apply str (interpose \; (map html-codes ansi)))
         s (clojure.string/replace s "\n" "<br>")
         s (clojure.string/replace s " " "&nbsp")]
     [:span {:style style-str} s]))
  ([s] (hiccup-color-str s html-codes)))

(defn wrap-w-p
  "Wrap a sequence of hiccup elements with a p."
  [options coll]
  (let [default-style
          "font-size:15px;font-family: Lucidia Concole, Courier, monospace"
        style (:style (:paragraph (:output options)))
        style (if (nil? style) default-style style)]
    (into [:p {:style style}] coll)))

(defn hiccup->html
  "A very specialized hiccup to html converter.  Only converts hiccup
  that is produced by this routine!"
  [coll]
  (cond (vector? coll) (let [first-sym (symbol (first coll))
                             map-second? (map? (second coll))
                             rest-coll
                               (if map-second? (nnext coll) (next coll))]
                         (str "<"
                              first-sym
                              (if map-second? (hiccup->html (second coll)) "")
                              ">"
                              (apply str (mapv hiccup->html rest-coll))
                              "</"
                              first-sym
                              ">"))
        (map? coll) (let [pair (first (seq coll))]
                      (str " " (symbol (first pair)) "=\"" (second pair) "\""))
        :else coll))