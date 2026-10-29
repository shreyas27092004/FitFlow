import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { handlers } from '../../../test/handlers';
import { setupStore } from '../../../app/store';
import { ClassDetailsPage } from '../ClassDetailsPage';
import { SessionDetailsPage } from '../../sessions/SessionDetailsPage';
import { expectNoA11yViolations } from '../../../test/setup';

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Class & Session Details Suite', () => {
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

  // 1. ClassDetailsPage: renders class details overview
  it('ClassDetailsPage: renders title, category, description, and intensity', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/classes/cls_1']}>
          <Routes>
            <Route path="/app/classes/:id" element={<ClassDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'Power Vinyasa Yoga' })).toBeInTheDocument();
    });

    expect(screen.getAllByText('YOGA')[0]).toBeInTheDocument();
    expect(screen.getByText(/A dynamic flow sequence/i)).toBeInTheDocument();
    expect(screen.getByText(/60 mins/i)).toBeInTheDocument();
  });

  // 2. ClassDetailsPage: renders scheduled sessions for this class
  it('ClassDetailsPage: renders upcoming sessions for this class', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/classes/cls_1']}>
          <Routes>
            <Route path="/app/classes/:id" element={<ClassDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'Power Vinyasa Yoga' })).toBeInTheDocument();
    });

    // Session room
    expect(screen.getByText(/Studio A/i)).toBeInTheDocument();
  });

  // 3. ClassDetailsPage: empty sessions state
  it('ClassDetailsPage: displays empty state when no sessions are scheduled', async () => {
    server.use(
      http.get('/api/v1/sessions', () => {
        return HttpResponse.json({ content: [], totalElements: 0 });
      })
    );

    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/classes/cls_1']}>
          <Routes>
            <Route path="/app/classes/:id" element={<ClassDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('No scheduled sessions right now')).toBeInTheDocument();
    });
  });

  // 4. ClassDetailsPage: not found state
  it('ClassDetailsPage: displays not found state when class does not exist', async () => {
    server.use(
      http.get('/api/v1/classes/:id', () => {
        return HttpResponse.json({ code: 'NOT_FOUND', detail: 'Class missing' }, { status: 404 });
      })
    );

    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/classes/cls_nonexistent']}>
          <Routes>
            <Route path="/app/classes/:id" element={<ClassDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Class Not Found')).toBeInTheDocument();
    });

    expect(screen.getByRole('link', { name: /Back to Classes/i })).toBeInTheDocument();
  });

  // 5. SessionDetailsPage: renders session info and trainer
  it('SessionDetailsPage: renders session metadata and trainer details', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'Power Vinyasa Yoga' })).toBeInTheDocument();
    });

    expect(screen.getByText('Ananya Sharma')).toBeInTheDocument();
    expect(screen.getByText('Studio A')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Confirm Spot Reservation/i })).toBeInTheDocument();
  });

  // 6. SessionDetailsPage: completes booking flow
  it('SessionDetailsPage: allows user to confirm spot and books session', async () => {
    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Confirm Spot Reservation/i })).toBeInTheDocument();
    });

    const bookBtn = screen.getByRole('button', { name: /Confirm Spot Reservation/i });
    fireEvent.click(bookBtn);

    await waitFor(() => {
      expect(screen.getByText(/Booking Confirmed!/i)).toBeInTheDocument();
    });
  });

  // 7. SessionDetailsPage: handles entitlement exhaustion UPGRADE_REQUIRED
  it('SessionDetailsPage: shows upgrade prompt when entitlement exhausted', async () => {
    server.use(
      http.post('/api/v1/bookings', () => {
        return HttpResponse.json(
          {
            code: 'UPGRADE_REQUIRED',
            detail: 'You have used all session credits in your current membership.',
          },
          { status: 402 }
        );
      })
    );

    const store = createTestStore();
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Confirm Spot Reservation/i })).toBeInTheDocument();
    });

    const bookBtn = screen.getByRole('button', { name: /Confirm Spot Reservation/i });
    fireEvent.click(bookBtn);

    await waitFor(() => {
      expect(screen.getByText(/You have used all session credits included in your current plan/i)).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /View Membership Plans & Upgrades/i })).toBeInTheDocument();
  });

  // 8. SessionDetailsPage: passes axe-core accessibility checks
  it('SessionDetailsPage: passes axe-core accessibility checks', async () => {
    const store = createTestStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/sessions/sess_1']}>
          <Routes>
            <Route path="/app/sessions/:id" element={<SessionDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'Power Vinyasa Yoga' })).toBeInTheDocument();
    });

    await expectNoA11yViolations(container);
  });
});
