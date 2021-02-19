(ns zprint.guide-test
  (:require [expectations.clojure.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            #?(:cljs [cljs.test :refer-macros [deftest is]])
            #?(:clj [clojure.test :refer [deftest is]])
            #?(:cljs [cljs.tools.reader :refer [read-string]])
            [zprint.core :refer
             [zprint-str set-options! zprint-str-internal czprint-str
              zprint-file-str zprint czprint]]
            [zprint.guide :refer
             [rodguide moustacheguide areguide jrequireguide rumguide
              rumguide-1]]))

;; Keep some of the test on wrapping so they still work
;!zprint {:comment {:wrap? false}}

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

;
; Set :force-eol-blanks? true here to see if we are catching eol blanks
;

(set-options!
  {:configured? true, :force-eol-blanks? false, :test-for-eol-blanks? true})

(defexpect guide-tests

  ;;
  ;; # Basic guide tests, not using an :option-fn
  ;;

(expect "(stuff (caller aaaa bbbb\n               ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 27}))

(expect
  "(stuff (caller aaaa bbbb\n               ccc\n                 dddddd))"
  (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
              {:parse-string? true,
               :list {:respect-nl? false},
               :guide-debug [:list 2
                             [:element :pair-begin :element :element :element
                              :element :pair-end]],
               :width 26}))

(expect "(stuff (caller\n         aaaa bbbb\n         ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 24}))

(expect "(stuff (caller\n         aaaa bbbb\n         ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 21}))

(expect "(stuff (caller\n         aaaa bbbb\n         ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 22}))

(expect "(stuff\n  (caller aaaa bbbb\n          ccc\n            dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 20}))

(expect "(stuff\n  (caller\n    aaaa bbbb\n    ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 19}))

(expect "(stuff\n  (caller\n    aaaa bbbb\n    ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 16}))

(expect "(stuff\n  (caller\n    aaaa bbbb\n    ccc\n      dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 15}))

(expect "(stuff\n  (caller\n    aaaa bbbb\n    ccc\n      dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 13}))

(expect "(stuff\n  (caller\n    aaaa\n      bbbb\n    ccc\n      dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 12}))

  (expect
"(caller aaaa\n            bbbb          ccc          ddddd\n            eeeee         fff\n                                       ;comment 1\n                                       ggg\n            hhh           iii          jjj\n            kkk           lll ;comment 2\n                                       mmm\n            nnn           ooo          ppp)"
    (zprint-str
      "(caller aaaa bbbb ccc ddddd eeeee fff \n;comment 1\n \nggg hhh iii jjj kkk lll ;comment 2\n\n mmm nnn ooo ppp)"
      {:parse-string? true,
       :list {:respect-nl? false},
       :guide-debug [:list 1 [:element :element :newline :spaces 10 :mark 1 :element :spaces 10
               :mark 2 :element :spaces 10 :mark 3 :element :newline
               :element-align 1 :element-align 2 :element-align 3 :newline
               :element-align 1 :element-align 2 :element-align 3 :newline
               :element-align 1 :element-align 2 :element-align 3 :newline
               :element-align 1 :element-align 2 :element-align 3]],
       :width 80}))

  (expect "(caller     aaaa bbbb ccc\n            dddddd)"
          (zprint-str "(caller aaaa bbbb ccc dddddd)"
                      {:parse-string? true,
                       :list {:respect-nl? false},
                       :guide-debug [:list 1 [:element :spaces 5 :mark 2 :element :element
                               :element :newline :element-align 2]],
                       :width 80}))

(expect "(stuff (caller     aaaa bbbb ccc\n                   dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :spaces 5 :mark 2 :element :element
                                    :element :newline :element-align 2]],
                     :width 80}))

(expect
"(caller aaaa          bbbb          ccc          ddddd\n                      eeeee         fff\n                                                 ;comment 1\n                                                 ggg\n                      hhh           iii          jjj\n                      kkk           lll ;comment 2\n                                                 mmm\n                      nnn           ooo          ppp)"
  (zprint-str
    "(caller aaaa bbbb ccc ddddd eeeee fff \n;comment 1\n \nggg hhh iii jjj kkk lll ;comment 2\n\n mmm nnn ooo ppp)"
    {:parse-string? true,
     :list {:respect-nl? false},
     :guide-debug [:list 1
                   [:element :element :spaces 10 :mark 1 :element :spaces 10
                    :mark 2 :element :spaces 10 :mark 3 :element :newline
                    :element-align 1 :element-align 2 :element-align 3 :newline
                    :element-align 1 :element-align 2 :element-align 3 :newline
                    :element-align 1 :element-align 2 :element-align 3 :newline
                    :element-align 1 :element-align 2 :element-align 3]],
     :width 80}))

(expect
  "(stuff (caller aaaa\n                   bbbb\n                   eeeee))"
  (zprint-str "(stuff (caller aaaa bbbb eeeee))"
              {:parse-string? true,
               :list {:respect-nl? false},
               :guide-debug [:list 2
                             [:element :element :newline :spaces 10 :mark 1
                              :element :newline :element-align 1]],
               :width 80}))

  ; Ensure that, even though we are asking for alignment, we don't let two
  ; things run together

(expect
  "(stuff (caller aaaa\n                   bbbb   ccc\n                   eeeeee fff))"
  (zprint-str "(stuff (caller aaaa bbbb ccc eeeeee fff))"
              {:parse-string? true,
               :list {:respect-nl? false},
               :guide-debug [:list 2
                             [:element :element :newline :spaces 10 :mark 1
                              :element :spaces 3 :mark 2 :element :newline
                              :element-align 1 :element-align 2]],
               :width 80}))

(expect
  "(stuff (caller aaaa\n                   bbbb   ccc\n                   eeeeeee fff))"
  (zprint-str "(stuff (caller aaaa bbbb ccc eeeeeee fff))"
              {:parse-string? true,
               :list {:respect-nl? false},
               :guide-debug [:list 2
                             [:element :element :newline :spaces 10 :mark 1
                              :element :spaces 3 :mark 2 :element :newline
                              :element-align 1 :element-align 2]],
               :width 80}))

(expect
  "(stuff (caller aaaa\n                   bbbb   ccc\n                   eeeeeeeeeee fff))"
  (zprint-str "(stuff (caller aaaa bbbb ccc eeeeeeeeeee fff))"
              {:parse-string? true,
               :list {:respect-nl? false},
               :guide-debug [:list 2
                             [:element :element :newline :spaces 10 :mark 1
                              :element :spaces 3 :mark 2 :element :newline
                              :element-align 1 :element-align 2]],
               :width 80}))

; See that the default indent when we use :style :community is 1

(expect "(stuff\n (caller\n  aaaa bbbb\n  ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 15,
                     :style :community}))

; And that "body" function can be guided and end up with indent 2

(expect "(stuff\n (caller\n   aaaa bbbb\n   ccc dddddd))"
        (zprint-str "(stuff (caller aaaa bbbb ccc dddddd))"
                    {:parse-string? true,
                     :list {:respect-nl? false},
                     :guide-debug [:list 2
                                   [:element :pair-begin :element :element
                                    :element :element :pair-end]],
                     :width 15,
                     :style :community,
                     :fn-map {"caller" :none-body}}))

; Do comments take on the spacing of the following :element?

(expect
  "(;comment 1\n caller\n  xxx\n  ;comment x\n  yyy\n       aaaa\n       ;comment 2\n       bbbb\n       ;comment 3\n       ccc\n       dddddd)"
  (zprint-str
    "(;comment 1\n  caller xxx \n;comment x\n yyy aaaa \n;comment 2\n bbbb \n;comment 3\n ccc dddddd)"
    {:parse-string? true,
     :list {:respect-nl? false},
     :guide-debug [:list 1
                   [:element :newline :element :newline :element :newline
                    :spaces 5 :mark 1 :element :newline :spaces 5 :element
                    :newline :element-align 1 :newline :element-align 1]],
     :width 80}))

(expect
  "(stuff (;comment 1\n        caller\n         xxx\n         ;comment x\n         yyy\n              aaaa\n              ;comment 2\n              bbbb\n              ;comment 3\n              ccc\n              dddddd))"
  (zprint-str
    "(stuff (;comment 1\n  caller xxx \n;comment x\n yyy aaaa \n;comment 2\n bbbb \n;comment 3\n ccc dddddd))"
    {:parse-string? true,
     :list {:respect-nl? false},
     :guide-debug [:list 2
                   [:element :newline :element :newline :element :newline
                    :spaces 5 :mark 1 :element :newline :spaces 5 :element
                    :newline :element-align 1 :newline :element-align 1]],
     :width 80}))

(expect
  "(stuff (;comment 1\n        caller\n             sss\n             ;comment :indent 6\n             ttt\n         xxx\n         ;comment :indent-reset\n         yyy\n              aaaa\n              ;comment :element-align 1\n              bbbb\n              ;comment :spaces 5\n              ccc\n              dddddd))"
  (zprint-str
    "(stuff (;comment 1\n  caller sss \n;comment :indent 6\n ttt xxx \n;comment :indent-reset \n yyy aaaa \n;comment :element-align 1\n bbbb \n;comment :spaces 5\n ccc dddddd))"
    {:parse-string? true,
     :list {:respect-nl? false},
     :guide-debug [:list 2
                   [:element :newline :indent 6 :element :newline :element
                    :indent-reset :newline :element :newline :element :newline
                    :spaces 5 :mark 1 :element :newline :spaces 5 :element
                    :newline :element-align 1 :newline :element-align 1]],
     :width 80}))

;;
;; # :indent and :indent-reset
;;

(expect
  "(stuff (caller aaaa\n               bbbb\n               ccc\n         dddddd))"
  (zprint-str
    "(stuff (caller aaaa bbbb ccc dddddd))"
    {:parse-string? true,
     :list {:respect-nl? false},
     :guide-debug [:list 2
                   [:element :element :newline :indent 8 :element :newline
                    :element :indent-reset :newline :element]],
     :width 80}))

(expect "(stuff (caller aaaa\n        bbbb\n        ccc\n         dddddd))"
        (zprint-str
          "(stuff (caller aaaa bbbb ccc dddddd))"
          {:parse-string? true,
           :list {:respect-nl? false},
           :guide-debug [:list 2
                         [:element :element :newline :indent 1 :element :newline
                          :element :indent-reset :newline :element]],
           :width 80}))

;;
;; Comment as the first thing in a list
;;

(expect
  "(stuff (;comment\n        caller\n         aaaa\n         bbbb\n         ccc\n         dddddd))"
  (zprint-str "(stuff (;comment\n  caller aaaa bbbb ccc dddddd))"
              {:parse-string? true,
               :list {:respect-nl? false},
               :guide-debug [:list 2
                             [:element :newline :element :newline :element
                              :newline :element :newline :element]],
               :width 80}))

  ;;
  ;; # rodguide
  ;;

  (def rod1
    "
(defn rod1
  \"An exmple showing how pairs are indented.\"
  [a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))")

  (def rod2
    "
(defn rod2
  [a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))")

  (def rod3
    "
(defn rod3
  ([a b c d]
   (cond (nil? a) (list d)
         (nil? b) (list c d a b)
         :else (list a b c d)))
  ([a b c] (rod3 a b c nil)))")

  (def rod4
    "
(defn rod4
  \"A multi-arity test.\"
  ([a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))
  ([a b c] (rod4 a b c nil)))")

  (def rod5
    "
(defn rod5
  ([a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))
  ([a b c] (rod5 a b c nil))
  ([a b] (rod5 a b nil nil)))")

  (def rod6
    "
(defn rod6
  \"Thus illustrating the rules of defn.\"
  ([a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))
  ([a b c] (rod6 a b c nil))
  ([a b] (rod6 a b nil nil)))")

  (def rod7
    "
(defn rod7
  \"Thus illustrating the rules of defn.\"
  ([a b c d]
  (cond (nil? a) (list d)
        (nil? b) (list c d a b)
        :else (list a b c d)))
        ; This is a comment
        ; actually, a two line one
  ([a b c]  ; this is an embedded comment
  (rod7 a b c nil))
  ([a b] (rod7 a b nil nil)))")

  (expect
    "(defn rod1\n  \"An exmple showing how pairs are indented.\"\n  [a b c d]\n  (cond (nil? a) (list d)\n        (nil? b) (list c d a b)\n        :else (list a b c d)))"
    (zprint-str rod1
                {:parse-string? true,
                 :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}}))


  (expect
    "(defn rod2 [a b c d]\n  (cond (nil? a) (list d)\n        (nil? b) (list c d a b)\n        :else (list a b c d)))"
    (zprint-str rod2
                {:parse-string? true,
                 :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}}))

  (expect
    "(defn rod3\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) (list c d a b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod3 a b c nil)))"
    (zprint-str rod3
                {:parse-string? true,
                 :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}}))

  (expect
    "(defn rod4\n  \"A multi-arity test.\"\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) (list c d a b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod4 a b c nil)))"
    (zprint-str rod4
                {:parse-string? true,
                 :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}}))

  (expect
    "(defn rod5\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) (list c d a b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod5 a b c nil))\n\n  ([a b]\n   (rod5 a b nil nil)))"
    (zprint-str rod5
                {:parse-string? true,
                 :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}}))

  (expect
    "(defn rod6\n  \"Thus illustrating the rules of defn.\"\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) (list c d a b)\n         :else (list a b c d)))\n\n  ([a b c]\n   (rod6 a b c nil))\n\n  ([a b]\n   (rod6 a b nil nil)))"
    (zprint-str rod6
                {:parse-string? true,
                 :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}}))

  (expect
    "(defn rod7\n  \"Thus illustrating the rules of defn.\"\n  ([a b c d]\n   (cond (nil? a) (list d)\n         (nil? b) (list c d a b)\n         :else (list a b c d)))\n\n  ; This is a comment\n  ; actually, a two line one\n  ([a b c]  ; this is an embedded comment\n   (rod7 a b c nil))\n\n  ([a b]\n   (rod7 a b nil nil)))"
    (zprint-str rod7
                {:parse-string? true,
                 :fn-map {"defn" [:guided {:list {:option-fn rodguide}}]}}))

  ;;
  ;; # moustacheguide
  ;;



  (def mapp
    "(m/app middle1 middle2
       middle3 middle4
       middle5 [route]
       handler [route]
       handler)")

  (def mapp1
    "(m/app middle1 middle2
       middle3 (this is \"a\" test \"this\" is \"only a\" test) middle4
       middle5 [route]
       handler [route]
       handler)")

  (def mapp2
    "(m/app :get  (m/app [route] handler
                    [route] handler)
       :post (m/app [route] handler
                    [route] handler))")

  (def mapp3
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    [route] handler)
       :post (m/app middle of the road
                    [route] handler
                    [route] handler))")

  (def mapp3a
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    [route that is long] handler)
       :post (m/app middle of the road
                    [route] handler
                    [route] handler))")

  (def mapp3b
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    [route that is long] (handler that also is long))
       :post (m/app middle of the road
                    [route] handler
                    [route] handler))")

  (def mapp3c
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route that is long] handler
                    [route] (handler that also is long))
       :post (m/app middle of the road
                    [route] handler
                    [route] handler))")

  (def mapp4
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    [route] handler))")

  (def mapp5
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    ; How do comments work?
                    [route] handler)
       ; How do comments work here?
       :post (m/app middle of the road
                    [route] handler
                    [route] ; What about comments here?
                    handler))")

  (def mapp6
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    ; How do comments work?
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" test) 
                    )
       ; How do comments work here?
       :post (m/app 
                    [route] handler
                    [route] ; What about comments here?
                    handler))")

  (def mapp6a
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route that is long] handler
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" test) 
                    )
       ; How do comments work here?
       :post (m/app 
                    [route] handler
                    [route] ; What about comments here?
                    handler))")
  (def mapp6b
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route that is long] handler
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" test) 
                    )
       :post (m/app 
                    [route] handler
                    [route] ; What about comments here?
                    handler))")

  (def mapp6c
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route that is long] handler
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" test) 
                    )
       :post (m/app 
                    [route] handler
                    [route] 
                    handler))")

  (def mapp6d
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route that is long] handler
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" real test) 
                    )
       :post (m/app 
                    [route] handler
                    [route] 
                    handler))")

  (def mapp6e
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route that is long] handler
                    [route] 
        (handler this is a test that is pretty long) 
                    )
       :post (m/app 
                    [route] handler
                    [route] 
                    handler))")

  (def mapp6f
    "(m/app middle1 
                    [route] handler
                    ; How do comments work?
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" test) 
                    )")


  (def mapp6g
    "(m/app :get  (m/app middle1 middle2 middle3
                    [much longer route] handler
                    ; How do comments work?
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" test) 
                    )
       ; How do comments work here?
       :post (m/app 
                    [route] handler
                    [route] ; What about comments here?
                    handler))")



  (def mapp7
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    ; How do comments work?
                    [route] 
        (handler this is \"a\" test \"this\" is \"only a\" test))
       ; How do comments work here?
       true (should be paired with true)
       false (should be paired with false)
       6 (should be paired with 6)
       \"string\" (should be paired with string)
       :post (m/app 
                    [route] handler
                    [route] ; What about comments here?
                    handler))")

  (def mapp8
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    ; How do comments work?
                    [longer route] 
        (handler this is \"a\" test \"this\" is \"only a\" test))
       ; How do comments work here?
       true (should be paired with true)
       false (should be paired with false)
       6 (should be paired with 6)
       \"string\" (should be paired with string)
       :post (m/app 
                    [a really long route] handler
                    [route] ; What about comments here?
                    handler))")

  (def mapp9
    "(m/app :get  (m/app middle1 middle2 middle3
                    [route] handler
                    [longer route] 
        (handler this is \"a\" test \"this\" is \"only a\" test))
       ; How do comments work here?
       true (should be paired with true)
       false (should be paired with false)
       6 (should be paired with 6)
       \"string\" (should be paired with string)
       :post (m/app 
                    [a really long route] handler
                    [route] 
                    handler))")

  (expect
    "(m/app\n  middle1\n  middle2\n  middle3\n  middle4\n  middle5\n  [route] handler\n  [route] handler)"
    (zprint-str mapp
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  middle1\n  middle2\n  middle3\n  (this is \"a\" test \"this\" is \"only a\" test)\n  middle4\n  middle5\n  [route] handler\n  [route] handler)"
    (zprint-str mapp1
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          [route] handler\n          [route] handler)\n  :post (m/app\n          [route] handler\n          [route] handler))"
    (zprint-str mapp2
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route] handler\n          [route] handler)\n  :post (m/app\n          middle\n          of\n          the\n          road\n          [route] handler\n          [route] handler))"
    (zprint-str mapp3
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get (m/app\n         middle1\n         middle2\n         middle3\n         [route] handler\n         [route] handler))"
    (zprint-str mapp4
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route]              handler\n          [route that is long] handler)\n  :post (m/app\n          middle\n          of\n          the\n          road\n          [route] handler\n          [route] handler))"
    (zprint-str mapp3a
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route]              handler\n          [route that is long] (handler that also is long))\n  :post (m/app\n          middle\n          of\n          the\n          road\n          [route] handler\n          [route] handler))"
    (zprint-str mapp3b
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route that is long] handler\n          [route]              (handler that also is long))\n  :post (m/app\n          middle\n          of\n          the\n          road\n          [route] handler\n          [route] handler))"
    (zprint-str mapp3c
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route] handler\n          ; How do comments work?\n          [route] handler)\n  ; How do comments work here?\n  :post (m/app\n          middle\n          of\n          the\n          road\n          [route] handler\n          [route] ; What about comments here?\n            handler))"
    (zprint-str mapp5
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route] handler\n          ; How do comments work?\n          [route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  ; How do comments work here?\n  :post (m/app\n          [route] handler\n          [route] ; What about comments here?\n            handler))"
    (zprint-str mapp6
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route that is long] handler\n          [route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  ; How do comments work here?\n  :post (m/app\n          [route] handler\n          [route] ; What about comments here?\n            handler))"
    (zprint-str mapp6a
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route that is long] handler\n          [route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  :post (m/app\n          [route] handler\n          [route] ; What about comments here?\n            handler))"
    (zprint-str mapp6b
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route that is long] handler\n          [route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  :post (m/app\n          [route] handler\n          [route] handler))"
    (zprint-str mapp6c
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route that is long] handler\n          [route] (handler this is \"a\" test \"this\" is \"only a\" real test))\n  :post (m/app\n          [route] handler\n          [route] handler))"
    (zprint-str mapp6d
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))
  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [route that is long] handler\n          [route]              (handler this is a test that is pretty long))\n  :post (m/app\n          [route] handler\n          [route] handler))"
    (zprint-str mapp6e
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  middle1\n  [route] handler\n  ; How do comments work?\n  [route] (handler this is \"a\" test \"this\" is \"only a\" test))"
    (zprint-str mapp6f
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))
  (expect
    "(m/app\n  :get  (m/app\n          middle1\n          middle2\n          middle3\n          [much longer route] handler\n          ; How do comments work?\n          [route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  ; How do comments work here?\n  :post (m/app\n          [route] handler\n          [route] ; What about comments here?\n            handler))"
    (zprint-str mapp6g
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get     (m/app\n             middle1\n             middle2\n             middle3\n             [route] handler\n             ; How do comments work?\n             [route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  ; How do comments work here?\n  true     (should be paired with true)\n  false    (should be paired with false)\n  6        (should be paired with 6)\n  \"string\" (should be paired with string)\n  :post    (m/app\n             [route] handler\n             [route] ; What about comments here?\n               handler))"
    (zprint-str mapp7
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get     (m/app\n             middle1\n             middle2\n             middle3\n             [route]        handler\n             ; How do comments work?\n             [longer route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  ; How do comments work here?\n  true     (should be paired with true)\n  false    (should be paired with false)\n  6        (should be paired with 6)\n  \"string\" (should be paired with string)\n  :post    (m/app\n             [a really long route] handler\n             [route] ; What about comments here?\n               handler))"
    (zprint-str mapp8
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  (expect
    "(m/app\n  :get     (m/app\n             middle1\n             middle2\n             middle3\n             [route]        handler\n             [longer route] (handler this is \"a\" test \"this\" is \"only a\" test))\n  ; How do comments work here?\n  true     (should be paired with true)\n  false    (should be paired with false)\n  6        (should be paired with 6)\n  \"string\" (should be paired with string)\n  :post    (m/app\n             [a really long route] handler\n             [route]               handler))"
    (zprint-str mapp9
                {:parse-string? true,
                 :fn-map {"m/app" [:guided
                                   {:list {:option-fn moustacheguide}}]}}))

  ;;
  ;; # areguide
  ;;



  (def are1
    "(are [x y z] (= x y z)
   3 (stuff y) (bother z)
   4 (foo y) (bar z))")

  (def are2 "(are [x y] (= x y)  
  2 (+ 1 1)
  4 (* 2 2))")

  (def are3 "(are [x y z] (= x y z)  
  2 (+ 1 1) (- 4 2)
  4 (* 2 2) (/ 8 2))")


  (def are4
    "(deftest grab-map-values-using-are
  (are [y z] (= y (:x z))
       2 {:x 2}
       1 {:x 1}
       3 {:x 3 :y 4}))")

  (def are5
    "(deftest add-x-to-y-a-few-times
  (is (= 5 (add 2 3)))
  (is (= 5 (add 1 4)))
  (is (= 5 (add 3 2))))")

  (def are6
    "(deftest add-x-to-y-a-using-are
  (are [x y] (= 5 (add x y))
       2 3
       1 4
       3 2))")

  (def are7
    "(deftest grab-map-values-using-are
  (are [y z] (= y (:x z))
       2 {:x 2}
       1 {:x 1}
       3 {:x 3 :y 4}))")

  (def are8
    "(are [result arg-map] (= result (+ (:x arg-map) (:y arg-map)))
             5      {:x 2 :y 3},
             10     {:x 6 :y 4})")

  (def ltest
    "
(ns testloop
  (:use clojure.test
        looping))

(deftest test-itwell
  (are [result keys vals] (= result (ipm-1 keys vals))
       {} nil nil
       {:a 1} [:a] [1])
  (are [result keys vals] (= result (ipm-2 keys vals))
       {} nil nil
       {:a 1} [:a] [1])
  (are [result keys vals] (= result (ipm-3 keys vals))
       {} nil nil
       {:a 1} [:a] [1])
  (are [result keys vals] (= result (ipm-4 keys vals))
       {} nil nil
       {:a 1} [:a] [1])
  (are [result keys vals] (= result (ipm-5 keys vals))
       {} nil nil
       {:a 1} [:a] [1]))

(deftest test-small
  (are [result vals] (= result (apply small-1 vals))
       2 [6 2 4]
       2 [6 3 2 4]
       0 [0])
  (are [result vals] (= result (apply small-2 vals))
       2 [6 2 4]
       2 [6 3 2 4]
       0 [0])
  (are [result vals] (= result (apply small-3 vals))
       2 [6 2 4]
       2 [6 3 2 4]
       0 [0])
  (are [result vals] (= result (apply small vals))
       2 [6 2 4]
       2 [6 3 2 4]
       0 [0]))

(deftest test-smallandlarge
  (are [result vals] (= result (apply smallandlarge-1 vals))
       {:min 2 :max 7} [7 2 4]
       {:min 0 :max 0} [0])
  (are [result vals] (= result (apply smallandlarge-2 vals))
       {:min 2 :max 7} [7 2 4]
       {:min 0 :max 0} [0]))")


  (expect
    "(are [x y z] (= x y z)\n  3 (stuff y) (bother z)\n  4 (foo y) (bar z))"
    (zprint-str are1
                {:parse-string? true,
                 :fn-map {"are" [:guided {:list {:option-fn areguide}}]}}))

  (expect "(are [x y] (= x y)\n  2 (+ 1 1)\n  4 (* 2 2))"
          (zprint-str are2
                      {:parse-string? true,
                       :fn-map {"are" [:guided
                                       {:list {:option-fn areguide}}]}}))

  (expect "(are [x y z] (= x y z)\n  2 (+ 1 1) (- 4 2)\n  4 (* 2 2) (/ 8 2))"
          (zprint-str are3
                      {:parse-string? true,
                       :fn-map {"are" [:guided
                                       {:list {:option-fn areguide}}]}}))

  (expect
    "(deftest grab-map-values-using-are\n  (are [y z] (= y (:x z))\n    2 {:x 2}\n    1 {:x 1}\n    3 {:x 3, :y 4}))"
    (zprint-str are4
                {:parse-string? true,
                 :fn-map {"are" [:guided {:list {:option-fn areguide}}]}}))
  (expect
    "(deftest add-x-to-y-a-few-times\n  (is (= 5 (add 2 3)))\n  (is (= 5 (add 1 4)))\n  (is (= 5 (add 3 2))))"
    (zprint-str are5
                {:parse-string? true,
                 :fn-map {"are" [:guided {:list {:option-fn areguide}}]}}))
  (expect
    "(deftest add-x-to-y-a-using-are\n  (are [x y] (= 5 (add x y))\n    2 3\n    1 4\n    3 2))"
    (zprint-str are6
                {:parse-string? true,
                 :fn-map {"are" [:guided {:list {:option-fn areguide}}]}}))

  (expect
    "(deftest grab-map-values-using-are\n  (are [y z] (= y (:x z))\n    2 {:x 2}\n    1 {:x 1}\n    3 {:x 3, :y 4}))"
    (zprint-str are7
                {:parse-string? true,
                 :fn-map {"are" [:guided {:list {:option-fn areguide}}]}}))

  (expect
    "(are [result arg-map] (= result (+ (:x arg-map) (:y arg-map)))\n  5 {:x 2, :y 3}\n  10 {:x 6, :y 4})"
    (zprint-str are8
                {:parse-string? true,
                 :fn-map {"are" [:guided {:list {:option-fn areguide}}]}}))

  (expect
    "\n(ns testloop (:use clojure.test looping))\n\n(deftest test-itwell\n  (are [result keys vals] (= result (ipm-1 keys vals))\n    {} nil nil\n    {:a 1} [:a] [1])\n  (are [result keys vals] (= result (ipm-2 keys vals))\n    {} nil nil\n    {:a 1} [:a] [1])\n  (are [result keys vals] (= result (ipm-3 keys vals))\n    {} nil nil\n    {:a 1} [:a] [1])\n  (are [result keys vals] (= result (ipm-4 keys vals))\n    {} nil nil\n    {:a 1} [:a] [1])\n  (are [result keys vals] (= result (ipm-5 keys vals))\n    {} nil nil\n    {:a 1} [:a] [1]))\n\n(deftest test-small\n  (are [result vals] (= result (apply small-1 vals))\n    2 [6 2 4]\n    2 [6 3 2 4]\n    0 [0])\n  (are [result vals] (= result (apply small-2 vals))\n    2 [6 2 4]\n    2 [6 3 2 4]\n    0 [0])\n  (are [result vals] (= result (apply small-3 vals))\n    2 [6 2 4]\n    2 [6 3 2 4]\n    0 [0])\n  (are [result vals] (= result (apply small vals))\n    2 [6 2 4]\n    2 [6 3 2 4]\n    0 [0]))\n\n(deftest test-smallandlarge\n  (are [result vals] (= result (apply smallandlarge-1 vals))\n    {:min 2, :max 7} [7 2 4]\n    {:min 0, :max 0} [0])\n  (are [result vals] (= result (apply smallandlarge-2 vals))\n    {:min 2, :max 7} [7 2 4]\n    {:min 0, :max 0} [0]))"
    (zprint-file-str ltest
                     "stuff"
                     {:fn-map {"are" [:guided {:list {:option-fn areguide}}]}}))

  ;;
  ;; # jrequireguide
  ;;



  (def jr1
    "(ns ^:no-doc zprint.zprint
  #?@(:cljs [[:require-macros
              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])
    [clojure.string :as s]
    [zprint.finish :refer [newline-vec]]
    [zprint.zfns :refer
     [zstring znumstr zbyte-array? zcomment? zsexpr zseqnws zseqnws-w-nl
      zfocus-style zstart zfirst zfirst-no-comment zsecond znthnext zcount zmap
      zanonfn? zfn-obj? zfocus zfind-path zwhitespace? zlist?
      zcount-zloc-seq-nc-nws zvector? zmap? zset? zcoll? zuneval? zmeta? ztag
      zlast zarray? zatom? zderef zrecord? zns? zobj-to-vec zexpandarray
      znewline? zwhitespaceorcomment? zmap-all zpromise? zfuture? zdelay?
      zkeyword? zconstant? zagent? zreader-macro? zarray-to-shift-seq zdotdotdot
      zsymbol? znil? zreader-cond-w-symbol? zreader-cond-w-coll? zlift-ns zfind
      zmap-w-nl zmap-w-nl-comma ztake-append znextnws-w-nl znextnws
      znamespacedmap? zmap-w-bl zseqnws-w-bl zsexpr?]]
    [zprint.comment :refer [blanks inlinecomment? length-before]]
    [zprint.ansi :refer [color-str]]
    [zprint.config :refer [validate-options merge-deep]]
    [zprint.zutil :refer [add-spec-to-docstring]]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip :as z]
    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))")


  (def jr2
    "(ns ^:no-doc zprint.zprint
  (:require
    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])
    [clojure.string :as s]
    [zprint.finish :refer [newline-vec]]
    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))")

  (def jr3
    "(ns ^:no-doc zprint.zprint
  #?@(:cljs [[:require-macros
              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])
    [clojure.string :as s]
    [zprint.finish :refer [newline-vec]]
    [zprint.zfns :refer
     [zstring znumstr zbyte-array? zcomment? zsexpr zseqnws zseqnws-w-nl
      zfocus-style zstart zfirst zfirst-no-comment zsecond znthnext zcount zmap
      zanonfn? zfn-obj? zfocus zfind-path zwhitespace? zlist?
      zcount-zloc-seq-nc-nws zvector? zmap? zset? zcoll? zuneval? zmeta? ztag
      zlast zarray? zatom? zderef zrecord? zns? zobj-to-vec zexpandarray
      znewline? zwhitespaceorcomment? zmap-all zpromise? zfuture? zdelay?
      zkeyword? zconstant? zagent? zreader-macro? zarray-to-shift-seq zdotdotdot
      zsymbol? znil? zreader-cond-w-symbol? zreader-cond-w-coll? zlift-ns zfind
      zmap-w-nl zmap-w-nl-comma ztake-append znextnws-w-nl znextnws
      znamespacedmap? zmap-w-bl zseqnws-w-bl zsexpr?]]
    [zprint.comment :refer [blanks inlinecomment? length-before]]
    '[zprint.ansi :refer [color-str]]
    ~`[zprint.config :refer [validate-options merge-deep]]
    [zprint.zutil :refer [add-spec-to-docstring]]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip :as z]
    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))")


  (def jr4
    "(ns ^:no-doc zprint.zprint
  #?@(:cljs [[:require-macros
              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])
  (:require
    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])
    [clojure.string :as s]
    [zprint.finish :refer [newline-vec]]
    [zprint.zfns :refer
     [zstring znumstr zbyte-array? zcomment? zsexpr zseqnws zseqnws-w-nl
      zfocus-style zstart zfirst zfirst-no-comment zsecond znthnext zcount zmap
      zanonfn? zfn-obj? zfocus zfind-path zwhitespace? zlist?
      zcount-zloc-seq-nc-nws zvector? zmap? zset? zcoll? zuneval? zmeta? ztag
      zlast zarray? zatom? zderef zrecord? zns? zobj-to-vec zexpandarray
      znewline? zwhitespaceorcomment? zmap-all zpromise? zfuture? zdelay?
      zkeyword? zconstant? zagent? zreader-macro? zarray-to-shift-seq zdotdotdot
      zsymbol? znil? zreader-cond-w-symbol? zreader-cond-w-coll? zlift-ns zfind
      zmap-w-nl zmap-w-nl-comma ztake-append znextnws-w-nl znextnws
      znamespacedmap? zmap-w-bl zseqnws-w-bl zsexpr?]]
    [zprint.comment :refer [blanks inlinecomment? length-before]]
    [zprint.ansi :as ansi :refer [color-str]]
    [zprint.config :as config :refer [validate-options merge-deep]]
    [zprint.zutil :refer [add-spec-to-docstring]]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip :as z]
    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))")


  (expect
    "(ns ^:no-doc zprint.zprint\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])\n    [clojure.string     :as s]\n    [zprint.finish      :refer [newline-vec]]\n    [zprint.zfns        :refer [zstring znumstr zbyte-array? zcomment? zsexpr\n                                zseqnws zseqnws-w-nl zfocus-style zstart zfirst\n                                zfirst-no-comment zsecond znthnext zcount zmap\n                                zanonfn? zfn-obj? zfocus zfind-path zwhitespace?\n                                zlist? zcount-zloc-seq-nc-nws zvector? zmap?\n                                zset? zcoll? zuneval? zmeta? ztag zlast zarray?\n                                zatom? zderef zrecord? zns? zobj-to-vec\n                                zexpandarray znewline? zwhitespaceorcomment?\n                                zmap-all zpromise? zfuture? zdelay? zkeyword?\n                                zconstant? zagent? zreader-macro?\n                                zarray-to-shift-seq zdotdotdot zsymbol? znil?\n                                zreader-cond-w-symbol? zreader-cond-w-coll?\n                                zlift-ns zfind zmap-w-nl zmap-w-nl-comma\n                                ztake-append znextnws-w-nl znextnws\n                                znamespacedmap? zmap-w-bl zseqnws-w-bl zsexpr?]]\n    [zprint.comment     :refer [blanks inlinecomment? length-before]]\n    [zprint.ansi        :refer [color-str]]\n    [zprint.config      :refer [validate-options merge-deep]]\n    [zprint.zutil       :refer [add-spec-to-docstring]]\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip    :as z]\n    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))"
    (zprint-str jr1
                {:parse-string? true,
                 :fn-map {":require" [:none
                                      {:list {:option-fn jrequireguide}}]}}))
  (expect
    "(ns ^:no-doc zprint.zprint\n  (:require #?@(:clj [[zprint.macros :refer\n                       [dbg-pr dbg dbg-form dbg-print zfuture]]])\n            [clojure.string :as s]\n            [zprint.finish  :refer [newline-vec]]\n            #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))"
    (zprint-str jr2
                {:parse-string? true,
                 :fn-map {":require" [:none
                                      {:list {:option-fn jrequireguide}}]}}))

  (expect
    "(ns ^:no-doc zprint.zprint\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])\n    [clojure.string     :as s]\n    [zprint.finish      :refer [newline-vec]]\n    [zprint.zfns        :refer [zstring znumstr zbyte-array? zcomment? zsexpr\n                                zseqnws zseqnws-w-nl zfocus-style zstart zfirst\n                                zfirst-no-comment zsecond znthnext zcount zmap\n                                zanonfn? zfn-obj? zfocus zfind-path zwhitespace?\n                                zlist? zcount-zloc-seq-nc-nws zvector? zmap?\n                                zset? zcoll? zuneval? zmeta? ztag zlast zarray?\n                                zatom? zderef zrecord? zns? zobj-to-vec\n                                zexpandarray znewline? zwhitespaceorcomment?\n                                zmap-all zpromise? zfuture? zdelay? zkeyword?\n                                zconstant? zagent? zreader-macro?\n                                zarray-to-shift-seq zdotdotdot zsymbol? znil?\n                                zreader-cond-w-symbol? zreader-cond-w-coll?\n                                zlift-ns zfind zmap-w-nl zmap-w-nl-comma\n                                ztake-append znextnws-w-nl znextnws\n                                znamespacedmap? zmap-w-bl zseqnws-w-bl zsexpr?]]\n    [zprint.comment     :refer [blanks inlinecomment? length-before]]\n    '[zprint.ansi :refer [color-str]]\n    ~`[zprint.config :refer [validate-options merge-deep]]\n    [zprint.zutil       :refer [add-spec-to-docstring]]\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip    :as z]\n    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))"
    (zprint-str jr3
                {:parse-string? true,
                 :fn-map {":require" [:none
                                      {:list {:option-fn jrequireguide}}]}}))

  (expect
    "(ns ^:no-doc zprint.zprint\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print zfuture]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print zfuture]]])\n    [clojure.string     :as s]\n    [zprint.finish      :refer [newline-vec]]\n    [zprint.zfns        :refer [zstring znumstr zbyte-array? zcomment? zsexpr\n                                zseqnws zseqnws-w-nl zfocus-style zstart zfirst\n                                zfirst-no-comment zsecond znthnext zcount zmap\n                                zanonfn? zfn-obj? zfocus zfind-path zwhitespace?\n                                zlist? zcount-zloc-seq-nc-nws zvector? zmap?\n                                zset? zcoll? zuneval? zmeta? ztag zlast zarray?\n                                zatom? zderef zrecord? zns? zobj-to-vec\n                                zexpandarray znewline? zwhitespaceorcomment?\n                                zmap-all zpromise? zfuture? zdelay? zkeyword?\n                                zconstant? zagent? zreader-macro?\n                                zarray-to-shift-seq zdotdotdot zsymbol? znil?\n                                zreader-cond-w-symbol? zreader-cond-w-coll?\n                                zlift-ns zfind zmap-w-nl zmap-w-nl-comma\n                                ztake-append znextnws-w-nl znextnws\n                                znamespacedmap? zmap-w-bl zseqnws-w-bl zsexpr?]]\n    [zprint.comment     :refer [blanks inlinecomment? length-before]]\n    [zprint.ansi        :as    ansi\n                        :refer [color-str]]\n    [zprint.config      :as    config\n                        :refer [validate-options merge-deep]]\n    [zprint.zutil       :refer [add-spec-to-docstring]]\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip    :as z]\n    #_[taoensso.tufte :as tufte :refer (p defnp profiled profile)]))"
    (zprint-str jr4
                {:parse-string? true,
                 :fn-map {":require" [:none
                                      {:list {:option-fn jrequireguide}}]}}))

  ;;
  ;; # rumguide
  ;;


  (def r8
    '(rum/defcs component
       < rum/static
         rum/reactive
         (rum/local 0 :count)
         (rum/local "" :text)
       [state label]
       (let [count-atom (:count state) text-atom (:text state)] [:div])))



  (def cp3
    '(rum/defcs component3
       "This is a component with a doc-string!  How unusual..."
       <
       rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
       (let [count-atom (:count state) text-atom (:text state)] [:div])))

  (def cp4
    '(rum/defcs component4
       <
       rum/static
       rum/reactive
       (rum/local 0 :count)
       (rum/local "" :text)
       (let [count-atom (:count state) text-atom (:text state)] [:div])))

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
    '(rum/defcs
       component
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

  (def cz8a
    '(stuff (rum/defcs component
              "This is a component with a doc-string!  How unusual..."
              < rum/static
                rum/reactive
                (rum/local 0 :count)
                (rum/local "" :text)
              ([state label]
               (let [count-atom (:count state) text-atom (:text state)] [:div]))
              ([state] (component state nil)))))

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


  (def cz8x1
    "(;comment 1
  rum/defcs ;comment 2
  component

  ;comment 3
  \"This is a component with a doc-string!  How unusual...\"
  ;comment 4
  < ;comment 5

  rum/static
                       rum/reactive
                       ;comment 6
                       (rum/local 0 :count)

                       (rum/local \"\" :text)
  ;comment 7
  [state label]
  ;comment 8
  (let [count-atom (:count state)
        text-atom  (:text state)]
    [:div]))")

  (expect
    "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str r8
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component3\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cp3
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component4\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cp4
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz1
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz2
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz3
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz4
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz5
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz6
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz7
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
    (zprint-str cz8
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  {:a :b,\n   \"this\" [is a test],\n   :c [this is a very long vector how do you suppose it will work]}\n   rum/static\n   rum/reactive\n   (rum/local 0 :count)\n   (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz9
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (expect
"(;comment 1\n rum/defcs ;comment 2\n  component\n  ;comment 3\n  \"This is a component with a doc-string!  How unusual...\"\n  ;comment 4\n  < ;comment 5\n  rum/static\n    rum/reactive\n    ;comment 6\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ;comment 7\n  [state label]\n  ;comment 8\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz8x1
                {:parse-string? true,
                 :fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide}}]}}))

  (comment
    ; This is what it should do
    "(;comment 1\n rum/defcs ;comment 2\n  component\n  ;comment 3\n  \"This is a component with a doc-string!  How unusual...\"\n  ;comment 4\n  < ;comment 5\n    rum/static\n    rum/reactive\n    ;comment 6\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n    ;comment 7\n  [state label]\n  ;comment 8\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    ; This is what it does today
  )

  ;
  ; Tests with an alternative rumguide, which uses mark and not spaces.
  ;

  (expect
    "(stuff (rum/defcs component\n         \"This is a component with a doc-string!  How unusual...\"\n         < rum/static\n           rum/reactive\n           (rum/local 0 :count)\n           (rum/local \"\" :text)\n         ([state label]\n          (let [count-atom (:count state) text-atom (:text state)] [:div]))\n         ([state] (component state nil))))"
    (zprint-str cz8a
                {:fn-map {"defcs" [:guided {:list {:option-fn rumguide}}]}}))


  (expect
    "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str r8
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component3\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cp3
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component4\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cp4
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz1
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz2
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz3
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  [state label]\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz4
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz5
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  <\n  rum/static\n  rum/reactive\n  (rum/local 0 :count)\n  (rum/local \"\" :text)\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz6
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  (let [count-atom (:count state) text-atom (:text state)] [:div]))"
    (zprint-str cz7
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(rum/defcs component\n  \"This is a component with a doc-string!  How unusual...\"\n  < rum/static\n    rum/reactive\n    (rum/local 0 :count)\n    (rum/local \"\" :text)\n  ([state label]\n   (let [count-atom (:count state) text-atom (:text state)] [:div]))\n  ([state] (component state nil)))"
    (zprint-str cz8
                {:fn-map {"defcs" [:arg1-force-nl
                                   {:list {:option-fn rumguide-1}}]}}))

  (expect
    "(stuff (rum/defcs component\n         \"This is a component with a doc-string!  How unusual...\"\n         < rum/static\n           rum/reactive\n           (rum/local 0 :count)\n           (rum/local \"\" :text)\n         ([state label]\n          (let [count-atom (:count state) text-atom (:text state)] [:div]))\n         ([state] (component state nil))))"
    (zprint-str cz8a
                {:fn-map {"defcs" [:guided {:list {:option-fn rumguide-1}}]}}))




  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
  ;; End of defexpect
  ;;
  ;; All tests MUST come before this!!!
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
)

