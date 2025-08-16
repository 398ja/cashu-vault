# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -q -pl cashu-vault-jpa -am package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV cashu_vault_port=3333
ENV VAULT_BASE_URL=http://localhost:${cashu_vault_port}
COPY --from=build /build/cashu-vault-jpa/target/cashu-vault-jpa-*.jar app.jar
EXPOSE ${cashu_vault_port}
ENTRYPOINT ["java","-jar","app.jar"]

