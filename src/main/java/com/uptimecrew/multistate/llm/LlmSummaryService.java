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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.InputStream;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

// W3 D5 Task 3 — manual OpenTelemetry span around the Spring AI ChatClient call.
//
// The W3 D4 body called chatClient.prompt().user(prompt).call().entity(TenantSummary.class)
// directly. Here we wrap the call in a manual Span named "llm.summarize" (CLIENT kind)
// so the LLM segment shows in Jaeger as a child of the current GraphQL/controller span,
// and we attach cost-SLI attributes read from ChatResponse.getMetadata().getUsage():
//   llm.model            — static, the configured model name
//   llm.input.aggregate_id — static, the entity being summarised (filter many requests)
//   llm.tokens.in / .out  — dynamic, only knowable AFTER the response comes back
// Tokens are set BEFORE the JSON parse/validate so a malformed body still records cost.
//
// Two-stage output contract is preserved from W3 D4:
//   1. Jackson coerces the model's JSON into the TenantSummary record (shape: names + types).
//   2. The explicit JSON Schema re-validates constraints the record can't express
//      (additionalProperties=false, required, minimum=0) so payload drift fails loudly.
@Service
public class LlmSummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(LlmSummaryService.class);

    private static final AttributeKey<String> ATTR_MODEL        = AttributeKey.stringKey("llm.model");
    private static final AttributeKey<String> ATTR_AGGREGATE_ID = AttributeKey.stringKey("llm.input.aggregate_id");
    private static final AttributeKey<Long>   ATTR_TOKENS_IN     = AttributeKey.longKey("llm.tokens.in");
    private static final AttributeKey<Long>   ATTR_TOKENS_OUT    = AttributeKey.longKey("llm.tokens.out");

    private final ChatClient chatClient;
    private final TenantReadModelRepository readModelRepository;
    private final ObjectMapper mapper;
    private final JsonSchema schema;
    private final Tracer tracer;
    private final String configuredModel;

    public LlmSummaryService(ChatClient.Builder chatClientBuilder,
                             TenantReadModelRepository readModelRepository,
                             ObjectMapper mapper,
                             OpenTelemetry openTelemetry,
                             @Value("${spring.ai.anthropic.chat.options.model:unknown}") String configuredModel) {
        this.chatClient = chatClientBuilder.build();
        this.readModelRepository = readModelRepository;
        this.mapper = mapper;
        this.tracer = openTelemetry.getTracer("com.uptimecrew.multistate.llm");
        this.configuredModel = configuredModel;
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

        Span span = tracer.spanBuilder("llm.summarize")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(ATTR_MODEL, configuredModel)
                .setAttribute(ATTR_AGGREGATE_ID, id)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            ChatResponse response = chatClient.prompt().user(prompt).call().chatResponse();

            // Token counts are only knowable after the response returns. Spring AI surfaces
            // them on ChatResponse.getMetadata().getUsage(); record them BEFORE the parse so
            // the cost-SLI is captured even when the body fails JSON parsing/validation. If
            // the provider returned no usage, safeLong maps the nulls to 0.
            var usage = response.getMetadata().getUsage();
            long promptTokens = safeLong(usage.getPromptTokens());
            long completionTokens = safeLong(usage.getCompletionTokens());
            span.setAttribute(ATTR_TOKENS_IN, promptTokens);
            span.setAttribute(ATTR_TOKENS_OUT, completionTokens);

            String content = response.getResult().getOutput().getText();
            TenantSummary result = content == null
                    ? null
                    : mapper.readValue(content, TenantSummary.class);

            validate(result);
            span.setStatus(StatusCode.OK);
            LOG.info("structured-output ok id={} model={} tokens.in={} tokens.out={}",
                    id, configuredModel, promptTokens, completionTokens);
            return result;
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getClass().getSimpleName());
            throw ex;
        } catch (Exception ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getClass().getSimpleName());
            throw new IllegalStateException("LLM call failed", ex);
        } finally {
            span.end();
        }
    }

    private static long safeLong(Number n) {
        return n == null ? 0L : n.longValue();
    }

    private void validate(TenantSummary candidate) {
        JsonNode node = mapper.valueToTree(candidate);
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            LOG.warn("structured-output schema violation errors={}", errors);
            throw new IllegalStateException("LLM output failed JSON Schema validation: " + errors);
        }
    }
}
