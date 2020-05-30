# Use zprint to format a babashka script

You can use any of the zprint programs to format a babashka script
without any problem.  zprint will recognize the first line as a
"shebang", remove it, format the script, and then restore the first
line before producing the output.

You might want to have different formatting conventions when formatting
a babashka script (or any script).  You might wish to change the
default `:width`, for instance.

You can configure additional zprint configuration options that will
be applied when zprint recognizes an incoming file as a script.
These options are configured like this:

```clojure
{:script {:more-options options-map}}
```
The `options-map` will be applied on top of any existing options
currently in force when a file is recognized to be a script, which
happens last -- after all other options processing has been completed.

Thus, to change the `:width` just for scripts, and leave other
formatting unchanged, you would configure:
```clojure
{:script {:more-options {:width 120}}}
```
