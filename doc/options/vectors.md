# Format Vectors Based on their Content

It is possible to specify an executable function to examine
the content of a vector and format it differently based on its
content.

One of the executable functions, `:option-fn-first` makes the
first element of every vector available to a user-defined function,
and that user-defined function is responsible for returning any
desired changes to the options map to be used while formatting
only this specific vector.  The actual calling sequence is:
```clojure
(fn [current-options-map first-non-whitespace-non-comment-element] ...).
```

Conceptually, this is pretty simple, as it allows you to essentially 
hand-format a vector where the first data element is a keyword.

One of the currently supported styles uses this capability.
Here is the definition of `:keyword-respect-nl`:

```clojure
{:style-map {:keyword-respect-nl
               {:vector {:option-fn-first
                           #(let [k? (keyword? %2)]
                              (when (not= k? (:respect-nl? (:vector %1)))
                                {:vector {:respect-nl? k?}}))}}}}
```

This will ensure that all newlines in a vector that starts with a keyword
are preserved.

A simple example:

First, classic zprint:

```clojure
[:html {} [:head {} [:title {} "Example Web Page"]]
 [:body {}
  [:p {}
   "You have reached this web page by typing \"example.com\",\n\"example.net\",\n  or \"example.org\" into your web browser."]
  [:p {}
   "These domain names are reserved for use in documentation and are not available \n  for registration. See "
   [:a {:href "http://www.rfc-editor.org/rfc/rfc2606.txt", :shape "rect"}
    "RFC \n  2606"] ", Section 3."]]]
```

Next, classic zprint with `{:style :keyword-respect-nl}`:
```clojure
[:html {}
 [:head {}
  [:title {} "Example Web Page"]]
 [:body {}
  [:p {}
   "You have reached this web page by typing \"example.com\",\n\"example.net\",\n  or \"example.org\" into your web browser."]
  [:p {}
   "These domain names are reserved for use in documentation and are not available \n  for registration. See "
   [:a {:href "http://www.rfc-editor.org/rfc/rfc2606.txt", :shape "rect"}
    "RFC \n  2606"]
   ", Section 3."]]]
```
Potentially useful, if not dramatically different.

The other executable function, `:option-fn`, makes the entire
vector available to the user-defined function where, again,
the user-defined function is responsible for returning an options
map (or nil) which will be used when formatting the remained of this
vector.  There are actually three arguments to an `:option-fn`
function: the current options map, the number of non-comment
non-whitespace elements of the function, and a sequence (not
necessarily a vector) containing those elements.  The `:option-fn`
returns either an options map or nil.  Every returned options-map
is validated with spec before being used, so don't return the
current options map if you don't change it, return nil!

The calling arguments are:

```clojure
(fn [options element-count non-whitespace-non-comment-element-seq] ... )
```

See the [reference](../reference.md#option-fn-nil) for `:option-fn`. 

Here is an example of `:option-fn`, which is used in the
`:style :hiccup`.  First, the definition of the style:

```clojure
{:style-map {:hiccup
               {:vector
                  {:option-fn
                     (fn [opts n exprs]
                       (let [hiccup? (and (>= n 2)
                                          (or (keyword? (first exprs))
                                              (symbol? (first exprs)))
                                          (map? (second exprs)))]
                         (cond (and hiccup? (not (:fn-format (:vector opts))))
                                 {:vector {:fn-format :arg1-force-nl}}
                               (and (not hiccup?) (:fn-format (:vector opts)))
                                 {:vector {:fn-format nil}}
                               :else nil))),
                   :wrap? false},
                :vector-fn {:indent 1, :indent-arg 1}}}}
```
This is a complex style, which uses `:option-fn` to determine that
the vector contains hiccup information, and then uses the 
[`:vector :fn-format` capability](../reference#fn-format-nil) 
to force the formatting of the vector to use the `:arg1-force-nl`
fn-type used for lists starting with `defprotocol` or `fdef`.

Here is the output, first without the style:

```clojure
(defn subscribe
  []
  [:div {:class "well"}
   [:form {:novalidate "", :role "form"}
    [:div {:class "form-group"} (label {:class "control-label"} "email" "Email")
     (email-field
       {:class "form-control", :placeholder "Email", :ng-model "user.email"}
       "user.email")]
    [:div {:class "form-group"}
     (label {:class "control-label"} "password" "Password")
     (password-field {:class "form-control",
                      :placeholder "Password",
                      :ng-model "user.password"}
                     "user.password")]
    [:div {:class "form-group"}
     (label {:class "control-label"} "gender" "Gender")
     (reduce conj
       [:div {:class "btn-group"}]
       (map labeled-radio ["male" "female" "other"]))]
    [:div {:class "form-group"}
     [:label (check-box {:ng-model "user.remember"} "user.remember-me")
      " Remember me"]]] [:pre "form = {{ user | json }}"]])
```

Then with `{:style :hiccup}`:
```clojure
(defn subscribe
  []
  [:div {:class "well"}
   [:form {:novalidate "", :role "form"}
    [:div {:class "form-group"}
     (label {:class "control-label"} "email" "Email")
     (email-field
       {:class "form-control", :placeholder "Email", :ng-model "user.email"}
       "user.email")]
    [:div {:class "form-group"}
     (label {:class "control-label"} "password" "Password")
     (password-field {:class "form-control",
                      :placeholder "Password",
                      :ng-model "user.password"}
                     "user.password")]
    [:div {:class "form-group"}
     (label {:class "control-label"} "gender" "Gender")
     (reduce conj
       [:div {:class "btn-group"}]
       (map labeled-radio ["male" "female" "other"]))]
    [:div {:class "form-group"}
     [:label
      (check-box {:ng-model "user.remember"} "user.remember-me")
      " Remember me"]]]
   [:pre "form = {{ user | json }}"]])
```

They aren't wildly different, but look at the last line, and the second
`:div`.  In a more complex or longer data set these types of changes would
be of considerable value.

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
