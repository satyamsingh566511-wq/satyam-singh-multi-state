package com.uptimecrew.multistate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Spring Boot entry point for the multistate application.
 *
 * Component-scans everything under com.uptimecrew.multistate.* so the
 * Week 1 service, its strategies, and the Day 4 repositories are all picked
 * up without explicit configuration.
 *
 * <p>{@code DataSourceAutoConfiguration} is excluded for now: the W2 D3
 * {@code spring-boot-starter-jdbc} + Postgres driver sit on the classpath,
 * but no {@code spring.datasource.url} is configured yet, so auto-config
 * would abort startup ("Failed to determine a suitable driver class").
 * Remove this exclusion on Day 4 once a real datasource is configured.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
