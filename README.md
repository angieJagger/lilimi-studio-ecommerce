# Lilimi Studio

An e-commerce application for digital machine embroidery patterns and embroidered clothing, developed for the Lilimi Studio brand.

The project combines an Angular storefront with a Java / Spring Boot backend and PostgreSQL.

> Work in progress. The storefront currently uses local demo product data. The backend product catalog is available and tested independently. Order submission and payments are not connected yet.

## Implemented features

### Storefront

- Polish and English versions using Transloco.
- Responsive product catalog with filtering, sorting and incremental loading.
- Product detail pages and related products.
- Sweatshirt configuration: fit, size, color and embroidery option.
- Cart with quantity controls, item removal and local persistence.
- Checkout contact and address validation.
- Courier selection and delivery totals.
- Order review and preparation of order request data.
- Contact and project inquiry forms with client-side validation.

### Backend

- PostgreSQL schema managed with Flyway migrations.
- Product catalog with Polish and English translations.
- Fixed prices for digital products.
- Starting prices calculated from active clothing variants.
- Product lookup by slug.
- HTTP 404 responses for unavailable or unknown products.
- Integration tests using a separate PostgreSQL container.

## Technology stack

| Area | Technologies |
| --- | --- |
| Frontend | Angular 22, TypeScript, SCSS, Transloco |
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL 17 |
| Database migrations | Flyway |
| Testing | Vitest, JUnit, AssertJ, MockMvc, Testcontainers |
| Local infrastructure | Docker Compose |

## Repository structure

```text
.
├── src/                       # Angular application
├── public/                    # Frontend assets
├── angular.json
├── package.json
└── backend/
    ├── src/main/java/         # Spring Boot application
    ├── src/main/resources/    # Configuration and Flyway migrations
    ├── src/test/java/         # Backend tests
    ├── compose.yaml           # Local PostgreSQL
    ├── pom.xml
    └── mvnw.cmd
```

## Prerequisites

- Node.js and npm compatible with Angular 22.
- JDK 21.
- Docker Desktop with Linux containers running.

Maven Wrapper is included, so a separate Maven installation is not required.

The commands below use Windows PowerShell.

## Run locally

### 1. Start the frontend

From the repository root:

```powershell
npm ci
npm start
```

Open http://localhost:4200.

### 2. Configure the backend

From the repository root:

```powershell
cd backend
Copy-Item .env.example .env
Copy-Item src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

For the first setup, replace the password placeholder in both copied files with the same local password.

If these files already exist, keep your existing configuration instead of overwriting it.

The `.env` file is read by Docker Compose. Spring Boot reads `application-local.properties` when the `local` profile is active.

Both local configuration files are excluded from Git. Never commit real credentials or API tokens.

Changing these files does not change the password of a PostgreSQL user already stored in an existing Docker volume.

### 3. Start PostgreSQL

From `backend`:

```powershell
docker compose up -d --wait
```

PostgreSQL is available at `localhost:15432`. This is a database port, not a website.

Database files are stored in a named Docker volume.

### 4. Start Spring Boot

Ensure your terminal uses JDK 21:

```powershell
.\mvnw.cmd -version
```

Then run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Alternatively, run `LilimiBackendApplication` in IntelliJ IDEA with JDK 21 and the following program argument:

```text
--spring.profiles.active=local
```

Flyway applies pending migrations during startup.

Health endpoint: http://localhost:8080/actuator/health

## Product API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/products` | Active products with available prices |
| GET | `/api/products/{slug}` | A single available product |
| GET | `/actuator/health` | Application health |

Example product URLs:

- http://localhost:8080/api/products/forest-dragon
- http://localhost:8080/api/products/embroidered-sweatshirt

Prices are represented as integer grosz amounts in PLN. For example, `12900` means PLN 129.00.

For clothing, `priceType: "from"` represents the cheapest active variant, not the price of every configuration.

## Tests and builds

### Frontend

From the repository root:

```powershell
npx ng test --watch=false
npm run build
```

### Backend

From `backend`, with Docker Desktop running:

```powershell
.\mvnw.cmd test
```

To run tests and package the application:

```powershell
.\mvnw.cmd verify
```

Backend integration tests create their own PostgreSQL container and apply the migrations. They do not use the local development database.

## Database migrations

Migration scripts are stored in:

```text
backend/src/main/resources/db/migration
```

Do not modify migrations that have already been applied. Introduce database changes in a new versioned migration.

## Planned work

- Connect the Angular storefront to the backend API.
- Expose product configuration options and variant prices.
- Implement server-side order validation, pricing and persistence.
- Integrate payments and payment notifications.
- Provide secure access to purchased digital files.
- Add customer accounts and order history.
- Implement delivery workflows and transactional emails.
- Support embroidery digitization inquiries and quotes.
- Add product and order administration.
- Prepare brand graphics, production deployment and end-to-end tests.
