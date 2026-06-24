// src/pages/LoginPage.tsx
import type { ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';

export function LoginPage(): ReactElement {
  const navigate = useNavigate();

  // THREAT MODEL note: see src/apollo/client.ts — JWT-in-localStorage is an
  // XSS exposure we accept until W6 wires HttpOnly cookies. This stub token
  // stands in for a real auth exchange until the W6 login flow lands.
  const signIn = (): void => {
    localStorage.setItem('uc:jwt', 'stub-jwt-token');
    void navigate('/tenants', { replace: true });
  };

  return (
    <main className="page">
      <h1>Sign in</h1>
      <button type="button" onClick={signIn}>
        Sign in (stub)
      </button>
    </main>
  );
}
