# Cashu Vault

Cashu Vault is a Spring Boot service that stores and manages Cashu data using a PostgreSQL database.

Full documentation, including tutorials, how-to guides, reference material, and explanations, is available in the [docs](docs/) directory.

## Docker images

Docker images for `cashu-vault-jpa` are published to `docker.398ja.xyz/cashu-vault-jpa`.
Each image is tagged with both the project version and `latest`, allowing consumers to pull the most recent build without specifying a version.

### Building the Docker image

To build the Docker image locally:

```bash
# Build the JAR
mvn clean package -DskipTests

# Build the Docker image
cd cashu-vault-jpa
docker build -t docker.398ja.xyz/cashu-vault-jpa:latest .
```

The Dockerfile uses the Spring Boot repackaged JAR from `target/cashu-vault-jpa-*.jar`.
