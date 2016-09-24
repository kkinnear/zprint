(ns
  zprint.finish-test
  (:require
   [expectations :refer :all]
   [zprint.core :refer :all]
   [zprint.core-test :refer :all]
   [zprint.zprint :refer :all]
   [zprint.config :refer :all :exclude
    [set-options! configure-all! get-options]]
   [zprint.finish :refer :all]
   [clojure.string :as str]
   [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
   [rewrite-clj.node :as n]
   [rewrite-clj.zip :as z :only [edn*]]))
;;
;;
;; # str-style-vec Tests
;;
;; cvec-to-style-vec
;; compress-style
;;

(def xcv
  '(["(" :green :left]
    ["defnp" :blue :element]
    [" " :none :whitespace]
    ["trim-vec" :none :element]
    ["\n  " :none :whitespace]
    ["\"Take a vector of any length, and trim it to be\n  only n elements in length.\""
     :red :element]
    ["\n  " :none :whitespace]
    ["[" :purple :left]
    ["n" :none :element]
    [" " :none :whitespace]
    ["v" :none :element]
    ["]" :purple :right]
    ["\n  " :none :whitespace]
    ["(" :green :left]
    ["into" :blue :element]
    [" " :none :whitespace]
    ["[" :purple :left]
    [""]
    ["]" :purple :right]
    [" " :none :whitespace]
    ["(" :green :left]
    ["take" :blue :element]
    [" " :none :whitespace]
    ["n" :none :element]
    [" " :none :whitespace]
    ["v" :none :element]
    [")" :green :right]
    [")" :green :right]
    [")" :green :right]))

(def xssv
  '(["(" :green]
    ["defnp" :blue]
    [" " :none]
    ["trim-vec" :none]
    ["\n  " :none]
    ["\"Take a vector of any length, and trim it to be\n  only n elements in length.\""
     :red]
    ["\n  " :none]
    ["[" :purple]
    ["n" :none]
    [" " :none]
    ["v" :none]
    ["]" :purple]
    ["\n  " :none]
    ["(" :green]
    ["into" :blue]
    [" " :none]
    ["[" :purple]
    ["" :b]
    ["]" :purple]
    [" " :none]
    ["(" :green]
    ["take" :blue]
    [" " :none]
    ["n" :none]
    [" " :none]
    ["v" :none]
    [")" :green]
    [")" :green]
    [")" :green]))

(def xcps
  [["(" :green 0 1] ["defnp" :blue 1 5] [" trim-vec\n  " :none 6 12]
   ["\"Take a vector of any length, and trim it to be\n  only n elements in length.\""
    :red 18 77] ["\n  " :none 95 3] ["[" :purple 98 1] ["n v" :none 99 3]
   ["]" :purple 102 1] ["\n  " :none 103 3] ["(" :green 106 1]
   ["into" :blue 107 4] [" " :none 111 1] ["[" :purple 112 1] ["" :b 113 0]
   ["]" :purple 113 1] [" " :none 114 1] ["(" :green 115 1] ["take" :blue 116 4]
   [" n v" :none 120 4] [")))" :green 124 3]])

(expect xssv (cvec-to-style-vec {:style-map no-style-map} xcv))
(expect xcps (compress-style xssv))