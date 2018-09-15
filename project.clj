(defproject zprint "0.4.11"
  :description "Pretty print zippers and s-expressions"
  :url "https://github.com/kkinnear/zprint"
  :license {:name "MIT License",
            :url "https://opensource.org/licenses/MIT",
            :key "mit",
            :year 2015}
  :plugins
    [[lein-expectations "0.0.8"] [lein-codox "0.10.3"] [lein-zprint "0.3.11"]]
  :profiles {:dev {:dependencies [[expectations "2.2.0-rc1"]
                                  [com.taoensso/tufte "1.1.1"]
                                  #_[org.clojure/clojurescript "1.9.946"]
                                  ;[rum "0.10.8"];
                                  [better-cond "1.0.1"]
                                  [org.clojure/core.match "0.3.0-alpha5"]
                                  [clojure-future-spec "1.9.0-alpha17"]]},
             :uberjar {:aot [zprint.core zprint.main],
                       ; For 1.9.0-alpha17, use this for the :aot value
                       ;:aot [zprint.core zprint.main clojure.core.specs.alpha],
                       :main zprint.main,
                       :dependencies [[clojure-future-spec "1.9.0-alpha17"]],
                       :omit-source true,
                       :uberjar-name "zprint-filter-%s"}}
  ; Clojure 1.8 you can exclude all sources in the uberjar
  :uberjar-exclusions [#"\.(clj|java|txt)"]
  ; Clojure 1.9 requires the .clj files in the uberjar
  ; :uberjar-exclusions [#"\.(clj\.|java|cljs|txt)"]
  :jar-exclusions [#"\.(clj$|clj\.|java|txt)"]
  :zprint {:old? false}
  :jvm-opts ^:replace ["-server" "-Xms2048m" "-Xmx2048m" "-Xss500m"]
  :scm {:name "git" :url "http//github.com/kkinnear/zprint"}
  :codox {:namespaces [zprint.core],
          :doc-files
            ["README.md" "doc/bang.md" "doc/graalvm.md" "doc/filter.md"],
          :metadata {:doc/format :markdown}}
  :dependencies
    [#_[org.clojure/clojure "1.10.0-RC1"]
     #_[org.clojure/clojure "1.9.0"]
     [org.clojure/clojure "1.8.0"]
     [rewrite-cljs "0.4.4" :exclusions [[org.clojure/clojurescript]]]
     [rewrite-clj "0.6.1" :exclusions [[com.cemerick/austin]]]
     #_[table "0.4.0" :exclusions [[org.clojure/clojure]]]
     #_[cprop "0.1.6"]])
