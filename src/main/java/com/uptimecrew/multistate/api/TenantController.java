package com.uptimecrew.multistate.api;

import com.uptimecrew.multistate.clients.IdentityProfile;
import com.uptimecrew.multistate.clients.IdentityService;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.service.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenants", description = "Tenants read API and LLM-summary POST endpoint")
public class TenantController {

    private static final Logger LOG = LoggerFactory.getLogger(TenantController.class);

    private final AllocationService service;
    private final IdentityService identityService;
    private final IdempotencyService idempotency;

    public TenantController(AllocationService service,
                               IdentityService identityService,
                               IdempotencyService idempotency) {
        this.service = service;
        this.identityService = identityService;
        this.idempotency = idempotency;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_tenants.read') and hasRole('TENANT_READER')")
    @Operation(summary = "Fetch a tenant by id",
               description = "Returns the denormalised read-model document for the given id.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "JWT present but lacks required scope or role"),
        @ApiResponse(responseCode = "404", description = "No tenant with that id")
    })
    public ResponseEntity<TenantReadModel> getById(@PathVariable String id,
                                                    @AuthenticationPrincipal Jwt jwt) {
        LOG.info("get id={} subject={}", id, jwt.getSubject());
        LOG.debug("layer-cache discipline probe v5: getById invoked for id={}", id);
        Optional<TenantReadModel> found = service.findById(id);
        return found.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/summary")
    @PreAuthorize("hasAuthority('SCOPE_tenants.read') and hasRole('TENANT_READER')")
    @Operation(summary = "Generate an LLM summary for a tenant",
               description = "Idempotent POST: pass an Idempotency-Key header (UUID) so retries return the cached body.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary generated (or cached body returned)"),
        @ApiResponse(responseCode = "400", description = "Idempotency-Key missing or not a valid UUID"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "JWT present but lacks required scope or role"),
        @ApiResponse(responseCode = "409", description = "Idempotency key in flight for a different request")
    })
    public ResponseEntity<Map<String, String>> summary(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        final UUID parsed;
        try {
            parsed = UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException ex) {
            LOG.warn("rejected non-UUID Idempotency-Key for id={} subject={}", id, jwt.getSubject());
            return ResponseEntity.badRequest().build();
        }

        return idempotency.handle(parsed.toString(), "tenants.summary", () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            IdentityProfile profile = identityService.getProfile(jwt.getSubject());
            LOG.info("summary id={} subject={} displayName={}",
                    id, jwt.getSubject(), profile.displayName());
            return ResponseEntity.ok(Map.of(
                    "summary", "Stub LLM summary for " + id,
                    "displayName", profile.displayName()
            ));
        });
    }
}
