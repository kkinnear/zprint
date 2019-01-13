(ns ^:no-doc zprint.redef
  #?@(:clj [(:import (java.util.concurrent.locks ReentrantLock))]))

;;
;; # Function switch support
;;
;; ztype is an atom containing a vector with two elements:
;;
;; A keyword:
;;
;;   :zipper
;;   :structure
;;   :none
;;
;; An integer, representing the number of invocations of zprint
;; running at one time.
;;
;; All of this is to manage the zfns without using the binding
;; function, and it requires ^:dyanmic, which costs performance.
;;

#?(:clj (def ztype (atom [:none 0])))
#?(:clj (def ztype-lock (ReentrantLock.)))
#?(:clj (def zlock-enable (atom true)))

#?(:clj
     (defmacro zlocking
       "Emulates locking macro, but doesn't use synchronized block
       because that interacts badly with graalvm. Executes exprs
       in an implicit do, while locking x.  Will release the lock
       on x in all circumstances.  Only does locking if the global
       zlock-enable is true, because graalvm will compile with the
       following code, but will throw an exception since it is
       unable to find the unlock method."
       [^ReentrantLock x & body]
       `(let [lockee# ~x]
          (try (if @zlock-enable (. lockee# (lock)))
               ~@body
               (if @zlock-enable (. lockee# (unlock)))
               (catch Exception e#
                 (if @zlock-enable (. lockee# (unlock)))
                 (throw e#))))))
#?(:clj
     (defn remove-locking
       "Removes the locking on the ztype-lock because graalvm doesn't seem
       to be able to figure out how it works and operate correctly."
       []
       (reset! zlock-enable false)))

;#?(:clj (def ztype-history (atom [])))

#?(:clj (defn bind-vars
          "Change the root binding of all of the vars in the binding-map."
          [binding-map]
          (doseq [[the-var var-value] binding-map]
            (.bindRoot ^clojure.lang.Var the-var var-value))))

; Note that this is always and only called by do-redef-vars,
; a macro in macros.cljc
#?(:clj
     (defn redef-vars
       "Redefine all of the traversal functions for zippers or structures, 
       then call the function of no arguments passed in."
       [the-type binding-map body-fn]
       (zlocking
         ztype-lock
         (let [[current-type the-count :as current-ztype] @ztype]
           #_(swap! ztype-history conj [:in the-type current-type the-count])
           (if (= current-type the-type)
             ; We are already using the right functions, indicate
             ; that another zprint is using them.
             (reset! ztype [current-type (inc the-count)])
             ; We are not currently using the right functions, see
             ; if we can change to use them?
             (if (zero? the-count)
               ; Nobody else is using them, so we can change them.
               (do #_(swap! ztype-history conj
                       [:change current-type the-type the-count])
                   (bind-vars binding-map)
                   (reset! ztype [the-type 1]))
               ; Somebody else is using them, we cannot use them
               (throw
                 (Exception. (str "Attempted to run zprint with type: "
                                  the-type
                                  " when "
                                  the-count
                                  " invocations were already running with type "
                                  current-type
                                  " ! ")))))))
       ;
       ; There is a doall below because we must ensure that all of the
       ; calls to any of the redefed vars take place before we reduce the
       ; reference count of the number of users of those redefed vars.
       ; Otherwise the redefed vars could get reset to some other value,
       ; which would then not work well if you then evaluated the lazy
       ; sequence expecting the previous fn mappings to be in place.
       ;
       (try (doall (body-fn))
            (finally
              (zlocking
                ztype-lock
                (let [[current-type the-count] @ztype]
                  #_(swap! ztype-history conj
                      [:out the-type current-type the-count])
                  (if (= current-type the-type)
                    ; Note that we never put the original values back, as they
                    ; might be fine for the next call, saving us the trouble
                    ; of setting them again.  We do, of course, decrement the
                    ; count.
                    (reset! ztype [current-type (dec the-count)])
                    (throw (Exception.
                             (str "Internal Error: when attempting to reduce"
                                  " count of invocations using: " the-type
                                  ", the type was: " current-type))))))))))
