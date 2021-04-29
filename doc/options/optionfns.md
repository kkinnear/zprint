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
