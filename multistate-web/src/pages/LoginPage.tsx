// src/pages/LoginPage.tsx
import type { FormEvent, ReactElement } from 'react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export function LoginPage(): ReactElement {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  // THREAT MODEL note: see src/apollo/client.ts — JWT-in-localStorage is an
  // XSS exposure we accept until W6 wires HttpOnly cookies. This stub token
  // stands in for a real auth exchange until the W6 login flow lands; the
  // email/password fields are real form controls so the W4 D5 E2E can drive a
  // realistic sign-in, but any non-empty pair is accepted in this demo build.
  const signIn = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    localStorage.setItem('uc:jwt', 'stub-jwt-token');
    void navigate('/tenants', { replace: true });
  };

  return (
    <main className="page page--center">
      <div className="shell shell--narrow">
        <form className="card auth-card" onSubmit={signIn}>
          <span className="auth-card__mark" aria-hidden="true">
            ▦
          </span>
          <p className="tenant__eyebrow">Multi-State Tax Tracker</p>
          <h1 className="auth-card__title">Sign in</h1>
          <p className="auth-card__sub">
            Track where remote workers earned income and allocate it to the
            right jurisdiction at year end.
          </p>

          <div className="field">
            <label className="field__label" htmlFor="login-email">
              Email
            </label>
            <input
              id="login-email"
              className="input"
              type="email"
              name="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.currentTarget.value)}
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="login-password">
              Password
            </label>
            <input
              id="login-password"
              className="input"
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.currentTarget.value)}
            />
          </div>

          <button type="submit" className="btn btn--primary btn--block">
            Sign in
          </button>
          <p className="auth-card__hint">
            Demo build — any credentials are accepted.
          </p>
        </form>
      </div>
    </main>
  );
}
