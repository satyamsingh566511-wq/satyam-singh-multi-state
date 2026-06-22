package com.uptimecrew.multistate.graphql;

import com.uptimecrew.multistate.llm.LlmSummaryService;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.service.AllocationService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class TenantGraphQlController {

    private static final Logger LOG = LoggerFactory.getLogger(TenantGraphQlController.class);

    private final AllocationService service;
    private final LlmSummaryService llmSummary;

    public TenantGraphQlController(AllocationService service, LlmSummaryService llmSummary) {
        this.service = service;
        this.llmSummary = llmSummary;
    }

    @QueryMapping
    public TenantReadModel tenant(@Argument String id) {
        LOG.info("graphql query tenant id={}", id);
        return service.findById(id).orElse(null);
    }

    @QueryMapping
    public List<TenantReadModel> latestTenants(@Argument Integer limit) {
        return service.findLatest(limit == null ? 10 : limit);
    }

    @QueryMapping
    public List<TenantReadModel> tenantsByTag(@Argument String tag) {
        LOG.info("graphql query tenantsByTag tag={}", tag);
        return service.tenantsByTag(tag);
    }

    @MutationMapping
    public TenantSummary summarizeTenant(@Argument String id) {
        LOG.info("graphql mutation summarizeTenant id={}", id);
        return llmSummary.summarize(id);
    }

    // @BatchMapping over a manual DataLoader registration: Spring for GraphQL
    // collects every parent Tenant resolved in one generation and invokes this
    // method ONCE with the full list, so the resolver hits the repository a single
    // time (one WHERE tenant_id IN (...) SELECT) instead of once per parent. We get
    // the batching/dedup that a DataLoader provides without hand-writing a
    // BatchLoader, registering it in the DataLoaderRegistry, or threading the
    // loader key type through the resolver — the mapping is declared right next to
    // the field it resolves.
    @BatchMapping(typeName = "Tenant", field = "lines")
    public Map<TenantReadModel, List<LineItem>> lines(List<TenantReadModel> parents) {
        return service.loadLineItemsByParent(parents);
    }
}
