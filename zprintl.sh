# First argument -- location of graalvm
# Second argument -- file name of native image in graalvml directory
# Third argument -- version of zprint-filter
cd /app
export PATH=$1/bin:$PATH
export JAVA_HOME=$1
echo "$PATH"
pwd
gu install -L $2
whereis native-image
native-image --version
LC_ALL=C.UTF-8 native-image --no-server -J-Xmx4G -J-Xms2G -jar target/zprint-filter-$3 -H:Name="zprintl-$3" --report-unsupported-elements-at-runtime --initialize-at-build-time --no-fallback
