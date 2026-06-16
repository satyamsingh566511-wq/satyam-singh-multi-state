package com.uptimecrew.multistate.clients;

import feign.Param;
import feign.RequestLine;

public interface TenantIdentityClient {

    @RequestLine("GET /identity/{userId}/profile")
    IdentityProfile getProfile(@Param("userId") String userId);
}
