;!zprint {:vector {:wrap? false}}
(defproject zprint "0.4.4"
  :description "Pretty print zippers and s-expressions"
  :url "https://github.com/kkinnear/zprint"
  :license {:name "MIT License",
            :url "https://opensource.org/licenses/MIT",
            :key "mit",
            :year 2015}
  :plugins [[lein-expectations "0.0.8"] [lein-zprint "0.3.3"]]
  :profiles {:dev {:dependencies [#_[expectations "2.0.16"]
                                  [expectations "2.2.0-rc1"]
                                  [com.taoensso/tufte "1.1.1"]
                                  [clojure-future-spec "1.9.0-alpha17"]
				  ]},
             :uberjar {:aot [zprint.core zprint.main],
		       ; For 1.9.0-alpha17, use this for the :aot value
	               ;:aot [zprint.core zprint.main clojure.core.specs.alpha],
                       :main zprint.main,
                       :dependencies [[clojure-future-spec "1.9.0-alpha17"]],
                       :omit-source true,
                       :uberjar-name "zprint-filter-0.4.4"}}
  ; Clojure 1.8 you can exclude all sources in the uberjar
   :uberjar-exclusions [#"\.(clj|java|cljs)"]
  ; Clojure 1.9 requires the .clj files in the uberjar
  ; :uberjar-exclusions [#"\.(java|cljs)"]
  :zprint {:old? false}
  :dependencies
    [#_[org.clojure/clojure "1.9.0-beta3"] 
     [org.clojure/clojure "1.8.0"]
     [rewrite-cljs "0.4.4" :exclusions [[org.clojure/clojurescript]]]
     [rewrite-clj "0.6.0" :exclusions [[com.cemerick/austin]]]
     #_[table "0.4.0" :exclusions [[org.clojure/clojure]]]
     #_[cprop "0.1.6"]])
