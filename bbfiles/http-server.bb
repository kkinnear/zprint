#!/usr/bin/env bb
#_" -*- mode: clojure; -*-"
;; Babashka HTTP server for serving static files, similar to
;;   `python -m http.server` but more flexible :)
;; Based veru closely on: gist from holyjak/http-server.bb
;; Based on
;; https://github.com/babashka/babashka/blob/master/examples/image_viewer.clj

;!zprint {:style [:require-justify :hiccup]}

(ns http-server
  (:require
    [babashka.fs         :as fs]
    [clojure.java.browse :as browse]
    [clojure.string      :as str]
    [clojure.tools.cli   :refer [parse-opts]]
    [org.httpkit.server  :as server]
    [hiccup2.core        :as html])
  (:import [java.net URLDecoder URLEncoder]))

(def cli-options
  [["-p"
    "--port PORT"
    "Port for HTTP server"
    :default
    8090
    :parse-fn
    #(Integer/parseInt %)]
   ["-d" "--dir DIR" "Directory to serve files from" :default "."]
   ["-h" "--help" "Print usage info"]])
(def parsed-args (parse-opts *command-line-args* cli-options))
(def opts (:options parsed-args))

(cond (:help opts)
        (do (println
              "Start a http server for static files in the given dir. Usage:\n"
              (:summary parsed-args))
            (System/exit 0))
      (:errors parsed-args) (do (println "Invalid arguments:\n"
                                         (str/join "\n" (:errors parsed-args)))
                                (System/exit 1))
      :else :continue)


(def port (:port opts))
(def dir (fs/path (:dir opts)))

(assert (fs/directory? dir) (str "The given dir `" dir "` is not a directory."))

(defn index
  [f]
  (let [files (map #(str (.relativize dir %)) (fs/list-dir f))]
    {:body (-> [:html
                [:head
                 [:meta {:charset "UTF-8"}]
                 [:title (str "Index of `" f "`")]]
                [:body
                 [:h1 "Index of " [:code (str f)]]
                 [:ul
                  (for [child files]
                    [:li
                     [:a {:href (URLEncoder/encode (str child))}
                      child
                      (when (fs/directory? (fs/path dir child)) "/")]])]
                 [:hr]
                 [:footer {:style {"text-align" "center"}}
                  "Served by http-server.bb"]]]
               html/html
               str)}))

(defn body [path] {:body (fs/file path)})

(server/run-server
  (fn [{:keys [:uri]}]
    (let [f (fs/path dir (str/replace-first (URLDecoder/decode uri) #"^/" ""))
          index-file (fs/path f "index.html")]
      (cond (and (fs/directory? f) (fs/readable? index-file)) (body index-file)
            (fs/directory? f) (index f)
            (fs/readable? f)  (body f)
            :else             {:status 404,
                               :body   (str "Not found `" f "` in " dir)})))
  {:port port})

(println "Starting http server at " port "for" (str dir))
#_(browse/browse-url (format "http://localhost:%s/" port))

@(promise)
