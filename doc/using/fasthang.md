# Use zprint to run faster even on very deeply nested files

__EXPERIMENTAL__

Because zprint tries to compare two different ways of handling
every list or pair it formats, when formatting deeply nested
data or code, it can take a while to make these comparisons.

The comparison is between a "hang":
```clojure
(this is
      a
      hang)
```
and a "flow":
```clojure
(this 
  is
  a 
  flow)
```
Obviously either of these expressions could format all on the
same line -- the point here is any of these symbols could be an
expression of any arbitrary size.

A style has been added which will reduce the time it takes to
format deeply nested code or data by trying the hang, and if it works
(i.e., fits within the `:width`), it will use the hang.  There
are some tuning parameters that go along with this style, which will
cause zprint to continue to compare the hang with the flow and decide which
is better in some cases.

The result is that zprint runs from a little faster on normally nested
code and data, to dramatically faster on unusually deeply indented code
and data.  It also sometimes hangs things that it used to flow because the
hangs took more lines than the flow when it compared them.  So, there are
more hangs.

The configuration is easy:
```clojure
{:style :fast-hang}
```

I'd be interested in any feedback that you might have about the "look"
of code formatted with `{:style :fast-hang}`, as well as the perceived
performance of zprint when using this style.  

Should you have code or data that formats particularly slowly (with or
without `{:style :fast-hang}`), I'd be interested in hearing about that
too.

This style is __EXPERIMENTAL__ largely because I might make changes to
the tuning affecting the look and performance of the result, not because
I expect to remove it entirely. 


