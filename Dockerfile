# Build stage
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -pl cashu-vault-jpa -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
ENV VAULT_BASE_URL=http://localhost:3333/
COPY --from=build /build/cashu-vault-jpa/target/cashu-vault-jpa-*.jar /app.jar
CMD ["java", "-jar", "/app.jar"]
