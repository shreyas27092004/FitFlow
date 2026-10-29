import { describe, it, expect, vi, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { setupStore } from '../../../app/store';
import { AccountPage } from '../AccountPage';
import { expectNoA11yViolations } from '../../../test/setup';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { handlers } from '../../../test/handlers';

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Account Self-Service & DPDP Data Rights', () => {
  it('AccountPage: renders profile with optimistic lock version and updates name', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <AccountPage />
        </BrowserRouter>
      </Provider>
    );

    expect(await screen.findByDisplayValue('Test Member')).toBeInTheDocument();
    expect(screen.getByText(/Record Version: 1/i)).toBeInTheDocument();

    await expectNoA11yViolations(container);

    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'Updated Member Name' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save Profile Changes' }));

    expect(await screen.findByText('Profile updated successfully!')).toBeInTheDocument();
  });

  it('AccountPage: handles 409 OPTIMISTIC_LOCK conflict gracefully', async () => {
    server.use(
      http.patch('/api/v1/me', () => {
        return HttpResponse.json(
          {
            status: 409,
            code: 'OPTIMISTIC_LOCK',
            detail: 'Schedule updated by another user. Reloading latest availability.',
          },
          { status: 409 }
        );
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <AccountPage />
        </BrowserRouter>
      </Provider>
    );

    expect(await screen.findByDisplayValue('Test Member')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'Conflicted Name' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save Profile Changes' }));

    expect(
      await screen.findByText(/Profile was updated elsewhere/i)
    ).toBeInTheDocument();
  });

  it('AccountPage: changes password when confirmation matches', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <AccountPage />
        </BrowserRouter>
      </Provider>
    );

    expect(await screen.findByDisplayValue('Test Member')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Current Password/i), { target: { value: 'OldPassword123' } });
    fireEvent.change(screen.getByLabelText(/^New Password/i), { target: { value: 'BrandNewPassword123' } });
    fireEvent.change(screen.getByLabelText(/Confirm New Password/i), { target: { value: 'BrandNewPassword123' } });

    fireEvent.click(screen.getByRole('button', { name: 'Update Password' }));

    expect(await screen.findByText('Password changed successfully!')).toBeInTheDocument();
  });

  it('AccountPage: triggers DPDP JSON data export download', async () => {
    const store = setupStore();
    render(
      <Provider store={store}>
        <BrowserRouter>
          <AccountPage />
        </BrowserRouter>
      </Provider>
    );

    const exportBtn = await screen.findByRole('button', { name: /Export Data \(JSON\)/i });

    // Mock createObjectURL and prevent JSDOM navigation error on a.click()
    window.URL.createObjectURL = vi.fn().mockReturnValue('blob:mock-url');
    window.URL.revokeObjectURL = vi.fn();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    fireEvent.click(exportBtn);

    await waitFor(() => {
      expect(window.URL.createObjectURL).toHaveBeenCalled();
      expect(clickSpy).toHaveBeenCalled();
    });

    clickSpy.mockRestore();
  });

  it('AccountPage: opens deletion modal and requires typed confirmation "DELETE"', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <AccountPage />
        </BrowserRouter>
      </Provider>
    );

    const deleteBtn = await screen.findByRole('button', { name: 'Delete Account' });
    fireEvent.click(deleteBtn);

    expect(await screen.findByText('Delete Account Permanently?')).toBeInTheDocument();
    const confirmDeleteBtn = screen.getByRole('button', { name: 'Permanently Delete My Account' });
    expect(confirmDeleteBtn).toBeDisabled();

    // Type "DELETE"
    const confirmInput = screen.getByLabelText('Confirmation Word');
    fireEvent.change(confirmInput, { target: { value: 'DELETE' } });
    expect(confirmDeleteBtn).not.toBeDisabled();

    // Enter password
    const passwordInput = screen.getByLabelText(/Re-enter Account Password/i);
    fireEvent.change(passwordInput, { target: { value: 'ValidPassword123' } });

    fireEvent.click(confirmDeleteBtn);

    await expectNoA11yViolations(container);
  });
});
