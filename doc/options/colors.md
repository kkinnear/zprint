#__ Change the colors

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
to change the color of parentheses from the default green to black
(for only this call to zprint):
```
(czprint-fn defn {:color-map {:paren :black}})
```

You can include multiple ANSI sequences together:
```
(czprint-fn defn {:color-map {:none [:bright-black :italic]}})
```

You can of course change it permanently by placing the change
(in this case `{:color-map {:paren :black}}`) in an
options map that is used for configuration, like `~/.zprintrc`.

The allowable colors are (including their ANSI codes for reference):

```
  {:off 0,
   :reset 0,
   :bold 1,
   :faint 2,
   :italic 3,
   :underline 4,
   :blink 5,
   :reverse 7,
   :hidden 8,
   :strike 9,
   :normal 22,
   :italic-off 23,
   :underline-off 24,
   :blink-off 25,
   :reverse-off 27,
   :hidden-off 28,
   :strike-off 29,
   :black 30,
   :none 30,
   :red 31,
   :green 32,  
   :yellow 33, 
   :blue 34,   
   :magenta 35,
   :purple 35,
   :cyan 36, 
   :white 37,
   :xsf 38,
   :back-black 40,  
   :back-red 41,    
   :back-green 42,  
   :back-yellow 43, 
   :back-blue 44,    
   :back-magenta 45, 
   :back-purple 45,
   :back-cyan 46,
   :back-white 47,
   :bright-black 90,
   :bright-red 91,
   :bright-green 92,
   :bright-yellow 93,
   :bright-blue 94,
   :bright-magenta 95,
   :bright-purple 95,
   :bright-cyan 96,
   :bright-white 97,
   :back-bright-black 100,
   :back-bright-red 101,
   :back-bright-green 102,
   :back-bright-yellow 103,
   :back-bright-blue 104,
   :back-bright-magenta 105,
   :back-bright-purple 105,
   :back-bright-cyan 106,
   :back-bright-white 107}
```

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
