// src/gql/operations.ts
//
// The client-preset emits typed documents, not React hooks, and the legacy
// typescript-react-apollo plugin imports hooks from the v3 entry point that
// Apollo Client v4 moved to `@apollo/client/react`. These thin, fully-typed
// wrappers give the pages the conventional `useLatestTenantsQuery` /
// `useSummarizeTenantMutation` API over the generated documents.
import { useQuery, useMutation } from '@apollo/client/react';
import {
  LatestTenantsDocument,
  SummarizeTenantDocument,
  type LatestTenantsQuery,
  type LatestTenantsQueryVariables,
  type SummarizeTenantMutation,
  type SummarizeTenantMutationVariables,
} from './generated/graphql';

export function useLatestTenantsQuery(
  options?: useQuery.Options<LatestTenantsQuery, LatestTenantsQueryVariables>,
) {
  return useQuery(LatestTenantsDocument, options);
}

export function useSummarizeTenantMutation(
  options?: useMutation.Options<
    SummarizeTenantMutation,
    SummarizeTenantMutationVariables
  >,
) {
  return useMutation(SummarizeTenantDocument, options);
}
