# Classic zprint

One of the things I like the most about Clojure (and any Lisp) is
that the logical structure of a function has a visual representation
-- if the function is formatted (pretty printed) in a known way.

Classic zprint does precisely that, ignoring any existing white
space within functions.  It will fit the result as strictly as
possible within a specified margin, while using the vertical space
most efficiently, in part to maximize the amount of code that fits
into an editor window.

## Basic Source Formatting 
All these examples use a default width of 80 columns, which you can 
change permanently or on an individual use of zprint.

### Before
```clojure
(defn config-set-options!
  "Add some options to the current options, checking to make sure
  that they are correct. op-options are operational options that
  affect where to get options or how to do the processing, but do
  not affect the format of the output directly."
  ([new-options doc-str op-options]
   ; avoid infinite recursion, while still getting the doc-map updated
    (let [error-vec (when (and (not (:configured? (get-options))) (not (:configured? new-options)))
    (config-configure-all! op-options))] (when error-vec (throw (#?(:clj Exception.  :cljs js/Error.)
    (str "set-options! for " doc-str " found these errors: " error-vec)))) (internal-set-options! 
    doc-str (get-explained-all-options) (get-options) new-options))) ([new-options] (config-set-options! 
    new-options (str "repl or api call " (inc-explained-sequence)) (select-op-options new-options)))
    ([new-options doc-str] (config-set-options! new-options doc-str (select-op-options new-options))))
```
### Classic zprint
```clojure
(defn config-set-options!
  "Add some options to the current options, checking to make sure
  that they are correct. op-options are operational options that
  affect where to get options or how to do the processing, but do
  not affect the format of the output directly."
  ([new-options doc-str op-options]
   ; avoid infinite recursion, while still getting the doc-map updated
   (let [error-vec (when (and (not (:configured? (get-options)))
                              (not (:configured? new-options)))
                     (config-configure-all! op-options))]
     (when error-vec
       (throw
         (#?(:clj Exception.
             :cljs js/Error.)
          (str "set-options! for " doc-str " found these errors: " error-vec))))
     (internal-set-options! doc-str
                            (get-explained-all-options)
                            (get-options)
                            new-options)))
  ([new-options]
   (config-set-options! new-options
                        (str "repl or api call " (inc-explained-sequence))
                        (select-op-options new-options)))
  ([new-options doc-str]
   (config-set-options! new-options doc-str (select-op-options new-options))))
```
### Before
```clojure
(defn allow-one-line?  
  "Should we allow this function to print on a single line?"   
 
  [{:keys [fn-force-nl fn-gt2-force-nl fn-gt3-force-nl], 
    :as options}    
   len 
   fn-style] 
  (not (or (fn-force-nl fn-style) 
           (and (> len 3) (fn-gt2-force-nl fn-style)) 
     (and (> len 4) (fn-gt3-force-nl fn-style)) 
     (if-let [future-caller  
     (fn-style->caller fn-style)] 
       (let [caller-map (future-caller options)] (or (:flow? caller-map) 
                                                     (:force-nl? caller-map)))))
))  

```
### Classic zprint
Notice how destructuring in the argument list was handled, by default.
```clojure
(defn allow-one-line?
  "Should we allow this function to print on a single line?"
  [{:keys [fn-force-nl fn-gt2-force-nl fn-gt3-force-nl], :as options} len
   fn-style]
  (not (or (fn-force-nl fn-style)
           (and (> len 3) (fn-gt2-force-nl fn-style))
           (and (> len 4) (fn-gt3-force-nl fn-style))
           (if-let [future-caller (fn-style->caller fn-style)]
             (let [caller-map (future-caller options)]
               (or (:flow? caller-map) (:force-nl? caller-map)))))))

```
## Formatting Spec Information
Specs are formatted by default very much as people format them by hand:
### Before
```clojure
(s/def ::line-seq 
       (s/nilable 
         (s/coll-of 
           (s/or :number 
                 number?
                 :range 
                 (s/coll-of number? :kind sequential?))
           :kind sequential?)))
```
### Classic zprint
Notice how the keys `:number`, `:range`, and `:kind` are paired up, 
automatically.  
```clojure
(s/def ::line-seq
  (s/nilable (s/coll-of (s/or :number number?
                              :range (s/coll-of number? :kind sequential?))
                        :kind sequential?)))
```
## Keyword Arguments
The Seesaw Clojure library makes extensive use of keyword arguments. 
### Before
This is intentionally messed up, otherwise it is hard to appreciate the 
formatting performed.
```clojure
(dialog 
  :parent top-frame :title "Loading and Saving Data"
  :content (mig-panel :items
    [["To load data from a file, press:"]
    [(action :name "Load" :handler 
     (fn [x] (choose-file :type :open :success-fn load-from-file
     :dir (clojure.java.io/file cwd))))
     "wrap"] [same-box "wrap"]
     [(label :text "To save data as it is entered, press:"
     :id :save-label :class :save)]
    [(button :class :save :id :save-button
    :action (action :name "Save" :handler (fn [x]
    (choose-file :type :save :success-fn save-to-file
    :dir (clojure.java.io/file cwd))))) "wrap"]]) :visible? false)
```

### Classic Zprint
```clojure
(dialog :parent top-frame
        :title "Loading and Saving Data"
        :content
          (mig-panel
            :items
            [["To load data from a file, press:"]
             [(action :name "Load"
                      :handler (fn [x]
                                 (choose-file :type :open
                                              :success-fn load-from-file
                                              :dir (clojure.java.io/file cwd))))
              "wrap"] [same-box "wrap"]
             [(label :text "To save data as it is entered, press:"
                     :id :save-label
                     :class :save)]
             [(button :class :save
                      :id :save-button
                      :action (action :name "Save"
                                      :handler (fn [x]
                                                 (choose-file
                                                   :type :save
                                                   :success-fn save-to-file
                                                   :dir (clojure.java.io/file
                                                          cwd))))) "wrap"]])
        :visible? false)
```
## Justified Pairs
If you use `{:style :justified}` zprint will justify pairs in `let` bindings, 
maps, and other places where pairs appear -- but only if it doesn't make it 
take a lot more vertical space or look terrible.  Not necessarily great for 
every situation, but can make certain functions look much better.
### Classic zprint
```clojure
(defn scan-up-dir-tree
  "Take a vector of filenames and scan up the directory tree from 
  the current directory to the root, looking for any of the files 
  in each directory."
  [filename-vec file-sep]
  (let [regex-file-sep (if (= file-sep "\\") "\\\\" file-sep)
        file-sep-pattern (re-pattern regex-file-sep)
        cwd (java.io.File. ".")
        path-to-root (.getCanonicalPath cwd)
        dirs-to-root (clojure.string/split path-to-root file-sep-pattern)]
    (reduce (partial get-config-from-dirs filename-vec file-sep)
      ["."]
      dirs-to-root)))
```
### Classic zprint: `{:style :justified}`
```clojure
(defn scan-up-dir-tree
  "Take a vector of filenames and scan up the directory tree from 
  the current directory to the root, looking for any of the files 
  in each directory."
  [filename-vec file-sep]
  (let [regex-file-sep   (if (= file-sep "\\") "\\\\" file-sep)
        file-sep-pattern (re-pattern regex-file-sep)
        cwd              (java.io.File. ".")
        path-to-root     (.getCanonicalPath cwd)
        dirs-to-root     (clojure.string/split path-to-root file-sep-pattern)]
    (reduce (partial get-config-from-dirs filename-vec file-sep)
      ["."]
      -to-root)))
```
## Hiccup Vectors
### Before
```clojure
(defn header
  [{:keys [title icon description]}]
  [:header.container
   [:div.cols {:class "gap top", :on-click (fn [] (println "tsers"))}
    [:div {:class "shrink"} icon]
    [:div.rows {:class "gap-xs"}
     [:dov.cols {:class "middle between"} [:div title]
      [:div {:class "shrink"} [:button "x"]]] [:div description]]]])
```
### Classic zprint `{:style :hiccup}`
```clojure
(defn header
  [{:keys [title icon description]}]
  [:header.container
   [:div.cols {:class "gap top", :on-click (fn [] (println "tsers"))}
    [:div {:class "shrink"}
     icon]
    [:div.rows {:class "gap-xs"}
     [:dov.cols {:class "middle between"}
      [:div title]
      [:div {:class "shrink"}
       [:button "x"]]]
     [:div description]]]])
```

## 'Better Cond'
Some people like the "better cond", which allows binding vectors inside the `cond`.
### Before
```clojure
(b/cond 
  (odd? a) 1 
  :let [a (quot a 2)]
  :when-let [x (fn-which-may-return-nil a) y (fn-which-may-return-nil (* 2 a))]
  ; bails early with nil unless x and y are both truthy
  (odd? (+ x y)) 2
  3)
```
### Classic Zprint
Note that the binding vector on the `when-let` is recognized!
```clojure
(b/cond (odd? a) 1
        :let [a (quot a 2)]
        :when-let [x (fn-which-may-return-nil a)
                   y (fn-which-may-return-nil (* 2 a))]
        ; bails early with nil unless x and y are both truthy
        (odd? (+ x y)) 2
        3)
```


