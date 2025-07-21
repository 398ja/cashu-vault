# Cashu Vault Docker Setup

This repository contains a multi-module Maven project.
The main Spring Boot application is located in `cashu-vault-jpa`.

## Building the Docker image

Use Docker's build command from the repository root:

```bash
docker build -t cashu-vault .
```

The build stage uses Maven to package the application. Tests are skipped during the image build.

## Running the container

Run the image and expose port `3333`:

```bash
docker run -p 3333:3333 cashu-vault
```

The application will start and listen on port `3333` inside the container.
