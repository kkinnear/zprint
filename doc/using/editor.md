# Use zprint while using an editor

[vim](#vim)  
[emacs](#emacs)  
[Sublime Text](#sublime-text-2-or-3)  
[Atom](#atom)  

Many editors have a feature allowing you to pipe some segment of the
text in the editor through an external program, and replace the existing
text with the results of that external program.  To use zprint in this 
environment, you must then:
  * Install zprint to [format whole files](./files.md)
  * Instruct your editor to send the text to zprint and use the result

## Install zprint to format whole files
See [here](./files.md) for information on how to install zprint
to format whole files.

## Instruct your editor to pipe text through an external program

In this case, the external program though which you will pipe the text
will be zprint.

There are two aspects to using zprint effectively in your editing
environment, and a third that can help:

  1. How to move to the start of a function definition? [_nice to have_]

  2. How to select the text to send to zprint? [__required__]

  3. How to actually send the text to zprint? [__required__]

You may find one command that will do both #2 and #3 (see vim,
below).  But they might be separate commands or keystrokes that you
might want to make a macro or user defined command in your editor.

In the discussions below, the name of the zprint program is `zprint`.
In this context, `zprint` will read Clojure source from stdin, and produce
formatted Clojure source on stdout.

### vim

In vim, if your cursor is at the top level left parenthesis of a
function definition, you can type `!a(zprint`.
This will pipe everything to the balanced
right parenthesis through `zprint`, formatting the entire function.

There are other ways to mark all of the text between parentheses
in vim.

You can also pass an options map to zprint, though
you have to enclose it in single quotes.  For instance, you
could type this: `!a(zprint '{:width 90}'` and have
zprint format the result for 90 columns instead of the default 80.

### emacs

There is an [emacs plugin for zprint](https://github.com/pesterhazy/zprint-mode.el).

[![MELPA](https://melpa.org/packages/zprint-mode-badge.svg)](https://melpa.org/#/zprint-mode)


##### If you want to do it yourself.

Emacs seems to have several ways to move to the top of a function
definition and to then mark a region bounded by parentheses, 
and your personal configuration may have even more.  I'll leave
it to you to find the best way to do that in your environment.

To pipe a region through a filter and replace the region with the
returned text use `C-u M-| zprint RET`.

Of course you don't have to limit what you send to just the characters
of a function definition.  You can send multiple function defintions,
or you can include  `;!zprint {...}` formatting directives.  You can
even send a whole buffer through the filter.  What you cannot do is send
an unbalanced set of delimiters (parentheses, braces, brackets) through
the filter.  If you do, it will return an error and the unmodified original 
text.

### Sublime Text 2 or 3

In Sublime Text, you can use the 
[External Command](https://packagecontrol.io/packages/External%20Command)
plugin to send either the entire file or your current selection to this 
executable.

Once you've installed External Command (manually or via [Package
Control](https://packagecontrol.io)), place the following in your
sublime-keymap file (where we assume `zprint` is the name of the program
which runs zprint, and `KYBRD SHORTCUT` is what you want to use 
to do this operation when editing):

```
{ "keys": ["KYBRD SHORTCUT"], "command": "filter_through_command", "args": { "cmdline": "zprint" } }
``` 

At this point, you can use your keyboard shortcut to send the entire file to
your executable. 

If you have selected text, it will send your current selection to
the executable and replace only that selection with the output. You
can also use `Selection > Expand Selection to Brackets` (defaults
to `Ctrl-Shift-m`) to select between parentheses and repeat it once
to include the parentheses themseleves.  If you are deep within a
function, you may need to repeat it several times to get to the
top level.  There may be other ways to get to the top level.  

### Atom

The atom editor has a [zprint plugin](https://atom.io/packages/zprint-atom).


### Other Editors

I would suggest that you check the documentation for your editor or 
IDE for how to send text to the `fmt` filter, as the answer to that
will tell you how to send text to zprint, which acts similarly to `fmt`,
though of course operating to format Clojure source instead of word wrapping
text (which is what `fmt` does).  

You then want to figure out how to send all of the items between
balanced parentheses to an external program.

Then put these together.

If you get this to work in an editor not yet listed here, please file
an issue and tell me how to you did it, and I'll update the instructions
here.  Even better would be to create a pull request for this file with 
detailed instructions!

