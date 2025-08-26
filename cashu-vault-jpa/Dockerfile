# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -q package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV cashu_vault_port=3333
ENV VAULT_BASE_URL=http://localhost:${cashu_vault_port}
COPY --from=build /build/target/cashu-vault-jpa-*.jar /app/app.jar
RUN chown -R /app
EXPOSE ${cashu_vault_port}
ENTRYPOINT ["java","-jar","/app/app.jar"]

