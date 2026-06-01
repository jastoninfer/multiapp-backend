# Demo Data Seeding

This document explains how the `demo` profile initializes enough data to present
and test the Multiapp system without starting from a blank database.

## Entry Point

The backend demo seed code is:

```text
src/main/java/com/example/multiapp/demo/DemoDataSeeder.java
```

It is enabled only when:

```text
spring.profiles.active includes demo
```

and this property is not explicitly disabled:

```text
app.demo.seed.enabled=false
```

The seeder is deliberately isolated in the `demo` package. It does not change
existing controllers, services, entities, or repositories.

## Why This Is a Seeder Instead of R__seed.sql

Flyway repeatable SQL is good for tiny reference data, but demo data has more
moving parts:

- Keycloak users must be matched by issuer and subject.
- App users, tenants, memberships, contacts, tickets, appointments, blocks, and
  attachments must be created in dependency order.
- Attachment metadata must match actual local files.
- Seed execution must be idempotent.
- The demo data should be easy to evolve by seed version.

For those reasons, demo data now lives in Java startup code instead of a broad
SQL script.

## Keycloak Dependency

The seeder reads:

```text
keycloak/import/multiapp-realm.json
```

by default.

You can override this path:

```properties
app.demo.keycloak-import=keycloak/import/multiapp-realm.json
```

The expected users are:

```text
platform.admin@demo.com
tenant.admin@acme.demo
agent@acme.demo
resource@acme.demo
customer@acme.demo
guest.customer@demo.com
multi.member@demo.com
```

The seeder reads each user's Keycloak `id` as the OIDC subject and upserts
`app.app_user` by `(issuer, keycloak_sub)`.

The issuer defaults to:

```text
spring.security.oauth2.resourceserver.jwt.issuer-uri
```

or:

```text
http://localhost:8080/realms/multiapp
```

You can override it:

```properties
app.demo.keycloak-issuer=http://localhost:8080/realms/multiapp
```

## Idempotency

The seeder creates:

```text
app.demo_seed_history
```

with:

```text
seed_name
seed_version
applied_at
```

Default seed version:

```text
v2
```

Override:

```properties
app.demo.seed.version=v2
```

Force re-apply:

```properties
app.demo.seed.force=true
```

Normal behavior:

1. If `multiapp-demo-data/v2` already exists, skip.
2. If not applied, seed all demo data inside one transaction.
3. Mark the seed as applied only after all steps succeed.

## Creation Order

The seeder writes data in this order:

1. `app_user`
2. `tenant`
3. `tenant_membership`
4. `contact`
5. `contact_claim`
6. `resource_working_hours`
7. `resource_block`
8. `ticket`
9. `ticket_comment`
10. `appointment`
11. `ticket_attachment` plus local demo files

This order follows the database foreign key dependencies.

## Demo Tenants

```text
Acme Facilities
Beta Clinic
Suspended Tenant
```

`Acme Facilities` is the main demo tenant. Most feature coverage is there.

`Beta Clinic` exists to verify tenant switching and data isolation.

`Suspended Tenant` exists so platform/admin UI can show a suspended state.

## Demo Role Coverage

The seed creates memberships for:

```text
platform.admin@demo.com     platform admin + Acme ADMIN + Suspended ADMIN
tenant.admin@acme.demo      Acme ADMIN + Beta ADMIN
agent@acme.demo             Acme AGENT
resource@acme.demo          Acme RESOURCE_USER
customer@acme.demo          Acme CUSTOMER
multi.member@demo.com       Acme AGENT + Beta CUSTOMER
guest.customer@demo.com     no tenant membership
```

`guest.customer@demo.com` intentionally has no tenant membership. Use it to test
the "user has no tenant" state.

## Ticket Coverage

The seed creates tickets covering:

```text
URGENT + IN_PROGRESS
MEDIUM + CLOSED
HIGH + NEW
LOW + CLOSED
HIGH + REOPENED
Beta tenant NEW ticket
```

This gives the frontend enough data for:

- ticket list filters
- detail view
- owner display
- customer/requester display
- comments count
- attachments count
- upcoming appointment summary
- tenant data isolation

## Appointment and Resource Coverage

The seed creates:

```text
upcoming BOOKED appointment
past COMPLETED appointment
past CANCELLED appointment
Monday-Friday working hours
one upcoming resource block
```

This is enough to demo:

- `/appointments`
- `/appointments/:id`
- ticket detail appointment summaries
- resource availability
- resource blocks
- optimistic-lock edit flows after details are loaded

## Contact Claim Demo

The seed creates one unlinked contact and one active claim code.

Demo claim code:

```text
ACME2026
```

The contact identity is:

```text
email: guest.unlinked@demo.com
phone: +61466100999
```

## Attachments

The seeder inserts attachment metadata and writes small local text files under:

```text
app.upload.dir
```

Default:

```text
/tmp/multiapp/uploads
```

The storage key intentionally matches the current `LocalAttachmentStorage`
format so downloads can resolve the file.

## About R__seed.sql

`src/main/resources/db/migration/R__seed.sql` is no longer necessary for the
demo environment.

Currently it still runs in every Flyway-enabled profile because it is under the
default migration location:

```text
src/main/resources/db/migration
```

That means it will insert the old sample tenant/users even during `demo`.

Recommended cleanup:

1. Move old development-only seed data out of the default migration folder.
2. Use profile-specific Flyway locations if you still want SQL seed data for
   local development.
3. Keep `R__seed.sql` only for tiny reference data that is valid in every
   profile.

For this project, demo business data should stay in `DemoDataSeeder`.

## Known Schema Caveat

`V1__init.sql` currently has an `audit_log.entity_type` check constraint that
only lists:

```text
TICKET
APPOINTMENT
COMMENT
ATTACHMENT
USER
TENANT
```

But the Java enum also contains:

```text
MEMBERSHIP
CONTACT
CONTACT_CLAIM
RESOURCE_BLOCK
RESOURCE_WORKING_HOURS
```

Some normal API calls that write audit logs for these newer entity types may
fail until the database check constraint is updated.

The demo seeder avoids writing audit logs directly, so seed startup is not
blocked by this mismatch.
