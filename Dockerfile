# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -q -pl cashu-vault-jpa -am package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV VAULT_BASE_URL=http://localhost:3333
COPY --from=build /build/cashu-vault-jpa/target/cashu-vault-jpa-*.jar app.jar
EXPOSE 3333
ENTRYPOINT ["java","-jar","app.jar"]

