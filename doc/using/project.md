# Use zprint with different formatting for different projects

zprint can read its configuration from several different places:

  * `$HOME/.zprintrc`
  * a `.zprintrc` file in a project
  * a URL of a `zprintrc` file
  * the command line

For personal use, keeping the zprint configuration in `$HOME/.zprintrc`
is convenient.  However, when you want different formatting conventions
for different projects, or when working with other team members,
locating the zprint configuration with the project files can be a
simpler solution.

Keeping a `.zprintrc` file with other files associated a project
often makes the most sense, since then it can be added to source
control and stays current with the project.

In order to use a `.zprintrc` file in a project, every team member
has to enable that usage for zprint configuration in their personal
`$HOME/.zprintrc` file:

```clojure
{:search-config? true}
```
This doesn't change the zprint formatting directly, it simply instructs
zprint where to look for additional configuration information.

This will cause zprint, when run, to look in the current working
directory for a `.zprintrc` file, and if it doesn't find one there
to look in the parent of that directory for a `.zprintrc` file, and
keep looking up the directory tree until it finds a `.zprintrc`
file.  Obviously, you need to ensure that somewhere in the project
directory tree above the sources there is a `.zprintrc` file.
Typically this would go at the top level of a project, and be managed
by the same source control as other project related files.

Then the project `.zprintrc` file should have all of the configuration
necessary for your project, and each individual's `.zprintrc` file
has only `{:search-config? true}` to direct zprint to look for a
file in the current working directory or some parent of that
directory.

