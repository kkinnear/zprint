# first argument -- version of zprint-filter
gu install native-image
native-image --version
cd /app
pwd
LC_ALL=C.UTF-8 native-image --no-server -J-Xmx7G -J-Xms4G -jar target/zprint-filter-$1 -H:Name="zprintl-$1" -H:EnableURLProtocols=https,http -H:+ReportExceptionStackTraces --report-unsupported-elements-at-runtime --initialize-at-build-time --no-fallback
