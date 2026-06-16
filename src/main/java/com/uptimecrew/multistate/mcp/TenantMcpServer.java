package com.uptimecrew.multistate.mcp;

import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.service.AllocationService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class TenantMcpServer {

    private static final Logger LOG = LoggerFactory.getLogger(TenantMcpServer.class);

    private final AllocationService service;

    public TenantMcpServer(AllocationService service) {
        this.service = service;
    }

    @Tool(description = "Look up a tenant by id and return its summary read model")
    public Optional<TenantReadModel> lookupTenant(
            @ToolParam(description = "The tenant id") String id) {
        LOG.info("mcp tool lookupTenant invoked id={}", id);
        return service.findById(id);
    }
}
