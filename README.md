# ShopAPI — E-Commerce Backend

A RESTful e-commerce backend built with Java and Spring Boot. The project demonstrates backend development practices including JWT authentication, role-based authorization, transactional order processing, inventory management, and automated testing.

## Tech Stack

- **Java 17**
- **Spring Boot 4.1.1**
- **Spring MVC and Spring Data JPA**
- **MySQL 8.4**
- **Spring Security and JWT**
- **Maven**
- **Docker and Docker Compose**
- **Springdoc OpenAPI / Swagger UI**
- **JUnit, Spring Boot Test, and H2**

## Features

- User registration and login with JWT authentication
- Role-based authorization for users and administrators
- Product listing with pagination, sorting, and validation
- Admin-only product creation, updates, and deletion
- Order creation, retrieval, and cancellation
- Inventory validation and stock updates during order processing
- Transactional order creation and concurrency control
- Idempotency support for order creation requests
- Centralized exception handling and structured error responses
- Health and readiness endpoints
- Automated unit, integration, and controller tests

## Architecture

The application follows a layered architecture:

- **Controller:** REST endpoints and HTTP request handling
- **Service:** Business logic and transaction coordination
- **Repository:** Database access through Spring Data JPA
- **Model:** JPA entities and domain types
- **DTO:** Request and response objects
- **Mapper:** Conversion between entities and DTOs
- **Config:** Security, OpenAPI, and development configuration
- **Exception:** Centralized error handling and domain exceptions

## Requirements

- Java 17
- Docker Desktop with Docker Compose
- Git

## Run with Docker

1. Clone the repository:

   ```bash
   git clone https://github.com/yasirbilir06/mini-ecommerce.git
   cd mini-ecommerce
   ```

2. Create a local `.env` file in the project root. Do not commit this file.

   ```dotenv
   DB_PASSWORD=replace_with_a_strong_database_password
   MYSQL_ROOT_PASSWORD=replace_with_a_strong_root_password
   JWT_SECRET=replace_with_a_random_secret_of_at_least_32_bytes
   ```

   Use your own strong values. Never use these placeholder strings as production credentials.

3. Start the application and database:

   ```bash
   docker compose up --build
   ```

4. Open the API documentation:

   [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

The API runs on port `8080`. Docker maps MySQL to host port `3307`. The Compose configuration uses a persistent volume for database data.

To stop the services:

```bash
docker compose down
```

This keeps the database volume. Do not use `docker compose down -v` unless you intentionally want to remove the persisted database data.

## Configuration

Configure secrets through environment variables rather than committing credentials.

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for the application database |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret used to sign JWTs |
| `SPRING_PROFILES_ACTIVE` | Optional Spring profile |
| `BOOTSTRAP_ADMIN_ENABLED` | Enables the development admin bootstrap when configured for the `dev` profile |
| `BOOTSTRAP_ADMIN_EMAIL` | Development bootstrap admin email |
| `BOOTSTRAP_ADMIN_PASSWORD` | Development bootstrap admin password |

## API Overview

The following routes describe the main API areas. Consult Swagger UI for the exact request schemas, parameters, and response codes.

| Area | Routes | Access |
|---|---|---|
| Authentication | `/api/auth/**` | Public registration and login routes |
| Products | `GET /api/products` | Public |
| Product management | `POST`, `PUT`, `DELETE /api/products/**` | Admin |
| Orders | `/api/orders/**` | Authenticated users; access is scoped by authorization rules |
| Administration | `/api/admin/**` | Admin |
| Readiness | `/actuator/health/readiness` | Health-check endpoint |

Authenticated endpoints require a bearer JWT. Product management and administrative operations require the appropriate role.

## Testing

Run the standard test suite:

```bash
./mvnw test
```

The default test profile uses an in-memory H2 database for isolated tests.

A separate MySQL integration-test profile is available in `application-mysql-test.properties`. It requires a reachable test database and the corresponding environment variables. Configure it only against a dedicated test database, never a database containing data you need to preserve.

Example, after configuring the test database credentials in your shell:

```bash
MYSQL_TEST_DB_PASSWORD='your_test_database_password' \
./mvnw -Dtest=OrderServiceMySqlIntegrationTest test
```

The MySQL integration tests use the `mysql-test` profile and `spring.jpa.hibernate.ddl-auto=create-drop`. **The configured test database schema is dropped and recreated by the test run.** Never point this profile at your development or production database.

## Security Notes

- Do not commit `.env`, credentials, JWT secrets, or real user data.
- Use a strong, unique JWT signing secret.
- Use HTTPS and appropriate secret management in deployed environments.
- The development admin bootstrap is intended for controlled development use only.
- Review authorization rules and configuration before deploying publicly.

## Project Status

This is a portfolio project for demonstrating Java backend engineering, REST API design, authentication, relational persistence, transaction handling, and testing.
