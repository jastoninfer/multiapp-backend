# Multiapp Backend

[![Backend CI/CD](https://github.com/jastoninfer/multiapp-backend/actions/workflows/backend-ci-cd.yml/badge.svg)](https://github.com/jastoninfer/multiapp-backend/actions/workflows/backend-ci-cd.yml)

Spring Boot backend for **Multiapp**, a multi-tenant ticketing and appointment SaaS demo. The project is designed to show backend skills that are common in real business systems: authentication, tenant isolation, role-based access control, database migrations, audit logs, scheduling rules, and Docker deployment.

- Live demo: [https://multiapp-frontend.pages.dev](https://multiapp-frontend.pages.dev)
- Frontend repository: [https://github.com/jastoninfer/multiapp-frontend](https://github.com/jastoninfer/multiapp-frontend)

## What This Backend Does

- Uses Keycloak/OIDC for login and JWT validation.
- Maps each request to a tenant with `X-Tenant-Id`.
- Supports roles for platform admin, tenant admin, agent, resource user, and customer.
- Provides APIs for tickets, comments, attachments, appointments, contacts, members, tenants, resources, availability, and audit logs.
- Uses Flyway migrations with PostgreSQL.
- Uses `Idempotency-Key` for create requests that should not be duplicated.
- Uses ETag / `If-Match` for update requests where concurrent edits matter.
- Records audit logs for important changes.
- Runs with Docker Compose, PostgreSQL, Keycloak, the Spring Boot API, and Caddy.

## Stack

- Java 17
- Spring Boot 4
- Spring Security OAuth2 Resource Server
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Flyway
- Keycloak 26
- Docker / Docker Compose
- Caddy
- GitHub Actions

## Repository Layout

```text
src/main/java/com/example/multiapp
  appointment/       Appointment APIs and scheduling rules
  attachment/        Local attachment storage and download metadata
  audit/             Audit log API
  comment/           Ticket comment APIs
  contact/           External contact APIs
  contactclaim/      Claim-code flow for linking contacts to users
  idempotency/       Idempotency records and response replay
  membership/        Tenant member APIs
  outbox/            Stored domain events
  resource/          Working hours and unavailable-time APIs
  tenant/            Tenant management APIs
  ticket/            Ticket workflow, authorization, and search
  user/              /me, tenant selection, and user access APIs
```

## Main API Areas

| Area | Purpose |
| --- | --- |
| `/me` | Current user profile and tenant memberships |
| `/tickets` | Ticket search, create, detail, update, status changes, assignment |
| `/tickets/{id}/comments` | Public and internal comments |
| `/tickets/{id}/attachments` | Upload and protected download |
| `/tickets/{id}/appointments` | Create appointment from a ticket |
| `/appointments` | Appointment list, detail, and update |
| `/resources` | Resource users, working hours, and unavailable time |
| `/contacts` | External customer/contact records |
| `/members` | Tenant member management |
| `/tenant` | Tenant settings and platform tenant list |
| `/audit-logs` | Tenant activity history |

## Security And Tenant Model

- Keycloak handles login and user credentials.
- The backend validates JWTs and maps tokens to application users.
- Tenant-specific APIs require `X-Tenant-Id`.
- A user can have different roles in different tenants.
- The frontend hides unavailable actions, but the backend still checks every protected action.

## CI/CD

CI/CD means the project is checked and deployed through a repeatable pipeline.

For this backend:

- **CI** runs on pull requests and pushes to `main`.
- The pipeline runs Maven tests.
- It builds a Docker image after tests pass.
- **CD** runs only for `main`.
- The deploy job connects to the VPS through GitHub Actions secrets, pulls the latest code, restarts the Docker Compose stack using the VPS-local runtime files, and checks the deployed API health endpoint.

The workflow file is:

```text
.github/workflows/backend-ci-cd.yml
```

Required GitHub secrets:

```text
VPS_HOST
VPS_PORT
VPS_USER
VPS_SSH_KEY
VPS_APP_DIR
```

Required GitHub variable:

```text
BACKEND_HEALTH_URL
```

Private values are kept in GitHub secrets or repository variables, not in this README.

## Runtime Configuration Files

The repository keeps public templates for deployment shape:

```text
Caddyfile.example
docker-compose.example.yml
docker-compose.demo-prod.example.yml
.env.demo-prod.example
keycloak/import/multiapp-realm.template.json
```

The real runtime files are not committed:

```text
Caddyfile
docker-compose.yml
docker-compose.demo-prod.yml
.env.demo-prod
keycloak/import/multiapp-realm.json
```

This keeps server-specific values out of Git while still showing how the stack is meant to run. On a local machine or VPS, create the real files from the templates and fill in the actual values for that environment.

## Local Demo Run

Create local runtime files from the templates:

```bash
cp Caddyfile.example Caddyfile
cp docker-compose.example.yml docker-compose.yml
cp docker-compose.demo-prod.example.yml docker-compose.demo-prod.yml
cp .env.demo-prod.example .env.demo-prod
```

Start the stack:

```bash
docker compose --env-file .env.demo-prod \
  -f docker-compose.yml \
  -f docker-compose.demo-prod.yml \
  up -d --build
```

For the local Caddy setup, the browser-facing origins are:

```text
http://app.localhost
http://api.localhost
http://auth.localhost
```

Health check:

```bash
curl http://api.localhost/actuator/health
```

## Tests

```bash
./mvnw test
```

The full test suite uses Testcontainers, so Docker must be running locally.

## Demo Accounts

All demo accounts use `Demo123!`.

| Role | Account |
| --- | --- |
| Tenant admin | `tenant.admin@acme.demo` |
| Agent | `agent@acme.demo` |
| Resource user | `resource@acme.demo` |
| Customer | `customer@acme.demo` |

## Keycloak Realm Template

The repository includes a sanitized Keycloak realm template:

```text
keycloak/import/multiapp-realm.template.json
```

Runtime deployments provide the actual realm import file:

```text
keycloak/import/multiapp-realm.json
```

The runtime import file is not meant to expose generated keys, client secrets, or password hash material.
