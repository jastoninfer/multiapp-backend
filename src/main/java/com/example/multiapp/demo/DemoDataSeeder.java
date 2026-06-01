package com.example.multiapp.demo;

import com.example.multiapp.common.crypto.Hashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Component
@Profile({"demo", "demo-prod"})
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {
    private static final String SEED_NAME = "multiapp-demo-data";
    private static final String DEFAULT_SEED_VERSION = "v4";

    private static final UUID TENANT_PLATFORM_ADMIN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ACME = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID TENANT_BETA = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID TENANT_SUSPENDED = UUID.fromString("10000000-0000-0000-0000-000000000004");

    private static final UUID CONTACT_CUSTOMER_LINKED = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CONTACT_GUEST_UNLINKED = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CONTACT_PROPERTY_MANAGER = UUID.fromString("20000000-0000-0000-0000-000000000003");

    private static final UUID CLAIM_GUEST = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final String CLAIM_GUEST_CODE = "ACME2026";
    private static final String CLAIM_BETA_CODE = "BETA2026";
    private static final String CLAIM_EXPIRED_CODE = "OLD2026";
    private static final String CLAIM_CONSUMED_CODE = "USED2026";

    private static final UUID TICKET_HVAC = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_PRINTER = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID TICKET_LEAK = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID TICKET_BADGE = UUID.fromString("30000000-0000-0000-0000-000000000004");
    private static final UUID TICKET_GUEST = UUID.fromString("30000000-0000-0000-0000-000000000005");
    private static final UUID TICKET_BETA = UUID.fromString("30000000-0000-0000-0000-000000000006");

    private static final UUID APPT_HVAC = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID APPT_PRINTER_DONE = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID APPT_LEAK_CANCELLED = UUID.fromString("40000000-0000-0000-0000-000000000003");

    private static final UUID BLOCK_TRAINING = UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final UUID COMMENT_HVAC_PUBLIC = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID COMMENT_HVAC_INTERNAL = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID COMMENT_LEAK_PUBLIC = UUID.fromString("60000000-0000-0000-0000-000000000003");

    private static final UUID ATTACHMENT_HVAC = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID ATTACHMENT_LEAK = UUID.fromString("70000000-0000-0000-0000-000000000002");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Environment env;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        if (!hasDemoProfile()) {
            return;
        }
        tx.executeWithoutResult(status -> seed());
    }

    private void seed() {
        ensureHistoryTable();
        String seedVersion = env.getProperty("app.demo.seed.version", DEFAULT_SEED_VERSION);
        boolean force = env.getProperty("app.demo.seed.force", Boolean.class, false);
        if (!force && alreadyApplied(seedVersion)) {
            log.info("Demo seed {} {} already applied, skipping.", SEED_NAME, seedVersion);
            return;
        }

        String issuer = env.getProperty("app.demo.keycloak-issuer",
                env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "http://localhost:8080/realms/multiapp"));
        Map<String, DemoUser> users = loadKeycloakUsers(issuer);

        upsertUsers(users);
        upsertTenants();
        upsertMemberships(users);
        upsertContacts(users);
        upsertContactClaims(users);
        upsertWorkingHours(users);
        upsertResourceBlocks(users);
        upsertTickets(users);
        upsertComments(users);
        upsertAppointments(users);
        upsertAttachments(users);
        markApplied(seedVersion);
        log.info("Demo seed {} {} applied.", SEED_NAME, seedVersion);
    }

    private boolean hasDemoProfile() {
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        return profiles.contains("demo") || profiles.contains("demo-prod");
    }

    private void ensureHistoryTable() {
        jdbc.execute("""
            create table if not exists app.demo_seed_history(
                seed_name text primary key,
                seed_version text not null,
                applied_at timestamptz not null default now()
            )
            """);
    }

    private boolean alreadyApplied(String seedVersion) {
        Integer count = jdbc.queryForObject("""
            select count(*) from app.demo_seed_history
            where seed_name = ? and seed_version = ?
            """, Integer.class, SEED_NAME, seedVersion);
        return count != null && count > 0;
    }

    private void markApplied(String seedVersion) {
        jdbc.update("""
            insert into app.demo_seed_history(seed_name, seed_version, applied_at)
            values (?, ?, now())
            on conflict (seed_name) do update
                set seed_version = excluded.seed_version,
                    applied_at = excluded.applied_at
            """, SEED_NAME, seedVersion);
    }

    private Map<String, DemoUser> loadKeycloakUsers(String issuer) {
        Path importPath = Path.of(env.getProperty("app.demo.keycloak-import", "keycloak/import/multiapp-realm.json"));
        JsonNode root = readKeycloakImport(importPath);
        Map<String, DemoUser> users = new LinkedHashMap<>();
        JsonNode usersNode = root.path("users");
        if (!usersNode.isArray()) {
            throw new IllegalStateException("Keycloak import has no users array: " + importPath);
        }
        for (JsonNode node : usersNode) {
            String email = text(node, "email", text(node, "username", null));
            if (email == null || email.isBlank()) continue;
            String sub = text(node, "id", null);
            if (sub == null || sub.isBlank()) {
                throw new IllegalStateException("Demo Keycloak user missing id/sub: " + email);
            }
            String displayName = displayName(node, email);
            String phone = firstAttribute(node, "phone");
            boolean platformAdmin = "1".equals(firstAttribute(node, "is_platform_admin"))
                    || "true".equalsIgnoreCase(firstAttribute(node, "is_platform_admin"));
            users.put(email.toLowerCase(Locale.ROOT),
                    new DemoUser(issuer, sub, email.toLowerCase(Locale.ROOT), displayName, phone, platformAdmin));
        }
        requireUsers(users,
                "platform.admin@demo.com",
                "platform.ops@demo.com",
                "tenant.admin@acme.demo",
                "acme.admin2@demo.com",
                "agent@acme.demo",
                "acme.agent2@demo.com",
                "resource@acme.demo",
                "acme.resource2@demo.com",
                "acme.resource3@demo.com",
                "customer@acme.demo",
                "acme.customer2@demo.com",
                "acme.customer3@demo.com",
                "guest.customer@demo.com",
                "multi.member@demo.com",
                "beta.admin@demo.com",
                "beta.agent@demo.com",
                "beta.agent2@demo.com",
                "beta.resource@demo.com",
                "beta.resource2@demo.com",
                "beta.customer@demo.com");
        return users;
    }

    private JsonNode readKeycloakImport(Path importPath) {
        try {
            if (Files.exists(importPath)) {
                return objectMapper.readTree(importPath.toFile());
            }
            ClassPathResource fallback = new ClassPathResource(importPath.toString());
            if (fallback.exists()) {
                try (InputStream in = fallback.getInputStream()) {
                    return objectMapper.readTree(in);
                }
            }
            throw new IllegalStateException("Keycloak import not found: " + importPath.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Keycloak import: " + importPath.toAbsolutePath(), e);
        }
    }

    private void requireUsers(Map<String, DemoUser> users, String... emails) {
        for (String email : emails) {
            if (!users.containsKey(email)) {
                throw new IllegalStateException("Missing required demo Keycloak user: " + email);
            }
        }
    }

    private void upsertUsers(Map<String, DemoUser> users) {
        for (DemoUser user : users.values()) {
            UUID preferredLocalId = UUID.fromString(user.sub());
            jdbc.update("""
                insert into app.app_user(
                    id, issuer, keycloak_sub, email, display_name, phone, status, is_platform_admin
                ) values (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                on conflict (issuer, keycloak_sub) do update
                    set email = excluded.email,
                        display_name = excluded.display_name,
                        phone = excluded.phone,
                        status = 'ACTIVE',
                        is_platform_admin = excluded.is_platform_admin,
                        updated_at = now()
                """, preferredLocalId, user.issuer(), user.sub(), user.email(), user.displayName(),
                    user.phone(), user.platformAdmin());
            user.localId(queryRequiredUuid("""
                select id from app.app_user where issuer = ? and keycloak_sub = ?
                """, user.issuer(), user.sub()));
        }
    }

    private void upsertTenants() {
        upsertTenant(TENANT_PLATFORM_ADMIN,
                env.getProperty("app.platform.admin.tenant",
                        "__platform_admin"), "ACTIVE");
        upsertTenant(TENANT_ACME, "Acme Facilities", "ACTIVE");
        upsertTenant(TENANT_BETA, "Beta Clinic", "ACTIVE");
        upsertTenant(TENANT_SUSPENDED, "Suspended Tenant", "SUSPENDED");
    }

    private void upsertTenant(UUID id, String name, String status) {
        jdbc.update("""
            insert into app.tenant(id, name, status)
            values (?, ?, ?)
            on conflict (id) do update
                set name = excluded.name,
                    status = excluded.status,
                    updated_at = now()
            """, id, name, status);
    }

    private void upsertMemberships(Map<String, DemoUser> users) {
        UUID platform = user(users, "platform.admin@demo.com").localId();
        UUID platformOps = user(users, "platform.ops@demo.com").localId();
        UUID admin = user(users, "tenant.admin@acme.demo").localId();
        UUID acmeAdmin2 = user(users, "acme.admin2@demo.com").localId();
        UUID agent = user(users, "agent@acme.demo").localId();
        UUID acmeAgent2 = user(users, "acme.agent2@demo.com").localId();
        UUID resource = user(users, "resource@acme.demo").localId();
        UUID acmeResource2 = user(users, "acme.resource2@demo.com").localId();
        UUID acmeResource3 = user(users, "acme.resource3@demo.com").localId();
        UUID customer = user(users, "customer@acme.demo").localId();
        UUID acmeCustomer2 = user(users, "acme.customer2@demo.com").localId();
        UUID acmeCustomer3 = user(users, "acme.customer3@demo.com").localId();
        UUID multi = user(users, "multi.member@demo.com").localId();
        UUID betaAdmin = user(users, "beta.admin@demo.com").localId();
        UUID betaAgent = user(users, "beta.agent@demo.com").localId();
        UUID betaAgent2 = user(users, "beta.agent2@demo.com").localId();
        UUID betaResource = user(users, "beta.resource@demo.com").localId();
        UUID betaResource2 = user(users, "beta.resource2@demo.com").localId();
        UUID betaCustomer = user(users, "beta.customer@demo.com").localId();

        upsertMembership(TENANT_PLATFORM_ADMIN, platform, "ADMIN", true);
        upsertMembership(TENANT_PLATFORM_ADMIN, platformOps, "ADMIN", true);
        upsertMembership(TENANT_ACME, platform, "ADMIN", false);
        upsertMembership(TENANT_ACME, platformOps, "ADMIN", false);
        upsertMembership(TENANT_ACME, admin, "ADMIN", true);
        upsertMembership(TENANT_ACME, acmeAdmin2, "ADMIN", true);
        upsertMembership(TENANT_ACME, agent, "AGENT", true);
        upsertMembership(TENANT_ACME, acmeAgent2, "AGENT", true);
        upsertMembership(TENANT_ACME, resource, "RESOURCE_USER", true);
        upsertMembership(TENANT_ACME, acmeResource2, "RESOURCE_USER", true);
        upsertMembership(TENANT_ACME, acmeResource3, "RESOURCE_USER", true);
        upsertMembership(TENANT_ACME, customer, "CUSTOMER", true);
        upsertMembership(TENANT_ACME, acmeCustomer2, "CUSTOMER", true);
        upsertMembership(TENANT_ACME, acmeCustomer3, "CUSTOMER", true);
        upsertMembership(TENANT_ACME, multi, "AGENT", false);

        upsertMembership(TENANT_BETA, admin, "ADMIN", false);
        upsertMembership(TENANT_BETA, platform, "ADMIN", false);
        upsertMembership(TENANT_BETA, platformOps, "ADMIN", false);
        upsertMembership(TENANT_BETA, betaAdmin, "ADMIN", true);
        upsertMembership(TENANT_BETA, betaAgent, "AGENT", true);
        upsertMembership(TENANT_BETA, betaAgent2, "AGENT", true);
        upsertMembership(TENANT_BETA, betaResource, "RESOURCE_USER", true);
        upsertMembership(TENANT_BETA, betaResource2, "RESOURCE_USER", true);
        upsertMembership(TENANT_BETA, multi, "CUSTOMER", true);
        upsertMembership(TENANT_BETA, betaCustomer, "CUSTOMER", true);
        upsertMembership(TENANT_SUSPENDED, platform, "ADMIN", false);
        upsertMembership(TENANT_SUSPENDED, platformOps, "ADMIN", false);
    }

    private void upsertMembership(UUID tenantId, UUID userId, String role, boolean isDefault) {
        if (isDefault) {
            jdbc.update("update app.tenant_membership set is_default = false where user_id = ?", userId);
        }
        jdbc.update("""
            insert into app.tenant_membership(tenant_id, user_id, role, is_default)
            values (?, ?, ?, ?)
            on conflict (tenant_id, user_id) do update
                set role = excluded.role,
                    is_default = excluded.is_default
            """, tenantId, userId, role, isDefault);
    }

    private void upsertContacts(Map<String, DemoUser> users) {
        UUID admin = user(users, "tenant.admin@acme.demo").localId();
        UUID customer = user(users, "customer@acme.demo").localId();
        UUID acmeCustomer2 = user(users, "acme.customer2@demo.com").localId();
        UUID acmeCustomer3 = user(users, "acme.customer3@demo.com").localId();
        UUID betaAdmin = user(users, "beta.admin@demo.com").localId();
        UUID betaCustomer = user(users, "beta.customer@demo.com").localId();
        UUID multi = user(users, "multi.member@demo.com").localId();

        upsertContact(TENANT_ACME, CONTACT_CUSTOMER_LINKED, "PERSON",
                "customer@acme.demo", "+61466100105", "Sophie Taylor",
                customer, admin);
        upsertContact(TENANT_ACME, CONTACT_GUEST_UNLINKED, "PERSON",
                "guest.unlinked@demo.com", "+61466100999", "Unlinked Guest Contact",
                null, admin);
        upsertContact(TENANT_ACME, CONTACT_PROPERTY_MANAGER, "PERSON",
                "manager@building.demo", "+61466100888", "Building Manager",
                null, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 4), "PERSON",
                "emily.davis.contact@acme.demo", "+61466100206", "Emily Davis",
                acmeCustomer2, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 5), "PERSON",
                "mason.hill.contact@acme.demo", "+61466100207", "Mason Hill",
                acmeCustomer3, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 6), "ORG",
                "facilities.vendor@demo.com", "+61466100881", "Northside Facilities Vendor",
                null, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 7), "PERSON",
                "security.contractor@demo.com", "+61466100882", "Security Contractor",
                null, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 8), "PERSON",
                "afterhours.cleaner@demo.com", "+61466100883", "After Hours Cleaner",
                null, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 9), "ORG",
                "hvac.vendor@demo.com", "+61466100884", "HVAC Vendor",
                null, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 10), "PERSON",
                "expired.claim@demo.com", "+61466100885", "Expired Claim Contact",
                null, admin);
        upsertContact(TENANT_ACME, demoUuid(20000000, 11), "PERSON",
                "consumed.claim@demo.com", "+61466100886", "Consumed Claim Contact",
                customer, admin);

        upsertContact(TENANT_BETA, demoUuid(20000000, 101), "PERSON",
                "benjamin.adams.contact@beta.demo", "+61466100213", "Benjamin Adams",
                betaCustomer, betaAdmin);
        upsertContact(TENANT_BETA, demoUuid(20000000, 102), "PERSON",
                "ava.beta.contact@demo.com", "+61466100107", "Ava Wilson",
                multi, betaAdmin);
        upsertContact(TENANT_BETA, demoUuid(20000000, 103), "ORG",
                "imaging.vendor@beta.demo", "+61466100701", "Imaging Equipment Vendor",
                null, betaAdmin);
        upsertContact(TENANT_BETA, demoUuid(20000000, 104), "PERSON",
                "clinic.manager@beta.demo", "+61466100702", "Clinic Manager",
                null, betaAdmin);
        upsertContact(TENANT_BETA, demoUuid(20000000, 105), "PERSON",
                "beta.unlinked@demo.com", "+61466100703", "Beta Unlinked Contact",
                null, betaAdmin);
        upsertContact(TENANT_BETA, demoUuid(20000000, 106), "PERSON",
                "beta.afterhours@demo.com", "+61466100704", "Beta After Hours Contact",
                null, betaAdmin);
    }

    private void upsertContact(UUID tenantId, UUID id, String type, String email, String phone,
                               String displayName, UUID linkedUserId, UUID createdByUserId) {
        jdbc.update("""
            insert into app.contact(
                tenant_id, id, contact_type, email, phone, email_normalized, phone_normalized,
                display_name, linked_user_id, created_by_user_id
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, id) do update
                set contact_type = excluded.contact_type,
                    email = excluded.email,
                    phone = excluded.phone,
                    email_normalized = excluded.email_normalized,
                    phone_normalized = excluded.phone_normalized,
                    display_name = excluded.display_name,
                    linked_user_id = excluded.linked_user_id,
                    updated_at = now()
            """, tenantId, id, type, email, phone, normalizeEmail(email), phone,
                displayName, linkedUserId, createdByUserId);
    }

    private void upsertContactClaims(Map<String, DemoUser> users) {
        UUID admin = user(users, "tenant.admin@acme.demo").localId();
        UUID customer = user(users, "customer@acme.demo").localId();
        UUID betaAdmin = user(users, "beta.admin@demo.com").localId();

        upsertContactClaim(TENANT_ACME, CLAIM_GUEST, CONTACT_GUEST_UNLINKED, CLAIM_GUEST_CODE,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(30), null, null, admin);
        upsertContactClaim(TENANT_ACME, demoUuid(21000000, 2), demoUuid(20000000, 10), CLAIM_EXPIRED_CODE,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1), null, null, admin);
        upsertContactClaim(TENANT_ACME, demoUuid(21000000, 3), demoUuid(20000000, 11), CLAIM_CONSUMED_CODE,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(10), OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                customer, admin);
        upsertContactClaim(TENANT_BETA, demoUuid(21000000, 101), demoUuid(20000000, 105), CLAIM_BETA_CODE,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(30), null, null, betaAdmin);
    }

    private void upsertContactClaim(UUID tenantId, UUID id, UUID contactId, String code,
                                    OffsetDateTime expiresAt, OffsetDateTime consumedAt,
                                    UUID consumedByUserId, UUID createdByUserId) {
        OffsetDateTime createdAt = contactClaimCreatedAt(expiresAt, consumedAt);
        jdbc.update("""
            insert into app.contact_claim(
                tenant_id, id, contact_id, code_hash, expires_at, consumed_at,
                consumed_by_user_id, created_by_user_id, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, id) do update
                set code_hash = excluded.code_hash,
                    expires_at = excluded.expires_at,
                    consumed_at = excluded.consumed_at,
                    consumed_by_user_id = excluded.consumed_by_user_id,
                    created_at = excluded.created_at,
                    attempts = 0,
                    last_attempt_at = null
            """, tenantId, id, contactId, Hashing.sha256Hex(code), expiresAt, consumedAt,
                consumedByUserId, createdByUserId, createdAt);
    }

    private OffsetDateTime contactClaimCreatedAt(OffsetDateTime expiresAt, OffsetDateTime consumedAt) {
        if (consumedAt != null) {
            return consumedAt.minusDays(3);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (expiresAt.isBefore(now)) {
            return expiresAt.minusDays(30);
        }
        return now.minusDays(1);
    }

    private void upsertWorkingHours(Map<String, DemoUser> users) {
        upsertWeekdayHours(TENANT_ACME, user(users, "resource@acme.demo").localId(), "09:00", "17:00");
        upsertWeekdayHours(TENANT_ACME, user(users, "acme.resource2@demo.com").localId(), "08:00", "16:00");
        upsertWorkingHoursForDays(TENANT_ACME, user(users, "acme.resource3@demo.com").localId(),
                new int[]{2, 3, 4, 5, 6}, "10:00", "18:00", "Australia/Adelaide");
        upsertWeekdayHours(TENANT_BETA, user(users, "beta.resource@demo.com").localId(), "09:00", "17:00");
        upsertWorkingHoursForDays(TENANT_BETA, user(users, "beta.resource2@demo.com").localId(),
                new int[]{1, 2, 3, 4}, "07:30", "15:30", "Australia/Adelaide");
    }

    private void upsertWeekdayHours(UUID tenantId, UUID resourceUserId, String start, String end) {
        upsertWorkingHoursForDays(tenantId, resourceUserId, new int[]{1, 2, 3, 4, 5},
                start, end, "Australia/Adelaide");
    }

    private void upsertWorkingHoursForDays(UUID tenantId, UUID resourceUserId, int[] days,
                                           String start, String end, String timezone) {
        for (int day = 1; day <= 5; day++) {
            jdbc.update("""
                delete from app.resource_working_hours
                where tenant_id = ? and resource_user_id = ? and day_of_week = ?
                """, tenantId, resourceUserId, day);
        }
        for (int day = 6; day <= 7; day++) {
            jdbc.update("""
                delete from app.resource_working_hours
                where tenant_id = ? and resource_user_id = ? and day_of_week = ?
                """, tenantId, resourceUserId, day);
        }
        for (int day : days) {
            jdbc.update("""
                insert into app.resource_working_hours(
                    tenant_id, resource_user_id, day_of_week, start_local, end_local, timezone
                ) values (?, ?, ?, cast(? as time), cast(? as time), ?)
                on conflict (tenant_id, resource_user_id, day_of_week) do update
                    set start_local = excluded.start_local,
                        end_local = excluded.end_local,
                        timezone = excluded.timezone
                """, tenantId, resourceUserId, day, start, end, timezone);
        }
    }

    private void upsertResourceBlocks(Map<String, DemoUser> users) {
        UUID acmeResource = user(users, "resource@acme.demo").localId();
        UUID acmeResource2 = user(users, "acme.resource2@demo.com").localId();
        UUID acmeResource3 = user(users, "acme.resource3@demo.com").localId();
        UUID betaResource = user(users, "beta.resource@demo.com").localId();
        UUID betaResource2 = user(users, "beta.resource2@demo.com").localId();

        upsertResourceBlock(TENANT_ACME, BLOCK_TRAINING, acmeResource,
                nextResourceWorkingHour(TENANT_ACME, acmeResource, 2, 12, 1), 1, "Safety training");
        upsertResourceBlock(TENANT_ACME, demoUuid(50000000, 2), acmeResource,
                nextResourceWorkingHour(TENANT_ACME, acmeResource, 5, 15, 2), 2, "Parts pickup");
        upsertResourceBlock(TENANT_ACME, demoUuid(50000000, 3), acmeResource2,
                nextResourceWorkingHour(TENANT_ACME, acmeResource2, 1, 12, 1), 1, "Lunch and travel buffer");
        upsertResourceBlock(TENANT_ACME, demoUuid(50000000, 4), acmeResource2,
                nextResourceWorkingHour(TENANT_ACME, acmeResource2, 8, 8, 8), 8, "Annual leave");
        upsertResourceBlock(TENANT_ACME, demoUuid(50000000, 5), acmeResource3,
                nextResourceWorkingHour(TENANT_ACME, acmeResource3, 3, 14, 2), 2, "Vendor coordination");
        upsertResourceBlock(TENANT_BETA, demoUuid(50000000, 101), betaResource,
                nextResourceWorkingHour(TENANT_BETA, betaResource, 2, 11, 1), 1, "Clinic safety briefing");
        upsertResourceBlock(TENANT_BETA, demoUuid(50000000, 102), betaResource2,
                nextResourceWorkingHour(TENANT_BETA, betaResource2, 4, 13, 2), 2, "Equipment calibration");
        upsertResourceBlock(TENANT_BETA, demoUuid(50000000, 103), user(users, "beta.resource2@demo.com").localId(),
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 2, 10, 1),
                1, "Past training block");
    }

    private void upsertResourceBlock(UUID tenantId, UUID id, UUID resourceUserId,
                                     OffsetDateTime start, int hours, String reason) {
        jdbc.update("""
            insert into app.resource_block(
                tenant_id, id, resource_user_id, start_at, end_at, reason
            ) values (?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, id) do update
                set resource_user_id = excluded.resource_user_id,
                    start_at = excluded.start_at,
                    end_at = excluded.end_at,
                    reason = excluded.reason,
                    deleted_at = null,
                    updated_at = now()
            """, tenantId, id, resourceUserId, start, start.plusHours(hours), reason);
    }

    private void upsertTickets(Map<String, DemoUser> users) {
        UUID admin = user(users, "tenant.admin@acme.demo").localId();
        UUID acmeAdmin2 = user(users, "acme.admin2@demo.com").localId();
        UUID agent = user(users, "agent@acme.demo").localId();
        UUID acmeAgent2 = user(users, "acme.agent2@demo.com").localId();
        UUID customer = user(users, "customer@acme.demo").localId();
        UUID acmeCustomer2 = user(users, "acme.customer2@demo.com").localId();
        UUID acmeCustomer3 = user(users, "acme.customer3@demo.com").localId();
        UUID multi = user(users, "multi.member@demo.com").localId();
        UUID betaAdmin = user(users, "beta.admin@demo.com").localId();
        UUID betaAgent = user(users, "beta.agent@demo.com").localId();
        UUID betaAgent2 = user(users, "beta.agent2@demo.com").localId();
        UUID betaCustomer = user(users, "beta.customer@demo.com").localId();

        upsertTicket(TENANT_ACME, TICKET_HVAC, customer, customer, null, agent,
                "IN_PROGRESS", "URGENT", "INCIDENT",
                "Air conditioner not working",
                "The reception area is getting very hot and customers are waiting.",
                "Reception, Level 1");
        upsertTicket(TENANT_ACME, TICKET_PRINTER, admin, null, CONTACT_PROPERTY_MANAGER, agent,
                "CLOSED", "MEDIUM", "SERVICE_REQUEST",
                "Printer setup request",
                "Install and configure the shared printer near the admin desk.",
                "Admin office");
        upsertTicket(TENANT_ACME, TICKET_LEAK, customer, customer, null, agent,
                "NEW", "HIGH", "INCIDENT",
                "Water leak in meeting room",
                "Water is dripping near the projector wall.",
                "Meeting Room 3B");
        upsertTicket(TENANT_ACME, TICKET_BADGE, customer, customer, null, null,
                "CLOSED", "LOW", "SERVICE_REQUEST",
                "Badge access issue",
                "Access badge was not opening the side entrance.",
                "Side entrance");
        upsertTicket(TENANT_ACME, TICKET_GUEST, admin, null, CONTACT_GUEST_UNLINKED, agent,
                "REOPENED", "HIGH", "INCIDENT",
                "Guest Wi-Fi keeps dropping",
                "External visitor reported repeated Wi-Fi disconnects during a workshop.",
                "Training room");
        upsertTicket(TENANT_BETA, TICKET_BETA, multi, multi, null, null,
                "NEW", "MEDIUM", "SERVICE_REQUEST",
                "Beta tenant onboarding question",
                "This ticket exists to verify tenant switching and data isolation.",
                "Beta front desk");

        upsertTicket(TENANT_ACME, demoUuid(30000000, 7), acmeCustomer2, acmeCustomer2, null, acmeAgent2,
                "IN_PROGRESS", "HIGH", "INCIDENT",
                "Elevator noise on level 4",
                "The lift makes a grinding sound when stopping at level 4.",
                "Lift lobby, Level 4");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 8), acmeAdmin2, null, demoUuid(20000000, 6), acmeAgent2,
                "NEW", "MEDIUM", "SERVICE_REQUEST",
                "Vendor access for maintenance",
                "Facilities vendor needs access for scheduled maintenance.",
                "Loading dock");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 9), acmeCustomer3, acmeCustomer3, null, null,
                "NEW", "LOW", "SERVICE_REQUEST",
                "Desk lamp replacement",
                "Desk lamp flickers intermittently.",
                "Workspace 5C");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 10), agent, customer, null, agent,
                "CLOSED", "LOW", "SERVICE_REQUEST",
                "Keyboard replacement",
                "Customer reported sticky keys on the front desk keyboard.",
                "Reception desk");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 11), admin, null, demoUuid(20000000, 7), agent,
                "IN_PROGRESS", "MEDIUM", "INCIDENT",
                "Security camera offline",
                "External security contractor reported one offline camera.",
                "Car park entrance");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 12), acmeCustomer2, acmeCustomer2, null, null,
                "REOPENED", "URGENT", "INCIDENT",
                "Heating failed again",
                "The heating issue returned after the previous service visit.",
                "Suite 210");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 13), acmeAdmin2, null, demoUuid(20000000, 8), acmeAgent2,
                "CLOSED", "MEDIUM", "SERVICE_REQUEST",
                "After-hours cleaning access",
                "Cleaner needed temporary access after the building lockout.",
                "North entrance");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 14), customer, customer, null, agent,
                "IN_PROGRESS", "MEDIUM", "SERVICE_REQUEST",
                "Conference room setup",
                "Prepare room layout and AV for the client presentation.",
                "Conference Room A");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 15), admin, null, demoUuid(20000000, 9), acmeAgent2,
                "NEW", "URGENT", "INCIDENT",
                "HVAC vendor emergency follow-up",
                "Vendor reported an urgent compressor fault requiring inspection.",
                "Rooftop plant");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 16), acmeCustomer3, acmeCustomer3, null, agent,
                "CLOSED", "HIGH", "INCIDENT",
                "Water stain near kitchen",
                "A stain appeared after last week's leak.",
                "Shared kitchen");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 17), multi, customer, null, multi,
                "IN_PROGRESS", "LOW", "SERVICE_REQUEST",
                "Nameplate update",
                "Update reception nameplate after team change.",
                "Reception");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 18), acmeAdmin2, null, demoUuid(20000000, 3), null,
                "NEW", "HIGH", "INCIDENT",
                "Building manager escalation",
                "Manager reported intermittent building access failures.",
                "Main lobby");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 19), customer, customer, null, acmeAgent2,
                "REOPENED", "MEDIUM", "SERVICE_REQUEST",
                "Monitor arm adjustment",
                "The monitor arm is loose again after adjustment.",
                "Reception workstation");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 20), acmeCustomer2, acmeCustomer2, null, agent,
                "CLOSED", "URGENT", "INCIDENT",
                "Electrical smell near printer",
                "A burning smell was noticed near the printer cabinet.",
                "Print room");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 21), admin, null, demoUuid(20000000, 2), agent,
                "IN_PROGRESS", "HIGH", "INCIDENT",
                "Guest network outage",
                "External guest contact reported network outage during workshop.",
                "Training room");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 22), acmeCustomer3, acmeCustomer3, null, null,
                "NEW", "MEDIUM", "SERVICE_REQUEST",
                "Move request for two desks",
                "Move two desks closer to the window area.",
                "Level 2 open area");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 23), admin, null, demoUuid(20000000, 6), acmeAgent2,
                "CLOSED", "LOW", "SERVICE_REQUEST",
                "Vendor invoice document request",
                "Vendor requested a copy of completed maintenance paperwork.",
                "Facilities office");
        upsertTicket(TENANT_ACME, demoUuid(30000000, 24), customer, customer, null, agent,
                "IN_PROGRESS", "HIGH", "INCIDENT",
                "Reception tablet not charging",
                "The sign-in tablet no longer charges on the dock.",
                "Reception");

        upsertTicket(TENANT_BETA, demoUuid(30000000, 25), betaCustomer, betaCustomer, null, betaAgent,
                "IN_PROGRESS", "HIGH", "INCIDENT",
                "Exam room light flickering",
                "Light flickers during patient appointments.",
                "Exam Room 2");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 26), betaAdmin, null, demoUuid(20000000, 103), betaAgent,
                "NEW", "URGENT", "INCIDENT",
                "Imaging device coolant alert",
                "Vendor reported a coolant warning on imaging equipment.",
                "Imaging Room");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 27), multi, multi, null, betaAgent2,
                "CLOSED", "LOW", "SERVICE_REQUEST",
                "Waiting room signage update",
                "Update signage for the new check-in process.",
                "Waiting room");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 28), betaAdmin, null, demoUuid(20000000, 104), betaAgent,
                "IN_PROGRESS", "MEDIUM", "SERVICE_REQUEST",
                "Clinic manager onboarding setup",
                "Prepare workspace and access for new clinic manager.",
                "Admin desk");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 29), betaCustomer, betaCustomer, null, null,
                "NEW", "MEDIUM", "SERVICE_REQUEST",
                "Patient tablet replacement",
                "Tablet screen is cracked and needs replacement.",
                "Reception");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 30), betaAdmin, null, demoUuid(20000000, 105), betaAgent2,
                "REOPENED", "HIGH", "INCIDENT",
                "External requester Wi-Fi issue",
                "Unlinked external contact reports recurring Wi-Fi drops.",
                "Training room");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 31), betaCustomer, betaCustomer, null, betaAgent,
                "CLOSED", "URGENT", "INCIDENT",
                "Water leak under sink",
                "Leak under sink disrupted morning appointments.",
                "Treatment Room 1");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 32), betaAdmin, null, demoUuid(20000000, 106), null,
                "NEW", "LOW", "SERVICE_REQUEST",
                "After-hours access request",
                "Temporary access required for evening cleaning.",
                "Back entrance");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 33), multi, multi, null, betaAgent2,
                "IN_PROGRESS", "MEDIUM", "SERVICE_REQUEST",
                "Storage room shelving",
                "Install additional shelving for supplies.",
                "Storage Room B");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 34), betaCustomer, betaCustomer, null, betaAgent,
                "CLOSED", "MEDIUM", "SERVICE_REQUEST",
                "Appointment kiosk rebooting",
                "Self check-in kiosk rebooted twice yesterday.",
                "Front desk");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 35), betaAdmin, null, demoUuid(20000000, 103), betaAgent2,
                "IN_PROGRESS", "LOW", "SERVICE_REQUEST",
                "Vendor documentation upload",
                "Upload service report for imaging vendor visit.",
                "Admin office");
        upsertTicket(TENANT_BETA, demoUuid(30000000, 36), betaCustomer, betaCustomer, null, null,
                "NEW", "HIGH", "INCIDENT",
                "Cold room temperature alert",
                "Temperature monitor shows the cold room above threshold.",
                "Cold Storage");
    }

    private void upsertTicket(UUID tenantId, UUID id, UUID createdByUserId, UUID requesterUserId,
                              UUID requesterContactId, UUID ownerUserId, String status, String priority,
                              String ticketType, String title, String description, String locationText) {
        validateTicketRequester(tenantId, id, requesterUserId, requesterContactId);
        OffsetDateTime now = nowUtc();
        OffsetDateTime createdAt = demoTicketCreatedAt(id, now);
        OffsetDateTime firstResponseAt = firstResponseAtFor(status, createdAt);
        OffsetDateTime closedAt = closedAtFor(status, createdAt);
        OffsetDateTime updatedAt = ticketUpdatedAtFor(status, createdAt, firstResponseAt, closedAt);
        jdbc.update("""
            insert into app.ticket(
                tenant_id, id, created_by_user_id, requester_user_id, requester_contact_id,
                owner_user_id, status, priority, ticket_type, title, description, location_text,
                first_response_at, closed_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, id) do update
                set created_by_user_id = excluded.created_by_user_id,
                    requester_user_id = excluded.requester_user_id,
                    requester_contact_id = excluded.requester_contact_id,
                    owner_user_id = excluded.owner_user_id,
                    status = excluded.status,
                    priority = excluded.priority,
                    ticket_type = excluded.ticket_type,
                    title = excluded.title,
                    description = excluded.description,
                    location_text = excluded.location_text,
                    first_response_at = excluded.first_response_at,
                    closed_at = excluded.closed_at,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at
            """, tenantId, id, createdByUserId, requesterUserId, requesterContactId, ownerUserId,
                status, priority, ticketType, title, description, locationText, firstResponseAt, closedAt,
                createdAt, updatedAt);
    }

    private OffsetDateTime firstResponseAtFor(String status, OffsetDateTime createdAt) {
        return switch (status) {
            case "NEW" -> null;
            case "IN_PROGRESS", "CLOSED", "REOPENED" ->
                    createdAt.plusHours(2);
            default -> throw new IllegalArgumentException("Unsupported demo ticket status: " + status);
        };
    }

    private OffsetDateTime closedAtFor(String status, OffsetDateTime createdAt) {
        return switch (status) {
            case "CLOSED" -> createdAt.plusHours(8);
            case "NEW", "IN_PROGRESS", "REOPENED" -> null;
            default -> throw new IllegalArgumentException("Unsupported demo ticket status: " + status);
        };
    }

    private OffsetDateTime ticketUpdatedAtFor(String status, OffsetDateTime createdAt,
                                              OffsetDateTime firstResponseAt, OffsetDateTime closedAt) {
        return switch (status) {
            case "NEW" -> createdAt.plusMinutes(30);
            case "IN_PROGRESS", "REOPENED" -> firstResponseAt.plusHours(1);
            case "CLOSED" -> closedAt;
            default -> throw new IllegalArgumentException("Unsupported demo ticket status: " + status);
        };
    }

    private void upsertComments(Map<String, DemoUser> users) {
        UUID customer = user(users, "customer@acme.demo").localId();
        UUID acmeCustomer2 = user(users, "acme.customer2@demo.com").localId();
        UUID acmeCustomer3 = user(users, "acme.customer3@demo.com").localId();
        UUID agent = user(users, "agent@acme.demo").localId();
        UUID acmeAgent2 = user(users, "acme.agent2@demo.com").localId();
        UUID admin = user(users, "tenant.admin@acme.demo").localId();
        UUID acmeResource = user(users, "resource@acme.demo").localId();
        UUID acmeResource2 = user(users, "acme.resource2@demo.com").localId();
        UUID acmeResource3 = user(users, "acme.resource3@demo.com").localId();
        UUID multi = user(users, "multi.member@demo.com").localId();
        UUID betaCustomer = user(users, "beta.customer@demo.com").localId();
        UUID betaAgent = user(users, "beta.agent@demo.com").localId();
        UUID betaAgent2 = user(users, "beta.agent2@demo.com").localId();
        UUID betaAdmin = user(users, "beta.admin@demo.com").localId();
        UUID betaResource = user(users, "beta.resource@demo.com").localId();
        UUID betaResource2 = user(users, "beta.resource2@demo.com").localId();

        upsertComment(TENANT_ACME, COMMENT_HVAC_PUBLIC, TICKET_HVAC, customer, "PUBLIC",
                "The lobby is uncomfortable for visitors. Please prioritize this.");
        upsertComment(TENANT_ACME, COMMENT_HVAC_INTERNAL, TICKET_HVAC, agent, "INTERNAL",
                "Bring replacement thermostat and check rooftop unit error code.");
        upsertComment(TENANT_ACME, COMMENT_LEAK_PUBLIC, TICKET_LEAK, agent, "PUBLIC",
                "We have assigned a technician and will inspect the room today.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 4), TICKET_LEAK, customer, "PUBLIC",
                "Please avoid using the projector until the leak is checked.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 5), demoUuid(30000000, 7), acmeCustomer2, "PUBLIC",
                "The sound is louder in the morning peak period.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 6), demoUuid(30000000, 7), acmeAgent2, "INTERNAL",
                "Check motor room access before dispatch.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 7), demoUuid(30000000, 11), agent, "INTERNAL",
                "Camera 3 may need a replacement PoE injector.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 8), demoUuid(30000000, 12), acmeCustomer2, "PUBLIC",
                "This is affecting the morning shift again.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 9), demoUuid(30000000, 12), admin, "INTERNAL",
                "Escalate if the repeat fault is confirmed.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 10), demoUuid(30000000, 14), customer, "PUBLIC",
                "The room must be ready before 2 PM.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 11), demoUuid(30000000, 15), acmeAgent2, "INTERNAL",
                "Vendor requested rooftop access and isolation permit.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 12), demoUuid(30000000, 16), acmeCustomer3, "PUBLIC",
                "The stain is dry now but still visible.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 13), demoUuid(30000000, 18), admin, "PUBLIC",
                "We are checking access logs with the building manager.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 14), demoUuid(30000000, 20), agent, "INTERNAL",
                "Closed after isolating the faulty power board.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 15), demoUuid(30000000, 21), customer, "PUBLIC",
                "Workshop attendees still need guest access this afternoon.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 16), demoUuid(30000000, 24), agent, "PUBLIC",
                "A replacement charging dock has been ordered.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 17), TICKET_PRINTER, agent, "PUBLIC",
                "Printer drivers were installed and a test page printed successfully.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 18), TICKET_PRINTER, admin, "INTERNAL",
                "Closed after confirming the property manager can print from the shared workstation.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 19), TICKET_BADGE, customer, "PUBLIC",
                "The side entrance works again after the badge was reissued.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 20), TICKET_BADGE, agent, "INTERNAL",
                "Old badge token was revoked before closing the request.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 21), TICKET_GUEST, admin, "PUBLIC",
                "The guest Wi-Fi issue has returned during another visitor workshop.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 22), TICKET_GUEST, agent, "INTERNAL",
                "Check DHCP lease exhaustion and compare against last week's access point logs.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 23), demoUuid(30000000, 8), acmeAgent2, "PUBLIC",
                "Vendor access is pending confirmation from building security.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 24), demoUuid(30000000, 9), acmeCustomer3, "PUBLIC",
                "Replacement can wait until the regular facilities round.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 25), demoUuid(30000000, 10), agent, "PUBLIC",
                "Keyboard was swapped and the old unit was labelled for disposal.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 26), demoUuid(30000000, 10), admin, "INTERNAL",
                "Low-priority replacement handled from spare stock.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 27), demoUuid(30000000, 13), acmeAgent2, "PUBLIC",
                "Temporary access window was granted and expired automatically.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 28), demoUuid(30000000, 17), agent, "INTERNAL",
                "Nameplate design approved; waiting for print batch.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 29), demoUuid(30000000, 19), customer, "PUBLIC",
                "The monitor arm slipped again after being adjusted.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 30), demoUuid(30000000, 19), acmeAgent2, "INTERNAL",
                "Likely needs replacement clamp rather than another tightening.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 31), demoUuid(30000000, 22), acmeCustomer3, "PUBLIC",
                "Please move the desks before the new starters arrive.");
        upsertComment(TENANT_ACME, demoUuid(60000000, 32), demoUuid(30000000, 23), acmeAgent2, "PUBLIC",
                "Maintenance paperwork has been sent to the vendor contact.");

        upsertComment(TENANT_BETA, demoUuid(60000000, 101), demoUuid(30000000, 25), betaCustomer, "PUBLIC",
                "The flicker happens every few minutes.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 102), demoUuid(30000000, 25), betaAgent, "INTERNAL",
                "Check ballast and keep spare fitting in the van.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 103), demoUuid(30000000, 26), betaAgent, "INTERNAL",
                "Vendor callback required before any on-site work.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 104), demoUuid(30000000, 28), betaAdmin, "PUBLIC",
                "Please complete setup before the Monday onboarding.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 105), demoUuid(30000000, 30), betaAgent2, "PUBLIC",
                "We will retest signal strength after clinic hours.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 106), demoUuid(30000000, 31), betaAgent, "INTERNAL",
                "Closed after replacing the sink trap seal.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 107), demoUuid(30000000, 33), betaAgent2, "PUBLIC",
                "Shelving parts are ready for installation.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 108), demoUuid(30000000, 36), betaCustomer, "PUBLIC",
                "Please treat this as urgent because samples are stored there.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 109), TICKET_BETA, betaAdmin, "PUBLIC",
                "Onboarding answer was shared with the front desk team.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 110), TICKET_BETA, betaAgent, "INTERNAL",
                "Keep this ticket for tenant switching and visibility regression checks.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 111), demoUuid(30000000, 27), betaAgent2, "PUBLIC",
                "Signage was updated after hours to avoid disrupting patients.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 112), demoUuid(30000000, 29), betaCustomer, "PUBLIC",
                "The cracked tablet is still usable but should be replaced soon.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 113), demoUuid(30000000, 29), betaAgent, "INTERNAL",
                "Check whether this can be handled from spare tablet inventory.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 114), demoUuid(30000000, 32), betaAdmin, "PUBLIC",
                "Cleaner needs the access window for Thursday evening only.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 115), demoUuid(30000000, 34), betaAgent, "PUBLIC",
                "Firmware update resolved the reboot loop.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 116), demoUuid(30000000, 35), betaAgent2, "INTERNAL",
                "Attach vendor report after the booked appointment is completed.");
        upsertComment(TENANT_BETA, demoUuid(60000000, 117), demoUuid(30000000, 36), betaResource2, "INTERNAL",
                "I checked the temperature log format and attached the sample we need for comparison.");

        upsertDemoCommentThread(TENANT_ACME, TICKET_HVAC, 12, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, TICKET_PRINTER, 12, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, TICKET_LEAK, 1, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, TICKET_BADGE, 12, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, TICKET_GUEST, 12, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_BETA, TICKET_BETA, 1, false,
                multi, betaAgent, betaAdmin, betaResource);

        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 7), 12, true,
                acmeCustomer2, acmeAgent2, admin, acmeResource2);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 8), 1, true,
                acmeCustomer2, acmeAgent2, admin, acmeResource3);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 9), 1, false,
                acmeCustomer3, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 10), 12, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 11), 12, true,
                customer, agent, admin, acmeResource2);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 12), 12, true,
                acmeCustomer2, acmeAgent2, admin, acmeResource3);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 13), 12, true,
                customer, acmeAgent2, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 14), 12, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 15), 1, false,
                customer, acmeAgent2, admin, acmeResource3);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 16), 12, true,
                acmeCustomer3, agent, admin, acmeResource2);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 17), 12, true,
                customer, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 18), 1, true,
                customer, acmeAgent2, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 19), 12, true,
                customer, acmeAgent2, admin, acmeResource2);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 20), 12, true,
                acmeCustomer2, agent, admin, acmeResource2);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 21), 12, true,
                customer, agent, admin, acmeResource3);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 22), 1, true,
                acmeCustomer3, agent, admin, acmeResource);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 23), 12, true,
                customer, acmeAgent2, admin, acmeResource2);
        upsertDemoCommentThread(TENANT_ACME, demoUuid(30000000, 24), 12, true,
                customer, agent, admin, acmeResource);

        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 25), 12, true,
                betaCustomer, betaAgent, betaAdmin, betaResource);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 26), 1, false,
                betaCustomer, betaAgent, betaAdmin, betaResource2);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 27), 12, true,
                multi, betaAgent2, betaAdmin, betaResource);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 28), 12, true,
                betaCustomer, betaAgent, betaAdmin, betaResource2);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 29), 1, false,
                betaCustomer, betaAgent, betaAdmin, betaResource);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 30), 12, true,
                betaCustomer, betaAgent2, betaAdmin, betaResource);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 31), 12, true,
                betaCustomer, betaAgent, betaAdmin, betaResource2);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 32), 1, true,
                betaCustomer, betaAgent2, betaAdmin, betaResource2);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 33), 12, true,
                multi, betaAgent2, betaAdmin, betaResource);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 34), 12, true,
                betaCustomer, betaAgent, betaAdmin, betaResource2);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 35), 12, true,
                betaCustomer, betaAgent2, betaAdmin, betaResource);
        upsertDemoCommentThread(TENANT_BETA, demoUuid(30000000, 36), 1, true,
                betaCustomer, betaAgent, betaAdmin, betaResource2);
    }

    private void upsertDemoCommentThread(UUID tenantId, UUID ticketId, int commentCount,
                                         boolean includeInternal, UUID customerUserId,
                                         UUID agentUserId, UUID adminUserId, UUID resourceUserId) {
        int ticketOrdinal = demoOrdinal(ticketId);
        for (int i = 1; i <= commentCount; i++) {
            boolean internal = includeInternal && (i == 1 || i % 3 == 0);
            DemoCommentAuthor author = demoCommentAuthor(i, internal, customerUserId,
                    agentUserId, adminUserId, resourceUserId);
            upsertComment(tenantId, demoUuid(61000000, ticketOrdinal * 100 + i), ticketId,
                    author.userId(), internal ? "INTERNAL" : "PUBLIC",
                    demoCommentBody(i, internal, author.role()));
        }
    }

    private DemoCommentAuthor demoCommentAuthor(int index, boolean internal,
                                                UUID customerUserId, UUID agentUserId,
                                                UUID adminUserId, UUID resourceUserId) {
        if (internal) {
            return switch (index % 3) {
                case 0 -> new DemoCommentAuthor(resourceUserId, "resource");
                case 1 -> new DemoCommentAuthor(agentUserId, "agent");
                default -> new DemoCommentAuthor(adminUserId, "admin");
            };
        }
        return switch (index % 4) {
            case 0 -> new DemoCommentAuthor(resourceUserId, "resource");
            case 1 -> new DemoCommentAuthor(customerUserId, "customer");
            case 2 -> new DemoCommentAuthor(agentUserId, "agent");
            default -> new DemoCommentAuthor(adminUserId, "admin");
        };
    }

    private String demoCommentBody(int index, boolean internal, String role) {
        if (internal) {
            return internalCommentBody(index, role);
        }
        return publicCommentBody(index, role);
    }

    private String internalCommentBody(int index, String role) {
        return switch (index) {
            case 1 -> switch (role) {
                case "resource" -> "I checked the site notes and there is enough access for a standard visit.";
                case "admin" -> "I reviewed the requester and priority. The current assignment still looks right.";
                default -> "I am keeping this with the assigned team for now and will watch for any change in urgency.";
            };
            case 3 -> switch (role) {
                case "resource" -> "Parts and tools look straightforward. I do not see anything that should block the appointment.";
                case "admin" -> "This can stay in the current queue. No tenant visibility or permission issue is apparent.";
                default -> "The latest customer context is enough to proceed without asking for another intake round.";
            };
            case 6 -> switch (role) {
                case "resource" -> "If the first check does not confirm the fault, I will capture photos before closing the visit.";
                case "admin" -> "Please keep the public updates short and avoid promising a final fix until the visit is complete.";
                default -> "I have noted the likely cause and will confirm it with the technician before updating the requester.";
            };
            case 9 -> switch (role) {
                case "resource" -> "The work area may need a short interruption window, but no special permit appears necessary.";
                case "admin" -> "The audit trail is clear enough for handover if another team member picks this up.";
                default -> "This is ready for the next action. I do not need more information from the requester right now.";
            };
            case 12 -> switch (role) {
                case "resource" -> "I added the field details so the next person can compare them with the uploaded file.";
                case "admin" -> "The uploaded evidence matches the ticket description and can stay attached to this request.";
                default -> "I checked the attachment and it supports the current plan. No separate follow-up ticket is needed.";
            };
            default -> switch (role) {
                case "resource" -> "The technical details are consistent with the ticket history so far.";
                case "admin" -> "The request is still within the expected workflow for this tenant.";
                default -> "The thread has enough context for the team to continue without re-triage.";
            };
        };
    }

    private String publicCommentBody(int index, String role) {
        return switch (role) {
            case "resource" -> switch (index) {
                case 4 -> "I have enough access details for the visit and will record what I find on site.";
                case 8 -> "The work area looks ready. If anything changes before the appointment, please add it here.";
                case 12 -> "I attached the relevant field note so the team can compare it with the current condition.";
                default -> "I have noted the practical details for the visit and will keep the ticket updated.";
            };
            case "admin" -> switch (index) {
                case 3 -> "The request has been reviewed and the team has the information needed to keep moving.";
                case 7 -> "I checked the details against the tenant record and there is no extra approval needed right now.";
                case 11 -> "The latest notes and attachment belong on this ticket, so we will keep the history together.";
                default -> "The ticket is still on the right path. We will keep updates in this thread.";
            };
            case "agent" -> switch (index) {
                case 2 -> "Thanks, we have the issue logged and are coordinating the next step with the right person.";
                case 6 -> "The current plan is still valid. I will update the ticket if timing or assignment changes.";
                case 10 -> "The file attached here gives us enough context to continue without opening a second request.";
                default -> "We are tracking this in the same thread so everyone can see the latest status.";
            };
            default -> switch (index) {
                case 1 -> "I added this because the issue is still visible and affects the usual workflow.";
                case 5 -> "The situation has not changed much since the first report, but the timing is becoming important.";
                case 9 -> "I can confirm the location and symptoms are the same as described earlier.";
                default -> "This extra context should help the team verify the issue when they review the ticket.";
            };
        };
    }

    private void upsertComment(UUID tenantId, UUID id, UUID ticketId, UUID authorUserId,
                               String visibility, String body) {
        OffsetDateTime createdAt = demoChildCreatedAt(ticketId, id, 1);
        jdbc.update("""
            insert into app.ticket_comment(
                tenant_id, id, ticket_id, author_user_id, visibility, body, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, id) do update
                set ticket_id = excluded.ticket_id,
                    author_user_id = excluded.author_user_id,
                    visibility = excluded.visibility,
                    body = excluded.body,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at,
                    deleted_at = null
            """, tenantId, id, ticketId, authorUserId, visibility, body, createdAt, createdAt);
    }

    private void upsertAppointments(Map<String, DemoUser> users) {
        UUID resource = user(users, "resource@acme.demo").localId();
        UUID acmeResource2 = user(users, "acme.resource2@demo.com").localId();
        UUID acmeResource3 = user(users, "acme.resource3@demo.com").localId();
        UUID customer = user(users, "customer@acme.demo").localId();
        UUID acmeCustomer2 = user(users, "acme.customer2@demo.com").localId();
        UUID acmeCustomer3 = user(users, "acme.customer3@demo.com").localId();
        UUID multi = user(users, "multi.member@demo.com").localId();
        UUID betaResource = user(users, "beta.resource@demo.com").localId();
        UUID betaResource2 = user(users, "beta.resource2@demo.com").localId();
        UUID betaCustomer = user(users, "beta.customer@demo.com").localId();

        OffsetDateTime upcoming = nextResourceWorkingHour(TENANT_ACME, resource, 1, 10, 1);
        OffsetDateTime completed = previousResourceWorkingHour(TENANT_ACME, resource, 3, 10, 1);
        OffsetDateTime cancelled = previousResourceWorkingHour(TENANT_ACME, resource, 1, 14, 1);

        upsertAppointment(TENANT_ACME, APPT_HVAC, TICKET_HVAC, resource, customer, null,
                upcoming, upcoming.plusHours(1), "Reception, Level 1",
                "Check compressor and thermostat.", "BOOKED", null, null);
        upsertAppointment(TENANT_ACME, APPT_PRINTER_DONE, TICKET_PRINTER, resource, null, CONTACT_PROPERTY_MANAGER,
                completed, completed.plusHours(1), "Admin office",
                "Printer was configured and tested.", "COMPLETED",
                completed.plusMinutes(5), completed.plusMinutes(55));
        upsertAppointment(TENANT_ACME, APPT_LEAK_CANCELLED, TICKET_LEAK, resource, customer, null,
                cancelled, cancelled.plusHours(1), "Meeting Room 3B",
                "Cancelled due to room access restriction.", "CANCELLED", null, null);

        upsertAppointment(TENANT_ACME, demoUuid(40000000, 4), demoUuid(30000000, 7), acmeResource2, acmeCustomer2, null,
                nextResourceWorkingHour(TENANT_ACME, acmeResource2, 1, 9, 1),
                nextResourceWorkingHour(TENANT_ACME, acmeResource2, 1, 10, 1), "Lift lobby, Level 4",
                "Inspect noise and log travel direction.", "BOOKED", null, null);
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 5), demoUuid(30000000, 8), acmeResource3, null, demoUuid(20000000, 6),
                nextResourceWorkingHour(TENANT_ACME, acmeResource3, 2, 10, 1),
                nextResourceWorkingHour(TENANT_ACME, acmeResource3, 2, 11, 1), "Loading dock",
                "Meet vendor and verify access list.", "BOOKED", null, null);
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 6), demoUuid(30000000, 11), acmeResource2, null, demoUuid(20000000, 7),
                nextResourceWorkingHour(TENANT_ACME, acmeResource2, 3, 10, 1),
                nextResourceWorkingHour(TENANT_ACME, acmeResource2, 3, 11, 1), "Car park entrance",
                "Bring PoE injector and ladder.", "RESCHEDULED", null, null);
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 7), demoUuid(30000000, 12), acmeResource3, acmeCustomer2, null,
                nextResourceWorkingHour(TENANT_ACME, acmeResource3, 4, 10, 1),
                nextResourceWorkingHour(TENANT_ACME, acmeResource3, 4, 11, 1), "Suite 210",
                "Repeat heating fault inspection.", "BOOKED", null, null);
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 8), demoUuid(30000000, 14), resource, customer, null,
                nextResourceWorkingHour(TENANT_ACME, resource, 4, 14, 1),
                nextResourceWorkingHour(TENANT_ACME, resource, 4, 15, 1), "Conference Room A",
                "Confirm AV and seating layout.", "BOOKED", null, null);
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 9), demoUuid(30000000, 16), acmeResource2, acmeCustomer3, null,
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 4, 11, 1),
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 4, 12, 1),
                "Shared kitchen", "Checked wall moisture and closed the job.", "COMPLETED",
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 4, 11, 1).plusMinutes(5),
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 4, 11, 1).plusMinutes(50));
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 10), demoUuid(30000000, 18), resource, null, demoUuid(20000000, 3),
                nextResourceWorkingHour(TENANT_ACME, resource, 6, 10, 1),
                nextResourceWorkingHour(TENANT_ACME, resource, 6, 11, 1), "Main lobby",
                "Review reader logs with building manager.", "BOOKED", null, null);
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 11), demoUuid(30000000, 20), acmeResource2, acmeCustomer2, null,
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 6, 9, 1),
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 6, 10, 1),
                "Print room", "Power board isolated and replaced.", "COMPLETED",
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 6, 9, 1).plusMinutes(8),
                previousResourceWorkingHour(TENANT_ACME, acmeResource2, 6, 9, 1).plusMinutes(55));
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 12), demoUuid(30000000, 21), acmeResource3, null, demoUuid(20000000, 2),
                nextResourceWorkingHour(TENANT_ACME, acmeResource3, 7, 10, 1),
                nextResourceWorkingHour(TENANT_ACME, acmeResource3, 7, 11, 1), "Training room",
                "Re-test guest network and capture signal levels.", "BOOKED", null, null);
        upsertAppointment(TENANT_ACME, demoUuid(40000000, 13), demoUuid(30000000, 24), resource, customer, null,
                nextResourceWorkingHour(TENANT_ACME, resource, 8, 11, 1),
                nextResourceWorkingHour(TENANT_ACME, resource, 8, 12, 1), "Reception",
                "Replace charging dock and verify tablet battery.", "BOOKED", null, null);

        upsertAppointment(TENANT_BETA, demoUuid(40000000, 101), demoUuid(30000000, 25), betaResource, betaCustomer, null,
                nextResourceWorkingHour(TENANT_BETA, betaResource, 1, 9, 1),
                nextResourceWorkingHour(TENANT_BETA, betaResource, 1, 10, 1), "Exam Room 2",
                "Inspect lighting during quiet period.", "BOOKED", null, null);
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 102), demoUuid(30000000, 26), betaResource2, null, demoUuid(20000000, 103),
                nextResourceWorkingHour(TENANT_BETA, betaResource2, 1, 11, 1),
                nextResourceWorkingHour(TENANT_BETA, betaResource2, 1, 12, 1), "Imaging Room",
                "Vendor diagnostic appointment.", "BOOKED", null, null);
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 103), demoUuid(30000000, 27), betaResource, multi, null,
                previousResourceWorkingHour(TENANT_BETA, betaResource, 5, 10, 1),
                previousResourceWorkingHour(TENANT_BETA, betaResource, 5, 11, 1),
                "Waiting room", "Signage updated and photographed.", "COMPLETED",
                previousResourceWorkingHour(TENANT_BETA, betaResource, 5, 10, 1).plusMinutes(4),
                previousResourceWorkingHour(TENANT_BETA, betaResource, 5, 10, 1).plusMinutes(45));
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 104), demoUuid(30000000, 28), betaResource2, null, demoUuid(20000000, 104),
                nextResourceWorkingHour(TENANT_BETA, betaResource2, 2, 9, 1),
                nextResourceWorkingHour(TENANT_BETA, betaResource2, 2, 10, 1), "Admin desk",
                "Set up workstation and access card.", "RESCHEDULED", null, null);
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 105), demoUuid(30000000, 30), betaResource, null, demoUuid(20000000, 105),
                nextResourceWorkingHour(TENANT_BETA, betaResource, 3, 9, 1),
                nextResourceWorkingHour(TENANT_BETA, betaResource, 3, 10, 1), "Training room",
                "Wi-Fi signal retest after clinic hours.", "BOOKED", null, null);
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 106), demoUuid(30000000, 31), betaResource2, betaCustomer, null,
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 2, 9, 1),
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 2, 10, 1),
                "Treatment Room 1", "Sink trap seal replaced.", "COMPLETED",
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 2, 9, 1).plusMinutes(2),
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 2, 9, 1).plusMinutes(40));
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 107), demoUuid(30000000, 33), betaResource, multi, null,
                nextResourceWorkingHour(TENANT_BETA, betaResource, 4, 10, 1),
                nextResourceWorkingHour(TENANT_BETA, betaResource, 4, 11, 1), "Storage Room B",
                "Install shelving and anchor points.", "BOOKED", null, null);
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 108), demoUuid(30000000, 34), betaResource2, betaCustomer, null,
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 8, 13, 1),
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 8, 14, 1),
                "Front desk", "Kiosk reboot issue resolved after firmware update.", "COMPLETED",
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 8, 13, 1).plusMinutes(6),
                previousResourceWorkingHour(TENANT_BETA, betaResource2, 8, 13, 1).plusMinutes(52));
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 109), demoUuid(30000000, 35), betaResource, null, demoUuid(20000000, 103),
                nextResourceWorkingHour(TENANT_BETA, betaResource, 5, 11, 1),
                nextResourceWorkingHour(TENANT_BETA, betaResource, 5, 12, 1), "Admin office",
                "Collect and attach vendor documentation.", "BOOKED", null, null);
        upsertAppointment(TENANT_BETA, demoUuid(40000000, 110), demoUuid(30000000, 36), betaResource2, betaCustomer, null,
                nextResourceWorkingHour(TENANT_BETA, betaResource2, 6, 9, 1),
                nextResourceWorkingHour(TENANT_BETA, betaResource2, 6, 10, 1), "Cold Storage",
                "Inspect monitor and validate threshold alarm.", "BOOKED", null, null);
    }

    private void upsertAppointment(UUID tenantId, UUID id, UUID ticketId, UUID resourceUserId,
                                   UUID customerUserId, UUID customerContactId, OffsetDateTime startAt,
                                   OffsetDateTime endAt, String addressText, String notes, String status,
                                   OffsetDateTime arrivedAt, OffsetDateTime completedAt) {
        validateAppointmentCustomerMatchesTicket(tenantId, id, ticketId, customerUserId, customerContactId);
        validateDemoAppointmentAvailability(tenantId, id, resourceUserId, startAt, endAt);
        OffsetDateTime createdAt = appointmentCreatedAt(startAt);
        OffsetDateTime updatedAt = appointmentUpdatedAt(status, createdAt, arrivedAt, completedAt);
        jdbc.update("""
            insert into app.appointment(
                tenant_id, id, ticket_id, resource_user_id, customer_user_id, customer_contact_id,
                start_at, end_at, status, address_text, notes, arrived_at, completed_at,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (tenant_id, id) do update
                set ticket_id = excluded.ticket_id,
                    resource_user_id = excluded.resource_user_id,
                    customer_user_id = excluded.customer_user_id,
                    customer_contact_id = excluded.customer_contact_id,
                    start_at = excluded.start_at,
                    end_at = excluded.end_at,
                    status = excluded.status,
                    address_text = excluded.address_text,
                    notes = excluded.notes,
                    arrived_at = excluded.arrived_at,
                    completed_at = excluded.completed_at,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at
            """, tenantId, id, ticketId, resourceUserId, customerUserId, customerContactId,
                startAt, endAt, status, addressText, notes, arrivedAt, completedAt, createdAt, updatedAt);
    }

    private OffsetDateTime appointmentCreatedAt(OffsetDateTime startAt) {
        OffsetDateTime now = nowUtc();
        if (startAt.isAfter(now)) {
            return now.minusDays(1);
        }
        return startAt.minusDays(2);
    }

    private OffsetDateTime appointmentUpdatedAt(String status, OffsetDateTime createdAt,
                                                OffsetDateTime arrivedAt, OffsetDateTime completedAt) {
        return switch (status) {
            case "BOOKED", "RESCHEDULED" -> createdAt.plusHours(1);
            case "CANCELLED" -> createdAt.plusHours(2);
            case "COMPLETED" -> completedAt != null ? completedAt : createdAt.plusHours(2);
            default -> throw new IllegalArgumentException("Unsupported demo appointment status: " + status);
        };
    }

    private void validateDemoAppointmentAvailability(UUID tenantId, UUID appointmentId, UUID resourceUserId,
                                                     OffsetDateTime startAt, OffsetDateTime endAt) {
        if (!isCoveredByResourceWorkingHours(tenantId, resourceUserId, startAt, endAt)) {
            throw new IllegalStateException("Demo appointment " + appointmentId
                    + " is outside resource working hours: " + startAt + " - " + endAt);
        }
        if (exists("""
            select count(*)
            from app.resource_block
            where tenant_id = ?
              and resource_user_id = ?
              and deleted_at is null
              and end_at > ?
              and start_at < ?
            """, tenantId, resourceUserId, startAt, endAt)) {
            throw new IllegalStateException("Demo appointment " + appointmentId
                    + " overlaps a resource block: " + startAt + " - " + endAt);
        }
    }

    private boolean isCoveredByResourceWorkingHours(UUID tenantId, UUID resourceUserId,
                                                    OffsetDateTime startAt, OffsetDateTime endAt) {
        for (DemoWorkingHoursRule rule : workingHourRules(tenantId, resourceUserId)) {
            LocalDate startDate = startAt.atZoneSameInstant(rule.zone()).toLocalDate();
            LocalDate endDate = endAt.atZoneSameInstant(rule.zone()).toLocalDate();
            if (!startDate.equals(endDate)) {
                continue;
            }
            if (startAt.atZoneSameInstant(rule.zone()).getDayOfWeek().getValue() != rule.dayOfWeek()) {
                continue;
            }
            LocalTime startLocal = startAt.atZoneSameInstant(rule.zone()).toLocalTime();
            LocalTime endLocal = endAt.atZoneSameInstant(rule.zone()).toLocalTime();
            if (!startLocal.isBefore(rule.startLocal()) && !endLocal.isAfter(rule.endLocal())) {
                return true;
            }
        }
        return false;
    }

    private void validateTicketRequester(UUID tenantId, UUID ticketId, UUID requesterUserId,
                                         UUID requesterContactId) {
        if ((requesterUserId == null) == (requesterContactId == null)) {
            throw new IllegalStateException("Demo ticket " + ticketId
                    + " must have exactly one requester: registered user or contact.");
        }
        if (requesterUserId != null && !exists("""
                select count(*) from app.tenant_membership
                where tenant_id = ? and user_id = ?
                """, tenantId, requesterUserId)) {
            throw new IllegalStateException("Demo ticket " + ticketId
                    + " requester_user_id is not a member of tenant " + tenantId + ": " + requesterUserId);
        }
        if (requesterContactId != null && !exists("""
                select count(*) from app.contact
                where tenant_id = ? and id = ?
                """, tenantId, requesterContactId)) {
            throw new IllegalStateException("Demo ticket " + ticketId
                    + " requester_contact_id is not a contact of tenant " + tenantId + ": " + requesterContactId);
        }
    }

    private void validateAppointmentCustomerMatchesTicket(UUID tenantId, UUID appointmentId, UUID ticketId,
                                                          UUID customerUserId, UUID customerContactId) {
        if ((customerUserId == null) == (customerContactId == null)) {
            throw new IllegalStateException("Demo appointment " + appointmentId
                    + " must have exactly one customer: registered user or contact.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select requester_user_id, requester_contact_id
            from app.ticket
            where tenant_id = ? and id = ?
            """, tenantId, ticketId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Demo appointment " + appointmentId
                    + " references missing ticket " + ticketId);
        }
        UUID ticketRequesterUserId = (UUID) rows.get(0).get("requester_user_id");
        UUID ticketRequesterContactId = (UUID) rows.get(0).get("requester_contact_id");
        if (!Objects.equals(ticketRequesterUserId, customerUserId)
                || !Objects.equals(ticketRequesterContactId, customerContactId)) {
            throw new IllegalStateException("Demo appointment " + appointmentId
                    + " customer must match ticket requester. ticket=" + ticketId);
        }
    }

    private void upsertAttachments(Map<String, DemoUser> users) {
        UUID agent = user(users, "agent@acme.demo").localId();
        UUID acmeAgent2 = user(users, "acme.agent2@demo.com").localId();
        UUID betaAgent = user(users, "beta.agent@demo.com").localId();
        UUID betaResource2 = user(users, "beta.resource2@demo.com").localId();
        upsertAttachment(TENANT_ACME, ATTACHMENT_HVAC, TICKET_HVAC, agent,
                "hvac-diagnostic.pdf", "application/pdf",
                "Demo HVAC diagnostic note for the reception air conditioner.\n");
        upsertAttachment(TENANT_ACME, ATTACHMENT_LEAK, TICKET_LEAK, agent,
                "leak-photo-note.jpg", "image/jpeg",
                "Demo placeholder for the meeting room leak photo.\n");
        upsertAttachment(TENANT_ACME, demoUuid(70000000, 3), demoUuid(30000000, 7), acmeAgent2,
                "elevator-noise-report.pdf", "application/pdf",
                "Demo elevator noise inspection report.\n");
        upsertAttachment(TENANT_ACME, demoUuid(70000000, 4), demoUuid(30000000, 11), agent,
                "camera-offline-snapshot.jpg", "image/jpeg",
                "Demo camera offline snapshot placeholder.\n");
        upsertAttachment(TENANT_ACME, demoUuid(70000000, 5), demoUuid(30000000, 15), acmeAgent2,
                "hvac-vendor-quote.pdf", "application/pdf",
                "Demo vendor quote for emergency HVAC work.\n");
        upsertAttachment(TENANT_ACME, demoUuid(70000000, 6), demoUuid(30000000, 20), agent,
                "electrical-resolution.pdf", "application/pdf",
                "Demo electrical resolution certificate.\n");
        upsertAttachment(TENANT_ACME, demoUuid(70000000, 7), demoUuid(30000000, 21), agent,
                "guest-network-scan.png", "image/png",
                "Demo guest network signal scan placeholder.\n");
        upsertAttachment(TENANT_ACME, demoUuid(70000000, 8), demoUuid(30000000, 24), agent,
                "tablet-dock-photo.jpg", "image/jpeg",
                "Demo tablet charging dock photo placeholder.\n");
        upsertAttachment(TENANT_BETA, demoUuid(70000000, 101), demoUuid(30000000, 25), betaAgent,
                "exam-room-light-photo.jpg", "image/jpeg",
                "Demo exam room light photo placeholder.\n");
        upsertAttachment(TENANT_BETA, demoUuid(70000000, 102), demoUuid(30000000, 26), betaAgent,
                "imaging-coolant-alert.pdf", "application/pdf",
                "Demo imaging device coolant alert.\n");
        upsertAttachment(TENANT_BETA, demoUuid(70000000, 103), demoUuid(30000000, 31), betaAgent,
                "sink-repair-report.pdf", "application/pdf",
                "Demo sink repair report.\n");
        upsertAttachment(TENANT_BETA, demoUuid(70000000, 104), demoUuid(30000000, 36), betaResource2,
                "cold-room-temperature-log.pdf", "application/pdf",
                "Demo cold room temperature log.\n");
    }

    private void upsertAttachment(UUID tenantId, UUID attachmentId, UUID ticketId, UUID uploadedByUserId,
                                  String filename, String contentType, String content) {
        validateDemoAttachmentUploader(tenantId, attachmentId, ticketId, uploadedByUserId);
        String storageKey = tenantId + "/tickets/" + ticketId + "/attachments" + attachmentId;
        byte[] bytes = demoAttachmentBytes(filename, contentType, content);
        writeLocalAttachment(storageKey, bytes);
        OffsetDateTime createdAt = demoChildCreatedAt(ticketId, attachmentId, 3);
        jdbc.update("""
            insert into app.ticket_attachment(
                tenant_id, id, ticket_id, filename, content_type, size_bytes,
                storage_provider, storage_key, sha256, uploaded_by_user_id, created_at
            ) values (?, ?, ?, ?, ?, ?, 'LOCAL', ?, ?, ?, ?)
            on conflict (tenant_id, id) do update
                set ticket_id = excluded.ticket_id,
                    filename = excluded.filename,
                    content_type = excluded.content_type,
                    size_bytes = excluded.size_bytes,
                    storage_key = excluded.storage_key,
                    sha256 = excluded.sha256,
                    uploaded_by_user_id = excluded.uploaded_by_user_id,
                    created_at = excluded.created_at,
                    deleted_at = null
            """, tenantId, attachmentId, ticketId, filename, contentType, bytes.length,
                storageKey, sha256(bytes), uploadedByUserId, createdAt);
    }

    private void validateDemoAttachmentUploader(UUID tenantId, UUID attachmentId,
                                                UUID ticketId, UUID uploadedByUserId) {
        if (!exists("""
            select count(*)
            from app.ticket t
            where t.tenant_id = ? and t.id = ?
              and (
                t.requester_user_id = ?
                or t.owner_user_id = ?
                or exists (
                    select 1 from app.appointment a
                    where a.tenant_id = t.tenant_id
                      and a.ticket_id = t.id
                      and a.resource_user_id = ?
                )
                or exists (
                    select 1 from app.tenant_membership m
                    where m.tenant_id = t.tenant_id
                      and m.user_id = ?
                      and m.role = 'ADMIN'
                )
              )
            """, tenantId, ticketId, uploadedByUserId, uploadedByUserId,
                uploadedByUserId, uploadedByUserId)) {
            throw new IllegalStateException("Demo attachment " + attachmentId
                    + " uploader must be ticket requester, owner, assigned resource, or admin.");
        }
        if (!exists("""
            select count(*)
            from app.ticket_comment
            where tenant_id = ? and ticket_id = ? and author_user_id = ? and deleted_at is null
            """, tenantId, ticketId, uploadedByUserId)) {
            throw new IllegalStateException("Demo attachment " + attachmentId
                    + " uploader should have a comment on the ticket before uploading.");
        }
    }

    private byte[] demoAttachmentBytes(String filename, String contentType, String content) {
        return switch (contentType) {
            case "application/pdf" -> demoPdfBytes(filename, content);
            case "image/jpeg" -> demoImageBytes("jpg", filename, content);
            case "image/png" -> demoImageBytes("png", filename, content);
            default -> throw new IllegalArgumentException("Unsupported demo attachment content type: "
                    + contentType);
        };
    }

    private byte[] demoPdfBytes(String filename, String content) {
        String stream = """
                BT
                /F1 18 Tf
                72 720 Td
                (%s) Tj
                0 -30 Td
                /F1 12 Tf
                (%s) Tj
                ET
                """.formatted(pdfText(filename), pdfText(content.strip()));
        StringBuilder pdf = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        pdf.append("%PDF-1.4\n");
        appendPdfObject(pdf, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        appendPdfObject(pdf, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        appendPdfObject(pdf, offsets, 3, """
                << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]
                   /Resources << /Font << /F1 5 0 R >> >>
                   /Contents 4 0 R >>
                """);
        appendPdfObject(pdf, offsets, 4, """
                << /Length %d >>
                stream
                %sendstream
                """.formatted(stream.getBytes(StandardCharsets.ISO_8859_1).length, stream));
        appendPdfObject(pdf, offsets, 5, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n");
        pdf.append("0 6\n");
        pdf.append("0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append("%010d 00000 n \n".formatted(offset));
        }
        pdf.append("""
                trailer
                << /Size 6 /Root 1 0 R >>
                startxref
                %d
                %%EOF
                """.formatted(xrefOffset));
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private void appendPdfObject(StringBuilder pdf, List<Integer> offsets, int objectNumber, String body) {
        offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
        pdf.append(objectNumber).append(" 0 obj\n");
        pdf.append(body.strip()).append("\n");
        pdf.append("endobj\n");
    }

    private String pdfText(String value) {
        return value.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private byte[] demoImageBytes(String format, String filename, String content) {
        BufferedImage image = new BufferedImage(800, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(244, 247, 250));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(new Color(34, 49, 63));
            g.fillRect(0, 0, image.getWidth(), 88);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.drawString("Demo attachment", 48, 56);
            g.setColor(new Color(42, 56, 70));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            g.drawString(filename, 48, 150);
            g.setColor(new Color(74, 86, 99));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            drawWrappedText(g, content.strip(), 48, 205, 700, 28);
            g.setColor(new Color(120, 136, 153));
            g.drawString("Generated by DemoDataSeeder", 48, 420);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No image writer for demo attachment format: " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate demo image attachment: " + filename, e);
        }
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        StringBuilder line = new StringBuilder();
        int currentY = y;
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                g.drawString(line.toString(), x, currentY);
                line = new StringBuilder(word);
                currentY += lineHeight;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, currentY);
        }
    }

    private void writeLocalAttachment(String storageKey, byte[] bytes) {
        String root = env.getProperty("app.upload.dir", "/tmp/multiapp/uploads");
        Path target = Path.of(root).toAbsolutePath().normalize().resolve(storageKey).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write demo attachment: " + target, e);
        }
    }

    private static UUID demoUuid(int namespace, int number) {
        return UUID.fromString("%08d-0000-0000-0000-%012d".formatted(namespace, number));
    }

    private UUID queryRequiredUuid(String sql, Object... args) {
        List<UUID> ids = jdbc.query(sql, (rs, rowNum) -> rs.getObject(1, UUID.class), args);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Required UUID not found for query: " + sql);
        }
        return ids.get(0);
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private DemoUser user(Map<String, DemoUser> users, String email) {
        DemoUser user = users.get(email);
        if (user == null) {
            throw new IllegalStateException("Missing demo user: " + email);
        }
        if (user.localId() == null) {
            throw new IllegalStateException("Demo user was not persisted: " + email);
        }
        return user;
    }

    private OffsetDateTime nextResourceWorkingHour(UUID tenantId, UUID resourceUserId,
                                                   int workingDaysAhead, int localHour,
                                                   int durationHours) {
        return resourceWorkingHour(tenantId, resourceUserId, workingDaysAhead, localHour,
                durationHours, true);
    }

    private OffsetDateTime previousResourceWorkingHour(UUID tenantId, UUID resourceUserId,
                                                       int workingDaysAgo, int localHour,
                                                       int durationHours) {
        return resourceWorkingHour(tenantId, resourceUserId, workingDaysAgo, localHour,
                durationHours, false);
    }

    private OffsetDateTime resourceWorkingHour(UUID tenantId, UUID resourceUserId, int ordinal,
                                               int localHour, int durationHours, boolean future) {
        if (ordinal <= 0 || durationHours <= 0) {
            throw new IllegalArgumentException("ordinal and durationHours must be positive");
        }
        List<DemoWorkingHoursRule> rules = workingHourRules(tenantId, resourceUserId);
        if (rules.isEmpty()) {
            throw new IllegalStateException("Demo resource has no working hours: " + resourceUserId);
        }
        LocalTime requestedStart = LocalTime.of(localHour, 0);
        LocalTime requestedEnd = requestedStart.plusHours(durationHours);
        int remaining = ordinal;
        LocalDate date = nowUtc()
                .atZoneSameInstant(rules.get(0).zone())
                .toLocalDate()
                .plusDays(future ? 1 : -1);
        for (int attempts = 0; attempts < 370; attempts++) {
            for (DemoWorkingHoursRule rule : rules) {
                if (date.getDayOfWeek().getValue() != rule.dayOfWeek()) {
                    continue;
                }
                if (requestedStart.isBefore(rule.startLocal()) || requestedEnd.isAfter(rule.endLocal())) {
                    continue;
                }
                remaining--;
                if (remaining == 0) {
                    return ZonedDateTime.of(date, requestedStart, rule.zone()).toOffsetDateTime();
                }
            }
            date = date.plusDays(future ? 1 : -1);
        }
        throw new IllegalStateException("Cannot find demo working slot for resource " + resourceUserId
                + " at local hour " + localHour);
    }

    private List<DemoWorkingHoursRule> workingHourRules(UUID tenantId, UUID resourceUserId) {
        return jdbc.query("""
            select day_of_week, start_local, end_local, timezone
            from app.resource_working_hours
            where tenant_id = ? and resource_user_id = ?
            order by day_of_week
            """, (rs, rowNum) -> new DemoWorkingHoursRule(
                rs.getInt("day_of_week"),
                rs.getObject("start_local", LocalTime.class),
                rs.getObject("end_local", LocalTime.class),
                ZoneId.of(rs.getString("timezone"))
        ), tenantId, resourceUserId);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private OffsetDateTime demoTicketCreatedAt(UUID ticketId, OffsetDateTime now) {
        int ordinal = demoOrdinal(ticketId);
        int daysAgo = ((ordinal - 1) % 7) + 1;
        int hour = 8 + ((ordinal - 1) % 8);
        int minute = ((ordinal - 1) % 4) * 10;
        return now.minusDays(daysAgo)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
    }

    private OffsetDateTime demoChildCreatedAt(UUID ticketId, UUID childId, int baseHoursAfterTicket) {
        OffsetDateTime ticketCreatedAt = demoTicketCreatedAt(ticketId, nowUtc());
        int ordinal = demoOrdinal(childId);
        return ticketCreatedAt
                .plusHours(baseHoursAfterTicket + (ordinal % 3))
                .plusMinutes((ordinal % 4) * 10);
    }

    private int demoOrdinal(UUID id) {
        String lastSegment = id.toString().substring(24);
        int ordinal = Integer.parseInt(lastSegment);
        if (ordinal <= 0) {
            throw new IllegalArgumentException("Demo UUID ordinal must be positive: " + id);
        }
        return ordinal;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText();
    }

    private static String displayName(JsonNode node, String email) {
        String first = text(node, "firstName", "");
        String last = text(node, "lastName", "");
        String full = (first + " " + last).strip();
        if (!full.isBlank()) return full;
        String username = text(node, "username", email);
        return username == null || username.isBlank() ? email : username;
    }

    private static String firstAttribute(JsonNode node, String name) {
        JsonNode attr = node.path("attributes").path(name);
        if (attr.isArray() && !attr.isEmpty()) return attr.get(0).asText();
        if (attr.isTextual()) return attr.asText();
        return null;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record DemoCommentAuthor(UUID userId, String role) {
    }

    private record DemoWorkingHoursRule(int dayOfWeek, LocalTime startLocal,
                                        LocalTime endLocal, ZoneId zone) {
    }

    private static final class DemoUser {
        private final String issuer;
        private final String sub;
        private final String email;
        private final String displayName;
        private final String phone;
        private final boolean platformAdmin;
        private UUID localId;

        private DemoUser(String issuer, String sub, String email, String displayName,
                         String phone, boolean platformAdmin) {
            this.issuer = issuer;
            this.sub = sub;
            this.email = email;
            this.displayName = displayName;
            this.phone = phone;
            this.platformAdmin = platformAdmin;
        }

        private String issuer() {
            return issuer;
        }

        private String sub() {
            return sub;
        }

        private String email() {
            return email;
        }

        private String displayName() {
            return displayName;
        }

        private String phone() {
            return phone;
        }

        private boolean platformAdmin() {
            return platformAdmin;
        }

        private UUID localId() {
            return localId;
        }

        private void localId(UUID localId) {
            this.localId = localId;
        }
    }
}
