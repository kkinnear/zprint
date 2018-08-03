# Installing a zprint filter

Would you be interested in a capability where you could
type a few keystrokes, wait about 1.5 seconds, and have 
a Clojure(script) function pretty-printed from scratch 
(not just indented) right in your editor?

__Quick Start:__
  * [Install the zprint-filter so it starts up in < 1 second](#appcds-version)
  * [How to use the filter with editors](#now-how-do-i-use-it)

The more complete explanation...

* [What is a Unix filter](#what-is-a-unix-filter)
* [What would a zprint filter do](#what-would-a-zprint-filter-do)
* [Why would I want a zprint filter](#why-would-i-want-a-zprint-filter)
* [We all know Clojure startup is so slow that this will not work](#we-all-know-that-clojure-startup-is-so-slow-that-this-will-not-work)
  * [Performance Results](#performance-results)
   * [Benchmarks](#benchmarks)
* [Okay, how do I set this up for myself](#okay-how-do-i-set-this-up-for-myself)
  * [appcds version](#appcds-version)
  * [bootclasspath version](#bootclasspath-version)
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
number of columns.  The markdown source text for this paragraph,
for instance, was piped through `fmt` to make it fit nicely inside
of an 80 column editor window.

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
  long, and it is a reasonable example for a function you might
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

Your machine is probably faster than my 2012 MacBook Air, so you would see
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

### appcds version

This approach should give you sub-second startup for the zprint-filter!

It uses a "commercial" feature of Java that, unless you have paid
for a license, you are not supposed to use in __production__.  However,
developers can use this feature for free, and using it 
while editing source is clearly not "production".  I have actually
talked to people at Oracle and have been assured that developers
__can__ use this feature while doing development.  

This option was added in Java 1.8.0_40.  If you don't have a version
of Java 1.8 later than _40, don't bother with this approach, go on to 
the [bootclasspath version](#bootclasspath-version) below.

Find out what you have this way:

```
java -version
```

This approach (using the same zprint-filter downloaded from Github),
creates a cache of information in a single file that will speed up startup.
Not clear exactly what is in the cache, but it does help, more than
any other approach.

There are two ways to set this up.  

#### The automated (easy) way 

You can use a script released along with the zprint-filter uberjar
to create another script which will run the zprint-filter in a way
that it will startup in less than a second.

  1. Download `zprint-filter-0.4.11` from [releases](https://github.com/kkinnear/zprint/releases/latest)

  2. Download the install script `appcds` from [releases](https://github.com/kkinnear/zprint/releases/latest)

  3. Put both of these files in a directory where they can live for a while.
  Once you run the `appcds` script, the zprint-filter uberjar can't be moved.

  4. Make the `appcds` script executable:

  ```
  chmod +x appcds
  ```

  5. Decide what you would like to have your new shell script be
  named.  You might want this to be a short name, because you may
  be typing it to pretty print a function in your editor.  So the
  number of keystrokes it takes to run it probably matters.  You
  can rename it later if you want.  For now, call it `za` (for
  "zprint-filter appcds"), which is what some of the examples later assume.

  6. Run the install script:

  ```
  ./appcds zprint-filter-0.4.11 za
  ```
  It will run pretty quickly when it works, and will spit out
  less than a page of status and statistics.  When it completes,
  it outputs some text showing you how to test the result.

  7. You can put the `za` script anywhere, but it will only be
  useful if it resides in a directory on your PATH.  You can name
  it anything you want.

  This output script will accept Clojure(script) source on stdin
  and send the pretty-printed version to stdout.  See 
  [below](#now-how-do-i-use-it) for how to use it.

#### The manual way

Here are the steps to set this version up manually:

  1. Put the zprint-filter uberjar in some directory on your path.  It doesn't
  have to be there, but these instructions assume it will be.  Feel free
  to move it (but you'll have to understand what is going on a bit to
  do so).

  2. Create a file for testing called `helloworld.clj` with the single line
  `"hello world"` in it.

  3. Choose a filename for the new class cache.  This example 
  assumes `zprint.filter.cache`

  4. Create the list of classes used on startup -- type this command:

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:off \
          -XX:DumpLoadedClassList=zprint.filter.classlist \
	  -cp zprint-filter-0.4.11 \
          zprint.main  < helloworld.clj > /dev/null
  ```
  or, for easier copying and pasting: 

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:off -XX:DumpLoadedClassList=zprint.filter.classlist -cp zprint-filter-0.4.11 zprint.main < helloworld.clj > /dev/null
  ```  

  5. Build the cache now that you have the list of classes used -- type this 
  command. Note that this assumes it is running with a shell that will replace
  "`pwd`" with the path.

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:dump \
          -XX:SharedClassListFile=zprint.filter.classlist \
          -XX:SharedArchiveFile=`pwd`/zprint.filter.cache \
	  -cp `pwd`/zprint-filter-0.4.11 \
          zprint.main < helloworld.clj 
  ```

  or, for easier copying and pasting: 

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:dump -XX:SharedClassListFile=zprint.filter.classlist -XX:SharedArchiveFile=`pwd`/zprint.filter.cache -cp `pwd`/zprint-filter-0.4.11 zprint.main < helloworld.clj 
  ```  

  This will output a bunch of statistics about building the cache.

  6. Create a file named `za` (for zprint-filter appcds) with the following
  contents:

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:on \
          -XX:SharedArchiveFile=`pwd`/zprint.filter.cache \
	  -cp `pwd`/zprint-filter-0.4.11 zprint.main
  ```
  or, for easier copying and pasting:

  ```
  java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:on -XX:SharedArchiveFile=`pwd`/zprint.filter.cache -cp `pwd`/zprint-filter-0.4.11 zprint.main
  ```  

  7. Make that file executable:
  
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
  za < wherever-the-helloworld-file-is/helloworld.clj
  ```

  You should see the `"hello world"` come out on the terminal.

  If you see this instead:

  ```
  Error: Could not find or load main class zprint.main
  ```
  something is wrong with the path to the archive file and the
  zprint-filter file.  These files need to be specified with absolute
  paths, and that was why there is a `pwd` in the commands above.
  The `za` script you have created needs to have absolute paths to
  the class cache and the uberjar or it won't work.

For what it is worth, this should startup about twice as fast as just
running the uberjar "normally":

`java -jar zprint-filter <helloworld.clj`

and about 10% faster than the bootclasspath approach, described above.


### bootclasspath version

  1. Put the zprint-filter uberjar in some directory on your path.  It doesn't
  actually have to be there -- only the shell script you are creating does --
  but these instructions assume it will be.  Feel free to put it wherever
  you want (but you will have to modify these instructions a bit to do so).

  2. Change your current directory to the directory on your path.

  3. Create a file for testing called `helloworld.clj` with the single line
  `"hello world"` in it.

  4. Create a file called `zb` (for zprint-filter bootclasspath)
  containing the following single line:

  ```
  java -Xbootclasspath/a:`pwd`/zprint-filter-0.4.11 zprint.main
  ```

  Note that the switch you are using is: `-Xbootclasspath/a:`, and
  immediately after that (with no spaces) comes the fully
  specified file path to zprint-filter.

  5. Make that file executable:

  `chmod +x zb`

  6. Then, try it out:

  `./zb < helloworld.clj`

  you should see `"hello world"` come out on your terminal.

  7. Try it from somewhere else.  Change your directory to
  somewhere other than the directory with the zb file in it,
  and try it on some Clojure(script) source (called "x.clj"
  in the following example):

  `zb <x.clj`

  You should see the source to x.clj come out on your terminal.


## Now, how do I use it

There are two aspects to using this effectively in your editing
environment, and a third that can help:

  1. How to move to the start of a function definition? [nice to have]

  2. How to select the text to send to the zprint-filter? [required]

  3. How to actually send the text to the zprint-filter? [required]

You may find one command that will do both #2 and #3 (see vim,
below).  But they might be separate commands or keystrokes that you
might want to make a macro or user defined command in your editor.

### vim

In vim, if your cursor is at the top level left parenthesis of a
function definition, you can type `!a(za` if you have created the
filter script to be called `za`.  This will pipe everything to the balanced
right parenthesis through `za`, formatting the entire function.

There are other ways to mark all of the text between parentheses
in vim.

You can also pass an options map to the zprint-filter, though
you have to enclose it in single quotes.  For instance, you
could type this: `!a(za '{:vector {:wrap? false}}'` and have
the zprint-filter no pack things into vectors.

### emacs

Emacs seems to have several ways to move to the top of a function
definition and to then mark a region bounded by parentheses, 
and your personal configuration may have even more.  I'll leave
it to you to find the best way to do that in your environment.

To pipe a region through a filter and replace the region with the
returned text use `C-u M-| za RET`, where `za` is the
filter script that you created above.

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
sublime-keymap file (where we assume `za` is the name of the filter
script you created, and `KYBRD SHORTCUT` is what you want to use 
to do this operation when editing):

```
{ "keys": ["KYBRD SHORTCUT"], "command": "filter_through_command", "args": { "cmdline": "za" } }
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
directives.  If you have `!zprint` directives located contiguous
to function definitions, it is fairly easy to include them in the
text you send to the zprint-filter script.

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

You can also edit the appcds version (which we called `za` above) to
do the same things.

You can have as many different shell scripts as you want, that each
do something different.

If you have used `;!zprint {< option >}` in your source files,
if you don't pipe those comments though commands through the filter, it won't
see them and format things the way you want.  But with different shell
scripts you can get fine grained control of the filter.

