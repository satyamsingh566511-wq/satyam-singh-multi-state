// src/pages/LoginPage.test.tsx
import { describe, it, expect, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import { LoginPage } from './LoginPage';

describe('LoginPage', () => {
  beforeEach(() => window.localStorage.clear());

  it('renders the sign-in heading and credential fields', () => {
    renderWithProviders(<LoginPage />, { route: '/login' });
    expect(
      screen.getByRole('heading', { name: /sign in/i }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('lets the engineer type into the email and password fields', async () => {
    const { user } = renderWithProviders(<LoginPage />, { route: '/login' });

    await user.type(screen.getByLabelText(/email/i), 'eng@example.test');
    await user.type(screen.getByLabelText(/password/i), 'hunter2');

    expect(screen.getByLabelText(/email/i)).toHaveValue('eng@example.test');
    expect(screen.getByLabelText(/password/i)).toHaveValue('hunter2');
  });

  it('persists the stub JWT on submit so the protected layout unlocks', async () => {
    const { user } = renderWithProviders(<LoginPage />, { route: '/login' });

    await user.type(screen.getByLabelText(/email/i), 'eng@example.test');
    await user.type(screen.getByLabelText(/password/i), 'hunter2');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(window.localStorage.getItem('uc:jwt')).not.toBeNull();
  });
});
