# How are constants defined when searching for "constant pairs"?

First, what are constant pairs?  The term "constant pair" refers
to a capability of zprint where, when formatting a list, it will
search backwards from end end of a list, looking at every second element
from the last (i.e., second from last, fourth from last, etc.) to see
if it is a "constant".  If it is, then all of the contiguous elements
that have constants in those positions are formatted as "pairs".

Some examples would be helpful.

First an example from code using Seesaw, a Clojure library for building
user interfaces using Java Swing:

```clojure
(zprint dialogf {:parse-string? true :list {:constant-pair? false}})

; This is what you get with normal formatting, without constant pairs

(dialog
  :parent
  top-frame
  :title
  "Loading and Saving Data"
  :content
  (mig-panel
    :items
    [["To load data from a file, press:"]
     [(action :name
              "Load"
              :handler
              (fn [x]
                (choose-file :type
                             :open
                             :success-fn
                             load-from-file
                             :dir
                             (clojure.java.io/file cwd)))) "wrap"]
     [same-box "wrap"]
     [(label :text
             "To save data as it is entered, press:"
             :id
             :save-label
             :class
             :save)]
     [(button :class
              :save
              :id
              :save-button
              :action
              (action :name
                      "Save"
                      :handler
                      (fn [x]
                        (choose-file :type
                                     :save
                                     :success-fn
                                     save-to-file
                                     :dir
                                     (clojure.java.io/file cwd))))) "wrap"]])
  :visible?
  false)

; This is what you get with the default zprint formatting, which recognizes
; constant pairs.  Quite a difference!

(zprint dialogf {:parse-string? true})

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
Constant pairing also makes Clojure specs much more legible:
```clojure
; First, without constant pairs, it looks like normal lisp formatting,
; which isn't great in this situation

(zprint spec {:parse-string? true :list {:constant-pair? false}}) 

(s/def ::line-seq
  (s/nilable (s/coll-of (s/or :number
                              number?
                              :range
                              (s/coll-of number? :kind sequential?))
                        :kind
                        sequential?)))

; Now, with constant pair recognition (the default),it looks much better:

(zprint spec {:parse-string? true})

(s/def ::line-seq
  (s/nilable (s/coll-of (s/or :number number?
                              :range (s/coll-of number? :kind sequential?))
                        :kind sequential?)))
```
Constant pairs are only recognized in lists.  There are two basic controls
for constant pairs:

  * `{:list {:constant-pair? true}}` will enable (or disable them) altogether.

  * `{:list {:constant-pair-min 4}}` is the minimum number of non-whitespace
elements required to exist before constant-pairs at the end of a list
are rendered differently.

You can also control what is considered a "constant" for constant pairing.

By default, a "constant" is keyword, string, number, or true or false.
You can define a `:constant-pair-fn` which, when given an element, will return
non-nil or nil to control whether or not that element is considered a
"constant".  

Solely for the purposes of illustration (i.e., this is not how the
default form of constant pairing is implemented), here is a `:constant-pair-fn`
which mimics the default behavior:

```clojure
{:list {:constant-pair-fn
          #(or (keyword %) (string %) (number? %) (= true %) (= false %))}}
```
You can define such a function yourself, in order to allow additional items to
be treated as pairs and be controlled by the constant pairing options in 
`:list`.  

The function takes one argument:

`{:list {:constant-pair-fn (fn [element] ...)}}`

See [this discussion](./optionfns.md) for more information
on user-defined functions and using `sci`.

Here is an example of where this might be useful, also note the use of
`:next-inner` to restrict the use of `:constant-pair-fn` to just the
top level of `m/app`:

```clojure

(def mapp6
"(m/app :get  (m/app middle1 middle2 middle3\n                    [route] handler\n\t\t    ; How do comments work?\n                    [route] \n        (handler this is \"a\" test \"this\" is \"only a\" test) \n\t\t    )\n       ; How do comments work here?\n       :post (m/app \n                    [route] handler\n                    [route] ; What about comments here?\n\t\t    handler))")

; Let's  see what happens if we just use the default configuration.
; The narrow width is to force constant pairing on the second handler
; of the :get

(czprint mapp6 {:parse-string? true :width 55})

(m/app :get (m/app middle1
                   middle2
                   middle3
                   [route]
                   handler
                   ; How do comments work?
                   [route]
                   (handler this
                            is
                            "a" test
                            "this" is
                            "only a" test))
       ; How do comments work here?
       :post (m/app [route]
                    handler
                    [route] ; What about comments here?
                    handler))

; This is ok, but it would be nice to pair the handlers up with the routes
; Since they fall at the end of the expressions, sounds like we could use
; constant-pairing to force the pair behavior.

; Let's see what we can do if we define our own function to determine
; what constant-pairing will consdier a "constant"

(zprint
  mapp6
  {:parse-string? true,
   :fn-map {"app" [:none
                   {:list {:constant-pair-min 1,
                           :constant-pair-fn #(or (vector? %) (keyword? %))},
                    :next-inner {:list {:constant-pair-fn nil,
                                        :constant-pair-min 4}}}]},
   :width 55})

(m/app :get (m/app middle1
                   middle2
                   middle3
                   [route] handler
                   ; How do comments work?
                   [route] (handler this
                                    is
                                    "a" test
                                    "this" is
                                    "only a" test))
       ; How do comments work here?
       :post (m/app [route] handler
                    [route] ; What about comments here?
                      handler))

; Much nicer.  Note that we had to define both keywords and vectors as
; "constants", to preserve the keyword constant-pairing.
; Note also the use of :next-inner to restore constant-pairing to its
; default behavior down inside of expressions contained in `m/app`.
```

## Specifying Functions in Option Maps

Typically, the user-defined function are specified in an options
map (often defining a new `:style`) which appears in a `.zprintrc`
file.  When `.zprintrc` files are read-in, they are read using
`sci`, the small Clojure interpreter.  Thus, any user-defined
functions are defined using a large subset of the available Clojure
function, specifically excepting any functions that can be used to
operation outside of the sandbox provided by the `sci` interpreter.

See [this discussion](./optionfns.md) for more information
on user-defined functions and using `sci`.
