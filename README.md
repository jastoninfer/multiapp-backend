# Multiapp Backend

Spring Boot backend for a multi-tenant IT service management demo. The project models a SaaS workspace where tenants manage tickets, appointments, resources, external contacts, membership roles, audit events, and availability.

This repository is the backend/API side of the demo. It is designed to run with PostgreSQL, Keycloak, and Caddy through Docker Compose.

## Highlights

- Multi-tenant access model with `X-Tenant-Id` request scoping.
- Keycloak/OIDC login with JWT resource-server validation.
- Role-aware ticket workflow for customer users, agents, resource users, tenant admins, and platform admins.
- Ticket lifecycle, comments, attachments, appointments, resource availability, contacts, tenant membership, and audit-log APIs.
- ETag / `If-Match` support for state-changing operations where concurrent edits matter.
- Demo seeding with realistic tenants, users, tickets, appointments, contacts, and availability data.
- Docker Compose setup for local and server-based demo environments.

## Stack

- Java 17
- Spring Boot 4
- Spring Security OAuth2 Resource Server
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Flyway
- Keycloak 26
- Caddy
- Docker / Docker Compose

## Repository Layout

```text
src/main/java/com/example/multiapp
  appointment/       Appointment APIs and scheduling rules
  attachment/        Local attachment storage and download metadata
  audit/             Read-only audit log API
  comment/           Ticket comment APIs
  contact/           External contact APIs
  contactclaim/      Claim-code flow for linking contacts to users
  membership/        Tenant member list/detail/update/remove APIs
  resource/          Working hours and unavailable-time APIs
  tenant/            Tenant management APIs
  ticket/            Ticket workflow, authorization, and search
  user/              /me, tenant selection, and user access APIs
```

## Local Demo Run

Create a local environment file from the example:

```bash
cp .env.demo-prod.example .env.demo-prod
```

Then start the local Docker stack:

```bash
docker compose --env-file .env.demo-prod \
  -f docker-compose.yml \
  -f docker-compose.demo-prod.yml \
  up -d --build
```

For the local Caddy setup, the expected browser-facing origins are:

```text
http://app.localhost
http://api.localhost
http://auth.localhost
```

Health check:

```bash
curl http://api.localhost/actuator/health
```

## Demo Accounts

All demo accounts use the same password:

```text
Demo123!
```

| Workspace / tenant | Role | Accounts |
| --- | --- | --- |
| `__platform_admin` | Admin | `platform.admin@demo.com` (default)<br>`platform.ops@demo.com` (default) |
| Acme Facilities | Admin | `platform.admin@demo.com`<br>`platform.ops@demo.com`<br>`tenant.admin@acme.demo` (default)<br>`acme.admin2@demo.com` (default) |
| Acme Facilities | Agent | `agent@acme.demo` (default)<br>`acme.agent2@demo.com` (default)<br>`multi.member@demo.com` |
| Acme Facilities | Resource user | `resource@acme.demo` (default)<br>`acme.resource2@demo.com` (default)<br>`acme.resource3@demo.com` (default) |
| Acme Facilities | Customer | `customer@acme.demo` (default)<br>`acme.customer2@demo.com` (default)<br>`acme.customer3@demo.com` (default) |
| Beta Clinic | Admin | `platform.admin@demo.com`<br>`platform.ops@demo.com`<br>`tenant.admin@acme.demo`<br>`beta.admin@demo.com` (default) |
| Beta Clinic | Agent | `beta.agent@demo.com` (default)<br>`beta.agent2@demo.com` (default) |
| Beta Clinic | Resource user | `beta.resource@demo.com` (default)<br>`beta.resource2@demo.com` (default) |
| Beta Clinic | Customer | `multi.member@demo.com` (default)<br>`beta.customer@demo.com` (default) |
| Suspended Tenant | Admin | `platform.admin@demo.com`<br>`platform.ops@demo.com` |
| No workspace membership | Customer account | `guest.customer@demo.com` |

## Keycloak Realm Template

The repository includes a sanitized Keycloak realm template:

```text
keycloak/import/multiapp-realm.template.json
```

It preserves the demo realm structure, client settings, and stable demo user IDs used by the backend seeder. Generated signing keys, client secrets, and password hash material are omitted from the template.

Runtime deployments provide the actual Keycloak import file at:

```text
keycloak/import/multiapp-realm.json
```

Keeping stable user IDs allows `DemoDataSeeder` to align `app_user.keycloak_sub` values with Keycloak access-token `sub` claims.

## Environment Notes

Important environment variables:

```env
APP_ORIGIN=https://frontend.example.com
APP_CORS_ALLOWED_ORIGINS=https://frontend.example.com
KC_HOSTNAME=https://auth.example.com
OIDC_ISSUER_URI=https://auth.example.com/realms/multiapp
APP_UPLOAD_DIR=/data/uploads
```

The issuer URI must match the `iss` claim in Keycloak access tokens. In Docker, the backend must also be able to resolve the auth origin, usually through Caddy on the Compose network.

## Tests

```bash
./mvnw test
```

## Deployment Shape

The deployment model uses a small VPS-backed stack:

- VPS: PostgreSQL, Keycloak, backend, Caddy
- Static frontend hosting: Cloudflare Pages, Vercel, or similar
- HTTPS: handled by Caddy for API/Auth origins
- Persistent storage: PostgreSQL volume and `/data/uploads`
