package com.uptimecrew.multistate.api;

import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.service.AllocationService;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * Read-only HTTP edge over AllocationService. The SecurityFilterChain has already
 * authenticated the Bearer token before any method here runs (default-deny on
 * /api/**), so a missing token is a 401 at the filter; the @PreAuthorize gate
 * below turns an authenticated-but-under-privileged caller into a 403.
 */
@RestController
@RequestMapping("/api/tenants")
/* Not final: @PreAuthorize makes Spring CGLIB-proxy this bean, which subclasses
 * the target — impossible for a final class. (Repo's "final by default" yields
 * to that framework constraint, as SecurityConfig and AllocationService note.) */
public class TenantController {

    private static final Logger LOG = LoggerFactory.getLogger(TenantController.class);

    private final AllocationService service;

    public TenantController(AllocationService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tenants.read') and hasRole('TENANT_READER')")
    public ResponseEntity<TenantReadModel> getById(@PathVariable String id,
                                                    @AuthenticationPrincipal Jwt jwt) {
        LOG.info("get id={} subject={}", id, jwt.getSubject());
        Optional<TenantReadModel> found = service.findById(id);
        return found.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /*
     * Stubs an LLM-backed summary. Today is about the rate-limit plumbing
     * (see RateLimitFilter), not the model — the sleep stands in for an LLM
     * round-trip so the /api/**+/summary path actually exercises the meter.
     * Gated by the same @PreAuthorize as getById.
     */
    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAuthority('SCOPE_tenants.read') and hasRole('TENANT_READER')")
    public Map<String, String> summary(@PathVariable String id,
                                        @AuthenticationPrincipal Jwt jwt) throws InterruptedException {
        LOG.info("summary id={} subject={}", id, jwt.getSubject());
        Thread.sleep(100);
        return Map.of("summary", "Stub LLM summary for " + id);
    }
}
