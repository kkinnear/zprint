# Get zprint for VS Code
There exists an independently developed extension for VS Code which 
gives you access to zprint while using VS Code.

You can find it by searching the extensions for "zprint".

It is called 
[vscode-clj-zprint](https://github.com/rflagreca/vscode-clj-zprint).

Things to know about various versions:

## Version 0.0.2 12/15/21

### The zprint version it uses is `1.0.2`

It is built to use zprint version is `1.0.2`, while the latest
zprint release is `1.2.0`.  While `1.2.0` is clearly better in
several ways from `1.0.2`, in most cases you probably wouldn't
notice the difference.

### Use of `.zprintrc` files varies from zprint pre-built binaries

The configuration model is slightly different from zprint.  The VS
Code extension will read your `.zprintrc` files, but the way it
uses them varies from the zprint pre-built binaries.  The extension
will look for a `.zprintrc` file in the current directory, and if
it finds one, it will use only that `.zprintrc` file.  If it doesn't
find a `.zprintrc` file in the current directory, it will look for
a `.zprintrc` file in the users home directory.  If it finds one
there, it will use it.

The major difference is that zprint will always use the `.zprintrc`
file in the users home directory, and if that `.zprintrc` file has
`{:cwd-zprintrc? true}` or `{:search-config? true}`, then it will
also apply any additional configuration found in a `.zprintrc` file
in the current directory (having already applied the configuration
information found in the `.zprintrc` file in the users home directory).

So, if you are using this extension, ensure that whatever configuration
you have placed in your `.zprintrc` file resides entirely in a
single `.zprintrc` file in either your home directory or the current
directory.

This isn't a huge problem if you are aware of it, but could easily
trip you up if you are already using zprint with configuration files
in both places.

### Overall Experience

Overall, this extension works well in VS Code.  You can format a
whole file, or if you select an entire function, you can format
just that function.  It does not use zprint to format as you type,
nor will it format just some random lines inside of a function.
The minimum "formatable" unit is an entire function (or other
top-level expression), which is a zprint requirement and not something
missing from the extension itself.

### Works well with zprint "File Comment API"

Note that if you are using the zprint [file comment API](../bang.md),
where you can alter the way that zprint formats a single top level
function (or any expression) by giving zprint a formatting directive
in a Clojure comment preceding the function, you can include that
comment in the selection in VS Code and the extension will correctly
interpret that comment when formatting the function.

## Future Versions

Keep an eye out for later versions.  Features in progress:

  * Track the zprint configuration model regarding `.zprintrc` and
  `.zprint.edn` files.

  * Utilize zprint's `:range` capability internally to allow
  formatting of a top-level expression by selecting any portion of
  it.  That is, you don't have to exactly select a whole function
  to format it.

  * If you configure `vscode-clj-zprint` as the default formatter,
  you can format the top level expression where the cursor resides
  with two keystrokes (CMD-K CMD-F on Macos) without explicitly
  selecting anything.

  * Configure zprint from within VSCode directly, with or without
  using the `.zprintrc` or `.zprint.edn` files.

  * Built with later verions of zprint.
 
