import { describe, it, expect, vi, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter, MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { setupStore } from '../../../app/store';
import { SignInPage } from '../SignInPage';
import { RegisterPage } from '../RegisterPage';
import { ForgotPasswordPage } from '../ForgotPasswordPage';
import { ResetPasswordPage } from '../ResetPasswordPage';
import { ForcedPasswordChangePage } from '../ForcedPasswordChangePage';
import { expectNoA11yViolations } from '../../../test/setup';
import { setupServer } from 'msw/node';
import { handlers } from '../../../test/handlers';

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Auth Screens Suite', () => {
  // --- Sign In ---
  it('SignInPage: renders and validates required fields', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <SignInPage />
        </BrowserRouter>
      </Provider>
    );

    await expectNoA11yViolations(container);

    fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));
    expect(await screen.findByText('Please enter a valid email address')).toBeInTheDocument();
  });

  it('SignInPage: shows server error on invalid credentials', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <SignInPage />
        </BrowserRouter>
      </Provider>
    );

    fireEvent.change(screen.getByLabelText(/Email Address/i), {
      target: { value: 'member@fitflow.test' },
    });
    fireEvent.change(screen.getByLabelText(/^Password/i), {
      target: { value: 'WrongPass123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));

    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument();
  });

  it('SignInPage: displays account lockout countdown on 429 / locked status', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <SignInPage />
        </BrowserRouter>
      </Provider>
    );

    fireEvent.change(screen.getByLabelText(/Email Address/i), {
      target: { value: 'locked@fitflow.test' },
    });
    fireEvent.change(screen.getByLabelText(/^Password/i), {
      target: { value: 'AnyPass12345' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));

    expect(await screen.findByText(/Account temporarily locked/i)).toBeInTheDocument();
    expect(screen.getByText(/Retry available in/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign In' })).toBeDisabled();
  });

  it('SignInPage: signs in successfully and stores access token in Redux memory only', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <SignInPage />
        </BrowserRouter>
      </Provider>
    );

    fireEvent.change(screen.getByLabelText(/Email Address/i), {
      target: { value: 'member@fitflow.test' },
    });
    fireEvent.change(screen.getByLabelText(/^Password/i), {
      target: { value: 'ValidPass12345' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));

    await waitFor(() => {
      expect(store.getState().auth.isAuthenticated).toBe(true);
      expect(store.getState().auth.accessToken).toBe('fake-jwt-token-12345');
    });

    // Verify token was NOT stored in localStorage or sessionStorage
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('accessToken')).toBeNull();
  });

  // --- Register ---
  it('RegisterPage: validates minimum age >= 14', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <RegisterPage />
        </BrowserRouter>
      </Provider>
    );

    await expectNoA11yViolations(container);

    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'Young User' } });
    fireEvent.change(screen.getByLabelText(/Email Address/i), { target: { value: 'young@fitflow.test' } });

    // Under 14 (born 2 years ago)
    const tooYoung = new Date();
    tooYoung.setFullYear(tooYoung.getFullYear() - 10);
    fireEvent.change(screen.getByLabelText(/Date of Birth/i), {
      target: { value: tooYoung.toISOString().slice(0, 10) },
    });

    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'ValidPass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Account' }));

    expect(await screen.findByText(/You must be at least 14 years old/i)).toBeInTheDocument();
  });

  it('RegisterPage: rejects password that matches email or lacks digits', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <RegisterPage />
        </BrowserRouter>
      </Provider>
    );

    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'Valid User' } });
    fireEvent.change(screen.getByLabelText(/Date of Birth/i), { target: { value: '1995-05-15' } });
    fireEvent.change(screen.getByLabelText(/Email Address/i), { target: { value: 'passwordsame1@fitflow.test' } });
    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'passwordsame1@fitflow.test' } });

    fireEvent.click(screen.getByRole('button', { name: 'Create Account' }));
    expect(await screen.findByText(/Password must not match your email address/i)).toBeInTheDocument();
  });

  it('RegisterPage: registers successfully with valid data', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <RegisterPage />
        </BrowserRouter>
      </Provider>
    );

    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'New Member' } });
    fireEvent.change(screen.getByLabelText(/Email Address/i), { target: { value: 'newmember@fitflow.test' } });

    const validDob = new Date();
    validDob.setFullYear(validDob.getFullYear() - 25);
    fireEvent.change(screen.getByLabelText(/Date of Birth/i), {
      target: { value: validDob.toISOString().slice(0, 10) },
    });

    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'StrongPassword123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Account' }));

    await waitFor(() => {
      expect(store.getState().auth.isAuthenticated).toBe(true);
    });
  });

  // --- Forgot Password ---
  it('ForgotPasswordPage: submits and never reveals if email exists', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <ForgotPasswordPage />
        </BrowserRouter>
      </Provider>
    );

    await expectNoA11yViolations(container);

    fireEvent.change(screen.getByLabelText(/Email Address/i), {
      target: { value: 'nonexistent@fitflow.test' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send Reset Link' }));

    expect(await screen.findByText('Check your inbox')).toBeInTheDocument();
  });

  // --- Reset Password ---
  it('ResetPasswordPage: reads token from query and clears it via history.replaceState', async () => {
    const store = setupStore();
    const replaceStateSpy = vi.spyOn(window.history, 'replaceState');

    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/reset-password?token=secret-reset-token-xyz']}>
          <Routes>
            <Route path="/reset-password" element={<ResetPasswordPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    // Assert history.replaceState was called to scrub token from address bar
    expect(replaceStateSpy).toHaveBeenCalled();

    await expectNoA11yViolations(container);

    fireEvent.change(screen.getByLabelText(/^New Password/i), {
      target: { value: 'NewBrandNewPass123' },
    });
    fireEvent.change(screen.getByLabelText(/Confirm New Password/i), {
      target: { value: 'NewBrandNewPass123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Update Password' }));

    expect(await screen.findByText(/Password successfully reset/i)).toBeInTheDocument();
  });

  // --- Forced Password Change ---
  it('ForcedPasswordChangePage: validates and updates temporary password', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <ForcedPasswordChangePage />
        </BrowserRouter>
      </Provider>
    );

    await expectNoA11yViolations(container);

    fireEvent.change(screen.getByLabelText(/Temporary Password/i), {
      target: { value: 'TempPass123' },
    });
    fireEvent.change(screen.getByLabelText(/^New Password/i), {
      target: { value: 'PermanentPass123' },
    });
    fireEvent.change(screen.getByLabelText(/Confirm New Password/i), {
      target: { value: 'PermanentPass123' },
    });

    fireEvent.click(screen.getByRole('button', { name: /Set New Password/i }));
    await waitFor(() => {
      expect(screen.queryByText(/Password must/i)).not.toBeInTheDocument();
    });
  });
});
