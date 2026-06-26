// src/gql/operations.ts
//
// The client-preset emits typed documents, not React hooks, and the legacy
// typescript-react-apollo plugin imports hooks from the v3 entry point that
// Apollo Client v4 moved to `@apollo/client/react`. This thin, fully-typed
// wrapper gives the list page the conventional `useLatestTenantsQuery` API over
// the generated document.
import { useQuery } from '@apollo/client/react';
import {
  LatestTenantsDocument,
  type LatestTenantsQuery,
  type LatestTenantsQueryVariables,
} from './generated/graphql';

export function useLatestTenantsQuery(
  options?: useQuery.Options<LatestTenantsQuery, LatestTenantsQueryVariables>,
) {
  return useQuery(LatestTenantsDocument, options);
}
