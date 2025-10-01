FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/cashu-vault-jpa-*-exec.jar app.jar
EXPOSE 3333
ENTRYPOINT ["java", "-jar", "app.jar"]
