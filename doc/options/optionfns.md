# Specifying User Defined Functions in Option Maps

There are several places in the options map where user defined
functions can be used to alter the formatting based on the content
of the element to be formatted:

```
{:list {:constant-pair-fn (fn [element] ...)}}
{:list {:option-fn (fn [options element-count non-comment-non-whitespace-element-seq] ...)}}
{:vector-fn {:constant-pair-fn (fn [element] ...)}}
{:vector {:option-fn-first (fn [options first-non-comment-non-whitespace--element] ...)}}
{:vector {:option-fn (fn [options element-count non-comment-non-whitespace-element-seq] ...)}}
```

## Calling Sequence

The required arity for `option-fn` functions is 
`[options element-count sexpr]`.  However, you should also specify a 
zero-argument arity for all `option-fn`s that returns the function's 
descriptive name.  For example:

```clojure
(fn ([options len sexpr] ... code that returns a options map)
    ([] "mytestoptionsfunction"))
```

This name will be used in error messages, and when something goes wrong, 
you will be glad you did this.

## Returning an Options Map

The function of every `option-fn` is to return a new options map which will
be merged into the then current options map, and used for formatting the
current collection (vector or list), and all enclosed collections.  This
options map can also contain a key `:next-inner`, the value of which is
an options map which will be merged into the current options map prior
to formatting any enclosed elements or collections.  

If the `option-fn` was configured from the `:fn-map`, for example:
```clojure
{:fn-map {"myfn" [:none {:list {:option-fn myoptionfn}}]}}
```
then presumably the option map returned by this `option-fn` is designed
to adjust the formatting of `myfn`.  If this formatting adjustment
is supposed to apply to every list contained inside of `myfn`, then
that's fine.  Frequently, however, the formatting change is designed to
affect only the "top level" of the formatting for `myfn`, and the lists
enclosed by `myfn` are supposed to be formatted as before.  In this
situation, `myoptionfn` can return an options map which has the
formatting changes specified for the top level of `myfn`, and can
revert those to the previous values for the inner lists.  It can
(and should) also remove the `option-fn` from the `:list` configuration.

An options map returned by the `option-fn` might look like this:

```
{:list {:hang? false :option-fn nil} :next-inner {:list {:hang? true}}}
```
An equivalent options map would be:

```
{:list {:hang? false} :next-inner {:list {:hang? true :option-fn nil}}}
```
The second might be a bit more understandable, but they are equivalent.
Once you are in an `option-fn`, you can remove it from the configuration
without affecting anything since you are already in the `option-fn`.

This is the basic approach for `:next-inner`, but of course the problem
with this approach is that when "resetting" the things that have been
changed, we aren't really resetting them to what they were before.  We
are changing them to what we imagine they were before, but we don't really
have know if some overall configuration has changed them before we changed
them again.

While you could examine the options map in an `:option-fn`, and create
a `:next-inner` map to restore things to how they try were before the
`:option-fn` ran, there is an easier way. 

### :next-inner-restore

You can use the map key `:next-inner-restore` to restore the values of
specific key sequences.  The `:next-inner-restore` processing will determine
the values of these key sequences prior to changing them, and create a
`:next-inner` map for you which will restore them to the values they had
prior to being changed. 

`:next-inner-restore` takes a vector of vectors.  Each contained vector can
be in one of two formats:

  1. A vector of map keys (i.e, a "key sequence").  In this case, the
  current value of that key sequence in the options map is placed in
  the contructed `:next-inner` map, to restore that value in when
  processing more deeply nested expressions.

  2. A vector containing two things, where the first is a vector of map
  keys (also a "key sequence"), and the second is a single element.  In
  this case, the first element of the vector, the key sequence, is the
  location of a "set" in the options map.  The second element of the 
  vector is the element in the set specified by the key sequence to restore.
  In this case, if the element appears in the set, then the contructed
  `:next-inner` map will ensure that it also appears in the correct set.  If
  that element does not appear in the specified set, then the contructed 
  `:next-inner` map will ensure that the element does not appear in that
  set.

Let's demonstrate the use of these two approaches, not using an `:option-fn`,
since that can get pretty complex, but just with basic configuration.

Here is a function definition, formatted with classical zprint:

```
(czprint prepost2 {:parse-string? true})

(defn selected-protocol-for-indications
  {:pre [(map? m) (empty? m)], :post [(not-empty %)]}
  [{:keys [spec]} procedure-id indications]
  (->> {:procedure-id procedure-id, :pre preceding, :indications indications}
       (sql/op spec queries :selected-protocol-for-indications)
       (map :protocol-id)))
```

The desire is to have the map with `:pre` and `:post` format with each pair
on a different line, and for ensure that `:pre` comes first, before `:post`.
We also want to gobally sort the keys of maps that appear in code (which will
make `:post` come before `:pre` if we don't do something about that). Finally,
we want to have the maps inside of the expressions of the `defn` to be
formatted without regard to `:pre`, and on one line if they will fit on one
line.  While this may seem contrived, this was an actual request from a
zprint user.

Here is how to format the map containing `:pre`, but the problem with this
is that the formatting bleads over into all of the expressions, causing
the map contained in the `->>` to format incorrectly: it formats on multiple
lines and it it isn't sorted.

```
(czprint prepost2
         {:parse-string? true,
          :map {:sort-in-code? true},
          :fn-map {"defn" [:arg1-force-nl-body
                           {:map {:force-nl? true, :key-no-sort #{":pre"}}}]}})

(defn selected-protocol-for-indications
  {:pre [(map? m) (empty? m)],
   :post [(not-empty %)]}
  [{:keys [spec]} procedure-id indications]
  (->> {:procedure-id procedure-id,
        :pre preceding,
        :indications indications}
       (sql/op spec queries :selected-protocol-for-indications)
       (map :protocol-id)))
```

Let's (try to) restore things so that the contained expressions are handled
correctly.

```
(czprint
  prepost2
  {:parse-string? true,
   :map {:sort-in-code? true},
   :fn-map {"defn" [:arg1-force-nl-body
                    {:map {:force-nl? true, :key-no-sort #{":pre"}},
                     :next-inner-restore [[:map :force-nl?]
                                          [[:map :key-no-sort] ":pre"]]}]}})

(defn selected-protocol-for-indications
  {:post [(not-empty %)], :pre [(map? m) (empty? m)]}
  [{:keys [spec]} procedure-id indications]
  (->> {:indications indications, :pre preceding, :procedure-id procedure-id}
       (sql/op spec queries :selected-protocol-for-indications)
       (map :protocol-id)))
```
Wait, that broke the formatting for the map we had working, and
seems to have ignored all of our configuration we put in the `:fn-map`
for `defn`.  It turns out that the expression we wanted to format
differently inside of the `defn` was itself a map.  We want to alter
the format of the interior of a top level expression of the `defn`.
Thus, we want our formatting to affect the "next inner" expression
from the `defn` itself.  So if we restore the changes we made when
we enter the next-inner expression, we lose the changes when we get
to actually formatting the map we want to change.

We can work with that, now that we know what is going on.

```
(czprint
  prepost2
  {:parse-string? true,
   :map {:sort-in-code? true},
   :fn-map {"defn" [:arg1-force-nl-body
                    {:next-inner
                       {:map {:force-nl? true, :key-no-sort #{":pre"}},
                        :next-inner-restore [[:map :force-nl?]
                                             [[:map :key-no-sort] ":pre"]]}}]}})

(defn selected-protocol-for-indications
  {:pre [(map? m) (empty? m)],
   :post [(not-empty %)]}
  [{:keys [spec]} procedure-id indications]
  (->> {:indications indications, :pre preceding, :procedure-id procedure-id}
       (sql/op spec queries :selected-protocol-for-indications)
       (map :protocol-id)))
```
That's got it right now.  We format the first "next inner" level differently
and then we restore things so that deeper levels format as they did before.
The map with `:pre` and `:post` is correct, and the internal map inside
of the `->>` is also correct.

This example is complex because we are aren't formatting the actual `defn` 
expression differently, we are formatting the top level expresions of the
`defn` differently.  The advantage of doing it this way is that this 
approach to configuration will integrate well with other configurations.


Now you have seen how to use `:next-inner` and `:next-inner-restore` to
achieve a desired formatting output.


## Specifying an Option Function

If you are using zprint as a library or at the REPL, you can just
specify the functions to be used with the `(fn [x] ...)` or `#(...
% ...)` approach.

If, however, you are configuring one of these functions in a
`.zprintrc` file, there are some potential problems.

Foremost among these is security -- if you can specify a function
in an external file, and then that function can be executed when
someone runs zprint, we have a huge security hole.

Additionally, some environments (e.g., the graalVM binaries) don't
accept new function definitions once they are compiled.

The solution to both of these issues is to use the sandboxed Clojure
interpreter, `sci` to define and execute these functions.
This allows zprint to accept function definitions
in any available `.zprintrc` file, as well as options maps loaded
using the `--url` or `--url-only` switches or from the command line.
Any function defined in an options map cannot reference the file
system or do anything else that outside of the `sci` sandbox in
which it is operating.

When defining in-line functions in an options map, `sci` will support
either the `(fn [x] ...)` form of function definition, or the `#(...)`
form of function definition.

## Functions available in `sci`

The functions available in `sci`, and therefore the functions you can
use in a function declared in an options map are as listed below, 
indexed by the namespace in which they appear.  The namespace
`:macro` is used for the special forms interpreted by `sci`.
```clojure
{:macros #{. and case comment declare def defmacro defn do doseq
           expand-constructor expand-dot* fn fn* for if import in-ns lazy-seq
           let loop new ns or resolve set! try var},
 clojure.core
   #{* *' *1 *2 *3 *e *err* *file* *in* *ns* *out* *print-length* *print-level*
     *print-meta* *print-namespace-maps* + +' - -' -> ->> -new-dynamic-var
     -new-var -reified-methods .. / < <= = == > >= add-watch aget alength alias
     all-ns alter-meta! alter-var-root ancestors any? apply array-map as-> aset
     assert assoc assoc! assoc-in associative? atom bean bigdec bigint
     biginteger binding binding-conveyor-fn bit-and bit-and-not bit-flip bit-not
     bit-or bit-set bit-shift-left bit-shift-right bit-test bit-xor boolean
     boolean-array boolean? booleans bound? bounded-count butlast byte
     byte-array bytes bytes? cat char char-array char-escape-string
     char-name-string char? chars chunk chunk-append chunk-buffer chunk-cons
     chunk-first chunk-next chunk-rest chunked-seq? class class? coll? comment
     comp comparator compare compare-and-set! complement completing concat cond
     cond-> cond->> condp conj conj! cons constantly contains? count counted?
     cycle dec dec' decimal? dedupe defmethod defmulti defn- defonce defprotocol
     defrecord delay deliver denominator deref derive descendants disj dissoc
     distinct distinct? doall dorun dotimes doto double double-array double?
     doubles drop drop-last drop-while eduction empty empty? ensure-reduced
     enumeration-seq eval even? every-pred every? ex-cause ex-data ex-info
     ex-message extend extend-protocol extend-type extends? false? ffirst filter
     filterv find find-ns find-var first flatten float float-array float? floats
     flush fn? fnext fnil format frequencies gensym get get-in get-method
     get-thread-binding-frame-impl get-thread-bindings group-by has-root-impl
     hash hash-map hash-set hash-unordered-coll ident? identical? identity
     if-let if-not if-some ifn? inc inc' indexed? inst? instance? int int-array
     int? integer? interleave intern interpose into into-array ints isa? iterate
     iterator-seq juxt keep keep-indexed key keys keyword keyword? last lazy-cat
     letfn line-seq list list* list? load-string long long-array longs
     macroexpand macroexpand-1 make-array make-hierarchy map map-entry?
     map-indexed map? mapcat mapv max max-key memoize merge merge-with meta
     methods min min-key mod multi-fn-add-method-impl multi-fn-impl
     multi-fn?-impl munge name namespace namespace-munge nat-int? neg-int? neg?
     newline next nfirst nil? nnext not not-any? not-empty not-every? not=
     ns-aliases ns-imports ns-interns ns-map ns-name ns-publics ns-refers
     ns-resolve ns-unmap nth nthnext nthrest num number? numerator object-array
     odd? parents partial partition partition-all partition-by peek persistent!
     pop pop-thread-bindings pos-int? pos? pr pr-str prefer-method prefers print
     print-dup print-method print-str printf println prn prn-str promise
     protocol-type-impl push-thread-bindings qualified-ident? qualified-keyword?
     qualified-symbol? quot rand rand-int rand-nth random-sample range ratio?
     rational? rationalize re-find re-groups re-matcher re-matches re-pattern
     re-seq read read-line read-string realized? record? reduce reduce-kv
     reduced reduced? reductions refer reify reify* rem remove
     remove-all-methods remove-method remove-ns remove-watch repeat repeatedly
     replace replicate require requiring-resolve reset! reset-meta!
     reset-thread-binding-frame-impl reset-vals! resolve rest reverse
     reversible? rseq rsubseq run! satisfies? second select-keys seq seq?
     seqable? seque sequence sequential? set set? short short-array shorts
     shuffle simple-ident? simple-keyword? simple-symbol? some some-> some->>
     some-fn some? sort sort-by sorted-map sorted-map-by sorted-set
     sorted-set-by sorted? special-symbol? split-at split-with str string? subs
     subseq subvec supers swap! swap-vals! symbol symbol? tagged-literal
     tagged-literal? take take-last take-nth take-while the-ns to-array
     trampoline transduce transient tree-seq true? type unchecked-add
     unchecked-add-int unchecked-byte unchecked-char unchecked-dec-int
     unchecked-divide-int unchecked-double unchecked-float unchecked-inc
     unchecked-inc-int unchecked-int unchecked-long unchecked-multiply
     unchecked-multiply-int unchecked-negate unchecked-negate-int
     unchecked-remainder-int unchecked-short unchecked-subtract
     unchecked-subtract-int underive unquote unreduced unsigned-bit-shift-right
     update update-in uri? use uuid? val vals var-get var-set var? vary-meta vec
     vector vector? volatile! vreset! vswap! when when-first when-let when-not
     when-some while with-bindings with-in-str with-local-vars with-meta
     with-open with-out-str with-redefs with-redefs-fn xml-seq zero? zipmap},
 clojure.edn #{read read-string},
 clojure.lang #{IAtom IAtom2 IDeref compareAndSet deref reset resetVals swap
                swapVals},
 clojure.repl #{apropos demunge dir dir-fn doc find-doc print-doc pst source
                source-fn stack-element-str},
 clojure.set #{difference index intersection join map-invert project rename
               rename-keys select subset? superset? union},
 clojure.string #{blank? capitalize ends-with? escape includes? index-of join
                  last-index-of lower-case re-quote-replacement replace
                  replace-first reverse split split-lines starts-with? trim
                  trim-newline triml trimr upper-case},
 clojure.template #{apply-template do-template},
 clojure.walk #{keywordize-keys macroexpand-all postwalk postwalk-demo
                postwalk-replace prewalk prewalk-demo prewalk-replace
                stringify-keys walk}}
```
If you use additional functions not in the list above, zprint will not 
accept the `.zprintrc` file call to change the current options map.

Note that `sci` is used only when reading options maps from `.zprintrc`
files.  It is not used when the options map is changed by using the
`set-options!` call when using zprint as a library or at the REPL.
