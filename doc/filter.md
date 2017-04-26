# Installing a zprint filter

Would you be interested in a capability where you could
type a few keystrokes, wait about 1.5 seconds, and have 
a Clojure(script) function pretty-printed from scratch 
(not just indented) right in your
editor?

If so, read on -- or jump right in:

* [What is a Unix filter](#what-is-a-unix-filter)
* [What would a zprint filter do](#what-would-a-zprint-filter-do)
* [Why would I want a zprint filter](#why-would-i-want-a-zprint-filter)
* [We all know Clojure startup is so slow that this will not work](#we-all-know-that-clojure-startup-is-so-slow-that-this-will-not-work)
  * [Performance Results](#performance-results)
   * [Benchmarks](#benchmarks)
* [Okay, how do I set this up for myself](#okay-how-do-i-set-this-up-for-myself)
  * [bootclasspath version](#bootclasspath-version)
  * [appcds version](#appcds-version)
* [Now, how do I use it](#now-how-do-i-use-it)
  * [vim](#vim)
  * [emacs](#emacs)
  * [other editors](#other-editors)
* [Configuring the filter](#configuring-the-filter)
  * [Different files for different styles](#different-files-for-different-styles)


## What is a Unix filter

In the Unix world (which includes Linux and MacOS for the purposes
of this discussion), a __filter__ is a program that reads from
standard input, performs some operation on the data, and writes to
standard output.  Examples are `grep`, `sort`, `cat`, `uniq`, `tail`
and `fmt`.  All of these process and transform or select text from
standard in (stdin) and put the results of these transformations
on standard out (stdout).

`fmt` is an interesting example, because it doesn't actually change
the content of the text passed through it, but rather it changes
the __layout__ or __formatting__ of the text -- by word wrapping
the result.  Back in the day, `fmt` was used to word-wrap email and
even today people use it make text fit conveniently into some given
number of columns.  The source text for this paragraph, for instance,
was piped through `fmt` to make it fit nicely inside of an 80 column
editor window.

## What would a zprint filter do

In the same mold as `fmt`, which reformats plain ASCII text, a `zprint`
filter would accept Clojure(script) source on stdin, and produce pretty-printed
Clojure(script) source on stdout.

## Why would I want a zprint filter

Most editors or IDE's have some way to take a section of the source
and pipe it though a filter and replace the source with whatever the
filter returns.  Thus, I can take one of the paragraphs of this text
file and pipe it through `fmt` with the vi clone editor that I use.
Certainly vim and emacs both can take text (which might be Clojure(script)
source) and pipe it through a filter and then replace the source with
whatever is returned.

So, if you had a zprint filter, by typing a few keystrokes, you
could have any individual function pretty-printed by zprint while
you were sitting in an editor or IDE looking at the function.

## We all know Clojure startup is so slow that this is never going to work

Yes, I thought that Clojure startup would be __way__ too slow, so I spent
some significant time exploring Clojurescript specifically so that I could
build a zprint formatting filter using Clojurescript.  And I did, and it
does work in Clojurescript -- you can see the results of my efforts in
`lein-zprint` [here](https://github.com/kkinnear/lein-zprint#a-zprint-formatting-filter-using-planck-or-lumo)

However, I have found that, if I AOT compile all of zprint and
the associated libraries (and use just minimal libraries), and use the
right Java command line arguments (and caching techniques), that I can
get a Clojure zprint filter to startup slightly __faster__ than a Clojurescript
zprint filter.  And to do so in just under 1 second.  Which isn't zero, but
which is fast enough to be perfectly useful.

Moreover, as the amount of source to be formatted grows, the Clojure
version runs considerably faster than the Clojurescript version.

### Performance Results

I have built two Clojurescript zprint filters, one using `planck`, which
uses JavascriptCore on MacOS, and one using `lumo`, which uses node.js.
There are also three different invocations of the Clojure zprint filter,
which I will explain later.  

__The bottom line__: If you download an uberjar from github, you can
have a filter that will pretty-print your Clojure(script) source
code in about 1.5 seconds for a reasonable sized function.
There are two ways to set it up to be fast, with the "appcds"
approach being about 10% faster than the "bootclasspath" approach.
Both are quite simple, and there are complete instructions below
on how to do it.

#### Benchmarks

If you want the details, here they are.

The machine is a MacBook Air (13-inch, Mid 2012), 2 GHz Intel Core
i7, 8 GB 16000 MHz DDR3.  SSD disk, but mostly it runs out of the
file cache.  

All numbers are an average of at least 7 runs, using
whatever kind of cache is available (both Clojurescript filters
cache the compiled Javascript, and the zprint-filter appcds has a
cache as well).

The table below has a lot of data in it.  There are three basic
experiments:

  * "Startup Secs" -- how long does it take to format the
  string "hello world", which measures startup and actually
  doing something -- but not much.

  * "Format defn" -- how long does it take to format the
  1.8 source for the function `defn`.  This is 49 lines
  long, and it a reasonable example for a function you might
  want to format.

  * "Format 3K loc" -- how long does it take to format
  a source file in zprint itself, which has 3K lines in it.
  It contains 113 lines of comments.  It has a number of
  of normal sized functions and a couple of very large ones.
  This is the entire source file, something you could certainly
  use the filter for, but not the expected use case. 

  I've included this edge case because it shows how Java really
  does run faster than Javascript, but it can take some work to
  get it to start up quickly.

Here are the numbers:

| Filter | Startup Secs | % of basic Startup | Format defn (49 loc) | % of basic format defn | Format 3K loc | % of basic format 3K loc|
|--------|------|-------------------------------|----------|----|----|----|
| planck (JavascriptCore) | 3.100 | 158% | 3.530 | 146% | 13.854 | 194% |
| zprint-filter basic | 1.955 | 100% | 2.415 | 100% | 7.107 | 100% |
| zprint-filter bootclasspath | 1.179 | 60% | 1.611 | 67% | 5.914 | 83% |
| lumo (node.js)| 1.003 | 51% | 2.188 | 91% | 20.442 | 288%  |
| zprint-filter appcds | .996 | 51% | 1.407 | 58%  | 5.900 | 83%  |

Your machine may very well be faster than mine, so you would see
even better numbers.  

## Okay, how do I set this up for myself

The two lines you should be interested in are the zprint-filter bootclasspath
and zprint-filter appcds lines.  As you might imagine, the bootclasspath
is easier to set up, and the appcds runs faster for small functions.

I have provided an uberjar in the
[releases](https://github.com/kkinnear/zprint/releases/latest)
area, which you need to
download.  It doesn't have a .jar extension because I find that various
programs get all excited about .jar files.   Why upset them needlessly?

First, you need to find a directory which is on your path (or, if you don't
have one, you need to create one).  I use `~/bin`, but it doesn't matter.
You are going to create a shell script.

### bootclasspath version

  1. Put the zprint-filter uberjar in some directory on your path.  It doesn't
  actually have to be there -- only the shell script you are creating does --
  but these instructions assume it will be.  Feel free to put it wherever
  you want.

  2. Change your current directory to the directory on your path.

  3. Create a file for testing called `helloworld.clj` with the single line
  `"hello world"` in it.

  4. Find the absolute pathname to the directory containing zprint-filter:
  `pwd`

  5. Create a file called `zb` (for zprint-filter bootclasspath)
  containing the following single line, where you replace
  `<path-to-zprint-filter>` below with the path you found in #3 above:

  `java -Xbootclasspath/a:<path-to-zprint-filter>/zprint-filter-0.3.0 zprint.main`

  Note that the switch you are using is: `-Xbootclasspath/a:`, and
  immediately after that (with no spaces) comes the fully
  specified file path to zprint-filter.

  6. Make that file executable:

  `chmod +x zb`

  7. Then, try it out:

  `./zb < helloworld.clj`

  you should see `"hello world"` come out on your terminal.

  8. Try it from somewhere else.  Change your directory to
  somewhere other than the directory with the zb file in it,
  and try it on some Clojure(script) source (called "x.clj"
  in the following example):

  `zb <x.clj`

  You should see the source to x.clj come out on your terminal.

### appcds version

This one is a bit more work, but for it, you get faster startup.

It uses a "commercial" feature of Java that, unless you have paid
for a license, you are not supposed to use in production.  However,
developers can use this feature for free, and using it 
while editing source is clearly not "production".  I have actually
talked to people at Oracle and have been assured that developers
__can__ use this feature while doing development.  

This option was added in Java 1.8.0_40.

This approach (using the same zprint-filter downloaded from Github),
creates a cache of information in a single file that will speed up startup.
Not clear exactly what is in the cache, but it does help, more than
any other approach.

Here are the steps to set this version up:

  1. Put the zprint-filter uberjar in the directory on your path.  It doesn't
  have to be there, but these instructions assume it will be.  Feel free
  to move it.

  2. Create a file for testing called `helloworld.clj` with the single line
  `"hello world"` in it.

  3. Choose a filename for the new class cache.  This example 
  assumes `zprint.filter.cache`

  4. Create the list of classes used on startup -- type this command:

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:off \
          -XX:DumpLoadedClassList=zprint.filter.classlist \
	  -cp zprint-filter-0.3.0 \
          zprint.main  < helloworld.clj > /dev/null
  ```

  or, for easier copying and pasting: 
  

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:off -XX:DumpLoadedClassList=zprint.filter.classlist -cp zprint-filter-* zprint.main < helloworld.clj > /dev/null
  ```  

  5. Figure out where you are.  Type `pwd`, remember that as the `cwd`.

  6. Build the cache now that you have the list of classes used -- type this 
  command (replacing `cwd` with the absolute path to the current working 
  directory determined above).:

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:dump \
          -XX:SharedClassListFile=zprint.filter.classlist \
          -XX:SharedArchiveFile=cwd/zprint.filter.cache \
	  -cp cwd/zprint-filter-0.3.0 \
          zprint.main < helloworld.clj 
  ```


  or, for easier copying and pasting: 



  ```
   java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:dump -XX:SharedClassListFile=zprint.filter.classlist -XX:SharedArchiveFile=cwd/zprint.filter.cache -cp cwd/zprint-filter-0.3.0 zprint.main < helloworld.clj 
  ```  

  This will output a bunch of statistics about building the cache.

  7. Create a file `za` (for zprint-filter appcds) with the following contents,
  again replacing the characters "cwd" with the result of the `pwd` command 
  above:

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:on \
          -XX:SharedArchiveFile=cwd/zprint.filter.cache \
	  -cp cwd/zprint-filter-0.3.0 zprint.main
  ```

  or, for easier copying and pasting: 



  ```
 java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:on -XX:SharedArchiveFile=cwd/zprint.filter.cache -cp cwd/zprint-filter-0.3.0 zprint.main
  ```  

  8. Make that file executable:
  
  ```
  chmod +x za
  ```

  8. Try it out:

  ```
  za < helloworld.clj
  ```

  you should see `"hello world"` come out on your terminal.

  9. Try it from somewhere else.  Change your directory to
  somewhere other than the directory with the za file in it
  and type:

  ```
  za < cwd/helloworld.clj
  ```

  You should see the `"hello world"` come out on the terminal.

  If you see this instead:

  ```
  Error: Could not find or load main class zprint.main
  ```
  you haven't set up the path to the archive file and the zprint-filter
  file correctly.

For what it is worth, this should be about twice as fast as just
running the uberjar "normally":

`java -jar zprint-filter <helloworld.clj`

and about 10% faster than the bootclasspath approach, described above.


## Now, how do I use it

There are two aspects to using this effectively in your editing
environment, and a third that can help:

  1. How to I select the text to send to the zprint-filter? [required]

  2. How do I move to the start of a function definition? [nice to have]

  3. How to I actually send the text to the zprint-filter? [required]

I know that emacs has an answer (probably several) for #2.  

You may find one command that will do both #1 and #3 (see vim, below).  But they might
be separate commands or keystrokes that you might want to make
a macro or user defined command in your editor.

### vim

In vim, you can type `!a(zb` if you have created the script to be
called zb. If you are sitting on the top level left parenthesis
(which you should be), then this will pipe everything to the balanced
right parenthesis through zb, formatting an entire function. 

There
are other ways to mark all of the text between parentheses in vim.

### emacs

Emacs seems to have several ways to do this, including moving to
the top level of an s-expression before sending all of the information
off to an external program. I'm not even going to try to sort through
the various options and recommend one particular one.

### Sublime Text 2 or 3

Once you've gotten one of the above methods to work via an executable on your
PATH, you can use the [External Command](https://packagecontrol.io/packages/External%20Command)
plugin to send either the entire file or your current selection to this 
executable.

Once you've installed External Command (manually or via [Package Control](https://packagecontrol.io)),
place the following in your sublime-keymap file:

```
{ "keys": ["< YOUR KEYBOARD SHORTCUT >"], "command": "filter_through_command", "args": { "cmdline": "< EXECUTABLE NAME HERE >" } }
``` 

At this point, you can use your keyboard shortcut to send the entire file to
your executable. 

If you have selected text, it will send your current selection to the executable
 and replace only that selection with the output. You can also use 
 `Selection > Expand Selection to Brackets` (defaults to `Ctrl-Shift-m`) to select 
 between parentheses and repeat to include the parentheses themseleves.  

### Other Editors

I would suggest that you check the documentation for your editor or 
IDE for how to send text to the `fmt` filter, as the answer to that
will tell you how to send text to the zprint-filter.  

You then want to figure out how to send all of the items between
balanced parentheses to an external program.

Then put these together.

If you get this to work in an editor not yet listed here, please file
an issue and tell me how to you did it, and I'll update the instructions
here.

## Configuring the filter

You can configure the filter in two (and only two) ways:

  1. Using the ~/.zprintrc file.

  2. Placing an options-map on the command line as the only argument.

You cannot configure the filter from environment-variables or
from Java properties.  

The filter __will__ recognize and respond to `;!zprint { < options-map > }` 
directives in a source file -- but only if it actually encounters them.
So if you pipe a whole file through the zprint-filter, it will encounter
any `;!zprint` directives you have included.  If you pipe only a function
definition through the zprint-filter, it won't see any of the `;!zprint`
directives.

### Different files for different styles

You can create multiple files, each with a different format directive.
For instance, the options map `{:style :spec}` is really helpful for formatting
clojure.spec files, so you might have:

File zb: `java -Xbootclasspath/a:zprint-filter zprint.main`

File zs: `java -Xbootclasspath/a:zprint-filter zprint.main '{:style :spec}' `

and you would use `zs` when formatting spec files, and `zb` when formatting
other files.  You can do the same with the AppCDS cache file approach, and 
each shell script can use the same cache.

You might have a file `zj` for `{:style :justified}`, so for certain
functions you might format them justified.

You can have as many different shell scripts as you want, that each
does something different.

If you have used `;!zprint {< option >}` in your source files,
if you don't pipe those comments though commands through the filter, it won't
see them and format things the way you want.  But with different shell
scripts you can get fine grained control of the filter.

