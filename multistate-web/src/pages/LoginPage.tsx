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
    <main className="page page--center">
      <div className="shell shell--narrow">
        <div className="card auth-card">
          <span className="auth-card__mark" aria-hidden="true">
            ▦
          </span>
          <p className="tenant__eyebrow">Multi-State Tax Tracker</p>
          <h1 className="auth-card__title">Sign in</h1>
          <p className="auth-card__sub">
            Track where remote workers earned income and allocate it to the
            right jurisdiction at year end.
          </p>
          <button
            type="button"
            className="btn btn--primary btn--block"
            onClick={signIn}
          >
            Sign in (stub)
          </button>
          <p className="auth-card__hint">
            Demo build — no credentials required.
          </p>
        </div>
      </div>
    </main>
  );
}
