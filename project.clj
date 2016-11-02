(defproject zprint "0.2.7"
  :description "Pretty print zippers and s-expressions"
  :url "https://github.com/kkinnear/zprint"
  :license {:name "MIT License"
	    :url "https://opensource.org/licenses/MIT"
	    :key "mit"
	    :year 2015}
  :plugins [[lein-expectations "0.0.8"]
	    [lein-zprint "0.1.7"]]
  :profiles {:dev {:dependencies [[expectations "2.0.16"]]}}
  :zprint {:old? false}
  :dependencies [;[org.clojure/clojure "1.9.0-alpha13"]
                 [org.clojure/clojure "1.8.0"]
                 [rewrite-cljs "0.4.3"]
                 [rewrite-clj "0.4.13" :exclusions [[com.cemerick/austin]]]
		 [table "0.4.0" :exclusions [[org.clojure/clojure]]]
		 [trptcolin/versioneer "0.1.0"]
		 [prismatic/schema "1.1.3"]
		 [cprop "0.1.6"]
		 ])
