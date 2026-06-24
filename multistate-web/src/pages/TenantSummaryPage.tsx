// src/pages/TenantSummaryPage.tsx
import type { ReactElement } from 'react';
import { useParams } from 'react-router-dom';
import { useSummarizeTenantMutation } from '../gql/operations';
import type { SummarizeTenantMutation } from '../gql/generated/graphql';

export function TenantSummaryPage(): ReactElement {
  const { id = '' } = useParams<{ id: string }>();

  // The placeholder doubles as the optimisticResponse payload (so Apollo can
  // normalise the optimistic write — note the __typename) AND as the card we
  // paint while the mutation is in flight. In Apollo Client v4 a mutation's
  // optimisticResponse is no longer surfaced through the hook's `data`, so we
  // drive the placeholder off `loading` and let the real value swap in.
  const placeholder: SummarizeTenantMutation['summarizeTenant'] = {
    __typename: 'TenantSummary',
    id,
    summaryText: '…thinking…',
    confidence: 'MEDIUM',
  };

  const [summarize, { loading, data, error }] = useSummarizeTenantMutation({
    variables: { id },
    optimisticResponse: { summarizeTenant: placeholder },
  });

  // Real result once it lands; otherwise the placeholder while in flight.
  const summary = data?.summarizeTenant ?? (loading ? placeholder : undefined);

  return (
    <main className="page">
      <button
        type="button"
        onClick={() => {
          void summarize();
        }}
        disabled={loading}
      >
        Summarize
      </button>

      {error && <p role="alert">Error: {error.message}</p>}

      {summary && (
        <section aria-label="tenant-summary" className="summary-card">
          <p>{summary.summaryText}</p>
          <p>confidence: {summary.confidence}</p>
        </section>
      )}
    </main>
  );
}
