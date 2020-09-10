(ns zprint.finish-test
  (:require [expectations.clojure.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            #?(:cljs [cljs.tools.reader :refer [read-string]])
            [zprint.core :refer [zprint-str set-options! czprint-str]]
            [zprint.finish :refer [cvec-to-style-vec compress-style]]))

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

(set-options! {:configured? true})

(defexpect finish-test

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
    '(["(" :green :left]
      ["defnp" :blue :element]
      [" " nil :whitespace]
      ["trim-vec" nil :element]
      ["\n  " nil :whitespace]
      ["\"Take a vector of any length, and trim it to be\n  only n elements in length.\""
       :red :element]
      ["\n  " nil :whitespace]
      ["[" :purple :left]
      ["n" nil :element]
      [" " nil :whitespace]
      ["v" nil :element]
      ["]" :purple :right]
      ["\n  " nil :whitespace]
      ["(" :green :left]
      ["into" :blue :element]
      [" " nil :whitespace]
      ["[" :purple :left]
      ["" nil nil]
      ["]" :purple :right]
      [" " nil :whitespace]
      ["(" :green :left]
      ["take" :blue :element]
      [" " nil :whitespace]
      ["n" nil :element]
      [" " nil :whitespace]
      ["v" nil :element]
      [")" :green :right]
      [")" :green :right]
      [")" :green :right]))

  (def xcps
    [["(" :green 0 1] ["defnp" :blue 1 5] [" trim-vec\n  " nil 6 12]
     ["\"Take a vector of any length, and trim it to be\n  only n elements in length.\""
      :red 18 77] ["\n  " nil 95 3] ["[" :purple 98 1] ["n v" nil 99 3]
     ["]" :purple 102 1] ["\n  " nil 103 3] ["(" :green 106 1]
     ["into" :blue 107 4] [" " nil 111 1] ["[" :purple 112 1] ["" nil 113 0]
     ["]" :purple 113 1] [" " nil 114 1] ["(" :green 115 1] ["take" :blue 116 4]
     [" n v" nil 120 4] [")))" :green 124 3]])

  (expect xssv (cvec-to-style-vec {:style-map zprint.finish/no-style-map} xcv))
  (expect xcps (compress-style xssv))

  ;; Ensure that we only get escape sequences when we actually do colors

  (expect 181 (count (czprint-str [:a :b {:c :d, 'e 'f} '(x y z) "bother"])))
  (expect 181
          (count (zprint-str [:a :b {:c :d, 'e 'f} '(x y z) "bother"]
                             {:color? true})))

  (expect 37 (count (zprint-str [:a :b {:c :d, 'e 'f} '(x y z) "bother"])))
  (expect 37
          (count (czprint-str [:a :b {:c :d, 'e 'f} '(x y z) "bother"]
                              {:color? false})))

  (expect 15
          (count (czprint-str "(a b c)\n {:a :b}"
                              {:parse-string-all? true, :color? false})))

)
