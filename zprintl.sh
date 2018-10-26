# First argument -- location of graalvm
# Second argument -- version of zprint-filter
cd /app
export PATH=$PATH:$1/Contents/Home/bin
export JAVA_HOME=$1/Contents/Home
native-image --version
native-image --no-server -J-Xmx4G -J-Xms2G -jar target/zprint-filter-$2 -H:Name="zprintl-$2" -H:+ReportUnsupportedElementsAtRuntime
