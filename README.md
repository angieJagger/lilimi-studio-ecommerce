# Lilimi Studio

A full-stack application combining a digital embroidery shop, embroidered products and custom service inquiries.

I am building Lilimi Studio for my own planned business. I design the interface and develop the Angular frontend, Spring Boot backend, database, tests and deployment infrastructure.

[Live demo](https://lilimistudio.com) · [Technical guide](docs/PROJECT_GUIDE.md) · [Recovery procedures](deploy/RECOVERY.md)

![Lilimi Studio homepage](docs/screenshots/home-desktop.png)

> Demo application under active development. Stripe payments are tested in a sandbox and disabled on the public demo. Real payments, automatic fulfilment and digital file delivery are not available.

## Main features

- Polish and English studio website with service pages and project inquiry forms.
- API-backed catalogue, garment configuration and a persistent cart supporting digital and physical products.
- Server-priced checkout with saved order snapshots and protection against duplicate submissions.
- Customer accounts with email verification, password recovery, saved profiles and order history.
- Stripe sandbox integration with hosted Checkout, payment resumption, verified webhooks and cancellation handling.
- Administrator tools for orders, inquiries, product translations, visibility, prices and garment variants.
- Order work queues, archive views, status and date filters, customer search, and URL-preserved navigation.

## Technology

| Area | Stack |
| --- | --- |
| Frontend | Angular 22, TypeScript, signals, RxJS, SCSS, Transloco, SSR |
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA |
| Database | PostgreSQL 17, Flyway |
| Testing | Vitest, Angular TestBed, JUnit, MockMvc, Testcontainers |
| Delivery | GitHub Actions, Docker Compose, GHCR, AWS Lightsail, Caddy |

## Selected engineering decisions

- **The server owns pricing.** Orders retain snapshots of purchased products, configurations and prices.
- **Retries must not duplicate operations.** Order submission and Stripe session creation use idempotency keys.
- **Payment confirmation comes from verified webhooks.** Browser redirects cannot mark an order as paid; webhook deduplication and payment updates share a transaction.
- **External calls stay outside database transactions.** Email and payment session expiration use persisted queues with bounded retries.
- **Tests use PostgreSQL.** Integration tests exercise migrations, constraints and concurrency against Testcontainers databases.
- **Deployments use verified commits.** CI runs tests, dependency audits and image scans before publishing images tagged with the full commit SHA.

## Run locally

Prerequisites: Node.js 24, npm, JDK 21 and Docker with Linux containers.

For development, run Angular and Spring Boot separately with PostgreSQL and Mailpit. The technical guide includes first-time configuration, startup commands and local Stripe testing:

[Local development setup](docs/PROJECT_GUIDE.md#run-locally)

## Verification

Frontend, from the repository root:

```powershell
npm test -- --watch=false
npm run build
npm audit --audit-level=high
```

Backend, from `backend`, with Docker running:

```powershell
.\mvnw.cmd verify
```

## Design and implementation

The project covers both interface design and implementation. Initial designs and user flows are available in the technical guide; some designs and screenshots reflect earlier development stages.

- [Code tour](docs/PROJECT_GUIDE.md#code-tour)
- [Architecture](docs/PROJECT_GUIDE.md#architecture)
- [API](docs/PROJECT_GUIDE.md#api)
- [Deployment](docs/PROJECT_GUIDE.md#deployment)

## Next steps

- Inquiry work queues, filtering and per-administrator read tracking.
- Product image management with persistent media storage.
- Further payment recovery, refunds and preparation for real payments.
- Controlled digital file delivery, fulfilment notifications and off-host backups.

[Detailed features and implementation notes](docs/PROJECT_GUIDE.md)