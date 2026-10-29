import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { setupStore } from '../../../app/store';
import { HomeDashboard } from '../HomeDashboard';
import { BrowseClassesPage } from '../../classes/BrowseClassesPage';
import { ClassDetailsPage } from '../../classes/ClassDetailsPage';
import { PaymentsPage } from '../../payments/PaymentsPage';
import { expectNoA11yViolations } from '../../../test/setup';
import { setupServer } from 'msw/node';
import { handlers } from '../../../test/handlers';

const server = setupServer(...handlers);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('Member Dashboard, Classes & Payments', () => {
  it('HomeDashboard: renders member metrics, active plan, and next scheduled booking', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter>
          <HomeDashboard />
        </MemoryRouter>
      </Provider>
    );

    expect(await screen.findByText(/Welcome back, Test Member/i)).toBeInTheDocument();
    expect(screen.getByText('Plan Sessions Left')).toBeInTheDocument();
    expect(screen.getByText('5 / 8')).toBeInTheDocument();
    expect(screen.getByText('Upcoming Classes')).toBeInTheDocument();
    expect(screen.getByText('Active Streak')).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });

  it('BrowseClassesPage: renders search bar, category chips, and class cards', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/classes']}>
          <Routes>
            <Route path="/app/classes" element={<BrowseClassesPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(await screen.findByRole('heading', { name: 'Power Vinyasa Yoga' })).toBeInTheDocument();
    expect(screen.getByText('High-Intensity Interval Training (HIIT)')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search by title/i)).toBeInTheDocument();

    // Filter by clicking category tag
    fireEvent.click(screen.getByRole('button', { name: 'HIIT' }));

    await expectNoA11yViolations(container);
  });

  it('ClassDetailsPage: displays full description and upcoming sessions', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/classes/cls_1']}>
          <Routes>
            <Route path="/app/classes/:id" element={<ClassDetailsPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const heading = await screen.findByRole('heading', { level: 1 });
    expect(heading).toHaveTextContent('Power Vinyasa Yoga');
    expect(screen.getByText(/A dynamic flow sequence linking breath/i)).toBeInTheDocument();
    expect(screen.getByText('Upcoming Sessions for this Class')).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });

  it('PaymentsPage: renders payments table and opens invoice modal', async () => {
    const store = setupStore();
    const { container } = render(
      <Provider store={store}>
        <MemoryRouter>
          <PaymentsPage />
        </MemoryRouter>
      </Provider>
    );

    expect(await screen.findByText('Rs 499.00')).toBeInTheDocument();
    const invoiceBtn = screen.getByRole('button', { name: 'INV-2026-001' });
    fireEvent.click(invoiceBtn);

    expect(await screen.findByRole('heading', { name: /INV-2026-001/i })).toBeInTheDocument();
    expect(screen.getByText('Starter Flow Monthly Studio Membership')).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });
});
