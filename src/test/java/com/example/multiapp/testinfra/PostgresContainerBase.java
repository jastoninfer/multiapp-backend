package com.example.multiapp.testinfra;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgresContainerBase {
    static final PostgreSQLContainer postgresContainer = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @BeforeAll
    static void start() {
        postgresContainer.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        r.add("spring.datasource.username", postgresContainer::getUsername);
        r.add("spring.datasource.password", postgresContainer::getPassword);

        // flyway建议开启(默认会跑)
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.schemas", () -> "app");
        r.add("spring.flyway.default-schema", () -> "app");
        r.add("spring.flyway.create-schemas", () -> "true");
        // 避免Hibernate自动ddl干扰flyway
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    public static class TestRequestContextWebConfig {
    }
}
