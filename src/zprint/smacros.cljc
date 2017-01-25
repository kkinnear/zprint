(ns zprint.smacros)

;;
;; # Macros to help with using Clojure Spec
;;

(defmacro only-keys
  "Like keys, but checks that only the keys mentioned are allowed. 
  Credit to Alistair Roche, 9/25/16 post in Clojure google group."
  [& {:keys [req req-un opt opt-un], :as args}]
  `(s/merge (s/keys ~@(apply concat (vec args)))
            (s/map-of ~(set (concat req
                                    (map (comp keyword name) req-un)
                                    opt
                                    (map (comp keyword name) opt-un)))
                      (constantly true))))
