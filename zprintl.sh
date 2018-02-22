cd /app
native-image --no-server -J-Xmx4G -J-Xms2G -jar target/zprint-filter-$1 -H:Name="zprintl-$1" -H:+ReportUnsupportedElementsAtRuntime
