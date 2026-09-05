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
    # Heap dumps are OFF by default (audit L-13). A dump from this service contains every proof
    # secret and signing key resident at the time, written unencrypted to a path that is often a
    # mounted volume shipped elsewhere. Enable deliberately, with somewhere safe to put it:
    #   -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/secure/heapdump.hprof
    "-jar", "app.jar"]
