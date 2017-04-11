(defproject zprint
  "0.3.1"
  :description "Pretty print zippers and s-expressions"
  :url "https://github.com/kkinnear/zprint"
  :license {:name "MIT License",
            :url "https://opensource.org/licenses/MIT",
            :key "mit",
            :year 2015}
  :plugins [[lein-expectations "0.0.8"] [lein-zprint "0.2.0"]]
  :profiles {:dev {:dependencies [[expectations "2.0.16"]
                                  [com.taoensso/tufte "1.1.1"]
				  [clojure-future-spec "1.9.0-alpha15"]]},
             :uberjar {:aot [zprint.core zprint.main],
                       :main zprint.main,
		       :dependencies [[clojure-future-spec "1.9.0-alpha15"]]
		       :omit-source true
                       :uberjar-name "zprint-filter-0.3.1"}}
  ; Clojure 1.8 you can exclude all sources
  :uberjar-exclusions [#"\.(clj|java|cljs)"]
  ; Clojure 1.9 requires the .clj files
  ; :uberjar-exclusions [#"\.(java|cljs)"]
  :zprint {:old? false}
  :dependencies
    [#_[org.clojure/clojure "1.9.0-alpha15"] 
       [org.clojure/clojure "1.8.0"]
     #_[clojure-future-spec "1.9.0-alpha15"]
       [rewrite-cljs "0.4.3" :exclusions [[org.clojure/clojurescript]]]
       [rewrite-clj "0.4.13" :exclusions [[com.cemerick/austin]]]
     #_[table "0.4.0" :exclusions [[org.clojure/clojure]]] 
     #_[cprop "0.1.6"]])
