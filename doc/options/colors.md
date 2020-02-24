# Change the colors

When output is directed to an ANSI terminal, zprint will colorize both code
and structures output.  I does this with a color map in the options map.  

The default color map:
```
 :color-map {:brace :red,
             :bracket :purple,
             :comment :green,
             :deref :red,
             :fn :blue,
             :hash-brace :red,
             :hash-paren :green,
             :keyword :magenta,
             :nil :yellow,
             :none :black,
             :number :purple,
             :paren :green,
             :quote :red,
             :string :red,
             :syntax-quote-paren :red,
             :uneval :magenta,
             :user-fn :black},
```
If you want to change the colors, simple remap something.  For example,
to change the color of parentheses from the default green to black:
```
(czprint-fn defn {:color-map {:paren :black}})
```
You can of course change it permanently by placing the change in an
options map that is used for configuration, like `~/.zprintrc`.

### Unevaluated code

When zprint finds the symbols `#_` that mark unevaluated code, it changes
the color map to:
```
:uneval {:color-map {:brace :yellow,
                      :bracket :yellow,
                      :comment :green,
                      :deref :yellow,
                      :fn :cyan,
                      :hash-brace :yellow,
                      :hash-paren :yellow,
                      :keyword :yellow,
                      :nil :yellow,
                      :none :yellow,
                      :number :yellow,
                      :paren :yellow,
                      :quote :yellow,
                      :string :yellow,
                      :syntax-quote-paren :yellow,
                      :uneval :magenta,
                      :user-fn :cyan}},
```
This highlights the unevaluated code.  You can change any of these colors
as well.

If you wish to change the colors permanently, you can place these 
options maps
[anywhere an options map is accepted](../altering.md#2-get-the-options-map-recognized-by-zprint-when-formatting).
