# Configuration Reference

The service and client can be customized through environment variables and system properties.

## Environment variables

| Name | Default | Description | Example |
|------|---------|-------------|---------|
| `cashu_vault_port` | `3333` | Port exposed by the vault service. Also used to build the default base URL. | `cashu_vault_port=8080 java -jar cashu-vault-jpa/target/cashu-vault-jpa-*.jar` |
| `VAULT_BASE_URL` | `http://localhost:${cashu_vault_port}` (defaults to `http://localhost:3333`) | Base URL of the vault service used by clients and configuration. | `VAULT_BASE_URL=https://vault.example.com java -jar client.jar` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/cashu_vault` | JDBC connection string for the database. | `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vault ...` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username. | `SPRING_DATASOURCE_USERNAME=vault` |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password. | `SPRING_DATASOURCE_PASSWORD=secret` |
| `POSTGRES_DB` | `cashu_vault` | Name of the PostgreSQL database when using Docker Compose. | `POSTGRES_DB=cashu_vault` |
| `POSTGRES_USER` | `postgres` | PostgreSQL user when using Docker Compose. | `POSTGRES_USER=user` |
| `POSTGRES_PASSWORD` | `postgres` | PostgreSQL password when using Docker Compose. | `POSTGRES_PASSWORD=secret` |

## System properties

| Property | Default | Description | Example |
|----------|---------|-------------|---------|
| `vault.base.url` | `http://localhost:${cashu_vault_port}` (defaults to `http://localhost:3333`) | Base URL of the vault service used by `VaultClient`. | `java -Dvault.base.url=https://vault.example.com -jar client.jar` |

## Usage examples

### Run the service on a custom port

```bash
cashu_vault_port=8080 ./cashu-vault-jpa/mvnw spring-boot:run
```

### Override the base URL for clients

```bash
VAULT_BASE_URL=https://vault.example.com java -jar client.jar
# or using a system property
java -Dvault.base.url=https://vault.example.com -jar client.jar
```

### Customize database connection

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/cashu_vault \
SPRING_DATASOURCE_USERNAME=vault \
SPRING_DATASOURCE_PASSWORD=secret \
java -jar cashu-vault-jpa/target/cashu-vault-jpa-*.jar
```
