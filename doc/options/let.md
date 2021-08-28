# Change how `let` forms are formatted

There are a number of Clojure functions and macros which take a 
"binding vector" as their first argument.  These functions all share 
the function type of `:binding`.  See the end of this section for
a list of all of the functions and macros with a function type
of `:binding`.

The `let` macro is the one most frequently discussed in the 
issues for zprint, doubtless because many people have very impassioned
ideas about how they want to see their `let` forms formatted.
zprint has a lot of flexibility about how it will format `let` forms,
and this section works to demonstrate some of the most common options
used to format them.

In thinking about `let` forms, there are two major types: `let` forms
that fit on one line, and those that do not.  We will deal with them
separately here.

By default, a `let` form that fits on the rest of the line from where
it begins will print on that single line.  If you are happy with that
behavior, then skip the next section and go to the section on
"Multi-line `let` forms".

## Single line `let` forms

As mentioned above, a `let` form which will fit on the same line as
it started on will be formatted on that single line.  For instance:

```
(czprint i201a {:parse-string? true})
(let [a b] (g h (i j)))
```

If you would like every `let` form to format on multiple lines, you can
use:

```
(czprint i201a {:parse-string? true :fn-force-nl #{:binding}})
(let [a b]
  (g h (i j)))
```

If you would like every `let` form with more than a single binding to
format on multiple lines, you can use:

```
(czprint i201 {:parse-string? true})
(let [a b c d] (g h (i j)))

(czprint i201 {:parse-string? true :fn-gt2-force-nl #{:binding-vector}})
(let [a b
      c d]
  (g h (i j)))

(czprint i201a {:parse-string? true})
(let [a b] (g h (i j)))

(czprint i201a {:parse-string? true :fn-gt2-force-nl #{:binding-vector}})
(let [a b] (g h (i j)))
```

Thus, you can have short `let` forms format pretty much the way you want.

## Multi-line `let` forms

Multi-line `let` forms are `let` forms that don't fit on the remainder of
the line on which they begin.  The issue here is whether and where to
place blank lines, and how to indent a binding expression that formats
on a line by itself.

A multi-line `let` form will attempt to place each binding pair on the
same line, and use a different line for each pair.   

```
(czprint i201b {:parse-string? true})
(let [foo (bar baz)
      stuff (and bother (or maybe (not bother)))
      here (we go gathering (nuts in may))]
  (this is (a test (this is (only a test)))))
```

In the examples
below, a narrow width has been used to force the binding expressions to
not format on the same line as the local symbol, to illustrate differences
in formatting.

```
(czprint i201b {:parse-string? true :width 28})
(let [foo (bar baz)
      stuff
        (and bother
             (or maybe
                 (not
                   bother)))
      here (we go
               gathering
               (nuts in
                     may))]
  (this
    is
    (a test
       (this is
             (only a
                   test)))))
```

One approach is to have a blank line after ever binding expression which
doesn't fit on the same line as the local symbol:

```
(czprint i201b {:parse-string? true :width 28 :binding {:nl-separator? true}})
(let [foo (bar baz)
      stuff
        (and bother
             (or maybe
                 (not
                   bother)))

      here (we go
               gathering
               (nuts in
                     may))]
  (this
    is
    (a test
       (this is
             (only a
                   test)))))

```

Note that the binding expression for `stuff` is indented be default.  You can 
change that:

```
(czprint i201b {:parse-string? true :width 28 :binding {:nl-separator? true :indent 0}})
(let [foo (bar baz)
      stuff
      (and bother
           (or maybe
               (not
                 bother)))

      here (we go
               gathering
               (nuts in
                     may))]
  (this
    is
    (a test
       (this is
             (only a
                   test)))))
```

This is available as a "style":

```
(czprint i201b {:parse-string? true :width 28 :style :binding-nl})
(let [foo (bar baz)
      stuff
      (and bother
           (or maybe
               (not
                 bother)))

      here (we go
               gathering
               (nuts in
                     may))]
  (this
    is
    (a test
       (this is
             (only a
                   test)))))
```

You might prefer to have a blank line after every binding pair, regardless
of whether or not the binding expression didn't fit on the same line as
the local symbol:

```
(czprint i201b {:parse-string? true :width 28 :binding {:nl-separator-all? true}})
(let [foo (bar baz)

      stuff
        (and bother
             (or maybe
                 (not
                   bother)))

      here (we go
               gathering
               (nuts in
                     may))]
  (this
    is
    (a test
       (this is
             (only a
                   test)))))
```

Again, without the indent is also a style:

```
(czprint i201b {:parse-string? true :width 28 :style :binding-nl-all})
(let [foo (bar baz)

      stuff
      (and bother
           (or maybe
               (not
                 bother)))

      here (we go
               gathering
               (nuts in
                     may))]
  (this
    is
    (a test
       (this is
             (only a
                   test)))))
```

The `:nl-separator-all?` key doesn't depend on whether or not the 
binding expression formats on the same line as the local symbol, so it
works all the time:

```
(czprint i201b {:parse-string? true :width 80 :style :binding-nl-all})
(let [foo (bar baz)

      stuff (and bother (or maybe (not bother)))

      here (we go gathering (nuts in may))]
  (this is (a test (this is (only a test)))))
```

All of these approaches with lines between the binding pairs also works
when the `let` form would format on a single line (see the previous section
for more on potential single line `let` forms):

```
(czprint i201 {:parse-string? true :width 28 :fn-force-nl #{:binding} :style :binding-nl-all})
(let [a b

      c d]
  (g h (i j)))
```

## Conclusion

You can get pretty much anything you want when it comes to formating `let` 
forms, and everything above also affects all functions and macros which
have a function type of `:binding`, which is (by default):

```
"binding" :binding,
"doseq" :binding,
"dotimes" :binding,
"for" :binding,
"if-let" :binding,
"if-some" :binding,
"let" :binding,
"letfn" :binding,
"loop" :binding,
"when-first" :binding,
"when-let" :binding,
"when-some" :binding,
"with-local-vars" :binding,
"with-open" :binding,
"with-redefs" :binding,
```

If you have some way you would like to format a `let` form (or other `:binding`
form) that isn't covered by this explanation, feel free to submit an issue
which demonstrates what you would to see, and we'll see if we can't figure
out how to get it!
