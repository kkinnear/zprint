# Change the indentation of lists

The default options map for lists is as follows:
```clojure
 :list {:constant-pair-min 4,
        :constant-pair? true,
        :hang-avoid 0.5,
        :hang-diff 1,
        :hang-expand 2.0,
        :hang-size 100,
        :hang? true,
        :indent 2,
        :indent-arg nil,
        :indent-only-style :input-hang,
        :indent-only? false,
        :pair-hang? true,
        :replacement-string nil,
        :respect-nl? false},
```
Indentation for lists is primarily configured by `:indent`, though 
`:indent-arg` will come into play if it is non-nil.

### What is a hang and how does it differ from a flow?

zprint has a name for these two different approaches to formatting:

  * Hang

```clojure
(this is
      a
      hang)
```

  * Flow

```clojure
(this
  is
  a
  flow)
```
### When does zprint do a hang or a flow?

There are a number of complex heuristics about when zprint will do a hang
or a flow, depending in many cases on how many lines it will take (less is
better), and how it will "look".  To simplify this situation, the basic rules
are:

zprint will hang when:

  * The entire list will not fit in the remaining space on the current line
  * The first element of a list could be a function name
  * The hang approach will not take more lines than the flow approach
  * Some other complex calculations on how it will "look"

Clearly, when zprint is doing a hang, the configured `:indent` is meaningless,
since the indent for the subsequent lines is based on the size of the first
element of the list.

### When does zprint use `:indent`?

zprint will use the `:indent` for a list when it is doing a flow, which is
when:

  * The entire list will not fit in the remaining space on the current line
  * The first element of a list could be a function name
  * A flow is considered "better" than a hang

In this case, zprint will place a number of spaces controlled by `:indent`
in front of the second and subsequent elements of the list.

Note that if zprint doesn't think that the first element can be a function
name (for example, it is a number), then zprint will not use `:indent` for
subsequent elements, it will use a single space.  For example:

```clojure
(555
 aaaa
 bbbb
 cccc)
```
### Different indents for body functions and argument functions?

There is a full discussion of this in [community standards](./community.md).

## How to change indents for lists?

You can place an options map which contains `{:list {:indent n}}` or 
`{:list {:indent-arg n}}`
[anywhere an options map is accepted](../altering.md#2-get-the-options-map-recognized-by-zprint-when-formatting).


