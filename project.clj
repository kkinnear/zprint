(defproject zprint "0.2.1"
  :description "Pretty print zippers and s-expressions"
  :url "https://github.com/kkinnear/zprint"
  :license {:name "MIT License"
	    :url "https://opensource.org/licenses/MIT"
	    :key "mit"
	    :year 2015}
  :plugins [[lein-expectations "0.0.8"]
	    [lein-zprint "0.1.1"]]
  :profiles {:dev {:dependencies [[expectations "2.0.16"]
				  [com.taoensso/timbre "4.0.2"]
				  [com.taoensso/tufte "1.0.2"]
				  [criterium "0.4.3"]]}
             :uberjar {:aot :all 
	               :dependencies [[org.clojure/clojure "1.8.0"]]}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [rewrite-clj "0.4.13" :exclusions [[com.cemerick/austin]]]
		 [table "0.4.0" :exclusions [[org.clojure/clojure]]]
		 [trptcolin/versioneer "0.1.0"]
		 [prismatic/schema "1.0.5"]
		 [cprop "0.1.6"]])
