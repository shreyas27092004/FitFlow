import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { setupStore } from '../../../app/store';
import { SessionDetailsPage } from '../../sessions/SessionDetailsPage';
import { MyBookingsPage } from '../MyBookingsPage';
import { expectNoA11yViolations } from '../../../test/setup';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { handlers } from '../../../test/handlers';

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Booking Flows & Response Codes', () => {
  it('handles 201 CONFIRMED: reserves spot and shows confirmation card', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const bookBtn = await screen.findByRole('button', { name: /Confirm Spot Reservation/i });
    expect(bookBtn).toBeInTheDocument();

    await expectNoA11yViolations(container);

    fireEvent.click(bookBtn);

    expect(await screen.findByText('Booking Confirmed!')).toBeInTheDocument();
    expect(screen.getByText(/We look forward to seeing you in the studio/i)).toBeInTheDocument();
  });

  it('handles 202 WAITLISTED: shows waitlist position and updates status', async () => {
    server.use(
      http.get('/api/v1/sessions/sess_wait_test', () => {
        return HttpResponse.json({
          id: 'sess_wait_test',
          classId: 'cls_1',
          classTitle: 'Power Vinyasa Yoga',
          category: 'YOGA',
          trainerName: 'Ananya Sharma',
          roomName: 'Studio A',
          startsAt: '2026-11-01T07:00:00Z',
          endsAt: '2026-11-01T08:00:00Z',
          capacity: 20,
          bookedCount: 20, // Full session
          status: 'SCHEDULED',
        });
      }),
      http.post('/api/v1/bookings', () => {
        return HttpResponse.json(
          {
            id: 'bk_wait_123',
            sessionId: 'sess_wait_test',
            classTitle: 'Power Vinyasa Yoga',
            status: 'WAITLISTED',
            waitlistPosition: 3,
            bookedAt: '2026-10-29T10:00:00Z',
          },
          { status: 202 }
        );
      })
    );

    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_wait_test']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const waitlistBtn = await screen.findByRole('button', { name: /Join Waitlist/i });
    fireEvent.click(waitlistBtn);

    expect(await screen.findByText('Waitlist Spot Confirmed!')).toBeInTheDocument();
    expect(screen.getByText(/You are #3 on the waitlist/i)).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });

  it('handles 402 UPGRADE_REQUIRED: displays prompt to view plans', async () => {
    server.use(
      http.post('/api/v1/bookings', () => {
        return HttpResponse.json(
          {
            status: 402,
            code: 'UPGRADE_REQUIRED',
            detail: 'You have used all session credits included in your current plan.',
          },
          { status: 402 }
        );
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const bookBtn = await screen.findByRole('button', { name: /Confirm Spot Reservation/i });
    fireEvent.click(bookBtn);

    expect(
      await screen.findByText(/You have used all session credits/i)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /View Membership Plans & Upgrades/i })
    ).toBeInTheDocument();
  });

  it('handles 409 SESSION_FULL: shows session full notification', async () => {
    server.use(
      http.post('/api/v1/bookings', () => {
        return HttpResponse.json(
          {
            status: 409,
            code: 'SESSION_FULL',
            detail: 'This class is full. You can join the waitlist.',
          },
          { status: 409 }
        );
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const bookBtn = await screen.findByRole('button', { name: /Confirm Spot Reservation/i });
    fireEvent.click(bookBtn);

    expect(await screen.findByText(/This class is full/i)).toBeInTheDocument();
  });

  it('handles BOOKING_RULE_VIOLATION: locks reservations 2 hours before start', async () => {
    server.use(
      http.post('/api/v1/bookings', () => {
        return HttpResponse.json(
          {
            status: 400,
            code: 'BOOKING_RULE_VIOLATION',
            detail: 'Classes lock 2 hours before start. Cancellations or new bookings are closed.',
          },
          { status: 400 }
        );
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const bookBtn = await screen.findByRole('button', { name: /Confirm Spot Reservation/i });
    fireEvent.click(bookBtn);

    expect(await screen.findByText(/Classes lock 2 hours before start/i)).toBeInTheDocument();
  });

  it('handles AGE_RESTRICTED: displays age restriction error', async () => {
    server.use(
      http.post('/api/v1/bookings', () => {
        return HttpResponse.json(
          {
            status: 403,
            code: 'AGE_RESTRICTED',
            detail: 'You must be 18 or older to access nutrition calculations and workout safety screening.',
          },
          { status: 403 }
        );
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const bookBtn = await screen.findByRole('button', { name: /Confirm Spot Reservation/i });
    fireEvent.click(bookBtn);

    expect(await screen.findByText(/You must be 18 or older/i)).toBeInTheDocument();
  });

  it('handles 429 RATE_LIMITED with Retry-After', async () => {
    server.use(
      http.post('/api/v1/bookings', () => {
        return HttpResponse.json(
          {
            status: 429,
            code: 'RATE_LIMITED',
            detail: 'Too many requests. Please wait a moment before trying again.',
            retryAfter: 30,
          },
          { status: 429 }
        );
      })
    );

    const store = setupStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const bookBtn = await screen.findByRole('button', { name: /Confirm Spot Reservation/i });
    fireEvent.click(bookBtn);

    expect(await screen.findByText(/Too many requests/i)).toBeInTheDocument();
  });

  // --- MyBookings Page & Cutoff Dialog ---
  it('MyBookingsPage: displays upcoming and past bookings, and explains 2-hour cutoff rule in cancellation dialog', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter>
          <MyBookingsPage />
        </MemoryRouter>
      </Provider>
    );

    expect(await screen.findByText('Power Vinyasa Yoga')).toBeInTheDocument();
    await expectNoA11yViolations(container);

    const cancelBtn = screen.getByRole('button', { name: 'Cancel Reservation' });
    fireEvent.click(cancelBtn);

    // Assert dialog opens and explains 2-hour cutoff rule and credit restoration
    expect(await screen.findByText('Cancel Class Reservation?')).toBeInTheDocument();
    expect(screen.getByText(/2-Hour Studio Cutoff Rule/i)).toBeInTheDocument();
    expect(screen.getByText(/Cancellations made at least 2 hours before/i)).toBeInTheDocument();
    expect(screen.getByText(/immediately restore your monthly session credit/i)).toBeInTheDocument();

    await expectNoA11yViolations(container);

    // Confirm cancel
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Cancellation' }));
    await waitFor(() => {
      expect(screen.queryByText('Cancel Class Reservation?')).not.toBeInTheDocument();
    });
  });
});
