(ns zprint.style-sample-test
  (:require [expectations :refer :all]
            [zprint.core :refer :all]
            [zprint.zprint :refer :all]
            [zprint.config :refer :all]
            [zprint.finish :refer :all]
            [clojure.repl :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [rewrite-clj.parser :as p :only [parse-string parse-string-all]]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z :only [edn*]]))

(set-options! {:configured? false})


(def styles
  (-> default-zprint-options :style-map keys))

(def original :original)

(defn style->filename [style]
  (format "style-sample/%s.clj" (name style)))

(defn dump-all []
  (let [origin-filename (style->filename original)
        origin-content (slurp origin-filename)]
    (doseq [style styles]
      (->> (zprint-file-str origin-content  "test" {:style style})
           (spit (style->filename style))))))

(comment (dump-all))

(let [origin-filename (style->filename original)
      origin-content (slurp origin-filename)]
  (doseq [style styles]
    (expect (zprint-file-str origin-content  "test" {:style style})
            (slurp (style->filename style)))))
