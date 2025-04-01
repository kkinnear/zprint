(ns zprint.rewrite-test
  (:require [expectations.clojure.test
             #?(:clj :refer
                :cljs :refer-macros) [defexpect expect]]
            [zprint.core :refer [set-options! zprint-file-str zprint-str]]))

;; Keep some of the test on wrapping so they still work
;!zprint {:comment {:wrap? false}}

;
; Keep tests from configuring from any $HOME/.zprintrc or local .zprintrc
;

(set-options! {:configured? true})

(defexpect rewrite-tests

  ;;
  ;; # Tests for rewriting zippers
  ;;

  (def prj2
    "(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[expectations \"2.2.0-rc1\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  ;[rum \"0.10.8\"];\n                                  [better-cond \"1.0.1\"]\n\t\t\t\t  [zpst \"0.1.6\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\" \"-Xms2048m\" \"-Xmx2048m\" \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[org.clojure/clojure \"1.10.0-beta3\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [org.clojure/clojure \"1.8.0\"]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]\n     #_[cprop \"0.1.6\"]])\n")

  ;; Should not sort because it has a comment

  (expect
    "(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[expectations \"2.2.0-rc1\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  ;[rum \"0.10.8\"];\n                                  [better-cond \"1.0.1\"]\n                                  [zpst \"0.1.6\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main\n                       ;clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\"\n             \"-Xms2048m\"\n             \"-Xmx2048m\"\n             \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[cprop \"0.1.6\"]\n     #_[org.clojure/clojure \"1.10.0-beta3\"]\n     [org.clojure/clojure \"1.8.0\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]])\n"
    (zprint-file-str prj2 "stuff" {:style :sort-dependencies}))


  (def prj3
    "(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[expectations \"2.2.0-rc1\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  [rum \"0.10.8\"]\n                                  [better-cond \"1.0.1\"]\n\t\t\t\t  [zpst \"0.1.6\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\" \"-Xms2048m\" \"-Xmx2048m\" \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[org.clojure/clojure \"1.10.0-beta3\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [org.clojure/clojure \"1.8.0\"]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]\n     #_[cprop \"0.1.6\"]])\n")


  ;;
  ;; Now see if we can sort the dependencies in this leiningen project.clj
  ;; file without comments
  ;;


  (expect
    "(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[better-cond \"1.0.1\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  [expectations \"2.2.0-rc1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [rum \"0.10.8\"]\n                                  [zpst \"0.1.6\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main\n                       ;clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\"\n             \"-Xms2048m\"\n             \"-Xmx2048m\"\n             \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[cprop \"0.1.6\"]\n     #_[org.clojure/clojure \"1.10.0-beta3\"]\n     [org.clojure/clojure \"1.8.0\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]])"
    (zprint-str prj3 {:parse-string? true, :style :sort-dependencies}))



  ;;
  ;; :sort-require
  ;;
  ;; Issue #310
  ;;

  (def i310
    "(ns i310\n  (:require \n\t    [a.b]\n\t    [b.c.e.f]\n\t    [b.c.d.e]\n            [a.b.c.e]\n\t    [c.b.a]\n\t    [a.b.c.d.e.f]\n\t    [c.b.d]\n            [a.b.c.d]\n\t    [a.b.c.f]\n\t    [a.b.c]\n\t    \n\t    ))\n  \n\t    \n")

  (expect
    "(ns i310\n  (:require [a.b]\n            [b.c.e.f]\n            [b.c.d.e]\n            [a.b.c.e]\n            [c.b.a]\n            [a.b.c.d.e.f]\n            [c.b.d]\n            [a.b.c.d]\n            [a.b.c.f]\n            [a.b.c]))"
    (zprint-str i310 {:parse-string? true}))

  (expect
    "(ns i310\n  (:require [a.b]\n            [a.b.c]\n            [a.b.c.d]\n            [a.b.c.d.e.f]\n            [a.b.c.e]\n            [a.b.c.f]\n            [c.b.a]\n            [c.b.d]\n            [b.c.d.e]\n            [b.c.e.f]))"
    (zprint-str i310
                {:parse-string? true,
                 :style {:style-call :sort-require, :regex-vec [:| #"^b\."]}}))

  (expect
    "(ns i310\n  (:require [a.b]\n            [b.c.e.f]\n            [b.c.d.e]\n            [a.b.c.e]\n            [c.b.a]\n            [a.b.c.d.e.f]\n            [c.b.d]\n            [a.b.c.d]\n            [a.b.c.f]\n            [a.b.c]))"
    (zprint-str i310 {:parse-string? true}))

  (expect
    "(ns i310\n  (:require [a.b]\n            [a.b.c]\n            [a.b.c.d]\n            [a.b.c.d.e.f]\n            [a.b.c.e]\n            [a.b.c.f]\n            [b.c.e.f]\n            [c.b.a]\n            [c.b.d]\n            [b.c.d.e]))"
    (zprint-str i310
                {:parse-string? true,
                 :style {:style-call :sort-require,
                         :regex-vec [:| #"^b\.c\.d"]}}))

  (expect
    "(ns i310\n  (:require [b.c.d.e]\n            [a.b]\n            [a.b.c]\n            [a.b.c.d]\n            [a.b.c.d.e.f]\n            [a.b.c.e]\n            [a.b.c.f]\n            [b.c.e.f]\n            [c.b.a]\n            [c.b.d]))"
    (zprint-str i310
                {:parse-string? true,
                 :style {:style-call :sort-require, :regex-vec [#"^b\.c\.d"]}}))

  (expect
    "(ns i310\n  (:require [b.c.d.e]\n            [a.b]\n            [a.b.c]\n            [a.b.c.d]\n            [a.b.c.d.e.f]\n            [a.b.c.e]\n            [a.b.c.f]\n            [c.b.a]\n            [c.b.d]\n            [b.c.e.f]))"
    (zprint-str i310
                {:parse-string? true,
                 :style {:style-call :sort-require,
                         :regex-vec [#"^b\.c\.d" :| #"^b\.c\.e"]}}))

  (def corens1
    "(ns zprint.core
#?@(:cljs [[:require-macros
[zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])
(:require
#?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])
clojure.string
#?@(:cljs [[cljs.reader :refer [read-string]]])
#?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])
[zprint.zprint      :as    zprint
:refer [fzprint line-count max-width line-widths
expand-tabs zcolor-map
determine-ending-split-lines]]
[zprint.finish      :refer [cvec-to-style-vec compress-style no-style-map
color-comp-vec handle-lines]]
[zprint.comment     :refer [fzprint-inline-comments fzprint-wrap-comments
fzprint-align-inline-comments blanks]]
[zprint.config      :as    config
:refer [add-calculated-options config-set-options!
get-options config-configure-all! reset-options!
help-str get-explained-options
get-explained-set-options
get-explained-all-options get-default-options
validate-options apply-style perform-remove
no-color-map merge-deep sci-load-string
config-and-validate
]]
[zprint.zutil       :refer [zmap-all zcomment? whitespace? znewline?
find-root-and-path-nw]]
[zprint.sutil]
[zprint.focus       :refer [range-ssv]]
[zprint.range       :refer [expand-range-to-top-level split-out-range
reassemble-range]]
[rewrite-clj.parser :as p]
[rewrite-clj.zip :as z :refer [edn* string]]
#_[clojure.spec.alpha :as s])
#?@(:clj ((:import #?(:bb []
:clj (java.net URL URLConnection))
#?(:bb []
:clj (java.util.concurrent Executors))
(java.io File)
(java.util Date)))))")

  (expect
    "(ns zprint.core\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])\n    #_[clojure.spec.alpha :as s]\n    #?@(:cljs [[cljs.reader :refer [read-string]]])\n    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])\n    clojure.string\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip :as z :refer [edn* string]]\n    [zprint.comment :refer\n     [fzprint-inline-comments fzprint-wrap-comments\n      fzprint-align-inline-comments blanks]]\n    [zprint.config :as config :refer\n     [add-calculated-options config-set-options! get-options\n      config-configure-all! reset-options! help-str get-explained-options\n      get-explained-set-options get-explained-all-options get-default-options\n      validate-options apply-style perform-remove no-color-map merge-deep\n      sci-load-string config-and-validate]]\n    [zprint.finish :refer\n     [cvec-to-style-vec compress-style no-style-map color-comp-vec\n      handle-lines]]\n    [zprint.focus :refer [range-ssv]]\n    [zprint.range :refer\n     [expand-range-to-top-level split-out-range reassemble-range]]\n    [zprint.sutil]\n    [zprint.zprint :as zprint :refer\n     [fzprint line-count max-width line-widths expand-tabs zcolor-map\n      determine-ending-split-lines]]\n    [zprint.zutil :refer\n     [zmap-all zcomment? whitespace? znewline? find-root-and-path-nw]])\n  #?@(:clj ((:import #?(:bb []\n                        :clj (java.net URL URLConnection))\n                     #?(:bb []\n                        :clj (java.util.concurrent Executors))\n                     (java.io File)\n                     (java.util Date)))))"
    (zprint-str corens1
                {:parse-string? true,
                 :style {:style-call :sort-require, :sort-refer? false}}))


  (expect
    "(ns zprint.core\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])\n    [zprint.comment :refer\n     [fzprint-inline-comments fzprint-wrap-comments\n      fzprint-align-inline-comments blanks]]\n    #?@(:cljs [[cljs.reader :refer [read-string]]])\n    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])\n    [zprint.config :as config :refer\n     [add-calculated-options config-set-options! get-options\n      config-configure-all! reset-options! help-str get-explained-options\n      get-explained-set-options get-explained-all-options get-default-options\n      validate-options apply-style perform-remove no-color-map merge-deep\n      sci-load-string config-and-validate]]\n    [zprint.finish :refer\n     [cvec-to-style-vec compress-style no-style-map color-comp-vec\n      handle-lines]]\n    [zprint.focus :refer [range-ssv]]\n    [zprint.range :refer\n     [expand-range-to-top-level split-out-range reassemble-range]]\n    [zprint.sutil]\n    [zprint.zprint :as zprint :refer\n     [fzprint line-count max-width line-widths expand-tabs zcolor-map\n      determine-ending-split-lines]]\n    [zprint.zutil :refer\n     [zmap-all zcomment? whitespace? znewline? find-root-and-path-nw]]\n    #_[clojure.spec.alpha :as s]\n    clojure.string\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip :as z :refer [edn* string]])\n  #?@(:clj ((:import #?(:bb []\n                        :clj (java.net URL URLConnection))\n                     #?(:bb []\n                        :clj (java.util.concurrent Executors))\n                     (java.io File)\n                     (java.util Date)))))"
    (zprint-str corens1
                {:parse-string? true,
                 :style {:style-call :sort-require,
                         :regex-vec [#"^zprint\."],
                         :sort-refer? false}}))

  (expect
    "(ns zprint.core\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])\n    [zprint.comment :refer\n     [fzprint-inline-comments fzprint-wrap-comments\n      fzprint-align-inline-comments blanks]]\n    #?@(:cljs [[cljs.reader :refer [read-string]]])\n    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])\n    [zprint.config :as config :refer\n     [add-calculated-options config-set-options! get-options\n      config-configure-all! reset-options! help-str get-explained-options\n      get-explained-set-options get-explained-all-options get-default-options\n      validate-options apply-style perform-remove no-color-map merge-deep\n      sci-load-string config-and-validate]]\n    [zprint.finish :refer\n     [cvec-to-style-vec compress-style no-style-map color-comp-vec\n      handle-lines]]\n    [zprint.focus :refer [range-ssv]]\n    [zprint.range :refer\n     [expand-range-to-top-level split-out-range reassemble-range]]\n    [zprint.sutil]\n    [zprint.zprint :as zprint :refer\n     [fzprint line-count max-width line-widths expand-tabs zcolor-map\n      determine-ending-split-lines]]\n    [zprint.zutil :refer\n     [zmap-all zcomment? whitespace? znewline? find-root-and-path-nw]]\n    #_[clojure.spec.alpha :as s]\n    clojure.string\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip :as z :refer [edn* string]])\n  #?@(:clj ((:import #?(:bb []\n                        :clj (java.net URL URLConnection))\n                     #?(:bb []\n                        :clj (java.util.concurrent Executors))\n                     (java.io File)\n                     (java.util Date)))))"
    (zprint-str corens1
                {:parse-string? true,
                 :style {:style-call :sort-require,
                         :regex-vec [#"^zprint\." #"^clojure\."],
                         :sort-refer? false}}))

  (expect
    "(ns zprint.core\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])\n    [zprint.comment :refer\n     [fzprint-inline-comments fzprint-wrap-comments\n      fzprint-align-inline-comments blanks]]\n    #?@(:cljs [[cljs.reader :refer [read-string]]])\n    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])\n    [zprint.config :as config :refer\n     [add-calculated-options config-set-options! get-options\n      config-configure-all! reset-options! help-str get-explained-options\n      get-explained-set-options get-explained-all-options get-default-options\n      validate-options apply-style perform-remove no-color-map merge-deep\n      sci-load-string config-and-validate]]\n    [zprint.finish :refer\n     [cvec-to-style-vec compress-style no-style-map color-comp-vec\n      handle-lines]]\n    [zprint.focus :refer [range-ssv]]\n    [zprint.range :refer\n     [expand-range-to-top-level split-out-range reassemble-range]]\n    [zprint.sutil]\n    [zprint.zprint :as zprint :refer\n     [fzprint line-count max-width line-widths expand-tabs zcolor-map\n      determine-ending-split-lines]]\n    [zprint.zutil :refer\n     [zmap-all zcomment? whitespace? znewline? find-root-and-path-nw]]\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip :as z :refer [edn* string]]\n    #_[clojure.spec.alpha :as s]\n    clojure.string)\n  #?@(:clj ((:import #?(:bb []\n                        :clj (java.net URL URLConnection))\n                     #?(:bb []\n                        :clj (java.util.concurrent Executors))\n                     (java.io File)\n                     (java.util Date)))))"
    (zprint-str corens1
                {:parse-string? true,
                 :style {:style-call :sort-require,
                         :regex-vec [#"^zprint\." :| #"^clojure\."],
                         :sort-refer? false}}))

  (expect
    "(ns zprint.core\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])\n    [rewrite-clj.parser :as p]\n    #?@(:cljs [[cljs.reader :refer [read-string]]])\n    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])\n    [rewrite-clj.zip :as z :refer [edn* string]]\n    [zprint.comment :refer\n     [fzprint-inline-comments fzprint-wrap-comments\n      fzprint-align-inline-comments blanks]]\n    [zprint.config :as config :refer\n     [add-calculated-options config-set-options! get-options\n      config-configure-all! reset-options! help-str get-explained-options\n      get-explained-set-options get-explained-all-options get-default-options\n      validate-options apply-style perform-remove no-color-map merge-deep\n      sci-load-string config-and-validate]]\n    [zprint.finish :refer\n     [cvec-to-style-vec compress-style no-style-map color-comp-vec\n      handle-lines]]\n    [zprint.focus :refer [range-ssv]]\n    [zprint.range :refer\n     [expand-range-to-top-level split-out-range reassemble-range]]\n    [zprint.sutil]\n    [zprint.zprint :as zprint :refer\n     [fzprint line-count max-width line-widths expand-tabs zcolor-map\n      determine-ending-split-lines]]\n    [zprint.zutil :refer\n     [zmap-all zcomment? whitespace? znewline? find-root-and-path-nw]]\n    #_[clojure.spec.alpha :as s]\n    clojure.string)\n  #?@(:clj ((:import #?(:bb []\n                        :clj (java.net URL URLConnection))\n                     #?(:bb []\n                        :clj (java.util.concurrent Executors))\n                     (java.io File)\n                     (java.util Date)))))"
    (zprint-str corens1
                {:parse-string? true,
                 :style {:style-call :sort-require,
                         :regex-vec [:| #"^zprint\." #"^clojure\."],
                         :sort-refer? false}}))

  ;;
  ;; Try multiple styles together
  ;;

  (expect
    "(ns zprint.core\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])\n    [zprint.comment     :refer [fzprint-inline-comments fzprint-wrap-comments\n                                fzprint-align-inline-comments blanks]]\n    #?@(:cljs [[cljs.reader :refer [read-string]]])\n    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])\n    [zprint.config      :as    config\n                        :refer [add-calculated-options config-set-options!\n                                get-options config-configure-all! reset-options!\n                                help-str get-explained-options\n                                get-explained-set-options\n                                get-explained-all-options get-default-options\n                                validate-options apply-style perform-remove\n                                no-color-map merge-deep sci-load-string\n                                config-and-validate]]\n    [zprint.finish      :refer [cvec-to-style-vec compress-style no-style-map\n                                color-comp-vec handle-lines]]\n    [zprint.focus       :refer [range-ssv]]\n    [zprint.range       :refer [expand-range-to-top-level split-out-range\n                                reassemble-range]]\n    [zprint.sutil]\n    [zprint.zprint      :as    zprint\n                        :refer [fzprint line-count max-width line-widths\n                                expand-tabs zcolor-map\n                                determine-ending-split-lines]]\n    [zprint.zutil       :refer [zmap-all zcomment? whitespace? znewline?\n                                find-root-and-path-nw]]\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip    :as    z\n                        :refer [edn* string]]\n    #_[clojure.spec.alpha :as s]\n    clojure.string)\n  #?@(:clj ((:import\n              #?(:bb []\n                 :clj (java.net URL URLConnection))\n              #?(:bb []\n                 :clj (java.util.concurrent Executors))\n              (java.io     File)\n              (java.util   Date)))))"
    (zprint-str corens1
                {:parse-string? true,
                 :style [:how-to-ns
                         {:style-call :sort-require,
                          :regex-vec [#"^zprint\." :| #"^clojure\."],
                          :sort-refer? false} :ns-justify]}))

  ;;
  ;; Will it sort with comments?  I hope not...
  ;;

  (def i310a
    "(ns i310a\n  (:require \n\t    [a.b]\n\t    [b.c.e.f]\n\t    [b.c.d.e]\n            [a.b.c.e]\n\t    ; This is a comment\n\t    [c.b.a]\n\t    [a.b.c.d.e.f]\n\t    [c.b.d]\n            [a.b.c.d]\n\t    [a.b.c.f]\n\t    [a.b.c]\n\t    \n\t    ))\n  \n")

  (expect
    "(ns i310a\n  (:require [a.b]\n            [b.c.e.f]\n            [b.c.d.e]\n            [a.b.c.e]\n            ; This is a comment\n            [c.b.a]\n            [a.b.c.d.e.f]\n            [c.b.d]\n            [a.b.c.d]\n            [a.b.c.f]\n            [a.b.c]))"
    (zprint-str i310a {:parse-string? true, :style :sort-require}))

  ;;
  ;; See if sorting refers works
  ;;

  (expect
    "(ns zprint.core\n  #?@(:cljs [[:require-macros\n              [zprint.macros :refer [dbg dbg-pr dbg-form dbg-print]]]])\n  (:require\n    #?@(:clj [[zprint.macros :refer [dbg-pr dbg dbg-form dbg-print]]])\n    #_[clojure.spec.alpha :as s]\n    #?@(:cljs [[cljs.reader :refer [read-string]]])\n    #?@(:clj [[clojure.java.io :as io] [clojure.repl :refer [source-fn]]])\n    clojure.string\n    [rewrite-clj.parser :as p]\n    [rewrite-clj.zip    :as    z\n                        :refer [edn* string]]\n    [zprint.comment     :refer [blanks fzprint-align-inline-comments\n                                fzprint-inline-comments fzprint-wrap-comments]]\n    [zprint.config      :as    config\n                        :refer [add-calculated-options apply-style\n                                config-and-validate config-configure-all!\n                                config-set-options! get-default-options\n                                get-explained-all-options get-explained-options\n                                get-explained-set-options get-options help-str\n                                merge-deep no-color-map perform-remove\n                                reset-options! sci-load-string\n                                validate-options]]\n    [zprint.finish      :refer [color-comp-vec compress-style cvec-to-style-vec\n                                handle-lines no-style-map]]\n    [zprint.focus       :refer [range-ssv]]\n    [zprint.range       :refer [expand-range-to-top-level reassemble-range\n                                split-out-range]]\n    [zprint.sutil]\n    [zprint.zprint      :as    zprint\n                        :refer [determine-ending-split-lines expand-tabs fzprint\n                                line-count line-widths max-width zcolor-map]]\n    [zprint.zutil       :refer [find-root-and-path-nw whitespace? zcomment?\n                                zmap-all znewline?]])\n  #?@(:clj ((:import\n              #?(:bb []\n                 :clj (java.net URL URLConnection))\n              #?(:bb []\n                 :clj (java.util.concurrent Executors))\n              (java.io     File)\n              (java.util   Date)))))"
    (zprint-str corens1
                {:parse-string? true,
                 :style [{:style-call :sort-require} :ns-justify]}))

  ;;
  ;; Bug with :refer :all
  ;;

  (def i310l
    "(defn -main\n  \"Launch repl and init landing app\"\n  [& args]\n  (try (when-not (force-option? (first args)) (landing/-main))\n       (core-repl/start-repl)\n       (ns user\n         (:require\n          [automaton-core.dev :refer :all]))\n       (catch Exception e\n         ;; The print is done on purpose, as it is the message in the console if you launch the repl and it doesn't work\n         (prn (str \"error: \" e))\n         (core-log/error (ex-info \"Failed to start landing, relaunch with user/\"\n                                  {:error e}))\n         (throw e))))\n")

  (expect
    "(defn -main\n  \"Launch repl and init landing app\"\n  [& args]\n  (try (when-not (force-option? (first args)) (landing/-main))\n       (core-repl/start-repl)\n       (ns user\n         (:require [automaton-core.dev :refer :all]))\n       (catch Exception e\n         ;; The print is done on purpose, as it is the message in the\n         ;; console if you launch the repl and it doesn't work\n         (prn (str \"error: \" e))\n         (core-log/error (ex-info \"Failed to start landing, relaunch with user/\"\n                                  {:error e}))\n         (throw e))))"
    (zprint-str i310l {:parse-string? true, :style :sort-require}))

  ;;
  ;; Bug with (:require ) (i.e., empty :require)
  ;;

  (def i310p
    "(ns automaton-core.utils.uuid-gen\n  \"Generate uuid, is a proxy to `http://danlentz.github.io/clj-uuid/`\"\n  (:require))\n")
  (expect
    "(ns automaton-core.utils.uuid-gen\n  \"Generate uuid, is a proxy to `http://danlentz.github.io/clj-uuid/`\"\n  (:require))"
    (zprint-str i310p {:parse-string? true, :style :sort-require}))

   ;;
   ;; :style :sort-require loops forever.  Turns out that if the :require
   ;; isn't the first thing in the collection where it appears, it will
   ;; keep putting the :require in front of where it is going to go next,
   ;; causing a very hard infinite loop.  Issue #346.

 (expect
"(ns a\n  (:require [a]\n            [b]\n            [c]))"
(zprint-str "(ns a (:require [c] [b] [a]))" {:parse-string? true :style :sort-require}))

(expect
"(ns a (stuff :require [c] [b] [a]))"
(zprint-str "(ns a (stuff :require [c] [b] [a]))" {:parse-string? true :style :sort-require}))

(expect
"(ns a\n  (#?(:clj :require\n      :cljs :require-macros)\n   [b]))"
(zprint-str "(ns a (#?(:clj :require  :cljs :require-macros) [b]))" {:parse-string? true :style :sort-require}))

;;
;; Issue #342 -- :sort-require throws a NPE when the :refer is empty
;;

(def
i342
"(ns foo (:require [a.b.c :refer []]))\n")

(expect
"(ns foo\n  (:require [a.b.c :refer []]))"
(zprint-str "(ns foo (:require [a.b.c :refer []]))\n" {:parse-string? true :style :sort-require}))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
  ;; End of defexpect
  ;;
  ;; All tests MUST come before this!!!
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
)

