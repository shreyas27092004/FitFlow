import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { handlers } from '../../../test/handlers';
import { setupStore } from '../../../app/store';
import { PaymentsPage } from '../PaymentsPage';
import { PaymentResultPage } from '../../membership/PaymentResultPage';
import { expectNoA11yViolations } from '../../../test/setup';

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Payments & Receipts Suite', () => {
  const createTestStore = () =>
    setupStore({
      auth: {
        accessToken: 'valid-token',
        user: {
          id: 'mem_1',
          email: 'member@fitflow.test',
          fullName: 'Aarav Sharma',
          roles: ['MEMBER'],
          mustChangePassword: false,
        },
      },
    });

  // 1. PaymentsPage: renders payments table with date, amount in paise, status badge
  it('PaymentsPage: renders payments history table with formatted paise', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <PaymentsPage />
        </MemoryRouter>
      </Provider>
    );

    // Initial loading skeleton or pulse
    expect(document.querySelector('.animate-pulse')).toBeInTheDocument();

    // Succeeded payment row should display
    await waitFor(() => {
      expect(screen.getByText('INV-2026-001')).toBeInTheDocument();
    });

    // Check paise formatting: 49900 paise -> Rs 499.00
    expect(screen.getByText('Rs 499.00')).toBeInTheDocument();
    expect(screen.getByText('SUCCEEDED')).toBeInTheDocument();
  });

  // 2. PaymentsPage: view invoice modal opens with breakdown
  it('PaymentsPage: opens invoice modal on button click', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <PaymentsPage />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /INV-2026-001/i })).toBeInTheDocument();
    });

    const viewBtn = screen.getByRole('button', { name: /INV-2026-001/i });
    fireEvent.click(viewBtn);

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Starter Flow Monthly Studio Membership')).toBeInTheDocument();
    });
  });

  // 3. PaymentsPage: empty state
  it('PaymentsPage: displays empty state when member has zero payments', async () => {
    server.use(
      http.get('/api/v1/me/payments', () => {
        return HttpResponse.json({ content: [], totalElements: 0 });
      })
    );

    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <PaymentsPage />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('No past payments found')).toBeInTheDocument();
    });

    expect(screen.getByText('No past invoices found for this account.')).toBeInTheDocument();
  });

  // 4. PaymentsPage: accessibility
  it('PaymentsPage: passes axe-core accessibility checks', async () => {
    const store = createTestStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter>
          <PaymentsPage />
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Payments & Invoices')).toBeInTheDocument();
    });

    await expectNoA11yViolations(container);
  });

  // 5. PaymentResultPage: success mode
  it('PaymentResultPage: renders success state when status is SUCCEEDED', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/payments/result?paymentId=pay_123']}>
          <Routes>
            <Route path="/app/payments/result" element={<PaymentResultPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Payment Succeeded!')).toBeInTheDocument();
    });

    expect(screen.getByText('INV-2026-001')).toBeInTheDocument();
    // 68713 paise -> Rs 687.13
    expect(screen.getByText('Rs 687.13')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Book Your First Class/i })).toBeInTheDocument();
  });

  // 6. PaymentResultPage: fallback to query param status
  it('PaymentResultPage: renders success from query parameters if payment query skipped', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/payments/result?status=SUCCEEDED']}>
          <Routes>
            <Route path="/app/payments/result" element={<PaymentResultPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Payment Succeeded!')).toBeInTheDocument();
  });

  // 7. PaymentResultPage: failed state
  it('PaymentResultPage: renders failure state with retry option when payment failed', async () => {
    server.use(
      http.get('/api/v1/payments/:id', () => {
        return HttpResponse.json({
          id: 'pay_failed',
          amountPaise: 49900,
          currency: 'INR',
          status: 'FAILED',
          gateway: 'RAZORPAY',
          createdAt: '2026-10-29T12:00:00Z',
        });
      })
    );

    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/payments/result?paymentId=pay_failed']}>
          <Routes>
            <Route path="/app/payments/result" element={<PaymentResultPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Payment Failed')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /Return to Membership Plans/i })).toBeInTheDocument();
  });

  // 8. PaymentResultPage: accessibility
  it('PaymentResultPage: passes axe-core accessibility checks', async () => {
    const store = createTestStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/payments/result?paymentId=pay_123']}>
          <Routes>
            <Route path="/app/payments/result" element={<PaymentResultPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Payment Succeeded!')).toBeInTheDocument();
    });

    await expectNoA11yViolations(container);
  });
});
