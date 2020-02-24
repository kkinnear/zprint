# How the second element of a pair is indented

Part of the reason for zprint's existence revolves around the
current approach to indenting used for `cond` clauses, `let` binding vectors,
and maps and other things with pairs (extend and reader conditionals).

Back in the day some of the key functions that include pairs, e.g.
`cond` and `let`, had their pairs nested in parentheses.  Clojure doesn't
follow this convention, which does create cleaner looking code in
the usual case, when the second part of the pair is short and fits
on the same line or when the second part of the pair can be represented
in a hang.  In those cases when the second part of the pair ends
up on the next line (as a flow), it can sometimes become a bit
tricky to separate the test and expr pair in a cond, or a destructured
binding-form from the init-expr, as they will start in the same
column.

While the cases where it is a bit confusing are rather rare, I
find them bothersome, so by default zprint will indent the
second part of these pairs by 2 columns (controlled by `{:pair {:indent 2}}`
for `cond` and `{:binding {:indent 2}}` for binding functions).

Maps also have pairs, and perhaps suffer from the potential
for confusion a bit more then binding-forms and cond functions.
By default then, the map indent for the value that is placed on the
next line (i.e., in a flow) is 2 (controlled by `{:map {:indent 2}}`).
The default is 2 for extend and reader-conditionals as well.

Is this perfect?  No, there are opportunities for confusion here
too, but it works considerably better for me, and it might for
you too. I find this particularly useful for `:binding` and `:map`
formatting.

Should you not like what this does to your code or your s-expressions,
the simple answer is to use `{:style :community}` as an options-map
when calling zprint (specify that in your `.zprintrc` file, perhaps).

You can change the indent from the default of 2 to 0 individually
in `:binding`, `:map`, or `:pair` if you want to tune it in more detail.

