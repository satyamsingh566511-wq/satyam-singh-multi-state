// src/hooks/useGetMultiStateRest.ts
import { useQuery } from '@tanstack/react-query';

export type TenantRest = {
    readonly id: string;
    readonly name: string;
    readonly updatedAt: string;
};

export function useGetMultiStateRest(id: string) {
    return useQuery({
        queryKey: ['multistate', id],
        enabled: Boolean(id),
        queryFn: async () => {
            const res = await fetch(`http://localhost:8080/api/v1/tenants/${id}`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return (await res.json()) as TenantRest;
        },
    });
}

// The list variant used by the TenantSummaryPage roster table (W4 D5). Hits the
// same Spring REST surface but the collection endpoint, so the summary page can
// render every tenant as a row without a per-id round trip.
export function useTenantsRest() {
    return useQuery({
        queryKey: ['multistate', 'tenants'],
        queryFn: async () => {
            const res = await fetch('/api/v1/tenants');
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return (await res.json()) as readonly TenantRest[];
        },
    });
}