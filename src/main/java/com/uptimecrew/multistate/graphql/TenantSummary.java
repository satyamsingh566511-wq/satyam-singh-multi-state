package com.uptimecrew.multistate.graphql;

// Pattern reference for Task 3 (Structured outputs).
//
// The LLM is bound to THIS record by Spring AI's .entity(TenantSummary.class) converter,
// then re-validated against the hand-written JSON Schema in
// resources/schemas/TenantSummary.schema.json so a future model release that drifts the
// shape fails loudly instead of silently shipping a malformed summary.
//
// NOTE on types: this is an LLM-generated *summary* DTO at the GraphQL boundary, not a
// value in the BigDecimal allocation domain — no money arithmetic is performed on it. Its
// field types deliberately mirror the JSON Schema (number -> Double, integer -> Integer)
// and the GraphQL Float/Int contract so the .entity(...) converter and the validator agree
// on shape.
public record TenantSummary(
        String primaryState,
        Double totalAllocation,
        Integer stateCount,
        String complianceTier) { }
