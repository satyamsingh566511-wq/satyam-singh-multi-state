// src/apollo/client.ts
import {
    ApolloClient,
    InMemoryCache,
    HttpLink,
    from,
} from '@apollo/client';
import { setContext } from '@apollo/client/link/context';

// THREAT MODEL: storing the JWT in localStorage exposes it to any XSS
// that runs on the page. We accept that today because the W6 cookie
// story (HttpOnly, SameSite=Strict, server-set) isn't built yet —
// see §9 Sticking Points.
// Relative URI so the request is same-origin and rides the Vite dev proxy
// (see vite.config.ts), which forwards /graphql to the Spring backend on :8080.
// This avoids needing CORS on the backend.
const httpLink = new HttpLink({ uri: '/graphql' });

const authLink = setContext((_op, prevContext) => {
    const token = localStorage.getItem('uc:jwt');
    // DefaultContext extends Record<string, any>, so `headers` is `any`;
    // narrow it before spreading to satisfy no-unsafe-assignment.
    const headers = prevContext.headers as Record<string, string> | undefined;
    return {
        headers: {
            ...headers,
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
    };
});

export const apolloClient = new ApolloClient({
    link: from([authLink, httpLink]),
    cache: new InMemoryCache({
        typePolicies: {
            Tenant: { keyFields: ['id'] },
        },
    }),
});