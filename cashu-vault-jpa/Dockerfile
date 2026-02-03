FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/logs
COPY target/cashu-vault-jpa-*-exec.jar app.jar
EXPOSE 3333
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-Xms512m", \
    "-Xmx512m", \
    "-XX:MetaspaceSize=128m", \
    "-XX:MaxMetaspaceSize=256m", \
    "-Xlog:gc*:file=/app/logs/gc.log:filecount=5,filesize=50m", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs/heapdump.hprof", \
    "-jar", "app.jar"]
