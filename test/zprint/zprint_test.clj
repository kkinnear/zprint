(ns zprint.zprint-test
  (:require [expectations :refer :all]
            [zprint.core :refer :all]
            [zprint.core-test :refer :all]
            [zprint.zprint :refer :all]
            [zprint.finish :refer :all]
            [clojure.repl :refer :all]
            [clojure.string :as str]
            [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only [edn*]]))

;; Keep some of the test on wrapping so they still work
;!zprint {:comment {:wrap? false}}

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

(set-options! {:configured? true})


;;
;; # Pretty Tests
;;
;; See if things print the way they are supposed to.
;;

; 
; Test with size of 20
;
; with and without do
;


(def iftestdo
  '(if (and :abcd :efbg) (do (list xxx yyy zzz) (list ccc ddd eee))))

(def iftest '(if (and :abcd :efbg) (list xxx yyy zzz) (list ccc ddd eee)))

(def iftest-19-str
  "(if (and :abcd\n         :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))")

(def iftest-20-str
  "(if (and :abcd\n         :efbg)\n  (list xxx yyy zzz)\n  (list ccc\n        ddd\n        eee))")

(def iftest-21-str
  "(if (and :abcd :efbg)\n  (list xxx yyy zzz)\n  (list ccc ddd eee))")

(expect iftest-19-str (zprint-str iftest 19 {:list {:constant-pair? nil}}))

(defn lines "Turn a string into lines." [s] (str/split s #"\n"))

(expect (lines iftest-20-str) (lines (zprint-str iftest 20)))
(expect iftest-21-str (zprint-str iftest 21))

;;
;; Another couple of fidelity tests of two of our actual functions,
;; first with :parallel? false (the current default, but that could 
;; change)
;;

(def y1 (source-fn 'fzprint-map-two-up))
(expect (read-string y1)
        (read-string (zprint-str y1 {:parallel? false, :parse-string? true})))

(def y2 (source-fn 'partition-all-2-nc))
(expect (trim-gensym-regex (read-string y2))
        (trim-gensym-regex (read-string (zprint-str y2
                                                    {:parallel? false,
                                                     :parse-string? true}))))
(def y3 (source-fn 'fzprint-list*))
(expect (trim-gensym-regex (read-string y3))
        (trim-gensym-regex (read-string (zprint-str y3
                                                    {:parallel? false,
                                                     :parse-string? true}))))

;;
;; and again with :parallel? true
;;

(def y1 (source-fn 'fzprint-map-two-up))
(expect (read-string y1)
        (read-string (zprint-str y1 {:parallel? true, :parse-string? true})))

(def y2 (source-fn 'partition-all-2-nc))
(expect (trim-gensym-regex (read-string y2))
        (trim-gensym-regex
          (read-string (zprint-str y2 {:parallel? true, :parse-string? true}))))

(def y3 (source-fn 'fzprint-list*))
(expect (trim-gensym-regex (read-string y3))
        (trim-gensym-regex
          (read-string (zprint-str y3 {:parallel? true, :parse-string? true}))))

;;
;; and again with :parallel? true and {:style :justify}
;;

(def y1 (source-fn 'fzprint-map-two-up))
(expect
  (read-string y1)
  (read-string
    (zprint-str y1 {:style :justified, :parallel? true, :parse-string? true})))

(def y2 (source-fn 'partition-all-2-nc))
(expect (trim-gensym-regex (read-string y2))
        (trim-gensym-regex (read-string (zprint-str y2
                                                    {:style :justified,
                                                     :parallel? true,
                                                     :parse-string? true}))))

(def y3 (source-fn 'fzprint-list*))
(expect (trim-gensym-regex (read-string y3))
        (trim-gensym-regex (read-string (zprint-str y3
                                                    {:style :justified,
                                                     :parallel? true,
                                                     :parse-string? true}))))

;;
;; Check out line count
;;

(expect 1 (line-count "abc"))

(expect 2 (line-count "abc\ndef"))

(expect 3 (line-count "abc\ndef\nghi"))


(def r {:aaaa :bbbb, :cccc '({:eeee :ffff, :aaaa :bbbb, :cccccccc :dddddddd})})

(def r-str (pr-str r))

;;
;; r should take 29 characters
;;{:aaaa :bbbb,
;; :cccc ({:aaaa :bbbb,
;;         :cccccccc :dddddddd,
;;         :eeee :ffff})}
;;
;; The comma at the end of the :dddd should be in
;; column 29.  So this should fit with a width of 29, but not 28.
;;
;; Check both sexpression printing and parsing into a zipper printing.
;;

(expect 29 (max-width (zprint-str r 30)))
(expect 4 (line-count (zprint-str r 30)))

(expect 29 (max-width (zprint-str r 29)))
(expect 4 (line-count (zprint-str r 29)))

(expect 25 (max-width (zprint-str r 28 {:map {:hang-adjust 0}})))
(expect 23 (max-width (zprint-str r 28 {:map {:hang-adjust -1}})))
(expect 5 (line-count (zprint-str r 28 {:map {:hang-adjust 0}})))

(expect 29 (max-width (zprint-str r-str 30 {:parse-string? true})))
(expect 4 (line-count (zprint-str r-str 30 {:parse-string? true})))

(expect 29 (max-width (zprint-str r-str 29 {:parse-string? true})))
(expect 4 (line-count (zprint-str r-str 29 {:parse-string? true})))

(expect 25
        (max-width
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust 0}})))
(expect 23
        (max-width
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust -1}})))
(expect 5
        (line-count
          (zprint-str r-str 28 {:parse-string? true, :map {:hang-adjust 0}})))

(def t {:cccc '({:dddd :eeee, :fffffffff :ggggggggggg}), :aaaa :bbbb})

(def t-str (pr-str t))

;;
;;{:aaaa :bbbb,
;; :cccc ({:dddd :eeee,
;;         :fffffffff
;;           :ggggggggggg})}
;;
;; This takes 26 characters, and shouldn't fit on a 25
;; character line.

(expect 26 (max-width (zprint-str t 26)))
(expect 4 (line-count (zprint-str t 26)))

(expect 22 (max-width (zprint-str t 25)))
(expect 5 (line-count (zprint-str t 25)))

(expect 26 (max-width (zprint-str t-str 26 {:parse-string? true})))
(expect 4 (line-count (zprint-str t-str 26 {:parse-string? true})))

(expect 22 (max-width (zprint-str t-str 25 {:parse-string? true})))
(expect 5 (line-count (zprint-str t-str 25 {:parse-string? true})))

;;
;; Test line-lengths
;;

(def ll
  [["{" :red :left] [":aaaa" :purple :element] [" " :none :whitespace]
   [":bbbb" :purple :element] [",\n        " :none :whitespace]
   [":ccccccccccc" :purple :element] ["\n          " :none :whitespace]
   [":ddddddddddddd" :purple :element] [",\n        " :none :whitespace]
   [":eeee" :purple :element] [" " :none :whitespace] [":ffff" :purple :element]
   ["}" :red :right]])

(expect [20 20 25 20] (line-lengths {} 7 ll))

(def u
  {:aaaa :bbbb, :cccc {:eeee :ffff, :aaaa :bbbb, :ccccccccccc :ddddddddddddd}})

(def u-str (pr-str u))

;;
;;{:aaaa :bbbb,
;; :cccc {:aaaa :bbbb,
;;        :ccccccccccc
;;          :ddddddddddddd,
;;        :eeee :ffff}}
;;
;; This takes 25 characters and shouldn't fit ona  24 character line
;;

(expect 25 (max-width (zprint-str u 25)))
(expect 5 (line-count (zprint-str u 25)))

(expect 21 (max-width (zprint-str u 24)))
(expect 6 (line-count (zprint-str u 24)))

(expect 25 (max-width (zprint-str u-str 25 {:parse-string? true})))
(expect 5 (line-count (zprint-str u-str 25 {:parse-string? true})))

(expect 21 (max-width (zprint-str u-str 24 {:parse-string? true})))
(expect 6 (line-count (zprint-str u-str 24 {:parse-string? true})))

(def d
  '(+ :aaaaaaaaaa
      (if :bbbbbbbbbb
        :cccccccccc
        (list :ddddddddd
              :eeeeeeeeee :ffffffffff
              :gggggggggg :hhhhhhhhhh
              :iiiiiiiiii :jjjjjjjjjj
              :kkkkkkkkkk :llllllllll
              :mmmmmmmmmm :nnnnnnnnnn
              :oooooooooo :pppppppppp))))

(def d-str (pr-str d))

(expect 16 (line-count (zprint-str d 80 {:list {:constant-pair? nil}})))
(expect
  16
  (line-count
    (zprint-str d-str 80 {:parse-string? true, :list {:constant-pair? nil}})))

(def e {:aaaa :bbbb, :cccc :dddd, :eeee :ffff})
(def e-str (pr-str e))

(expect 39 (max-width (zprint-str e 39)))
(expect 1 (line-count (zprint-str e 39)))
(expect 13 (max-width (zprint-str e 38)))
(expect 3 (line-count (zprint-str e 38)))

(expect 39 (max-width (zprint-str e-str 39 {:parse-string? true})))
(expect 1 (line-count (zprint-str e-str 39 {:parse-string? true})))
(expect 13 (max-width (zprint-str e-str 38 {:parse-string? true})))
(expect 3 (line-count (zprint-str e-str 38 {:parse-string? true})))

(expect {:a nil} (read-string (zprint-str {:a nil})))

;;
;; Check out zero argument functions
;;

(expect 3 (max-width (zprint-str '(+))))

;;
;; Printing atoms
;;

(def atm (atom {:a :b}))

(expect (str "#<Atom " @atm ">")
        (let [result (zprint-str atm)]
          (clojure.string/replace result (re-find #"@[a-fA-F0-9]+" result) "")))

;;
;; Atom printing -- prints the contents of the atom
;; in a pretty way too.
;;

(def atm2 (atom u))

(expect 5 (line-count (zprint-str atm2 48)))
(expect 1 (line-count (pr-str atm2)))

;;
;; Byte arrays
;;

(def ba (byte-array [1 2 3 4 -128]))

(expect "[01 02 03 04 80]" (zprint-str ba {:array {:hex? true}}))

(def ba1
  (byte-array [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25
               26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47
               48 49 50]))

(expect 51 (max-width (zprint-str ba1 51 {:array {:hex? true}})))
(expect 3 (line-count (zprint-str ba1 51 {:array {:hex? true}})))



;;
;; # Rightmost tests
;;

(def atm1 (atom [:aaaaaaaaa :bbbbbbbbb {:a :b}]))
(def atm1-length (count (zprint-str atm1)))
(expect 1 (line-count (zprint-str atm1 (inc atm1-length))))
(expect 1 (line-count (zprint-str atm1 atm1-length)))
(expect 2 (line-count (zprint-str atm1 (dec atm1-length))))

(def map1 {:abc :def, :hij :klm, "key" "value", :x :y})
(expect 1 (line-count (zprint-str map1 45)))
(expect 1 (line-count (zprint-str map1 44)))
(expect 4 (line-count (zprint-str map1 43)))
;
; Test zipper version
;
(def maps (str map1))
(expect 1 (line-count (zprint-str maps 45 {:parse-string? true})))
(expect 1 (line-count (zprint-str maps 44 {:parse-string? true})))
(expect 4 (line-count (zprint-str maps 43 {:parse-string? true})))


(def vec1 [:asdf :jklx 1 2 3 4 5 "abc" "def"])
(expect 1 (line-count (zprint-str vec1 36)))
(expect 1 (line-count (zprint-str vec1 35)))
(expect 2 (line-count (zprint-str vec1 34)))
;
; Test zipper version
;
(def vecs (str vec1))
(expect 1 (line-count (zprint-str vecs 36 {:parse-string? true})))
(expect 1 (line-count (zprint-str vecs 35 {:parse-string? true})))
(expect 2 (line-count (zprint-str vecs 34 {:parse-string? true})))

(def lis1 '(asdf jklsemi :aaa :bbb :ccc 1 2 3 4 5 "hello"))
(expect 1 (line-count (zprint-str lis1 48)))
(expect 1 (line-count (zprint-str lis1 47)))
(expect 10 (line-count (zprint-str lis1 46 {:list {:constant-pair? nil}})))
(expect 6 (line-count (zprint-str lis1 46 {:list {:constant-pair? true}})))
;
; Test zipper version
;
(def liss (str lis1))
(expect 1 (line-count (zprint-str liss 48 {:parse-string? true})))
(expect 1 (line-count (zprint-str liss 47 {:parse-string? true})))
(expect
  10
  (line-count
    (zprint-str liss 46 {:parse-string? true, :list {:constant-pair? nil}})))
(expect
  6
  (line-count
    (zprint-str liss 46 {:parse-string? true, :list {:constant-pair? true}})))

(def lis2 '(aaaa bbbb cccc (dddd eeee ffff)))
(expect 1 (line-count (zprint-str lis2 34)))
(expect 1 (line-count (zprint-str lis2 33)))
(expect 3 (line-count (zprint-str lis2 32)))

(def set1 #{:aaa :bbb :ccc :ddd "stuff" "bother" 1 2 3 4 5})
(expect 1 (line-count (zprint-str set1 50)))
(expect 1 (line-count (zprint-str set1 49)))
(expect 2 (line-count (zprint-str set1 48)))

(def set2 #{{:a :b} {:c :d} {:e :f} {:g :h}})
(expect 1 (line-count (zprint-str set2 35)))
(expect 1 (line-count (zprint-str set2 34)))
(expect 2 (line-count (zprint-str set2 33)))
;
; Test zipper version
;
(def sets (str set2))
(expect 1 (line-count (zprint-str sets 35 {:parse-string? true})))
(expect 1 (line-count (zprint-str sets 34 {:parse-string? true})))
(expect 2 (line-count (zprint-str sets 33 {:parse-string? true})))

(def rs (make-record :reallylongleft :r))
(expect 1 (line-count (zprint-str rs 53)))
(expect 1 (line-count (zprint-str rs 52)))
(expect 1 (line-count (zprint-str rs 51)))
(expect 2 (line-count (zprint-str rs 50)))
(expect 2 (line-count (zprint-str rs 49)))

;;
;; Lest these look like "of course" tests, remember that
;; the zprint functions can parse a string into a zipper and then
;; print it, so these are all getting parsed and then handled by
;; the zipper code.
;;

(expect "()" (zprint-str "()" {:parse-string? true}))
(expect "[]" (zprint-str "[]" {:parse-string? true}))
(expect "{}" (zprint-str "{}" {:parse-string? true}))
(expect "#()" (zprint-str "#()" {:parse-string? true}))
(expect "#{}" (zprint-str "#{}" {:parse-string? true}))
(expect "#_()" (zprint-str "#_()" {:parse-string? true}))
(expect "#_[]" (zprint-str "#_[]" {:parse-string? true}))
(expect "~{}" (zprint-str "~{}" {:parse-string? true}))
(expect "^{:a :b} stuff" (zprint-str "^{:a :b} stuff" {:parse-string? true}))

;;
;; # Constant pairing
;;

(expect
  "(if (and\n      :abcd :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))"
  (zprint-str iftest
              19
              {:list {:constant-pair-min 2, :constant-pair? true},
               :tuning {:general-hang-adjust 0}}))

(expect
  "(if (and :abcd\n         :efbg)\n  (list xxx\n        yyy\n        zzz)\n  (list ccc\n        ddd\n        eee))"
  (zprint-str iftest 19 {:list {:constant-pair-min 4, :constant-pair? true}}))

(expect
  6
  (line-count
    (zprint-str
      "(println :aaaa :bbbb :cccc :dddd :eeee :ffff 
                :gggg :hhhh :iiii :jjjj :kkkk)"
      50
      {:parse-string? true, :list {:constant-pair? true}})))

(expect
  11
  (line-count
    (zprint-str
      "(println :aaaa :bbbb :cccc :dddd :eeee :ffff 
                :gggg :hhhh :iiii :jjjj :kkkk)"
      50
      {:parse-string? true, :list {:constant-pair? nil}})))

(expect "{:a :b, :c nil}" (zprint-str "{:a :b :c nil}" {:parse-string? true}))

;;
;; # Line Lengths
;;

(expect [3 0] (zprint.zprint/line-lengths {} 3 [["; stuff" :none :comment]]))

(expect
  [14 30 20]
  (zprint.zprint/line-lengths
    {}
    12
    [[":c" :magenta :element] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect
  [2 30 20]
  (zprint.zprint/line-lengths
    {}
    0
    [[":c" :magenta :element] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect
  [12 30 20]
  (zprint.zprint/line-lengths
    {}
    12
    [[";" :green :comment] ["\n            " :none :whitespace]
     ["(" :green :left] ["identity" :blue :element] [" " :none :whitespace]
     ["\"stuff\"" :red :element] [")" :green :right]
     ["\n            " :none :whitespace] ["\"bother\"" :red :element]]))

(expect "(;a\n list\n :b\n :c\n ;def\n  )"
        (zprint-str "(;a\nlist\n:b\n:c ;def\n)"
                    {:parse-string? true, :comment {:inline? false}}))

(expect [6 1 8 1 9 1 11]
        (zprint.zprint/line-lengths
          {}
          0
          [["(" :green :left] ["cond" :blue :element] [" " :none :whitespace]
           ["; one" :green :comment] [" " :none :whitespace]
           ["; two   " :green :comment] [" " :none :whitespace]
           [":stuff" :magenta :element] [" " :none :whitespace]
           ["; middle" :green :comment] [" " :none :whitespace]
           ["; second middle" :green :comment] [" " :none :whitespace]
           [":bother" :magenta :element] [" " :none :whitespace]
           ["; three" :green :comment] [" " :none :whitespace]
           ["; four" :green :comment] [" " :none :whitespace]
           [":else" :magenta :element] [" " :none :whitespace]
           ["nil" :yellow :element] [")" :green :right]]))

(expect
  [1 1 1 1 16]
  (zprint.zprint/line-lengths
    {}
    0
    [["[" :purple :left] [";a" :green :comment] [" " :none :whitespace]
     [";" :green :comment] [" " :none :whitespace] [";b" :green :comment]
     [" " :none :whitespace] [";c" :green :comment] ["\n " :none :whitespace]
     ["this" :black :element] [" " :none :whitespace] ["is" :black :element]
     [" " :none :whitespace] ["a" :black :element] [" " :none :whitespace]
     ["test" :blue :element] ["]" :purple :right]]))


;;
;; # Comments handling
;;

(expect "[;a\n ;\n ;b\n ;c\n this is a test]"
        (zprint-str "[;a\n;\n;b\n;c\nthis is a test]" {:parse-string? true}))

(expect
  "(defn testfn8\n  \"Test two comment lines after a cond test.\"\n  [x]\n  (cond\n    ; one\n    ; two\n    :stuff\n      ; middle\n      ; second middle\n      :bother\n    ; three\n    ; four\n    :else nil))"
  (zprint-fn-str zprint.core-test/testfn8
                 {:pair-fn {:hang? nil}, :comment {:inline? false}}))

(defn zctest3
  "Test comment forcing things"
  [x]
  (cond (and (list ;
                   (identity "stuff")
                   "bother"))
          x
        :else (or :a :b :c)))

(defn zctest4
  "Test comment forcing things"
  [x]
  (cond (and (list :c (identity "stuff") "bother")) x
        :else (or :a :b :c)))

(defn zctest5
  "Model defn issue."
  [x]
  (let [abade :b
        ceered (let [b :d]
                 (if (:a x)
                   ; this is a very long comment that should force things way to the left
                   (assoc b :a :c)))]
    (list :a
          (with-meta name x)
          ; a short comment that might be long if we wanted it to be
          :c)))


(expect
  "(defn zctest4\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and (list :c\n               (identity \"stuff\")\n               \"bother\"))\n      x\n    :else (or :a :b :c)))"
  (zprint-fn-str zprint.zprint-test/zctest4 40 {:pair-fn {:hang? nil}}))

(expect
  "(defn zctest3\n  \"Test comment forcing things\"\n  [x]\n  (cond\n    (and (list ;\n               (identity \"stuff\")\n               \"bother\"))\n      x\n    :else (or :a :b :c)))"
  (zprint-fn-str zprint.zprint-test/zctest3 40 {:pair-fn {:hang? nil}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let\n    [abade :b\n     ceered\n       (let [b :d]\n         (if (:a x)\n           ; this is a very long comment that should force things way\n           ; to the left\n           (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5
                 70
                 {:comment {:count? true, :wrap? true}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force\n                   ; things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5
                 70
                 {:comment {:count? nil, :wrap? true}}))

(expect
  "(defn zctest5\n  \"Model defn issue.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a very long comment that should force things way to the left\n                   (assoc b :a :c)))]\n    (list :a\n          (with-meta name x)\n          ; a short comment that might be long if we wanted it to be\n          :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5
                 70
                 {:comment {:wrap? nil, :count? nil}}))

;;
;; # Tab Expansion
;;

(expect "this is a tab   test to see if it works"
        (zprint.zprint/expand-tabs 8 "this is a tab\ttest to see if it works"))

(expect "this is a taba  test to see if it works"
        (zprint.zprint/expand-tabs 8 "this is a taba\ttest to see if it works"))

(expect "this is a tababc        test to see if it works"
        (zprint.zprint/expand-tabs 8
                                   "this is a tababc\ttest to see if it works"))

(expect "this is a tabab test to see if it works"
        (zprint.zprint/expand-tabs 8
                                   "this is a tabab\ttest to see if it works"))

;;
;; # File Handling
;;

(expect ["\n\n" ";;stuff\n" "(list :a :b)" "\n\n"]
        (zprint.zutil/zmap-all
          (partial czprint-str-internal {:zipper? true, :color? false})
          (z/edn* (p/parse-string-all "\n\n;;stuff\n(list :a :b)\n\n"))))

;;
;; #Deref
;;

(expect
  "@(list this\n       is\n       a\n       test\n       this\n       is\n       only\n       a\n       test)"
  (zprint-str "@(list this is a test this is only a test)"
              30
              {:parse-string? true}))

;;
;; # Reader Conditionals
;;

(expect "#?(:clj (list :a :b)\n   :cljs (list :c :d))"
        (zprint-str "#?(:clj (list :a :b) :cljs (list :c :d))"
                    30
                    {:parse-string? true}))


(expect "#?@(:clj (list :a :b)\n    :cljs (list :c :d))"
        (zprint-str "#?@(:clj (list :a :b) :cljs (list :c :d))"
                    30
                    {:parse-string? true}))

;;
;; # Var
;;

(expect "#'(list this\n        is\n        :a\n        \"test\")"
        (zprint-str "#'(list this is :a \"test\")" 20 {:parse-string? true}))

;;
;; # Ordered Options in maps
;;

(expect "{:a :test, :second :more-value, :should-be-first :value, :this :is}"
        (zprint-str
          {:this :is, :a :test, :should-be-first :value, :second :more-value}))

(expect "{:should-be-first :value, :second :more-value, :a :test, :this :is}"
        (zprint-str
          {:this :is, :a :test, :should-be-first :value, :second :more-value}
          {:map {:key-order [:should-be-first :second]}}))

;;
;; # Ordered Options in reader-conditionals
;;

(expect "#?(:clj (list :c :d) :cljs (list :a :b))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond {:force-nl? false, :sort? true}}))

(expect "#?(:cljs (list :a :b) :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond {:force-nl? false, :sort? nil}}))

(expect
  "#?(:cljs (list :a :b) :clj (list :c :d))"
  (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
              {:parse-string? true,
               :reader-cond
                 {:force-nl? false, :sort? nil, :key-order [:clj :cljs]}}))

(expect "#?(:cljs (list :a :b)\n   :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    {:parse-string? true,
                     :reader-cond
                       {:force-nl? true, :sort? nil, :key-order [:clj :cljs]}}))

(expect
  "#?(:clj (list :c :d) :cljs (list :a :b))"
  (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
              {:parse-string? true,
               :reader-cond
                 {:force-nl? false, :sort? true, :key-order [:clj :cljs]}}))

;;
;; # Rightmost in reader conditionals
;;

(expect "#?(:cljs (list :a :b)\n   :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    40
                    {:parse-string? true}))

(expect "#?(:cljs (list :a :b) :clj (list :c :d))"
        (zprint-str "#?(:cljs (list :a :b) :clj (list :c :d))"
                    41
                    {:reader-cond {:force-nl? false}, :parse-string? true}))

;;
;; # Reader Literals
;;

(expect "#stuff/bother\n (list :this\n       \"is\"\n       a\n       :test)"
        (zprint-str "#stuff/bother (list :this \"is\" a :test)"
                    20
                    {:parse-string? true}))

(expect "#stuff/bother (list :this \"is\" a :test)"
        (zprint-str "#stuff/bother (list :this \"is\" a :test)"
                    {:parse-string? true}))

;;
;; # The third cond clause in fzprint-two-up
;;

(expect
  "(let\n  [:a\n     ;x\n     ;y\n     :b\n   :c :d]\n  (println\n    :a))"
  (zprint-str "(let [:a ;x\n;y\n :b :c :d] (println :a))"
              10
              {:parse-string? true, :comment {:inline? false}}))

;;
;; # Promise, Future, Delay
;;

(def dd (delay [:d :e]))

(expect "#<Delay pending>"
        (clojure.string/replace (zprint-str dd) #"\@[0-9a-f]*" ""))

(def ee (delay [:d :e]))
(def ff @ee)

(expect "#<Delay [:d :e]>"
        (clojure.string/replace (zprint-str ee) #"\@[0-9a-f]*" ""))

(def pp (promise))

(expect "#<Promise not-delivered>"
        (clojure.string/replace (zprint-str pp) #"\@[0-9a-f]*" ""))

(def qq (promise))
(deliver qq [:a :b])

(expect "#<Promise [:a :b]>"
        (clojure.string/replace (zprint-str qq) #"\@[0-9a-f]*" ""))

(def ff (future [:f :g]))

(Thread/sleep 500)

(expect "#<Future [:f :g]>"
        (clojure.string/replace (zprint-str ff) #"\@[0-9a-f]*" ""))

;;
;; # Agents
;;

(def ag (agent [:a :b]))

(expect "#<Agent [:a :b]>"
        (clojure.string/replace (zprint-str ag) #"\@[0-9a-f]*" ""))

(def agf (agent [:c :d]))
(send agf + 5)

(expect "#<Agent FAILED [:c :d]>"
        (clojure.string/replace (zprint-str agf) #"\@[0-9a-f]*" ""))

;;
;; # Sorting maps in code
;;

; Regular sort

(expect "{:a :b, :g :h, :j :k}"
        (zprint-str "{:g :h :j :k :a :b}" {:parse-string? true}))

; Still sorts in a list, not code 

(expect "({:a :b, :g :h, :j :k})"
        (zprint-str "({:g :h :j :k :a :b})" {:parse-string? true}))

; Doesn't sort in code (where stuff might be a function)

(expect "(stuff {:g :h, :j :k, :a :b})"
        (zprint-str "(stuff {:g :h :j :k :a :b})" {:parse-string? true}))

; Will sort in code if you tell it to

(expect "(stuff {:a :b, :g :h, :j :k})"
        (zprint-str "(stuff {:g :h :j :k :a :b})"
                    {:parse-string? true, :map {:sort-in-code? true}}))

;;
;; # Sorting sets in code
;;

; Regular sort

(expect "#{:a :b :g :h :j :k}"
        (zprint-str "#{:g :h :j :k :a :b}" {:parse-string? true}))

; Still sorts in a list, not code 

(expect "(#{:a :b :g :h :j :k})"
        (zprint-str "(#{:g :h :j :k :a :b})" {:parse-string? true}))

; Doesn't sort in code (where stuff might be a function)

(expect "(stuff #{:g :h :j :k :a :b})"
        (zprint-str "(stuff #{:g :h :j :k :a :b})" {:parse-string? true}))

; Will sort in code if you tell it to

(expect "(stuff #{:a :b :g :h :j :k})"
        (zprint-str "(stuff #{:g :h :j :k :a :b})"
                    {:parse-string? true, :set {:sort-in-code? true}}))



; contains-nil?

(expect nil (contains-nil? [:a :b :c :d]))
(expect 1 (contains-nil? [:a nil :b :c :d]))
(expect 2 (contains-nil? [:a :b nil '() :c :d]))

(def e2 {:aaaa :bbbb, :ccc :ddddd, :ee :ffffff})

(expect "{:aaaa :bbbb,\n :ccc  :ddddd,\n :ee   :ffffff}"
        (zprint-str e2 38 {:map {:justify? true}}))

(expect "{:aaaa :bbbb, :ccc :ddddd, :ee :ffffff}"
        (zprint-str e2 39 {:map {:justify? true}}))

;;
;; :wrap? for vectors
;;

(def vba1 (apply vector ba1))

(expect 48 (max-width (zprint-str vba1 48)))
(expect 3 (line-count (zprint-str vba1 48)))

(expect 4 (max-width (zprint-str vba1 48 {:vector {:wrap? nil}})))
(expect 50 (line-count (zprint-str vba1 48 {:vector {:wrap? nil}})))

;;
;; :wrap? for sets
;;

(def svba1 (set vba1))

(expect 47 (max-width (zprint-str svba1 48 {:set {:sort? false}})))
(expect 4 (line-count (zprint-str svba1 48 {:set {:sort? false}})))

(expect 5 (max-width (zprint-str svba1 48 {:set {:wrap? nil}})))
(expect 50 (line-count (zprint-str svba1 48 {:set {:wrap? nil}})))

;;
;; :wrap? for arrays
;;

(expect 48 (max-width (zprint-str ba1 48)))
(expect 3 (line-count (zprint-str ba1 48)))

(expect 4 (max-width (zprint-str ba1 48 {:array {:wrap? nil}})))
(expect 50 (line-count (zprint-str ba1 48 {:array {:wrap? nil}})))

;;
;; # indent-arg and indent-body
;;

(defn zctest5a
  "Test indents."
  [x]
  (let [abade :b
        ceered (let [b :d]
                 (if (:a x)
                   ; this is a slightly long comment
                   ; a second comment line
                   (assoc b :a :c)))]
    (list :a
          (with-meta name x)
          (vector :thisisalongkeyword :anotherlongkeyword
                  :ashorterkeyword :reallyshort)
          ; a short comment that might be long
          :c)))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n      :a\n      (with-meta name x)\n      (vector\n        :thisisalongkeyword :anotherlongkeyword\n        :ashorterkeyword :reallyshort)\n      ; a short comment that might be long\n      :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a {:list {:hang? false}}))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a
                 {:list {:hang? false, :indent-arg 1}}))

(expect
  "(defn zctest5a\n \"Test indents.\"\n [x]\n (let [abade :b\n       ceered (let [b :d]\n               (if (:a x)\n                ; this is a slightly long comment\n                ; a second comment line\n                (assoc b :a :c)))]\n  (list\n   :a\n   (with-meta name x)\n   (vector\n    :thisisalongkeyword :anotherlongkeyword\n    :ashorterkeyword :reallyshort)\n   ; a short comment that might be long\n   :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a
                 {:list {:hang? false, :indent-arg 1, :indent 1}}))

(expect
  "(defn zctest5a\n  \"Test indents.\"\n  [x]\n  (let [abade :b\n        ceered (let [b :d]\n                 (if (:a x)\n                   ; this is a slightly long comment\n                   ; a second comment line\n                   (assoc b :a :c)))]\n    (list\n     :a\n     (with-meta name x)\n     (vector\n      :thisisalongkeyword :anotherlongkeyword\n      :ashorterkeyword :reallyshort)\n     ; a short comment that might be long\n     :c)))"
  (zprint-fn-str zprint.zprint-test/zctest5a
                 {:list {:hang? false, :indent-arg 1, :indent 2}}))

;;
;; # key-ignore, key-ignore-silent
;;

;;
;; ## Basic routines
;;

(def ignore-m {:a :b, :c {:e {:f :g}, :h :i}})

(expect {:a :b, :c {:e {:f :g}, :h :zprint-ignored}}
        (map-ignore :map {:map {:key-ignore [[:c :h]]}} ignore-m))

(expect {:a :b, :c {:e {:f :g}}}
        (map-ignore :map {:map {:key-ignore-silent [[:c :h]]}} ignore-m))

(expect {:a :b, :c {:e {:f :g}}}
        (map-ignore :map {:map {:key-ignore-silent [:f [:c :h]]}} ignore-m))

(expect
  {:a :b}
  (map-ignore :map {:map {:key-ignore-silent [[:c :h] [:c :e]]}} ignore-m))

(expect {:a :b, :c {:e :zprint-ignored, :h :zprint-ignored}}
        (map-ignore :map {:map {:key-ignore [[:c :h] [:c :e]]}} ignore-m))

(expect "{:a :b, :c {:e :zprint-ignored, :h :zprint-ignored}}"
        (zprint-str ignore-m {:map {:key-ignore [[:c :h] [:c :e]]}}))

(expect "{:a :zprint-ignored, :c {:e :zprint-ignored, :h :i}}"
        (zprint-str ignore-m {:map {:key-ignore [[:c :e] :a]}}))

(expect "{:c {:h :i}}"
        (zprint-str ignore-m {:map {:key-ignore-silent [:a [:c :e :f]]}}))

;;
;; Test fix for issue #1
;;

(expect
  "(fn [arg1 arg2 arg3] [:first-keyword-in-vector\n                      :some-very-long-keyword-that-makes-this-wrap\n                      :next-keyword])"
  (zprint-str
    "(fn [arg1 arg2 arg3] [:first-keyword-in-vector :some-very-long-keyword-that-makes-this-wrap :next-keyword])"
    {:parse-string? true}))

;;
;; no-arg? test
;;

(expect
  "(-> context\n    (assoc ::error (throwable->ex-info t\n                                       execution-id\n                                       interceptor\n                                       :error)\n           ::stuff (assoc a-map\n                     :this-is-a-key :this-is-a-value))\n    (update-in [::suppressed] conj ex))"
  (zprint-str
    " (-> context (assoc ::error (throwable->ex-info t execution-id interceptor :error) ::stuff (assoc a-map :this-is-a-key :this-is-a-value)) (update-in [::suppressed] conj ex))"
    55
    {:parse-string? true}))

;;
;; Test equal size hang and flow should hang, particularly issue in
;; fzprint-hang-remaining where it was messing that up unless hang-expand was 4.0
;; instead of the 2.0.  This *should* hang up next to the do, not flow under the do.
;;

(expect
  "(do (afunction :stuff t\n               :reallybother (:rejection-type ex)\n               :downtrodden-id bits-id)\n    (-> pretext\n        (assoc ::error (catchable->my-info u\n                                           pretext-id\n                                           sceptor\n                                           :error))\n        (update-in [::expressed] con ex)))"
  (zprint-str
    "(do (afunction :stuff t :reallybother (:rejection-type ex) :downtrodden-id bits-id) (-> pretext (assoc ::error (catchable->my-info u pretext-id sceptor :error)) (update-in [::expressed] con ex)))"
    60
    {:parse-string? true}))

;;
;; Test for the bug with not calculating the size of the left part of a pair
;; correctly.  Shows up with commas in maps that fit on one line as the
;; left part of a pair.
;;

(expect
  "(defn ctest20\n  ([query-string body]\n   (let [aabcdefghijklmnopqrstuvwxyzabcdefghijkllmnpqr @(http-get query-string\n                                                                  {:body body})]\n     nil)))"
  (zprint-str
    "(defn ctest20\n ([query-string body]\n   (let [aabcdefghijklmnopqrstuvwxyzabcdefghijkllmnpqr @(http-get query-string {:body body})]\n    \n   nil)))"
    {:parse-string? true}))

;;
;; Test new :nl-separator? capability
;;

(def mx
  {:djlsfdjfkld {:jlsdfjsdlk :kjsldkfjdslk,
                 :jsldfjdlsd :ksdfjldsjkf,
                 :jslfjdsfkl :jslkdfjsld},
   :jsdlfjskdlfjldsk :jlksdfdlkfsdj,
   :lsafjsdlfj :ljsdfjsdlk})

(def my
  {:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,
                      :sjlkfjdlf :sdlkfjdsl,
                      :slkfjdlskf :slfjdsfkldsljfk},
   :djlsfdjfkld {:jlsdfjsdlk :kjsldkfjdslk,
                 :jsldfjdlsd :ksdfjldsjkf,
                 :jslfjdsfkl :jslkdfjsld},
   :jsdlfjskdlfjldsk :jlksdfdlkfsdj,
   :lsafjsdlfj :ljsdfjsdlk})

;
; This is the test for where maps only get an extra new-line when the
; right hand part of the pair gets a :flow, and you don't get an extra
; new line when the right hand part gets a hang (i.e. a multi-line hang).
;

(expect
  "{:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,\n                    :sjlkfjdlf :sdlkfjdsl,\n                    :slkfjdlskf :slfjdsfkldsljfk},\n :djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str my
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? true}}))

;
; This is the test for when any multi-line pair gets an extra new-line
; with :nl-separator? true, not just the ones that did flow.
;
#_(expect
    "{:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,\n                    :sjlkfjdlf :sdlkfjdsl,\n                    :slkfjdlskf :slfjdsfkldsljfk},\n \n :djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n \n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
    (zprint-str my
                {:map {:hang? true,
                       :force-nl? false,
                       :flow? false,
                       :indent 0,
                       :nl-separator? true}}))

(expect
  "{:adsfjdslfdfjdlsk {:jlsfjdlslfdk :jdslfdjlsdfk,\n                    :sjlkfjdlf :sdlkfjdsl,\n                    :slkfjdlskf :slfjdsfkldsljfk},\n :djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str my
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? false}}))

(expect
  "{:djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str mx
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? false}}))

(expect
  "{:djlsfdjfkld\n {:jlsdfjsdlk :kjsldkfjdslk, :jsldfjdlsd :ksdfjldsjkf, :jslfjdsfkl :jslkdfjsld},\n\n :jsdlfjskdlfjldsk :jlksdfdlkfsdj,\n :lsafjsdlfj :ljsdfjsdlk}"
  (zprint-str mx
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? false,
                     :indent 0,
                     :nl-separator? true}}))

(expect
  "{:djlsfdjfkld\n {:jlsdfjsdlk\n  :kjsldkfjdslk,\n\n  :jsldfjdlsd\n  :ksdfjldsjkf,\n\n  :jslfjdsfkl\n  :jslkdfjsld},\n\n :jsdlfjskdlfjldsk\n :jlksdfdlkfsdj,\n\n :lsafjsdlfj\n :ljsdfjsdlk}"
  (zprint-str mx
              {:map {:hang? true,
                     :force-nl? false,
                     :flow? true,
                     :indent 0,
                     :nl-separator? true}}))

;;
;; # :flow? tests
;;


(expect "(cond a b c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:flow? false},
                     :remove {:fn-gt2-force-nl #{:pair-fn}}}))

(expect "(cond a b\n      c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:flow? false}}))


; Note that this also tests that :flow? overrides the indent checks in
; fzprint-two-up, which would otherwise prevent the flow because the keys
; are only 1 character long.

(expect "(cond a\n        b\n      c\n        d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:flow? true}}))

(expect "{:abc :def, :ghi :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:flow? false}}))

(expect "{:abc\n   :def,\n :ghi\n   :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:flow? true}}))

(expect "(let [a b c d e f] (list a b c d e f))"
        (zprint-str "(let [a b c d e f] (list a b c d e f))"
                    {:parse-string? true, :binding {:flow? false}}))

(expect
  "(let [a\n        b\n      c\n        d\n      e\n        f]\n  (list a b c d e f))"
  (zprint-str "(let [a b c d e f] (list a b c d e f))"
              {:parse-string? true, :binding {:flow? true}}))

(deftype Typetest [cnt _meta]
  clojure.lang.IHashEq
    (hasheq [this] (list this))
  clojure.lang.Counted
    (count [_] cnt)
  clojure.lang.IMeta
    (meta [_] _meta))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? false}}))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? true}}))

;;
;; # :force-nl? tests
;;


(expect "(cond a b c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:force-nl? false},
                     :remove {:fn-gt2-force-nl #{:pair-fn}}}))

(expect "(cond a b\n      c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:force-nl? false}}))

(expect "(cond a b\n      c d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true, :pair {:force-nl? true}}))

(expect "{:abc :def, :ghi :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:force-nl? false}}))

(expect "{:abc :def,\n :ghi :ijk}"
        (zprint-str {:abc :def, :ghi :ijk} {:map {:force-nl? true}}))

(expect "(let [a b c d e f] (list a b c d e f))"
        (zprint-str "(let [a b c d e f] (list a b c d e f))"
                    {:parse-string? true, :binding {:force-nl? false}}))

(expect "(let [a b\n      c d\n      e f]\n  (list a b c d e f))"
        (zprint-str "(let [a b c d e f] (list a b c d e f))"
                    {:parse-string? true, :binding {:force-nl? true}}))

;;
;; Test to see if either :flow? true or :force-nl? true will force new lines
;; in :arg2-extend functions.
;;
;; This tests zprint.zprint/allow-one-line? and the map associated with it,
;; fn-style->caller.
;;

(expect
  "(deftype Typetest [cnt _meta] clojure.lang.IHashEq (hasheq [this] (list this)) clojure.lang.Counted (count [_] cnt) clojure.lang.IMeta (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest
                 200
                 {:extend {:flow? false, :force-nl? false}}))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this] (list this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest
                 200
                 {:extend {:flow? false, :force-nl? true}}))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest
                 200
                 {:extend {:flow? true, :force-nl? true}}))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest
                 200
                 {:extend {:flow? true, :force-nl? false}}))



;;
;; # :nl-separator? tests
;;

(expect "(cond a\n        b\n      c\n        d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? false}}))

(expect "(cond a\n        b\n\n      c\n        d)"
        (zprint-str "(cond a b c d)"
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? true}}))

(expect "{:abc\n   :def,\n :ghi\n   :ijk}"
        (zprint-str {:abc :def, :ghi :ijk}
                    {:map {:flow? true, :nl-separator? false}}))

(expect "{:abc\n   :def,\n\n :ghi\n   :ijk}"
        (zprint-str {:abc :def, :ghi :ijk}
                    {:map {:flow? true, :nl-separator? true}}))

(expect
  "(let [a\n        b\n      c\n        d\n      e\n        f]\n  (list a b c d e f))"
  (zprint-str "(let [a b c d e f] (list a b c d e f))"
              {:parse-string? true,
               :binding {:flow? true, :nl-separator? false}}))

(expect
  "(let [a\n        b\n\n      c\n        d\n\n      e\n        f]\n  (list a b c d e f))"
  (zprint-str "(let [a b c d e f] (list a b c d e f))"
              {:parse-string? true,
               :binding {:flow? true, :nl-separator? true}}))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest {:extend {:flow? true}}))

(expect
  "(deftype Typetest [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] (list this))\n\n  clojure.lang.Counted\n    (count [_] cnt)\n\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest
                 {:extend {:flow? true, :nl-separator? true}}))

;;
;; # Does :flow? and :nl-separator? work for constant pairs?
;;

(expect "(println :this :should\n         :constant :pair)"
        (zprint-str "(println :this :should :constant :pair)"
                    37
                    {:parse-string? true, :pair {:flow? false}}))

(expect
  "(println :this\n           :should\n         :constant\n           :pair)"
  (zprint-str "(println :this :should :constant :pair)"
              37
              {:parse-string? true, :pair {:flow? true}}))

(expect
  "(println :this\n           :should\n         :constant\n           :pair)"
  (zprint-str "(println :this :should :constant :pair)"
              37
              {:parse-string? true, :pair {:flow? true, :nl-separator? false}}))

(expect
  "(println :this\n           :should\n\n         :constant\n           :pair)"
  (zprint-str "(println :this :should :constant :pair)"
              37
              {:parse-string? true, :pair {:flow? true, :nl-separator? true}}))

(expect "(println\n  :this\n    :should\n  :constant\n    :pair)"
        (zprint-str "(println :this :should :constant :pair)"
                    15
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? false}}))

(expect "(println\n  :this\n    :should\n\n  :constant\n    :pair)"
        (zprint-str "(println :this :should :constant :pair)"
                    15
                    {:parse-string? true,
                     :pair {:flow? true, :nl-separator? true}}))

;;
;; # :extend -- support :hang? for :extend
;;


(deftype Typetest1 [cnt _meta]
  clojure.lang.IHashEq
    (hasheq [this] (list this) (list this this) (list this this this this))
  clojure.lang.Counted
    (count [_] cnt)
  clojure.lang.IMeta
    (meta [_] _meta))

(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq (hasheq [this]\n                         (list this)\n                         (list this this)\n                         (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest1
                 60
                 {:extend {:flow? false, :hang? true}}))

(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted (count [_] cnt)\n  clojure.lang.IMeta (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest1
                 60
                 {:extend {:flow? false, :hang? false}}))

(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest1
                 60
                 {:extend {:flow? true, :hang? true}}))

(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this]\n      (list this)\n      (list this this)\n      (list this this this this))\n  clojure.lang.Counted\n    (count [_] cnt)\n  clojure.lang.IMeta\n    (meta [_] _meta))"
  (zprint-fn-str zprint.zprint-test/->Typetest1
                 60
                 {:extend {:flow? true, :hang? false}}))

;;
;; # Test a variant form of cond with :nl-separator?
;;

(defn zctest8x
  []
  (let [a (list 'with 'arguments)
        foo nil
        bar true
        baz "stuff"
        other 1
        bother 2
        stuff 3
        now 4
        output 5
        b 3
        c 5
        this "is"]
    (cond (or foo bar baz) (format output now)
          :let [stuff (and bother foo bar)
                bother (or other output foo)]
          (and a b c (bother this)) (format other stuff))
    (list a :b :c "d")))

(expect
  "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
  (zprint-fn-str zprint.zprint-test/zctest8x 20))

(expect
  "(defn zctest8x\n  []\n  (let\n    [a (list\n         'with\n         'arguments)\n     foo nil\n     bar true\n     baz \"stuff\"\n     other 1\n     bother 2\n     stuff 3\n     now 4\n     output 5\n     b 3\n     c 5\n     this \"is\"]\n    (cond\n      (or foo\n          bar\n          baz)\n        (format\n          output\n          now)\n\n      :let\n        [stuff\n           (and\n             bother\n             foo\n             bar)\n         bother\n           (or\n             other\n             output\n             foo)]\n\n      (and a\n           b\n           c\n           (bother\n             this))\n        (format\n          other\n          stuff))\n    (list a\n          :b\n          :c\n          \"d\")))"
  (zprint-fn-str zprint.zprint-test/zctest8x 20 {:pair {:nl-separator? true}}))

;;
;; # Issue 17
;;
;; There should be no completely blank lines in the output for this function.
;;

(defn zpair-tst
  []
  (println (list :ajfkdkfdj :bjlfkdsfjsdl)
           (list :cjslkfsdjl :dklsdfjsdsjsldf)
           [:ejlkfjdsfdfklfjsljfsd :fjflksdfjlskfdjlk]
           :const1 "stuff"
           :const2 "bother"))

(expect
  "(defn zpair-tst\n  []\n  (println\n    (list :ajfkdkfdj\n          :bjlfkdsfjsdl)\n    (list :cjslkfsdjl\n          :dklsdfjsdsjsldf)\n    [:ejlkfjdsfdfklfjsljfsd\n     :fjflksdfjlskfdjlk]\n    :const1 \"stuff\"\n    :const2 \"bother\"))"
  (zprint-fn-str zprint.zprint-test/zpair-tst 30 {:pair {:nl-separator? true}}))

;
; Should be one blank line here
;

(expect
  "(defn zpair-tst\n  []\n  (println\n    (list\n      :ajfkdkfdj\n      :bjlfkdsfjsdl)\n    (list\n      :cjslkfsdjl\n      :dklsdfjsdsjsldf)\n    [:ejlkfjdsfdfklfjsljfsd\n     :fjflksdfjlskfdjlk]\n    :const1\n      \"stuff\"\n\n    :const2\n      \"bother\"))"
  (zprint-fn-str zprint.zprint-test/zpair-tst 17 {:pair {:nl-separator? true}}))

;;
;; # {:extend {:modifers #{"static"}}} Tests
;;

(def zextend-tst1
  '(deftype Foo [a b c]
     P
       (foo [this] a)
     Q
       (bar-me [this] b)
       (bar-me [this y] (+ c y))
     R
     S
       (baz [this] a)
     static T
       (baz-it [this] b)
     static V
       (baz-it [this] b)
       (bar-none [this] a)
     stuff
     Q
     R
       (fubar [this] it)))


(expect
  "(deftype Foo [a b c]\n  P (foo [this] a)\n  Q\n    (bar-me [this] b)\n    (bar-me [this y] (+ c y))\n  R\n  S (baz [this] a)\n  static T (baz-it [this] b)\n  static V\n    (baz-it [this] b)\n    (bar-none [this] a)\n  stuff\n  Q\n  R (fubar [this] it))"
  (zprint-str zprint.zprint-test/zextend-tst1 {:extend {:flow? false}}))

(expect
  "(deftype Foo [a b c]\n  P\n    (foo [this] a)\n  Q\n    (bar-me [this] b)\n    (bar-me [this y] (+ c y))\n  R\n  S\n    (baz [this] a)\n  static T\n    (baz-it [this] b)\n  static V\n    (baz-it [this] b)\n    (bar-none [this] a)\n  stuff\n  Q\n  R\n    (fubar [this] it))"
  (zprint-str zprint.zprint-test/zextend-tst1 {:extend {:flow? true}}))

;
; What happens if the modifier and the first element don't fit on the same line?
;

(expect
  "(deftype bax [a b c]\n  static this-is-very-long-and-should-not-work\n    (baz-it [this] b))"
  (zprint-str
    "(deftype bax [a b c] static this-is-very-long-and-should-not-work (baz-it [this] b))"
    {:parse-string? true}))

(expect
  "(deftype bax [a b c]\n  static\n    this-is-very-long-and-should-not-work\n    (baz-it [this] b))"
  (zprint-str
    "(deftype bax [a b c] static this-is-very-long-and-should-not-work (baz-it [this] b))"
    45
    {:parse-string? true}))

;
; Test removal of a modifier to see both that it works and confirm that
; removing it produces the right result.
;

(expect
  "(deftype Foo [a b c]\n  P (foo [this] a)\n  Q\n    (bar-me [this] b)\n    (bar-me [this y] (+ c y))\n  R\n  S (baz [this] a)\n  static\n  T (baz-it [this] b)\n  static\n  V\n    (baz-it [this] b)\n    (bar-none [this] a)\n  stuff\n  Q\n  R (fubar [this] it))"
  (zprint-str zprint.zprint-test/zextend-tst1
              {:remove {:extend {:modifiers #{"static"}}},
               :extend {:flow? false}}))

;;
;; # Tests for key-color and key-depth-color
;;

; key-depth-color


(defn key-color-tst
  []
  {:abc
     ;stuff
     :bother,
   "deep" {"and" "even", :deeper {"that" :is, :just "the", "way" :it-is}},
   "def" "ghi",
   5 "five",
   ["hi"] "there"})

; :key-depth-color []

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["" :none :whitespace] ["]" :purple :right] ["\n  " :none :indent]
   ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :indent]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   [", " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] [", " :none :whitespace]
   [":just" :magenta :element] [" " :none :whitespace] ["\"the\"" :red :element]
   [", " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-fn-str zprint.zprint-test/key-color-tst
                  {:map {:key-depth-color []}, :return-cvec? true}))

; :key-depth-color [:blue :yellow :green]

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["" :none :whitespace] ["]" :purple :right] ["\n  " :none :indent]
   ["{" :red :left] [":abc" :blue :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :indent]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :blue :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :yellow :element] [" " :none :whitespace]
   ["\"even\"" :red :element] [", " :none :whitespace]
   [":deeper" :yellow :element] [" " :none :whitespace] ["{" :red :left]
   ["\"that\"" :green :element] [" " :none :whitespace]
   [":is" :magenta :element] [", " :none :whitespace] [":just" :green :element]
   [" " :none :whitespace] ["\"the\"" :red :element] [", " :none :whitespace]
   ["\"way\"" :green :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :blue :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :blue :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-fn-str zprint.zprint-test/key-color-tst
                  {:map {:key-depth-color [:blue :yellow :green]},
                   :return-cvec? true}))

; :key-color {}

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["" :none :whitespace] ["]" :purple :right] ["\n  " :none :indent]
   ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :indent]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   [", " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] [", " :none :whitespace]
   [":just" :magenta :element] [" " :none :whitespace] ["\"the\"" :red :element]
   [", " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-fn-str zprint.zprint-test/key-color-tst
                  {:map {:key-color {}}, :return-cvec? true}))

; :key-color {:abc :blue "deep" :cyan 5 :green}

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["" :none :whitespace] ["]" :purple :right] ["\n  " :none :indent]
   ["{" :red :left] [":abc" :blue :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :indent]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :cyan :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   [", " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] [", " :none :whitespace]
   [":just" :magenta :element] [" " :none :whitespace] ["\"the\"" :red :element]
   [", " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :green :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-fn-str zprint.zprint-test/key-color-tst
                  {:map {:key-color {:abc :blue, "deep" :cyan, 5 :green}},
                   :return-cvec? true}))

; Test out nil's in the :key-depth-color vector, and if :key-color values
; will override what is in :key-depth-color

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["" :none :whitespace] ["]" :purple :right] ["\n  " :none :indent]
   ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :indent]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   [", " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] [", " :none :whitespace]
   [":just" :magenta :element] [" " :none :whitespace] ["\"the\"" :red :element]
   [", " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :cyan :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-fn-str zprint.zprint-test/key-color-tst
                  {:map {:key-depth-color [:blue nil :green],
                         :key-color {"def" :cyan}},
                   :return-cvec? true}))

;;
;; Issue #23 -- can't justify a map that has something too big
;;
;; Bug was added in 0.3.0 when pmap showed up
;;

(expect "{:a\n   \"this is a pretty long string\",\n :b :c}"
        (zprint-str {:a "this is a pretty long string", :b :c}
                    30
                    {:map {:justify? true}, :parallel? false}))

;;
;; Test :arg2-pair, see if both data and string versions of zthird
;; work, essentially.
;;

(expect "(defn test-condp\n  [x y]\n  (condp = 1\n    1 :pass\n    2 :fail))"
        (zprint-str '(defn test-condp [x y] (condp = 1 1 :pass 2 :fail)) 20))

(expect "(defn test-condp\n  [x y]\n  (condp = 1\n    1 :pass\n    2 :fail))"
        (zprint-str "(defn test-condp [x y] (condp = 1 1 :pass 2 :fail))"
                    20
                    {:parse-string? true}))

;;
;; Issue #25 -- problem with printing (fn ...) when it is an s-expression
;; but not when it is a string.  concat-no-nil contains a (fn ...) 
;;

(expect (read-string (source-fn 'zprint.zprint/concat-no-nil))
        (read-string (zprint-str (read-string
                                   (source-fn 'zprint.zprint/concat-no-nil)))))

;;
;; Try a large function to see if we can do code in s-expressions correctly
;;

(expect
  (trim-gensym-regex (read-string (source-fn 'zprint.zprint/fzprint-list*)))
  (read-string (zprint-str (trim-gensym-regex
                             (read-string (source-fn
                                            'zprint.zprint/fzprint-list*))))))

;;
;; # key-value-color
;;
;; When you find this key, use the color map associated with it when formatting
;; the value.
;;

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["" :none :whitespace] ["]" :purple :right] ["\n  " :none :indent]
   ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :indent]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   [", " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :magenta :element] [", " :none :whitespace]
   [":just" :magenta :element] [" " :none :whitespace] ["\"the\"" :red :element]
   [", " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :magenta :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :blue :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-fn-str zprint.zprint-test/key-color-tst
                  {:map {:key-value-color {["hi"] {:string :blue}}},
                   :return-cvec? true}))

(expect
  [["(" :green :left] ["defn" :blue :element] [" " :none :whitespace]
   ["key-color-tst" :black :element] ["\n  " :none :indent] ["[" :purple :left]
   ["" :none :whitespace] ["]" :purple :right] ["\n  " :none :indent]
   ["{" :red :left] [":abc" :magenta :element] ["\n     " :none :indent]
   [";stuff" :green :comment] ["\n     " :none :indent]
   [":bother" :magenta :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["\"deep\"" :red :element] [" " :none :whitespace] ["{" :red :left]
   ["\"and\"" :red :element] [" " :none :whitespace] ["\"even\"" :red :element]
   [", " :none :whitespace] [":deeper" :magenta :element]
   [" " :none :whitespace] ["{" :red :left] ["\"that\"" :red :element]
   [" " :none :whitespace] [":is" :blue :element] [", " :none :whitespace]
   [":just" :blue :element] [" " :none :whitespace] ["\"the\"" :red :element]
   [", " :none :whitespace] ["\"way\"" :red :element] [" " :none :whitespace]
   [":it-is" :blue :element] ["}" :red :right] ["}" :red :right]
   ["," :none :whitespace] ["\n   " :none :indent] ["\"def\"" :red :element]
   [" " :none :whitespace] ["\"ghi\"" :red :element] ["," :none :whitespace]
   ["\n   " :none :indent] ["5" :purple :element] [" " :none :whitespace]
   ["\"five\"" :red :element] ["," :none :whitespace] ["\n   " :none :indent]
   ["[" :purple :left] ["\"hi\"" :red :element] ["]" :purple :right]
   [" " :none :whitespace] ["\"there\"" :red :element] ["}" :red :right]
   [")" :green :right]]
  (czprint-fn-str zprint.zprint-test/key-color-tst
                  {:map {:key-value-color {:deeper {:keyword :blue}}},
                   :return-cvec? true}))

;;
;; # Namespaced key tests
;;

;;
;; First, the parse-string tests
;;

(expect "(list #:x{:a :b, :c :d})"
        (zprint-str "(list {:x/a :b :x/c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? true}}))

(expect "(list {:x/a :b, :x/c :d})"
        (zprint-str "(list {:x/a :b :x/c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? false}}))

(expect "(list {::a :b, ::c :d})"
        (zprint-str "(list {::a :b ::c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? true}}))

(expect "(list {::a :b, ::c :d})"
        (zprint-str "(list {::a :b ::c :d})"
                    {:parse-string? true,
                     :map {:lift-ns? true, :lift-ns-in-code? false}}))

(expect "{::a :b, ::c :d}"
        (zprint-str "{::a :b ::c :d}"
                    {:parse-string? true, :map {:lift-ns? true}}))

(expect "{:x/a :b, :y/c :d}"
        (zprint-str "{:x/a :b :y/c :d}"
                    {:parse-string? true, :map {:lift-ns? true}}))

(expect "#:x{:a :b, :c :d}"
        (zprint-str "{:x/a :b :x/c :d}"
                    {:parse-string? true, :map {:lift-ns? true}}))

;;
;; Second, the repl s-expression tests
;;

(expect "#:zprint.zprint-test{:a :b, :c :d}"
        (zprint-str {::a :b, ::c :d} {:map {:lift-ns? true}}))

(expect "{:zprint.zprint-test/a :b, :zprint.zprint-test/c :d}"
        (zprint-str {::a :b, ::c :d} {:map {:lift-ns? false}}))

(expect "#:zprint.zprint-test{:a :b, :c :d}"
        (zprint-str {::a :b, ::c :d} {:map {:lift-ns? true}}))

(expect "{:x/a :b, :zprint.zprint-test/c :d}"
        (zprint-str {:x/a :b, ::c :d} {:map {:lift-ns? true}}))

(expect "#:x{:a :b, :c :d}"
        (zprint-str {:x/a :b, :x/c :d} {:map {:lift-ns? true}}))

(expect "{:x/a :b, :x/c :d}"
        (zprint-str {:x/a :b, :x/c :d} {:map {:lift-ns? false}}))

(expect "{:c :d, :x/a :b}"
        (zprint-str {:x/a :b, :c :d} {:map {:lift-ns? true}}))

;;
;; # condp
;;
;; Handling :>> in condp
;;

(expect "(condp a b\n  cdkjdfksjkdf :>> djkdsjfdlsjkl\n  e)"
        (zprint-str "(condp a b cdkjdfksjkdf :>> djkdsjfdlsjkl e)"
                    40
                    {:parse-string? true}))

(expect "(condp a b\n  cdkjdfksjkdf :>>\n    djkdsjfdlsjkl\n  e)"
        (zprint-str "(condp a b cdkjdfksjkdf :>> djkdsjfdlsjkl e)"
                    30
                    {:parse-string? true}))

(expect "(condp a b\n  cdkjdfksjkdf\n    :>>\n    djkdsjfdlsjkl\n  e)"
        (zprint-str "(condp a b cdkjdfksjkdf :>> djkdsjfdlsjkl e)"
                    15
                    {:parse-string? true}))

;;
;; # Commas (Issue #31)
;;
;; Even though commas were turned off, it still needed space for the comma.
;;

(expect 20
        (max-width (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                               20
                               {:map {:comma? false}})))
(expect 2
        (line-count (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                                20
                                {:map {:comma? false}})))

(expect 21
        (max-width (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                               21
                               {:map {:comma? true}})))
(expect 2
        (line-count (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                                21
                                {:map {:comma? true}})))

(expect 14
        (max-width (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                               20
                               {:map {:comma? true}})))
(expect 3
        (line-count (zprint-str {:abcdefg :hijklmnop, :edc :kkk}
                                20
                                {:map {:comma? true}})))

;;
;; # (czprint nil) doesn't print "nil" (Issue #32)
;;

(expect "nil" (zprint-str nil))
(expect [["nil" :yellow :element]] (czprint-str nil {:return-cvec? true}))

;;
;; # Inline Comments
;;

(defn zctest9
  "Test inline comments"
  []
  (let [a (list 'with 'arguments)
        foo nil ; end of line comment
        bar true
        baz "stuff"
        other 1
        bother 2 ; a really long inline comment that should wrap about here
        stuff 3
        ; a non-inline comment
        now ;a middle inline comment
          4
        ; Not an inline comment
        output 5
        b 3
        c 5
        this "is"]
    (cond (or foo bar baz) (format output now)  ;test this
          :let [stuff (and bother foo bar) ;test that
                bother (or other output foo)] ;and maybe the other
          (and a b c (bother this)) (format other stuff))
    (list a :b :c "d")))

(expect
  "(defn zctest9\n  \"Test inline comments\"\n  []\n  (let [a (list 'with 'arguments)\n        foo nil ; end of line comment\n        bar true\n        baz \"stuff\"\n        other 1\n        bother 2 ; a really long inline comment that should wrap about\n                 ; here\n        stuff 3\n        ; a non-inline comment\n        now ;a middle inline comment\n          4\n        ; Not an inline comment\n        output 5\n        b 3\n        c 5\n        this \"is\"]\n    (cond (or foo bar baz) (format output now)  ;test this\n          :let [stuff (and bother foo bar) ;test that\n                bother (or other output foo)] ;and maybe the other\n          (and a b c (bother this)) (format other stuff))\n    (list a :b :c \"d\")))"
  (zprint-fn-str zprint.zprint-test/zctest9 70 {:comment {:inline? true}}))

(expect
  "(defn zctest9\n  \"Test inline comments\"\n  []\n  (let [a (list 'with 'arguments)\n        foo nil\n        ; end of line comment\n        bar true\n        baz \"stuff\"\n        other 1\n        bother 2\n        ; a really long inline comment that should wrap about here\n        stuff 3\n        ; a non-inline comment\n        now\n          ;a middle inline comment\n          4\n        ; Not an inline comment\n        output 5\n        b 3\n        c 5\n        this \"is\"]\n    (cond (or foo bar baz) (format output now)\n          ;test this\n          :let [stuff (and bother foo bar)\n                ;test that\n                bother (or other output foo)]\n          ;and maybe the other\n          (and a b c (bother this)) (format other stuff))\n    (list a :b :c \"d\")))"
  (zprint-fn-str zprint.zprint-test/zctest9 70 {:comment {:inline? false}}))

;
; Maps too
;

(defn zctest10
  "Test maps with inline comments."
  []
  {:a :b,
   ; single line comment
   :d :e, ; stuff
   :f :g, ; bother
   :i ;middle
     :j})


(expect
  "(defn zctest10\n  \"Test maps with inline comments.\"\n  []\n  {:a :b,\n   ; single line comment\n   :d :e,\n   ; stuff\n   :f :g,\n   ; bother\n   :i\n     ;middle\n     :j})"
  (zprint-fn-str zprint.zprint-test/zctest10 {:comment {:inline? false}}))

(expect
  "(defn zctest10\n  \"Test maps with inline comments.\"\n  []\n  {:a :b,\n   ; single line comment\n   :d :e, ; stuff\n   :f :g, ; bother\n   :i ;middle\n     :j})"
  (zprint-fn-str zprint.zprint-test/zctest10 {:comment {:inline? true}}))

;;
;; Rum :arg1-mixin tests
;;

;; Define things to test (note that these are all
;; structures, not zipper tests, but we also do zipper
;; tests by specifying the strings to zprint-str after
;; the tests with structures).

(def cz1
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     < rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz2
  '(rum/defcs component
     < rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz3
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz4
  '(rum/defcs component
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz5
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     <
     rum/static
     rum/reactive
     (rum/local 0 :count)
     (rum/local "" :text)
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz6
  '(rum/defcs component
     <
     rum/static
     rum/reactive
     (rum/local 0 :count)
     (rum/local "" :text)
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz7
  '(rum/defcs component
              (let [count-atom (:count state) text-atom (:text state)] [:div])))

(def cz8
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     < rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
     ([state label]
      (let [count-atom (:count state) text-atom (:text state)] [:div]))
     ([state] (component state nil))))

(def cz9
  '(rum/defcs component
     "This is a component with a doc-string!  How unusual..."
     {:a :b,
      "this" [is a test],
      :c [this is a very long vector how do you suppose it will work]}
      rum/static
      rum/reactive
      (rum/local 0 :count)
      (rum/local "" :text)
     [state label]
     (let [count-atom (:count state) text-atom (:text state)] [:div])))

;;
;; Does it work with structures
;;

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz1))

(expect
  "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz2))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz3))

(expect
  "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz4))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz5))

(expect
  "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz6))

(expect
  "(rum/defcs component\n           (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz7))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
  (zprint-str cz8))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   \"this\" [is a test],\n   :c [this is a very long vector how do you suppose it will work]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str cz9))

;;
;; Does it all work with zippers?
;;

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n           (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n           (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
    {:parse-string? true}))

(expect
  "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   \"this\" [is a test],\n   :c [this is a very long vector how do you suppose it will work]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
  (zprint-str
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   \"this\" [is a test],\n   :c [this is a very long vector how do you suppose it will work]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    {:parse-string? true}))

;;
;; # Respect newline in vectors
;;

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)] [:div \"height\" (int 6)] [:div \"weight\" (int 7)]]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi \n [:div \n  [:div :e (int 5)] \n  [:div  \n\"height\" (int 6)] \n  [:div  \n\"weight\" (int 7)] \n] \n[:div :a :b :c]]]]"
    {:parse-string? true}))

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div\n    [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)]\n    [:div\n     \"weight\" (int 7)]\n   ]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi \n [:div \n  [:div :e (int 5)] \n  [:div  \n\"height\" (int 6)] \n  [:div  \n\"weight\" (int 7)] \n] \n[:div :a :b :c]]]]"
    {:parse-string? true, :vector {:respect-nl? true}}))

;;[:dev.very.top
;; [:dev.top
;;  [:dev.bmi
;;   [:div [:div :e (int 5)] [:div "height" (int 6)] [:div "weight" (int 7)]]
;;   [:div :a :b :c]]]]

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)] [:div \"height\" (int 6)] [:div \"weight\" (int 7)]]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true}))

;;[:dev.very.top
;; [:dev.top
;;  [:dev.bmi
;;   [:div [:div :e (int 5)]
;;    [:div
;;     "height" (int 6)] [:div "weight" (int 7)]] [:div :a :b :c]]]]

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)] [:div \"weight\" (int 7)]] [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true, :vector {:respect-nl? true}}))

;;[:dev.very.top
;; [:dev.top
;;  [:dev.bmi
;;   [:div [:div :e (int 5)]
;;    [:div
;;     "height" (int 6)]
;;    [:div "weight" (int 7)]]
;;   [:div :a :b :c]]]]

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)]\n    [:div \"weight\" (int 7)]]\n   [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true,
     :vector {:respect-nl? true, :wrap-after-multi? false}}))

;;
;; option-fn-first, embedded in :style :keyword-respect-nl
;;

(expect
  "[:dev.very.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)] [:div \"weight\" (int 7)]] [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.very.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true, :style :keyword-respect-nl}))

;;
;; almost the same as above, but explicitly with :repect-nl? enabled
;;

(expect
  "[:dev.v.top\n [:dev.top\n  [:dev.bmi\n   [:div [:div :e (int 5)]\n    [:div\n     \"height\" (int 6)] [:div \"weight\" (int 7)]] [:div :a :b :c]]]]"
  (zprint-str
    "[:dev.v.top [:dev.top [:dev.bmi [:div [:div :e (int 5)] [:div  \n\"height\" (int 6)] [:div \"weight\" (int 7)] ] [:div :a :b :c]]]]"
    {:parse-string? true, :vector {:respect-nl? true}}))

;;
;; validation for option-fn-first return
;;

(expect
  "java.lang.Exception: Options resulting from :vector :option-fn-first called with :g had these errors: In the key-sequence [:vector :sort?] the key :sort? was not recognized as valid!"
  (try (zprint-str "[:g :f :d :e :e \n :t :r :a :b]"
                   {:parse-string? true,
                    :vector {:respect-nl? true,
                             :option-fn-first
                               #(do %1 %2 (identity {:vector {:sort? true}}))}})
       (catch Exception e (str e))))


;;
;; # zprint-file-str tests
;;

(expect
  ";!zprint {:format :next :vector {:wrap? false}}\n\n(def help-str-readable\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
  (zprint-file-str
    ";!zprint {:format :next :vector {:wrap? false}}\n\n(def help-str-readable\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
    "test"))

;;
;; :format :next
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :next :vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :next :vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; :format :off
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; :format :on
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :on}\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :off}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :on}\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; :format :skip
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :skip}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:format :skip}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; Change format for the rest of the file (or rest of the string)
;;
;; Note that the next test depends on this one (where the next one ensures that
;; the values set into the options map in this test don't bleed out into the
;; the environment beyond this call to zprint-file-str).
;;

(expect
  "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n                   \"\" \"   (zprint x)\" \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n;!zprint {:vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about)\n                   \"\"\n                   \" The basic call uses defaults, prints to stdout\"\n                   \"\"\n                   \"   (zprint x)\"\n                   \"\"\n                   \" All zprint functions also allow the following arguments:\"\n                   \"\"\n                   \"   (zprint x <width>)\"\n                   \"   (zprint x <width> <options>)\"\n                   \"   (zprint x <options>)\"]))\n"
  (zprint-file-str
    "(def h1\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n;!zprint {:vector {:wrap? false}}\n\n(def h2\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n(def h3\n  (vec-str-to-str [(about) \"\" \" The basic call uses defaults, prints to stdout\"\n   \"\" \"   (zprint x)\" \"\"\n   \" All zprint functions also allow the following arguments:\"\n   \"\" \"   (zprint x <width>)\" \"   (zprint x <width> <options>)\"\n   \"   (zprint x <options>)\"]))\n\n"
    "test"))

;;
;; See if removing wrap in the previous test bleeds out into the environment.
;;
;; If I change the code to cause {:vector {:wrap? false}} to bleen out from 
;; the previous test, this next test *does* fail, so we can be sure that it
;; will verify this.
;;

(expect true (:wrap? (:vector (zprint.config/get-options))))

;;
;; # Tests for max length as a single number
;;

;; List with constant pair

(expect
  "(abc sdfjsksdfjdskl\n     jkfjdsljdlfjldskfjklsjfjd\n     :a (quote b)\n     :c (quote d)\n     :e (quote f)\n     :g (quote h)\n     :i (quote j))"
  (zprint-str '(abc sdfjsksdfjdskl
                    jkfjdsljdlfjldskfjklsjfjd
                    :a 'b
                    :c 'd
                    :e 'f
                    :g 'h
                    :i 'j)
              {:max-length 13}))

(expect
  "(abc sdfjsksdfjdskl\n     jkfjdsljdlfjldskfjklsjfjd\n     :a (quote b)\n     :c (quote d)\n     :e (quote f)\n     :g (quote h)\n     :i ...)"
  (zprint-str '(abc sdfjsksdfjdskl
                    jkfjdsljdlfjldskfjklsjfjd
                    :a 'b
                    :c 'd
                    :e 'f
                    :g 'h
                    :i 'j)
              {:max-length 12}))

;; Map

(expect "{:a 1, :b 2, :c 3, :d 4, ...}"
        (zprint-str {:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}
                    {:max-length 4}))

(expect "{:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, ...}"
        (zprint-str {:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}
                    {:max-length 8}))

(expect "{:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}"
        (zprint-str {:a 1, :b 2, :c 3, :d 4, :e 5, :f 6, :g 7, :h 8, :i 9}
                    {:max-length 9}))

;; Set

(expect "#{:a :b :c :d ...}"
        (zprint-str #{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}
                    {:max-length 4}))

(expect "#{:a :b :c :d :e :f :g :h :i :j :k :l :m :n ...}"
        (zprint-str #{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}
                    {:max-length 14}))

(expect "#{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}"
        (zprint-str #{:a :b :c :d :e :f :g :h :i :j :k :l :m :n :o}
                    {:max-length 15}))

;; Vector

(expect "[:a :b :c :d :e ...]"
        (zprint-str [:a :b :c :d :e :f :g :h :i :j :k] {:max-length 5}))

(expect "[:a :b :c :d :e :f :g :h :i :j ...]"
        (zprint-str [:a :b :c :d :e :f :g :h :i :j :k] {:max-length 10}))

(expect "[:a :b :c :d :e :f :g :h :i :j :k]"
        (zprint-str [:a :b :c :d :e :f :g :h :i :j :k] {:max-length 11}))

;; List, multi-level, zipper (i.e. :parse-string? true)

(expect "(a b (c ...) i j ...)"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [5 1 0]}))

(expect "(a b (c ...) i j (k ...))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 1 0]}))

(expect "(a b (c d ...) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 2 0]}))

(expect "(a b (c d ##) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 0]}))

(expect "(a b (c d (e ...)) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 1 0]}))

(expect "(a b (c d (e f ##)) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3 0]}))

(expect "(a b (c d (e f (g h))) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3]}))

(expect "(a b (c d (e f (g ...))) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3 1 0]}))

(expect "(a b (c d (e f (g h))) i j (k l))"
        (zprint-str "(a b (c d (e f (g h))) i j (k l))"
                    {:parse-string? true, :max-length [6 3 3 2 0]}))

;; set, multi-level

(expect "#{#{#{## ...} :c} :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 2 1 0]}))

(expect "#{#{## :c} :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 2 0]}))

(expect "#{#{## ...} :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 1 0]}))

(expect "#{## :a :j ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [3 0]}))

(expect "#{## ...}"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [1 0]}))

(expect "##"
        (zprint-str #{#{:c #{:e #{:f :g :h} :i}} :a :j :k :l :m :n :o :p :q :r
                      :s :t :u :v :w :x :y}
                    {:max-length [0]}))


;; map, multi-level


(expect "{#{#{## ...} :c} :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 2 1 0]}))

(expect "{#{## :c} :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 2 0]}))

(expect "{#{## ...} :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 1 0]}))

(expect "{## :a, :j :k, :l :m, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [3 0]}))

(expect "{## :a, ...}"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [1 0]}))

(expect "##"
        (zprint-str {#{:c #{:e #{:f :g :h} :i}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [0]}))


(expect
  "{:j :k,\n :l :m,\n :n :o,\n :p :q,\n :r :s,\n :t :u,\n :v :w,\n :x :y,\n {:c {:e {:f :g, :h :i}, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2]}))

(expect
  "{:j :k,\n :l :m,\n :n :o,\n :p :q,\n :r :s,\n :t :u,\n :v :w,\n :x :y,\n {:c {:e {:f :g, ...}, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2 1]}))

(expect
  "{:j :k,\n :l :m,\n :n :o,\n :p :q,\n :r :s,\n :t :u,\n :v :w,\n :x :y,\n {:c {:e {:f :g, ...}, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2 1 0]}))

(expect
  "{:j :k, :l :m, :n :o, :p :q, :r :s, :t :u, :v :w, :x :y, {:c {:e ##, :i :j}} :a}"
  (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
               :j :k,
               :l :m,
               :n :o,
               :p :q,
               :r :s,
               :t :u,
               :v :w,
               :x :y}
              {:max-length [10 3 2 0]}))

(expect "{:j :k, :l :m, :n :o, :p :q, :r :s, :t :u, :v :w, :x :y, {:c ##} :a}"
        (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [10 3 0]}))

(expect "{:j :k, :l :m, :n :o, :p :q, :r :s, :t :u, :v :w, :x :y, ## :a}"
        (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [10 0]}))

(expect "{:j :k, :l :m, :n :o, :p :q, :r :s, ...}"
        (zprint-str {{:c {:e {:f :g, :h :i}, :i :j}} :a,
                     :j :k,
                     :l :m,
                     :n :o,
                     :p :q,
                     :r :s,
                     :t :u,
                     :v :w,
                     :x :y}
                    {:max-length [5 0]}))

;; vector, multi-level

(expect "[:a [:b [:c ...] ...] :o ...]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [3 2 1 0]}))

(expect "[:a [:b [:c ...] ...] :o ...]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [3 2 1]}))

(expect "[:a [:b [:c [:d [:e :f :g] :h ...] :j ...] :l ...] :o ...]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [3]}))

(expect "[:a [:b ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 1 0]}))

(expect "[:a ## :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 0]}))

(expect "[:a [:b ## :l ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 3 0]}))

(expect "[:a [:b ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 1 3 0]}))

(expect "[:a [:b [:c ## :j :k] :l :m ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 4 4 0]}))

(expect "[:a [:b [:c ## :j :k] ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 2 4 0]}))

(expect "[:a [:b [:c [:d ...] :j :k] ...] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-length [4 2 4 1 0]}))

;; record, multi-level

(def rml (make-record :reallylongleft {:r :s, [[:t] :u :v] :x}))

(expect "#zprint.zprint/r {:left :reallylongleft, ...}"
        (zprint-str rml {:max-length 1}))

(expect
  "#zprint.zprint/r {:left :reallylongleft, :right {:r :s, [[:t] :u :v] :x}}"
  (zprint-str rml))

(expect
  "#zprint.zprint/r {:left :reallylongleft, :right {:r :s, [[:t] :u ...] :x}}"
  (zprint-str rml {:max-length 2}))

(expect "#zprint.zprint/r {:left :reallylongleft, :right {:r :s, ...}}"
        (zprint-str rml {:max-length [2 1 0]}))

(expect "#zprint.zprint/r {:left :reallylongleft, :right ##}"
        (zprint-str rml {:max-length [2 0]}))

(expect "#zprint.zprint/r {:left :reallylongleft, :right ##}"
        (zprint-str rml {:max-length [3 0]}))

;; depth

;; set

(expect "##" (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 0}))

(expect "#{## :a}" (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 1}))

(expect "#{#{## :b} :a}" (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 2}))

(expect "#{#{#{## :c} :b} :a}"
        (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 3}))

(expect "#{#{#{#{:d} :c} :b} :a}"
        (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 4}))

(expect "#{#{#{#{:d} :c} :b} :a}"
        (zprint-str #{:a #{:b #{:c #{:d}}}} {:max-depth 5}))

;; vector

(expect "[:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 5}))

(expect "[:a [:b [:c [:d ## :h :i] :j :k] :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 4}))

(expect "[:a [:b [:c ## :j :k] :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 3}))

(expect "[:a [:b ## :l :m :n] :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 2}))

(expect "[:a ## :o :p]"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 1}))

(expect "##"
        (zprint-str [:a [:b [:c [:d [:e :f :g] :h :i] :j :k] :l :m :n] :o :p]
                    {:max-depth 0}))

;; list

(expect "##"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 0}))

(expect "(:a ## :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 1}))

(expect "(:a (:b ## :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 2}))

(expect "(:a (:b (:c ## :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 3}))

(expect "(:a (:b (:c (:d ## :h :i) :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 4}))

(expect "(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 5}))

(expect "(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)"
        (zprint-str '(:a (:b (:c (:d (:e :f :g) :h :i) :j :k) :l :m :n) :o :p)
                    {:max-depth 6}))

;; map

(expect "{:a {:b {:c {:d :e}}}}"
        (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 5}))

(expect "{:a {:b {:c {:d :e}}}}"
        (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 4}))

(expect "{:a {:b {:c ##}}}" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 3}))

(expect "{:a {:b ##}}" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 2}))

(expect "{:a ##}" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 1}))

(expect "##" (zprint-str {:a {:b {:c {:d :e}}}} {:max-depth 0}))

;;
;; # Bug in ztake-append.
;;

(expect
  "(deftype Typetest1 [cnt _meta]\n  clojure.lang.IHashEq\n    (hasheq [this] ...)\n  clojure.lang.Counted\n    (count [_] ...)\n  clojure.lang.IMeta\n    (meta [_] ...))"
  (zprint-fn-str zprint.zprint-test/->Typetest1 {:max-length [100 2 10 0]}))

(expect "(deftype Typetest1\n  ...)"
        (zprint-fn-str zprint.zprint-test/->Typetest1 {:max-length 2}))

;;
;; # Bug in printing multiple uneval things -- Issues #58
;;
;; Using this crazy syntax:  (a b c (d #_#_(e f g) h))
;;
;; Instead of: (a b c (d #_(e f g) #_h))
;;
;; Who knew?
;;

(expect "(a b c (d #_#_(e f g) h))"
        (zprint-str "(a b c (d #_#_(e f g) h))" {:parse-string? true}))

;;
;; # prefix-tags tests.  
;;
;; We already have a lot of these, but might as well gather them all
;; together here.
;;
;; These show that the basics works

(expect "'(a b c)" (zprint-str "'(a b c)" {:parse-string? true}))
(expect "`(a b c)" (zprint-str "`(a b c)" {:parse-string? true}))
(expect "~(a b c)" (zprint-str "~(a b c)" {:parse-string? true}))
(expect "~@(a b c)" (zprint-str "~@(a b c)" {:parse-string? true}))
(expect "@(a b c)" (zprint-str "@(a b c)" {:parse-string? true}))
(expect "#'thisisatest" (zprint-str "#'thisisatest" {:parse-string? true}))
(expect "#_(a b c)" (zprint-str "#_(a b c)" {:parse-string? true}))
(expect "#_#_(a b c) d" (zprint-str "#_#_(a b c) d" {:parse-string? true}))

;;
;; These try for the indents
;;

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      '[aaaaaaa bbbbbbbb\n        cccccccccc])"
  (zprint-str
    "(this is a test this is only a test '[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      `[aaaaaaa bbbbbbbb\n        cccccccccc])"
  (zprint-str
    "(this is a test this is only a test `[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      ~[aaaaaaa bbbbbbbb\n        cccccccccc])"
  (zprint-str
    "(this is a test this is only a test ~[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      ~@[aaaaaaa bbbbbbbb\n         cccccccccc])"
  (zprint-str
    "(this is a test this is only a test ~@[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      #'thisisalsoatest)"
  (zprint-str "(this is a test this is only a test #'thisisalsoatest)"
              30
              {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      #_[aaaaaaa bbbbbbbb\n         cccccccccc])"
  (zprint-str
    "(this is a test this is only a test #_[aaaaaaa bbbbbbbb cccccccccc])"
    30
    {:parse-string? true}))

(expect
  "(this is\n      a\n      test\n      this\n      is\n      only\n      a\n      test\n      #_#_[aaaaaaa bbbbbbbb\n           cccccccccc]\n        [ddddddddd eeeeeeeeee\n         fffffffffff])"
  (zprint-str
    "(this is a test this is only a test #_#_[aaaaaaa bbbbbbbb cccccccccc][ddddddddd eeeeeeeeee fffffffffff])"
    30
    {:parse-string? true}))

;
; When the fn-type (fn-style) is not a keyword, like :arg1, but a vector
; like [:arg1 {:vector {:wrap? false}}], does it actually do anything?  
;
; Configuration of this in tested in config_test.clj, but this is to test
; the functionality.
;

(def dp
  "(defproject name version :test :this :stuff [:aaaaa :bbbbbbb  
:ccccccccc :ddddddd :eeeeeee ])")

; Does defproject inhibit the wrapping of elements of a vector (which it is
; configured to do)?

(expect
  "(defproject name version\n  :test :this\n  :stuff [:aaaaa\n          :bbbbbbb\n          :ccccccccc\n          :ddddddd\n          :eeeeeee])"
  (redef-state [zprint.config] (zprint-str dp 50 {:parse-string? true})))

; If we remove that configuration, will it stop inhibiting the wrapping of vector
; elements?

(expect
  "(defproject name version\n  :test :this\n  :stuff [:aaaaa :bbbbbbb :ccccccccc :ddddddd\n          :eeeeeee])"
  (redef-state [zprint.config]
               (zprint-str dp
                           50
                           {:parse-string? true,
                            :fn-map {"defproject" :arg2-pair}})))

(expect "{a 1}" (zprint-str "{a 1}" {:parse-string? true}))

(expect
  "(defrecord ~tagname\n  ~fields\n  (~-collect-vars\n    [acc]\n    (reduce #(list datascript.par ser/collect-vars-acc %1 %2))))"
  (zprint-str
    "(defrecord ~tagname ~fields (~-collect-vars [acc] (reduce #(list datascript.par
ser/collect-vars-acc %1 %2) )))"
    {:parse-string? true}))
