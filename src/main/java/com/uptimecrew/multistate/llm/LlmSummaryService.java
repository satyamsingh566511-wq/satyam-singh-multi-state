package com.uptimecrew.multistate.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import com.uptimecrew.multistate.graphql.TenantSummary;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import java.io.InputStream;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

// Pattern reference for Task 3 (Spring AI .entity(...) + JSON Schema validation).
//
// Two-stage contract — why validate twice?
//   1. Spring AI's .entity(TenantSummary.class) converter coerces the LLM's JSON into the
//      record via Jackson. That enforces SHAPE the JVM can express: field names and Java
//      types. It will happily accept a primaryState of "Mars", a negative stateCount, or an
//      extra unmodelled field that Jackson simply ignores.
//   2. The explicit JSON Schema in resources/schemas/ re-validates the same object against
//      constraints the record type CAN'T express: additionalProperties=false (no drift
//      fields), required (no nulls silently allowed), and minimum=0 on stateCount. So a
//      future model/release that drifts the payload fails loudly here instead of shipping a
//      malformed summary downstream.
@Service
public class LlmSummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(LlmSummaryService.class);

    private final ChatClient chatClient;
    private final TenantReadModelRepository readModelRepository;
    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public LlmSummaryService(ChatClient.Builder builder,
                             TenantReadModelRepository readModelRepository,
                             ObjectMapper mapper) {
        this.chatClient = builder.build();
        this.readModelRepository = readModelRepository;
        this.mapper = mapper;
        try (InputStream in = new ClassPathResource(
                "schemas/TenantSummary.schema.json").getInputStream()) {
            this.schema = JsonSchemaFactory
                    .getInstance(VersionFlag.V202012)
                    .getSchema(in);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load TenantSummary schema", ex);
        }
    }

    public TenantSummary summarize(String id) {
        TenantReadModel doc = readModelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown id " + id));
        String prompt = "Summarise this tenant as a JSON object matching the "
                + "TenantSummary schema. Output JSON only, no prose. Domain data: "
                + doc.toString();
        TenantSummary result = chatClient.prompt().user(prompt).call().entity(TenantSummary.class);
        validate(result);
        LOG.info("structured-output ok id={}", id);
        return result;
    }

    private void validate(TenantSummary candidate) {
        JsonNode node = mapper.valueToTree(candidate);
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            LOG.warn("schema violation errors={}", errors);
            throw new IllegalStateException("LLM output failed JSON Schema validation: " + errors);
        }
    }
}
