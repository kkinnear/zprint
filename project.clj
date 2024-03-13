(defproject zprint "1.2.9"
  :description "Pretty print Clojure source and s-expressions"
  :url "https://github.com/kkinnear/zprint"
  :license {:name "MIT License",
            :url "https://opensource.org/licenses/MIT",
            :key "mit",
            :year 2015}
  :plugins [[lein-doo "0.1.10"] [lein-codox "0.10.3"] [lein-zprint "1.2.9"]]
  :profiles {:repl {:dependencies [#_[com.taoensso/tufte "1.1.1"]
                                   [com.taoensso/tufte "2.4.5"]
                                   [better-cond "1.0.1"]
                                   [olical/cljs-test-runner "3.7.0"]
                                   [pjstadig/humane-test-output "0.10.0"]
                                   [org.clojure/core.match "0.3.0-alpha5"]
                                   #_[com.bhauman/rebel-readline "0.1.4"]
                                   [hiccup "1.0.5"]
                                   #_[clojure-future-spec "1.9.0-alpha17"]]},
             :dev {:dependencies [#_[com.bhauman/rebel-readline "0.1.4"]
                                  [hiccup "1.0.5"]]},
             :expectations {:dependencies
                              [[com.github.seancorfield/expectations "2.0.143"]
                               [pjstadig/humane-test-output "0.10.0"]]},
             :uberjar {;:aot [zprint.core zprint.main],
                       ; For 1.9.0-alpha17, use this for the :aot value
                       :aot [zprint.core zprint.main clojure.core.specs.alpha],
                       :main zprint.main,
                       :dependencies [#_[clojure-future-spec "1.9.0-alpha17"]],
                       :omit-source true,
                       :uberjar-name "zprint-filter-%s"}}
  ; Clojure 1.8 you can exclude all sources in the uberjar
  ; :uberjar-exclusions [#"\.(clj|java|txt)"] Clojure 1.9 requires the .clj
  ; files in the uberjar
  :uberjar-exclusions [#"\.(clj\.|java|cljs|txt)"]
  :jar-exclusions [#"\.(clj$|clj\.|java|txt|cljs)"]
  :zprint {:old? false}
  :jvm-opts ^:replace
            ["-server"
             "-Xms2048m"
             "-Xmx2048m"
             "-Xss500m"
             "-XX:-OmitStackTraceInFastThrow"]
  :scm {:name "git", :url "https://github.com/kkinnear/zprint"}
  :codox {:namespaces [zprint.core],
          :doc-files
            ["README.md" "doc/bang.md" "doc/graalvm.md" "doc/filter.md"],
          :metadata {:doc/format :markdown}}
  :dependencies [#_[org.clojure/clojure "1.10.3"]
                 [org.clojure/clojure "1.11.1"]
                 #_[org.clojure/clojure "1.12.0-alpha8"]
                 #_[org.clojure/clojure "1.10.0"]
                 #_[org.clojure/clojure "1.9.0"]
                 #_[org.clojure/clojure "1.8.0"]
                 #_[org.babashka/sci "0.7.39"]
                 [org.babashka/sci "0.8.40"]
                 [rewrite-clj/rewrite-clj "1.1.47"]])
