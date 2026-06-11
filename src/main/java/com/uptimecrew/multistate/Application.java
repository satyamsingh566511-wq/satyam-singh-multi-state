package com.uptimecrew.multistate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Spring Boot entry point for the multistate application.
 *
 * Component-scans everything under com.uptimecrew.multistate.* so the
 * Week 1 service, its strategies, and the Day 4 repositories are all picked
 * up without explicit configuration.
 *
 * <p>As of W2 D4 a real datasource is configured in {@code application.yml},
 * so {@code DataSourceAutoConfiguration} is left enabled — Spring Data JPA
 * needs the resulting {@code DataSource} to bootstrap the
 * {@code EntityManagerFactory} and the repositories in
 * {@code com.uptimecrew.multistate.repository}.
 */
@SpringBootApplication
@EnableCaching                                          /* W2 D5: activate Spring's cache abstraction (Redis-backed @Cacheable) */
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
