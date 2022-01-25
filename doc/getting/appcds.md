# Accelerate the uberjar startup with appcds

There exists an uberjar for zprint which requires only Java to run,
and has no external dependencies.  It starts up rather slowly, but
you can accelerate its startup a lot by using appcds (application class
data sharing) in the JVM.

__NOTE:__ This script is only known to work for Oracle Java 8.  In particular,
it does *not* work for:

```
% java -version
openjdk version "1.8.0_275"
OpenJDK Runtime Environment (Zulu 8.50.0.1017-CA-macos-aarch64) (build 1.8.0_275-b01)
OpenJDK 64-Bit Server VM (Zulu 8.50.0.1017-CA-macos-aarch64) (build 25.275-b01, mixed mode)
``` 
which is an ARM OpenJDK that otherwise works fine on Apple M1 silicon.

There is a script which automates the setup for appcds for the zprint uberjar.

Follow these steps to use this script:

## 1. Go to the latest release for zprint
You can find the latest release [here](https://github.com/kkinnear/zprint/releases/latest).
## 2. Download the uberjar and appcds script
The uberjar is called `zprint-filter-0.x.y`, where `x.y` varies.
The script is called `appcds`. 
Click on each of these to download them.
## 3. Put the uberjar and the appcds script somewhere on your path
## 4. Run the appcds setup script:
The setup script takes two arguments:
  * The filename of the zprint-filter uberjar that you would like to speed up 
  with appcds.
  * The file name of the executable file produced by the appcds script which
  will startup the uberjar much more qickly.

For example:
```
% ./appcds zprint-filter-0.5.3 zprint
```
This will produce a script called zprint which wills startup the 
zprint-filter in about a second.  

## 5. Try it with `-v`
```
% zprint -v
zprint-1.2.2
```
This will also demonstrate the time it takes to start up the uberjar
with appcds.

## 6. Try it with `-e`
```
% zprint -e
```
This should cause the uberjar to emit the entire options map, showing
the current values for all of the configuration options.  Any option whose
value has been changed from the default will be noted by some key value
pairs indicating from where the non-default value originated.

## 7. Try it for real
```
zprint '{:width 90}' < myfile.clj
```
This will format `myfile.clj` for 90 column output and send the value to 
the terminal.  The `{:width 90}` is just to show an example of how to include
an options map on the call -- it is not required.
