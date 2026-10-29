import { describe, it, expect, vi, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { setupStore } from '../app/store';
import { RequireAuth } from '../layouts/RequireAuth';
import { RequireRole } from '../layouts/RequireRole';
import { setCredentials, clearCredentials } from '../features/auth/authSlice';
import { ClassDetailsPage } from '../features/classes/ClassDetailsPage';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

const server = setupServer(
  http.get('/api/v1/classes/cls_xss', () => {
    return HttpResponse.json({
      id: 'cls_xss',
      title: 'XSS Test Class',
      description: '<img src=x onerror=alert(1)>',
      category: 'YOGA',
      intensity: 'LOW',
      durationMinutes: 60,
      capacityDefault: 20,
      active: true,
    });
  }),
  http.get('/api/v1/sessions', () => {
    return HttpResponse.json({ content: [], totalElements: 0 });
  })
);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Security Guarantees & XSS Prevention', () => {
  it('Tokens are strictly in-memory: NEVER written to localStorage or sessionStorage', () => {
    const localSpy = vi.spyOn(Storage.prototype, 'setItem');
    const sessionSpy = vi.spyOn(window.sessionStorage, 'setItem');

    const store = setupStore();
    store.dispatch(
      setCredentials({
        accessToken: 'super-secret-jwt-token-xyz',
        user: { id: 'usr_1', email: 'test@fitflow.test', fullName: 'Tester', roles: ['MEMBER'] },
      })
    );

    // Verify state has token in memory
    expect(store.getState().auth.accessToken).toBe('super-secret-jwt-token-xyz');

    // Assert that setItem was NEVER called with 'accessToken' or 'refreshToken' or any token string
    for (const call of localSpy.mock.calls) {
      expect(String(call[0])).not.toMatch(/token/i);
      expect(String(call[1])).not.toMatch(/super-secret-jwt-token-xyz/);
    }
    for (const call of sessionSpy.mock.calls) {
      expect(String(call[0])).not.toMatch(/token/i);
      expect(String(call[1])).not.toMatch(/super-secret-jwt-token-xyz/);
    }

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('accessToken')).toBeNull();
  });

  it('XSS prevention: class description containing <img src=x onerror=alert(1)> is safely rendered as plain text', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/classes/cls_xss']}>
          <Routes>
            <Route path="/app/classes/:id" element={<ClassDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    // Find the description text element
    const xssText = await screen.findByText('<img src=x onerror=alert(1)>');
    expect(xssText).toBeInTheDocument();

    // Verify that NO <img> tag was injected into the DOM
    const imgElement = document.querySelector('img[src="x"]');
    expect(imgElement).toBeNull();
  });

  it('Route guards: RequireAuth redirects unauthenticated user to /login', () => {
    const store = setupStore();
    store.dispatch(clearCredentials());

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/dashboard']}>
          <Routes>
            <Route
              path="/app/dashboard"
              element={
                <RequireAuth>
                  <div>Protected Member Dashboard</div>
                </RequireAuth>
              }
            />
            <Route path="/login" element={<div>Sign In Screen</div>} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Sign In Screen')).toBeInTheDocument();
    expect(screen.queryByText('Protected Member Dashboard')).toBeNull();
  });

  it('Route guards: RequireRole routes TRAINER to /trainer/dashboard when accessing MEMBER route', () => {
    const store = setupStore();
    store.dispatch(
      setCredentials({
        accessToken: 'trainer-token',
        user: { id: 'usr_trainer', email: 'trainer@fitflow.test', fullName: 'Trainer User', roles: ['TRAINER'] },
      })
    );

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/dashboard']}>
          <Routes>
            <Route
              path="/app/dashboard"
              element={
                <RequireAuth>
                  <RequireRole roles={['MEMBER']}>
                    <div>Member Only Dashboard</div>
                  </RequireRole>
                </RequireAuth>
              }
            />
            <Route path="/trainer/dashboard" element={<div>Trainer Workspace Home</div>} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Trainer Workspace Home')).toBeInTheDocument();
    expect(screen.queryByText('Member Only Dashboard')).toBeNull();
  });
});
