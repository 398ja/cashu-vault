# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml project.properties ./
COPY cashu-vault-api/pom.xml cashu-vault-api/pom.xml
COPY cashu-vault-jpa/pom.xml cashu-vault-jpa/pom.xml
RUN mvn -B -q dependency:go-offline
COPY . .
RUN mvn -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/cashu-vault-jpa/target/*.jar app.jar
EXPOSE 3333
ENTRYPOINT ["java","-jar","app.jar"]
