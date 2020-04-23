# Comments

Comments are indented to the appropriate level.  In addition, there is some
special processing:

  * Comments are not counted when evaluating the "look" of a function, and
  so may well run long.

  * Comments are word-wrapped if they run beyone the specified `:width`,
  unless you are using `:indent-only`.

  * Inline comments are kept on the same line of code where there were
  in the input.

  * Inline comments are aligned if they are separated by less than 5 lines
  from each other.

  * At the top level blank lines are preserved, unless you are using 
  `{:parse {:interpose ...}}` to control the top level inter-expression 
  blank lines.  The `:interpose` value is only used after a comment 
  if the comment is followed by a blank line -- so that a block of 
  comments are not forced apart by the `:interpose` value.

All of these things are optional, and can be changed by changing the
options map.  The options map for comments:

```
:comment
   {:count? false, :inline-align-style :align, :inline? true, :wrap? true}
```
See [:comment](../reference.md#comment) for details on how to change the
configuration for comments.

