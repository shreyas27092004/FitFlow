import { describe, it, expect, vi, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { setupStore } from '../../../app/store';
import { CheckoutLauncher } from '../CheckoutLauncher';
import { PaymentResultPage } from '../PaymentResultPage';
import { expectNoA11yViolations } from '../../../test/setup';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { handlers } from '../../../test/handlers';

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Membership Preview & Checkout State Machine', () => {
  // Test 1: Numbers Verbatim Assertion
  it('renders backend preview numbers VERBATIM without client arithmetic', async () => {
    // Deliberately asymmetric/arbitrary numbers that would never be produced by standard arithmetic
    const mockArbitraryPreview = {
      targetPlanId: 'plan_test',
      targetPlanName: 'Arbitrary Tier',
      targetPricePaise: 77777, // Rs 777.77
      creditPaise: 12345,      // -Rs 123.45
      amountDuePaise: 99999,   // Arbitrary amount due: Rs 999.99 (proves UI did NOT do 77777 - 12345 = 65432!)
      refundPaise: 54321,      // Rs 543.21
      usedRatio: 0.25,
      unusedRatio: 0.75,
      explanationLines: ['Line 1 from backend engine', 'Line 2 from backend engine'],
      cappedByCashPaise: true,
    };

    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter>
          <CheckoutLauncher
            targetPlanId="plan_test"
            targetPlanName="Arbitrary Tier"
            preview={mockArbitraryPreview}
            onClose={() => {}}
          />
        </MemoryRouter>
      </Provider>
    );

    // Verify all figures follow the backend numbers exactly
    expect(screen.getByTestId('target-price')).toHaveTextContent('Rs 777.77');
    expect(screen.getByTestId('credit-applied')).toHaveTextContent('-Rs 123.45');
    expect(screen.getByTestId('refund-amount')).toHaveTextContent('Rs 543.21');
    expect(screen.getByTestId('amount-due')).toHaveTextContent('Rs 999.99');

    // Verify explanation lines
    expect(screen.getByText('Line 1 from backend engine')).toBeInTheDocument();
    expect(screen.getByText(/Refund is capped at the total cash/i)).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });

  // Test 2: MOCK Gateway Panel Renders in MOCK mode
  it('renders MOCK test payment panel when gateway is MOCK', async () => {
    server.use(
      http.post('/api/v1/memberships/change', () => {
        return HttpResponse.json({ status: 'REQUIRES_PAYMENT', paymentId: 'pay_mock_1' }, { status: 202 });
      }),
      http.get('/api/v1/payments/pay_mock_1/client-payload', () => {
        return HttpResponse.json({
          paymentId: 'pay_mock_1',
          gateway: 'MOCK',
          amountPaise: 68713,
          currency: 'INR',
        });
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <CheckoutLauncher
            targetPlanId="plan_unlimited"
            targetPlanName="Unlimited Flow"
            preview={{
              targetPlanId: 'plan_unlimited',
              targetPlanName: 'Unlimited Flow',
              targetPricePaise: 99900,
              creditPaise: 31187,
              amountDuePaise: 68713,
              refundPaise: 0,
              usedRatio: 0.375,
              unusedRatio: 0.625,
              explanationLines: [],
            }}
            onClose={() => {}}
          />
        </MemoryRouter>
      </Provider>
    );

    const payBtn = screen.getByRole('button', { name: /Pay Rs 687.13/i });
    fireEvent.click(payBtn);

    // Assert mock test panel appears
    expect(await screen.findByTestId('mock-gateway-panel')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Success \(tok_ok\)/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Decline \(tok_fail\)/i })).toBeInTheDocument();
  });

  // Test 3: MOCK Gateway Panel MUST NEVER Render When Gateway is RAZORPAY!
  it('MUST NEVER render MOCK panel when gateway is RAZORPAY', async () => {
    server.use(
      http.post('/api/v1/memberships/change', () => {
        return HttpResponse.json({ status: 'REQUIRES_PAYMENT', paymentId: 'pay_real_rzp' }, { status: 202 });
      }),
      http.get('/api/v1/payments/pay_real_rzp/client-payload', () => {
        return HttpResponse.json({
          paymentId: 'pay_real_rzp',
          gateway: 'RAZORPAY', // Real gateway!
          amountPaise: 68713,
          currency: 'INR',
          keyId: 'rzp_live_key',
          orderId: 'order_123',
        });
      })
    );

    // Mock window.Razorpay
    window.Razorpay = vi.fn().mockImplementation(() => ({
      open: vi.fn(),
    }));

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <CheckoutLauncher
            targetPlanId="plan_unlimited"
            targetPlanName="Unlimited Flow"
            preview={{
              targetPlanId: 'plan_unlimited',
              targetPlanName: 'Unlimited Flow',
              targetPricePaise: 99900,
              creditPaise: 31187,
              amountDuePaise: 68713,
              refundPaise: 0,
              usedRatio: 0.375,
              unusedRatio: 0.625,
              explanationLines: [],
            }}
            onClose={() => {}}
          />
        </MemoryRouter>
      </Provider>
    );

    const payBtn = screen.getByRole('button', { name: /Pay Rs 687.13/i });
    fireEvent.click(payBtn);

    // Wait for real Razorpay modal to open
    await waitFor(() => {
      expect(window.Razorpay).toHaveBeenCalled();
    });

    // Assert mock test panel is NEVER rendered when gateway is RAZORPAY
    expect(screen.queryByTestId('mock-gateway-panel')).toBeNull();
  });

  // Test 4: Handles 409 Conflict (Stale Preview)
  it('handles 409 CONFLICT: prompts user with updated preview numbers', async () => {
    server.use(
      http.post('/api/v1/memberships/change', () => {
        return HttpResponse.json(
          {
            status: 409,
            code: 'STALE_PREVIEW',
            detail: 'Preview has expired due to session credit activity.',
          },
          { status: 409 }
        );
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter>
          <CheckoutLauncher
            targetPlanId="plan_unlimited"
            targetPlanName="Unlimited Flow"
            preview={{
              targetPlanId: 'plan_unlimited',
              targetPlanName: 'Unlimited Flow',
              targetPricePaise: 99900,
              creditPaise: 31187,
              amountDuePaise: 68713,
              refundPaise: 0,
              usedRatio: 0.375,
              unusedRatio: 0.625,
              explanationLines: [],
            }}
            onClose={() => {}}
          />
        </MemoryRouter>
      </Provider>
    );

    const payBtn = screen.getByRole('button', { name: /Pay Rs 687.13/i });
    fireEvent.click(payBtn);

    expect(
      await screen.findByText(/Membership Preview Updated \(409 Conflict\)/i)
    ).toBeInTheDocument();
  });

  // Test 5: Payment Result Page shows invoice number & total verbatim
  it('PaymentResultPage: renders invoice number and formatted total from backend', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/membership/payment-result?paymentId=pay_123&status=SUCCEEDED']}>
          <Routes>
            <Route path="/app/membership/payment-result" element={<PaymentResultPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(await screen.findByText('Payment Succeeded!')).toBeInTheDocument();
    expect(screen.getByText('INV-2026-001')).toBeInTheDocument();
    expect(screen.getByText('Rs 687.13')).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });
});
