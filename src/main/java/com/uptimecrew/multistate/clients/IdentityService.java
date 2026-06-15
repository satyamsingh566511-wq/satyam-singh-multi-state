package com.uptimecrew.multistate.clients;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);

    private final TenantIdentityClient client;

    public IdentityService(@Value("${spring.identity.base-url}") String baseUrl) {
        this.client = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .logger(new Slf4jLogger())
                .target(TenantIdentityClient.class, baseUrl);
    }

    @CircuitBreaker(name = "identity", fallbackMethod = "fallbackProfile")
    public IdentityProfile getProfile(String userId) {
        return client.getProfile(userId);
    }

    @SuppressWarnings("unused")
    private IdentityProfile fallbackProfile(String userId, Throwable t) {
        LOG.warn("identity breaker fallback for userId={} cause={}", userId, t.toString());
        return new IdentityProfile(userId, "", "unknown");
    }
}
