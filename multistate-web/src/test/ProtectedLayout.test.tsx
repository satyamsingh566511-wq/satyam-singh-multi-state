// src/test/ProtectedLayout.test.tsx
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ProtectedLayout } from '../router';

function renderAtTenants(): void {
  render(
    <MemoryRouter initialEntries={['/tenants']}>
      <Routes>
        <Route element={<ProtectedLayout />}>
          <Route path="/tenants" element={<div>protected content</div>} />
        </Route>
        <Route path="/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedLayout', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('redirects to /login when no JWT is present', () => {
    renderAtTenants();
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('protected content')).not.toBeInTheDocument();
  });

  it('renders the outlet when a JWT is present', () => {
    localStorage.setItem('uc:jwt', 'stub-jwt-token');
    renderAtTenants();
    expect(screen.getByText('protected content')).toBeInTheDocument();
    expect(screen.queryByText('login page')).not.toBeInTheDocument();
  });
});
