# Use zprint with a team

When developing Clojure(script) software in a team environment, it can be
very useful to use a common formatting tool such as zprint.  There are
three issues in this environment:

[1. Decide if you want all files to be formatted identically](#1-decide-if-you-want-all-files-to-be-formatted-identically)  
[2. Ensure that all members of your group use zprint the same way](#2-ensure-that-all-members-of-your-group-use-zprint-the-same-way)  
[3. Check to see if the files are formatted correctly](#3-check-to-see-if-files-are-formatted-correctly)


## 1. Decide if you want all files to be formatted identically

### Yes: I want to use zprint to enforce a particular source code format

Classic zprint will do exactly that -- it will reformat
every file you give it into what it thinks is the optimal format.
Classic zprint is highly (some might say "overly") configurable, and
can be configured to reproduce most common Clojure source formatting
styles.  If you have trouble configuring zprint to do what you want,
submit an issue (with an example of what you want), and I'll be glad
to help find a way to configure zprint to do what you want.

### No: I want to let team members control the source code format of their files to some degree

There are three different ways to get zprint to recognize the existing
formatting while still cleaning things up to varying degrees:

#### Just clean up the indentation and white space: `{:style :indent-only}`

You can use zprint's `{:style :indent-only}` to clean up
the indentation and white space in files, without otherwise changing their
basic format.

#### Enforce the configured format, but keep all blank lines: `{:style :respect-bl}`

zprint's `{:style :respect-bl}` will enforce a particular
format on the file -- with the exception of blank lines, which it will
always keep.  For many people, having the blank lines they put in the file
stay there, and having zprint format everything around those blank lines
is a nice mix between enforcing a particular format, and giving people
some latitude to format things "their way".

#### Keep all of the newlines, and enforce what is possible around that: `{:style :respect-nl}`

zprint's `{:style :respect-nl}` will keep all
of the newlines in the file.  zprint will add newlines as necessary
to try to keep things within the width as well as format functions 
as they are configured to be formatted.  This is a real middle ground
between `{:style :indent-only}` and classic zprint.

## 2. Ensure that all members of your group use zprint the same way

Whether you are using zprint to enforce a particular format, or simply
to clean up the existing format in your team's source files, it is important
to have all members of your team using the same zprint configuration.

zprint can read its configuration from several different places:

  * `$HOME/.zprintrc`
  * a `.zprintrc` file in a project
  * a URL of a `zprintrc` file

For personal use, keeping the zprint configuration in `$HOME/.zprintrc`
is convenient.  However, when you want different formatting conventions
for different projects, or when working with other team members,
locating the zprint configuration with the project files can be a
simpler solution.

Look [here](project.md) for information about how to keep zprint
configuration information local to a project.

## 3. Check to see if files are formatted correctly

As part of a build or commit process you can use zprint to enforce a particular
format in source files by formatting all files.  You can also use zprint
to check the format of files and exit with a non-zero exit status if it
encounters files that are not formatted as it would if asked to format the
file. 

To format all `.clj` files in the current directory:

```
% zprint -w *.clj

```
To check all `.clj` files in the current directory, exiting with an exit
status equal to the number of files that require formatting:

```
% zprint -c *.clj
```

There are additional options to each switch, for instance this will
output a summary of the results when all of the files are processed:

```
% zprint -sw *.clj
% zprint -sc *.clj
```
See [this discussion](files.md) about using the pre-built binaries for 
more useful details on the several options for use with `-w` and `-c` 
switches.


