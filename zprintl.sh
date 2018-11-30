# First argument -- location of graalvm
# Second argument -- version of zprint-filter
cd /app
export PATH=$1/bin:$PATH
export JAVA_HOME=$1
echo "$PATH"
pwd
whereis native-image
native-image --version
LC_ALL=C.UTF-8 native-image --no-server -J-Xmx4G -J-Xms2G -jar target/zprint-filter-$2 -H:Name="zprintl-$2" -H:+ReportUnsupportedElementsAtRuntime
