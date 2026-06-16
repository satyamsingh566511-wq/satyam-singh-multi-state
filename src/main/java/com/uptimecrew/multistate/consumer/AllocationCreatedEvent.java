package com.uptimecrew.multistate.consumer;

import com.uptimecrew.multistate.model.IncomeAllocation;
import java.util.List;

public record AllocationCreatedEvent(
        String aggregateId,
        List<IncomeAllocation> allocations) {}
