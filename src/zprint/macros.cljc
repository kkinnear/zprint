(ns zprint.macros (:require [clojure.string :as s]))

(defmacro dbg-pr
  "Output debugging print with pr."
  [options & rest]
  `(when (:dbg? ~options) (println (:dbg-indent ~options) (pr-str ~@rest))))

(defmacro dbg
  "Output debugging print with println."
  [options & rest]
  `(when (:dbg? ~options) (println (:dbg-indent ~options) ~@rest)))

(defmacro dbg-form
  "Output debugging print with println, and always return value."
  [options id form]
  `(let [value# ~form]
     (when (:dbg? ~options)
       (println (:dbg-indent ~options) ~id (pr-str value#)))
     value#))

(defmacro dbg-print
  "Output debugging print with println."
  [options & rest]
  `(when (or (:dbg? ~options) (:dbg-print? ~options))
     (println (:dbg-indent ~options) ~@rest)))
