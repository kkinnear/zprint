(ns zprint.zprint-rewrite
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
;; # Tests for rewriting zippers
;;

(def prj
  "(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[expectations \"2.2.0-rc1\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  ;[rum \"0.10.8\"];\n                                  [better-cond \"1.0.1\"]\n\t\t\t\t  [zpst \"0.1.6\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\" \"-Xms2048m\" \"-Xmx2048m\" \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[org.clojure/clojure \"1.10.0-beta3\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [org.clojure/clojure \"1.8.0\"]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]\n     #_[cprop \"0.1.6\"]])\n")

;;
;; Do the regular printing of a leiningen project.clj file
;;

(expect
  "(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[expectations \"2.2.0-rc1\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  ;[rum \"0.10.8\"];\n                                  [better-cond \"1.0.1\"]\n                                  [zpst \"0.1.6\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\"\n             \"-Xms2048m\"\n             \"-Xmx2048m\"\n             \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[org.clojure/clojure \"1.10.0-beta3\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [org.clojure/clojure \"1.8.0\"]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]\n     #_[cprop \"0.1.6\"]])\n"
  (zprint-file-str prj "stuff"))

;;
;; Now see if we can sort the dependencies in this leiningen project.clj
;; file
;;

(expect
  "(defproject zprint \"0.4.14\"\n  :description \"Pretty print zippers and s-expressions\"\n  :url \"https://github.com/kkinnear/zprint\"\n  :license {:name \"MIT License\",\n            :url \"https://opensource.org/licenses/MIT\",\n            :key \"mit\",\n            :year 2015}\n  :plugins\n    [[lein-expectations \"0.0.8\"] [lein-codox \"0.10.3\"] [lein-zprint \"0.3.12\"]]\n  :profiles {:dev {:dependencies [[better-cond \"1.0.1\"]\n                                  [clojure-future-spec \"1.9.0-alpha17\"]\n                                  [com.taoensso/tufte \"1.1.1\"]\n                                  ;[rum \"0.10.8\"];\n                                  [expectations \"2.2.0-rc1\"]\n                                  #_[org.clojure/clojurescript \"1.9.946\"]\n                                  [org.clojure/core.match \"0.3.0-alpha5\"]\n                                  [zpst \"0.1.6\"]]},\n             :uberjar {:aot [zprint.core zprint.main],\n                       ; For 1.9.0-alpha17, use this for the :aot value\n                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],\n                       :main zprint.main,\n                       :dependencies [[clojure-future-spec \"1.9.0-alpha17\"]],\n                       :omit-source true,\n                       :uberjar-name \"zprint-filter-%s\"}}\n  ; Clojure 1.8 you can exclude all sources in the uberjar\n  :uberjar-exclusions [#\"\\.(clj|java|txt)\"]\n  ; Clojure 1.9 requires the .clj files in the uberjar\n  ; :uberjar-exclusions [#\"\\.(clj\\.|java|cljs|txt)\"]\n  :jar-exclusions [#\"\\.(clj$|clj\\.|java|txt)\"]\n  :zprint {:old? false}\n  :jvm-opts ^:replace\n            [\"-server\"\n             \"-Xms2048m\"\n             \"-Xmx2048m\"\n             \"-Xss500m\"\n             \"-XX:-OmitStackTraceInFastThrow\"]\n  :scm {:name \"git\", :url \"https://github.com/kkinnear/zprint\"}\n  :codox {:namespaces [zprint.core],\n          :doc-files\n            [\"README.md\" \"doc/bang.md\" \"doc/graalvm.md\" \"doc/filter.md\"],\n          :metadata {:doc/format :markdown}}\n  :dependencies\n    [#_[cprop \"0.1.6\"]\n     #_[org.clojure/clojure \"1.10.0-beta3\"]\n     [org.clojure/clojure \"1.8.0\"]\n     #_[org.clojure/clojure \"1.9.0\"]\n     [rewrite-clj \"0.6.1\" :exclusions [[com.cemerick/austin]]]\n     [rewrite-cljs \"0.4.4\" :exclusions [[org.clojure/clojurescript]]]\n     #_[table \"0.4.0\" :exclusions [[org.clojure/clojure]]]])\n"
  (zprint-file-str prj "stuff" {:style :sort-dependencies}))
