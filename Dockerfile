# Use Maven to build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -pl cashu-vault-jpa -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/cashu-vault-jpa/target/cashu-vault-jpa-1.0-SNAPSHOT.jar app.jar
ENV VAULT_BASE_URL=http://localhost:3333
ENTRYPOINT ["java","-jar","/app/app.jar"]
